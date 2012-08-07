(ns formative.render-form)

(defmulti render-form* (fn [form-attrs fields]
                         (:form-style form-attrs)))