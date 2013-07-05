(ns formative.util
  (:require [clojure.string :as string]
            [clj-time.core :as ct]
            [clj-time.coerce :as cc]
            [clj-time.format :as cf])
  (:import org.joda.time.DateTime))

(defn normalize-options [opts]
  (let [opts (cond
               (fn? opts) (opts)
               (delay? opts) @opts
               :else opts)]
    (if (coll? (first opts))
      (if (map? (first opts))
        (map (juxt :value :label) opts)
        opts)
      (map #(vector % %) opts))))

(defn parse-date [s & [format]]
  (cf/parse (cf/formatter (or format "yyyy-MM-dd"))
            s))

(defn to-timezone [d timezone]
  (if timezone
    (let [timezone (if (string? timezone)
                      (ct/time-zone-for-id timezone)
                      timezone)]
      (ct/to-time-zone d timezone))
    d))

(defn from-timezone [d timezone]
  (if timezone
    (let [timezone (if (string? timezone)
                      (ct/time-zone-for-id timezone)
                      timezone)]
      (ct/from-time-zone d timezone))
    d))

(defn normalize-date [d & [format timezone]]
  (when d
    (let [d (cond
              (instance? org.joda.time.DateTime d) d
              (instance? java.util.Date d) (cc/from-date d)
              (integer? d) (cc/from-long d)
              (string? d) (try
                            (parse-date d format)
                            (catch Exception _))
              (map? d) (try
                         (let [year (Integer/valueOf (:year d (get d "year")))
                               month (Integer/valueOf (:month d (get d "month")))
                               day (Integer/valueOf (:day d (get d "day")))]
                           (ct/date-time year month day))
                         (catch Exception _))
              :else (throw (ex-info "Unrecognized date format" {:date d})))]
      (to-timezone d timezone))))

(defn format-date [^DateTime d & [format]]
  (cf/unparse (cf/with-zone (cf/formatter (or format "yyyy-MM-dd")) (.getZone d))
              d))

(defn get-year-month-day [date]
  [(ct/year date)
   (ct/month date)
   (ct/day date)])

(defn parse-time [s]
  (try
    (cf/parse (cf/formatter "H:m") s)
    (catch Exception _
      (cf/parse (cf/formatter "H:m:s") s))))

(defn with-time [^DateTime datetime h m s]
  (.withTime datetime h m s 0))

(defn normalize-time [t]
  (when t
    (cond
      (instance? org.joda.time.DateTime t) t
      (instance? java.util.Date t) (cc/from-date t)
      (string? t) (try
                    (parse-time t)
                    (catch Exception _))
      (map? t) (let [h (Integer/valueOf (:h t (get t "h")))
                     ampm (:ampm t (get t "ampm"))
                     h (if ampm
                         (cond
                           (= 12 h) (if (= "am" ampm) 0 12)
                           (= "pm" ampm) (+ h 12)
                           :else h)
                         h)
                     m (Integer/valueOf (:m t (get t "m" 0)))
                     s (Integer/valueOf (:s t (get t "s" 0)))]
                 (with-time (ct/epoch) h m s))
      :else (throw (ex-info "Unrecognized time format" {:time t})))))

(defn format-time [^DateTime t]
  (cf/unparse (cf/with-zone (cf/formatter "H:mm") (.getZone t))
              t))

(defn get-hours-minutes-seconds [date]
  [(ct/hour date)
   (ct/minute date)
   (ct/sec date)])

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