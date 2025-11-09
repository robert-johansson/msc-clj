(ns msc.exp1-harness-test
  (:require [clojure.test :refer [deftest is]]
            [msc.exp1-harness :as harness]))

(deftest stimuli-ordering-produces-two-events
  (let [ctx (-> (harness/initial-context {:trace? true
                                          :motor-babble 0.0})
                (assoc-in [:engine :time] 0))
        [ctx' _] (harness/run-trial ctx {:phase :baseline
                                         :block 1
                                         :trial 1
                                         :a1-left? true
                                         :provide-feedback? false})
        trace (:trace ctx')]
    (is (>= (count trace) 2))
    (is (= [:exp1 :A1-left] (:term (first trace))))
    (is (= [:exp1 :A2-right] (:term (second trace))))
    (is (= 0 (:time (first trace))))
    (is (= 1 (:time (second trace))))))

(deftest tracked-truths-exist-after-run
  (let [{:keys [context]} (harness/run-exp1-context {:motor-babble 0.0})
        truths (harness/tracked-truths context)]
    (doseq [k [:a1-left :a1-right :a2-left :a2-right]]
      (is (contains? truths k))
      (is (map? (get truths k))))))

(deftest decision-trace-collects-entries
  (let [{:keys [context]} (harness/run-exp1-context {:motor-babble 0.0
                                                     :decision-trace? true})
        trace (harness/decision-trace context)]
    (is (seq trace))
    (is (every? #(contains? % :time) trace))
    (is (every? #(contains? % :best) trace))))
