(ns rill.wheel.repository)

(defprotocol Repository
  (commit! [repo aggregate]
    "Commit changes to `aggregate` by storing its new events.
  Returns `true` on success or when there are no new events.")
  (fetch [repo aggregate-id]
    "Fetch an aggregate by constructing it from its stored events.
  Returns new empty aggregate if no events are stored for the given
  `aggregate-id`"))
