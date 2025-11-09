(ns msc.stamp-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc.stamp :as stamp]))

(deftest fresh-increments-counter
  (testing "fresh increments :next-stamp-id"
    (let [[engine' stamp-id] (stamp/fresh {:next-stamp-id 5})]
      (is (= 5 stamp-id))
      (is (= 6 (:next-stamp-id engine'))))))

(deftest independence-detection
  (testing "independent? respects overlap"
    (is (true? (stamp/independent? #{1 2} #{3})))
    (is (false? (stamp/independent? #{1 2} #{2 3})))))

(deftest union-merges-stamps
  (is (= #{1 2 3}
         (stamp/union [1 2] #{2 3} nil))))
