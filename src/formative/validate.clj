(ns formative.validate
  (:require [formative.data :as data]
            [clojure.string :as string]))

(defn make-validator [keys bad-pred msg]
  (fn [m]
    (let [bad-keys (filter #(bad-pred (get m %)) keys)]
      (when (seq bad-keys)
        {:keys bad-keys :msg msg}))))

(defn contains [& keys]
  (make-validator keys (complement contains?) "must be present"))

(defn not-blank [& keys]
  (make-validator keys string/blank? "must not be blank"))

(defn equal [& keys]
  (fn [m]
    (when-not (apply = (map #(get m %) keys))
      {:keys keys :msg "must be equal"})))

(defn matches [re & keys]
  (make-validator
    keys #(and (not (string/blank? %))
               (not (re-matches re %)))
    "incorrect format"))

(defn min-length [len & keys]
  (make-validator
    keys #(and (not (nil? %))
               (not (<= len (count %))))
    (str "must be at least " len " characters")))

(defn max-length [len & keys]
  (make-validator
    keys #(and (not (nil? %))
               (not (>= len (count %))))
    (str "cannot exceed " len " characters")))

(def ^:private zip-regex #"^\d{5}(?:[-\s]\d{4})?$")

(defn us-zip [& keys]
  (make-validator keys #(and (not (string/blank? %))
                             (not (re-matches zip-regex %)))
                  "must be a valid US zip code"))

(def ^:private us-states (set (filter (complement string/blank?)
                                      (map first data/us-states))))

(defn us-state [& keys]
  (make-validator keys #(and (not (string/blank? %))
                             (not (us-states %)))
                  "must be a valid US state"))

(def ^:private ca-states (set (filter (complement string/blank?)
                                      (map first data/ca-states))))

(defn ca-state [& keys]
  (make-validator keys #(and (not (string/blank? %))
                             (not (ca-states %)))
                  "must be a valid Canadian state"))

(def ^:private alpha2-countries (set (keep :alpha2 data/countries)))

(defn country [& keys]
  (make-validator keys #(and (not (string/blank? %))
                             (not (alpha2-countries %)))
                  "must be a valid country"))

(defn email [& keys]
  (make-validator
    keys #(and (not (string/blank? %))
               (not (re-matches #"[^^]+@[^$]+" %))) ;RFC be damned
    "must be a valid email"))

(defn bool [& keys]
  (make-validator
    keys #(and (not (nil? %)) (not (true? %)) (not (false? %)))
    "must be true or false"))

(defn bools [& keys]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(and (not (true? %)) (not (false? %))) v)))
    "must be all true or false"))

(defn integer [& keys]
  (make-validator
    keys #(and (not (nil? %)) (not (integer? %)))
    "must be an integer"))

(defn integers [& keys]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(not (integer? %)) v)))
    "must be integers"))

(defn floating-point [& keys]
  (make-validator
    keys #(and (not (nil? %)) (not (float? %)))
    "must be a floating point number"))

(defn floating-points [& keys]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(not (float? %)) v)))
    "must be floating point numbers"))

(defn decimal [& keys]
  (make-validator
    keys #(and (not (nil? %)) (not (decimal? %)))
    "must be a decimal number"))

(defn decimals [& keys]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(not (decimal? %)) v)))
    "must be decimals"))

(defn min-val [min & keys]
  (make-validator
    keys #(and (number? %) (> min %))
    (str "cannot be less than " min)))

(def at-least min-val)

(defn max-val [max & keys]
  (make-validator
    keys #(and (number? %) (< max %))
    (str "cannot be more than " max)))

(def at-most max-val)

(defn within [min max & keys]
  (make-validator
    keys #(and (number? %) (or (> min %) (< max %)))
    (str "must be within " min " and " max)))

(defn date [& keys]
  (make-validator
    keys #(and (not (nil? %)) (not (instance? java.util.Date %)))
    "must be a date"))

(defn dates [& keys]
  (make-validator
    keys (fn [v]
           (or (and (not (nil? v)) (not (sequential? v)))
               (some #(not (instance? java.util.Date %)) v)))
    "must be dates"))

(defn after [^java.util.Date date & keys]
  (make-validator
    keys #(and (not (nil? %)) (not (.after ^java.util.Date % date)))
    (str "must be after " date)))

(defn before [^java.util.Date date & keys]
  (make-validator
    keys #(and (not (nil? %)) (not (.before ^java.util.Date % date)))
    (str "must be before " date)))

(defn seqify [x]
  (if-not (sequential? x) [x] x))

(defn combine [& validators]
  (fn [m]
    (apply concat (map seqify (keep #(% m) validators)))))

(def type-validators
  {:us-zip us-zip
   :us-state us-state
   :ca-state ca-state
   :country country
   :email email
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
                    type-validator)]
    (validator values)))