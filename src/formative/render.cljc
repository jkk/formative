(ns formative.render
  (:require [formative.data :as data]
            [formative.util :as fu]
            [clojure.string :as string]
            #?(:cljs [goog.string :as gstring]
               :cljs goog.string.format)))

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

(defn- ucfirst [s]
  #?(:clj (str (Character/toUpperCase (.charAt ^String s 0)) (subs ^String s 1))
     :cljs (str (.toUpperCase (.charAt s 0)) (subs s 1))))

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
        (string/replace #"^[^\[]+\[([^\]])" "$1")
        (string/replace #"[\[\]\.]" " ")
        (string/replace #"\bid\b" "ID")
        ucfirst
        string/trim)))

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

(defmulti render-input-val
  "Renders the value of an input field as a string, if it's not already."
  (fn [field]
    (:datatype field (:type field))))

(defmethod render-input-val :default [field]
  (let [v (:value field)]
    (if (string? v)
      v
      (if (sequential? v)
        (string/join ", " (map str v))
        (str v)))))

(defmethod render-input-val :date [field]
  (if-let [date (fu/normalize-date (:value field) (:date-format field))]
    (fu/format-date date (:date-format field))
    ""))

(defmethod render-input-val :dates [field]
  (let [vs (fu/seqify-value (:value field))]
    (string/join ", " (for [v vs]
                        (when-let [date (fu/normalize-date v (:date-format field))]
                          (fu/format-date date (:date-format field)))))))

(defmethod render-input-val :time [field]
  (if-let [time (fu/normalize-time (:value field))]
    (fu/format-time time)
    ""))

(defmethod render-input-val :times [field]
  (let [vs (fu/seqify-value (:value field))]
    (string/join ", " (for [v vs]
                        (when-let [time (fu/normalize-time v)]
                          (fu/format-time time))))))

(defn render-default-input [field & [opts]]
  (let [attrs (get-input-attrs field [:type :name :id :class :value :autofocus
                                      :checked :disabled :href :style :src :size
                                      :readonly :tabindex :onchange :onclick
                                      :onfocus :onblur :placeholder :autofill
                                      :multiple :title])
        attrs (if (and (= :submit (:type attrs))
                       (empty? (:value attrs)))
                (dissoc attrs :value)
                (assoc attrs :value (render-input-val field)))
        attrs (assoc attrs :type (name (or (:type attrs) :text)))]
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
    [:textarea attrs (fu/escape-html (render-input-val field))]))

(defn ^:private build-opt-tag [v text val]
  (let [v (str v)]
    [:option {:value v :selected (= val v)} text]))

(defmethod render-field :select [field]
  (let [attrs (get-input-attrs field [:name :id :class :autofocus
                                      :disabled :multiple :size :readonly
                                      :tabindex :onchange :onclick :onfocus
                                      :onblur])
        val (render-input-val field)
        opts (fu/normalize-options (:options field))
        opts (if (:first-option field)
               (concat (fu/normalize-options [(:first-option field)])
                       opts)
               opts)
        opt-tags (for [[v text subopts] opts]
                   (if (empty? subopts)
                     (build-opt-tag v text val)
                     [:optgroup {:label text}
                      (for [[v text] (fu/normalize-options subopts)]
                        (build-opt-tag v text val))]))
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
        opts (fu/normalize-options (:options field))
        fname (str (name (:name field)) "[]")
        cols (:cols field 1)
        cb-per-col (+ (quot (count opts) cols)
                      (if (zero? (rem (count opts) cols))
                        0 1))
        build-cb (fn [oval olabel]
                   (let [id (str (:id field) "__" (opt-slug oval))]
                     [:div.cb-shell
                      [:label.checkbox {:for id} " "
                       [:span.cb-input-shell
                        (render-field {:name fname :id id :checked (contains? vals (str oval))
                                       :type :checkbox :value (str oval)})] " "
                       [:span.cb-label [:nobr olabel]]]]))]
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
        (for [[oval olabel subopts] colopts]
          (if (empty? subopts)
            (build-cb oval olabel)
            [:div.cb-group
             [:h5.cb-group-heading olabel]
             (for [[oval olabel] (fu/normalize-options subopts)]
               (build-cb oval olabel))]))])]))

(defn- render-radios [field]
  (let [val (str (:value field))
        opts (fu/normalize-options (:options field))
        build-radio (fn [oval olabel]
                      (let [id (str (:id field) "__" (opt-slug oval))]
                        [:div.radio-shell
                         [:label.radio {:for id}
                          [:span.radio-input-shell
                           (render-default-input {:name (:name field) :id id
                                                  :type :radio
                                                  :checked (= val (str oval))
                                                  :value oval})]
                          " "
                          [:span.radio-label [:nobr olabel]]]]))]
    [:div.radios
     (for [[oval olabel subopts] opts]
       (if (empty? subopts)
         (build-radio oval olabel)
         [:div.radio-group
          [:h5.radio-group-heading olabel]
          (for [[oval olabel] (fu/normalize-options subopts)]
            (build-radio oval olabel))]))]))

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

(defmethod render-field :date [field]
  (let [date (fu/normalize-date (:value field) (:date-format field))]
    (render-default-input
     (assoc field :value
            (when date
              (fu/format-date date (:date-format field "yyyy-MM-dd")))))))

(defmethod render-field :date-text [field]
  (let [date (fu/normalize-date (:value field) (:date-format field))]
    (render-default-input
     (assoc field
            :type :text
            :value (when date
                     (fu/format-date date (:date-format field "yyyy-MM-dd")))))))

