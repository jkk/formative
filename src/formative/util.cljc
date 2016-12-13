(ns formative.util
  (:require [clojure.string :as string]
            #?@(:clj [[clj-time.core :as ct]
                      [clj-time.coerce :as cc]
                      [clj-time.format :as cf]])
            #?(:cljs [goog.string :as gstring]))
  (:import #?@(:clj [org.joda.time.DateTime
                     org.joda.time.LocalDate
                     org.joda.time.LocalTime])))

(defn normalize-options [opts]
  (let [opts (cond
               (fn? opts) (opts)
               (delay? opts) @opts
               :else opts)]
    (if (coll? (first opts))
      (if (map? (first opts))
        (map (juxt :value :label :options) opts)
        opts)
      (map #(vector % %) opts))))

(defn seqify-value [s & [split-re]]
  (cond
    (or (sequential? s) (nil? s)) s
    (string? s) (if (string/blank? s)
                  []
                  (string/split s (or split-re #"\s*,\s*")))
    (nil? s) s
    :else [s]))

(def default-date-format "yyyy-MM-dd")

#?(:clj
   (defn parse-date [s & [format]]
     (cf/parse (cf/formatter (or format default-date-format))
               s)))

(defn parse-int [x]
  #?(:clj (Long/valueOf x))
  #?(:cljs (let [x (js/parseInt x 10)]
             (when-not (integer? x)
               (throw (ex-info "Bad integer value" {:value x})))
             x)))

(defn utc-date [& [y m d h mm s]]
  #?(:clj (ct/date-time y m d (or h 0) (or mm 0) (or s 0)))
  #?(:cljs (js/Date. (.UTC js/Date y (dec m) d (or h 0) (or mm 0) (or s 0)))))

#?(:cljs
   (defn parse-date [s & [format]]
     (let [format (or format default-date-format)]
       (if (not= default-date-format format)
         (throw (ex-info (str "Only " default-date-format " format supported")
                         {:format format}))
         (let [[y m d] (map parse-int (string/split s #"-"))]
           (utc-date y m d))))))

(defn to-timezone [d timezone]
  #?(:clj (if timezone
            (let [timezone (if (string? timezone)
                             (ct/time-zone-for-id timezone)
                             timezone)]
              (ct/to-time-zone d timezone))
            d)
     :cljs d))

(defn from-timezone [d timezone]
  #?(:clj (if timezone
            (let [timezone (if (string? timezone)
                             (ct/time-zone-for-id timezone)
                             timezone)]
              (ct/from-time-zone d timezone))
            d)
     :cljs d))

#?(:clj
   (defn normalize-date [d & [format timezone]]
     (when d
       (let [d (cond
                 (instance? LocalDate d) (cc/to-date-time d)
                 (instance? DateTime d) d
                 (instance? java.util.Date d) (cc/from-date d)
                 (integer? d) (cc/from-long d)
                 (string? d) (try
                               (parse-date d format)
                               (catch Exception _))
                 (map? d) (try
                            (let [year (parse-int (:year d (get d "year")))
                                  month (parse-int (:month d (get d "month")))
                                  day (parse-int (:day d (get d "day")))]
                              (utc-date year month day))
                            (catch Exception _))
                 :else (throw (ex-info "Unrecognized date format" {:date d})))]
         (to-timezone d timezone))))

   :cljs
   (defn normalize-date [d & [format timezone]]
     (when d
       (cond
         (instance? js/Date d) d
         (integer? d) (js/Date. d)
         (string? d) (try
                       (parse-date d format)
                       (catch js/Error _))
         (map? d) (try
                    (let [year (parse-int (:year d (get d "year")))
                          month (parse-int (:month d (get d "month")))
                          day (parse-int (:day d (get d "day")))]
                      (utc-date year month day))
                    (catch js/Error _))
         :else (throw (ex-info "Unrecognized date format" {:date d}))))))

(defn to-date [d]
  #?(:clj (cc/to-date d))
  #?(:cljs d))

(defn get-year-month-day [date]
  #?(:clj [(ct/year date)
           (ct/month date)
           (ct/day date)])
  #?(:cljs [(.getUTCFullYear date)
            (inc (.getUTCMonth date))
            (.getUTCDate date)]))

