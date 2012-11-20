(ns formative.core
  (:require [formative.render-form :refer [render-form*]]
            formative.render-form.table
            formative.render-form.div
            formative.render-form.bootstrap
            [formative.render-field :refer [render-field]]
            [clojure.walk :refer [stringify-keys]]
            [clojure.string :as string]
            [com.jkkramer.ordered.map :refer [ordered-map]]))

(def ^:dynamic *form-type* :bootstrap-horizontal)

(defn normalize-field
  "Ensures :name and :type keys are in the right format"
  [field]
  (assoc field
    :name (name (:name field))
    :type (if (:type field)
            (keyword (name (:type field)))
            :text)))

(defn- ucfirst [^String s]
  (str (Character/toUpperCase (.charAt s 0)) (subs s 1)))

(defn field-name->label
  "Turns a field name such as :foo-bar into a label like \"Foo bar\""
  [fname]
  (-> (name fname)
      (string/replace #"[_-]" " ")
      ucfirst))

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
    :label (:label field (field-name->label (:name field)))))

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
      :label (:label field (field-name->label (:name field))))))

(defmethod prep-field :submit [field values]
  (assoc field
    :value (:value field "")))

(defmethod prep-field :html [field values]
  field)

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
                              [(conj ret fname spec*) fields2*]))
                          [[] fields2]
                          fields1)]
    (apply concat ret leftovers)))

(defn prep-form
  "Prepares a form for rendering by normalizing and populating fields, adding
  a submit button field, etc. See render-form for a description of the form
  specification."
  [spec]
  (let [form-attrs (select-keys
                     spec [:action :method :enctype :accept :name :id :class
                           :onsubmit :onreset :accept-charset :autofill])
        form-attrs (assoc form-attrs
                     :type (:type spec *form-type*))
        values (stringify-keys (:values spec))
        fields (prep-fields (:fields spec) values)
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
        problems (if-not (set? (:problems spec))
                   (set (map name (:problems spec)))
                   (map name (:problems spec)))
        fields (for [field fields]
                 (if (problems (:name field))
                   (assoc field :problem true)
                   field))]
    [form-attrs fields]))

(defn render-form
  "Given a form specification, returns a Hiccup data structure representing a
  form.

  Valid keys for spec include the following HTML form attributes:

      :action :method :enctype :accept :name :id :class
      :onsubmit :onreset :accept-charset :autofill

  And the following special keys:

      :type         - Determines the type of renderer to use. Built-in options:
                        :bootstrap-horizontal (the default)
                        :bootstrap-stacked
                        :table
      :fields       - Sequence of form field specifications. See below.
      :values       - Map of values used to populate the form fields
      :submit-label - Label to use on the submit button. Defaults to \"Submit\"
      :cancel-href  - When provided, shows a \"Cancel\" hyperlink next to the
                      submit button
      :problems     - Sequence of field names that are a \"problem\". Form
                      renderers typically add a class and style to highlight
                      problem fields.

  A field specification is a map with the following keys:

      :name         - Required name of the field, a keyword
      :type         - UI type of the field. Defaults to :text. Built-in types
                      include: :text, :textarea, :select, :checkbox,
                      :checkboxes, :radio, :html, :heading, :us-state,
                      :ca-state, :country. Each type may have particular
                      keys that it makes use of.

                      Selection fields such as :select, :checkboxes, and :radio
                      expect an :options key, which is a collection of options
                      which conform to one of the following formats:
                        - [\"value\" \"label\"]
                        - {:value \"value\" :label \"label\"]
                        - \"value and label\"

                      The :heading type expects a :text key.

                      The :html type expects an :html key.
      :datatype     - Datatype of the field when parsed. Can be one of:
                      :int, :long, :boolean, :float, :double, :decimal, :bigint,
                      :date, :file.

                      All types can be appended with an \"s\" when a sequence
                      is expected - e.g., :ints for a sequence of integers. This
                      is useful for fields that have composite values, such as
                      :checkboxes.

                      :date field values are expected to be in YYYY-MM-DD
                      format.

                      :file fields must have an :upload-handler key which is
                      a function that takes two arguments: the field
                      specification, and the Ring file upload payload."
  [spec]
  (apply render-form* (prep-form spec)))

(defmacro with-form-type [type & body]
  `(binding [*form-type* ~type]
     (do ~@body)))
