(ns rill.wheel.caching-repository-test
  (:require [rill.wheel.caching-repository :refer [caching-repository]]
            [rill.wheel.repository-test :refer [subtest-fetch-and-store]]
            [rill.event-store.memory :refer [memory-store]]
            [clojure.test :refer [deftest]]))

(deftest test-caching-repository
  (subtest-fetch-and-store #(caching-repository (memory-store))))

