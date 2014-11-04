(defproject formative "0.8.8"
  :description "Web forms - rendering, parsing, and validating"
  :url "https://github.com/jkk/formative"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [jkkramer/verily "0.6.0"]
                 [clj-time "0.8.0"]
                 [prismatic/dommy "1.0.0"]
                 [ring/ring-anti-forgery "1.0.0"]]
  :test-paths ["target/test-classes"]
  :cljx {:builds [{:source-paths ["src"]
                   :output-path "target/classes"
                   :rules :clj}
                  {:source-paths ["src"]
                   :output-path "target/classes"
                   :rules :cljs}
                  {:source-paths ["test"]
                   :output-path "target/test-classes"
                   :rules :clj}
                  {:source-paths ["test"]
                   :output-path "target/test-classes"
                   :rules :cljs}]}
  :profiles {:dev {:dependencies [[com.cemerick/clojurescript.test "0.0.4"]
                                  [org.clojure/clojurescript "0.0-2138"]
                                  [com.cemerick/piggieback "0.0.5"]
                                  [com.keminglabs/cljx "0.4.0"]]
                   :plugins [[com.keminglabs/cljx "0.4.0"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl
                                                     cljx.repl-middleware/wrap-cljx]}
                   :hooks [cljx.hooks]}})
