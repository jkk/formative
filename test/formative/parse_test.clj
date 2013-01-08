(ns formative.parse-test
  (:require [clojure.test :refer [run-tests deftest testing is are]]
            [formative.parse :as fp]))

(def form1
  {:fields [{:name :f-default}

            {:name :f-int :type :hidden :datatype :int}
            {:name :f-long :datatype :long}
            {:name :f-boolean :datatype :boolean}
            {:name :f-float :datatype :float}
            {:name :f-double :datatype :double}
            {:name :f-decimal :datatype :decimal}
            {:name :f-bigint :datatype :bigint}
            {:name :f-date :datatype :date}
            
            {:name :f-ints :datatype :ints}
            {:name :f-longs :datatype :longs}
            {:name :f-booleans :datatype :booleans}
            {:name :f-floats :datatype :floats}
            {:name :f-doubles :datatype :doubles}
            {:name :f-decimals :datatype :decimals}
            {:name :f-bigints :datatype :bigints}
            {:name :f-dates :datatype :dates}
            
            {:name :f-textarea :type :textarea}
            {:name :f-select1 :type :select :options ["foo" "bar" "baz"]}
            {:name :f-select2 :type :select :datatype :boolean
             :options [{:label "Foo" :value true}
                       {:label "Bar" :value false}]}
            {:name :f-checkbox1 :type :checkbox}
            {:name :f-checkbox2 :type :checkbox :checked-value "foo"
             :unchecked-value "bar"}
            {:name :f-checkboxes1 :type :checkboxes
             :options ["foo" "bar" "baz"]}
            {:name :f-checkboxes2 :type :checkboxes :datatype :booleans
             :options [{:label "Foo" :value true}
                       {:label "Bar" :value false}]}
            {:name :f-checkboxes3 :type :checkboxes :datatype :ints
             :options [{:label "Foo" :value 1}
                       {:label "Bar" :value 2}
                       {:label "Baz" :value 3}]}
            {:name :f-radios1 :type :radios
             :options ["foo" "bar" "baz"]}
            {:name :f-radios2 :type :radios :datatype :boolean
             :options [{:label "Foo" :value true}
                       {:label "Bar" :value false}]}
            
            {:name :f-us-state :type :us-state}
            {:name :f-ca-state :type :ca-state}
            {:name :f-country :type :country}
            {:name :f-date-select :type :date-select}
            {:name :f-year-select :type :year-select}
            {:name :f-month-select :type :month-select}
            {:name :f-currency :type :currency}
            
            {:name :f-heading :type :heading}
            {:name :f-labeled-html :type :labeled-html}
            {:name :f-html :type :html}]})

(def good-params
  {:f-default "foo"
   :f-int "123"
   :f-long "123"
   :f-boolean "true"
   :f-float "123.45"
   :f-double "123.45"
   :f-decimal "123.45"
   :f-bigint "13918723981723918723987129387198273198273918273"
   :f-date "2012-12-25"
   :f-ints ["123" "456" "789"]
   :f-longs ["123" "456" "789"]
   :f-booleans ["true" "true" "false"]
   :f-floats ["123.45" "678.90"]
   :f-doubles ["123.45" "678.90"]
   :f-decimals ["123.45" "678.90"]
   :f-bigints ["13918723981723918723987129387198273198273918273"
               "29038402938402938402983409283049203948209384209"]
   :f-dates ["2012-01-01" "2012-02-03" "2012-10-04"]
   :f-textarea "foo"
   :f-select1 "bar"
   :f-select2 "true"
   :f-checkbox1 "false"
   :f-checkbox2 "bar"
   :f-checkboxes1 ["" "foo" "bar"]
   :f-checkboxes2 ["" "true" "false"]
   :f-checkboxes3 ["" "2" "3"]
   :f-radios1 "foo"
   :f-radios2 "false"
   :f-us-state "NY"
   :f-ca-state "ON"
   :f-country "US"
   :f-date-select {:month "12" :day "25" :year "2012"}
   :f-year-select "2012"
   :f-month-select "12"
   :f-currency "123.45"
   :f-heading "foo"
   :f-labeled-html "foo"
   :f-html "foo"})

(def good-values
  {:f-default "foo"
   :f-int 123
   :f-long 123
   :f-boolean true
   :f-float 123.45
   :f-double 123.45
   :f-decimal 123.45M
   :f-bigint 13918723981723918723987129387198273198273918273N
   :f-date (java.util.Date. 112 11 25)
   :f-ints [123 456 789]
   :f-longs [123 456 789]
   :f-booleans [true true false]
   :f-floats [123.45 678.90]
   :f-doubles [123.45 678.90]
   :f-decimals [123.45M 678.90M]
   :f-bigints [13918723981723918723987129387198273198273918273N
               29038402938402938402983409283049203948209384209N]
   :f-dates [(java.util.Date. 112 0 1)
             (java.util.Date. 112 1 3)
             (java.util.Date. 112 9 4)]
   :f-textarea "foo"
   :f-select1 "bar"
   :f-select2 true
   :f-checkbox1 false
   :f-checkbox2 "bar"
   :f-checkboxes1 ["foo" "bar"]
   :f-checkboxes2 [true false]
   :f-checkboxes3 [2 3]
   :f-radios1 "foo"
   :f-radios2 false
   :f-us-state "NY"
   :f-ca-state "ON"
   :f-country "US"
   :f-date-select (java.util.Date. 112 11 25)
   :f-year-select 2012
   :f-month-select 12
   :f-currency 123.45M})

(deftest parse-test
  (testing "Known-good params"
           (let [values (fp/parse-params form1 good-params)]
             (is (= values good-values))))
  (testing "Unparsed Ring params"
           (is (= (fp/parse-params form1 {"f-date-select[year]" "2012"
                                          "f-date-select[month]" "12"
                                          "f-date-select[day]" "25"
                                          "f-checkboxes2[]" ["" "true" "false"]})
                  {:f-date-select (java.util.Date. 112 11 25)
                   :f-checkboxes2 [true false]})))
  (testing "Failed parsing"
           (let [values (fp/parse-params form1 {:f-int "xxx"}
                                         :validate false)]
             (is (instance? formative.parse.ParseError (:f-int values))))
           (let [ex (try
                      (fp/parse-params form1 {:f-int "xxx"})
                      (catch Exception ex
                        ex))]
             (is (= [{:keys [:f-int] :msg "must be a number"}]
                    (:problems (ex-data ex)))))
           (let [ex (try
                      (fp/parse-params (assoc form1
                                              :validations
                                              [[:required [:f-us-state
                                                           :f-ca-state
                                                           :f-country]]])
                                       {:f-int "123"})
                      (catch Exception ex
                        ex))]
             (is (= [{:keys [:f-us-state :f-ca-state :f-country]
                      :msg "must not be blank"}]
                    (:problems (ex-data ex)))))))

;(run-tests)