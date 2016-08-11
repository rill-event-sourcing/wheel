(ns rill.wheel.aggregate
  (:refer-clojure :exclude [empty empty?])
  (:require [rill.event-store :refer [retrieve-events append-events]]
            [rill.wheel.repository :as repo]
            [rill.wheel.macro-utils :refer [parse-args keyword-in-current-ns]]))

(defmulti apply-event
  "Update the properties of `aggregate` given `event`. Implementation
  for different event types will be given by `defevent`"
  (fn [aggregate event]
    (:rill.message/type event)))

(defn apply-new-event
  "Apply a new event to the aggregate. The new events will be committed
  when the aggregate is committed to a repository."
  [aggregate event]
  (-> aggregate
      (apply-event event)
      (update ::new-events (fnil conj [])
              (if (map? (::id aggregate))
                (merge event (::id aggregate))
                event))))

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

;;---TODO(joost) make body optional; should return aggregate
(defmacro defevent
  "Defines function that takes aggregate + properties, constructs an
  event and applies the event as a new event to aggregate. Properties
  defined on the aggregate definition will be merged with the event;
  do not define properties with `defevent` that are already defined in
  the corresponding `defaggregate`.

  The given `body` defines an `apply-event` multimethod that applies
  the event to the aggregate.  {:arglists '([name doc-string?
  attr-map? [aggregate properties*] pre-post-map? body])}"
  [& args]
  (let [[n [aggregate & properties :as handler-args] & body] (parse-args args)
        n                                                    (vary-meta n assoc :rill.wheel.aggregate/event-fn true)]
    `(do (defmethod apply-event ~(keyword-in-current-ns n)
           [~aggregate {:keys ~(vec properties)}]
           ~@body)
         (defn ~n
           (~handler-args
            (apply-new-event ~aggregate
                             ~(into {:rill.message/type (keyword-in-current-ns n)}
                                    (map (fn [k]
                                           [(keyword k) k])
                                         properties))))))))

(defmacro defaggregate
  "Defines an aggregate type."
  {:arglists '([name doc-string? attr-map? [properties*] pre-post-map?])}
  [& args]
  (let [[n descriptor-args & body] (parse-args args)
        n                          (vary-meta n assoc :rill.wheel.aggregate/descriptor-fn true)
        repo-arg                   `repository#]
    (when (and (seq body)
               (or (< 1 (count (seq body)))
                   (not (map? (first body)))
                   (not (or (:pre (first body))
                            (:post (first body))))))
      (throw (IllegalArgumentException. "defaggregate takes only pre-post-map as after properties vector.")))
    `(defn ~n
       (~(vec descriptor-args)
        (empty (sorted-map
                ~@(mapcat (fn [k]
                            [(keyword k) k])
                          descriptor-args))))
       (~(into [repo-arg] descriptor-args)
        (repo/update ~repo-arg (apply ~n ~descriptor-args))))))
