(ns formative.core
  (:require [formative.render :as r]
            formative.render.table
            formative.render.div
            formative.render.bootstrap
            formative.render.inline
            [formative.util :as fu]
            [clojure.walk :refer [stringify-keys]]
            [clojure.string :as string]))

(def ^:dynamic *renderer* :bootstrap-horizontal)

(defn normalize-field
  "Ensures :name and :type keys are in the right format"
  [field]
  {:pre [(:name field)]}
  (assoc field
    :name (name (:name field))
    :type (if (:type field)
            (keyword (name (:type field)))
            :text)))

(defmulti prep-field
  "Prepares a field for rendering, dispatching on :type. The default
  preparation is to populate the :value key and add a label if not present.
  Each type may have its own particular preparation steps. For example, the
  :checkbox type adds a :checked key."
  (fn [field values]
    (:type field)))

(defmethod prep-field :default [field values]
  (assoc field
    :value (get-in values (fu/expand-name (:name field)))
    :label (r/get-field-label field)))

(defmethod prep-field :checkbox [field values]
  (let [field (if (and (not (contains? field :value))
                       (not (contains? field :unchecked-value)))
                (assoc field :value "true" :unchecked-value "false"
                       :datatype :boolean)
                field)
        val (get-in values (fu/expand-name (:name field)))]
    (assoc field
      :value (:value field "true")
      :checked (= (str val) (str (:value field "true")))
      :label (r/get-field-label field))))

(defmethod prep-field :submit [field values]
  (assoc field
    :value (:value field "")))

(defmethod prep-field :html [field values]
  field)

(defmethod prep-field :labeled-html [field values]
  (assoc field :label (r/get-field-label field)))

(defn prep-fields
  "Normalizes field specifications and populates them with values"
  [fields values]
  (for [field fields]
    (-> field
        (normalize-field)
        (prep-field values))))

(defn merge-fields
  "Combines two sequences of field specifications into a single sequence,
  using the following rules for each fields2 spec:

  - If the :name key matches an existing field, the spec is merged.
  - If an :after key is set, the spec will be inserted after the field whose
    :name matches :after.
  - If a :before key is set, the spec will be inserted before the field whose
    :name matches :before.
  - Otherwise, the spec will be appended.

  This function is mainly useful for making runtime tweaks to form fields."
  [fields1 fields2]
  (let [fields2-map (if (map? fields2)
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
                          fields1)]
    (concat ret (remove (some-fn :before :after)
                        (filter (comp leftovers :name) fields2)))))

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
  (let [form-attrs (select-keys
                     spec [:action :method :enctype :accept :name :id :class
                           :onsubmit :onreset :accept-charset :autofill
                           :novalidate :autocomplete])
        form-attrs (assoc form-attrs
                     :method (:method spec :post)
                     :renderer (:renderer spec *renderer*))
        values (stringify-keys (:values spec))
        fields (if (:tweaks spec)
                 (merge-fields (:fields spec) (:tweaks spec))
                 (:fields spec))
        fields (prep-fields fields values)
        fields (if (:cancel-href spec)
                 (for [field fields]
                   (if (= :submit (:type field))
                     (assoc field :cancel-href (:cancel-href spec))
                     field))
                 fields)
        fields (if (or (some #(= :submit (:type %)) fields)
                       (nil? (:submit-label spec ::absent)))
                 fields
                 (concat fields
                         [{:type :submit
                           :name "submit"
                           :cancel-href (:cancel-href spec)
                           :value (:submit-label spec "Submit")}]))
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

  Unlike an HTML form, :method defaults to :post.

  The following special keys are also supported:

      :renderer     - Determines renderer to use. Built-in options:
                        :bootstrap-horizontal (the default)
                        :bootstrap-stacked
                        :table
                        :inline
                      Custom renderers can be created by implementing the
                      formative.render/render-form multimethod. 
      :fields       - Sequence of form field specifications. See below.
      :tweaks       - Sequence of form field specifications which will be
                      merged with :fields using merge-fields.
      :values       - Map of values used to populate the form fields
      :submit-label - Label to use on the submit button. Defaults to \"Submit\"
      :cancel-href  - When provided, shows a \"Cancel\" hyperlink next to the
                      submit button
      :validations  - A sequence of validation specifications
      :validator    - A function to call to validate parsed values for this
                      form. The function should take a map of values and return
                      a sequence of problem maps for each field that failed to
                      validate. The problem map should contain the keys :keys
                      and :msg.
      :validate-types - Whether to validate datatypes; true by default.
      :problems     - Sequence of field names or problem maps. Form
                      renderers typically add a class and style to highlight
                      problem fields and, if problem maps are provided,
                      show descriptive messages.

  A field specification is a map with the following keys:

      :name         - Required name of the field, a keyword or string
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
                      :bigint, :date, :file.

                      Defaults to :str.

                      All types can be appended with an \"s\" when a sequence
                      is expected - e.g., :ints for a sequence of integers. This
                      is useful for fields that have composite values, such as
                      :checkboxes.

                      :date field values are expected to be in yyyy-MM-dd
                      format by default. Set :date-format to change that. 
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
                    as a java.util.Date. Special keys:
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
    :currency     - Text input for money. Parses as a :decimal datatype
    :file         - File upload input. Special keys:
                      :upload-handler - optional handler called when a file is
                        uploaded. The field's specification and Ring param
                        value are passed as arguments to the handler. The
                        handler can return whatever value is appropriate
                        (e.g., a String or a File).
    :submit       - Submit button. Included by default, but can be added
                    explicitly if you prefer. Unlike with a default submit
                    button, its value will be parsed.

  The :options key for :select and other types accepts a collection of any
  of the following formats:

    [\"value\" \"label\"]
    {:value \"value\" :label \"label\"}
    \"value and label\"

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

(defmacro with-renderer [renderer & body]
  `(binding [*renderer* ~renderer]
     (do ~@body)))
