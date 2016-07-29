(ns rill.wheel.repository-test
  (:require [rill.wheel.repository :as repo]
            [rill.wheel.aggregate :as aggregate :refer [defevent]]
            [rill.event-store.memory :refer [memory-store]]
            [clojure.test :refer [deftest testing is]]))

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
  (testing "aggregate with events"
    (let [repo (mk-repo)
          bird (-> (aggregate/empty {::species :bird
                                     ::id      :id})
                   layed
                   hatched)]
      (is (:bird? bird)
          "events applied")
      (is (repo/commit! repo bird)
          "commit succeeded")
      (is (= {::aggregate/id      {::species :bird
                                   ::id      :id}
              ::aggregate/version 1
              ::species           :bird
              ::id                :id
              :egg?               false
              :bird?              true}
             (repo/fetch repo {::species :bird
                               ::id      :id})))))
  (testing "empty aggregate"
    (let [repo (mk-repo)
          empty-aggregate (repo/fetch repo {:prop :unknown})]
      (is (aggregate/aggregate? empty-aggregate))
      (is (aggregate/empty? empty-aggregate))
      (is (repo/commit! repo empty-aggregate))
      (let [fetched (repo/fetch repo {:prop :unknown})]
        (is (= fetched empty-aggregate))))))

(deftest test-bare-repository
  (subtest-fetch-and-store #(repo/bare-repository (memory-store))))

