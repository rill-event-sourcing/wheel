(ns rill.wheel.saga
  "# Sagas in Rill/Wheel

  The transactional boundary in Rill is the aggregate, hence the
  standard `defcommand` macro assumes that commands affect only one
  aggregate. Often, use cases and workflows span multiple aggregates
  over time. Sagas are entities that manage the coordination of events
  and commands over multiple aggregates.

  ## Sagas describe potentially long-running processes

  ## Sagas are asynchronous

  ## Sagas can react to outside events

  ## Sagas are inside-out aggregates

  Standard aggregates accept commands and generate events, sagas accept
  events (from anywhere) and generate commands. You can use sagas to
  create reactions to events in the system. Since reactive systems can
  be hard to understand, we try to keep the sagas as simple as possible.

  - Sagas carry minimal state; only a single keyword
  - Sagas dispatch on event type only
  - Sagas only generate commands on a state transition

  ## Sagas are reified state machines

  Sagas register state transitions as handlers on event types.

    FetchSaga(event) -> S1
    apply-event(S1, event) -> S2
    if new-events(S2) ;; state machine can ignore events
      commit(S2) -> result
      when (ok? result)
        run-command(to-run(S2))
  "
  {:doc/format :markdown}
  (:require [rill.message :as message]
            [rill.wheel.aggregate :refer [defevent defaggregate apply-event]]
            [rill.wheel.macro-utils :refer [parse-args parse-pre-post keyword-in-current-ns]]))

(def ^:private saga-registry
  "Atom containing event handlers indexed by event-type and name"
  (atom {}))

(defn add-handler
  [event-type name f]
  (swap! saga-registry assoc-in [event-type name] f))

(defn remove-handler
  [event-type name]
  (swap! saga-registry update event-type dissoc name))

(defn call-handlers
  [repo e ex-handler]
  (let [event-type (::message/type e)]
    (doseq [h (vals (get @saga-registry event-type))]
      (try
        (h repo e)
        (catch Throwable t
          (ex-handler t))))))

(defn handler-name
  [saga-name]
  (symbol (str (name saga-name) "-event")))

(defn handled?
  "Test whether the `saga` already handled `event`. Events are tested
  for by comparing for their `:rill.message/number` and
  `:rill.message/stream-id` properties"
  [saga event]
  (contains? (:handled saga) (select-keys event [::message/number ::message/stream-id])))

#_(defn install-aggregate-handlers
    [saga-name transitions]
    (doseq [event-type (vals transitions)]
      (add-handler event-type (handler-name saga-name)
                   (fn [repo event]
                     (let [saga ....]
                       ...
                       )))))

(defmacro defsaga
  "Sagas receive events and generate commands."
  {:arglists    '([name doc-string? attr-map? [properties*] transitions commands])}
  [& args]
  (let [[n descriptor-args & body] (parse-args args)
        n                          (vary-meta n assoc ::descriptor-fn true)
        transitions                (-> n meta ::transitions)]
    (when-not (map? transitions)
      (throw (IllegalArgumentException. "defsaga must have `rill.wheel.saga/transitions` map in metadata")))
    #_(when (seq body)
        (throw (IllegalArgumentException. "defsaga takes no body or pre-post-map after properties vector")))

    `(do (defaggregate ~n ~descriptor-args)




         #_(defmethod apply-event ~(handler-name n)
             [~n ~event-arg]
             (update ~n :handled (fnil conj #{}) (select-keys ~event-arg [::message/number ::message/stream-id]))))))
