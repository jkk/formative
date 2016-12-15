(ns formative.parse-test
  #?(:cljs (:require-macros [cemerick.cljs.test :refer [is are deftest testing run-tests]]
                            [formative.macros :refer [with-fallback]]))
  (:require [formative.parse :as fp]
            [formative.util :as fu]
            #?(:clj [formative.parse :refer [with-fallback]])
            #?(:cljs [cemerick.cljs.test :as t])
            #?(:clj [clojure.test :refer [is are deftest testing run-tests]])))

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
            {:name :f-time :datatype :time}
            {:name :f-instant :datatype :instant}

            {:name :f-ints :datatype :ints}
            {:name :f-longs :datatype :longs}
            {:name :f-booleans :datatype :booleans}
            {:name :f-floats :datatype :floats}
            {:name :f-doubles :datatype :doubles}
            {:name :f-decimals :datatype :decimals}
            {:name :f-bigints :datatype :bigints}
            {:name :f-dates :datatype :dates}
            {:name :f-times :datatype :times}
            {:name :f-instants :datatype :instants}

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
            {:name :f-us-tel :type :us-tel}
            {:name :f-date-select :type :date-select}
            {:name :f-year-select :type :year-select}
            {:name :f-month-select :type :month-select}
            {:name :f-time-select :type :time-select}
            {:name :f-datetime-select :type :datetime-select
             :timezone "America/New_York"}
            {:name :f-currency :type :currency}

            {:name :f-heading :type :heading}
            {:name :f-labeled-html :type :labeled-html}
            {:name :f-html :type :html}
            {:name "foo[bar][baz]" :datatype :int}
            {:name :foo2.bar.baz :datatype :int}]})

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
   :f-time "23:06"
   :f-instant "2012-12-25T23:06:00"
   :f-ints ["123" "456" "789"]
   :f-longs "123,456, 789"
   :f-booleans ["true" "true" "false"]
   :f-floats ["123.45" "678.90"]
   :f-doubles ["123.45" "678.90"]
   :f-decimals ["123.45" "678.90"]
   :f-bigints ["13918723981723918723987129387198273198273918273"
               "29038402938402938402983409283049203948209384209"]
   :f-dates ["2012-01-01" "2012-02-03" "2012-10-04"]
   :f-times ["0:01" "23:02" "12:00"]
   :f-instants ["2012-01-01T00:01:00" "2012-02-03T23:02:00" "2012-10-04T12:00:00"]
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
   :f-us-tel "(234) 567-8901x123"
   :f-date-select {:month "12" :day "25" :year "2012"}
   :f-year-select "2012"
   :f-month-select "12"
   :f-time-select {:h "12" :m "0" :ampm "pm"}
   :f-datetime-select {:year "2012" :month "12" :day "25" :h "6" :m "0" :ampm "pm"}
   :f-currency "123.45"
   :f-heading "foo"
   :f-labeled-html "foo"
   :f-html "foo"
   :foo {:bar {:baz "1"}}
   :foo2 {:bar {:baz "1"}}})

