(ns formative.helpers
  (:require [clojure.string :as string]))

(defn- ucfirst [^String s]
  (str (Character/toUpperCase (.charAt s 0)) (subs s 1)))

(defn get-field-label
  "Determines what to use for a field's label. Uses the :label key if set,
  otherwise renders :name as a string - e.g., :foo-bar => \"Foo Bar\"."
  [field]
  (if (contains? field :label)
    (when (:label field)
      (ucfirst (:label field)))
    (-> (:name field)
      name
      (string/replace #"[_-]" " ")
      (string/replace #"\bid\b" "ID")
      ucfirst)))

(defn render-problems [problems & [fields]]
  (let [fields-by-name (if (map? fields)
                         fields
                         (into {} (for [f fields]
                                    [(:name f) f])))]
    [:div.form-problems.alert.alert-error.clearfix
     [:ul
      ;; double duty - handle Formative and Modelo problems
      (for [{:keys [keys field-name spec msg]} problems]
        (let [field-names (or (seq keys)
                              (when field-name [field-name]))
              field-labels (map #(get-field-label
                                   (or (fields-by-name %)
                                       (fields-by-name (name %))
                                       {:name %}))
                                field-names)]
          [:li
           (when (seq field-labels)
             (list [:strong (string/join ", " field-labels)] ": "))
           msg]))]]))