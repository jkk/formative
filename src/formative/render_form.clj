(ns formative.render-form)

(defmulti render-form* (fn [form-attrs fields]
                         (:type form-attrs)))