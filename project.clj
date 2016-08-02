(defproject rill-event-sourcing/wheel "0.1.1"
  :description "simpler command/aggregate handling for rill"
  :url "https://github.com/rill-event-sourcing//wheel"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.cache "0.6.5"]
                 [rill-event-sourcing/rill.event_store "0.2.1"]
                 [rill-event-sourcing/rill.temp_store "0.2.1"]])
