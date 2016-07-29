(ns rill.wheel.aggregate
  (:refer-clojure :exclude [empty empty?])
  (:require [rill.event-store :refer [retrieve-events append-events]]
            [rill.wheel.macro-utils :refer [parse-args keyword-in-current-ns]]))

(defmulti apply-event
  "Update the properties of `aggregate` given `event`. Implementation
  for different event types will be given by `defevent`"
  (fn [aggregate event]
    (:rill.message/type event)))

(defn apply-new-event
  "Apply a new event to the aggreate. The new events will be committed
  when the aggregate is committed to a repository."
  [aggregate event]
  (-> aggregate
      (apply-event event)
      (update ::new-events (fnil conj []) event)))

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

(defmacro defevent
  "Defines an event as a multi-arity function that takes properties
  and returns a new rill.message, or aggregate + properties and that
  applies the event as a new event to aggregate.

  The given `body` defines an `apply-event` multimethod that
  applies the event to the aggregate."
  {:arglists '([name doc-string? [aggregate properties*] pre-post-map? body])}
  [& args]
  (let [[n [aggregate & properties :as handler-args] & body] (parse-args args)
        n (vary-meta n assoc :rill.wheel.aggregate/event-fn true)]
    `(do (defmethod apply-event ~(keyword-in-current-ns n)
           [~aggregate {:keys ~(vec properties)}]
           ~@body)
         (defn ~n
           (~(vec properties)
            ~(into {:rill.message/type (keyword-in-current-ns n)}
                   (map (fn [k]
                          [(keyword k) k])
                        properties)))
           (~handler-args
            (apply-new-event ~aggregate (~n ~@properties)))))))

