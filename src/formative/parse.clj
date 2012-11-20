(ns formative.parse
  (:require [sundry.num :refer [parse-long parse-double]]
            [ring.middleware.nested-params :as np]
            [clojure.string :as string]
            [formative.core :as f]))

(defn throw-problem
  "Creates and throws an exception carrying information about a failed field
  parse"
  ([spec value]
    (throw-problem spec value "%s is not valid"))
  ([spec value msg]
    (throw (ex-info (format msg (name (:name spec)))
                    {:value value
                     :problems [{:field-name (:name spec)
                                 :spec spec
                                 :msg msg}]}))))

(defmulti parse-input
  "Parses the value for a particular field specification"
  (fn [spec v]
    (:datatype spec (:type spec))))

(defmethod parse-input :default [_ v]
  v)

(defmethod parse-input :int [_ v]
  (parse-long v))

(defmethod parse-input :ints [_ v]
  (map parse-long v))

(defmethod parse-input :long [_ v]
  (parse-long v))

(defmethod parse-input :longs [_ v]
  (map parse-long v))

(defmethod parse-input :boolean [_ v]
  (Boolean/valueOf v))

(defmethod parse-input :booleans [_ v]
  (map #(Boolean/valueOf %) v))

(defmethod parse-input :float [_ v]
  (parse-double v))

(defmethod parse-input :floats [_ v]
  (map parse-double v))

(defmethod parse-input :double [_ v]
  (parse-double v))

(defmethod parse-input :doubles [_ v]
  (map parse-double v))

(defn- parse-bigdec [x]
  (try (BigDecimal. x) (catch Exception _)))

(defmethod parse-input :decimal [_ v]
  (parse-bigdec v))

(defmethod parse-input :decimals [_ v]
  (map parse-bigdec v))

(defn- parse-bigint [x]
  (try (bigint (BigInteger. x)) (catch Exception _)))

(defmethod parse-input :bigint [_ v]
  (parse-bigint v))

(defmethod parse-input :bigints [_ v]
  (map parse-bigint v))

(defn- parse-date [spec x]
  (when-not (string/blank? x)
    (try
      (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd") x)
      (catch Exception e
        (throw-problem spec x "%s is not a valid date")))))

(defmethod parse-input :date [spec v]
  (parse-date spec v))

(defmethod parse-input :dates [spec v]
  (map #(parse-date spec %) v))

(defn- parse-file [spec x]
  (when-not (:upload-handler spec)
    (throw (IllegalStateException.
             (str "Missing :upload-handler for " (:name spec)))))
  (if (string/blank? (:filename x))
    ::absent
    ((:upload-handler spec) spec x)))

(defmethod parse-input :file [spec v]
  (parse-file spec v))

(defmethod parse-input :files [spec v]
  (map #(parse-file spec %) v))

;; TODO: more types

(defn- get-param [m kw]
  (get m (name kw) (get m kw)))

(defn- fix-input [input spec]
  (if (and (= :checkboxes (:type spec))
           (= "" (first input)))
    (rest input)
    input))

(defn parse-params
  "Given a sequence of field specifications and a Ring params map,
  returns a map of field names to parsed values."
  [fields params]
  (let [fields (f/prep-fields fields {})
        ;; FIXME: Should probably not rely on a private Ring fn (shhh)
        input (#'np/nest-params params
                                np/parse-nested-keys)]
    (reduce
      (fn [row spec]
        (let [fname (keyword (:name spec))]
          (if (or (contains? input (name fname))
                  (contains? input fname))
            (let [raw-val (fix-input
                            (get-param input fname) spec)
                  spec* (assoc spec :name fname)
                  val (parse-input spec* raw-val)]
              (if (= val ::absent)
                row
                (assoc row fname val)))
            row)))
      {}
      fields)))

;;;;

(defmacro with-fallback
  "Attempts to run body; if an ExceptionInfo with field problems is caught,
  calls form-fn with a :problems keyword argument containing the problem
  payload."
  [req form-fn & body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (if-let [problems# (:problems (ex-data e#))]
         (~form-fn ~req :problems problems#)
         (throw e#)))))