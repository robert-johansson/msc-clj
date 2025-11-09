(ns msc.infer-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc.engine :as engine]
            [msc.infer :as infer]))

(deftest induce-adds-positive-evidence
  (let [eng (engine/create)
        eng' (infer/induce eng {:ante [:a]
                                :cons [:g]
                                :op-id nil
                                :stamps #{1}
                                :dt 10})]
    (is (= [[:a] [:g] nil]
           (get-in eng' [:concepts [:g] :tables nil 0]))) ;; first entry
    (is (= [1.0 0.0]
           (get-in eng' [:implications [[:a] [:g] nil] :w])))))

(deftest assumption-of-failure-adds-negative
  (let [eng (engine/create)
        eng' (infer/assumption-of-failure eng
                                          [{:ante [:a]
                                            :cons [:g]
                                            :op-id nil
                                            :stamps #{1}
                                            :dt 5}]
                                          0.01)]
    (is (= [0.0 0.01]
           (get-in eng' [:implications [[:a] [:g] nil] :w])))))
