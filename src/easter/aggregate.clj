(ns easter.aggregate
  (:require [rill.event-store :refer [retrieve-events append-events]]))

(defmulti apply-event (fn [_ event]
                        (:rill.message/type event)))

(defn new-event
  [aggregate event]
  (-> aggregate
      (apply-event event)
      (update ::new-events (fnil conj []) event)))

(defn mk-aggregate
  [aggregate-id]
  {::id aggregate-id ::version -1})

(defn init
  [aggregate-id creation-event]
  (-> (mk-aggregate aggregate-id)
      (new-event creation-event)))

(defn apply-stored-event
  [aggregate event]
  (-> aggregate
      (apply-event event)
      (update ::version inc)))

(defn fetch [store aggregate-id]
  (when-let [events (seq (retrieve-events store aggregate-id))]
    (reduce apply-stored-event (mk-aggregate aggregate-id) events)))

(defn commit!
  [aggregate store]
  {:pre [(::id aggregate)]}
  (if-let [events (seq (::new-events aggregate))]
    (append-events store (::id aggregate) (::version aggregate) events)
    true))

(defn- parse-args
  "provide defn-style doc-string suppport for defapply"
  [[sym & [doc-string? :as rst-args]]]
  (if (string? doc-string?)
    (into [(vary-meta sym assoc :doc doc-string?)] (drop 1 rst-args))
    (into [sym] rst-args)))

(defn- keyword-in-current-ns
  [sym]
  (keyword (name (ns-name *ns*)) (name sym)))

(defmacro defapply
  "Defines an event as a named function that takes args and returns a new rill.message.

  Also defines an apply-event multimethod that applies the event to
  the aggregate."
  {:arglists '([name doc-string? [aggregate properties*] pre-post-map? body])}
  [& args]
  (let [[n [aggregate & properties :as handler-args] & body] (parse-args args)]
    `(do (defmethod apply-event ~(keyword-in-current-ns n)
           [~aggregate {:keys ~(vec properties)}]
           ~@body)
         (defn ~n ~(vec properties)
           ~(into {:rill.message/type (keyword-in-current-ns n)}
                  (map (fn [k]
                         [(keyword k) k])
                       properties))))))
