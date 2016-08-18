(ns rill.wheel.command
  "Commands are functions that apply new events to aggregates.

  ## Command flow

       (-> (get-some-aggregate repository id) ; 1.
           (cmd-call additional-argument)     ; 2.
           (commit!))                         ; 3.

  ### 1. Fetch aggregate

  Before calling the command, the aggregate it applies to should get
  fetched from the `repository`. In rill/wheel, this will always work
  and must be done even for aggregates that have no events applied to
  them - this will result in an `rill.wheel.aggregate/empty?`
  aggregate that can be committed later.

  ### 2. Calling the command

  A command can have any number of arguments, and it's idiomatic for
  commands to take the aggregate-to-change as the first argument.

  As a matter of style, it's suggested that commands do not fetch
  other objects from the repostory but are explicitly passed any
  necessary aggregates.

  #### Success

  A successful command returns an `uncomitted?` aggregate.

  #### Rejection

  A command may be rejected, in which case the command returns a
  `rejection` - meaning the request was denied for business
  reasons. Rejections are explicitly constructed in the `defcommand`
  body by the application writer.

  It's typically useless to retry a rejected command.

  ### 3. Committing results

  The result of a command can be persisted back to the repository by
  calling `commit!`. If `commit!` is passed a `rejection` it will
  return it. Otherwise the argument should be an aggregate that will
  be persisted.

  ### ok

  A successful commit returns an `ok?` object describing the committed
  events and aggregate.

  ### conflict

  Commiting an updated aggregate can return a `conflict`, meaning
  there were changes to the aggregate in the repository in the time
  between fetching the aggregate and calling `commit!`.

  Depending on the use case, it may be useful to update the aggregate
  and retry a conflicted command.

  ## Defining commands


       (defevent x-happened                            ; 1.
          \"X happened to obj\"
          [obj arg1]
          (assoc obj :a arg1))

       (defcommand do-x                                ; 2.
          \"Make X happen to obj\"
          [obj arg1]
          (if (= (:a obj) arg1))                       ; 3
              (rejection obj \"Arg already applied\")
              (x-happened obj)))                       ; 4.


  ### 1. Define events to apply

  Commands can only affect aggregates by applying events to them. Here
  we define an event with `defevent`. When the `x-happened` event is
  applied it will set key `:a` of aggregate `obj`.

  It's idiomatic to give events a past-tense name, to indicate that
  the event happened and is not .

  ### 2. Define command

  Commands are defined by calling `defcommand`, specifying a name,
  optional docstring, argument vector and a command body.

  ### 3. Test state and reject command

  Aggregate state is typically only used to keep track of information
  that must be used to validate commands. When a command must not
  proceed, the command body can return a `rejection` with a reason.

  ### 4. Apply new event(s)

  When the command is acceptable, it should apply the necessary events
  to the aggregate and return the result (the updated aggregate).
  "
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

(defn uncommitted?
  "`aggregate` has events applied that can be committed."
  [aggregate]
  (boolean (seq (:rill.wheel.aggregate/new-events aggregate))))

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

  The metadata of the command may contain a
  `:rill.wheel.command/events` key, which will specify the types of
  the events that may be generated. As a convenience, the
  corresponding event functions are `declare`d automatically so the
  `defevent` statements can be written after the command. This usually
  reads a bit nicer.

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
