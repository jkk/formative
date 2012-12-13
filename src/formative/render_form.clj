(ns formative.render-form)

(defmulti render-form* (fn [form-attrs fields]
                         (:renderer form-attrs)))