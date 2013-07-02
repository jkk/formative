(ns formative.core-test
  (:require [clojure.test :refer [run-tests deftest testing is are]]
            [formative.core :as f]))

(def form1
  {:method :post
   :action "/example/path"
   :submit-label "Do It"
   :cancel-href "/example"
   :fields [{:name :a :type :hidden :datatype :int}
            {:name :b :type :email}
            {:name :c :type :password}
            {:name :d :type :checkbox}
            {:name :e :type :labeled-html :html [:b "hello"]}
            {:name :f}
            {:name :g :type :text}
            {:name :h :type :select :options ["foo" "bar" "baz"]}
            {:name :foo-bar-baz}
            {:name "Foo_Bar[]"}]
   :values {:a 1234
            :d true
            :e "foobar"}})

(deftest prep-form-test
  (let [[form-attrs fields specs] (f/prep-form form1)]
    (is (= fields
           '({:name "a", :datatype :int, :type :hidden, :label "A", :value 1234}
              {:name "b", :type :email, :label "B", :value nil}
              {:name "c", :type :password, :label "C", :value nil}
              {:name "d",
               :datatype :boolean,
               :type :checkbox,
               :unchecked-value "false",
               :label "D",
               :value "true",
               :checked true}
              {:html [:b "hello"], :name "e", :type :labeled-html, :label "E"}
              {:name "f", :type :text, :label "F", :value nil}
              {:name "g", :type :text, :label "G", :value nil}
              {:name "h",
               :type :select,
               :options ["foo" "bar" "baz"],
               :label "H",
               :value nil}
              {:name "foo-bar-baz", :type :text, :label "Foo bar baz", :value nil}
              {:name "Foo_Bar[]", :type :text, :label "Foo Bar", :value nil}
              {:type :submit,
               :name "submit",
               :cancel-href "/example",
               :value "Do It"})))))

(deftest merge-fields-test
  (is (= (:fields
           (f/merge-fields form1
                           [{:name :c :type :textarea :label "Merged"}
                            {:name :e :html [:b "howdy"]}
                            {:name :appended :type :text}
                            {:name :after-test :after :f}
                            {:name :before-test :before :a}
                            {:name :after-test2 :after :f}
                            {:name :limbo :after :after-test2}
                            {:name :limbo2 :after :xxx}
                            {:name :appended2}
                            {:name :appended3}
                            {:name :appended4}
                            {:name :appended5}]))
         '({:name :before-test}
            {:name :a, :datatype :int, :type :hidden}
            {:name :b, :type :email}
            {:name :c, :type :textarea, :label "Merged"}
            {:name :d, :type :checkbox}
            {:html [:b "howdy"], :name :e, :type :labeled-html}
            {:name :f}
            {:name :after-test}
            {:name :after-test2}
            {:name :g, :type :text}
            {:name :h, :type :select, :options ["foo" "bar" "baz"]}
            {:name :foo-bar-baz}
            {:name "Foo_Bar[]"}
            {:name :appended, :type :text}
            {:name :appended2}
            {:name :appended3}
            {:name :appended4}
            {:name :appended5}))))

;(run-tests)