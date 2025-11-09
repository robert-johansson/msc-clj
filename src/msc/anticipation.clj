(ns msc.anticipation
  "Tracks anticipated implications for assumption-of-failure updates.")

(defn- matching-links [engine event threshold]
  (->> (vals (:implications engine))
       (filter #(and (= (:ante %) (:term event))
                     (>= (:expectation %) threshold)))))

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
