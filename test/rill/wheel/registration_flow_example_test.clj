(ns rill.wheel.registration-flow-example-test
  "This test demonstrates something close to a standard user
  registration flow with email validation."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rill.wheel
             :as
             wheel
             :refer
             [commit! defaggregate defcommand defevent]]
            [rill.wheel.repository :as repo]
            [rill.wheel.testing :refer [ephemeral-repository sub? with-instrument-all]]))

(use-fixtures :once with-instrument-all)

;;;;
;;;; Let's define an "account" first so we have a starting point for
;;;; our story.
;;;;

(defaggregate account
  "An account entity tracks the status of a user account with a given
  id. In this example all we care about is whether an account exists
  at all."
  [account-id]
  {:pre [(keyword? account-id)]}) ; typically, we'd use a UUID or something

;;;;
;;;; An account can be registered and that's pretty much it
;;;;

(defcommand register-account ::account
  "Register an account with the given name. The account-id is
  generated somewhere else and should be unique."
  {::wheel/events [::account-registered]}
  [account full-name]
  (if (wheel/exists account)
    (wheel/rejection account "Account with id already exists")
    (account-registered account full-name)))

(defevent account-registered ::account
  "Someone registered an account with a name"
  [account full-name]
  account)

;;;;
;;;; After an account is registered, we would like the user to have a
;;;; confirmed email address.
;;;;
;;;; The user is expected to enter her email address in a form, after
;;;; which we'll send an email to that adress containing a secret
;;;; token that should be provided back to the application to prove
;;;; that the user is in control of that address.
;;;;
;;;; Until the user proves her control of the address ownership of the
;;;; address can be in dispute; multiple accounts can try to claim the
;;;; address.
;;;;

(defaggregate email-ownership
  "An email-ownership entity holds the claims to a given email-address.

Ownership can be unclaimed, claimed by one or more accounts (possibly
multiple times), or taken by a single account.

When ownership is taken, it cannot be claimed or owned by other
accounts."
  [email-address]
  {:pre [(string? email-address)]})

(defn random-secret
  []
  (java.util.UUID/randomUUID))

(defcommand claim-email-address ::email-ownership
  "Account with claiming-account-id wants to register email-address.

This will fail if email adress is already taken by some other
account. Otherwise the email address needs to be confirmed within some
amount of time."
  {::wheel/events [::email-address-claimed]}
  [ownership claiming-account-id]
  (let [account (get-account (wheel/repository ownership) claiming-account-id)]
    (if (wheel/exists account)
      (if (and (:account-id ownership) (not= claiming-account-id (:account-id ownership)))
        (wheel/rejection ownership "Email address is already taken")
        (email-address-claimed ownership claiming-account-id (random-secret)))
      (wheel/rejection account "No such account"))))

