(ns rill.wheel.wrap-stream-properties-test
  (:require [rill.wheel.wrap-stream-properties :refer [wrap-stream-properties]]
            [rill.event-store :refer [append-events retrieve-events]]
            [rill.event-store.memory :refer [memory-store]]
            [rill.event-stream :refer [all-events-stream-id]]
            [rill.wheel.aggregate :as aggregate :refer [defevent defaggregate]]
            [rill.wheel.command :refer [ok?]]
            [rill.wheel.testing :refer [sub?]]
            [rill.wheel.repository :as repo]
            [rill.message :as msg]
            [rill.wheel.bare-repository :refer [bare-repository]]
            [clojure.test :refer [deftest testing is are]]))


(defaggregate person
  [given-name family-name]
  (sorted-map :given-name  given-name
              :family-name family-name))

(defevent registered
  [p]
  p)

(deftest test-stream-properties
  (let [store (-> (memory-store)
                  (wrap-stream-properties))
        repo  (bare-repository store)]
    (is (repo/commit! repo (-> (person repo "Alice" "Appleseed")
                               (registered))))
    (is (sub? [{::msg/type   ::registered
                :given-name  "Alice"
                :family-name "Appleseed"}]
              (retrieve-events store (person "Alice" "Appleseed"))))
    (is (sub? [{::msg/type   ::registered
                :given-name  "Alice"
                :family-name "Appleseed"}]
              (retrieve-events store all-events-stream-id)))))
