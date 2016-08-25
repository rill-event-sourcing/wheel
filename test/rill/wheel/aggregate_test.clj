(ns rill.wheel.aggregate-test
  (:require [clojure.test :refer [deftest testing is]]
            [rill.wheel.aggregate :as aggregate :refer [defaggregate defevent]]
            [rill.wheel.testing :refer [ephemeral-repository sub?]]
            [rill.message :as message]
            [rill.wheel.command :as command :refer [rejection rejection? ok? commit!]]))

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

(defaggregate turnstile
  "An aggregate with docstring"
  [turnstile-id]
  {:pre [(instance? java.util.UUID turnstile-id)]}
  ((installed
    "A turnstile was installed"
    [turnstile]
    (assoc turnstile
           :installed? true
           :locked? true
           :coins 0
           :turns 0
           :pushes 0))

   (coin-inserted
    "A Coin was inserted into the turnstile"
    [turnstile]
    (-> turnstile
        (update :coins inc)
        (assoc :locked? false)))

   (arm-turned
    "The turnstile's arm was turned"
    [turnstile]
    (-> turnstile
        (update :pushes inc)
        (update :turns inc)
        (assoc :locked? true)))

   (arm-pushed-ineffectively
    "The arm was pushed but did not move"
    [turnstile]
    (-> turnstile
        (update :pushes inc))))

  ((install-turnstile
    [turnstile]
    (if (aggregate/exists turnstile)
      (rejection turnstile "Already exists")
      (installed turnstile)))

   (insert-coin
    "Insert coin into turnstile, will unlock"
    [turnstile]
    (if (:installed? turnstile)
      (coin-inserted turnstile)
      (rejection turnstile "Turnstile not installed")))

   (push-arm
    "Push the arm, might turn or be ineffective"
    {::command/events [::arm-pushed-ineffectively ::arm-turned]}
    [turnstile]
    (cond
      (not (:installed? turnstile))
      (rejection turnstile "Not installed")
      (:locked? turnstile)
      (arm-pushed-ineffectively turnstile)
      :else
      (arm-turned turnstile)))))

(deftest test-extened-defaggregate
  (let [repo (ephemeral-repository)
        id   (java.util.UUID/randomUUID)]
    (is (rejection? (-> (get-turnstile repo id)
                        (push-arm)
                        commit!)))

    (is (sub? {::command/status :ok
               ::command/events [{::message/type ::installed}]}
              (-> (get-turnstile repo id)
                  (install-turnstile)
                  commit!)))

    (is (sub? {::command/status :ok
               ::command/events [{::message/type ::arm-pushed-ineffectively}]}
              (-> (get-turnstile repo id)
                  (push-arm)
                  commit!)))

    (is (sub? {::command/status :ok
               ::command/events [{::message/type ::coin-inserted}]}
              (-> (get-turnstile repo id)
                  (insert-coin)
                  commit!)))

    (is (sub? {::command/status :ok
               ::command/events [{::message/type ::arm-turned}]}
              (-> (get-turnstile repo id)
                  (push-arm)
                  commit!)))

    (is (sub? {::command/status :ok
               ::command/events [{::message/type ::arm-pushed-ineffectively}]}
              (-> (get-turnstile repo id)
                  (push-arm)
                  commit!)))))

(deftest test-type-properties
  (is (= [:prop] (aggregate/type-properties ::aggregate1)))
  (is (= [:turnstile-id] (aggregate/type-properties ::turnstile))))
