(ns formative.localization-test
  #+cljs (:require-macros [cemerick.cljs.test :refer [is are deftest run-tests]])
  (:require [formative.core :as f]
            [formative.util :as fu]
            #+cljs [cemerick.cljs.test :as t]
            #+clj [clojure.test :refer [is are deftest run-tests testing]]))

;;
;; tools for digging into the rendered fields
;;

(def date-field-id-pattern (re-pattern (str "^.*__(day|month|year)__$")))

(defmulti extract-date-selects
  "Extracts the list of date field select widgets from a rendered temporal field."
  (fn [field rendered] (:type field)))

(defmethod extract-date-selects :date-select [field rendered]
  (vec (fnext (fnext rendered))))

(defmethod extract-date-selects :datetime-select [field rendered]
  (vec (next (fnext (fnext rendered)))))

(defmethod extract-date-selects :month-select [field rendered]
  (vec (fnext (fnext rendered))))

(defn verify-date-localization [field]
  "Verifies that the date selects are ordered properly, and that their labels and values are properly localized."
  (let [date-field-order (fu/get-date-field-order (:locale field))
        date-field-names (map name date-field-order)
        selects (extract-date-selects field (f/render-field field))
        date-selects (filter (fn [date-select] (when-let [id (:id (fnext date-select))] (re-matches date-field-id-pattern id))) selects)
        date-select-pairs (zipmap date-field-order date-selects)
        expected-first-month (if (= "el" (:locale field)) "Ιανουαρίου" "January")]
    (for [[date-field-key date-select] date-select-pairs]
      (let [expected-default-label (date-field-key (:label-dictionary field))
            first-option-label (last (first (fnext (nfirst date-select))))
            first-month (fnext (fnext (nfirst date-select)))]
        (is (= expected-default-label first-option-label))
        (is (= expected-first-month first-month))))))

;;
;; the test forms
;;

(def localized-date-select-form
  {:method :post
   :action "/example/path"
   :locale "en-US"
   :fields [{:name :date-select-locale-default :type :date-select}
            {:name :date-select-locale-el :type :date-select :locale "el"
             :label-dictionary {:day "Ημέρα" :month "Μήνας" :year "Χρόνος"}}]})

(def localized-datetime-select-form
  {:method :post
   :action "/example/path"
   :locale "el"
   :timezone "Europe/Athens"
   :label-dictionary {:day "Ημέρα" :month "Μήνας" :year "Χρόνος"}
   :fields [{:name :datetime-select-locale-default :type :datetime-select}
            {:name :datetime-select-locale-el :type :datetime-select
             :locale "en-US" :timezone "America/New_York" :label-dictionary {:day "Day" :month "Month" :year "Year"}}]})

(def localized-month-select-form
  {:method :post
   :action "/example/path"
   :locale "el"
   :fields [{:name :month-select-locale-default :type :month-select}
            {:name :month-select-locale-el :type :month-select :locale "en-US"}]})

;;
;; and finally, the tests of the temporal field types
;;

(deftest test-localized-date-select
  (testing "Rendering of localized date-select widgets"
    (is
     (every? verify-date-localization (:fields localized-date-select-form))
     "Localized date-select widgets rendered properly.")))

(deftest test-localized-datetime-select
  (testing "Rendering of localized datetime-select widgets"
    (is
     (every? verify-date-localization (:fields localized-datetime-select-form))
     "Localized datetime-select widgets rendered properly.")))

(deftest test-localized-month-select
  (testing "Rendering of localized month-select widgets"
    (is
     (every? verify-date-localization (:fields localized-month-select-form))
     "Localized month-select widgets rendered properly.")))
