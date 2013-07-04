(ns formative.util
  (:require [clojure.string :as string]))

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

(defn normalize-time-val [t]
  (when t
    (cond
      (instance? java.sql.Time t) t
      (instance? java.util.Date t) t
      (string? t) (try
                    (.parse (java.text.SimpleDateFormat. "H:m") t)
                    (catch Exception _
                      (try
                        (.parse (java.text.SimpleDateFormat. "H:m:s") t)
                        (catch Exception _))))
      (map? t) (let [h (Integer/valueOf (:h t (get t "h")))
                     ampm (:ampm t (get t "ampm"))
                     h (if ampm
                         (cond
                           (= 12 h) (if (= "am" ampm) 0 12)
                           (= "pm" ampm) (- h 12)
                           :else h)
                         h)
                     m (Integer/valueOf (:m t (get t "m" 0)))
                     s (Integer/valueOf (:s t (get t "s" 0)))]
                 (java.sql.Time. h m s))
      :else (throw (IllegalArgumentException. "Unrecognized time format")))))

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