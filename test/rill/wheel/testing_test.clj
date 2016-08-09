(ns rill.wheel.testing-test
  (:require [clojure.test :refer [are deftest]]
            [rill.wheel.testing :refer [sub?]]))

(deftest test-sub?
  (are [x] x
    (sub? nil
          {:anything :at-all})
    (sub? [:a :b]
          [:a :b :c])
    (not (sub? [:a :b :c]
               [:a :b]))
    (sub? {:a [1 2 3]}
          {:a [1 2 3 4] :b 2})
    (sub? {:a [1 nil 3]}
          {:a [1 2 3 4] :b 2})
    (not (sub? {:a [1 2 3 4]}
               {:a [1 2 3] :b 2}))
    (sub? #{:a}
          {:a 1 :b 2})
    (sub? #{:a}
          #{:a :b})
    (not (sub? #{:a :c}
               #{:a :b}))
    (sub? :something
          :something)
    (sub? [:1 :2 :3]
          (list :1 :2 :3))
    (sub? [:1 :2]
          (list :1 :2 :3))
    (sub? (list :1 :2 :3)
          [:1 :2 :3])
    (not (sub? (list nil 2)
               [:1 :2 :3]))))
