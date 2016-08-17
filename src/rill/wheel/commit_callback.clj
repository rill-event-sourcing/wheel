(ns rill.wheel.commit-callback
  (:require [rill.event-store :refer [EventStore retrieve-events-since append-events]]))

(defrecord TrackCommits [delegated-event-store callback]
  EventStore
  (retrieve-events-since [this stream-id cursor wait-for-seconds]
    (retrieve-events-since delegated-event-store stream-id cursor wait-for-seconds))
  (append-events [this stream-id from-version events]
    (when (append-events delegated-event-store stream-id from-version events)
      (callback stream-id from-version events)
      true)))

(defn wrap-commit-callback
  "Call (f stream-id from-version events) after every successful
  commit."
  [event-store f]
  (map->TrackCommits {:delegated-event-store event-store :callback f}))


