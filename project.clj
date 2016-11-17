(defproject rill-event-sourcing/wheel "0.1.14-SNAPSHOT"
  :description "Command & aggregate handling for rill"
  :url "https://github.com/rill-event-sourcing/wheel"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-codox "0.9.6"]]
  :codox {:metadata {:doc/format :markdown}}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/core.cache "0.6.5"]
                 [rill-event-sourcing/rill.event_store "0.2.2"]
                 [rill-event-sourcing/rill.temp_store "0.2.2"]])
