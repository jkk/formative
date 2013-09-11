(ns formative.render.div
  (:require [formative.render :refer [render-form render-field
                                      render-problems]]
            [formative.util :as util]))

(defn render-form-row [field]
  (let [field-id (util/get-field-id field)
        field (assoc field :id field-id)]
    [:div {:id (util/get-field-container-id field)
           :class (str (if (= :submit (:type field))
                         "submit-group "
                         "field-group ")
                       (name (:type field :text)) "-row"
                       (when (:problem field) " problem"))}
     (if (= :heading (:type field))
       [:legend (render-field field)]
       (list
         [:div {:class (if (#{:checkbox :submit} (:type field))
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
            [:span.cancel-link " " [:a {:href (:cancel-href field)} (:cancel-label field)]])
          (when (:note field)
            [:div.note (:note field)])]))]))

(defmethod render-form :div [form-attrs fields opts]
  (let [[hidden-fields visible-fields] ((juxt filter remove)
                                        #(= :hidden (:type %)) fields)
        submit-only? (and (= 1 (count visible-fields))
                          (= :submit (:type (first visible-fields))))
        shell-attrs {:class (str "form-shell" (when submit-only? " submit-only"))}
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
       [:fieldset
        (map render-form-row visible-fields)])]]))

