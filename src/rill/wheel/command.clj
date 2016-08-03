(ns rill.wheel.command
  (:require [rill.wheel.aggregate :as aggregate]
            [rill.wheel.repository :as repo]
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
  (if (repo/commit! repo aggregate)
    (ok aggregate)
    (conflict aggregate)))

(defmacro defcommand
  "Defines a command as a named function that takes a repo and
  properties and returns an aggregate which will be committed, or a
  rejection that will be returned.

  Returns a `rejection`, an `ok` result or a `conflict`

  The metadata of the command MUST contain a :rill.wheel.events key,
  which will specify the types of the events that may be generated. As
  a convenience, the corresponding event functions are `declare`d
  automatically so the `defevent` statements can be written after the
  command. This usually reads a bit nicer.

    (defcommand answer-question
      \"Try to answer a question\"
      {::command/events [::answered-correctly ::answered-incorrectly]}
      [repo question-id user-id answer]
      (let [question (question repo question-id)]
        (if (some-check question answer)
         (answered-correctly question user-id answer)
         (answered-incorrectly question user-id answer))))

  "
  {:arglists '([name doc-string? attr-map? [repository properties*] pre-post-map? body])}
  [& args]
  (let [[n [repository & properties :as fn-args] & body] (parse-args args)
        n                                                (vary-meta n assoc :rill.wheel.command/command-fn true)]
    `(do ~(if-let [event-keys (::events (meta n))]
            `(declare ~@(map (fn [k]
                               (symbol (subs (str k) 1)))
                             event-keys))
            (throw (IllegalArgumentException. "command has no events specified")))

         (defn ~n ~(vec fn-args)
           (let [result# (do ~@body)]
             (if (rejection? result#)
               result#
               (commit! result# ~repository)))))))
