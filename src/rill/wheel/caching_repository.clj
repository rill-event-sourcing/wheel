(ns rill.wheel.caching-repository
  (:require [rill.event-store :as event-store]
            [rill.wheel.aggregate :as aggregate]
            [rill.wheel.repository :refer [Repository]]
            [clojure.core.cache :as cache]))

(defn ensure-aggregate-atom-is-in-cache
  [state aggregate-id]
  (if (cache/has? state aggregate-id)
    (cache/hit state aggregate-id)
    (cache/miss state aggregate-id (atom nil))))

(defn aggregate-atom
  [cache aggregate-id]
  (get (swap! cache ensure-aggregate-atom-is-in-cache aggregate-id) aggregate-id))

(defn update-aggregate
  [aggregate event-store]
  (when aggregate
    (reduce aggregate/apply-stored-event aggregate
            (event-store/retrieve-events-since event-store (::aggregate/id aggregate)
                                               (::aggregate/version aggregate) 0))))

(defrecord CachingRepository [event-store cache]
  Repository
  (commit-aggregate! [repo aggregate]
    {:pre [(::id aggregate)]}
    (if-let [events (seq (::aggregate/new-events aggregate))]
      (event-store/append-events event-store (::aggregate/id aggregate)
                                 (::aggregate/version aggregate) events)
      true))
  (fetch-aggregate [repo aggregate-id]
    (let [a (aggregate-atom cache aggregate-id)]
      ;; not using `swap!` here because update-aggregate might block
      ;; on network to event store. worst case, we need to fetch a few
      ;; more events next time we fetch this aggregate.
      (reset! a (update-aggregate @a event-store)))
    (when-let [events (seq (event-store/retrieve-events event-store aggregate-id))]
      (reduce aggregate/apply-stored-event (aggregate/empty-aggregate aggregate-id) events))))


(defn caching-repository
  ([event-store cache]
   (->CachingRepository event-store (atom cache)))
  ([event-store]
   (caching-repository event-store (cache/lru-cache-factory {} :threshold 20000))))
