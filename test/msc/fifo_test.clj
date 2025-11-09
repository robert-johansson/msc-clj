(ns msc.fifo-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc.fifo :as fifo]))

(deftest fifo-capacity-bounds
  (testing "push respects capacity and evicts oldest entries"
    (let [f (-> (fifo/create 2)
                (fifo/push :a)
                (fifo/push :b)
                (fifo/push :c))]
      (is (= [:b :c] (:items f)))
      (is (= :b (fifo/peek-oldest f)))
      (is (= :c (fifo/peek-newest f))))))

(deftest fifo-take-all
  (testing "take-all clears fifo"
    (let [f (-> (fifo/create 2)
                (fifo/push :a)
                (fifo/push :b))
          [items cleared] (fifo/take-all f)]
      (is (= [:a :b] items))
      (is (empty? (fifo/to-seq cleared))))))
