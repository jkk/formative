(ns formative.render-form.table
  (:require [formative.render-form :refer [render-form*]]
            [formative.render-field :refer [render-field]]
            [formative.helpers :refer [render-problems]]))

(def ^:dynamic *field-prefix* "field-")

(defn render-form-row [field]
  (let [field-id (if (:id field)
                   (name (:id field))
                   (str *field-prefix* (:name field)))
        field (assoc field :id field-id)
        label? (and (not (false? (:label field)))
                    (not= :html (:type field)))]
    [:tr {:id (str "row-" field-id)
          :class (str (name (:type field :text)) "-row"
                      (when (:problem field) " problem"))}
     (if (= :heading (:type field))
       [:th.heading-cell {:colspan 2} (render-field field)]
       (list
        (when label?
          [:th {:class (if (#{:checkbox :submit} (:type field))
                         "empty-cell"
                         "label-cell")}
           (when (and (not (#{:checkbox} (:type field))) (:label field))
             [:label {:for field-id}
              (:label field)])])
        [:td.input-cell {:colspan (if label? 1 2)} 
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

(defmethod render-form* :table [form-attrs fields opts]
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
       [:table.form-table
        (map render-form-row visible-fields)])]]))

