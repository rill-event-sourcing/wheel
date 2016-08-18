(ns rill.wheel.bare-repository
  "Defines a minimal repository implementation."
  (:require [rill.event-store :as event-store]
            [rill.wheel.aggregate :as aggregate]
            [rill.wheel.repository :refer [Repository]]))

(defrecord BareRepository [event-store]
  Repository
  (commit! [repo aggregate]
    (assert (aggregate/aggregate? aggregate) (str "Attempt to commit non-aggregate " (pr-str aggregate)))
    (if-let [events (seq (::aggregate/new-events aggregate))]
      (event-store/append-events event-store (::aggregate/id aggregate) (::aggregate/version aggregate) events)
      true))
  (update [repo aggregate]
    (reduce aggregate/apply-stored-event aggregate (event-store/retrieve-events-since event-store (::aggregate/id aggregate) (::aggregate/version aggregate) 0))))

;; leave these out of the documentation
(alter-meta! #'->BareRepository assoc :private true)
(alter-meta! #'map->BareRepository assoc :private true)

(defn bare-repository
  "A bare-bones repository that stores its events in a rill
  event-store"
  [event-store]
  (->BareRepository event-store))
