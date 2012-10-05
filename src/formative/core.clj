(ns formative.core
  (:require [formative.render-form :refer [render-form*]]
            formative.render-form.table
            formative.render-form.div
            [formative.render-field :refer [render-field]]
            [clojure.walk :refer [stringify-keys]]
            [clojure.string :as string]
            [ordered.map :refer [ordered-map]]))

(def ^:dynamic *form-type* :table)

(defn normalize-field [field]
  (assoc field
    :name (name (:name field))
    :type (if (:type field)
            (keyword (name (:type field)))
            :text)))

(defn- ucfirst [^String s]
  (str (Character/toUpperCase (.charAt s 0)) (subs s 1)))

(defn field-name->label [fname]
  (-> (name fname)
      (string/replace #"[_-]" " ")
      ucfirst))

(defmulti prep-field (fn [field values]
                       (:type field)))

(defmethod prep-field :default [field values]
  (assoc field
    :value (get values (:name field))
    :label (:label field (field-name->label (:name field)))))

(defmethod prep-field :checkbox [field values]
  (let [field (if (and (not (contains? field :value))
                       (not (contains? field :unchecked-value)))
                (assoc field :value "true" :unchecked-value "false")
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

(defn prep-fields [names+fields values]
  (for [[fname field] (partition 2 names+fields)
        :let [field (assoc field :name fname)]]
    (-> field
        (normalize-field)
        (prep-field values))))

(defn merge-fields [fields1 fields2]
  (let [fields2 (if (map? fields2)
                  fields2
                  (into (ordered-map) (map vec (partition 2 fields2))))
        [ret leftovers] (reduce
                          (fn [[ret fields2] [fname spec]]
                            (let [[spec* fields2*]
                                  (if (contains? fields2 fname)
                                    [(merge spec (get fields2 fname))
                                     (dissoc fields2 fname)]
                                    [spec fields2])]
                              [(conj ret fname spec*) fields2*]))
                          [[] fields2]
                          (partition 2 fields1))]
    (apply concat ret leftovers)))

(defn prep-form [params]
  (let [form-attrs (select-keys
                    params [:action :method :enctype :accept :name :id :class
                            :onsubmit :onreset :accept-charset :autofill])
        form-attrs (assoc form-attrs
                     :type (:type params *form-type*))
        values (stringify-keys (:values params))
        fields (prep-fields (:fields params) values)
        fields (if (:cancel-href params)
                 (for [field fields]
                   (if (= :submit (:type field))
                     (assoc field :cancel-href (:cancel-href params))
                     field))
                 fields)
        fields (if (some #(= :submit (:type %)) fields)
                 fields
                 (concat fields
                         [{:type :submit
                           :name "submit"
                           :cancel-href (:cancel-href params)
                           :value (:submit-label params "Submit")}]))
        problems (if-not (set? (:problems params))
                   (set (map name (:problems params)))
                   (map name (:problems params)))
        fields (for [field fields]
                 (if (problems (:name field))
                   (assoc field :problem true)
                   field))]
    [form-attrs fields]))

(defn render-form [& params]
  (let [[params kvs] (if (map? (first params))
                       [(first params) (rest params)]
                       [{} params])
        params (merge params (apply hash-map kvs))]
    (apply render-form* (prep-form params))))

(defmacro with-form-type [type & body]
  `(binding [*form-type* ~type]
     (do ~@body)))