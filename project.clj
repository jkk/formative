(defproject formative "0.8.11"
  :description "Web forms - rendering, parsing, and validating"
  :url "https://github.com/jkk/formative"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [jkkramer/verily "0.6.1"]
                 [clj-time "0.12.2"]
                 [ring/ring-anti-forgery "1.0.1"]
                 [crate "0.2.4"]]
  :test-paths ["test"]
  :profiles {:dev {:dependencies [[com.cemerick/clojurescript.test "0.0.4"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/clojurescript "1.7.228"]]}})
