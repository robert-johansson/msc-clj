(ns msc.exp1-harness
  (:require [clojure.string :as str]
            [msc.engine :as engine]
            [msc.truth :as truth]))

(def op-left-id 1)
(def op-right-id 2)
(def exp-block-trials 12)
(def exp1-baseline-blocks 3)
(def exp1-training-blocks 3)
(def exp1-testing-blocks 3)
(def exp2-baseline-blocks 2)
(def exp2-training1-blocks 4)
(def exp2-testing1-blocks 2)
(def exp2-training2-blocks 9)
(def exp2-testing2-blocks 2)
(def exp3-baseline-blocks 3)
(def exp3-training-blocks 6)
(def exp3-testing-blocks 3)
(def max-wait-cycles 64)
(def max-extra-attempts 4)
(def inter-trial-gap 100)
(def goal-term [:exp1 :goal])
(def term-a1-left [:exp1 :A1-left])
(def term-a1-right [:exp1 :A1-right])
(def term-a2-left [:exp1 :A2-left])
(def term-a2-right [:exp1 :A2-right])
(def term-a1-single [:exp1 :A1])
(def term-a2-single [:exp1 :A2])
(def operation-terms {op-left-id [:op op-left-id]
                      op-right-id [:op op-right-id]})
(defn- seq-term
  [& terms]
  (vec (cons :seq terms)))
(def implication-left [[:seq term-a1-left (operation-terms op-left-id)]
                       goal-term op-left-id])
(def implication-right [[:seq term-a1-right (operation-terms op-right-id)]
                        goal-term op-right-id])
(def implication-a2-left [[:seq term-a2-left (operation-terms op-left-id)]
                          goal-term op-left-id])
(def implication-a2-right [[:seq term-a2-right (operation-terms op-right-id)]
                           goal-term op-right-id])
(def implication-a1-left-cross [[:seq term-a1-left (operation-terms op-right-id)]
                                goal-term op-right-id])
(def implication-a1-right-cross [[:seq term-a1-right (operation-terms op-left-id)]
                                 goal-term op-left-id])
(def implication-a2-left-cross [[:seq term-a2-left (operation-terms op-right-id)]
                                goal-term op-right-id])
(def implication-a2-right-cross [[:seq term-a2-right (operation-terms op-left-id)]
                                 goal-term op-left-id])
(def implication-a1-single [[:seq term-a1-single (operation-terms op-left-id)]
                            goal-term op-left-id])
(def implication-a2-single [[:seq term-a2-single (operation-terms op-right-id)]
                            goal-term op-right-id])
(def goal-term-exp3 [:exp3 :goal])
(def term-sample-a1 [:exp3 :A1-sample])
(def term-sample-a2 [:exp3 :A2-sample])
(def term-b1-left [:exp3 :B1-left])
(def term-b1-right [:exp3 :B1-right])
(def term-b2-left [:exp3 :B2-left])
(def term-b2-right [:exp3 :B2-right])
(defn- exp3-implication
  [sample-term cue-term op-id]
  [[:seq (seq-term sample-term cue-term)
        (operation-terms op-id)]
   goal-term-exp3
   op-id])
(def exp3-implications
  {:exp-a1-b1-left (exp3-implication term-sample-a1 term-b1-left op-left-id)
   :exp-a1-b1-right (exp3-implication term-sample-a1 term-b1-right op-right-id)
   :exp-a2-b2-left (exp3-implication term-sample-a2 term-b2-left op-left-id)
   :exp-a2-b2-right (exp3-implication term-sample-a2 term-b2-right op-right-id)})
(def exp3-simple-implications
  {:simple-b1-left [[:seq term-b1-left (operation-terms op-left-id)] goal-term-exp3 op-left-id]
   :simple-b1-right [[:seq term-b1-right (operation-terms op-right-id)] goal-term-exp3 op-right-id]
   :simple-b2-left [[:seq term-b2-left (operation-terms op-left-id)] goal-term-exp3 op-left-id]
   :simple-b2-right [[:seq term-b2-right (operation-terms op-right-id)] goal-term-exp3 op-right-id]})
(def default-truth {:f 0.5 :c 0.0})
(def zero-truth {:f 0.0 :c 0.0})
(def default-config {:motor-babble 0.2
                     :negative-c 0.9
                     :trace? false
                     :engine-params {}})

