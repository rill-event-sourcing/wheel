(ns rill.wheel.wrap-new-events-callback
  "Provide a method for listening to events created only by this
  process."
  (:require [rill.event-store :refer [EventStore append-events retrieve-events-since]]))

(defrecord Wrapper [wrapped callback]
  EventStore
  (retrieve-events-since [this props cursor wait-for-seconds]
    (retrieve-events-since wrapped props cursor wait-for-seconds))
  (append-events [this props from-version events]
    (when (append-events wrapped props from-version events)
      (doseq [e events]
        (callback (-> (if (map? props)
                        (merge e props)
                        e)
                      (assoc :rill.message/stream-id props))))
      true)))

(defn wrap-new-events-callback
  "Create an `event-store` wrapper that will synchronously call
  `(callback event)` for every successfully committed event.

  `callback` will be called for every event in commit order."
  [event-store callback]
  (->Wrapper event-store callback))

;; leave these out of the documentation
(alter-meta! #'->Wrapper assoc :private true)
(alter-meta! #'map->Wrapper assoc :private true)