(defevent email-address-claimed ::email-ownership
  "There is a provisional claim on this email address that can be
  confirmed with secret.

Note that it's possible to have multiple concurrent claims on the same
and other accounts until one claim is confirmed!"
  [email-ownership account-id secret]
  (update-in email-ownership [:claims account-id] (fnil conj #{}) secret))

;;;;
;;;; We don't want to give people forever to claim their
;;;; address. After some reasonable time their confirmation attempt
;;;; will expire.
;;;;

(defcommand expire-email-address-confirmation ::email-ownership
  " We don't want to give people forever to claim their address. After
some reasonable amount of time their confirmation attempt will expire.

This command expires an email confirmation attempt. In a production
environment this might be called by a background worker."
  {::wheel/events [::email-address-confirmation-expired]}
  [ownership account-id secret]
  (if (wheel/exists ownership)
    (email-address-confirmation-expired ownership account-id secret)
    (wheel/rejection ownership "No activity on this email address")))

(defevent email-address-confirmation-expired ::email-ownership
  "It's taken too long to confirm this email address"
  [email-ownership account-id secret]
  (update-in email-ownership [:claims account-id] disj secret))

;;;;
;;;; When the user receives her secret, she can confirm control of the
;;;; address.
;;;;

(defcommand confirm-email-address ::email-ownership
  "Account attempts to confirm ownership of email address. This will
  fail if the token is incorrect, the email address was never claimed
  by this account or if another account confirmed the address
  earlier."
  {::wheel/events [::email-address-taken]}
  [ownership account-id secret]
  (if-let [current-owner-id (:account-id ownership)]
    (if (= current-owner-id account-id)
      ownership ; already owned by account-id
      (wheel/rejection ownership "Email address already owned by other account"))
    (if (get-in ownership [:claims account-id secret])
      (email-address-taken ownership account-id)
      (wheel/rejection ownership "Invalid confirmation"))))

(defevent email-address-taken ::email-ownership
  "This email address was confirmed"
  [email-ownership account-id]
  (-> email-ownership
      (dissoc :claims)                  ; remove any pending claims
      (assoc :account-id account-id)))

(defevent email-address-released ::email-ownership
  "There is no current owner of this address"
  [email-ownership]
  (dissoc email-ownership :account-id))

;;;;
;;;; Some test helpers
;;;;

(defn test-repo-with-two-users
  "Initialize a new repository with two registered users, neither of
  whom have a registered email address."
  []
  (doto (ephemeral-repository)
    (register-account! :first-user "User Number One")
    (register-account! :second-user "User Number Two")))

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
  (is (sub? {::wheel/status :ok
             ::wheel/events [{:rill.message/type ::email-address-claimed}]}
            result-of-claim))
  (get-in result-of-claim [::wheel/events 0 :secret]))


;;;;
;;;; Let's show that all of this actually works
;;;;
;;;; First, happy flow
;;;;

(deftest test-happy-flow
  (testing "User registers email address"
    (let [repo   (test-repo-with-two-users)
          secret (-> repo
                     (claim-email-address! "one@example.com" :first-user)
                     secret-from-claim-result)]
      (is secret)
      (is (sub? {::wheel/status :ok
                 ::wheel/events [{:rill.message/type ::email-address-taken
                                      :account-id        :first-user}]}
                (confirm-email-address! repo "one@example.com" :first-user secret))))))

;;;;
;;;; Show that some of the unhappy flows are implemented
;;;;

(deftest test-unhappy-flows
  ;; Happy flows are all alike; every unhappy flow is unhappy in its
  ;; own way.
  (testing "Multiple users attempting to register same, untaken address"
    (let [repo    (test-repo-with-two-users)
          secret1 (-> repo
                      (claim-email-address! "example@example.com" :first-user)
                      secret-from-claim-result)
          secret2 (-> repo
                      (claim-email-address! "example@example.com" :second-user)
                      secret-from-claim-result)]
      (is secret1 "secret generated for claim 1")
      (is secret2 "secret generated for claim 2")
      (is (not= secret1 secret2)
          "secrets are different for each claim")
      (is (sub? {::wheel/status :ok
                 ::wheel/events [{:rill.message/type ::email-address-taken
                                      :account-id        :second-user}]}
                (confirm-email-address! repo "example@example.com" :second-user secret2))
          "first confirmation succeeds")

      (is (wheel/rejection? (confirm-email-address! repo "example@example.com" :first-user secret1))
          "second confirmation is rejected")))

  (testing "User attempting to register already taken address"
    (let [repo (test-repo-with-two-users)

          ;; complete registering the address with :first-user
          secret (-> repo
                     (claim-email-address! "example@example.com" :first-user)
                     secret-from-claim-result)]
      (is secret "secret generated for first claim")
      (is (sub? {::wheel/status :ok
                 ::wheel/events [{:rill.message/type ::email-address-taken
                                      :account-id        :first-user}]}
                (confirm-email-address! repo "example@example.com" :first-user secret))
          "first confirmation succeeds")
      ;; email address is now owned by :first-user
      (is (wheel/rejection? (claim-email-address! repo "example@example.com" :second-user))
          "second claim is rejected immediately")))

  (testing "Malicious attempt to register address with non-existing user"
    (let [repo (ephemeral-repository)]
      (is (wheel/rejection? (claim-email-address! repo "user.address@example.com" :some-other-some)))))

  (testing "Malicious attempt to register address with incorrect token"
    (let [repo   (test-repo-with-two-users)
          secret (-> repo
                     (claim-email-address! "one@example.com" :first-user)
                     secret-from-claim-result)]
      (is (wheel/rejection? (confirm-email-address! repo "one@example.com" :first-user (random-secret)))
          "Guessing a secret will not work")))

  (testing "User is too late to confirm registration"
    (let [repo   (test-repo-with-two-users)
          secret (-> repo
                     (claim-email-address! "one@example.com" :first-user)
                     secret-from-claim-result)]
      (is secret "Secret received")
      (is (wheel/ok? (expire-email-address-confirmation! repo "one@example.com" :first-user secret))
          "Timeout committed")
      (is (wheel/rejection? (confirm-email-address! repo "one@example.com" :first-user secret))))))
