(ns formative.render-field
  (:require [hiccup.core :refer [h]]
            [formative.data :as data]
            [clojure.string :as string]))

(defmulti render-field (fn [field]
                         (:type field)))

(defn- get-input-attrs [field allowed-keys]
  (let [data-keys (filter #(re-find #"^data-" (name %))
                          (keys field))]
    (select-keys field (concat allowed-keys data-keys))))

(defn render-default-input [field]
  (let [attrs (get-input-attrs field [:type :name :id :class :value :autofocus
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
  (let [attrs (get-input-attrs field [:name :id :class :autofocus
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
  (let [attrs (get-input-attrs field [:name :id :class :autofocus
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

(defn- opt-slug [val]
  (-> (str val)
    (string/replace #"[^a-zA-Z0-9\-]" "-")
    (string/replace #"-{2,}" "-")))

(defmethod render-field :checkboxes [field]
  (let [vals (set (map str (:value field)))
        opts (normalize-options (:options field))
        fname (str (name (:name field)) "[]")
        cols (:cols field 1)
        cb-per-col (+ (quot (count opts) cols)
                      (if (zero? (rem (count opts) cols))
                        0 1))]
    [:div.checkboxes
     ;; FIXME: this prevents checkbox values from being absent in the submitted
     ;; request, but at the cost of including an empty value which must be
     ;; filtered out. We can't use an empty input without the "[]" suffix
     ;; because nested-params Ring middleware won't allow it.
     (render-field {:name fname :type :hidden})
     (for [[col colopts] (map vector
                              (range 1 (inc cols))
                              (partition-all cb-per-col opts))]
       [:div {:class (str "cb-col cb-col-" col)}
        (for [[oval olabel] colopts]
          (let [id (str (:id field) "__" (opt-slug oval))]
            [:div.cb-shell
             [:span.cb-input-shell
              (render-field {:name fname :id id :checked (contains? vals (str oval))
                             :type :checkbox :value oval})]
             " "
             [:label {:for id}
              [:nobr olabel]]]))])]))

(defn- render-radios [field]
  (let [val (str (:value field))
        opts (normalize-options (:options field))]
    [:div.radios
     (for [[oval olabel] opts]
       (let [id (str (:id field) "__" (opt-slug oval))]
         [:div.radio-shell
          [:span.radio-input-shell
           (render-default-input {:name (:name field) :id id
                                  :type :radio
                                  :checked (= val (str oval))
                                  :value oval})]
          " "
          [:label {:for id}
           [:nobr olabel]]]))]))

(defmethod render-field :radio [field]
  (render-radios field))

(defmethod render-field :radios [field]
  (render-radios field))

(defmethod render-field :html [field]
  (:html field))

(defmethod render-field :heading [field]
  [:h3 (:text field)])

(defmethod render-field :us-state [field]
  (render-field (assoc field
                       :type :select
                       :options data/us-states)))

(defmethod render-field :ca-state [field]
  (render-field (assoc field
                       :type :select
                       :options data/ca-states)))

(defmethod render-field :country [field]
  (render-field (assoc field
                       :type :select
                       :options (data/countries-by (or (:country-code field) :alpha2)))))

