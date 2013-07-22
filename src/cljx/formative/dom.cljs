(ns formative.dom
  (:require [formative.util :as fu]
            [formative.parse :as fp]
            [formative.render :as fr]
            [dommy.core :as d]
            [clojure.string :as string])
  (:require-macros [formative.macros :refer [with-fallback]]
                   [dommy.macros :refer [sel sel1 node]]))

(defn serialize [form-el]
  (->> (for [el (d/->Array (.-elements form-el))
             :let [name (.-name el)]
             :when (not (string/blank? name))]
         (let [node-name (.-nodeName el)
               type (.-type el)
               value (.-value el)]
           (cond
             (and (= "INPUT" node-name)
                  (#{"checkbox" "radio"} type)) (when (.-checked el)
                                                  (fu/encode-uri-kv name value))
             (and (= "SELECT" node-name)
                  (= "select-multiple" type)) (->> (for [opt (d/->Array (.-options el))
                                                         :when (.-selected opt)]
                                                     (fu/encode-uri-kv name (.-value opt)))
                                                (string/join "&"))
             (and (= "INPUT" node-name)
                  (= "file" type)) nil
             :else (fu/encode-uri-kv name value))))
    (remove nil?)
    (string/join "&")))

(defn get-form-el [container-or-form-el]
  (if (= "FORM" (.-nodeName container-or-form-el))
    container-or-form-el
    (sel1 container-or-form-el "form")))

(defn clear-problems [container-or-form-el]
  (let [form-el (get-form-el container-or-form-el)]
    (when-let [parent-el (.-parentNode form-el)]
      (when-let [problems-el (sel1 parent-el ".form-problems")]
        (d/remove! problems-el)))
    (doseq [el (sel form-el ".problem.error")]
      (d/remove-class! el "problem" "error"))))

(defn get-scroll-top []
  (if (exists? (.-pageYOffset js/window))
    (.-pageYOffset js/window)
    (.-scrollTop (or (-> js/document .-documentElement)
                     (-> js/document .-body .-parentNode)
                     (-> js/document .-body)))))

(defn get-offset-top [el]
  (let [rect (.getBoundingClientRect el)]
    (+ (.-top rect) (get-scroll-top))))

(defn scroll-to-el [el]
  (.scrollTo js/window 0 (- (get-offset-top el) 10)))

(defn show-problems [form-spec container-or-form-el problems]
  (let [form-el (get-form-el container-or-form-el)]
    (clear-problems form-el)
    (let [problems-el (node (fr/render-problems problems (:fields form-spec)))]
      (d/insert-before! problems-el form-el)
      (scroll-to-el problems-el))
    (doseq [problem problems
            :let [fnames (map name (if (map? problem)
                                     (:keys problem)
                                     [problem]))]
            fname fnames]
      (let [field-container-id (fu/get-field-container-id
                                 {:id (fu/get-field-id {:name fname})
                                  :name fname})]
        (when-let [el (sel1 (str "#" field-container-id))]
          (d/add-class! el "problem error"))))))

(defn handle-submit [form-spec container-or-form-el success & [failure]]
  (let [form-el (get-form-el container-or-form-el)
        failure (or failure
                    #(show-problems form-spec form-el %))]
    (d/listen! form-el :submit
               (fn [event]
                 (.preventDefault event)
                 (with-fallback failure
                   (clear-problems form-el)
                   (success
                     (fp/parse-params form-spec (serialize form-el))))))))