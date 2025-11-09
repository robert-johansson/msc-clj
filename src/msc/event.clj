(ns msc.event
  "Event ingestion and spike tracking."
  (:require [msc.fifo :as fifo]
            [msc.memory :as memory]
            [msc.stamp :as stamp]))

(def default-truth {:f 1.0 :c 0.9})

(defn- normalize-event
  [event kind time stamp]
  (-> event
      (assoc :truth (or (:truth event) default-truth))
      (assoc :kind kind
             :stamp stamp
             :time time)))

(defn- append-spike [spikes event limit]
  (->> (conj (vec spikes) event)
       (take-last limit)
       vec))

(defn add-event
  "Add a single event of `kind` (:belief or :goal) to the engine. Options:
   :record-ingested? (default true) controls whether the event should appear
   in `:ingested` for downstream stages."
  ([engine kind event]
   (add-event engine kind event {:record-ingested? true}))
  ([engine kind event {:keys [record-ingested?] :or {record-ingested? true}}]
   (let [term (:term event)
         spike-key (if (= kind :belief) :belief-spikes :goal-spikes)
         spike-limit (get-in engine [:params :spike-limit] 5)
         [engine stamp] (stamp/fresh engine)
         normalized (normalize-event event kind (:time engine) stamp)
         engine (memory/ensure-concept engine term)
         engine (update-in engine [:fifo kind] fifo/push normalized)
         engine (update-in engine [:concepts term spike-key]
                           (fnil #(append-spike % normalized spike-limit) []))]
     (if record-ingested?
       (update-in engine [:ingested kind] (fnil conj []) normalized)
       engine))))

(defn- ingest-kind [engine kind events]
  (if (seq events)
    (reduce
     (fn [eng event]
       (add-event eng kind event))
     engine
     events)
    engine))

(defn ingest
  "Ingest {:beliefs [...] :goals [...]} into the engine, pushing events into FIFO
   buffers and updating concept spikes."
  [engine {:keys [beliefs goals]}]
  (-> engine
      (assoc :ingested {:belief [] :goal []})
      (ingest-kind :belief beliefs)
      (ingest-kind :goal goals)))
