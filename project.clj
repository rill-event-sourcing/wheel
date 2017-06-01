(defproject rill-event-sourcing/wheel "0.1.20"
  :description "Command & aggregate handling for rill"
  :url "https://github.com/rill-event-sourcing/wheel"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-codox "0.9.6"]]
  :codox {:metadata {:doc/format :markdown}}
  :dependencies [[org.clojure/clojure "1.9.0-alpha16"]
                 [org.clojure/core.cache "0.6.5"]
                 [rill-event-sourcing/rill.event_store "0.2.2"]
                 [rill-event-sourcing/rill.temp_store "0.2.2"]]
  :profiles {:dev
             {:dependencies [[rill-event-sourcing/rill.event_store.mysql "0.2.3-RC2"]
                             [mysql/mysql-connector-java "5.1.6"]]}})
