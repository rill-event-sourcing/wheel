(ns rill.wheel.command
  (:require [rill.wheel.aggregate :as aggregate]
            [rill.wheel.repository :as repository]
            [rill.wheel.macro-utils :refer [parse-args]]))

(defn rejection? [result]
  (= (::status result) :rejected))

(defn ok? [result]
  (= (::status result) :ok))

(defn conflict? [result]
  (= (::status result) :conflict))

(defn ok [aggregate]
  {::status :ok ::events (::aggregate/new-events aggregate) ::aggregate aggregate})

(defn rejection
  [aggregate reason]
  {::status :rejected ::reason reason ::aggregate aggregate})

(defn conflict
  [aggregate]
  {::status :conflict ::aggregate aggregate})

(defn commit!
  [aggregate repo]
  (if (repository/commit! repo aggregate)
    (ok aggregate)
    (conflict aggregate)))

(defmacro defcommand
  "Defines a command as a named function that takes a repo and
  properties and returns an aggregate which will be committed, or a
  rejection that will be returned.

  Returns a `rejection`, an `ok` result or a `conflict`"
  {:arglists '([name doc-string? [repository properties*] pre-post-map? body])}
  [& args]
  (let [[n [repository & properties :as fn-args] & body] (parse-args args)
        n (vary-meta n assoc :rill.wheel.command/command-fn true)]
    `(defn ~n ~(vec fn-args)
       (let [result# (do ~@body)]
         (if (rejection? result#)
           result#
           (commit! result# ~repository))))))
