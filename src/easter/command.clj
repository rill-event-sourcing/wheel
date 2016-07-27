(ns easter.command
  (:require [easter.aggregate :as aggregate]
            [easter.repository :as repository]
            [easter.macro-utils :refer [parse-args]]))

(defn rejection? [result]
  (= (::status result) :rejected))

(defn ok? [result]
  (= (::status result) :ok))

(defn conflict? [result]
  (= (::status result) :conflict))

(defn ok [aggregate]
  {::status :ok ::events (::new-events aggregate) ::aggregate aggregate})

(defn rejection
  [aggregate reason]
  {::status :rejected ::reason reason ::aggregate aggregate})

(defn conflict
  [aggregate]
  {::status :conflict ::aggregate aggregate})

(defn commit!
  [aggregate repo]
  (if (repository/commit-aggregate! repo aggregate)
    (ok aggregate)
    (conflict aggregate)))

(defmacro defcommand
  "Defines a command as a named function that takes a repo and
  properties and returns an aggregate which will be committed, or a
  rejection that will be returned.

  Returns a `rejection`, an `ok` result or a `conflict`"
  {:arglists '([name doc-string? [repository properties*] pre-post-map? body])}
  [& args]
  (let [[n [repository & properties :as fn-args] & body] (parse-args args)]
    `(defn ~n ~(vec fn-args)
       (let [result# (do ~@body)]
         (if (rejection? result#)
           result#
           (commit! result# ~repository))))))
