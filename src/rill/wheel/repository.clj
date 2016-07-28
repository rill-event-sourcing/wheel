(ns rill.wheel.repository
  "Defines the basic aggregate Repository protocol and a minimal
  implementation."
  (:require [rill.event-store :as event-store]
            [rill.wheel.aggregate :as aggregate]))

(defprotocol Repository
  (commit-aggregate! [repo aggregate]
    "Commit changes to `aggregate` by storing its new events.
  Returns `true` on success or when there are no new events.")
  (fetch-aggregate [repo aggregate-id]
    "Fetch an aggregate by constructing it from its stored events.
  Returns nil if no events are stored for the given `aggregate-id`"))

(defrecord BareRepository [event-store]
  Repository
  (commit-aggregate! [repo aggregate]
    {:pre [(::id aggregate)]}
    (if-let [events (seq (::aggregate/new-events aggregate))]
      (event-store/append-events event-store (::aggregate/id aggregate) (::aggregate/version aggregate) events)
      true))
  (fetch-aggregate [repo aggregate-id]
    (when-let [events (seq (event-store/retrieve-events event-store aggregate-id))]
      (reduce aggregate/apply-stored-event (aggregate/empty-aggregate aggregate-id) events))))

(defn bare-repository
  "A bare-bones repository that stores its events in a rill
  event-store"
  [event-store]
  (->BareRepository event-store))
