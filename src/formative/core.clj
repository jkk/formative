(ns formative.core
  (:require [formative.render-form :refer [render-form*]]
            formative.render-form.table
            formative.render-form.div
            formative.render-form.bootstrap
            formative.render-form.inline
            [formative.render-field :as rfield]
            [formative.helpers :refer [get-field-label]]
            [clojure.walk :refer [stringify-keys]]
            [clojure.string :as string]
            [com.jkkramer.ordered.map :refer [ordered-map]]))

(def ^:dynamic *renderer* :bootstrap-horizontal)

(defn normalize-field
  "Ensures :name and :type keys are in the right format"
  [field]
  (assoc field
    :name (name (:name field))
    :type (if (:type field)
            (keyword (name (:type field)))
            :text)))

(defmulti prep-field
  "Prepares a field for rendering. The default preparation is to populate
  the :value key and add a label if not present. Each type may have its own
  particular preparation steps. For example, the :checkbox type adds a
  :checked key."
  (fn [field values]
    (:type field)))

(defmethod prep-field :default [field values]
  (assoc field
    :value (get values (:name field))
    :label (get-field-label field)))

(defmethod prep-field :checkbox [field values]
  (let [field (if (and (not (contains? field :value))
                       (not (contains? field :unchecked-value)))
                (assoc field :value "true" :unchecked-value "false"
                       :datatype :boolean)
                field)
        val (get values (:name field))]
    (assoc field
      :value (:value field "true")
      :checked (= (str val) (str (:value field "true")))
      :label (get-field-label field))))

(defmethod prep-field :submit [field values]
  (assoc field
    :value (:value field "")))

(defmethod prep-field :html [field values]
  field)

(defmethod prep-field :labeled-html [field values]
  (assoc field :label (get-field-label field)))

(defn prep-fields
  "Normalizes field specifications and populates them with values"
  [fields values]
  (for [field fields]
    (-> field
        (normalize-field)
        (prep-field values))))

(defn merge-fields
  "Combines two sequences of field specifications into a single sequence,
  merging field2 specs when the :name key matches, appending otherwise."
  [fields1 fields2]
  (let [fields2 (if (map? fields2)
                  fields2
                  (into (ordered-map) (map (juxt :name identity) fields2)))
        [ret leftovers] (reduce
                          (fn [[ret fields2] spec]
                            (let [fname (:name spec)
                                  [spec* fields2*]
                                  (if (contains? fields2 fname)
                                    [(merge spec (get fields2 fname))
                                     (dissoc fields2 fname)]
                                    [spec fields2])]
                              [(conj ret spec*) fields2*]))
                          [[] fields2]
                          fields1)]
    (concat ret (vals leftovers))))

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
        fields (if (some #(= :submit (:type %)) fields)
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
  "Given a form specification, returns a rendering of a form - e.g., Hiccup
  data, an HTML string, etc. 
  
  Valid keys for spec include the following HTML form attributes:

      :action :method :enctype :accept :name :id :class
      :onsubmit :onreset :accept-charset :autofill

  And the following special keys:

      :renderer     - Determines renderer to use. Built-in options:
                        :bootstrap-horizontal (the default)
                        :bootstrap-stacked
                        :table
                        :inline
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
      :problems     - Sequence of field names or problem maps. Form
                      renderers typically add a class and style to highlight
                      problem fields and, if problem maps are provided,
                      show descriptive messages.

  A field specification is a map with the following keys:

      :name         - Required name of the field, a keyword
      :type         - UI type of the field. Defaults to :text. Built-in types
                      include: :text, :textarea, :select, :checkbox,
                      :checkboxes, :radio, :html, :heading, :us-state,
                      :ca-state, :country, :date-select. Each type may have
                      particular keys that it makes use of.

                      Selection fields such as :select, :checkboxes, and :radio
                      expect an :options key, which is a collection of options
                      which conform to one of the following formats:
                        - [\"value\" \"label\"]
                        - {:value \"value\" :label \"label\"]
                        - \"value and label\"

                      The :heading type expects a :text key.

                      The :html type expects an :html key.
      :datatype     - Datatype of the field when parsed. Can be one of:
                      :str, :int, :long, :boolean, :float, :double, :decimal,
                      :bigint, :date, :file. Defaults to :str.

                      All types can be appended with an \"s\" when a sequence
                      is expected - e.g., :ints for a sequence of integers. This
                      is useful for fields that have composite values, such as
                      :checkboxes.

                      :date field values are expected to be in yyyy-MM-dd
                      format by default. Set :date-format to change that. 

                      :file fields must have an :upload-handler key which is
                      a function that takes two arguments: the field
                      specification, and the Ring file upload payload."
  [spec]
  (apply render-form* (prep-form spec)))

(defn render-field
  "Render an individual form field element"
  ([field]
    (rfield/render-field
      (prep-field (normalize-field field) {})))
  ([field value]
    (let [norm-field (normalize-field field)]
      (rfield/render-field
        (prep-field norm-field {(:name norm-field) value})))))

(defmacro with-renderer [renderer & body]
  `(binding [*renderer* ~renderer]
     (do ~@body)))
