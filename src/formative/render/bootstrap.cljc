(ns formative.render.bootstrap
  (:require [formative.render :refer [render-form render-field
                                      render-problems]]
            [formative.util :as util]))

(defn render-bootstrap-row [field]
  (let [field-id (util/get-field-id field)
        field (assoc field :id field-id)
        field (if (= :submit (:type field))
                (assoc field :class (str (:class field)
                                         " btn btn-primary"))
                field)]
    [:div {:id (util/get-field-container-id field)
           :class (str (if (= :submit (:type field))
                         "form-actions submit-group "
                         "field-group ")
                       (when-not (#{:heading :html} (:type field))
                         " control-group ")
                       (name (:type field :text)) "-row"
                       (when (:problem field) " error problem"))}
     (if (= :heading (:type field))
       [:legend (render-field field)]
       (list
        [:div {:class (if (#{:checkbox :submit} (:type field))
                        "empty-shell"
                        "label-shell")}
         (when (and (not (#{:checkbox} (:type field))) (:label field))
           [:label.control-label {:for field-id}
            (:label field)])]
        [:div {:class (str "input-shell" (when-not (#{:submit :html} (:type field))
                                           " controls"))}
         (when (:prefix field)
           [:span.prefix (:prefix field)])
         (if (= :checkbox (:type field))
           [:label.checkbox {:for field-id} " "
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
           [:div.note.help-inline (:note field)])]))]))

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

(defn render-bootstrap-form [form-attrs fields class opts]
  (let [[hidden-fields visible-fields] ((juxt filter remove)
                                        #(= :hidden (:type %)) fields)
        submit-only? (and (= 1 (count visible-fields))
                          (= :submit (:type (first visible-fields))))
        shell-attrs {:class (str class
                                 " bootstrap-form"
                                 (when submit-only? " submit-only"))}
        shell-attrs (if (:id form-attrs)
                      (assoc shell-attrs :id (str (name (:id form-attrs))
                                                  "-shell"))
                      shell-attrs)]
    [:div shell-attrs
     (when-let [problems (:problems opts)]
       (when (map? (first problems))
         (render-problems problems fields)))
     [:form (dissoc form-attrs :renderer)
      (list
       (map render-field hidden-fields)
       (for [fieldset (group-fieldsets visible-fields)]
         [:fieldset {:class (str "fieldset-" (name (:name (first fieldset))))}
          (map render-bootstrap-row fieldset)]))]]))

(defmethod render-form :bootstrap-horizontal [form-attrs fields opts]
  (render-bootstrap-form form-attrs fields "form-shell form-horizontal" opts))

(defmethod render-form :bootstrap-stacked [form-attrs fields opts]
  (render-bootstrap-form form-attrs fields "form-shell" opts))
