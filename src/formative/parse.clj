(ns formative.parse
  (:require [ring.middleware.nested-params :as np]
            [clojure.string :as string]
            [formative.core :as f]
            [formative.validate :as fv]))

(defrecord ParseError [])

(defmulti parse-input
  "Parses the value for a particular field specification"
  (fn [spec v]
    (:datatype spec (:type spec))))

(defmethod parse-input :default [_ v]
  v)

(defn- parse-long [spec x]
  (when-not (string/blank? x)
    (try
      (Long/valueOf x)
      (catch Exception _
        (ParseError.)))))

(defmethod parse-input :int [spec v]
  (parse-long spec v))

(defmethod parse-input :ints [spec v]
  (map #(parse-long spec %) v))

(defmethod parse-input :long [spec v]
  (parse-long spec v))

(defmethod parse-input :longs [spec v]
  (map #(parse-long spec %) v))

(defmethod parse-input :boolean [_ v]
  (Boolean/valueOf v))

(defmethod parse-input :booleans [_ v]
  (map #(Boolean/valueOf %) v))

(defn- parse-double [spec x]
  (when-not (string/blank? x)
    (try
      (Double/valueOf x)
      (catch Exception _
        (ParseError.)))))

(defmethod parse-input :float [spec v]
  (parse-double spec v))

(defmethod parse-input :floats [spec v]
  (map #(parse-double spec %) v))

(defmethod parse-input :double [spec v]
  (parse-double spec v))

(defmethod parse-input :doubles [spec v]
  (map #(parse-double spec %) v))

(defn- parse-bigdec [spec x]
  (when-not (string/blank? x)
    (try
      (BigDecimal. x)
      (catch Exception _
        (ParseError.)))))

(defmethod parse-input :decimal [spec v]
  (parse-bigdec spec v))

(defmethod parse-input :decimals [spec v]
  (map #(parse-bigdec spec %) v))

(defn- parse-bigint [spec x]
  (when-not (string/blank? x)
    (try
      (bigint (BigInteger. x))
      (catch Exception _
        (ParseError.)))))

(defmethod parse-input :bigint [spec v]
  (parse-bigint spec v))

(defmethod parse-input :bigints [spec v]
  (map #(parse-bigint spec %) v))

(defn- parse-date [spec x]
  (when-not (string/blank? x)
    (try
      (.parse (java.text.SimpleDateFormat.
                (:date-format spec "yyyy-MM-dd"))
        x)
      (catch Exception e
        (ParseError.)))))

(defmethod parse-input :date [spec v]
  (parse-date spec v))

(defmethod parse-input :dates [spec v]
  (map #(parse-date spec %) v))

(defmethod parse-input :date-select [spec v]
  (when (every? (comp (complement string/blank?) #(get v %))
                ["year" "month" "day"])
    (try
      (java.util.Date. (- (Integer/valueOf (get v "year")) 1900)
                       (dec (Integer/valueOf (get v "month")))
                       (Integer/valueOf (get v "day")))
      (catch Exception e
        (ParseError.)))))

(defmethod parse-input :year-select [spec v]
  (parse-long spec v))

(defmethod parse-input :month-select [spec v]
  (parse-long spec v))

(defmethod parse-input :currency [spec v]
  (parse-bigdec spec v))

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

(defn get-parse-errors [values]
  (for [[k v] values
        :when (instance? ParseError v)]
    {:keys [k]
     :msg (:msg v)}))

(defn- parse-nested-params [fields np]
  (reduce
    (fn [vals spec]
      (let [fname (keyword (:name spec))]
        (if (or (contains? np (name fname))
                (contains? np fname))
          (let [raw-val (fix-input
                          (get-param np fname) spec)
                spec* (assoc spec :name fname)
                val (parse-input spec* raw-val)]
            (if (= val ::absent)
              vals
              (assoc vals fname val)))
          vals)))
    {}
    fields))

(defn parse-params
  "Given a form specification or sequence of field specifications and a Ring
  :form-params or :query-params map, returns a map of field names to parsed
  values.

  Parsed values will be validated and an exception will be thrown if validation
  fails. The exception carries a :problems key with details about the validation
  failure.

  Keyword options:
    :validate - set to false to not validate parsed values"
  [form-or-fields params & {:keys [validate] :or {validate true}}]
  (let [[form fields] (if (map? form-or-fields)
                        [form-or-fields (:fields form-or-fields)]
                        [nil form-or-fields])
        fields (f/prep-fields fields {})
        ;; FIXME: Should probably not rely on a private Ring fn (shhh)
        nested-params (#'np/nest-params params
                                        np/parse-nested-keys)
        values (parse-nested-params fields nested-params)
        problems (when (and form validate)
                   (fv/validate form values))]
    (if (seq problems)
      (throw (ex-info "Problem parsing params" {:problems problems}))
      values)))

(defn parse-request
  "Given a form specification or sequence of field specifications and a Ring
  request, returns a map of form field names to parsed values. Checks
  :form-params first, then :query-params."
  [form-or-fields req]
  (parse-params form-or-fields
                (if (seq (:form-params req))
                  (:form-params req)
                  (:query-params req))))

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