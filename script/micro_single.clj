(ns micro-single
  (:require [msc.engine :as engine]))

(def op-right 2)
(def term-op-right [:op op-right])

(defn idle [eng cycles]
  (nth (iterate #(first (engine/step % {:beliefs [] :goals []})) eng) cycles))

(defn inject [eng event]
  (first (engine/step eng {:beliefs [event] :goals []})))

(defn seq-term [a b] [:seq a b])

(defn run-trial [eng]
  (-> eng
      (inject {:term [:micro :A2] :procedural? true})
      (inject {:term [:micro :B2_right] :procedural? true})
      (inject {:term (seq-term [:micro :A2] [:micro :B2_right]) :procedural? true})
      (inject {:term term-op-right :op-id op-right})
      (inject {:term [:micro :G]})
      (idle 100)))

(def implication-key [[:seq (seq-term [:micro :A2] [:micro :B2_right]) term-op-right]
                      [:micro :G] op-right])

(defn -main [& _]
  (let [eng (engine/create {:ops {op-right {:term term-op-right}}})
        eng (nth (iterate run-trial eng) 5)
        imp (get-in eng [:implications implication-key :truth])]
    (println implication-key imp)))
