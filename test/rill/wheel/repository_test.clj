(ns rill.wheel.repository-test
  (:require [clojure.test :refer [deftest is testing]]
            [rill.event-store.memory :refer [memory-store]]
            [rill.wheel.aggregate :as aggregate :refer [defevent defaggregate]]
            [rill.wheel.bare-repository :refer [bare-repository]]
            [rill.wheel.repository :as repo]))

(defaggregate creature [id species])

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
          init (creature :my-id :bird)
          bird (-> init
                   layed
                   hatched)]
      (is (:bird? bird)
          "events applied")
      (is (repo/commit! repo bird)
          "commit succeeded")
      (is (= {::aggregate/id      {:id      :my-id
                                   :species :bird}
              ::aggregate/version 1
              :species           :bird
              :id                :my-id
              :egg?               false
              :bird?              true}
             (repo/update repo init)))))
  (testing "empty aggregate"
    (let [repo            (mk-repo)
          init (creature :other-id :other)
          empty-aggregate (repo/update repo init)]
      (is (aggregate/aggregate? empty-aggregate))
      (is (aggregate/empty? empty-aggregate))
      (is (repo/commit! repo empty-aggregate))
      (let [fetched (repo/update repo init)]
        (is (= fetched empty-aggregate))))))

(deftest test-bare-repository
  (subtest-fetch-and-store #(bare-repository (memory-store))))
