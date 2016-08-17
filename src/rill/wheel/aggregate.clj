(ns rill.wheel.aggregate

  "## Defining aggregates and events

  ### Synopsis

      (require '[rill.wheel.aggregate :as aggregate
                 :refer [defaggregate defevent]])

      (defaggregate user
        \"a user is identified by a single `email` property\"
        [email])

      (defevent registered
        \"user was correctly registered\"
        [user]
        (assoc user :registered? true))

      (defevent unregistered
        \"user has unregistered\"
        [user]
        (dissoc user :registered?))

      (-> (user \"user@example.com\") registered :registered?)
        => true

      (registered-event (user \"user@example.com\"))
        => {:rill.message/type :user/registered,
            :email \"user@example.com\",
            :rill.wheel.aggregate/type :user/user}

      (aggregate/new-events some-aggreate)
        => seq-of-events

  ### Store and retrieve aggregates in a repository

      (repository/commit some-repository
            (-> (user \"user@example.com)
                (registered)))
      ;; ...
      (get-user some-repository \"user@example.com\")


  ### See also

  - `rill.wheel.command`
  - `rill.event-store`
  "
  {:doc/format :markdown}

  (:refer-clojure :exclude [empty empty? type])
  (:require [rill.event-store :refer [retrieve-events append-events]]
            [rill.wheel.repository :as repo]
            [rill.wheel.macro-utils :refer [parse-args keyword-in-current-ns parse-pre-post]]))

(defmulti apply-event
  "Update the properties of `aggregate` given `event`. Implementation
  for different event types will be given by `defevent`"
  (fn [aggregate event]
    (:rill.message/type event)))

(defmethod apply-event :default
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
  "Test that `obj` is an aggregate"
  [obj]
  (boolean (::id obj)))

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

(defn empty?
  "Test that the aggregate is new and has no uncommitted events"
  [aggregate]
  (and (new? aggregate)
       (clojure.core/empty? (::new-events aggregate))))

(defn exists
  "If aggregate is not new, return aggregate, otherwise nil"
  [aggregate]
  (when-not (new? aggregate)
    aggregate))

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

(defmacro defevent
  "Defines function that takes aggregate + properties, constructs an
  event and applies the event as a new event to aggregate. Properties
  defined on the aggregate definition will be merged with the event;
  do not define properties with `defevent` that are already defined in
  the corresponding `defaggregate`.

  For cases where you only need the event and can ignore the
  aggregate, the function \"{name}-event\" is defined with the same
  signature. This function is used by the \"{name}\" function to
  generate the event befor calling `apply-event` (see below).

  The given `prepost-map`, if supplied gets included in the definiton
  of the \"{name}-event\" function.

  The given `body`, if supplied, defines an `apply-event` multimethod
  that applies the event to the aggregate. If no `body` is supplied,
  the default `apply-event` will be used, which will return the
  aggregate as is."
  {:arglists '([name doc-string? attr-map? [aggregate properties*] pre-post-map?  body*])}
  [& args]
  (let [[n [aggregate & properties :as handler-args] & body] (parse-args args)
        [prepost body]                                       (parse-pre-post body)
        n                                                    (vary-meta n assoc :rill.wheel.aggregate/event-fn true)
        n-event                                              (symbol (str (name n) "-event"))]
    `(do ~(when (seq body)
            `(defmethod apply-event ~(keyword-in-current-ns n)
               [~aggregate {:keys ~(vec properties)}]
               ~@body))
         (defn ~n-event
           (~handler-args
            ~@(when prepost
                [prepost])
            (merge-aggregate-props ~aggregate
                                   ~(into {:rill.message/type (keyword-in-current-ns n)}
                                          (map (fn [k]
                                                 [(keyword k) k])
                                               properties)))))
         (defn ~n
           (~handler-args
            (apply-new-event ~aggregate (~n-event ~aggregate ~@properties)))))))

;;---TODO(Joost) allow for inlining event definitions in the aggregate
;;---possibly also commands.
(defmacro defaggregate
  "Defines an aggregate type, and aggregate-id function. The
  aggregate's id key is a map with a key for every property in
  `properties*`, plus the aggregate type, a qualified keyword from
  `name`.

  Also defines a function `get-{name}`, which takes an additional
  first repository argument and retrieves the aggregate."
  {:arglists '([name
  doc-string? attr-map? [properties*] pre-post-map?])}
  [& args]
  (let [[n descriptor-args & body] (parse-args args)
        n                          (vary-meta n assoc :rill.wheel.aggregate/descriptor-fn true)
        [prepost body]             (parse-pre-post body)
        repo-arg                   `repository#]
    (when (seq body)
      (throw (IllegalArgumentException. "defaggregate takes only pre-post-map as after properties vector.")))
    `(do (defn ~n
           ~(vec descriptor-args)
           ~@(when prepost
               [prepost])
           (empty (sorted-map ::type ~(keyword-in-current-ns n)
                              ~@(mapcat (fn [k]
                                          [(keyword k) k])
                                        descriptor-args))))
         (defn ~(symbol (str "get-" (name n)))
           ~(format "Fetch `%s` from repository `%s`" (name n) (name repo-arg))
           ~(into [repo-arg] descriptor-args)
           (repo/update ~repo-arg (apply ~n ~descriptor-args))))))

(defn type
  "Return the type of this aggregate"
  [aggregate]
  (::type aggregate))