(defmethod render-field :compound [field]
  (let [subfields (for [subfield (:fields field)]
                    (let [sfname (str (:name field) "[" (:name subfield) "]")]
                      (assoc subfield
                             :name sfname
                             :id (fu/get-field-id {:name sfname}))))
        combiner (or (:combiner field)
                     (fn [subfields]
                       [:span.compound
                        (interpose (:separator field " ") subfields)]))]
    (combiner (map render-field subfields))))

(defmethod render-field :date-select [field]
  (let [date (fu/normalize-date (:value field) nil (:timezone field))
        [year month day] (when date
                           (fu/get-year-month-day date))
        this-year (fu/get-this-year)
        year-start (:year-start field this-year)
        year-end (:year-end field (+ this-year 20))]
    [:span.date-select
     (render-field {:name (:name field)
                    :type :compound
                    :separator " "
                    :fields [{:type :select
                              :name "month"
                              :class "input-medium"
                              :value month
                              :options (cons ["" "Month"]
                                             (map vector
                                                  (range 1 13)
                                                  (fu/get-month-names)))}
                             {:type :select
                              :name "day"
                              :class "input-small"
                              :value day
                              :options (cons ["" "Day"]
                                             (map #(vector % %) (range 1 32)))}
                             {:type :select
                              :name "year"
                              :class "input-small"
                              :value year
                              :options (cons ["" "Year"]
                                             (map #(vector % %)
                                                  (range year-start (inc year-end))))}]})]))

(defmethod render-field :year-select [field]
  (let [this-year (fu/get-this-year)
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
                    (fu/get-month-names)))]
    [:div.month-select
     (render-field (assoc field
                          :class (str (:class field) " input-medium")
                          :type :select
                          :options opts))]))

(defn- round [x step]
  (int (* (Math/floor (/ x (double step)) ) step)))

(defmethod render-field :time [field]
  (let [time (fu/normalize-time (:value field))]
    (render-default-input
     (assoc field :value
            (when time
              (fu/format-time time))))))

(defn- get-hour+ampm [h ampm?]
  (when h
    (if ampm?
      (cond
        (zero? h) [12 "am"]
        (= 12 h) [12 "pm"]
        (< 12 h) [(- h 12) "pm"]
        :else [h "am"])
      [h])))

(defn- format-minutes [m]
  (#?(:clj format :cljs gstring/format) "%02d" m))

(defn- render-time-select-multi [fname h m s step ampm? seconds?]
  (let [[h ampm] (get-hour+ampm h ampm?)]
    (list
     (render-field {:type :select
                    :name (str fname "[h]")
                    :class "input-small"
                    :value h
                    :first-option ["" "--"]
                    :options (if ampm? (range 1 13) (range 0 24))})
     " "
     (render-field {:type :select
                    :name (str fname "[m]")
                    :class "input-small"
                    :value m
                    :first-option ["" "--"]
                    :options (map (juxt identity format-minutes)
                                  (range 0 60 step))})
     (when seconds?
       (list
        " "
        (render-field {:type :select
                       :name (str fname "[s]")
                       :class "input-small"
                       :value s
                       :first-option ["" "--"]
                       :options (map (juxt identity format-minutes)
                                     (range 0 60 step))})))
     (when ampm?
       (list
        " "
        (render-field {:type :select
                       :name (str fname "[ampm]")
                       :class "input-small"
                       :value ampm
                       :first-option ["" "--"]
                       :options ["am" "pm"]}))))))

(defn- add-minutes [h m mx]
  (let [m* (+ m mx)]
    (if (< 59 m*)
      [(inc h) (- m* 60)] ;assumes never adding more than 60
      [h m*])))

(defn- time-range [start [eh em] step]
  (take-while (fn [[h m]]
                (or (< h eh)
                    (and (= h eh) (<= m em))))
              (iterate (fn [[h m]] (add-minutes h m step))
                       start)))

(defn- format-time [h m ampm?]
  (let [[h ampm] (get-hour+ampm h ampm?)]
    (str h ":"
         (format-minutes m)
         (when ampm? (str " " ampm)))))

(defn- render-time-select-single [field h m step ampm? start end]
  (let [start (or (fu/normalize-time start)
                  (fu/normalize-time {:h 0 :m 0}))
        end (or (fu/normalize-time end)
                (fu/normalize-time {:h 23 :m 59}))
        opts (for [[h m] (time-range (fu/get-hours-minutes-seconds start)
                                     (fu/get-hours-minutes-seconds end)
                                     step)]
               [(str h ":" (format-minutes m))
                (format-time h m ampm?)])]
    (render-field (assoc field
                         :type :select
                         :value (str h ":" (format-minutes m))
                         :options opts))))

(defmethod render-field :time-select [field]
  (let [step (:step field 5)
        ampm? (:ampm field true)
        time (fu/normalize-time (:value field))
        [h m s] (when time
                  (fu/get-hours-minutes-seconds time))
        m (when m (round m step))
        s (when s (round s step))
        seconds? (:seconds field false)]
    [:span.time-select
     (if (:compact field)
       (render-time-select-single field h m step ampm? (:start field) (:end field))
       (render-time-select-multi (:name field) h m s step ampm? seconds?))]))

(defmethod render-field :datetime-select [field]
  [:span.datetime-select
   (render-field (assoc field :type :date-select))
   " "
   (render-field (assoc field :type :time-select :compact false))])

(defmethod render-field :currency [field]
  (render-default-input
   (assoc field :type :text)
   {:prefix "$"}))

(defmethod render-field :us-tel [field]
  (render-default-input
   (assoc field :type :tel :value (fu/format-us-tel (:value field)))))