(defn initial-context
  ([]
   (initial-context default-config))
  ([config]
   (let [cfg (merge default-config config)
         engine-params (merge {:motor-babble (:motor-babble cfg)}
                              (:engine-params cfg))
         engine (engine/create {:ops {op-left-id {:term (operation-terms op-left-id)}
                                      op-right-id {:term (operation-terms op-right-id)}}
                                :params engine-params})
         engine (if (:decision-trace? cfg)
                  (assoc engine :decision-trace [])
                  engine)]
     {:engine engine
      :h-rng (java.util.Random. 1337)
      :config cfg
      :stats {:decisions 0 :forced 0}
      :trace? (:trace? cfg)
      :trace []})))

(defn- record-trace [ctx inputs]
  (if-not (:trace? ctx)
    ctx
    (let [time (dec (get-in ctx [:engine :time]))
          entries (concat
                   (map (fn [b] {:time time :kind :belief :term (:term b)}) (:beliefs inputs))
                   (map (fn [g] {:time time :kind :goal :term (:term g)}) (:goals inputs)))]
      (update ctx :trace into entries))))

(defn- run-step*
  [ctx inputs]
  (let [[engine' effects _] (engine/step (:engine ctx) inputs)
        ctx' (assoc ctx :engine engine')]
    [(record-trace ctx' inputs) effects]))

(defn- clear-active-terms [ctx]
  (update ctx :engine dissoc :active-terms))

(defn- add-active-term [ctx term]
  (update ctx :engine #(update % :active-terms (fnil conj #{}) term)))

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
  ([ctx]
   (issue-goal ctx goal-term))
  ([ctx goal-term*]
   (run-step* ctx {:goals [{:term goal-term*}]})))

(defn- op-from-effects [effects]
  (some (fn [effect]
          (when (= :operation (:type effect))
            (:op-id effect)))
        effects))

(defn- inject-belief
  [ctx belief]
  (let [[ctx' _] (run-step* ctx {:beliefs [belief]})
        term (:term belief)]
    (if (:procedural? belief)
      (add-active-term ctx' term)
      ctx')))

(defn- inject-beliefs
  [ctx beliefs]
  (reduce inject-belief ctx beliefs))

(defn- stimuli-events
  [a1-left?]
  (if a1-left?
    [{:term term-a1-left :procedural? true}
     {:term term-a2-right :procedural? true}]
    [{:term term-a1-right :procedural? true}
     {:term term-a2-left :procedural? true}]))
(defn- single-stimulus [a1?]
  [(if a1?
     {:term term-a1-single :procedural? true}
     {:term term-a2-single :procedural? true})])
(defn- cue-label [term]
  (cond
    (or (= term term-b1-left)
        (= term term-b1-right)) 1
    (or (= term term-b2-left)
        (= term term-b2-right)) 2
    :else 0))

(defn- exp3-stimuli
  [sample op-id]
  (let [sample-a1? (= sample 1)
        sample-term (if sample-a1? term-sample-a1 term-sample-a2)
        {:keys [target-left target-right other-left other-right]}
        (if sample-a1?
          {:target-left term-b1-left
           :target-right term-b1-right
           :other-left term-b2-left
           :other-right term-b2-right}
          {:target-left term-b2-left
           :target-right term-b2-right
           :other-left term-b1-left
           :other-right term-b1-right})
        left-target? (= op-id op-left-id)
        left-term (if left-target? target-left other-left)
        right-term (if left-target? other-right target-right)
        target-term (if left-target? target-left target-right)]
    {:events [{:term sample-term :procedural? true}
              {:term left-term :procedural? left-target?}
              {:term right-term :procedural? (not left-target?)}]
     :sample sample
     :left (cue-label left-term)
     :right (cue-label right-term)
     :sample-term sample-term
     :left-term left-term
     :right-term right-term
     :target-term target-term}))

(defn- exp3-expected-op
  [left?]
  (if left? op-left-id op-right-id))

(defn- truth-or-default
  [engine key]
  (get-in engine [:implications key :truth] default-truth))

(defn- expectation-value
  [truth]
  (if (= truth default-truth)
    0.0
    (truth/expectation truth)))

(defn- random-op
  [ctx]
  (let [rng (:h-rng ctx)
        value (.nextInt rng 2)]
    (if (zero? value) op-left-id op-right-id)))

(defn- random-bool
  [ctx]
  (zero? (.nextInt (:h-rng ctx) 2)))

(defn- force-operation
  [ctx op-id]
  [(inject-belief ctx {:term (operation-terms op-id)
                       :op-id op-id})
   op-id])

(defn- increment-stat [ctx k]
  (update-in ctx [:stats k] (fnil inc 0)))

(defn- attempt-decision
  ([ctx goal-term*]
   (attempt-decision ctx goal-term* []))
  ([ctx goal-term* initial-effects]
   (if-let [op (op-from-effects initial-effects)]
     [(increment-stat ctx :decisions) op]
     (loop [state ctx
            attempts max-extra-attempts]
       (let [[state' op] (wait-for-decision state max-wait-cycles)]
         (if op
           [(increment-stat state' :decisions) op]
           (let [state'' (increment-stat state' :forced)
                 forced (random-op state'')
                 [state''' effects] (issue-goal state'' goal-term*)
                 op* (op-from-effects effects)]
             (cond
               op* [(increment-stat state''' :decisions) op*]
               (zero? attempts) (force-operation state''' forced)
               :else (recur state''' (dec attempts))))))))))

(defn- deliver-feedback
  [ctx goal-term* success?]
  (if success?
    (inject-belief ctx {:term goal-term*})
    (let [neg-c (get-in ctx [:config :negative-c] 0.9)]
      (inject-belief ctx {:term goal-term*
                          :truth {:f 0.0 :c neg-c}}))))

(defn- measurement
  [engine]
  {:exp-a1-left (truth-or-default engine implication-left)
   :exp-a1-right (truth-or-default engine implication-right)
   :exp-a2-left (truth-or-default engine implication-a2-left)
   :exp-a2-right (truth-or-default engine implication-a2-right)
   :exp-cross-a1-left (truth-or-default engine implication-a1-left-cross)
   :exp-cross-a1-right (truth-or-default engine implication-a1-right-cross)
   :exp-cross-a2-left (truth-or-default engine implication-a2-left-cross)
   :exp-cross-a2-right (truth-or-default engine implication-a2-right-cross)
   :simple-a1 (truth-or-default engine implication-a1-single)
   :simple-a2 (truth-or-default engine implication-a2-single)})

(defn- exp3-measurement
  [engine]
  (merge
   (into {}
         (map (fn [[k implication]]
                [k (truth-or-default engine implication)]))
         exp3-implications)
   (into {}
         (map (fn [[k implication]]
                [k (truth-or-default engine implication)]))
         exp3-simple-implications)))

(defn- single-measurement [engine]
  {:single-a1 (get-in engine [:implications implication-a1-single :truth] zero-truth)
   :single-a2 (get-in engine [:implications implication-a2-single :truth] zero-truth)})

(defn- perform-trial
  [ctx {:keys [phase block trial meta stimuli expected-op provide-feedback? measurement-fn sequence-term]
        :as opts}]
  (let [goal-term* (or (:goal-term opts) goal-term)
        ctx (-> ctx
                clear-active-terms
                (inject-beliefs stimuli))
        ctx (if sequence-term
              (inject-belief ctx {:term sequence-term :procedural? true})
              ctx)
        [ctx goal-effects] (issue-goal ctx goal-term*)
        [ctx decision] (attempt-decision ctx goal-term* goal-effects)
        [ctx decision] (if decision
                         [ctx decision]
                         (force-operation (increment-stat ctx :forced)
                                          (random-op ctx)))
        ctx (first (run-step* ctx {:beliefs [] :goals []}))
        ctx (clear-active-terms ctx)
        success? (= decision expected-op)
        ctx (if provide-feedback?
              (deliver-feedback ctx goal-term* success?)
              ctx)
        ctx (advance-cycles ctx 4)
        ctx (advance-cycles ctx inter-trial-gap)
        meas (measurement-fn (:engine ctx))]
    [ctx (merge {:phase phase
                 :block block
                 :trial trial
                 :chosen-op decision
                 :expected-op expected-op
                 :correct? success?}
                meta
                meas)]))

(defn run-trial
  [ctx {:keys [phase block trial a1-left? provide-feedback? expected-op]}]
  (let [expected (or expected-op (if a1-left?
                                   op-left-id
                                   op-right-id))]
    (perform-trial ctx {:phase phase
                        :block block
                        :trial trial
                        :meta {:a1-left? a1-left?}
                        :stimuli (stimuli-events a1-left?)
                        :expected-op expected
                        :provide-feedback? provide-feedback?
                        :measurement-fn measurement})))

(defn run-phase
  [ctx {:keys [name blocks provide-feedback? use-a1-mapping?]
        :or {use-a1-mapping? true}}]
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
                (let [a1-left? (zero? (mod toggle 2))
                      expected (if use-a1-mapping?
                                 (if a1-left? op-left-id op-right-id)
                                 (if a1-left? op-right-id op-left-id))
                      [state'' result] (run-trial state'
                                                  {:phase name
                                                   :block block
                                                   :trial trial
                                                   :a1-left? a1-left?
                                                   :expected-op expected
                                                   :provide-feedback? provide-feedback?})]
                  (recur state'' (inc trial) (inc toggle) (conj acc' result)))))]
        (let [[state-after block-data next-toggle] block-results]
          (recur state-after (inc block) next-toggle (into acc block-data)))))))

(defn run-single-trial
  [ctx {:keys [phase block trial a1-trial? provide-feedback?]}]
  (perform-trial ctx {:phase phase
                      :block block
                      :trial trial
                      :meta {:stim (if a1-trial? :A1 :A2)}
                      :stimuli (single-stimulus a1-trial?)
                      :expected-op (if a1-trial? op-left-id op-right-id)
                      :provide-feedback? provide-feedback?
                      :measurement-fn single-measurement}))

(defn run-exp09-phase
  [ctx {:keys [name blocks provide-feedback?]}]
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
                (let [a1-trial? (zero? (mod toggle 2))
                      [state'' result] (run-single-trial state'
                                                         {:phase name
                                                          :block block
                                                          :trial trial
                                                          :a1-trial? a1-trial?
                                                          :provide-feedback? provide-feedback?})]
                  (recur state'' (inc trial) (inc toggle) (conj acc' result)))))]
        (let [[state-after block-data next-toggle] block-results]
          (recur state-after (inc block) next-toggle (into acc block-data)))))))

(defn run-exp3-trial
  [ctx {:keys [phase block trial sample op-id provide-feedback?]}]
  (let [{:keys [events sample left right sample-term target-term]} (exp3-stimuli sample op-id)
        expected op-id
        seq-term* (seq-term sample-term target-term)]
    (perform-trial ctx {:phase phase
                        :block block
                        :trial trial
                        :meta {:sample sample
                               :left left
                               :right right}
                        :stimuli events
                        :expected-op expected
                        :sequence-term seq-term*
                        :provide-feedback? provide-feedback?
                        :measurement-fn exp3-measurement
                        :goal-term goal-term-exp3})))

(def exp3-combos
  [{:sample 1 :op-id op-left-id}
   {:sample 1 :op-id op-right-id}
   {:sample 2 :op-id op-left-id}
   {:sample 2 :op-id op-right-id}])

(defn run-exp3-phase
  [ctx {:keys [name blocks provide-feedback?]}]
  (loop [state ctx
         block 1
         idx 0
         acc []]
    (if (> block blocks)
      [state acc]
      (let [block-results
            (loop [state' state
                   trial 1
                   idx idx
                   acc' []]
              (if (> trial exp-block-trials)
                [state' acc' idx]
                (let [combo (nth exp3-combos (mod idx (count exp3-combos)))
                      [state'' result] (run-exp3-trial state'
                                                       {:phase name
                                                        :block block
                                                        :trial trial
                                                        :sample (:sample combo)
                                                        :op-id (:op-id combo)
                                                        :provide-feedback? provide-feedback?})]
                  (recur state'' (inc trial) (inc idx) (conj acc' result)))))]
        (let [[state-after block-data next-idx] block-results]
          (recur state-after (inc block) next-idx (into acc block-data)))))))

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

(def exp2-phases
  [{:name :baseline
    :blocks exp2-baseline-blocks
    :provide-feedback? false
    :use-a1-mapping? true}
   {:name :training1
    :blocks exp2-training1-blocks
    :provide-feedback? true
    :use-a1-mapping? true}
   {:name :testing1
    :blocks exp2-testing1-blocks
    :provide-feedback? false
    :use-a1-mapping? true}
   {:name :training2
    :blocks exp2-training2-blocks
    :provide-feedback? true
    :use-a1-mapping? false}
   {:name :testing2
    :blocks exp2-testing2-blocks
    :provide-feedback? false
    :use-a1-mapping? false}])

(def exp3-phases
  [{:name :baseline
    :blocks exp3-baseline-blocks
    :provide-feedback? false}
   {:name :training
    :blocks exp3-training-blocks
    :provide-feedback? true}
   {:name :testing
    :blocks exp3-testing-blocks
    :provide-feedback? false}])

(defn run-exp1-context
  ([] (run-exp1-context default-config))
  ([config]
   (loop [ctx (initial-context config)
          remaining phases
          acc []]
     (if (empty? remaining)
       {:context ctx
        :results acc}
       (let [[ctx' results] (run-phase ctx (first remaining))]
         (recur ctx' (rest remaining) (into acc results)))))))

(defn run-exp1
  ([] (:results (run-exp1-context default-config)))
  ([config] (:results (run-exp1-context config))))

(defn run-exp2-context
  ([] (run-exp2-context default-config))
  ([config]
   (loop [ctx (initial-context config)
          remaining exp2-phases
          acc []]
     (if (empty? remaining)
       {:context ctx
        :results acc}
       (let [[ctx' results] (run-phase ctx (first remaining))]
         (recur ctx' (rest remaining) (into acc results)))))))

(defn run-exp2
  ([] (:results (run-exp2-context default-config)))
  ([config] (:results (run-exp2-context config))))

(defn run-exp3-context
  ([] (run-exp3-context default-config))
  ([config]
   (let [cfg (update config :engine-params #(merge {:prop-iters 0
                                                    :prop-th 1.0
                                                    :eps 0.01}
                                                   %))]
     (loop [ctx (initial-context cfg)
          remaining exp3-phases
          acc []]
       (if (empty? remaining)
         {:context ctx
          :results acc}
         (let [[ctx' results] (run-exp3-phase ctx (first remaining))]
           (recur ctx' (rest remaining) (into acc results))))))))

(defn run-exp3
  ([] (:results (run-exp3-context default-config)))
  ([config] (:results (run-exp3-context config))))

(defn run-exp09-context
  ([] (run-exp09-context default-config))
  ([config]
   (loop [ctx (initial-context config)
          remaining phases
          acc []]
     (if (empty? remaining)
       {:context ctx
        :results acc}
       (let [[ctx' results] (run-exp09-phase ctx (first remaining))]
         (recur ctx' (rest remaining) (into acc results)))))))

(defn run-exp09
  ([] (:results (run-exp09-context default-config)))
  ([config] (:results (run-exp09-context config))))

(defn summarize-results
  [results]
  (let [training (filter #(= :training (:phase %)) results)
        testing (filter #(= :testing (:phase %)) results)
        final-training (last training)
        accuracy (if (seq testing)
                   (/ (count (filter :correct? testing))
                      (double (count testing)))
                   0.0)]
    {:training {:final-a1-left (:exp-a1-left final-training)
                :final-a1-right (:exp-a1-right final-training)
                :final-a2-left (:exp-a2-left final-training)
                :final-a2-right (:exp-a2-right final-training)}
     :testing {:accuracy accuracy}}))

(def csv-header
  ["phase" "block" "trial" "a1_left" "chosen_op" "correct"
   "a1_left_f" "a1_left_c" "a1_right_f" "a1_right_c"
   "a2_left_f" "a2_left_c" "a2_right_f" "a2_right_c"
   "cross_a1_left_f" "cross_a1_left_c"
   "cross_a1_right_f" "cross_a1_right_c"
   "cross_a2_left_f" "cross_a2_left_c"
   "cross_a2_right_f" "cross_a2_right_c"
   "simple_a1_f" "simple_a1_c"
   "simple_a2_f" "simple_a2_c"])

(defn- truth->fc [truth]
  [(format "%.6f" (:f truth))
   (format "%.6f" (:c truth))])

(defn format-row
  [{:keys [phase block trial a1-left? chosen-op correct?
           exp-a1-left exp-a1-right exp-a2-left exp-a2-right
           exp-cross-a1-left exp-cross-a1-right exp-cross-a2-left exp-cross-a2-right
           simple-a1 simple-a2]}]
  (-> [(name phase)
       (str block)
       (str trial)
       (if a1-left? "1" "0")
       (str (or chosen-op 0))
       (if correct? "1" "0")]
      (into (truth->fc exp-a1-left))
      (into (truth->fc exp-a1-right))
      (into (truth->fc exp-a2-left))
      (into (truth->fc exp-a2-right))
      (into (truth->fc exp-cross-a1-left))
      (into (truth->fc exp-cross-a1-right))
      (into (truth->fc exp-cross-a2-left))
      (into (truth->fc exp-cross-a2-right))
      (into (truth->fc simple-a1))
      (into (truth->fc simple-a2))))

(defn export-csv!
  [path]
  (let [results (:results (run-exp1-context))
        rows (map format-row results)
        lines (cons csv-header rows)]
    (spit path
          (str/join "\n" (map #(str/join "," %) lines)))))

(defn export-exp2-csv!
  [path]
  (let [results (:results (run-exp2-context))
        rows (map format-row results)
        lines (cons csv-header rows)]
    (spit path
          (str/join "\n" (map #(str/join "," %) lines)))))

(def exp3-csv-header
  ["phase" "block" "trial" "sample" "left" "right" "chosen_op" "correct"
   "exp_a1_b1_left" "exp_a1_b1_right" "exp_a2_b2_left" "exp_a2_b2_right"
   "simple_b1_left_f" "simple_b1_left_c"
   "simple_b1_right_f" "simple_b1_right_c"
   "simple_b2_left_f" "simple_b2_left_c"
   "simple_b2_right_f" "simple_b2_right_c"])

(defn- truth->expectation-str
  [truth]
  (format "%.6f" (expectation-value truth)))

(defn format-exp3-row
  [{:keys [phase block trial sample left right chosen-op correct?
           exp-a1-b1-left exp-a1-b1-right exp-a2-b2-left exp-a2-b2-right
           simple-b1-left simple-b1-right simple-b2-left simple-b2-right]}]
  (let [base [(name phase)
              (str block)
              (str trial)
              (str sample)
              (str left)
              (str right)
              (str (or chosen-op 0))
              (if correct? "1" "0")]
        exps [(truth->expectation-str exp-a1-b1-left)
              (truth->expectation-str exp-a1-b1-right)
              (truth->expectation-str exp-a2-b2-left)
              (truth->expectation-str exp-a2-b2-right)]
        simples [(truth->fc simple-b1-left)
                 (truth->fc simple-b1-right)
                 (truth->fc simple-b2-left)
                 (truth->fc simple-b2-right)]]
    (-> base
        (into exps)
        (into (mapcat identity simples)))))

(defn export-exp3-csv!
  [path]
  (let [results (:results (run-exp3-context))
        rows (map format-exp3-row results)
        lines (cons exp3-csv-header rows)]
    (spit path
          (str/join "\n" (map #(str/join "," %) lines)))))

(def exp09-csv-header
  ["phase" "block" "trial" "stim" "chosen_op" "correct"
   "a1_f" "a1_c" "a2_f" "a2_c"])

(defn- format-fc [truth]
  [(format "%.6f" (:f truth))
   (format "%.6f" (:c truth))])

(defn format-exp09-row
  [{:keys [phase block trial stim chosen-op correct?
           single-a1 single-a2]}]
  (let [[a1-f a1-c] (truth->fc single-a1)
        [a2-f a2-c] (truth->fc single-a2)]
    [(name phase)
     (str block)
     (str trial)
     (name stim)
     (str (or chosen-op 0))
     (if correct? "1" "0")
     a1-f a1-c a2-f a2-c]))

(defn export-exp09-csv!
  [path]
  (let [results (:results (run-exp09-context))
        rows (map format-exp09-row results)
        lines (cons exp09-csv-header rows)]
    (spit path
          (str/join "\n" (map #(str/join "," %) lines)))))

(defn tracked-truths
  "Return the truth values of the two key procedural implications from a context."
  [{:keys [engine]}]
  (let [imps (:implications engine)
        lookup (fn [key]
                 (or (get-in imps [key :truth])
                     default-truth))]
    {:a1-left (lookup implication-left)
     :a1-right (lookup implication-right)
     :a2-left (lookup implication-a2-left)
     :a2-right (lookup implication-a2-right)
     :cross-a1-left (lookup implication-a1-left-cross)
     :cross-a1-right (lookup implication-a1-right-cross)
     :cross-a2-left (lookup implication-a2-left-cross)
     :cross-a2-right (lookup implication-a2-right-cross)}))

(defn exp3-tracked-truths
  [{:keys [engine]}]
  (into {}
        (map (fn [[k implication]]
               [k (truth-or-default engine implication)]))
        exp3-implications))

(defn context-stats
  [{:keys [stats]}]
  stats)
(defn decision-trace [context]
  (get-in context [:engine :decision-trace]))
(defn- cue-label [term]
  (cond
    (or (= term term-b1-left)
        (= term term-b1-right)) 1
    (or (= term term-b2-left)
        (= term term-b2-right)) 2
    :else 0))
