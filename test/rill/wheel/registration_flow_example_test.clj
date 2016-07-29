(ns rill.wheel.registration-flow-example-test
  "This test demonstrates something close to a standard user
  registration flow with email validation."
  (:require  [clojure.test :refer [deftest testing is with-test are]]
             [rill.wheel.command :as command :refer [defcommand]]
             [rill.wheel.aggregate :as aggregate :refer [defevent]]
             [rill.wheel.repository :as repo]
             [rill.wheel.testing :refer [sub? ephemeral-repository]]))

(defevent email-address-claimed
  "There is a provisional claim on this email address that can be
  confirmed with secret.

Note that it's possible to have multiple concurrent claims on the same
and other accounts until one claim is confirmed!"
  [email-ownership account-id secret]
  (update-in email-ownership [:claims account-id] (fnil conj #{}) secret))

(defevent email-address-confirmation-expired
  "It's taken too long to confirm this email address"
  [email-ownership account-id secret]
  (update-in email-ownership [:claims account-id] disj secret))

(defcommand expire-email-address-confirmation
  "Internal command that expires the email confirmation attempt. In a
  production environment this might be called by a background worker."
  [repo account-id email-address secret]
  (let [ownership (repo/fetch repo {:email-address email-address})]
    (if (aggregate/exists ownership)
      (email-address-confirmation-expired ownership account-id secret)
      (command/rejection ownership "No activity on this email address"))))

(defevent account-initialized
  "Someone registered an account with a name"
  [account full-name]
  account)

(defevent email-address-taken
  "This email address was confirmed"
  [email-ownership account-id]
  (-> email-ownership
      (dissoc :claims)                  ;  nobody else can claim anymore
      (assoc :account-id account-id)))

(defevent email-address-released
  "There is no current owner of this address"
  [email-ownership]
  (dissoc email-ownership :account-id))

(defn random-secret
  []
  (java.util.UUID/randomUUID))

(defcommand claim-email-address
  "Account with claiming-account-id wants to register email-address.

This will fail if email adress is already taken by some other
account. Otherwise the email address needs to be confirmed within some
amount of time."
  [repo claiming-account-id email-address]
  (let [account (repo/fetch repo {:account-id claiming-account-id})]
    (if (aggregate/exists account)
      (let [{current-owner :account-id :as ownership} (repo/fetch repo {:email-address email-address})]
        (if (and current-owner (not= claiming-account-id (:account-id ownership)))
          (command/rejection ownership "Email address is already taken")
          (email-address-claimed ownership claiming-account-id (random-secret))))
      (command/rejection account "No such account"))))

(defcommand confirm-email-address
  "Account attempts to confirm ownership of email address. This will
  fail if the token is incorrect, the email address was never claimed
  by this account or if another account confirmed the address
  earlier."
  [repo account-id email-address secret]
  (if-let [ownership (aggregate/exists (repo/fetch repo {:email-address email-address}))]
    (if-let [current-owner-id (:account-id ownership)]
      (if (= current-owner-id account-id)
        ownership ; already owned by account-id
        (command/rejection ownership "Email address already owned by other account"))
      (if (get-in ownership [:claims account-id secret])
        (email-address-taken ownership account-id)
        (command/rejection ownership "Invalid confirmation")))
    (command/rejection nil "Email address was never claimed")))

(defn test-repo-with-two-users
  []
  (doto (ephemeral-repository)
    (repo/commit! (-> (aggregate/empty {:account-id :first-user})
                      (account-initialized "User Number One")))
    (repo/commit! (-> (aggregate/empty {:account-id :second-user})
                      (account-initialized "User Number Two")))))

(defn secret-from-claim-result
  "Get secret token from result of executing `claim-email-address`"

  ;; The first (and only) event resulting from a successful
  ;; `claim-email-address` command is a `email-address-claimed`,
  ;; with :secret property that we need to `confirm-email-address`
  ;; in a real world scenario, this secret would be mailed to the
  ;; user by some process that's watching for
  ;; ::email-address-claimed events. In this test scenario, we'll
  ;; just extract the secret from the event and pass it along
  ;; here.

  [result-of-claim]
  ;; assert that claim was actually accepted
  (is (sub? {::command/status :ok
             ::command/events [{:rill.message/type ::email-address-claimed}]}
            result-of-claim))
  (get-in result-of-claim [::command/events 0 :secret]))

(deftest test-happy-flow
  (testing "User registers email address"
    (let [repo   (test-repo-with-two-users)
          secret (-> repo
                     (claim-email-address :first-user "one@example.com")
                     secret-from-claim-result)]
      (is secret)
      (is (sub? {::command/status :ok
                 ::command/events [{:rill.message/type ::email-address-taken
                                    :account-id        :first-user}]}
                (confirm-email-address repo :first-user "one@example.com" secret))))))

(deftest test-unhappy-flows
  ;; Happy flows are all alike; every unhappy flow is unhappy in its
  ;; own way.
  (testing "Multiple users attempting to register same, untaken address"
    (let [repo    (test-repo-with-two-users)
          secret1 (-> repo
                      (claim-email-address :first-user "example@example.com")
                      secret-from-claim-result)
          secret2 (-> repo
                      (claim-email-address :second-user "example@example.com")
                      secret-from-claim-result)]
      (is secret1 "secret generated for claim 1")
      (is secret2 "secret generated for claim 2")
      (is (not= secret1 secret2)
          "secrets are different for each claim")
      (is (sub? {::command/status :ok
                 ::command/events [{:rill.message/type ::email-address-taken
                                    :account-id        :second-user}]}
                (confirm-email-address repo :second-user "example@example.com" secret2))
          "first confirmation succeeds")

      (is (command/rejection? (confirm-email-address repo :first-user "example@example.com" secret1))
          "second confirmation is rejected")))

  (testing "User attempting to register already taken address"
    (let [repo (test-repo-with-two-users)

          ;; complete registering the address with :first-user
          secret (-> repo
                     (claim-email-address :first-user "example@example.com")
                     secret-from-claim-result)]
      (is secret "secret generated for first claim")
      (is (sub? {::command/status :ok
                 ::command/events [{:rill.message/type ::email-address-taken
                                    :account-id        :first-user}]}
                (confirm-email-address repo :first-user "example@example.com" secret))
          "first confirmation succeeds")
      ;; email address is now owned by :first-user
      (is (command/rejection? (claim-email-address repo :second-user "example@example.com"))
          "second claim is rejected immediately")))

  (testing "Malicious attempt to register address with non-existing user"
    (let [repo (ephemeral-repository)]
      (is (command/rejection? (claim-email-address repo :some-other-user "some.address@example.com")))))

  (testing "Malicious attempt to register address with incorrect token"
    (let [repo   (test-repo-with-two-users)
          secret (-> repo
                     (claim-email-address :first-user "one@example.com")
                     secret-from-claim-result)]
      (is (command/rejection? (confirm-email-address repo :first-user "one@example.com" (random-secret)))
          "Guessing a secret will not work")))

  (testing "User is too late to confirm registration"
    (let [repo   (test-repo-with-two-users)
          secret (-> repo
                     (claim-email-address :first-user "one@example.com")
                     secret-from-claim-result)]
      (is secret "Secret received")
      (is (command/ok? (expire-email-address-confirmation repo :first-user "one@example.com" secret))
          "Timeout committed")
      (is (command/rejection? (confirm-email-address repo :first-user "one@example.com" secret))))))
