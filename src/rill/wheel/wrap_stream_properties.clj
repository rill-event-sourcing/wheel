(ns rill.wheel.wrap-stream-properties
  (:require [rill.event-store :refer [EventStore retrieve-events-since append-events]]
            [rill.event-stream :refer [all-events-stream-id]]))


(defn valid-props?
  [p]
  (if (coll? p)
    (or (sequential? p)
        (sorted? p))
    true))

(defrecord StreamPropertiesWrapper [delegated-event-store]
  EventStore
  (retrieve-events-since [this props cursor wait-for-seconds]
    (assert (valid-props? props)
            "Can't use unsorted maps or unsorted sets as props")
    (let [events (retrieve-events-since delegated-event-store props cursor wait-for-seconds)]
      (cond
        (= props all-events-stream-id)
        ;; must fetch props for each event separately
        (map (fn [e]
               (if (map? (:rill.message/stream-id e))
                 (merge e (:rill.message/stream-id e))
                 e))
             events)
        (map? props)
        ;; set these props on every event
        (map (fn [e] (merge e props))
             events)
        :else
        events)))
  (append-events [this props from-version events]
    (assert (valid-props? props)
            "Can't use unsorted maps or unsorted sets as props")
    (if (map? props)
      (append-events delegated-event-store props from-version (map #(apply dissoc % (keys props)) events))
      (append-events delegated-event-store props from-version events))))

(defn wrap-stream-properties
  [event-store]
  (map->StreamPropertiesWrapper {:delegated-event-store event-store}))
