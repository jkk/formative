(ns formative.render.bootstrap3
  (:require [formative.render :refer [render-form render-field
                                      get-field-label]]
            [formative.util :as util]
            [clojure.string :as string]))

(defn render-bootstrap-row [field]
  (let [field-id (util/get-field-id field)
        checkbox? (= :checkbox (:type field))
        field (assoc field
                     :id field-id
                     :class (if checkbox? "" "form-control"))
        field (if (= :submit (:type field))
                (assoc field :class (str (:class field)
                                         " btn btn-primary"))
                field)]

    [:div {:id (util/get-field-container-id field)
           :class (str (if checkbox? "checkbox " "form-group ")
                       (:div-class field)
                       (when (:problem field) " has-error problem " ))}
     (if (= :heading (:type field))
       (when (:text field) [:legend (render-field field)])
       (list
         (when (and (not checkbox?) (:label field))
           [:label.control-label {:for field-id} (:label field)])
         (when (:prefix field)
            [:span.prefix (:prefix field)])
          (if checkbox?
            [:label {:for field-id} " "
             (render-field field) " "
             [:span.cb-label (:label field)]]
            (render-field field))
          (when (:suffix field)
            [:span.suffix (:suffix field)])
          (when (and (= :submit (:type field))
                     (:cancel-href field))
            [:span.cancel-link " " [:a.btn {:href (:cancel-href field)}
                                    (:cancel-label field)]])
          (when (:note field)
            [:div.note.help-inline (:note field)])))]))

(defn- group-fieldsets [fields]
  (loop [ret []
         group []
         fields fields]
    (if (empty? fields)
      (if (seq group)
        (conj ret group)
        ret)
      (if (#{:heading :submit} (:type (first fields)))
        (recur (if (seq group) (conj ret group) ret)
               [(first fields)]
               (rest fields))
        (recur ret
               (conj group (first fields))
               (rest fields))))))

(defn render-problems
  "Renders a form problems as Hiccup data. Lists the each set of keys with
  their corresponding message."
  [problems & [fields]]
  (let [problems (if (map? problems) [problems] problems)
        fields-by-name (if (map? fields)
                         fields
                         (into {} (for [f fields]
                                    [(:name f) f])))]
    [:div.form-problems.alert-error.alert-block.clearfix.has-error
     [:ul
      (for [{:keys [keys msg]} problems
            :when msg]
        (let [field-labels (map #(get-field-label
                                   (or (fields-by-name %)
                                       (fields-by-name (name %))
                                       {:name %}))
                                keys)]
          [:li.control-label
           (when (seq field-labels)
             (list [:strong (string/join ", " field-labels)] ": "))
           msg]))]]))

(defn render-bootstrap-form [form-attrs fields class opts]
  (let [[hidden-fields visible-fields] ((juxt filter remove)
                                        #(= :hidden (:type %)) fields)
        submit-only? (and (= 1 (count visible-fields))
                          (= :submit (:type (first visible-fields))))
        extra-attrs {:class (str class
                                 (when submit-only? " submit-only"))}
        form-attrs (merge-with #(str %1 " " %2) form-attrs extra-attrs)]

    [:form (dissoc form-attrs :renderer)
      (when-let [problems (:problems opts)]
       (when (map? (first problems))
         (render-problems problems fields)))
      (list
       (map render-field hidden-fields)
       (for [fieldset (group-fieldsets visible-fields)]
         [:fieldset {:class (str "fieldset-" (name (:name (first fieldset))))}
          (map render-bootstrap-row fieldset)]))]))

(defmethod render-form :bootstrap3-stacked [form-attrs fields opts]
  (render-bootstrap-form form-attrs fields "form-shell" opts))

;; Broken
#_(defmethod render-form :bootstrap3-horizontal [form-attrs fields opts]
   (render-bootstrap-form form-attrs fields "form-shell form-horizontal" opts))
