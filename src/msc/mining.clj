(ns msc.mining
  "Temporal and procedural induction over recent events."
  (:require [msc.infer :as infer]
            [msc.stamp :as stamp]))

(defn- belief-history [engine]
  (get-in engine [:fifo :belief :items]))

(defn- new-beliefs [engine]
  (get-in engine [:ingested :belief]))

(defn- temporal-candidates [history cons-event]
  (filter #(and (nil? (:op-id %))
                (< (:time %) (:time cons-event))
                (stamp/independent? #{(:stamp %)} #{(:stamp cons-event)}))
          history))

(defn- induce-temporal [engine]
  (let [history (belief-history engine)]
    (reduce
     (fn [eng cons]
       (if (nil? (:op-id cons))
         (reduce
          (fn [eng' ante]
            (infer/induce eng'
                          {:ante (:term ante)
                           :cons (:term cons)
                           :op-id nil
                           :stamps (stamp/union #{(:stamp ante)} #{(:stamp cons)})
                           :dt (- (:time cons) (:time ante))}))
          eng
          (temporal-candidates history cons))
         eng))
     engine
     (new-beliefs engine))))

(defn- op-events-before [history cons-event]
  (filter #(and (:op-id %)
                (< (:time %) (:time cons-event)))
          history))

(defn- antecedents-before [history event]
  (filter #(and (nil? (:op-id %))
                (< (:time %) (:time event)))
          history))

(defn- induce-procedural [engine]
  (let [history (belief-history engine)]
    (reduce
     (fn [eng cons]
       (if (nil? (:op-id cons))
         (reduce
          (fn [eng' op-event]
            (reduce
             (fn [eng'' ante]
               (infer/induce eng''
                             {:ante [:seq (:term ante) (:term op-event)]
                              :cons (:term cons)
                              :op-id (:op-id op-event)
                              :stamps (stamp/union #{(:stamp ante)}
                                                   #{(:stamp op-event)}
                                                   #{(:stamp cons)})
                              :dt (- (:time cons) (:time ante))}))
             eng'
             (antecedents-before history op-event)))
          eng
          (op-events-before history cons))
         eng))
     engine
     (new-beliefs engine))))

(defn run [engine]
  (-> engine
      (induce-temporal)
      (induce-procedural)))