(defn format-date [dt & [format]]
  (let [format (or format default-date-format)]
    #?(:clj (cf/unparse (cf/with-zone (cf/formatter format) (.getZone ^DateTime dt))
                        dt)
       :cljs (if (not= format default-date-format)
               (throw (ex-info (str "Only " default-date-format " format supported")
                               {:format format}))
               (let [[y m d] (get-year-month-day dt)]
                 (cljs.core/format "%d-%02d-%02d" y m d))))))

(defn epoch []
  #?(:clj (ct/epoch)
     :cljs (js/Date. 0)))

#?(:clj
   (defn parse-time [s]
     (try
       (cf/parse (cf/formatter "H:m") s)
       (catch Exception _
         (cf/parse (cf/formatter "H:m:s") s)))))

(defn with-time [datetime h mm s]
  #?(:clj (.withTime ^DateTime datetime h mm s 0)
     :cljs (let [[y m d] (get-year-month-day datetime)]
             (utc-date y m d h mm s))))

#?(:cljs
   (defn parse-time [s]
     (when-not (string/blank? s)
       (let [[h mm s] (map parse-int (string/split s #":"))]
         (when (integer? h)
           (with-time (epoch) h mm s))))))

(defn mktime [h m s]
  (with-time (epoch) h m s))

#?(:clj
   (defn normalize-time [t]
     (when t
       (cond
         (instance? LocalTime t) (.toDateTime ^LocalTime t (epoch))
         (instance? DateTime t) t
         (instance? java.util.Date t) (cc/from-date t)
         (string? t) (try
                       (parse-time t)
                       (catch Exception _))
         (map? t) (try
                    (let [h (parse-int (:h t (get t "h")))
                          ampm (:ampm t (get t "ampm"))
                          h (if ampm
                              (cond
                                (= 12 h) (if (= "am" ampm) 0 12)
                                (= "pm" ampm) (+ h 12)
                                :else h)
                              h)
                          m (parse-int (:m t (get t "m" 0)))
                          s (parse-int (:s t (get t "s" 0)))]
                      (mktime h m s))
                    (catch Exception _))
         (number? t) (mktime t 0 0)
         :else (throw (ex-info "Unrecognized time format" {:time t}))))))

#?(:cljs
   (defn normalize-time [t]
     (when t
       (cond
         (instance? js/Date t) t
         (integer? t) (js/Date. t)
         (string? t) (try
                       (parse-time t)
                       (catch js/Error _))
         (map? t) (try
                    (let [h (parse-int (:h t (get t "h")))
                          ampm (:ampm t (get t "ampm"))
                          h (if ampm
                              (cond
                                (= 12 h) (if (= "am" ampm) 0 12)
                                (= "pm" ampm) (+ h 12)
                                :else h)
                              h)
                          m (parse-int (:m t (get t "m" 0)))
                          s (parse-int (:s t (get t "s" 0)))]
                      (mktime h m s))
                    (catch js/Error _))
         (number? t) (mktime t 0 0)
         :else (throw (ex-info "Unrecognized time format" {:time t}))))))

(defn hour [date]
  #?(:clj (ct/hour date)
     :cljs (.getUTCHours date)))

(defn minute [date]
  #?(:clj (ct/minute date)
     :cljs (.getUTCMinutes date)))

(defn sec [date]
  #?(:clj (ct/sec date))
  #?(:cljs (.getUTCSeconds date)))

(defn get-hours-minutes-seconds [date]
  [(hour date)
   (minute date)
   (sec date)])

(defn format-time [t]
  #?(:clj (cf/unparse (cf/with-zone (cf/formatter "H:mm") (.getZone ^DateTime t))
                      t)
     :cljs (gstring/format "%02d:%02d" (hour t) (minute t))))

(defn to-time [date]
  #?(:clj (java.sql.Time. (cc/to-long date))
     :cljs date))

(defn get-this-year []
  #?(:clj (ct/year (ct/now))
     :cljs (.getUTCFullYear (js/Date.))))

