(ns rill.wheel.commit-callback-test
  (:require [rill.wheel.commit-callback :refer [wrap-commit-callback]]
            [rill.wheel.testing :refer [sub? ephemeral-repository]]
            [clojure.test :refer [deftest testing is]]))


