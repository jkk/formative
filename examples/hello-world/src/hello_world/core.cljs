(ns hello-world.core
  (:require [formative.core :as f]
            [formative.dom :as fd])
  (:require-macros [dommy.macros :refer [node sel sel1]]))

(enable-console-print!)

(println "This text is printed from src/hello-world/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state (constantly))
  (-> (.getElementById js/document "form")
      (.appendChild (node (f/render-form {:fields [{:name :foo}]
                                          :renderer :bootstrap3-stacked}))))
  )
