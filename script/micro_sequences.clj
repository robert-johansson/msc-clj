(ns micro-sequences
  (:require [msc.engine :as engine]
            [msc.event :as event]
            [msc.truth :as truth]))

(def op-left 1)
(def op-right 2)
(def term-op-left [:op op-left])
(def term-op-right [:op op-right])
(def term-goal [:micro :G])

(def sequences
  [{:sample [:micro :A1] :cue [:micro :B1_left] :op op-left :label :a1-b1-left}
   {:sample [:micro :A1] :cue [:micro :B1_right] :op op-right :label :a1-b1-right}
   {:sample [:micro :A2] :cue [:micro :B2_left] :op op-left :label :a2-b2-left}
   {:sample [:micro :A2] :cue [:micro :B2_right] :op op-right :label :a2-b2-right}])

(defn idle [eng cycles]
  (loop [e eng n cycles]
    (if (zero? n) e (recur (first (engine/step e {:beliefs [] :goals []})) (dec n)))))

(defn inject [eng events]
  (reduce (fn [e ev]
            (first (engine/step e {:beliefs [ev] :goals []})))
          eng
          events))

(defn run-trial [eng {:keys [sample cue op]}]
  (-> eng
      (inject [{:term sample :procedural? true}
               {:term cue :procedural? true}
               {:term (if (= op op-left) term-op-left term-op-right) :op-id op}
               {:term term-goal}])
      (idle 100)))

(def implication-keys
  {:a1-b1-left [[:seq [:seq [:micro :A1] [:micro :B1_left]] term-op-left] term-goal op-left]
   :a1-b1-right [[:seq [:seq [:micro :A1] [:micro :B1_right]] term-op-right] term-goal op-right]
   :a2-b2-left [[:seq [:seq [:micro :A2] [:micro :B2_left]] term-op-left] term-goal op-left]
   :a2-b2-right [[:seq [:seq [:micro :A2] [:micro :B2_right]] term-op-right] term-goal op-right]})

(defn -main [& _]
  (let [engine (engine/create {:ops {op-left {:term term-op-left}
                                     op-right {:term term-op-right}}
                               :params {:motor-babble 0.0}})
        engine (reduce (fn [eng seq]
                         (nth (iterate #(run-trial % seq) eng) 5))
                       engine
                       sequences)
        imps (:implications engine)]
    (doseq [[label key] implication-keys]
      (let [truth (get-in imps [key :truth])]
        (println label truth)))))
