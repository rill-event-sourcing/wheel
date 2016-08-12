(ns rill.wheel.aggregate-test
  (:require [clojure.test :refer [deftest testing is]]
            [rill.wheel.aggregate :as aggregate :refer [defaggregate defevent]]))

(defaggregate aggregate1
  "Some documentation"
  [prop])

(defaggregate aggregate2
  [prop])

(defaggregate aggregate3
  "An aggregate with docstring and precondition"
  [prop]
  {:pre [(string? prop)]})

(deftest defaggregate-test
  (is (= {::aggregate/id      {:prop            "val1",
                               ::aggregate/type ::aggregate1},
          ::aggregate/version -1,
          :prop               "val1",
          ::aggregate/type    ::aggregate1}
         (aggregate1 "val1")))

  (is (= {::aggregate/id      {:prop            "val1",
                               ::aggregate/type ::aggregate2},
          ::aggregate/version -1,
          :prop               "val1",
          ::aggregate/type    ::aggregate2}
         (aggregate2 "val1")))


  (is (= {::aggregate/id      {:prop            "val1",
                               ::aggregate/type ::aggregate3},
          ::aggregate/version -1,
          :prop               "val1",
          ::aggregate/type    ::aggregate3}
         (aggregate3 "val1")))

  (is (thrown? AssertionError
               (aggregate3 123))
      "preconditions work"))

(defevent with-prepost
  [aggregate number-smaller-than-10]
  {:pre  [(number? number-smaller-than-10)]
   :post [(< (:number-smaller-than-10 %) 10)]}
  (assoc aggregate :prepost-event-handled true))

(deftest defevent-test
  (is (= (-> (aggregate1 "val")
             (with-prepost 5))
         {::aggregate/id         {:prop "val", ::aggregate/type ::aggregate1},
          ::aggregate/version    -1, :prop "val",
          ::aggregate/type       ::aggregate1,
          :prepost-event-handled true,
          ::aggregate/new-events [{:rill.message/type      ::with-prepost,
                                   :number-smaller-than-10 5,
                                   :prop                   "val",
                                   ::aggregate/type        ::aggregate1}]}))

  (is (thrown? AssertionError
               (-> (aggregate1 "val")
                   (with-prepost "string")))
      "preconditions")

  (is (thrown? AssertionError
               (-> (aggregate1 "val")
                   (with-prepost 200)))
      "postconditions"))
