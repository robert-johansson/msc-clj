(ns msc.simple-sequence-test
  (:require [clojure.test :refer [deftest is]]
            [msc.engine :as engine]))

(def term-A [:simple :A])
(def term-B [:simple :B])
(def term-AB [:seq term-A term-B])
(def goal-term [:simple :goal])
(def op-left-id 1)
(def op-term [:op op-left-id])

(def implication-A-left [[:seq term-A op-term] goal-term op-left-id])
(def implication-B-left [[:seq term-B op-term] goal-term op-left-id])
(def implication-AB-left [[:seq term-AB op-term] goal-term op-left-id])

(defn- empty-engine []
  (engine/create {:ops {op-left-id {:term op-term}}
                  :params {:motor-babble 0.0}}))

(defn- run-cycle [eng inputs]
  (first (engine/step eng {:beliefs inputs :goals []})))

(defn- idle [eng cycles]
  (nth (iterate #(first (engine/step % {:beliefs [] :goals []})) eng)
       cycles))

(defn- run-trial [eng]
  (-> eng
      (run-cycle [{:term term-A :procedural? true}])
      (run-cycle [{:term term-B :procedural? true}])
      (run-cycle [{:term term-AB :procedural? true}])
      (run-cycle [{:term op-term :op-id op-left-id}])
      (run-cycle [{:term goal-term}])
      (idle 10)))

(defn- truth [eng key]
  (get-in eng [:implications key :truth] {:f 0.5 :c 0.0}))

(deftest simple-sequence-yields-three-rules
  (let [eng (nth (iterate run-trial (empty-engine)) 20)]
    (doseq [k [implication-A-left implication-B-left implication-AB-left]]
      (is (> (:c (truth eng k)) 0.1)
          (str "Confidence did not grow for " k)))))
