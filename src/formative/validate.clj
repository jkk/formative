(ns formative.validate
  (:require [formative.data :as data]
            [clojure.string :as string]))

(defn seqify [x]
  (if-not (sequential? x) [x] x))

(defn make-validator [keys bad-pred msg]
  (fn [m]
    (let [bad-keys (filter #(bad-pred (get m %))
                           (seqify keys))]
      (when (seq bad-keys)
        (if (map? msg)
          msg
          {:keys bad-keys :msg msg})))))

(defn contains [keys & [msg]]
  (make-validator keys (complement contains?)
                  (or msg "must be present")))

(defn required [keys & [msg]]
  (make-validator keys #(or (nil? %) (and (string? %) (string/blank? %)))
                  (or msg "must not be blank")))

(defn exact [val keys & [msg]]
  (make-validator keys #(or (nil? %) (not= val %))
                  (or msg "incorrect value")))

(defn equal [keys & [msg]]
  (let [keys (seqify keys)]
    (fn [m]
      (when-not (apply = (map #(get m %) keys))
        (if (map? msg)
          msg
          {:keys keys :msg (or msg "must be equal")})))))

(defn matches [re keys & [msg]]
  (make-validator
    keys #(and (not (string/blank? %))
               (not (re-matches re %)))
    (or msg "incorrect format")))

(defn min-length [len keys & [msg]]
  (make-validator
    keys #(and (not (nil? %))
               (not (<= len (count %))))
    (or msg (str "must be at least " len " characters"))))

(defn max-length [len keys & [msg]]
  (make-validator
    keys #(and (not (nil? %))
               (not (>= len (count %))))
    (or msg (str "cannot exceed " len " characters"))))

(defn in [coll keys & [msg]]
  (let [coll-set (if (set? coll)
                   coll (set coll))]
    (make-validator
      keys #(and (not (nil? %))
                 (not (contains? coll-set %)))
      (or msg (str "not an accepted value")))))

(def ^:private zip-regex #"^\d{5}(?:[-\s]\d{4})?$")

(defn us-zip [keys & [msg]]
  (make-validator keys #(and (not (string/blank? %))
                             (not (re-matches zip-regex %)))
                  (or msg "must be a valid US zip code")))

(def ^:private us-states (set (filter (complement string/blank?)
                                      (map first data/us-states))))

(defn us-state [keys & [msg]]
  (make-validator keys #(and (not (string/blank? %))
                             (not (us-states %)))
                  (or msg "must be a valid US state")))

(def ^:private ca-states (set (filter (complement string/blank?)
                                      (map first data/ca-states))))

(defn ca-state [keys & [msg]]
  (make-validator keys #(and (not (string/blank? %))
                             (not (ca-states %)))
                  (or msg "must be a valid Canadian state")))

(def ^:private alpha2-countries (set (keep :alpha2 data/countries)))

(defn country [keys & [msg]]
  (make-validator keys #(and (not (string/blank? %))
                             (not (alpha2-countries %)))
                  (or msg "must be a valid country")))

(defn email [keys & [msg]]
  (make-validator
    keys #(and (not (string/blank? %))
               (not (re-matches #"[^^]+@[^$]+" %))) ;RFC be damned
    (or msg "must be a valid email")))

(defn string [keys & [msg]]
  (make-validator
    keys #(and (not (nil? %)) (not (string? %)))
    (or msg "must be a string")))

(defn strings [keys & [msg]]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(not (string? %)) v)))
    (or msg "must be strings")))

(defn bool [keys & [msg]]
  (make-validator
    keys #(and (not (nil? %)) (not (true? %)) (not (false? %)))
    (or msg "must be true or false")))

(defn bools [keys & [msg]]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(and (not (true? %)) (not (false? %))) v)))
    (or msg "must be all true or false")))

(defn integer [keys & [msg]]
  (make-validator
    keys #(and (not (nil? %)) (not (integer? %)))
    (or msg "must be a number")))

(defn integers [keys & [msg]]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(not (integer? %)) v)))
    (or msg "must be numbers")))

