(ns msc.goal
  "Goal propagation and decision scaffolding."
  (:require [msc.event :as event]))

(defn- candidate-links [engine term threshold]
  (->> (vals (:implications engine))
       (filter #(and (= (:cons %) term)
                     (>= (:expectation %) threshold)))))

(defn- antecedent-term [link]
  (let [ante (:ante link)]
    (if (and (vector? ante)
             (= :seq (first ante))
             (>= (count ante) 3))
      (second ante)
      ante)))

(defn- belief-available? [engine term]
  (some? (last (get-in engine [:concepts term :belief-spikes]))))

(defn- spawn-subgoal [engine target link]
  (let [depth (inc (or (:depth target) 0))
        subgoal {:term (:ante link)
                 :truth (:truth target)
                 :depth depth
                 :kind :goal
                 :time (:time engine)}]
    [(event/add-event engine :goal subgoal {:record-ingested? false})
     subgoal]))
(defn propagate
  "Naive goal propagation: for each goal spike, enqueue antecedent goals whose
   expectation exceeds prop-th."
  [engine]
  (let [threshold (get-in engine [:params :prop-th] 0.501)
        depth-limit (get-in engine [:params :prop-iters] 5)
        initial (vec (or (get-in engine [:ingested :goal]) []))]
    (let [eng' (loop [eng engine
                      queue initial]
                 (if (empty? queue)
                   eng
                   (let [goal (first queue)
                         queue-rest (subvec queue 1)
                         depth (or (:depth goal) 0)]
                     (if (>= depth depth-limit)
                       (recur eng queue-rest)
                       (let [[eng' queue'] (reduce
                                            (fn [[e q] link]
                                              (let [[e' subgoal] (spawn-subgoal e goal link)]
                                                [e' (conj q subgoal)]))
                                            [eng queue-rest]
                                            (candidate-links eng (:term goal) threshold))]
                         (recur eng' queue'))))))]
      (assoc-in eng' [:ingested :goal] []))))

(defn decide
  "Select an operation based on procedural implications whose desire exceeds
   `decision-th`. Falls back to motor babbling when no rule applies."
  [engine rng]
  (let [decision-th (get-in engine [:params :decision-th] 0.501)
        goals (get-in engine [:fifo :goal :items])
        candidates (for [goal goals
                         link (candidate-links engine (:term goal) decision-th)
                         :let [op-id (:op-id link)
                               ante (antecedent-term link)]
                         :when (and op-id (belief-available? engine ante))]
                     {:op-id op-id
                      :goal goal
                      :link link
                      :desire (:expectation link)})
        best (when (seq candidates)
               (apply max-key :desire candidates))]
    (if (and best (>= (:desire best) decision-th))
      (let [op-id (:op-id best)
            term (or (get-in engine [:ops op-id :term])
                     [:op op-id])
            effect {:op-id op-id
                    :term term
                    :time (:time engine)}]
        [engine [effect] rng])
      (let [motor (get-in engine [:params :motor-babble] 0.0)
            roll (.nextDouble rng)]
        (if (and (< roll motor) (seq (:ops engine)))
          (let [ops (vec (keys (:ops engine)))
                idx (.nextInt rng (count ops))
                op-id (ops idx)
                term (or (get-in engine [:ops op-id :term])
                         [:op op-id])
                effect {:op-id op-id
                        :term term
                        :time (:time engine)}]
            [engine [effect] rng])
          [engine [] rng])))))
