(ns rill.wheel.repository-test
  (:require [rill.wheel.repository :as repo]
            [rill.wheel.aggregate :as aggregate :refer [defevent]]
            [rill.temp-store :refer [given]]
            [clojure.test :refer [deftest is]]))

(defevent layed
  [creature]
  (assoc creature :egg? true))

(defevent hatched
  [creature]
  (assoc creature
         :egg? false
         :bird? true))

(defn subtest-fetch-and-store
  [mk-repo]
  (let [repo (mk-repo)
        bird (-> (aggregate/empty :bird-id)
                 layed
                 hatched)]
    (is (:bird? bird)
        "events applied")
    (is (repo/commit! repo bird)
        "commit succeeded")
    (is (= {::aggregate/id      :bird-id
            ::aggregate/version 1
            :egg?               false
            :bird?              true}
           (repo/fetch repo :bird-id)))))

(deftest test-bare-repository
  (subtest-fetch-and-store #(repo/bare-repository (given []))))
