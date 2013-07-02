(ns formative.validate
  (:require [formative.data :as data]
            [formative.util :as fu]
            [clojure.string :as string]
            [jkkramer.verily :as v]))

(def ^:private us-states (set (map first data/us-states)))

(defn us-state [keys & [msg]]
  (v/make-validator keys #(and (not= :jkkramer.verily/absent %)
                               (not (string/blank? %))
                               (not (us-states %)))
                    (or msg "must be a valid US state")))

(def ^:private ca-states (set (map first data/ca-states)))

(defn ca-state [keys & [msg]]
  (v/make-validator keys #(and (not= :jkkramer.verily/absent %)
                               (not (string/blank? %))
                               (not (ca-states %)))
                    (or msg "must be a valid Canadian state")))

(def ^:private alpha2-countries (set (map :alpha2 data/countries)))

(defn country [keys & [msg]]
  (v/make-validator keys #(and (not= :jkkramer.verily/absent %)
                               (not (string/blank? %))
                               (not (alpha2-countries %)))
                    (or msg "must be a valid country")))

(defmethod v/validation->fn :us-state [vspec]
  (apply us-state (rest vspec)))

(defmethod v/validation->fn :ca-state [vspec]
  (apply ca-state (rest vspec)))

(defmethod v/validation->fn :country [vspec]
  (apply country (rest vspec)))

(defn us-tel [keys & [msg]]
  (v/make-validator keys #(and (not= :jkkramer.verily/absent %)
                               (not (string/blank? %))
                               (not (fu/valid-us-tel? %)))
                    (or msg "must be a valid US phone number")))

(defmethod v/validation->fn :us-tel [vspec]
  (apply us-tel (rest vspec)))

(def type-validators
  {:us-zip v/us-zip
   :us-state us-state
   :ca-state ca-state
   :country country
   :email v/email
   :url v/url
   :web-url v/web-url
   :us-tel us-tel
   :str v/string
   :strs v/strings
   :clob v/string
   :clobs v/strings
   :boolean v/bool
   :booleans v/bools
   :int v/integer
   :long v/integer
   :bigint v/integer
   :ints v/integers
   :longs v/integers
   :bigints v/integers
   :float v/floating-point
   :double v/floating-point
   :floats v/floating-points
   :doubles v/floating-points
   :decimal v/decimal
   :decimals v/decimals
   :date v/date
   :dates v/dates
   :currency v/decimal})

(defn validate-types [fields values]
  (let [validators (for [field fields
                         :let [type (:datatype field (:type field))
                               validator (type-validators type)]
                         :when validator]
                     (if (:datatype-error field)
                       (validator (:name field) (:datatype-error field))
                       (validator (:name field))))]
    ((apply v/combine validators) values)))

(defn validate [form values]
  (let [type-validator (if (= false (:validate-types form))
                         (constantly nil)
                         (partial validate-types (:fields form)))
        validator (if-let [custom-validator (:validator form)]
                    (v/combine type-validator custom-validator)
                    type-validator)
        validator (if-let [validations (:validations form)]
                    (v/combine validator (v/validations->fn validations))
                    validator)]
    (validator values)))