(defn floating-point [keys & [msg]]
  (make-validator
    keys #(and (not (nil? %)) (not (float? %)))
    (or msg "must be a decimal number")))

(defn floating-points [keys & [msg]]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(not (float? %)) v)))
    (or msg "must be decimal numbers")))

(defn decimal [keys & [msg]]
  (make-validator
    keys #(and (not (nil? %)) (not (decimal? %)))
    (or msg "must be a decimal number")))

(defn decimals [keys & [msg]]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(not (decimal? %)) v)))
    (or msg "must be decimal numbers")))

(defn min-val [min keys & [msg]]
  (make-validator
    keys #(and (number? %) (> min %))
    (or msg (str "cannot be less than " min))))

(def at-least min-val)

(defn max-val [max keys & [msg]]
  (make-validator
    keys #(and (number? %) (< max %))
    (or msg (str "cannot be more than " max))))

(def at-most max-val)

(defn within [min max keys & [msg]]
  (make-validator
    keys #(and (number? %) (or (> min %) (< max %)))
    (or msg (str "must be within " min " and " max))))

(defn date [keys & [msg]]
  (make-validator
    keys #(and (not (nil? %)) (not (instance? java.util.Date %)))
    (or msg "must be a date")))

(defn dates [keys & [msg]]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(not (instance? java.util.Date %)) v)))
    (or msg "must be dates")))

(defn after [^java.util.Date date keys & [msg]]
  (make-validator
    keys #(and (not (nil? %)) (not (.after ^java.util.Date % date)))
    (or msg (str "must be after " date))))

(defn before [^java.util.Date date keys & [msg]]
  (make-validator
    keys #(and (not (nil? %)) (not (.before ^java.util.Date % date)))
    (or msg (str "must be before " date))))

(defn combine [& validators]
  (fn [m]
    (apply concat (map seqify (keep #(% m) validators)))))

(def validations-map
  {:contains contains
   :required required
   :exact exact
   :equal equal
   :matches matches
   :min-length min-length
   :max-length max-length
   :in in
   :us-zip us-zip
   :us-state us-state
   :ca-state ca-state
   :country country
   :email email
   :str string
   :string string
   :strs strings
   :strings strings
   :bool bool
   :boolean bool
   :bools bools
   :booleans bools
   :integer integer
   :integers integers
   :floating-point floating-point
   :floating-points floating-points
   :float floating-point
   :floats floating-points
   :decimal decimal
   :decimals decimals
   :min-val min-val
   :at-least at-least
   :max-val max-val
   :at-most at-most
   :within within
   :date date
   :after after
   :before before})

(defmulti validation->fn (fn [vspec] (first vspec)))

(defmethod validation->fn :default [vspec]
  (if-let [vfn (get validations-map (first vspec))]
    (apply vfn (rest vspec))
    (throw (IllegalArgumentException.
             (str "Unknown validation " (first vspec))))))

(defn validations->fn [validations]
  (apply combine (map validation->fn validations)))

(def type-validators
  {:us-zip us-zip
   :us-state us-state
   :ca-state ca-state
   :country country
   :email email
   :str string
   :strs strings
   :boolean bool
   :booleans bools
   :int integer
   :long integer
   :bigint integer
   :ints integers
   :longs integers
   :bigints integers
   :float floating-point
   :double floating-point
   :floats floating-points
   :doubles floating-points
   :decimal decimal
   :decimals decimals
   :date date
   :dates dates})

(defn validate-types [fields values]
  (let [groups (group-by #(:datatype % (:type %))
                         fields)
        validators (for [[type tfields] groups
                         :let [validator (type-validators type)]
                         :when validator]
                     (apply validator (map :name tfields)))]
    ((apply combine validators) values)))

(defn validate [form values]
  (let [type-validator (partial validate-types (:fields form))
        validator (if-let [custom-validator (:validator form)]
                    (combine type-validator custom-validator)
                    type-validator)
        validator (if-let [validations (:validations form)]
                    (combine validator (validations->fn validations))
                    validator)]
    (validator values)))