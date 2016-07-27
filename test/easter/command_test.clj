(ns easter.command-test
  (:require  [clojure.test :refer [deftest is]]
             [rill.temp-store :refer [given]]
             [rill.message :as msg]
             [easter.command :as command :refer [ok?]]
             [easter.aggregate :as aggregate :refer [defapply]]))

(defapply user-created
  "A new user was created"
  [user email full-name]
  (assoc user :email email :full-name full-name))

(defn create-or-fail
  [store email full-name]
  (if-let [user (aggregate/fetch store email)]
    (command/reject user (format "User with mail '%s' already exists" email))
    (-> (aggregate/init email (user-created email full-name))
        (command/commit! store))))

(deftest defapply-test
  (is (= (user-created "user@example.com" "joost")
         {:rill.message/type ::user-created
          :email             "user@example.com"
          :full-name         "joost"})))

(deftest aggregate-creation-test
  (let [store (given [])]
    (is (ok? (create-or-fail store "user@example.com" "Full Name")))
    (is (= {:easter.aggregate/id "user@example.com"
            :easter.aggregate/version 0
            :full-name "Full Name"
            :email "user@example.com"}
           (aggregate/fetch store "user@example.com")))))
