(ns rill.wheel.trigger
  "Triggers are called when events are committed."
  (:require [rill.message :as message]
            [rill.wheel :as wheel]
            [rill.wheel.repository :as repository :refer [Repository]]))

(defonce
  ^{:doc "Global registry of triggers. Atom containing map of
  event-type -> key -> callback."}
  triggers
  (atom {}))

(defn install-trigger
  "Globally register a callback `f` for `event-type` with key
  `key`. Replaces previously installed trigger with `event-type` and
  `key`.

  When a matching `event` is succesfully committed to `repository`,
  callback will be called as `(f repository target-aggregate event)`,
  where `target-aggregate` is the target of `event` in the state prior
  to commit."
  [event-type key f]
  (swap! triggers assoc-in [event-type key] f))

(defn remove-trigger
  [event-type key]
  "Remove the trigger for `event-type` and `key` from the global
  registry."
  (swap! triggers update event-type dissoc key))

(defn- call-triggers [repo aggregate events]
  (doseq [event events]
    (doseq [trigger (vals (@triggers (message/type event)))]
      (trigger repo aggregate event))))

(defrecord WithTriggers [wrapped]
  Repository
  (repository/commit! [this aggregate]
    (let [events (wheel/new-events aggregate)]
      (when (repository/commit! wrapped aggregate)
        (call-triggers this aggregate events)
        true)))
  (repository/update [this aggregate]
    (repository/update wrapped aggregate))
  (repository/event-store [this]
    (repository/event-store wrapped)))

(defn with-triggers
  "Wrap a repository with triggers. The standard `rill.wheel`
  repository constructors call this wrapper so library users should
  not have to call this function."
  [repo]
  (->WithTriggers repo))

;; leave these out of the documentation
(alter-meta! #'->WithTriggers assoc :private true)
(alter-meta! #'map->WithTriggers assoc :private true)

