(ns rill.wheel.caching-repository-test
  (:require [rill.wheel.caching-repository :refer [caching-repository]]
            [rill.wheel.repository-test :refer [subtest-fetch-and-store]]
            [rill.temp-store :refer [given]]
            [clojure.test :refer [deftest]]))

(deftest test-caching-repository
  (subtest-fetch-and-store #(caching-repository (given []))))

