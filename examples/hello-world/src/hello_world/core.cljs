(ns hello-world.core
  (:require [formative.core :as f]
            [formative.dom :as fd])
  (:require-macros [dommy.macros :refer [node sel sel1]]))

(enable-console-print!)

(println "This text is printed from src/hello-world/core.cljs. Go ahead and edit it and see reloading in action.")

(defn trivial-form []
  {:fields [{:name :foo}]
   :validations [[:required [:foo]]
                 [:max-length 3 [:foo]]]
   :renderer :bootstrap3-stacked})

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

(defn reset-form []
  (let [form-elem (.getElementById js/document "form")]
    (some-> form-elem
            .-firstChild
            .remove)
    (-> form-elem
        (.appendChild (node (f/render-form (trivial-form)))))
    (fd/handle-submit (trivial-form)
                      form-elem
                      #(js/alert "success!"))))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state (constantly))
  (reset-form))

(fd/clear-problems (.getElementById js/document "form"))
