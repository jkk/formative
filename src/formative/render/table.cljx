(ns formative.render.table
  (:require [formative.render :refer [render-form render-field
                                      render-problems]]
            [formative.util :as util]))

(defn render-form-row [field]
  (let [field-id (util/get-field-id field)
        field (assoc field :id field-id)
        label? (and (not (false? (:label field)))
                    (not= :html (:type field)))
        stacked? (= :stacked (:layout field))
        input-el [:div.input-shell
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
                    [:div.note (:note field)])]
        label-el (when (and (not (#{:checkbox} (:type field))) (:label field))
                   [:div.label-shell
                    (:label-prefix field)
                    [:label {:for field-id} (:label field)]
                    (:label-suffix field)])]
    [:tr {:id (util/get-field-container-id field)
          :class (str (name (:type field :text)) "-row"
                      (when (:problem field) " problem")
                      (when stacked? " stacked"))}
     (if (= :heading (:type field))
       [:th.heading-cell {:colspan 2} (render-field field)]
       (list
        (when (and label? (not stacked?))
          [:th {:class (if (#{:checkbox :submit} (:type field))
                         "empty-cell"
                         "label-cell")}
           label-el])
        [:td.input-cell {:colspan (if (and label? (not stacked?)) 1 2)}
         (when stacked?
           label-el)
         input-el]))]))

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

(defmethod render-form :table [form-attrs fields opts]
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
        (for [fieldset (group-fieldsets visible-fields)]
          [:tbody {:class (str "fieldset-" (name (:name (first fieldset))))}
           (map render-form-row fieldset)])])]]))

