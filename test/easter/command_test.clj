(ns easter.command-test
  (:require  [clojure.test :refer [deftest is]]
             [rill.temp-store :refer [given]]
             [easter.repository :as repository]
             [easter.caching-repository :refer [caching-repository]]
             [rill.message :as msg]
             [easter.command :as command :refer [ok? defcommand]]
             [easter.aggregate :as aggregate :refer [defevent]]))

(defevent user-created
  "A new user was created"
  [user email full-name]
  (assoc user :email email :full-name full-name))

(defcommand create-or-fail
  "Create user if none exists with the given email address."
  [repo email full-name]
  (if-let [user (repository/fetch-aggregate repo email)]
    (command/rejection user (format "User with mail '%s' already exists" email))
    (aggregate/init email (user-created email full-name))))

(deftest defevent-test
  (is (= (user-created "user@example.com" "joost")
         {:rill.message/type ::user-created
          :email             "user@example.com"
          :full-name         "joost"})))

(deftest aggregate-creation-test
  (let [repo (caching-repository (given []))]
    (is (ok? (create-or-fail repo "user@example.com" "Full Name")))
    (is (= {:easter.aggregate/id      "user@example.com"
            :easter.aggregate/version 0
            :full-name                "Full Name"
            :email                    "user@example.com"}
           (repository/fetch-aggregate repo "user@example.com")))))
