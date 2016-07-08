(defproject three-in-a-row "0.1.0-SNAPSHOT"
  :description "Three-in-a-row game"
  :license {:name "GNU GPL v3+"
            :url "http://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha9"]
                 [ring/ring-core "1.5.0"]
                 [enlive "1.1.6"]]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler three-in-a-row.core/app})
