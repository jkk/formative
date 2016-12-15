(ns formative.parse
  (:require [clojure.string :as string]
            [clojure.walk :refer [stringify-keys]]
            [formative.core :as f]
            [formative.validate :as fv]
            [formative.util :as fu]
            #?(:cljs [cljs.reader :as reader])))

(defrecord ParseError [bad-value])

(defn parse-error? [x]
  (instance? ParseError x))

(defmulti parse-input
  "Parses the value for a particular field specification, dispatching on
  :datatype if present, otherwise :type. Takes [field-spec value] as
  arguments."
  (fn [spec v]
    (:datatype spec (:type spec))))

(defmethod parse-input :default [spec v]
  (when-not (and (string? v) (:blank-nil spec) (string/blank? v))
    v))

(defn- parse-long [spec x]
  (when-not (string/blank? x)
    (try
      (fu/parse-int x)
      (catch #?(:clj Exception :cljs js/Error) _
          (->ParseError x)))))

(defmethod parse-input :int [spec v]
  (parse-long spec v))

(defmethod parse-input :ints [spec v]
  (map #(parse-long spec %) (fu/seqify-value v)))

(defmethod parse-input :long [spec v]
  (parse-long spec v))

(defmethod parse-input :longs [spec v]
  (map #(parse-long spec %) (fu/seqify-value v)))

(defmethod parse-input :boolean [_ v]
  #?(:clj (Boolean/valueOf v)
     :cljs (contains? #{"true" "on"} v)))

(defmethod parse-input :booleans [_ v]
  (map #?(:clj #(Boolean/valueOf %) :cljs #(contains? #{"true" "on"} %))
       (fu/seqify-value v)))

(defn- parse-double [spec x]
  (when-not (string/blank? x)
    (try
      #?(:clj (Double/valueOf x) :cljs (js/parseFloat x))
      (catch #?(:clj Exception :cljs js/Error) _
          (->ParseError x)))))

(defmethod parse-input :float [spec v]
  (parse-double spec v))

(defmethod parse-input :floats [spec v]
  (map #(parse-double spec %) (fu/seqify-value v)))

(defmethod parse-input :double [spec v]
  (parse-double spec v))

(defmethod parse-input :doubles [spec v]
  (map #(parse-double spec %) (fu/seqify-value v)))

(defn- parse-bigdec [spec x]
  (when-not (string/blank? x)
    (try
      #?(:clj (BigDecimal. x) :cljs x)
      (catch #?(:clj Exception :cljs js/Error) _
          (->ParseError x)))))

(defmethod parse-input :decimal [spec v]
  (parse-bigdec spec v))

(defmethod parse-input :decimals [spec v]
  (map #(parse-bigdec spec %) (fu/seqify-value v)))

(defn- parse-bigint [spec x]
  (when-not (string/blank? x)
    (try
      #?(:clj (bigint (BigInteger. x)) :cljs x)
      (catch #?(:clj Exception :cljs js/Error) _
          (->ParseError x)))))

(defmethod parse-input :bigint [spec v]
  (parse-bigint spec v))

(defmethod parse-input :bigints [spec v]
  (map #(parse-bigint spec %) (fu/seqify-value v)))

(defn- parse-date [spec x]
  (when-not (string/blank? x)
    (try
      (fu/to-date (fu/parse-date x (:date-format spec)))
      (catch #?(:clj Exception :cljs js/Error) e
          (->ParseError x)))))

(defmethod parse-input :date [spec v]
  (parse-date spec v))

(defmethod parse-input :dates [spec v]
  (map #(parse-date spec %) (fu/seqify-value v)))

(defmethod parse-input :date-text [spec v]
  (parse-date spec v))

(defmethod parse-input :date-texts [spec v]
  (map #(parse-date spec %) (fu/seqify-value v)))

(defmethod parse-input :date-select [spec v]
  (when (every? (comp (complement string/blank?) #(get v %))
                ["year" "month" "day"])
    (try
      (fu/to-date (fu/normalize-date v))
      (catch #?(:clj Exception :cljs js/Error) e
          (->ParseError v)))))

(defmethod parse-input :year-select [spec v]
  (parse-long spec v))

(defmethod parse-input :month-select [spec v]
  (parse-long spec v))

(defn- parse-time [spec x]
  (when-not (string/blank? x)
    (try
      (fu/to-time (fu/normalize-time x))
      (catch #?(:clj Exception :cljs js/Error) e
          (->ParseError x)))))

(defmethod parse-input :time [spec v]
  (parse-time spec v))

(defmethod parse-input :times [spec v]
  (map #(parse-time spec %) (fu/seqify-value v)))

(defmethod parse-input :time-select [spec v]
  (if (:compact spec)
    (parse-time spec v)
    (when (every? (comp (complement string/blank?) #(get v %))
                  ["h" "m"])
      (try
        (fu/to-time (fu/normalize-time v))
        (catch #?(:clj Exception :cljs js/Error) e
            (->ParseError v))))))

(defn- parse-instant [spec x]
  (when-not (string/blank? x)
    (try
      #?(:clj (clojure.instant/read-instant-date x)
         :cljs (let [[years months days hours minutes seconds ms offset]
                     (reader/parse-and-validate-timestamp x)
                     offset (if (or (js/isNaN offset)
                                    (not (number? offset)))
                              0 offset)]
                 (js/Date.
                  (- (.UTC js/Date years (dec months) days hours minutes seconds ms)
                     (* offset 60 1000)))))
      (catch #?(:clj Exception :cljs js/Error) e
          (->ParseError x)))))

(defmethod parse-input :instant [spec v]
  (parse-instant spec v))

(defmethod parse-input :instants [spec v]
  (map #(parse-instant spec %) (fu/seqify-value v)))

(defmethod parse-input :datetime-select [spec v]
  (when (every? (comp (complement string/blank?) #(get v %))
                ["year" "month" "day"])
    (try
      (when-let [date (fu/normalize-date v)]
        (when (every? (comp (complement string/blank?) #(get v %))
                      ["h" "m"])
          (try
            (when-let [time (fu/normalize-time v)]
              (fu/to-date
               (fu/from-timezone
                (fu/with-time date
                  (fu/hour time)
                  (fu/minute time)
                  (fu/sec time))
                (:timezone spec))))
            (catch #?(:clj Exception :cljs js/Error) e
                (->ParseError v)))))
      (catch #?(:clj Exception :cljs js/Error) e
          (->ParseError v)))))

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

(defn- parse-nested-params [fields np & [absent-nil?]]
  (reduce
   (fn [vals spec]
     (let [sname (fu/expand-name (:name spec))
           kname (mapv keyword sname)]
       (if (or (contains-in? np sname)
               (contains-in? np kname))
         (let [raw-val (fix-input
                        (get-param np sname) spec)
               val (parse-input spec raw-val)]
           (if (= val ::absent)
             (if absent-nil? (assoc-in vals kname nil) vals)
             (if (and (map? val) (:flatten spec))
               (let [ktip (name (peek kname))
                     kbase (pop kname)]
                 (reduce-kv
                  (fn [vals k v]
                    (assoc-in vals (conj kbase (keyword (str ktip "-" (name k)))) v))
                  vals val))
               (assoc-in vals kname val))))
         (if absent-nil?
           (assoc-in vals kname nil)
           vals))))
   {}
   fields))

(defmethod parse-input :compound [spec v]
  (try
    (parse-nested-params (:fields spec) v true)
    (catch #?(:clj Exception :cljs js/Error) e
        (->ParseError v))))

(defn- normalize-params [params]
  (when (seq params)
    (let [params (if (string? params)
                   (fu/decode-form-data params)
                   params)]
      (if (keyword? (ffirst params))
        (stringify-keys params)
        (:params (fu/nested-params-request {:params params}))))))

(defn parse-params
  "Given a form specification or sequence of field specifications and a Ring
  params map or form data string, returns a map of field names to parsed values.

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
        fields (f/prep-fields fields {} form)
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

#?(:clj
   (defmacro with-fallback
     "Attempts to run body; if an ExceptionInfo with a :problems key is caught,
  calls fallback-fn with the problems as the argument."
     [fallback-fn & body]
     `(try
        ~@body
        (catch clojure.lang.ExceptionInfo e#
          (if-let [problems# (:problems (ex-data e#))]
            (~fallback-fn problems#)
            (throw e#))))))
