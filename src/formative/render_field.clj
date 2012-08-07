(ns formative.render-field
  (:require [hiccup.core :refer [h]]))

(defmulti render-field (fn [field]
                         (:type field)))

(defn render-default-input [field]
  (let [attrs (select-keys field [:type :name :id :class :value :autofocus
                                  :checked :disabled :href :style :src :size
                                  :readonly :tabindex :onchange :onclick
                                  :onfocus :onblur :placeholder :autofill])
        attrs (if (:type attrs)
                attrs
                (assoc attrs :type :text))
        attrs (if (and (= :submit (:type attrs))
                       (empty? (:value attrs)))
                (dissoc attrs :value)
                attrs)]
    [:input attrs]))

(defmethod render-field :default [field]
  (render-default-input field))

(defmethod render-field :textarea [field]
  (let [attrs (select-keys field [:name :id :class :autofocus
                                  :disabled :style :size :rows :cols :wrap
                                  :readonly :tabindex :onchange :onclick
                                  :onfocus :onblur :placeholder])]
    [:textarea attrs (h (:value field))]))

(defn normalize-options [opts]
  (if (coll? (first opts))
    (if (map? (first opts))
      (map (juxt :value :label) opts)
      opts)
    (map #(vector % %) opts)))

(defmethod render-field :select [field]
  (let [attrs (select-keys field [:name :id :class :autofocus
                                  :disabled :multiple :size :readonly
                                  :tabindex :onchange :onclick :onfocus
                                  :onblur])
        val (str (:value field))
        opts (normalize-options (:options field))]
    [:select attrs
     (for [[v text] opts
           :let [v (str v)]]
       [:option {:value v :selected (= val v)} text])]))

(defmethod render-field :checkbox [field]
  (list
   (when (contains? field :unchecked-value)
     (render-default-input {:name (:name field)
                            :type :hidden
                            :value (:unchecked-value field)}))
   (render-default-input field)))

(defmethod render-field :html [field]
  (:html field))

(defmethod render-field :heading [field]
  [:h3 (:text field)])
