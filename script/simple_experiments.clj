(ns simple-experiments
  "Standalone driver for small procedural-learning experiments."
  (:require [msc.engine :as engine]))

(def op-left-id 1)
(def op-right-id 2)

(def term-a1 [:exp :A1])
(def term-a2 [:exp :A2])
(def term-g  [:exp :G])
(def op-left-term [:op op-left-id])
(def op-right-term [:op op-right-id])

(def implication-a1-left [[:seq term-a1 op-left-term] term-g op-left-id])
(def implication-a2-right [[:seq term-a2 op-right-term] term-g op-right-id])

(def default-truth {:f 0.5 :c 0.0})

(defn- base-engine []
  (engine/create {:ops {op-left-id {:term op-left-term}
                        op-right-id {:term op-right-term}}
                  :params {:motor-babble 0.0}}))

(defn- step-engine [eng inputs]
  (let [[eng' _ _] (engine/step eng inputs)]
    eng'))

(defn- inject-belief
  ([eng term]
   (inject-belief eng term {}))
  ([eng term opts]
   (step-engine eng {:beliefs [(merge {:term term}
                                      opts)]})))

(defn- inject-goal [eng term]
  (step-engine eng {:goals [{:term term}]}))

(defn- idle [eng cycles]
  (loop [state eng
         remaining cycles]
    (if (zero? remaining)
      state
      (recur (step-engine state {:beliefs [] :goals []})
             (dec remaining)))))

(defn- triple [eng stimulus-term op-term op-id]
  (-> eng
      (inject-belief stimulus-term {:procedural? true})
      (inject-belief op-term {:op-id op-id})
      (inject-belief term-g)))

(defn- truth [eng key]
  (or (get-in eng [:implications key :truth]) default-truth))

(defn- run-a1-left-only [trials]
  (loop [eng (base-engine)
         trial 1
         acc []]
    (if (> trial trials)
      {:engine eng
       :measure acc}
      (let [eng (-> eng
                    (triple term-a1 op-left-term op-left-id)
                    (idle 100))
            truth (truth eng implication-a1-left)]
        (recur eng (inc trial)
               (conj acc {:trial trial
                          :truth truth}))))))

(defn- run-alternating [pairs]
  (loop [eng (base-engine)
         pair 1
         acc []]
    (if (> pair pairs)
      {:engine eng
       :measure acc}
      (let [eng (-> eng
                    (triple term-a1 op-left-term op-left-id)
                    (idle 100))
            truth-a1 (truth eng implication-a1-left)
            eng (-> eng
                    (triple term-a2 op-right-term op-right-id)
                    (idle 100))
            truth-a2 (truth eng implication-a2-right)]
        (recur eng (inc pair)
               (conj acc {:pair pair
                          :a1-left truth-a1
                          :a2-right truth-a2}))))))

(defn- fmt-truth [{:keys [f c]}]
  (format "f=%.3f c=%.3f" f c))

(defn- report-a1-left [results]
  (println "Experiment 1: Repeat (A1, op_left, G) + 100 idle")
  (doseq [{:keys [trial truth]} results]
    (println (format "  Trial %d -> %s" trial (fmt-truth truth)))))

(defn- report-alternating [results]
  (println "\nExperiment 2: Alternate (A1, op_left, G) / (A2, op_right, G) with 100 idle gaps")
  (doseq [{:keys [pair a1-left a2-right]} results]
    (println (format "  Pair %d:" pair))
    (println (format "    A1-left rule -> %s" (fmt-truth a1-left)))
    (println (format "    A2-right rule -> %s" (fmt-truth a2-right)))))

(defn -main [& _]
  (let [{a1-measure :measure} (run-a1-left-only 5)]
    (report-a1-left a1-measure)
    (let [{alt-measure :measure} (run-alternating 5)]
      (report-alternating alt-measure))))
