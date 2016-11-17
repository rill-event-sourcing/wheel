(ns rill.wheel.caching-repository-test
  (:require [clojure.test :refer [deftest use-fixtures]]
            [rill.event-store.memory :refer [memory-store]]
            [rill.wheel.caching-repository :refer [caching-repository]]
            [rill.wheel.repository-test :refer [subtest-fetch-and-store]]
            [rill.wheel.testing :refer [with-instrument-all]]))

(deftest test-caching-repository
  (subtest-fetch-and-store #(caching-repository (memory-store))))

(use-fixtures :once with-instrument-all)
