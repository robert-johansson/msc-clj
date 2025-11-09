(ns msc.anticipation
  "Tracks anticipated implications for assumption-of-failure updates.")

(defn- recent-pre-spike?
  [engine pre-term op-time]
  (let [spike (last (get-in engine [:concepts pre-term :belief-spikes]))]
    (boolean (and spike
                  (< (:time spike) op-time)
                  (<= (- op-time (:time spike)) 2)))))

(defn- matching-links [engine event threshold]
  (let [term (:term event)
        event-time (or (:time event) (:time engine))]
    (->> (vals (:implications engine))
         (filter
          (fn [{:keys [ante expectation]}]
            (and (>= expectation threshold)
                 (if (and (vector? ante)
                          (= :seq (first ante))
                          (>= (count ante) 3))
                   (let [[_ pre op-term] ante]
                     (and (= op-term term)
                          (recent-pre-spike? engine pre event-time)))
                   (= ante term))))))))

(defn- add-anticipations [engine matches stamp]
  (reduce
   (fn [eng impl]
     (update eng :anticipations
             (fnil conj [])
             {:ante (:ante impl)
              :cons (:cons impl)
              :op-id (:op-id impl)
              :stamps #{stamp}
              :dt (:dt impl)}))
   engine
   matches))

(defn activate
  "Inspect belief events and append anticipated links whose expectation exceeds
   the propagation threshold."
  [engine belief-events]
  (let [threshold (get-in engine [:params :prop-th] 0.501)]
    (reduce
     (fn [eng event]
       (let [stamp (:stamp event)
             matches (matching-links eng event threshold)]
         (add-anticipations eng matches stamp)))
     engine
     belief-events)))

(defn consume
  "Return [anticipations engine'] clearing the current queue."
  [engine]
  [(:anticipations engine) (assoc engine :anticipations [])])
