(ns formative.render
  (:require [hiccup.core :refer [h]]
            [formative.data :as data]
            [formative.util :as fu]
            [clojure.string :as string]))

(defmulti render-form
  "Renders a form, dispatching on :renderer in form-attrs. Can return any
  representation of a form that the implementor chooses - Hiccup data, string,
  etc.

  This multimethod exists as an extension mechanism for anyone that wants to
  build their own form renderer. Library consumers should call
  formative.core/render-form.

  Arguments:
      form-attrs - HTML attributes of the form, plus a :renderer key
      fields     - normalized, prepared fields to include in the form
      opts       - the full form specification with all keys untouched"
  (fn [form-attrs fields opts]
    (:renderer form-attrs)))

(defn- ucfirst [^String s]
  (str (Character/toUpperCase (.charAt s 0)) (subs s 1)))

(defn get-field-label
  "Determines what to use for a field's label. Uses the :label key if set,
  otherwise renders :name as a string - e.g., :foo-bar => \"Foo Bar\"."
  [field]
  (if (contains? field :label)
    (if (string? (:label field))
      (ucfirst (:label field))
      (:label field))
    (-> (:name field)
      name
      (string/replace #"[_-]" " ")
      (string/replace #"\bid\b" "ID")
      ucfirst)))

(defn render-problems
  "Renders a form problems as Hiccup data. Lists the each set of keys with
  their corresponding message."
  [problems & [fields]]
  (let [problems (if (map? problems) [problems] problems)
        fields-by-name (if (map? fields)
                         fields
                         (into {} (for [f fields]
                                    [(:name f) f])))]
    [:div.form-problems.alert.alert-error.alert-block.clearfix
     [:ul
      (for [{:keys [keys msg]} problems
            :when msg]
        (let [field-labels (map #(get-field-label
                                   (or (fields-by-name %)
                                       (fields-by-name (name %))
                                       {:name %}))
                                keys)]
          [:li
           (when (seq field-labels)
             (list [:strong (string/join ", " field-labels)] ": "))
           msg]))]]))

(defmulti render-field
  "Render a field as Hiccup data. Dispatches on :type"
  (fn [field]
    (:type field)))

(defn- get-input-attrs [field allowed-keys]
  (let [data-keys (filter #(re-find #"^data-" (name %))
                          (keys field))]
    (select-keys field (concat allowed-keys data-keys))))

(defn render-default-input [field & [opts]]
  (let [attrs (get-input-attrs field [:type :name :id :class :value :autofocus
                                      :checked :disabled :href :style :src :size
                                      :readonly :tabindex :onchange :onclick
                                      :onfocus :onblur :placeholder :autofill
                                      :multiple :title])
        attrs (if (:type attrs)
                attrs
                (assoc attrs :type :text))
        attrs (if (and (= :submit (:type attrs))
                       (empty? (:value attrs)))
                (dissoc attrs :value)
                (assoc attrs :value (str (:value attrs))))]
    (list
      (when-let [prefix (:prefix opts)]
        [:span.input-prefix prefix])
      [:input attrs])))

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
        opts (normalize-options (:options field))
        opts (if (:first-option field)
               (concat (normalize-options [(:first-option field)])
                       opts)
               opts)
        opt-tags (for [[v text] opts
                       :let [v (str v)]]
                   [:option {:value (str v) :selected (= val (str v))} text])
        placeholder (if (true? (:placeholder field))
                      "Select one..."
                      (:placeholder field))
        opt-tags (if (and placeholder (string/blank? val))
                   (cons [:option {:value "" :disabled true :selected true}
                          placeholder]
                         opt-tags)
                   opt-tags)]
    [:select attrs opt-tags]))

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
             [:label.checkbox {:for id} " "
              [:span.cb-input-shell
               (render-field {:name fname :id id :checked (contains? vals (str oval))
                              :type :checkbox :value (str oval)})] " "
              [:span.cb-label [:nobr olabel]]]]))])]))

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

(defmethod render-field :labeled-html [field]
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

(defn- normalize-date-val [d & [format]]
  (when d
    (cond
      (instance? java.util.Date d) d
      (integer? d) (java.util.Date. d)
      (string? d) (try
                    (.parse (java.text.SimpleDateFormat.
                              (or format "yyyy-MM-dd"))
                      d)
                    (catch Exception _))
      (map? d) (try
                 (let [year (- (Integer/valueOf (:year d (get d "year"))) 1900)
                       month (dec (Integer/valueOf (:month d (get d "month"))))
                       day (Integer/valueOf (:month d (get d "day")))]
                   (java.util.Date. year month day))
                 (catch Exception _))
      :else (throw (IllegalArgumentException. "Unrecognized date format")))))

(defmethod render-field :date [field]
  (let [date (normalize-date-val (:value field) (:date-format field))]
    (render-default-input
      (assoc field :value
             (when date
               (.format (java.text.SimpleDateFormat.
                          (:date-format field "yyyy-MM-dd"))
                 date))))))

(defmethod render-field :date-text [field]
  (let [date (normalize-date-val (:value field) (:date-format field))]
    (render-default-input
      (assoc field
             :type :text
             :value (when date
                      (.format (java.text.SimpleDateFormat.
                                 (:date-format field "yyyy-MM-dd"))
                        date))))))

(defmethod render-field :date-select [field]
  (let [date (normalize-date-val (:value field))
        [year month day] (when date
                           [(+ 1900 (.getYear date))
                            (inc (.getMonth date))
                            (.getDate date)])
        this-year (+ 1900 (.getYear (java.util.Date.)))
        year-start (:year-start field this-year)
        year-end (:year-end field (+ this-year 20))]
    [:div.date-select
     (render-field {:type :select
                    :name (str (:name field) "[month]")
                    :class "input-medium"
                    :value month
                    :options (cons ["" "Month"]
                                   (map vector
                                        (range 1 13)
                                        (.getMonths (java.text.DateFormatSymbols.))))})
     " "
     (render-field {:type :select
                    :name (str (:name field) "[day]")
                    :class "input-small"
                    :value day
                    :options (cons ["" "Day"]
                                   (map #(vector % %) (range 1 32)))})
     " "
     (render-field {:type :select
                    :name (str (:name field) "[year]")
                    :class "input-small"
                    :value year
                    :options (cons ["" "Year"]
                                 (map #(vector % %)
                                      (range year-start (inc year-end))))})]))

(defmethod render-field :year-select [field]
  (let [this-year (+ 1900 (.getYear (java.util.Date.)))
        start (:start field this-year)
        end (:end field (+ this-year 20))]
    [:div.year-select
     (render-field (assoc field
                          :class (str (:class field) " input-small")
                          :type :select
                          :options (range start (inc end))))]))

(defmethod render-field :month-select [field]
  (let [opts (if (:numbers field)
               (range 1 13)
               (map vector
                    (range 1 13)
                    (.getMonths (java.text.DateFormatSymbols.))))]
    [:div.month-select
     (render-field (assoc field
                          :class (str (:class field) " input-medium")
                          :type :select
                          :options opts))]))

(defmethod render-field :currency [field]
  (render-default-input
    (assoc field :type :text)
    {:prefix "$"}))

(defmethod render-field :us-tel [field]
  (render-default-input
    (assoc field :type :tel :value (fu/format-us-tel (:value field)))))