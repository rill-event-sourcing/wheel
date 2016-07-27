(ns easter.command
  (:require [easter.aggregate :as aggregate]))

(defn rejected? [result]
  (= (::status result) :rejected))

(defn ok? [result]
  (= (::status result) :ok))

(defn conflict? [result]
  (= (::status result) :conflict))

(defn ok [aggregate]
  {::status :ok ::events (::new-events aggregate) ::aggregate aggregate})

(defn reject
  [aggregate reason]
  {::status :rejected ::reason reason ::aggregate aggregate})

(defn conflict
  [aggregate]
  {::status :conflict ::aggregate aggregate})

(defn commit!
  [aggregate store]
  (if (aggregate/commit! aggregate store)
    (ok aggregate)
    (conflict aggregate)))
