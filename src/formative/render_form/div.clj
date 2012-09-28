(ns formative.render-form.div
  (:require [formative.render-form :refer [render-form*]]
            [formative.render-field :refer [render-field]]))

(def ^:dynamic *field-prefix* "field-")

(defn render-form-row [field]
  (let [field-id (if (:id field)
                   (name (:id field))
                   (str *field-prefix* (:name field)))
        field (assoc field :id field-id)]
    [:div {:id (str "row-" field-id)
           :class (str (if (= :submit (:type field))
                         "submit-group "
                         "field-group ")
                       (name (:type field :text)) "-row"
                       (when (:problem field) " problem"))}
     (if (= :heading (:type field))
       [:legend (render-field field)]
       (list
         [:div.label-shell {:class (if (#{:checkbox :submit} (:type field))
                                     "empty-cell"
                                     "label-cell")}
          (when (and (not (#{:checkbox} (:type field))) (:label field))
            [:label {:for field-id}
             (:label field)])]
         [:div.input-shell
          (when (:prefix field)
            [:span.prefix (:prefix field)])
          (render-field field)
          (when (= :checkbox (:type field))
            [:label {:for field-id} " " [:span.cb-label (:label field)]])
          (when (:suffix field)
            [:span.suffix (:suffix field)])
          (when (and (= :submit (:type field)) (:cancel-href field))
            [:span.cancel-link " " [:a {:href (:cancel-href field)} "Cancel"]])
          (when (:note field)
            [:div.note (:note field)])]))]))

(defmethod render-form* :div [form-attrs fields]
  (let [[hidden-fields visible-fields] ((juxt filter remove)
                                        #(= :hidden (:type %)) fields)
        submit-only? (and (= 1 (count fields))
                          (= :submit (:type (first fields))))]
    [:div {:class (str "form-shell" (when submit-only? " submit-only"))}
     [:form (dissoc form-attrs :type)
      (list
       (map render-field hidden-fields)
       [:fieldset
        (map render-form-row visible-fields)])]]))

