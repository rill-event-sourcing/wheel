(ns rill.wheel.repository
  (:refer-clojure :exclude [update]))

(defprotocol Repository
  (commit! [repo aggregate]
    "Commit changes to `aggregate` by storing its new events.
  Returns `true` on success or when there are no new events.")
  (update [repo aggregate]
    "Update an aggregate by applying any not previously applied stored
     events, as determined by `:rill.wheel.aggregate/version`.

     Returns the given `aggregate` if its
     `:rill.wheel.aggregate/version` = 1 and no events are stored for
     it"))
