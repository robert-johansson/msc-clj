(ns msc.exp1-harness
  (:require [msc.engine :as engine]))

(def op-left-id 1)
(def op-right-id 2)
(def exp-block-trials 12)
(def exp1-baseline-blocks 3)
(def exp1-training-blocks 3)
(def exp1-testing-blocks 3)
(def max-wait-cycles 64)
(def max-extra-attempts 4)
(def inter-trial-gap 100)
(def goal-term [:exp1 :goal])
(def term-a1 [:exp1 :A1])
(def term-a2 [:exp1 :A2])
(def operation-terms {op-left-id [:op op-left-id]
                      op-right-id [:op op-right-id]})
(def implication-left [[:seq term-a1 (operation-terms op-left-id)]
                       goal-term op-left-id])
(def implication-right [[:seq term-a2 (operation-terms op-right-id)]
                        goal-term op-right-id])
(def default-truth {:f 0.5 :c 0.0})

(defn initial-context
  []
  {:engine (engine/create {:ops {op-left-id {:term (operation-terms op-left-id)}
                                 op-right-id {:term (operation-terms op-right-id)}}
                           :params {:motor-babble 0.2}})
   :h-rng (java.util.Random. 1337)})

(defn- run-step*
  [ctx inputs]
  (let [[engine' effects _] (engine/step (:engine ctx) inputs)]
    [(assoc ctx :engine engine') effects]))

(defn- advance-cycles
  [ctx n]
  (loop [state ctx
         remaining n]
    (if (zero? remaining)
      state
      (let [[state' _] (run-step* state {:beliefs [] :goals []})]
        (recur state' (dec remaining))))))

(defn- wait-for-decision
  [ctx cycles]
  (loop [state ctx
         remaining cycles]
    (if (zero? remaining)
      [state nil]
      (let [[state' effects] (run-step* state {:beliefs [] :goals []})
            op (some (fn [effect]
                       (when (= :operation (:type effect))
                         (:op-id effect)))
                     effects)]
        (if op
          [state' op]
          (recur state' (dec remaining)))))))

(defn- issue-goal
  [ctx]
  (first (run-step* ctx {:goals [{:term goal-term}]})))

(defn- inject-belief
  [ctx belief]
  (first (run-step* ctx {:beliefs [belief]})))

(defn- inject-beliefs
  [ctx beliefs]
  (reduce inject-belief ctx beliefs))

(defn- stimuli-events
  [trial-index]
  (if (zero? trial-index)
    [{:term term-a1}]
    [{:term term-a2}]))

(defn- truth-or-default
  [engine key]
  (get-in engine [:implications key :truth] default-truth))

(defn- random-op
  [ctx]
  (let [rng (:h-rng ctx)
        value (.nextInt rng 2)]
    (if (zero? value) op-left-id op-right-id)))

(defn- force-operation
  [ctx op-id]
  [(inject-belief ctx {:term (operation-terms op-id)
                       :op-id op-id})
   op-id])

(defn- attempt-decision
  [ctx]
  (loop [state ctx
         attempts max-extra-attempts]
    (let [[state' op] (wait-for-decision state max-wait-cycles)]
      (if op
        [state' op]
        (if (zero? attempts)
          [state' nil]
          (recur (issue-goal state') (dec attempts)))))))

(defn- deliver-feedback
  [ctx success?]
  (if success?
    (inject-beliefs ctx [{:term goal-term}])
    (inject-beliefs ctx [{:term goal-term
                          :truth {:f 0.0 :c 0.9}}])))

(defn- measurement
  [engine]
  {:exp-left (truth-or-default engine implication-left)
   :exp-right (truth-or-default engine implication-right)})

(defn run-trial
  [ctx {:keys [phase block trial stimuli-index provide-feedback?]}]
  (let [expected (if (zero? stimuli-index) op-left-id op-right-id)
        ctx (inject-beliefs ctx (stimuli-events stimuli-index))
        ctx (issue-goal ctx)
        [ctx decision] (attempt-decision ctx)
        [ctx decision] (if decision
                         [ctx decision]
                         (let [forced (random-op ctx)]
                           (force-operation ctx forced)))
        success? (= decision expected)
        ctx (if provide-feedback?
              (deliver-feedback ctx success?)
              ctx)
        ctx (advance-cycles ctx 4)
        ctx (advance-cycles ctx inter-trial-gap)
        meas (measurement (:engine ctx))]
    [ctx (merge {:phase phase
                 :block block
                 :trial trial
                 :stimuli-index stimuli-index
                 :chosen-op decision
                 :expected-op expected
                 :correct? success?}
                meas)]))

(defn run-phase
  [ctx {:keys [name blocks provide-feedback?] :as phase}]
  (loop [state ctx
         block 1
         toggle 0
         acc []]
    (if (> block blocks)
      [state acc]
      (let [block-results
            (loop [state' state
                   trial 1
                   toggle toggle
                   acc' []]
              (if (> trial exp-block-trials)
                [state' acc' toggle]
                (let [stimuli-index (mod toggle 2)
                      [state'' result] (run-trial state'
                                                  {:phase name
                                                   :block block
                                                   :trial trial
                                                   :stimuli-index stimuli-index
                                                   :provide-feedback? provide-feedback?})]
                  (recur state'' (inc trial) (inc toggle) (conj acc' result)))))]
        (let [[state-after block-data next-toggle] block-results]
          (recur state-after (inc block) next-toggle (into acc block-data)))))))

(def phases
  [{:name :baseline
    :blocks exp1-baseline-blocks
    :provide-feedback? false}
   {:name :training
    :blocks exp1-training-blocks
    :provide-feedback? true}
   {:name :testing
    :blocks exp1-testing-blocks
    :provide-feedback? false}])

(defn run-exp1-context
  []
  (loop [ctx (initial-context)
         remaining phases
         acc []]
    (if (empty? remaining)
      {:context ctx
       :results acc}
      (let [[ctx' results] (run-phase ctx (first remaining))]
        (recur ctx' (rest remaining) (into acc results))))))

(defn run-exp1
  []
  (:results (run-exp1-context)))