(defn expand-name
  "Expands a name like \"foo[bar][baz]\" into [\"foo\" \"bar\" \"baz\"]"
  [name]
  (let [[_ name1 more-names] (re-matches #"^([^\[]+)((?:\[[^\]]+?\])*)$" name)]
    (if name1
      (if (seq more-names)
        (into [name1] (map second (re-seq #"\[([^\]]+)\]" more-names)))
        [name1])
      [name])))

(defn normalize-us-tel [v]
  (when-not (string/blank? v)
    (-> v
        (string/replace #"[^0-9x]+" "") ;only digits and "x" for extension
        (string/replace #"^1" "") ;remove leading 1
        )))

(defn valid-us-tel? [v]
  (and v (re-matches #"\d{10}(?:x\d+)?" (normalize-us-tel v))))

(defn format-us-tel [v]
  (if v
    (let [v* (normalize-us-tel (str v))]
      (if (valid-us-tel? v*)
        (let [[_ area prefix line ext] (re-find #"(\d\d\d)(\d\d\d)(\d\d\d\d)(?:x(\d+))?"
                                                v*)]
          (str "(" area ") " prefix "-" line
               (when ext (str " x" ext))))
        v))
    v))

(defn safe-element-id [id]
  (when id
    (string/replace id #"[^a-zA-Z0-9\-\_\:\.]" "__")))

(def ^:dynamic *field-prefix* "field-")

(defn get-field-id [field]
  (safe-element-id
   (if (:id field)
     (name (:id field))
     (str *field-prefix* (:name field)))))

(defn get-field-container-id [field]
  (str "row-" (:id field)))

(defn escape-html [s]
  (-> s
      (string/replace "&"  "&amp;")
      (string/replace "<"  "&lt;")
      (string/replace ">"  "&gt;")
      (string/replace "\"" "&quot;")))

(defn get-month-names []
  #?(:clj (.getMonths (java.text.DateFormatSymbols.))
     ;; TODO: i18n??
     :cljs ["January" "February" "March" "April" "May" "June" "July"
            "August" "September" "October" "November" "December"]))

(defn encode-uri-component [str]
  (if str
    #?(:clj (java.net.URLEncoder/encode ^String str "UTF-8")
       :cljs (js/encodeURIComponent str))
    ""))

(defn encode-uri-kv [k v]
  (str (encode-uri-component k) "=" (encode-uri-component v)))

(defn decode-uri-component [str]
  (if str
    #?(:clj (java.net.URLDecoder/decode ^String str "UTF-8")
       :cljs (js/decodeURIComponent str))
    ""))

(defn decode-form-data [data]
  (reduce
   (fn [m kv]
     (if-let [[k v] (string/split kv #"=")]
       (update-in m [(decode-uri-component k)]
                  (fn [oldv]
                    (if oldv
                      (if (vector? oldv)
                        (conj oldv (decode-uri-component v))
                        [oldv (decode-uri-component v)])
                      (decode-uri-component v))))
       m))
   {}
   (string/split data #"&")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Copied wholesale from ring.middleware.nested-params, for the sake of
;; ClojureScript support
;;

(defn parse-nested-keys
  "Parse a parameter name into a list of keys using a 'C'-like index
  notation. e.g.
    \"foo[bar][][baz]\"
    => [\"foo\" \"bar\" \"\" \"baz\"]"
  [param-name]
  (let [[_ k ks] (re-matches #"([^\[]*)((?:\[.*?\])*)" (name param-name))
        keys     (if ks (map second (re-seq #"\[(.*?)\]" ks)))]
    (cons k keys)))

(defn- assoc-nested
  "Similar to assoc-in, but treats values of blank keys as elements in a
  list."
  [m [k & ks] v]
  (conj m
        (if k
          (if-let [[j & js] ks]
            (if (= j "")
              {k (assoc-nested (get m k []) js v)}
              {k (assoc-nested (get m k {}) ks v)})
            {k v})
          v)))

(defn- param-pairs
  "Return a list of name-value pairs for a parameter map."
  [params]
  (mapcat
   (fn [[name value]]
     (if (sequential? value)
       (for [v value] [name v])
       [[name value]]))
   params))

(defn- nest-params
  "Takes a flat map of parameters and turns it into a nested map of
  parameters, using the function parse to split the parameter names
  into keys."
  [params parse]
  (reduce
   (fn [m [k v]]
     (assoc-nested m (parse k) v))
   {}
   (param-pairs params)))

(defn nested-params-request
  "Converts a request with a flat map of parameters to a nested map."
  [request & [opts]]
  (let [parse (:key-parser opts parse-nested-keys)]
    (update-in request [:params] nest-params parse)))
