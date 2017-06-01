(ns rill.wheel

  "# Aggregates and Events

  ### Synopsis

      (require '[rill.wheel :as aggregate
                 :refer [defaggregate defevent]])

      (defaggregate user
        \"a user is identified by a single `email` property\"
        [email])

      (defevent registered ::user
        \"user was correctly registered\"
        [user]
        (assoc user :registered? true))

      (defevent unregistered ::user
        \"user has unregistered\"
        [user]
        (dissoc user :registered?))

      (-> (user \"user@example.com\") registered :registered?)
        => true

      (registered-event (user \"user@example.com\"))
        => {:rill.message/type :user/registered,
            :email \"user@example.com\",
            :rill.wheel/type :user/user}

      (wheel/new-events some-aggreate)
        => seq-of-events

  ### Store and retrieve aggregates in a repository

      (-> (get-user repo \"user@example.com)
          (registered)
          (command/commit!))
      ;; ...
      (get-user some-repository \"user@example.com\")

  ### Full example of defaggregate

      (defaggregate turnstile
        \"An aggregate with docstring\"
        [turnstile-id]
        {:pre [(instance? java.util.UUID turnstile-id)]}
        ((installed
          \"A turnstile was installed\"
          [turnstile]
          (assoc turnstile
                 :installed? true
                 :locked? true
                 :coins 0
                 :turns 0
                 :pushes 0))

         (coin-inserted
          \"A Coin was inserted into the turnstile\"
          [turnstile]
          (-> turnstile
              (update :coins inc)
              (assoc :locked? false)))

         (arm-turned
          \"The turnstile's arm was turned\"
          [turnstile]
          (-> turnstile
              (update :pushes inc)
              (update :turns inc)
              (assoc :locked? true)))

         (arm-pushed-ineffectively
          \"The arm was pushed but did not move\"
          [turnstile]
          (-> turnstile
              (update :pushes inc))))

        ((install-turnstile
          [repo turnstile-id]
          (let [turnstile (get-turnstile repo turnstile-id)]
            (if (wheel/exists turnstile)
              (rejection turnstile \"Already exists\")
              (installed turnstile))))

         (insert-coin
          \"Insert coin into turnstile, will unlock\"
          [repo turnstile-id]
          (let [turnstile (get-turnstile repo turnstile-id)]
            (if (:installed? turnstile)
              (coin-inserted turnstile)
              (rejection turnstile \"Turnstile not installed\"))))

         (push-arm
          \"Push the arm, might turn or be ineffective\"
          {::wheel/events [::arm-pushed-ineffectively ::arm-turned]}
          [repo turnstile-id]
          (let [turnstile (get-turnstile repo turnstile-id)]
            (cond
              (not (:installed? turnstile))
              (rejection turnstile \"Not installed\")
              (:locked? turnstile)
              (arm-pushed-ineffectively turnstile)
              :else
               (arm-turned turnstile))))))


  # Commands

  Commands are functions that apply new events to aggregates.

  ## Command flow

       (-> (get-some-aggregate repository id) ; 1.
           (cmd-call additional-argument)     ; 2.
           (commit!))                         ; 3.

  ### 1. Fetch aggregate

  Before calling the command, the aggregate it applies to should get
  fetched from the `repository`. In rill/wheel, this will always work
  and must be done even for aggregates that have no events applied to
  them - this will result in an `rill.wheel/empty?`
  aggregate that can be committed later.

  ### 2. Calling the command

  A command can have any number of arguments, and it's idiomatic for
  commands to take the aggregate-to-change as the first argument.

  As a matter of style, it's suggested that commands do not fetch
  other objects from the repository but are explicitly passed any
  necessary aggregates.

  #### Success

  A successful command returns an `uncommitted?` aggregate.

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

  Committing an updated aggregate can return a `conflict`, meaning
  there were changes to the aggregate in the repository in the time
  between fetching the aggregate and calling `commit!`.

  Depending on the use case, it may be useful to update the aggregate
  and retry a conflicted command.

  ## Defining commands

       (defaggregate x                                 ; 1.
         [id])

       (defevent x-happened                            ; 2.
          \"X happened to obj\"
          [obj arg1]
          (assoc obj :a arg1))

       (defcommand do-x ::x                            ; 3.
          \"Make X happen to obj\"
          [obj arg1]
          (if (= (:a obj) arg1))                       ; 4.
              (rejection obj \"Arg already applied\")
              (x-happened obj)))                       ; 5.


  ### 1. Define the aggregate type

  ### 2. Define events to apply

  Commands can only affect aggregates by applying events to them. Here
  we define an event with `defevent`. When the `x-happened` event is
  applied it will set key `:a` of aggregate `obj`.

  It's idiomatic to give events a past-tense name, to indicate that
  the event happened and cannot be rejected.

  ### 3. Define command

  Commands are defined by calling `defcommand`, specifying a name,
  aggregate type, optional docstring, argument vector and a command
  body.

  ### 4. Test state and reject command

  Aggregate state is typically only used to keep track of information
  that must be used to validate commands. When a command must not
  proceed, the command body can return a `rejection` with a reason.

  ### 5. Apply new event(s)

  When the command is acceptable, it should apply the necessary events
  to the aggregate and return the result (the updated aggregate).

  ## Alternative command invocations

  `defcommand` installs a number of functions and multi-methods that
  can be used to invoke the defined command.

  In the following examples, we assume the following definitions:


       (ns user
          (:require [rill.wheel :as aggregate
                                :refer [defaggregate
                                        defcommand
                                        defevent
                                        transact!
                                        ok?]]))

       (defaggregate user
          [user-id])

       (defevent registered ::user
          [user name])

       (defcommand register ::user
          [user name]
          (registered user name))

  ### Commands as data

  If you need to, you can describe and execute any command defined
  with `defcommand` as a message. The commands are implemented as maps
  with a `rill.message/type` key indicating the command type with a
  qualified keyword.

  #### Command constructor

  For every `(defcommand cmd-name ...)` definition, a constructor
  function named `->cmd-name` is created that will create a valid
  rill.wheel command message:

      (->register \"my-id\" \"Some Name\")

      => {:rill.message/type :user/register,
          :user-id \"my-id\",
          :name \"Some Name\"}

  Note that the ->register function also takes the identifying
  properties of the `user` aggregate. This is required so that the
  correct `user` aggregate can be fetched from the repository.

  #### `transact!`

  You can run the given command message directly against the
  repository using `transact!`. This will fetch the aggregate using
  the `fetch-aggregate` function, calls `apply-command` and `commit!`
  the result.

      (ok? (transact! repository
                      (->register \"my-id\"
                                  \"Some Name\")))

      => true

  If your commands are invoked from some remote source (like a
  single-page application - see the `mpare-net/weir` project), these
  are the semantics you probably want (excluding authentication etc).

  #### `apply-command`

  The apply-command function is used by `transact!` and takes the
  aggregate to update and the command message and executes the
  `defcommand` `body`:

       (-> (get-user repo my-id)
           (apply-command (->register my-id my-name)))

  #### `fetch-aggregate`

  Also used by `transact!`, this multi-method takes the repository and
  the command message and returns the aggregate the command should be
  applied to.

  ### Commands as functions

  It's convenient to be able to call commands directly as regular
  names functions. For this purpose there are two flavors:

  #### `command-name!`

  A generated function named after the command with an exclamation
  mark added. Takes the `repository` and all properties to identify
  the `aggregate` plus additional command properties, and `commit!`
  the result.

       (ok? (register! repository \"user-id\" \"Some Name\"))

       => true

  #### `command-name`

  Another generated function that takes the `aggregate` as the first
  argument and the additional command arguments and applies the
  command to the `aggregate`. This does not commit but returns the
  updated `aggregate` or a `rejection`. You can chain successful calls
  to named command functions and `commit!` the result.

       (ok? (-> repository
              (get-user \"user-id\")
              (register \"Some Name\")
              (commit!)))

       => true

  ## See also

  - `rill.event-store`
  "
  (:refer-clojure :exclude [empty empty? type])
  (:require [rill.event-store :refer [retrieve-events append-events]]
            [rill.message :as message]
            [rill.wheel.repository :as repo]
            [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [rill.wheel.macro-utils :refer [parse-args keyword-in-current-ns parse-pre-post]]))

(defmulti apply-event*
  (fn [aggregate event]
    (:rill.message/type event)))

(defn apply-event
  "Update the properties of `aggregate` given `event`. Implementation
  for different event types will be given by `defevent`."
  [aggregate event]
  (apply-event* aggregate event))

(defn message?
  [m]
  (boolean (:rill.message/type m)))

(defn aggregate?
  "Test that `obj` is an aggregate."
  [obj]
  (boolean (and (::id obj)
                (::type obj))))

(s/fdef apply-event
        :ret aggregate?
        :args (s/cat :aggregate aggregate? :event message?))

(defmethod apply-event* :default
  [aggregate _]
  aggregate)

(defn apply-new-event
  "Apply a new event to the aggregate. The new events will be committed
  when the aggregate is committed to a repository."
  [aggregate event]
  (-> aggregate
      (apply-event event)
      (update ::new-events (fnil conj []) event)))

(defn new-events
  "The events that will be committed when this aggregate is committed."
  [aggregate]
  (::new-events aggregate))

(defn aggregate?
  "Test that `obj` is an aggregate."
  [obj]
  (boolean (and (::id obj)
                (::type obj))))

(defn empty
  "Create a new aggregate with id `aggregate-id` and no
  events. Aggregate version will be -1. Note that empty aggregates
  cannot be stored."
  [aggregate-id]
  (let [base {::id aggregate-id ::version -1}]
    (if (map? aggregate-id)
      (merge base aggregate-id)
      base)))

(defn new?
  "Test that the aggregate has no committed events."
  [aggregate]
  (= (::version aggregate) -1))

(s/fdef new?
        :args (s/cat :aggregate aggregate?)
        :ret boolean?)

(defn empty?
  "Test that the aggregate is new and has no uncommitted events."
  [aggregate]
  (and (new? aggregate)
       (clojure.core/empty? (::new-events aggregate))))

(s/fdef empty?
        :args (s/cat :aggregate aggregate?)
        :ret boolean?)

(defn exists
  "If aggregate is not new, return aggregate, otherwise nil."
  [aggregate]
  (when-not (new? aggregate)
    aggregate))

(s/fdef exists
        :args (s/cat :aggregate aggregate?)
        :ret (s/or :nil nil? :aggregate aggregate?))

(defn apply-stored-event
  "Apply a previously committed event to the aggregate. This
  increments the version of the aggregate."
  [aggregate event]
  (-> aggregate
      (apply-event event)
      (update ::version inc)))

(defn merge-aggregate-props
  [aggregate partial-event]
  (if (map? (::id aggregate))
    (merge partial-event (::id aggregate))
    partial-event))

(defn type-properties
  "The properties of the identifier of aggregate type `t`."
  [t]
  (-> (symbol (namespace t) (name t))
      resolve
      meta
      ::properties))

(s/def ::defevent-args
  (s/cat :event-name symbol?
         :aggregate-type qualified-keyword?
         :doc-string (s/? string?)
         :attr-map (s/? map?)
         :event-args (s/and vector?
                            (s/cat :aggregate-arg any?
                                   :event-properties (s/* symbol?)))
         :pre-post-map (s/? (s/and map?
                                   #(some % [:pre :post])))
         :body (s/* any?)))

(s/fdef defevent
        :args ::defevent-args)

(defmacro defevent
  "Defines function that takes aggregate + properties, constructs an
  event and applies the event as a new event to aggregate. Properties
  defined on the `aggregate-type` definition will be merged with the
  event; do not define properties with `defevent` that are already
  defined in the corresponding `defaggregate`.

  For cases where you only need the event and can ignore the
  aggregate, the function \"{name}-event\" is defined with the same
  signature. This function is used by the \"{name}\" function to
  generate the event before calling `apply-event` (see below).

  The given `prepost-map`, if supplied gets included in the definition
  of the \"{name}-event\" function.

  The given `body`, if supplied, defines an `apply-event` multimethod
  that applies the event to the aggregate. If no `body` is supplied,
  the default `apply-event` will be used, which will return the
  aggregate as is."
  {:arglists '([name aggregate-type doc-string? attr-map? [aggregate properties*] pre-post-map?  body*])}
  [& args]
  (let [{:keys                                    [event-name aggregate-type doc-string attr-map pre-post-map body]
         {:keys [aggregate-arg event-properties]} :event-args}
        (s/conform ::defevent-args args)]
    (let [event-name    (cond-> event-name
                          (seq doc-string)
                          (vary-meta assoc :doc doc-string)
                          :always
                          (vary-meta assoc
                                     ::event-fn true
                                     ::aggregate aggregate-type
                                     ::properties (mapv keyword event-properties)))
          n-event       (symbol (str (name event-name) "-event"))
          fetch-props   (type-properties aggregate-type)
          aggregate-sym (gensym "aggregate")
          handler-args  (into [aggregate-sym] event-properties)
          _             (when-not (vector? fetch-props)
                          (throw (IllegalStateException. (format "Can't fetch type properties for aggregate %s" (str aggregate-type)))))
          fetch-props   (mapv #(-> % name symbol) fetch-props)]
      (when-let [collisions (seq (filter (set fetch-props) event-properties))]
        (throw (IllegalStateException. (str "defevent " event-name " has properties colliding with definition of aggregate " (name aggregate-type) ": " (string/join ", " collisions)))))

      `(do ~(when (seq body)
              `(defmethod apply-event* ~(keyword-in-current-ns event-name)
                 [~aggregate-arg {:keys ~(vec event-properties)}]
                 ~@body))

           (defn ~(symbol (str "->" (name event-name)))
             ~(into fetch-props event-properties)
             ~@(when pre-post-map
                 [pre-post-map])
             ~(into {:rill.message/type (keyword-in-current-ns event-name)
                     :rill.wheel/type   aggregate-type}
                    (map (fn [k]
                           [(keyword k) k])
                         (concat fetch-props event-properties))))

           (defn ~n-event
             ~handler-args
             ~@(when pre-post-map
                 [pre-post-map])
             (merge-aggregate-props ~aggregate-sym
                                    ~(into {:rill.message/type (keyword-in-current-ns event-name)}
                                           (map (fn [k]
                                                  [(keyword k) k])
                                                event-properties))))
           (defn ~event-name
             ~handler-args
             (apply-new-event ~aggregate-sym (~n-event ~aggregate-sym ~@event-properties)))

           (s/fdef ~event-name
                   :args (s/cat :aggregate aggregate?
                                :event-props (s/* any?)))))))

(defn type
  "Return the type of this aggregate."
  [aggregate]
  (::type aggregate))



(defn repository
  "Return the repository of `aggregate`."
  [aggregate]
  {:pre  [(aggregate? aggregate)]
   :post [%]}
  (::repository aggregate))

;;;; Command handling

(defn rejection
  "Create a rejection for aggregate with reason."
  [aggregate reason]
  {::status :rejected ::reason reason ::aggregate aggregate})

(defn rejection?
  "Checks if result is rejected."
  [result]
  (= (::status result) :rejected))

(defn conflict?
  "Was there a conflict between fetching the aggregate and calling
   `commit`?"
  [result]
  (= (::status result) :conflict))

(defn reason
  "Return the reason for a `rejection`. Returns `:rill.wheel/conflict`
  for a conflict."
  [rejection]
  (if (conflict? rejection)
    ::conflict
    (::reason rejection)))

(defn aggregate
  "Return the aggregate from a result. If the result *is* an
  aggregate, returns it as is."
  [result]
  (if (aggregate? result)
    aggregate
    (::aggregate result)))

(defn ok?
  "Is result an `ok?` object?"
  [result]
  (= (::status result) :ok))

(defn ok
  "Creates an `ok?` object describing the committed events and
  aggregate."
  [aggregate]
  (let [events (::new-events aggregate)]
    {::status    :ok
     ::events    (->> events
                      (map-indexed (fn [index event]
                                     (-> event
                                         (assoc ::message/stream-id (::id aggregate))
                                         (assoc ::message/number (+ (::version aggregate) (inc index))))))
                      vec)
     ::aggregate (-> aggregate
                     (update ::version + (count events))
                     (assoc ::new-events []))}))

(defn conflict
  "Creates a `conflict`. Use `conflict` when there were changes to
  the aggregate in the repository in the time between fetching the
  aggregate and calling `commit!`."
  [aggregate]
  {::status :conflict ::aggregate aggregate})

(defn uncommitted?
  "`aggregate` has events applied that can be committed."
  [aggregate]
  (boolean (seq (:rill.wheel/new-events aggregate))))

(defn commit!
  "Commit the result of a command execution. If the command returned a
  `rejection` nothing is committed and the rejection is returned. If the
  result is an aggregate it is committed to the repository. If that
  succeeds an `ok` is returned. Otherwise a `conflict` is returned."
  [aggregate-or-rejection]
  (cond
    (rejection? aggregate-or-rejection)
    aggregate-or-rejection
    (repo/commit! (:rill.wheel/repository aggregate-or-rejection) aggregate-or-rejection)
    (ok aggregate-or-rejection)
    :else
    (conflict aggregate-or-rejection)))

(s/fdef commit!
        :args (s/cat :aggregate-or-rejection (s/or :aggregate aggregate?
                                                   :rejection rejection?))
        :ret (s/or :ok ok? :rejection rejection? ))

(defmulti fetch-aggregate
  "Given a command and repository, fetch the target aggregate."
  (fn [repo command]
    (:rill.message/type command)))

(defmulti apply-command*
  (fn [aggregate command]
    (:rill.message/type command)))

(defn command-result?
  [r]
  (or (rejection? r)
      (aggregate? r)))

(defn apply-command
  "Given a command and aggregate, apply the command to the
  aggregate. Should return an updated aggregate or a rejection."
  [aggregate command]
  (apply-command* aggregate command))

(s/fdef apply-command
        :args (s/cat :aggregate aggregate? :command message?)
        :ret command-result?)


(defn transact!
  "Run and commit the given command against the repository."
  [repo command]
  (-> (fetch-aggregate repo command)
      (apply-command command)
      (commit!)))

;;;;----TODO(Joost) Insert pre-post checks at the right places, update
;;;;----documentation

(s/def ::defcommand-args
  (s/cat :command-name symbol?
         :aggregate-type qualified-keyword?
         :doc-string (s/? string?)
         :attr-map (s/? map?)
         :command-args (s/and vector?
                              (s/cat :aggregate-arg any?
                                     :command-properties (s/* symbol?)))
         :pre-post-map (s/? (s/and map?
                                   #(some % [:pre :post])))
         :body (s/* any?)))

(s/fdef defcommand
        :args ::defcommand-args)

(defmacro defcommand
  "Defines a command as a named function that takes any arguments and
  returns an `aggregate` or `rejection` that can be passed to
  `commit!`.

  The metadata of the command may contain a
  `:rill.wheel/events` key, which will specify the types of
  the events that may be generated. As a convenience, the
  corresponding event functions are `declare`d automatically so the
  `defevent` statements can be written after the command. This usually
  reads a bit nicer.

       (defcommand answer-question ::question
         \"Try to answer a question\"
         [question user-id answer]
         (if (some-check question answer)
           (answered-correctly question user-id answer)
           (answered-incorrectly question user-id answer)))

  "
  {:arglists '([name aggregate-type doc-string? attr-map? [repository properties*] pre-post-map? body])}
  [& args]
  (let [{:keys                                      [command-name aggregate-type doc-string attr-map pre-post-map body]
         {:keys [aggregate-arg command-properties]} :command-args}
        (s/conform ::defcommand-args args)

        n           (-> command-name
                        (vary-meta assoc
                                   ::command-fn true
                                   ::aggregate aggregate-type
                                   ::properties (mapv keyword command-properties))
                        (vary-meta merge attr-map))
        m           (meta n)
        fetch-props (type-properties aggregate-type)
        _           (when-not (vector? fetch-props)
                      (throw (IllegalStateException. (format "Can't fetch type properties for aggregate %s" (str aggregate-type)))))
        fetch-props (mapv #(-> % name symbol) fetch-props)
        getter      (symbol (namespace aggregate-type) (str "get-" (name aggregate-type)))]
    `(do ~(when-let [event-keys (::events m)]
            `(declare ~@(map (fn [k]
                               (symbol (subs (str k) 1)))
                             event-keys)))

         (defmethod apply-command* ~(keyword-in-current-ns n)
           [~aggregate-arg {:keys ~(vec command-properties)}]
           ~@body)

         (defmethod fetch-aggregate ~(keyword-in-current-ns n)
           [repository# {:keys ~fetch-props}]
           (~getter repository# ~@fetch-props))

         (defn ~(symbol (str "->" n))
           ~(format "Construct a %s command message" (name n))
           ~(into fetch-props command-properties)
           ~@(when pre-post-map
               [pre-post-map])
           ~(into {:rill.message/type (keyword-in-current-ns n)}
                  (map (fn [k]
                         [(keyword k) k])
                       (into fetch-props command-properties))))

         (defn ~(symbol (str n "-command"))
           ~(format "Construct a %s command message. Deprecated, use `->%s` instead" (name n) (name n))
           {:deprecated true}
           ~(into fetch-props command-properties)
           ~(cons (symbol (str "->" n)) (into fetch-props command-properties)))

         ~(let [ag-arg (gensym "aggregate")]
            `(defn ~n
               ~(format "Apply command %s to %s. Does not commit" (name n) (name aggregate-type))
               ~(into [ag-arg] command-properties)
               (apply-command ~ag-arg (~(symbol (str "->" n))
                                       ~@(map (fn [p]
                                                `(get ~ag-arg ~(keyword p)))
                                              fetch-props)
                                       ~@command-properties))))

         (defn ~(symbol (str (name n) "!"))
           ~(format "Apply command %s to repository and commit" (name n))
           [repository# ~@fetch-props ~@command-properties]
           (transact! repository# (~(symbol (str "->" (name n))) ~@fetch-props ~@command-properties))))))


(defmacro defaggregate
  "Defines an aggregate type, and aggregate-id function. The
  aggregate's id key is a map with a key for every property in
  `properties*`, plus the aggregate type, a qualified keyword from
  `name`.

  Also defines a function `get-{name}`, which takes an additional
  first repository argument and retrieves the aggregate.

  events? and commands? are sequences of event specs and command specs
  and passed to `defevent` and `rill.wheel/defcommand`
  respectively.


  "
  {:arglists '([name doc-string? attr-map? [properties*] pre-post-map? events? commands?])}
  [& args]
  (let [[n descriptor-args & body] (parse-args args)
        n                          (vary-meta n assoc
                                              ::descriptor-fn true
                                              ::properties (mapv keyword descriptor-args))
        [prepost body]             (parse-pre-post body)
        repo-arg                   `repository#]
    `(do (defn ~n
           ~(format "Build empty uncommitted `%s` from positional descriptor arguments" n)
           ~(vec descriptor-args)
           ~@(when prepost
               [prepost])
           (empty (sorted-map ::type ~(keyword-in-current-ns n)
                              ~@(mapcat (fn [k]
                                          [(keyword k) k])
                                        descriptor-args))))

         (s/fdef ~n :ret aggregate?)

         (defn ~(symbol (str "get-" (name n)))
           ~(format "Fetch `%s` from repository `%s`" (name n) (name repo-arg))
           ~(into [repo-arg] descriptor-args)
           (-> (repo/update ~repo-arg (apply ~n ~descriptor-args))
               (assoc :rill.wheel/repository ~repo-arg)))

         (s/fdef ~(symbol (str "get-" (name n)))
                 :args (s/cat :repository repo/repository? :descriptor (s/* any?))
                 :ret aggregate?)

         ~@(map (fn [event]
                  `(defevent ~(first event) ~(keyword-in-current-ns n) ~@(rest event)))
                (first body))
         ~@(map (fn [command]
                  `(defcommand ~(first command) ~(keyword-in-current-ns n) ~@(rest command)))
                (second body)))))
