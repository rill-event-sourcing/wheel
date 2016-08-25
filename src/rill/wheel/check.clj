(ns rill.wheel.check
  (:require [clojure.set :refer [difference]]))

(defn- vars-with-meta
  [ns k]
  (filter #(get (meta %) k)
          (vals (ns-publics ns))))

(defn ns-events
  [ns]
  (vars-with-meta ns :rill.wheel.aggregate/event-fn))

(defn ns-commands
  [ns]
  (vars-with-meta ns :rill.wheel.aggregate/command-fn))

(defn ns-aggregates
  [ns]
  (vars-with-meta ns :rill.wheel.aggregate/descriptor-fn))

(defn keyword->sym
  [k]
  (symbol (subs (str k) 1)))

(defn check-command
  [c events]
  (let [diff (difference (->> c meta :rill.wheel.aggregate/events
                              (map #(resolve (keyword->sym %)))
                              set)
                         (set events))]
    (when (seq diff)
      {::aggregate c
       ::missing-events  diff})))

(defn check
  []
  (let [events (set (mapcat ns-events (all-ns)))]
    (keep #(check-command % events)
          (mapcat ns-commands (all-ns)))))
