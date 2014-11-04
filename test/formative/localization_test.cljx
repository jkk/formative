(ns formative.localization-test
  #+cljs (:require-macros [cemerick.cljs.test :refer [is are deftest run-tests]])
  (:require [formative.core :as f]
            #+cljs [cemerick.cljs.test :as t]
            #+clj [clojure.test :refer [is are deftest run-tests testing]]))

(def localized-date-select-form
  {:method :post
   :action "/example/path"
   :locale "el"
   :fields [{:name :date-select-locale-default :type :date-select}
            {:name :date-select-locale-el :type :date-select :locale "en-US"}]})

(def localized-datetime-select-form
  {:method :post
   :action "/example/path"
   :locale "el"
   :timezone "Europe/Athens"
   :fields [{:name :datetime-select-locale-default :type :datetime-select}
            {:name :datetime-select-locale-el :type :datetime-select :locale "en-US" :timezone "America/New_York"}]})

(def localized-month-select-form
  {:method :post
   :action "/example/path"
   :locale "el"
   :fields [{:name :month-select-locale-default :type :month-select}
            {:name :month-select-locale-el :type :month-select :locale "en-US"}]})

(defn extract-first-month-label [field]
  (let [rendered (f/render-field field)]
    (condp = (:type field)
      :date-select ((vec (flatten rendered)) 9)
      :datetime-select ((vec (flatten rendered)) 10)
      :month-select ((vec (flatten rendered)) 5))))

(defn verify-first-month [field]
  (= (extract-first-month-label field)
     (if (= "el" (:locale field)) "Ιανουαρίου" "January")))

(deftest test-localized-date-select
  (testing "Rendering of localized date-select widgets"
    (is
     (every? verify-first-month (:fields localized-date-select-form))
     "Localized date-select widgets rendered properly.")))

(deftest test-localized-datetime-select
  (testing "Rendering of localized datetime-select widgets"
    (is
     (every? verify-first-month (:fields localized-datetime-select-form))
     "Localized datetime-select widgets rendered properly.")))

(deftest test-localized-month-select
  (testing "Rendering of localized month-select widgets"
    (is
     (every? verify-first-month (:fields localized-month-select-form))
     "Localized month-select widgets rendered properly.")))
