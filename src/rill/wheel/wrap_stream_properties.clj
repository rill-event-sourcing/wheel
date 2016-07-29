(ns rill.wheel.wrap-stream-properties
  (:require [rill.event-store :refer [EventStore retrieve-events-since append-events]]
            [rill.event-stream :refer [all-events-stream-id]]))

(defn props->stream-id
  [props]
  (cond (map? props)
        (pr-str (mapcat (fn [k]
                          [k (get props k)])
                        (sort (keys props))))
        (string? props)
        props))

(defn stream-id->props
  [stream-id]
  (if (string? stream-id)
    (into {} (partition 2 (read-string stream-id)))
    stream-id))

(defrecord StreamPropertiesWrapper [delegated-event-store]
  EventStore
  (retrieve-events-since [this props cursor wait-for-seconds]
    (if (= props all-events-stream-id)
      (map (fn [e]
             (merge e (stream-id->props (:rill.message/stream-id e))))
           (retrieve-events-since delegated-event-store (props->stream-id props) cursor wait-for-seconds))
      (retrieve-events-since delegated-event-store (props->stream-id props) cursor wait-for-seconds)))
  (append-events [this props from-version events]
    (let [ks (keys props)]
      (append-events delegated-event-store (props->stream-id) from-version (map #(apply dissoc % ks))))))

(defn wrap-stream-properties
  [event-store]
  (map->StreamPropertiesWrapper {:delegated-event-store event-store}))