(def good-values
  {:f-default "foo"
   :f-int 123
   :f-long 123
   :f-boolean true
   :f-float 123.45
   :f-double 123.45
   :f-decimal #?(:clj 123.45M :cljs "123.45")
   :f-bigint #?(:clj 13918723981723918723987129387198273198273918273N
                :cljs "13918723981723918723987129387198273198273918273")
   :f-date (fu/to-date (fu/utc-date 2012 12 25))
   :f-time (fu/to-time (fu/parse-time "23:06"))
   :f-instant (fu/to-date (fu/utc-date 2012 12 25 23 6))
   :f-ints [123 456 789]
   :f-longs [123 456 789]
   :f-booleans [true true false]
   :f-floats [123.45 678.90]
   :f-doubles [123.45 678.90]
   :f-decimals #?(:clj [123.45M 678.90M] :cljs ["123.45" "678.90"])
   :f-bigints #?(:clj [13918723981723918723987129387198273198273918273N
                       29038402938402938402983409283049203948209384209N]
                 :cljs ["13918723981723918723987129387198273198273918273"
                        "29038402938402938402983409283049203948209384209"])
   :f-dates [(fu/to-date (fu/utc-date 2012 1 1))
             (fu/to-date (fu/utc-date 2012 2 3))
             (fu/to-date (fu/utc-date 2012 10 4))]
   :f-times [(fu/to-time (fu/parse-time "00:01"))
             (fu/to-time (fu/parse-time "23:02"))
             (fu/to-time (fu/parse-time "12:00"))]
   :f-instants [(fu/to-date (fu/utc-date 2012 1 1 0 1))
                (fu/to-date (fu/utc-date 2012 2 3 23 2))
                (fu/to-date (fu/utc-date 2012 10 4 12 0))]
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
   :f-us-tel "2345678901x123"
   :f-date-select (fu/to-date (fu/utc-date 2012 12 25))
   :f-year-select 2012
   :f-month-select 12
   :f-time-select (fu/to-time (fu/parse-time "12:00"))
   :f-datetime-select (fu/to-date (fu/utc-date 2012 12 25 #?(:clj 23 :cljs 18) 0))
   :f-currency #?(:clj 123.45M :cljs "123.45")
   :foo {:bar {:baz 1}}
   :foo2 {:bar {:baz 1}}})

(deftest parse-test
  (testing "Known-good params"
    (let [values (fp/parse-params form1 good-params)]
      (is (= values good-values))))
  (testing "Unparsed Ring params"
    (is (= (fp/parse-params form1 {"f-date-select[year]" "2012"
                                   "f-date-select[month]" "12"
                                   "f-date-select[day]" "25"
                                   "f-checkboxes2[]" ["" "true" "false"]})
           {:f-date-select (fu/to-date (fu/utc-date 2012 12 25))
            :f-checkboxes2 [true false]})))
  (testing "Unparsed form data"
    (is (= (fp/parse-params form1 (str "f-date-select[year]=2012"
                                       "&f-date-select[month]=12"
                                       "&f-date-select[day]=25"
                                       "&f-checkboxes2[]="
                                       "&f-checkboxes2[]=true"
                                       "&f-checkboxes2[]=false"))
           {:f-date-select (fu/to-date (fu/utc-date 2012 12 25))
            :f-checkboxes2 [true false]})))
  (testing "Failed parsing"
    (let [values (fp/parse-params form1 {:f-int "xxx"}
                                  :validate false)]
      (is (instance? formative.parse.ParseError (:f-int values))))
    (let [ex (try
               (fp/parse-params form1 {:f-int "xxx"})
               (catch #?(:clj Exception :cljs js/Error) ex
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
               (catch #?(:clj Exception :cljs js/Error) ex
                   ex))]
      (is (= [{:keys [:f-us-state :f-ca-state :f-country]
               :msg "must not be blank"}]
             (:problems (ex-data ex)))))))

(def form2
  {:fields [{:name :a :datatype :int :datatype-error "foobar"}]
   :validations [[:int :a "nope"]]})

(deftest validate-types-test
  (testing ":validate-types true (default)"
    (let [ex (try
               (fp/parse-params form2 {:a "x"})
               (catch #?(:clj Exception :cljs js/Error) ex
                   ex))]
      (is (= '({:keys (:a), :msg "foobar"}
               {:keys (:a), :msg "nope"})
             (:problems (ex-data ex))))))
  (testing ":validate-types false"
    (let [ex (try
               (fp/parse-params (assoc form2 :validate-types false)
                                {:a "x"})
               (catch #?(:clj Exception :cljs js/Error) ex
                   ex))]
      (is (= '({:keys (:a), :msg "nope"})
             (:problems (ex-data ex))))))
  (testing ":validate-types false, without :validations"
    (is (= (fp/parse-params (-> form2
                                (dissoc :validations)
                                (assoc :validate-types false))
                            {:a "x"})
           {:a (fp/->ParseError "x")}))))

(deftest with-fallback-test
  (= [{:msg "hi"}]
     (with-fallback identity
       (throw (ex-info "Boo" {:problems [{:msg "hi"}]})))))

;;(run-tests)
