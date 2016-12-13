(ns formative.core
  (:require [formative.render :as r]
            formative.render.table
            formative.render.div
            formative.render.bootstrap
            formative.render.bootstrap3
            formative.render.inline
            [formative.util :as fu]
            [clojure.walk :refer [stringify-keys]]
            [clojure.string :as string]
            #?(:clj [ring.middleware.anti-forgery :refer [*anti-forgery-token*]])))

(def ^:dynamic *renderer* :bootstrap-horizontal)

(defn- normalize-name [fname]
  (if (keyword? fname)
    (let [fname (name fname)
          parts (string/split fname #"\.")]
      (if (next parts)
        (apply str (first parts) (map #(str "[" % "]") (rest parts)))
        fname))
    fname))

(defn normalize-field
  "Ensures :name and :type keys are in the right format"
  [field]
  {:pre [(:name field)]}
  (assoc field
         :name (normalize-name (:name field))
         :type (if (:type field)
                 (keyword (name (:type field)))
                 :text)))

(defmulti prep-field
  "Prepares a field for rendering, dispatching on :type. The default
  preparation is to populate the :value key and add a label if not present.
  Each type may have its own particular preparation steps. For example, the
  :checkbox type adds a :checked key."
  (fn [field values & [form]]
    (:type field)))

(defn- begins-with [s sub]
  (when (< (count sub) (count s)) ;must be longer in this case
    (= sub (subs s 0 (count sub)))))

(defn- get-value [values field]
  (or (get-in values (fu/expand-name (:name field)))
      (get values (:name field))
      (when (:flatten field)
        (let [name-prefix (str (:name field) "-")]
          (reduce-kv
           (fn [val k v]
             (if (begins-with k name-prefix)
               (assoc val (subs k (count name-prefix)) v)
               val))
           nil values)))))

(defn- prep-field-default [field values & [form]]
  (cond-> (assoc field
                 :value (get-value values field)
                 :label (r/get-field-label field))
    (:blank-nil form) (assoc :blank-nil true)))

(defmethod prep-field :default [field values & [form]]
  (prep-field-default field values form))

(defmethod prep-field :datetime-select [field values & [form]]
  (let [field* (prep-field-default field values form)]
    (if-let [timezone (:timezone field (:timezone form))]
      (assoc field* :timezone timezone)
      field*)))

(defmethod prep-field :checkbox [field values & [form]]
  (let [field (if (and (not (contains? field :value))
                       (not (contains? field :unchecked-value)))
                (assoc field :value "true" :unchecked-value "false"
                       :datatype :boolean)
                field)
        val (get-value values field)]
    (assoc field
           :value (:value field "true")
           :checked (= (str val) (str (:value field "true")))
           :label (r/get-field-label field))))

(defmethod prep-field :submit [field values & [form]]
  (assoc field
         :value (:value field "")))

(defmethod prep-field :html [field values & [form]]
  field)

(defmethod prep-field :labeled-html [field values & [form]]
  (assoc field :label (r/get-field-label field)))

(defn prep-fields
  "Normalizes field specifications and populates them with values"
  [fields values & [form]]
  (for [field fields]
    (-> field
        (normalize-field)
        (prep-field values form))))

(defmethod prep-field :compound [field values & [form]]
  (let [field (prep-field-default field values form)]
    (update-in field [:fields] prep-fields (:value field) form)))

(defn merge-fields
  "Combines two sequences of field specifications into a single sequence,
  using the following rules for each fields2 spec:

  - If the :name key matches an existing field, the spec is merged.
  - If an :after key is set, the spec will be inserted after the field whose
    :name matches :after.
  - If a :before key is set, the spec will be inserted before the field whose
    :name matches :before.
  - Otherwise, the spec will be appended.

  If a form is given, its fields will be used and the updated form will be
  returned.

  This function is mainly useful for making runtime tweaks to form fields."
  [form-or-fields1 fields2]
  (let [[form fields1] (if (map? form-or-fields1)
                         [form-or-fields1 (:fields form-or-fields1)]
                         [nil form-or-fields1])
        fields2-map (if (map? fields2)
                      fields2
                      (into {} (map (juxt :name identity)
                                    fields2)))
        after-fields (reduce
                      (fn [m spec]
                        (update-in m [(:after spec)]
                                   (fnil conj []) (dissoc spec :after)))
                      {}
                      (filter :after fields2))
        before-fields (reduce
                       (fn [m spec]
                         (update-in m [(:before spec)]
                                    (fnil conj []) (dissoc spec :before)))
                       {}
                       (filter :before fields2))
        [ret leftovers] (reduce
                         (fn [[ret f2m] spec]
                           (let [fname (:name spec)
                                 [spec* f2m*]
                                 (if (contains? f2m fname)
                                   [(merge spec (get f2m fname))
                                    (dissoc f2m fname)]
                                   [spec f2m])
                                 ret* (if-let [bspecs (get before-fields fname)]
                                        (into ret bspecs)
                                        ret)
                                 ret* (conj ret* spec*)
                                 ret* (if-let [aspecs (get after-fields fname)]
                                        (into ret* aspecs)
                                        ret*)]
                             [ret* f2m*]))
                         [[] fields2-map]
                         fields1)
        new-fields (concat ret (remove (some-fn :before :after)
                                       (filter (comp leftovers :name) fields2)))]
    (if form
      (assoc form :fields new-fields)
      new-fields)))

(defn remove-fields
  "Removes fields from form-or-fields according to their name"
  [form-or-fields names]
  (let [[form fields] (if (map? form-or-fields)
                        [form-or-fields (:fields form-or-fields)]
                        [nil form-or-fields])
        names-set (if (set? names) names (into #{} names))
        new-fields (remove (comp names-set :name)
                           fields)]
    (if form
      (assoc form :fields new-fields)
      new-fields)))

(defn- prep-problems [problems]
  (set
   (if (map? (first problems))
     (map name
          (mapcat (fn [p]
                    (or (:keys p)
                        (when (:field-name p)
                          [(:field-name p)])))
                  problems))
     (map name problems))))

(defn prep-form
  "Prepares a form for rendering by normalizing and populating fields, adding
  a submit button field, etc. See render-form for a description of the form
  specification."
  [spec]
  (let [;; HTML attrs
        form-attrs (select-keys
                    spec [:action :method :enctype :accept :name :id :class
                          :onsubmit :onreset :accept-charset :autofill
                          :novalidate :autocomplete])
        method (string/upper-case
                (name (or (:method spec) :post)))
        form-attrs (assoc form-attrs
                          :method (if (= "GET" method) method "POST")
                          :renderer (:renderer spec *renderer*))
        ;; Field values
        values (stringify-keys
                (if (string? (:values spec))
                  (fu/decode-form-data (:values spec))
                  (:values spec)))
        fields (:fields spec)
        ;; Emulate HTTP methods
        [fields values] (if-not (#{"PUT" "DELETE" "PATCH"} method)
                          [fields values]
                          [(cons {:type :hidden :name "_method"} fields)
                           (assoc values "_method" method)])
        ;; CSRF protection
        #?(:clj [fields values]) #?(:clj (if (and (not= "GET" method) (bound? #'*anti-forgery-token*)
                                                  *anti-forgery-token*)
                                           [(cons {:type :hidden :name "__anti-forgery-token"} fields)
                                            (assoc values "__anti-forgery-token" *anti-forgery-token*)]
                                           [fields values]))
        fields (prep-fields fields values spec)
        ;; Attach :cancel-href to submit button
        fields (if (or (:cancel-label spec) (:cancel-href spec))
                 (for [field fields]
                   (if (= :submit (:type field))
                     (merge field (select-keys spec [:cancel-label :cancel-href]))
                     field))
                 fields)
        ;; Add submit if not already present
        fields (if (or (some #(= :submit (:type %)) fields)
                       (nil? (:submit-label spec ::absent)))
                 fields
                 (concat fields
                         [{:type :submit
                           :name "submit"
                           :cancel-label (:cancel-label spec "Cancel")
                           :cancel-href (:cancel-href spec)
                           :value (:submit-label spec "Submit")}]))
        ;; Problems
        problems (prep-problems (:problems spec))
        fields (for [field fields]
                 (if (problems (:name field))
                   (assoc field :problem true)
                   field))]
    [form-attrs fields spec]))

(defn render-form
  "Given a form specification, returns a rendering of the form - e.g., Hiccup
  data, an HTML string, etc.

  Valid keys for spec include the following HTML form attributes:

      :action :method :enctype :accept :name :id :class
      :onsubmit :onreset :accept-charset :autofill :novalidate
      :autocomplete

  Unlike an HTML form, :method defaults to :post. If method is something other
  than :get or :post, a hidden field with name \"_method\" will be added, and
  the form method set to :post. If you are using Compojure for routing, it will
  recognize the \"_method\" field.

  The following special keys are also supported:

      :renderer     - Determines renderer to use. Built-in options:
                        :bootstrap-horizontal (the default)
                        :bootstrap-stacked
                        :bootstrap3-stacked
                        :table
                        :inline
                      Custom renderers can be created by implementing the
                      formative.render/render-form multimethod.
      :fields       - Sequence of form field specifications. See below.
      :values       - Map of values used to populate the form fields, or a
                      form-data-encoded string
      :submit-label - Label to use on the submit button. Defaults to \"Submit\"
      :cancel-label - Label to use on the cancel button. Defaults to \"Cancel\"
      :cancel-href  - When provided, shows a \"Cancel\" hyperlink next to the
                      submit button
      :validations  - A sequence of validation specifications
      :validator    - A function to call to validate parsed values for this
                      form. The function should take a map of values and return
                      a sequence of problem maps for each field that failed to
                      validate. The problem map should contain the keys :keys
                      and :msg.
      :validate-types - Whether to validate datatypes; true by default.
      :blank-nil    - When values are parsed, replace blank strings with nil
      :problems     - Sequence of field names or problem maps. Form
                      renderers typically add a class and style to highlight
                      problem fields and, if problem maps are provided,
                      show descriptive messages.
      :timezone     - String of timezone with which to localize the display of
                      :datetime-select fields. The default is UTC. JVM only.

  A field specification is a map with the following keys:

      :name         - Required name of the field, a keyword or string. Use
                      dotted keywords like :foo.bar to represent fields that
                      will parse as nested map values.
      :label        - Optional display name. Auto-generated from :name if not
                      provided
      :type         - Type of the field. Defaults to :text. See below for
                      built-in types. If an unrecognized type is provided,
                      an <input> element with that type will be assumed.
                      Certain types imply particular parsing or validation
                      rules - e.g., an :email field must be a valid email.
      :datatype     - Optional. Datatype of the field used for parsing. Can be
                      one of:

                      :str, :int, :long, :boolean, :float, :double, :decimal,
                      :bigint, :date, :time, :instant, :file.

                      Defaults to :str.

                      All types can be appended with an \"s\" when a sequence
                      is expected - e.g., :ints for a sequence of integers. This
                      is useful for fields that have composite values, such as
                      :checkboxes.

                      :date field values are expected to be in yyyy-MM-dd
                      format by default. Set :date-format to change that. :time
                      fields may be in H:m or H:m:s format. :instant fields
                      are in EDN instant (RFC-3339) format.

                      All date/time fields are parsed into java.util.Date
                      or java.sql.Time (or Date for ClojureScript) objects
                      created using the UTC timezone.
      :datatype-error - Optional custom error message to use if datatype
                      validation fails.
      :blank-nil    - When the value is parsed, replace a blank string with nil
      :flatten      - If a value parses to a map (e.g. for :compound fields),
                      adds each key of the map to the top level values map,
                      prefixed with the field name and a dash.
      :note         - A bit of explanatory content to accompany the field
      :prefix       - Content to insert before a field
      :suffix       - Content to insert after a field

  Built-in field types:

      :text         - Single-line text input
      :textarea     - Multi-line text input
      :select       - Dropdown. Special keys:
                        :options - options to display; see below for format
                        :placeholder - text for a first, disabled option
                        :first-option - option to prepend to the other options
    :checkbox     - Defaults to true/false when no :value is given. Special
                    keys:
                      :value - value of a checked input (default true)
                      :unchecked-value - value of an unchecked input (default
                        false)
    :checkboxes   - Multiple checkboxes that parse to a collection of values.
                    Special keys:
                      :options - options to display; see below for format
                      :cols - number of columns to group checkboxes into
    :radios       - Multiple radio inputs that parse to a single value.
                    Special keys:
                      :options - options to display; see below for format
    :email        - Email text input
    :us-state     - United States state dropdown. Accepts :select special
                    keys.
    :us-zip       - United States ZIP code
    :ca-state     - Canadian province
    :country      - Country dropdown. Accepts :select special keys.
    :date-select  - Date dropdown. Renders as multiple :select fields, parses
                    as a UTC java.util.Date (or Date for ClojureScript).
                    Accepts Joda dates as values.
                    Special keys:
                      :year-start
                      :year-end
    :year-select  - Year dropdown, parses to integer. Accepts :select special
                    keys plus:
                      :start
                      :end
    :month-select - Month dropdown, parses to integer (1-12). Accepts :select
                    special keys plus:
                      :numbers - when true, shows numbers instead of month
                                 names
    :time-select  - Time dropdown. Renders as multiple :select fields, parses
                    as a UTC java.sql.Time (or Date for ClojureScript).
                    Accepts Joda times as values.
                    Special keys:
                      :compact - true to use a single dropdown (default false)
                      :ampm - true to use am/pm (the default); false to use
                              24-hour format
                      :step - step between minutes/seconds; default 5
                      :seconds - whether to include a seconds field
                      :start - when :compact is true, start time
                      :end - when :compact is true, end time (inclusive)
    :datetime-select - Combined date/time dropdown. Parses as a UTC
                    java.util.Date (or Date for ClojureScript). Accepts Joda
                    date values. See :date-select and :time-select for special
                    keys, plus:
                      :timezone - String of timezone with which to localize the
                                  display. The default is UTC. JVM only.
    :currency     - Text input for money. Parses as a :decimal datatype
    :file         - File upload input. Special keys:
                      :upload-handler - optional handler called when a file is
                        uploaded. The field's specification and Ring param
                        value are passed as arguments to the handler. The
                        handler can return whatever value is appropriate
                        (e.g., a String or a File).
    :compound     - Multiple fields displayed and parsed as one field. Special
                    keys:
                      :separator - string or Hiccup data; defaults to a space
                      :combiner - a function which takes a collection of the
                                  rendered fields and returns Hiccup data
                                  that represents the combined field; by
                                  default, fields are combined by interposing
                                  the separator
    :submit       - Submit button. Included by default, but can be added
                    explicitly if you prefer. Unlike with a default submit
                    button, its value will be parsed.

  The :options key for :select and other types accepts a collection of any
  of the following formats:

    [\"value\" \"label\" sub-options]
    {:value \"value\" :label \"label\" :options sub-options}
    \"value and label\"

  If sub-options are provided, the element is rendered as a group (e.g.,
  optgroup for :select fields).

  The :options value can also be a function of no arguments or a Delay object --
  either of which must yield a collection in one of the above formats.

  The following presentational types are also available. They are excluded from
  parsing.

    :heading      - Section heading. In the default renderer, acts as a fieldset
                    separator. Special keys:
                      :text - heading text
    :html         - Custom, unlabeled HTML. Special keys:
                      :html - HTML string or Hiccup data
    :labeled-html - Custom, labeled HTML. Special keys:
                      :html - HTML string or Hiccup data

  Field types are extensible with the `formative.render/render-field` and
  `formative.parse/parse-input` multimethods."
  [spec]
  (apply r/render-form (prep-form spec)))

(defn render-field
  "Render an individual form field element as Hiccup data. See render-form
  for field specification format."
  ([field]
   (r/render-field
    (prep-field (normalize-field field) {})))
  ([field value]
   (let [norm-field (normalize-field field)]
     (r/render-field
      (prep-field norm-field {(:name norm-field) value})))))

#?(:clj
   (defmacro with-renderer [renderer & body]
     `(binding [*renderer* ~renderer]
        (do ~@body))))
