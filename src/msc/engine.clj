(ns msc.engine
  "Top-level orchestration for the immutable MSC engine."
  (:require [msc.fifo :as fifo]
            [msc.event :as event]
            [msc.infer :as infer]
            [msc.anticipation :as anticipation]
            [msc.mining :as mining]
            [msc.goal :as goal]))

(def default-params
  {:beta          0.8
   :decision-th   0.501
   :prop-th       0.501
   :prop-iters    5
   :eps           0.005
   :table-size    20
   :fifo-cap      20
   :spike-limit   5
   :decision-max-age 1
   :motor-babble  0.2})

(defn create
  "Return a freshly initialized engine map.
   Options:
     :params  → partial overrides for `default-params`
     :ops     → map of op-id -> {:term [:op id] :fn <identifier>}
     :rng     → java.util.SplittableRandom (defaults to seed 42)"
  ([] (create {}))
  ([{:keys [params ops rng]}]
   (let [params (merge default-params (or params {}))
         rng    (or rng (java.util.SplittableRandom. 42))]
     {:time 0
      :params params
      :rng rng
      :fifo {:belief (fifo/create (:fifo-cap params))
             :goal   (fifo/create (:fifo-cap params))}
      :concepts {}
      :implications {}
      :anticipations []
      :pending-feedback []
      :next-stamp-id 0
      :ops (or ops {})})))

(defn with-operator
  "Associate an operator definition with the engine."
  [engine op-id op-term]
  (assoc-in engine [:ops op-id] op-term))

(defn- apply-assumptions [engine]
  (let [[anticipated engine'] (anticipation/consume engine)
        eps (get-in engine [:params :eps])]
    (if (seq anticipated)
      (infer/assumption-of-failure engine' anticipated eps)
      engine')))

(defn- feedback->event [effect]
  {:term (:term effect)
   :truth {:f 1.0 :c 0.9}
   :op-id (:op-id effect)
   :kind :belief})

(defn- merge-feedback [engine inputs]
  (let [feedbacks (map feedback->event (:pending-feedback engine))
        beliefs (vec (concat feedbacks (or (:beliefs inputs) [])))
        goals (vec (or (:goals inputs) []))]
    [(assoc engine :pending-feedback [])
     {:beliefs beliefs
      :goals goals}]))

(defn step
  "Placeholder for the full MSC cycle. Accepts the engine, inputs map
   {:beliefs [] :goals []}, and an RNG instance. Returns [engine' effects rng']."
  ([engine inputs] (step engine inputs (:rng engine)))
  ([engine inputs rng]
   ;; Subsequent commits will replace this stub with the full pipeline.
   (let [[engine inputs] (merge-feedback engine inputs)
         engine (event/ingest engine inputs)
         engine (anticipation/activate engine (get-in engine [:ingested :belief]))
         engine (apply-assumptions engine)
         engine (mining/run engine)
         engine (goal/propagate engine)
         [engine effects rng'] (goal/decide engine rng)
         engine (assoc engine :pending-feedback effects)
         engine' (-> engine
                     (assoc :rng rng')
                     (update :time inc))]
     [engine' effects rng'])))
