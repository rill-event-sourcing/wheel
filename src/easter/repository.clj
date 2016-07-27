(ns easter.repository
  (:require [rill.event-store :as event-store]
            [easter.aggregate :as aggregate]))

(defprotocol Repository
  (commit-aggregate! [repo aggregate])
  (fetch-aggregate [repo aggregate-id]))

(defrecord BareRepository [event-store]
  Repository
  (commit-aggregate! [repo aggregate]
    {:pre [(::id aggregate)]}
    (if-let [events (seq (::aggregate/new-events aggregate))]
      (event-store/append-events event-store (::aggregate/id aggregate) (::aggregate/version aggregate) events)
      true))
  (fetch-aggregate [repo aggregate-id]
    (when-let [events (seq (event-store/retrieve-events event-store aggregate-id))]
      (reduce aggregate/apply-stored-event (aggregate/mk-aggregate aggregate-id) events))))


(defn bare-repository
  [event-store]
  (->BareRepository event-store))
