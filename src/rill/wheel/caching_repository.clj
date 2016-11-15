(ns rill.wheel.caching-repository
  "Defines a repository that takes a cache for its aggregates.

  Calling `rill.wheel.repository/update` on this repository will still
  call the backing `event-store` to retrieve any new events not
  already applied to the cached aggregate - this ensures that after
  calling `update` the aggregate is as up-to-date as possible.
  "
  (:refer-clojure :exclude [update])
  (:require [clojure.core.cache :as cache]
            [rill.event-store :as event-store]
            [rill.wheel :as aggregate]
            [rill.wheel.repository :refer [Repository]]
            [rill.wheel.trigger :refer [with-triggers]]))

(defn- ensure-aggregate-atom-is-in-cache
  [state aggregate-id]
  (if (cache/has? state aggregate-id)
    (cache/hit state aggregate-id)
    (cache/miss state aggregate-id (atom (aggregate/empty aggregate-id)))))

(defn- aggregate-atom
  [cache aggregate-id]
  (get (swap! cache ensure-aggregate-atom-is-in-cache aggregate-id) aggregate-id))

(defn- update-aggregate
  [aggregate event-store]
  (reduce aggregate/apply-stored-event aggregate
          (event-store/retrieve-events-since event-store (::aggregate/id aggregate)
                                             (::aggregate/version aggregate) 0)))

(defrecord CachingRepository [event-store cache]
  Repository
  (commit! [repo aggregate]
    {:pre [(::id aggregate)]}
    (if-let [events (seq (::aggregate/new-events aggregate))]
      (event-store/append-events event-store (::aggregate/id aggregate)
                                 (::aggregate/version aggregate) events)
      true))
  (update [repo supplied-aggregate]
    (let [a (aggregate-atom cache (::aggregate/id supplied-aggregate))
          fetched-aggregate @a
          to-update (if (< (::aggregate/version supplied-aggregate)
                           (::aggregate/version fetched-aggregate))
                      fetched-aggregate
                      supplied-aggregate)]
      ;; not using `swap!` here because update-aggregate might block
      ;; on network to event store. worst case, we need to fetch a few
      ;; more events next time we fetch this aggregate.
      (reset! a (update-aggregate to-update event-store))))
  (event-store [repo] event-store))


;; leave these out of the documentation
(alter-meta! #'->CachingRepository assoc :private true)
(alter-meta! #'map->CachingRepository assoc :private true)

(defn caching-repository
  "Construct a new caching repository backed by a rill event-store and
  a `clojure.core.cache` cache. By default a least-recently-used cache
  of 20000 items is used."
  ([event-store cache]
   (with-triggers (->CachingRepository event-store (atom cache))))
  ([event-store]
   (caching-repository event-store (cache/lru-cache-factory {} :threshold 20000))))
