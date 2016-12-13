(ns formative.render.inline
  (:require [formative.render :refer [render-form render-field
                                      render-problems]]
            [formative.util :as util]))

(defn render-form-item [field]
  (let [field-id (util/get-field-id field)
        field (assoc field :id field-id)]
    [:span {:id (util/get-field-container-id field)
            :class (str (name (:type field :text)) "-field"
                        (when (:problem field) " problem"))}
     (if (= :heading (:type field))
       (render-field field)
       [:span.input-shell
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
          [:span.note (:note field)])])]))

(defmethod render-form :inline [form-attrs fields opts]
  (let [[hidden-fields visible-fields] ((juxt filter remove)
                                        #(= :hidden (:type %)) fields)
        submit-only? (and (= 1 (count visible-fields))
                          (= :submit (:type (first visible-fields))))
        shell-attrs {:class (str "form-shell inline-form"
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
       [:div.inline-fields
        (map render-form-item visible-fields)])]]))
