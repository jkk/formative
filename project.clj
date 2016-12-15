(defproject formative "0.8.9-SNAPSHOT"
  :description "Web forms - rendering, parsing, and validating"
  :url "https://github.com/jkk/formative"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [jkkramer/verily "0.6.0"]
                 [clj-time "0.5.1"]
                 [prismatic/dommy "0.1.1"]
                 [ring-anti-forgery "0.2.1"]]
  :test-paths ["test"]
  :profiles {:dev {:dependencies [[com.cemerick/clojurescript.test "0.0.4"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/clojurescript "1.7.228"]]}})
