(ns formative.parse
  (:require [ring.middleware.nested-params :as np]
            [clojure.string :as string]
            [clojure.walk :refer [stringify-keys]]
            [formative.core :as f]
            [formative.validate :as fv]
            [formative.util :as fu]))

(defrecord ParseError [bad-value])

(defn parse-error? [x]
  (instance? ParseError x))

(defmulti parse-input
  "Parses the value for a particular field specification, dispatching on
  :datatype if present, otherwise :type. Takes [field-spec value] as
  arguments."
  (fn [spec v]
    (:datatype spec (:type spec))))

(defmethod parse-input :default [_ v]
  v)

(defn- parse-long [spec x]
  (when-not (string/blank? x)
    (try
      (Long/valueOf x)
      (catch Exception _
        (->ParseError x)))))

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
        (->ParseError x)))))

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
        (->ParseError x)))))

(defmethod parse-input :decimal [spec v]
  (parse-bigdec spec v))

(defmethod parse-input :decimals [spec v]
  (map #(parse-bigdec spec %) v))

(defn- parse-bigint [spec x]
  (when-not (string/blank? x)
    (try
      (bigint (BigInteger. x))
      (catch Exception _
        (->ParseError x)))))

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
        (->ParseError x)))))

(defmethod parse-input :date [spec v]
  (parse-date spec v))

(defmethod parse-input :dates [spec v]
  (map #(parse-date spec %) v))

(defmethod parse-input :date-text [spec v]
  (parse-date spec v))

(defmethod parse-input :date-texts [spec v]
  (map #(parse-date spec %) v))

(defmethod parse-input :date-select [spec v]
  (when (every? (comp (complement string/blank?) #(get v %))
                ["year" "month" "day"])
    (try
      (java.util.Date. (- (Integer/valueOf (get v "year")) 1900)
                       (dec (Integer/valueOf (get v "month")))
                       (Integer/valueOf (get v "day")))
      (catch Exception e
        (->ParseError v)))))

(defmethod parse-input :year-select [spec v]
  (parse-long spec v))

(defmethod parse-input :month-select [spec v]
  (parse-long spec v))

(defmethod parse-input :currency [spec v]
  (parse-bigdec spec v))

(defn- parse-file [spec x]
  (cond
    (or (nil? x) (and (map? x)
                      (contains? x :filename)
                      (string/blank? (:filename x)))) ::absent
    (:upload-handler spec) ((:upload-handler spec) spec x)
    :else x))

(defmethod parse-input :file [spec v]
  (parse-file spec v))

(defmethod parse-input :files [spec v]
  (map #(parse-file spec %) v))

(defmethod parse-input :heading [spec v]
  ::absent)

(defmethod parse-input :html [spec v]
  ::absent)

(defmethod parse-input :labeled-html [spec v]
  ::absent)

(defmethod parse-input :us-tel [spec v]
  (fu/normalize-us-tel v))

(defn- contains-in? [m keys]
  (loop [m m, keys keys]
    (if (seq keys)
      (let [k (first keys)
            v (get m k ::not-found)]
        (when (not= ::not-found v)
          (recur v (rest keys))))
      m)))

(defn- get-param [m ks]
  (get-in m ks (get-in m (map keyword ks))))

(defn- fix-input [input spec]
  (if (and (= :checkboxes (:type spec))
           (= "" (first input)))
    (rest input)
    input))

(defn- parse-nested-params [fields np]
  (reduce
    (fn [vals spec]
      (let [sname (fu/expand-name (:name spec))
            kname (map keyword sname)]
        (if (or (contains-in? np sname)
                (contains-in? np kname))
          (let [raw-val (fix-input
                          (get-param np sname) spec)
                val (parse-input spec raw-val)]
            (if (= val ::absent)
              vals
              (assoc-in vals kname val)))
          vals)))
    {}
    fields))

(defn- normalize-params [params]
  (if (keyword? (key (first params)))
    (stringify-keys params)
    ;; FIXME: Should probably not rely on a private Ring fn (shhh)
    (#'np/nest-params params np/parse-nested-keys)))

(defn parse-params
  "Given a form specification or sequence of field specifications and a Ring
  params map, returns a map of field names to parsed values.

  The Ring params map must be either 1) an untouched :query-params,
  :form-params, or :multipart-params map; or 2) a params map with the following
  middleware applied: wrap-params, wrap-nested-params, wrap-keyword-params.

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
        nested-params (normalize-params params)
        values (parse-nested-params fields nested-params)
        problems (when validate
                   (if form
                     (fv/validate form values)
                     (fv/validate-types fields values)))]
    (if (seq problems)
      (throw (ex-info "Problem parsing params" {:problems problems}))
      values)))

(defn parse-request
  "Given a form specification or sequence of field specifications and a Ring
  request, returns a map of form field names to parsed values. Checks
  :multipart-params first, then :form-params, then :query-params."
  [form-or-fields req]
  (parse-params form-or-fields
                (cond
                  (seq (:multipart-params req)) (:multipart-params req)
                  (seq (:form-params req)) (:form-params req)
                  :else (:query-params req))))

;;;;

(defmacro with-fallback
  "Attempts to run body; if an ExceptionInfo with a :problems key is caught,
  calls fallback-fn with the problems as the argument."
  [fallback-fn & body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (if-let [problems# (:problems (ex-data e#))]
         (~fallback-fn problems#)
         (throw e#)))))