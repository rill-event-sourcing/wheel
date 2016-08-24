(ns rill.wheel.saga-test
  "A test of the saga mechnism using the coin-operated turnstile
  example from https://en.wikipedia.org/wiki/Finite-state_machine

  The idea is that a turnstile will only turn when unlocked, will lock
  after being pushed and will unlock when a coin is inserted."
  (:require [rill.wheel.saga :as saga]
            [clojure.test :refer [deftest testing is]]
            [rill.wheel.testing :refer [ephemeral-repository sub?]]
            [rill.wheel.repository :as repo]
            [rill.wheel.aggregate :refer [defevent defaggregate]]
            [rill.wheel.command :as command :refer [defcommand commit! ok? rejection?]]
            [rill.wheel.saga :as saga :refer [defsaga]]))

(defaggregate turnstile
  [turnstile-id]
  ((arm-pushed
    [turnstile]
    (update turnstile :pushes (fnil inc 0)))
   (turned
    [turnstile]
    (update turnstile :turns (fnil inc 0))))

  ((push-arm
    [turnstile]
    (-> turnstile
        arm-pushed))
   (turn
    [turnstile]
    (-> turnstile
        (turned)))))

(defaggregate slot
  [slot-id turnstile-id]
  ((coin-inserted
    [slot]
    (update slot :coins (fnil inc 0))))

  ((insert-coin
    [slot]
    (-> slot
        coin-inserted))))

(defsaga turnstile-process
  {::saga/initial-state :locked
   ;; state -> event-type -> new-state
   ::saga/transitions   {:unlocked {::arm-pushed :locked}
                         :locked   {::coin-inserted :unlocked
                                    ::arm-pushed    :locked}}}
  [turnstile-id]
  ;; entry actions
  ((locked
    [repo]
    (-> (turnstile repo turnstile-id)
        (turn)))))



(deftest test-turnstile
  (let [repo (ephemeral-repository)]
    (is (nil? (-> repo
                  (get-turnstile :turnstile-id)
                  :turns))
        "initially no turns")
    (is (ok? (-> repo
                 (get-turnstile :turnstile-id)
                 (push-arm)
                 (commit!))))

    (is (nil? (:turns (get-turnstile repo :turnstile-id)))
        "can't turn if locked")
    (is (ok? (-> repo
                 (get-turnstile :turnstile-id)
                 push-arm
                 commit!)))
    (is (nil? (:turns (get-turnstile repo :turnstile-id)))
        "still won't turn")
    (is (ok? (-> repo
                 (get-slot :slot-id :turnstile-id)
                 insert-coin
                 commit!)))
    (is (sub? {:coins 1}
              (get-slot repo :slot-id :turnstile-id)))
    (is (ok? (-> repo
                 (get-turnstile :turnstile-id)
                 push-arm
                 commit!)))
    (is (sub? {:pushes 3
               :turns  1}
              (get-turnstile repo :turnstile-id))
        "will turn when unlocked")
    (is (ok? (-> repo
                 (push-arm :turnstile-id)
                 commit!)))
    (is (sub? {:pushes 3
               :turns  1}
              (get-turnstile repo :turnstile-id))
        "will turn only once when unlocked")))
