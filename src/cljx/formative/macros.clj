(ns formative.macros)

;; ClojureScript macros

(defmacro with-fallback
  "Attempts to run body; if an ExceptionInfo with a :problems key is caught,
  calls fallback-fn with the problems as the argument."
  [fallback-fn & body]
  `(try
     ~@body
     (catch js/Error e#
       (if-let [problems# (:problems (ex-data e#))]
         (~fallback-fn problems#)
         (throw e#)))))