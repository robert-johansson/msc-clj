(ns micro-quad
  (:require [msc.engine :as engine]))

(def op-left 1)
(def op-right 2)
(def term-op-left [:op op-left])
(def term-op-right [:op op-right])

(defn idle [eng n]
  (nth (iterate #(first (engine/step % {:beliefs [] :goals []})) eng) n))

(defn inject [eng event]
  (first (engine/step eng {:beliefs [event] :goals []})))

(defn seq-term [a b] [:seq a b])

(def trials
  [{:sample [:micro :A1] :cue [:micro :B1_left] :seq [:micro :B1_left] :term term-op-left :op op-left :label :a1-b1-left}
   {:sample [:micro :A1] :cue [:micro :B1_right] :seq [:micro :B1_right] :term term-op-right :op op-right :label :a1-b1-right}
   {:sample [:micro :A2] :cue [:micro :B2_left] :seq [:micro :B2_left] :term term-op-left :op op-left :label :a2-b2-left}
   {:sample [:micro :A2] :cue [:micro :B2_right] :seq [:micro :B2_right] :term term-op-right :op op-right :label :a2-b2-right}])

(defn run-trial [eng {:keys [sample cue seq term op]}]
  (-> eng
      (inject {:term sample :procedural? true})
      (inject {:term cue :procedural? true})
      (inject {:term (seq-term sample seq) :procedural? true})
      (inject {:term term :op-id op})
      (inject {:term [:micro :G]})
      (idle 100)))

(def implication-keys
  {:a1-b1-left [[:seq (seq-term [:micro :A1] [:micro :B1_left]) term-op-left] [:micro :G] op-left]
   :a1-b1-right [[:seq (seq-term [:micro :A1] [:micro :B1_right]) term-op-right] [:micro :G] op-right]
   :a2-b2-left [[:seq (seq-term [:micro :A2] [:micro :B2_left]) term-op-left] [:micro :G] op-left]
   :a2-b2-right [[:seq (seq-term [:micro :A2] [:micro :B2_right]) term-op-right] [:micro :G] op-right]})

(defn -main [& _]
  (let [eng (engine/create {:ops {op-left {:term term-op-left}
                                   op-right {:term term-op-right}}})
        eng (nth (iterate (fn [e]
                            (reduce run-trial e trials))
                          eng)
                 5)
        imps (:implications eng)]
    (doseq [[label key] implication-keys]
      (println label (get-in imps [key :truth])))))
