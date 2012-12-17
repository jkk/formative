(ns formative.render-form)

(defmulti render-form* (fn [form-attrs fields opts]
                         (:renderer form-attrs)))