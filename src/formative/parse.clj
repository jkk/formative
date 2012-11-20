(ns formative.parse
  (:require [sundry.num :refer [parse-long]]
            [ring.middleware.nested-params :as np]
            [clojure.string :as string]))

(defn throw-problem
  ([spec value]
    (throw-problem spec value "%s is not valid"))
  ([spec value msg]
    (throw (ex-info (format msg (name (:name spec)))
                    {:value value
                     :problems [{:field-name (:name spec)
                                 :spec spec
                                 :msg msg}]}))))

(defmulti parse-input (fn [spec v]
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

(defmethod parse-input :float [_ v]
  (try (Double/valueOf v) (catch Exception _)))

(defmethod parse-input :double [_ v]
  (try (Double/valueOf v) (catch Exception _)))

(defmethod parse-input :decimal [_ v]
  (try (BigDecimal. v) (catch Exception _)))

(defmethod parse-input :date [spec v]
  (when-not (string/blank? v)
    (try
      (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd") v)
      (catch Exception e
        (throw-problem spec v "%s is not a valid date")))))

(defmethod parse-input :file [spec v]
  (when-not (:upload-handler spec)
    (throw (IllegalStateException.
             (str "Missing :upload-handler for " (:name spec)))))
  (if (string/blank? (:filename v))
    ::absent
    ((:upload-handler spec) spec v)))

;; TODO: more types

(defn- get-param [m kw]
  (get m (name kw) (get m kw)))

(defn- fix-input [input spec]
  (if (and (= :checkboxes (:type spec))
           (= "" (first input)))
    (rest input)
    input))

;; TODO: doc
(defn parse-params [fields params]
  (let [;; FIXME: Should probably not rely on a private Ring fn (shhh)
        input (#'np/nest-params params
                                np/parse-nested-keys)]
    (reduce
      (fn [row spec]
        (let [fname (:name spec)]
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
  [req form-fn & body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (if-let [problems# (:problems (ex-data e#))]
         (~form-fn ~req :problems problems#)
         (throw e#)))))