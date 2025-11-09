(ns msc.memory-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc.engine :as engine]
            [msc.memory :as memory]))

(def base-engine (engine/create {:params {:table-size 2}}))

(deftest upsert-creates-implication
  (testing "new implication added and concept ensured"
    (let [eng (memory/upsert-implication base-engine
                                         {:ante [:a]
                                          :cons [:g]
                                          :op-id nil
                                          :delta-w [1.0 0.0]
                                          :stamps #{1}
                                          :dt 5})
          key [[:a] [:g] nil]
          record (get-in eng [:implications key])]
      (is (= [:g] (get-in eng [:concepts [:g] :term])))
      (is (= key (:key record)))
      (is (= #{1} (:stamps record)))
      (is (= 5.0 (double (:dt record)))))))

(deftest upsert-revises-existing
  (testing "evidence accumulates and dt averages"
    (let [eng1 (memory/upsert-implication base-engine
                                          {:ante [:a]
                                           :cons [:g]
                                           :op-id nil
                                           :delta-w [1.0 0.0]
                                           :stamps #{1}
                                           :dt 5})
          eng2 (memory/upsert-implication eng1
                                          {:ante [:a]
                                           :cons [:g]
                                           :op-id nil
                                           :delta-w [0.0 1.0]
                                           :stamps #{2}
                                           :dt 15})
          record (get-in eng2 [:implications [[:a] [:g] nil]])]
      (is (= #{1 2} (:stamps record)))
      (is (< 5.0 (:dt record) 15.0))
      (is (= [1.0 1.0] (:w record))))))

(deftest table-size-truncation
  (testing "concept tables keep only best expectations"
    (let [eng (-> base-engine
                  (assoc-in [:params :table-size] 1)
                  (memory/upsert-implication {:ante [:a]
                                              :cons [:g]
                                              :op-id 1
                                              :delta-w [1.0 0.0]
                                              :stamps #{1}
                                              :dt 1})
                  (memory/upsert-implication {:ante [:b]
                                              :cons [:g]
                                              :op-id 1
                                              :delta-w [0.0 1.0]
                                              :stamps #{2}
                                              :dt 2}))
          table (get-in eng [:concepts [:g] :tables 1])]
      (is (= 1 (count table)))
      ;; Higher expectation (success) should remain
      (is (= [[:a] [:g] 1] (first table))))))
