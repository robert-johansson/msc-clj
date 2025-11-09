(ns msc.memory
  "Concept and implication storage helpers."
  (:require [msc.stamp :as stamp]
            [msc.truth :as truth]))

(defn ensure-concept
  "Ensure the engine has a concept entry for `term`."
  [engine term]
  (if (get-in engine [:concepts term])
    engine
    (assoc-in engine [:concepts term]
              {:term term
               :belief-spikes []
               :goal-spikes []
               :usage 0.0
               :tables {}})))

(defn- implication-key [ante cons op-id]
  [ante cons op-id])

(defn- total-weight [[w+ w-]]
  (+ (double w+) (double w-)))

(defn- rank-table
  [engine cons-term op-id table-size new-key]
  (let [existing (get-in engine [:concepts cons-term :tables op-id] [])
        keys     (distinct (conj existing new-key))]
    (->> keys
         (map (fn [k]
                [k (get-in engine [:implications k :expectation] 0.0)]))
         (sort-by second >)
         (map first)
         (take table-size)
         vec)))

(defn upsert-implication
  "Insert or revise an implication record returning the updated engine.
   `opts` must include:
     :ante      → antecedent term
     :cons      → consequent term
     :op-id     → operator identifier (nil for temporal links)
     :delta-w   → [dw+ dw-] evidence to add (defaults to success evidence)
     :stamps    → stamp collection for the new evidence
     :dt        → temporal distance to incorporate (optional)"
  [engine {:keys [ante cons op-id delta-w stamps dt]
           :or {delta-w [1.0 0.0]}}]
  (let [key        (implication-key ante cons op-id)
        existing   (get-in engine [:implications key])
        prev-w     (:w existing [0.0 0.0])
        new-w      (truth/revise-w prev-w delta-w)
        truth      (truth/w->fc new-w)
        expectation (truth/expectation truth)
        prev-weight (total-weight prev-w)
        delta-weight (total-weight delta-w)
        combined-weight (+ prev-weight delta-weight)
        new-dt (cond
                 (and (:dt existing) dt (pos? combined-weight))
                 (/ (+ (* (:dt existing) prev-weight)
                       (* dt delta-weight))
                    combined-weight)
                 dt dt
                 :else (:dt existing))
        combined-stamps (stamp/union (:stamps existing) stamps)
        record     {:key key
                    :ante ante
                    :cons cons
                    :op-id op-id
                    :w new-w
                    :truth truth
                    :expectation expectation
                    :dt new-dt
                    :stamps combined-stamps}
        engine'    (-> engine
                       (ensure-concept cons)
                       (assoc-in [:implications key] record))
        table-size (get-in engine [:params :table-size] 20)]
    (update-in engine'
               [:concepts cons :tables op-id]
               (fn [_] (rank-table engine' cons op-id table-size key)))))
