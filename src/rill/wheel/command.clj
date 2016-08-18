(ns rill.wheel.command
  (:require [rill.wheel.repository :as repo]
            [rill.wheel.macro-utils :refer [parse-args]]))

(defn rejection? [result]
  (= (::status result) :rejected))

(defn ok? [result]
  (= (::status result) :ok))

(defn conflict? [result]
  (= (::status result) :conflict))

(defn ok [aggregate]
  {::status :ok ::events (:rill.wheel.aggregate/new-events aggregate) ::aggregate aggregate})

(defn rejection
  [aggregate reason]
  {::status :rejected ::reason reason ::aggregate aggregate})

(defn conflict
  [aggregate]
  {::status :conflict ::aggregate aggregate})

(defn commit!
  "Commit the result of a command execution. If the command returned a
  `rejection` nothing is committed and the rejection is returned. If the
  result is an aggregate it is committed to the repository. If that
  succeeds an `ok` is returned. Otherwise a `conflict` is returned."
  [aggregate-or-rejection]
  (cond
    (rejection? aggregate-or-rejection)
    aggregate-or-rejection
    (repo/commit! (:rill.wheel.aggregate/repository aggregate-or-rejection) aggregate-or-rejection)
    (ok aggregate-or-rejection)
    :else
    (conflict aggregate-or-rejection)))

;;--- TODO(Joost) - maybe - specify post-conditions that check the
;;--- generated event types based on the ::command/events metadata
(defmacro defcommand
  "Defines a command as a named function that takes any arguments and
  returns an `aggregate` or `rejection` that can be passed to
  `commit!`.

  The metadata of the command may contain a :rill.wheel.events key,
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
  (let [[n fn-args & body] (parse-args args)
        n                  (vary-meta n assoc :rill.wheel.command/command-fn true)]
    `(do ~(when-let [event-keys (::events (meta n))]
            `(declare ~@(map (fn [k]
                               (symbol (subs (str k) 1)))
                             event-keys)))

         (defn ~n ~(vec fn-args)
           ~@body))))
