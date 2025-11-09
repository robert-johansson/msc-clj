(ns msc.goal-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc.engine :as engine]
            [msc.event :as event]
            [msc.goal :as goal]
            [msc.memory :as memory]))

(deftest decide-returns-effects-vector
  (let [[engine effects rng] (goal/decide {:state :ok} (java.util.SplittableRandom. 42))]
    (is (= {:state :ok} engine))
    (is (vector? effects))
    (is (instance? java.util.SplittableRandom rng))))

(deftest decision-selects-learned-op
  (let [eng (-> (engine/create {:ops {1 {:term [:op 1]}}})
                (memory/upsert-implication {:ante [:seq [:pre] [:op 1]]
                                            :cons [:g]
                                            :op-id 1
                                            :delta-w [3.0 0.0]
                                            :stamps #{1}
                                            :dt 1})
                (event/add-event :belief {:term [:pre]})
                (event/add-event :goal {:term [:g]}))
        [_ effects _] (goal/decide eng (java.util.SplittableRandom. 42))]
    (is (= 1 (:op-id (first effects))))))

(deftest decision-motor-babbles-when-needed
  (let [rng (java.util.SplittableRandom. 42)
        eng (engine/create {:params {:motor-babble 1.0}
                            :ops {1 {:term [:op 1]}}})
        [_ effects _] (goal/decide eng rng)]
    (is (= 1 (:op-id (first effects))))))

(deftest decision-requires-recent-belief
  (let [rng (java.util.SplittableRandom. 42)
        eng (-> (engine/create {:params {:decision-max-age 0}
                                :ops {1 {:term [:op 1]}}})
                (assoc :time 10)
                (memory/upsert-implication {:ante [:seq [:pre] [:op 1]]
                                            :cons [:g]
                                            :op-id 1
                                            :delta-w [3.0 0.0]
                                            :stamps #{1}
                                            :dt 1})
                (event/add-event :belief {:term [:pre]})
                (assoc :time 12)
                (assoc-in [:ingested :belief] [])
                (event/add-event :goal {:term [:g]}))
        [eng-no effects-no _] (goal/decide eng rng)
        _ (is (empty? effects-no))
        eng' (event/add-event eng-no :belief {:term [:pre]})
        [_ effects _] (goal/decide eng' rng)]
    (is (= 1 (:op-id (first effects))))))

(defn- engine-with-link []
  (memory/upsert-implication
   (engine/create {})
   {:ante [:a]
    :cons [:g]
    :op-id nil
    :delta-w [2.0 0.0]
    :stamps #{1}
    :dt 1}))

(deftest propagate-enqueues-subgoal
  (testing "goal spike spawns antecedent goal"
    (let [eng (-> (engine-with-link)
                  (event/add-event :goal {:term [:g] :truth {:f 1.0 :c 0.9}}))
          eng' (goal/propagate eng)
          goals (map :term (get-in eng' [:fifo :goal :items]))]
      (is (some #{[:a]} goals))
      (is (= 1 (->> goals (filter #{[:a]}) count))))))

(deftest propagate-merges-truth
  (let [eng (-> (engine-with-link)
                (event/add-event :goal {:term [:g] :truth {:f 0.6 :c 0.4}}))
        eng1 (goal/propagate eng)
        first-truth (-> eng1 (get-in [:concepts [:a] :goal-spikes]) last :truth)
        eng2 (event/add-event eng1 :goal {:term [:g] :truth {:f 1.0 :c 0.6}})
        eng3 (goal/propagate eng2)
        merged-truth (-> eng3 (get-in [:concepts [:a] :goal-spikes]) last :truth)]
    (is (> (:c merged-truth) (:c first-truth)))
    (is (> (:f merged-truth) (:f first-truth)))))

(deftest propagate-respects-depth-limit
  (let [eng (-> (engine/create {:params {:prop-iters 1}})
                (memory/upsert-implication {:ante [:a]
                                            :cons [:g]
                                            :op-id nil
                                            :delta-w [2.0 0.0]
                                            :stamps #{1}
                                            :dt 1})
                (event/add-event :goal {:term [:g]}))
        eng1 (goal/propagate eng)
        depth-count (->> (get-in eng1 [:fifo :goal :items])
                         (filter #(= [:a] (:term %)))
                         count)
        eng2 (goal/propagate eng1)
        depth-count2 (->> (get-in eng2 [:fifo :goal :items])
                          (filter #(= [:a] (:term %)))
                          count)]
    (is (= 1 depth-count))
    (is (= depth-count depth-count2))))
