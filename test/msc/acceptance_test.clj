(ns msc.acceptance-test
  (:require [clojure.test :refer [deftest is]]
            [msc.engine :as engine]
            [msc.exp1-harness :as exp1]))

(defn- run-step [engine inputs]
  (first (engine/step engine inputs)))

(defn- idle-cycles [engine n]
  (loop [eng engine
         i 0]
    (if (>= i n)
      eng
      (recur (run-step eng {:beliefs [] :goals []}) (inc i)))))

(defn- push-beliefs [engine events]
  (run-step engine {:beliefs events :goals []}))

(defn- implication-truth [engine key]
  (when-let [record (get-in engine [:implications key])]
    (:truth record)))

(deftest hundred-tick-confidence-grows
  (let [trials 5
        gap 100
        eng-final
        (loop [eng (engine/create {})
               t 0]
          (if (= t trials)
            eng
            (let [eng (run-step eng {:beliefs [{:term [:A]}]})
                  eng (idle-cycles eng (dec gap))
                  eng (run-step eng {:beliefs [{:term [:G]}]})
                  eng (idle-cycles eng 1)]
              (recur eng (inc t)))))]
    (let [record (get-in eng-final [:implications [[:A] [:G] nil]])
          {:keys [f c]} (:truth record)]
      (is record)
      (is (> f 0.9))
      (is (> c 0.7)))))

(deftest procedural-triple-builds-link
  (let [trials 5
        eng-final
        (loop [eng (engine/create {:ops {1 {:term [:op 1]}}})
               t 0]
          (if (= t trials)
            eng
            (let [eng (push-beliefs eng [{:term [:pre]}])
                  eng (push-beliefs eng [{:term [:op 1] :op-id 1}])
                  eng (push-beliefs eng [{:term [:g]}])]
              (recur eng (inc t)))))]
    (let [record (get-in eng-final [:implications [[:seq [:pre] [:op 1]] [:g] 1]])]
      (is record)
      (is (>= (first (:w record)) 5))
      (is (> (:expectation record) 0.7)))))

(defn long-gap-measurements []
  (let [op-id 1
        key [[:seq [:A] [:op op-id]] [:G] op-id]
        trials 5
        gap 100
        measurements
        (loop [eng (engine/create {:ops {op-id {:term [:op op-id]}}})
               t 0
               acc []]
          (if (= t trials)
            acc
            (let [eng (run-step eng {:beliefs [{:term [:A]}]})
                  eng (run-step eng {:beliefs [{:term [:op op-id]
                                                :op-id op-id}]})
                  eng (run-step eng {:beliefs [{:term [:G]}]})
                  eng (idle-cycles eng gap)
                  truth (implication-truth eng key)]
              (recur eng (inc t)
                     (if truth
                       (conj acc (assoc truth :trial (inc t)))
                       acc)))))]
    measurements))

(deftest long-gap-procedural-confidence-climbs
  (let [measurements (long-gap-measurements)]
    (is (>= (count measurements) 2) "need at least two measurements to check growth")
    (doseq [[current next] (partition 2 1 measurements)]
      (is (> (:c next) (:c current))
          (str "confidence must grow, got " (:c next) " <= " (:c current))))
    (let [last-truth (last measurements)]
      (is (> (:c last-truth) 0.6))
      (is (> (:f last-truth) 0.9)))))

(deftest experiment-one-scenario-runs
  (let [{:keys [context results]} (exp1/run-exp1-context)
        {:keys [testing]} (exp1/summarize-results results)
        {:keys [left right]} (exp1/tracked-truths context)
        total-trials (* exp1/exp-block-trials
                        (+ exp1/exp1-baseline-blocks
                           exp1/exp1-training-blocks
                           exp1/exp1-testing-blocks))]
    (is (= total-trials (count results)))
    (is (> (:c left) 0.7))
    (is (> (:c right) 0.7))
    (is (> (:accuracy testing) 0.7))))
