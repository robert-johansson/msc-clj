(ns msc.exp1-harness
  (:require [clojure.string :as str]
            [msc.engine :as engine]))

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
(def term-a1-left [:exp1 :A1-left])
(def term-a2-left [:exp1 :A2-left])
(def term-a1-right [:exp1 :A1-right])
(def term-a2-right [:exp1 :A2-right])
(def operation-terms {op-left-id [:op op-left-id]
                      op-right-id [:op op-right-id]})
(def implication-left [[:seq term-a1-left (operation-terms op-left-id)]
                       goal-term op-left-id])
(def implication-right [[:seq term-a1-right (operation-terms op-right-id)]
                        goal-term op-right-id])
(def default-truth {:f 0.5 :c 0.0})
(def default-config {:motor-babble 0.2
                     :negative-c 0.9
                     :trace? false})

(defn initial-context
  ([]
   (initial-context default-config))
  ([config]
   (let [cfg (merge default-config config)
         engine (engine/create {:ops {op-left-id {:term (operation-terms op-left-id)}
                                      op-right-id {:term (operation-terms op-right-id)}}
                                :params {:motor-babble (:motor-babble cfg)}})
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
  [ctx]
  (first (run-step* ctx {:goals [{:term goal-term}]})))

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
    [{:term term-a2-left :procedural? false}
     {:term term-a1-left :procedural? true}]
    [{:term term-a2-right :procedural? false}
     {:term term-a1-right :procedural? true}]))

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

(defn- increment-stat [ctx k]
  (update-in ctx [:stats k] (fnil inc 0)))

(defn- attempt-decision
  [ctx]
  (loop [state ctx
         attempts max-extra-attempts]
    (let [[state' op] (wait-for-decision state max-wait-cycles)]
      (if op
        [(increment-stat state' :decisions) op]
        (let [state'' (increment-stat state' :forced)
              forced (random-op state'')]
          (if (zero? attempts)
            (force-operation state'' forced)
            (recur (issue-goal state'') (dec attempts))))))))

(defn- deliver-feedback
  [ctx success?]
  (if success?
    (inject-belief ctx {:term goal-term})
    (let [neg-c (get-in ctx [:config :negative-c] 0.9)]
      (inject-belief ctx {:term goal-term
                          :truth {:f 0.0 :c neg-c}}))))

(defn- measurement
  [engine]
  {:exp-left (truth-or-default engine implication-left)
   :exp-right (truth-or-default engine implication-right)})

(defn run-trial
  [ctx {:keys [phase block trial a1-left? provide-feedback?]}]
  (let [expected (if a1-left? op-left-id op-right-id)
        ctx (-> ctx
                clear-active-terms
                (inject-beliefs (stimuli-events a1-left?)))
        ctx (issue-goal ctx)
        [ctx decision] (attempt-decision ctx)
        [ctx decision] (if decision
                         [ctx decision]
                         (force-operation (increment-stat ctx :forced)
                                          (random-op ctx)))
        ctx (clear-active-terms ctx)
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
                 :a1-left? a1-left?
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
                (let [a1-left? (zero? (mod toggle 2))
                      [state'' result] (run-trial state'
                                                  {:phase name
                                                   :block block
                                                   :trial trial
                                                   :a1-left? a1-left?
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

(defn summarize-results
  [results]
  (let [training (filter #(= :training (:phase %)) results)
        testing (filter #(= :testing (:phase %)) results)
        final-training (last training)
        accuracy (if (seq testing)
                   (/ (count (filter :correct? testing))
                      (double (count testing)))
                   0.0)]
    {:training {:final-left (:exp-left final-training)
                :final-right (:exp-right final-training)}
     :testing {:accuracy accuracy}}))

(def csv-header
  ["phase" "block" "trial" "a1_left" "chosen_op" "correct"
   "exp_left_f" "exp_left_c" "exp_right_f" "exp_right_c"])

(defn format-row
  [{:keys [phase block trial a1-left? chosen-op correct? exp-left exp-right]}]
  [(name phase)
   (str block)
   (str trial)
   (if a1-left? "1" "0")
   (str (or chosen-op 0))
   (if correct? "1" "0")
   (format "%.6f" (:f exp-left))
   (format "%.6f" (:c exp-left))
   (format "%.6f" (:f exp-right))
   (format "%.6f" (:c exp-right))])

(defn export-csv!
  [path]
  (let [results (:results (run-exp1-context))
        rows (map format-row results)
        lines (cons csv-header rows)]
    (spit path
          (str/join "\n" (map #(str/join "," %) lines)))))

(defn tracked-truths
  "Return the truth values of the two key procedural implications from a context."
  [{:keys [engine]}]
  (let [imps (:implications engine)]
    {:left (get-in imps [implication-left :truth])
     :right (get-in imps [implication-right :truth])}))

(defn context-stats
  [{:keys [stats]}]
  stats)
(defn decision-trace [context]
  (get-in context [:engine :decision-trace]))
