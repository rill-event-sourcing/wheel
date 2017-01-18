(ns rill.wheel.wrap-upcasts
  (:require [rill.event-store :refer [EventStore append-events retrieve-events-since]]))

(defrecord Wrapper [wrapped upcast]
  EventStore
  (append-events [_ stream-id from-version events]
    (append-events wrapped stream-id from-version events))
  (retrieve-events-since [_ props cursor wait-for-seconds]
    (map upcast (retrieve-events-since wrapped props cursor wait-for-seconds))))

;; leave these out of the documentation
(alter-meta! #'->Wrapper assoc :private true)
(alter-meta! #'map->Wrapper assoc :private true)

(defn wrap-upcasts
  "Event Store wrapper that migrates retrieved events.

  Upcasts are functions that take an event and return an upgraded
  event. Upcasts are applied from left to right."
  [wrapped & upcasts]
  (map->Wrapper {:wrapped wrapped :upcast (apply comp (reverse upcasts))}))
