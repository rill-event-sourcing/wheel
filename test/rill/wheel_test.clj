(ns rill.wheel-test
  (:require [clojure.test :refer [deftest is testing]]
            [rill.message :as message]
            [rill.wheel
             :as
             aggregate
             :refer
             [commit!
              defaggregate
              defcommand
              defevent
              ok?
              rejection
              rejection?
              transact!]]
            [rill.wheel.testing :refer [ephemeral-repository sub?]]))

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

(defevent with-prepost ::aggregate1
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
    {::aggregate/events [::arm-pushed-ineffectively ::arm-turned]}
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

    (is (sub? {::aggregate/status :ok
               ::aggregate/events [{::message/type ::installed}]}
              (-> (get-turnstile repo id)
                  (install-turnstile)
                  commit!)))

    (is (sub? {::aggregate/status :ok
               ::aggregate/events [{::message/type ::arm-pushed-ineffectively}]}
              (-> (get-turnstile repo id)
                  (push-arm)
                  commit!)))

    (is (sub? {::aggregate/status :ok
               ::aggregate/events [{::message/type ::coin-inserted}]}
              (-> (get-turnstile repo id)
                  (insert-coin)
                  commit!)))

    (is (sub? {::aggregate/status :ok
               ::aggregate/events [{::message/type ::arm-turned}]}
              (-> (get-turnstile repo id)
                  (push-arm)
                  commit!)))

    (is (sub? {::aggregate/status :ok
               ::aggregate/events [{::message/type ::arm-pushed-ineffectively}]}
              (-> (get-turnstile repo id)
                  (push-arm)
                  commit!)))))

(deftest test-type-properties
  (is (= [:prop] (aggregate/type-properties ::aggregate1)))
  (is (= [:turnstile-id] (aggregate/type-properties ::turnstile))))

;;; Command tests

(defaggregate user
  [email])

(defcommand create-or-fail ::user
  "Create user if none exists with the given email address."
  {::aggregate/events [::created]}
  [user full-name]
  (if-not (aggregate/new? user)
    (rejection user "User already exists")
    (-> user
        (created full-name))))

(defevent created ::user
  "A new user was created"
  [user full-name]
  (assoc user :full-name full-name))

(defcommand rename ::user
  {::aggregate/events [::name-changed]}
  [user new-name]
  (if-not (aggregate/new? user)
    (name-changed user new-name)
    (rejection user "No such user with exists")))

(defevent name-changed ::user
  "user's `full-name` changed to `new-name`"
  [user new-name]
  (assoc user :full-name new-name))

(defevent no-op ::user
  "To test events with no body"
  [user arg1 arg2])

(deftest defevent-test
  (is (= {::aggregate/id         {::aggregate/type ::user
                                  :email           "user@example.com"}
          :full-name             "joost"
          :email                 "user@example.com"
          ::aggregate/type       ::user
          ::aggregate/version    -1
          ::aggregate/new-events [{:rill.message/type ::created
                                   ::aggregate/type   ::user
                                   :email             "user@example.com"
                                   :full-name         "joost"}]}
         (-> (user "user@example.com")
             (created "joost")))
      "Event fn calls handler with created event")

  (is (= {:rill.message/type ::created
          ::aggregate/type   ::user
          :email             "user@example.com"
          :full-name         "joost"}
         (-> (user "user@example.com")
             (created-event "joost")))
      "Can create event with aggregate")

  (is (= {:rill.message/type ::created
          ::aggregate/type   ::user
          :email             "user@example.com"
          :full-name         "joost"}
         (->created "user@example.com" "joost"))
      "Can create event standalone")

  (is (= {::aggregate/id         {::aggregate/type ::user
                                  :email           "user@example.com"}
          :email                 "user@example.com"
          ::aggregate/type       ::user
          ::aggregate/version    -1
          ::aggregate/new-events [{:rill.message/type ::no-op
                                   ::aggregate/type   ::user
                                   :email             "user@example.com"
                                   :arg1              1
                                   :arg2              2}]}
         (-> (user "user@example.com")
             (no-op 1 2)))))

(deftest aggregate-creation-test
  (let [repo (ephemeral-repository)]
    (is (ok? (-> (get-user repo "user@example.com")
                 (create-or-fail "Full Name")
                 commit!)))
    (is (sub? {::aggregate/id      {:email           "user@example.com"
                                    ::aggregate/type ::user}
               ::aggregate/type    ::user
               ::aggregate/version 0
               :full-name          "Full Name"
               :email              "user@example.com"}
              (get-user repo "user@example.com")))))



(defcommand rename-alt-impl ::user
  "example of new construct"
  [user new-name]
  (if (aggregate/exists user)
    (name-changed user new-name)
    (rejection user "User does not exist")))

(deftest test-defcommand-msg
  (let [repo (ephemeral-repository)]
    (is (ok? (-> (get-user repo "user@example.com")
                 (create-or-fail "Full Name")
                 commit!)))

    (is (ok? (transact! repo (->rename-alt-impl "user@example.com" "Other Name"))))

    (testing "deprecated variant"
      (is (ok? (transact! repo (rename-alt-impl-command "user@example.com" "Other Name")))))

    (is (ok? (rename-alt-impl! repo "user@example.com" "Other Name")))))


(deftest test-collisions
  (is (thrown? IllegalStateException
               (eval '(rill.wheel/defevent colliding-event ::user
                        [user email])))))
