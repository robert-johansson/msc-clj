(ns msc.mining-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc.engine :as engine]
            [msc.event :as event]
            [msc.mining :as mining]))

(defn- ingest-at [engine time events]
  (-> engine
      (assoc :time time)
      (event/ingest {:beliefs events})))

(deftest temporal-induction-creates-link
  (testing "A before G yields ⟨A ⇒ G⟩"
    (let [eng (engine/create {:params {:fifo-cap 5}})
          eng (ingest-at eng 0 [{:term [:A]}])
          eng (ingest-at eng 5 [{:term [:G]}])
          eng' (mining/run eng)
          key [[:A] [:G] nil]
          record (get-in eng' [:implications key])]
      (is record)
      (is (= [:A] (:ante record)))
      (is (= [:G] (:cons record)))
      (is (= 5 (:dt record)))
      (is (= [key] (get-in eng' [:concepts [:G] :tables nil]))))))

(deftest procedural-induction-creates-link
  (testing "A, op, G yields procedural implication"
    (let [eng (engine/create {:params {:fifo-cap 5}})
          eng (ingest-at eng 0 [{:term [:A]}])
          eng (ingest-at eng 1 [{:term [:op 1] :op-id 1}])
          eng (ingest-at eng 5 [{:term [:G]}])
          eng' (mining/run eng)
          key [[:seq [:A] [:op 1]] [:G] 1]
          record (get-in eng' [:implications key])]
      (is record)
      (is (= [:seq [:A] [:op 1]] (:ante record)))
      (is (= 1 (:op-id record))))))

(defn- inject-seq [engine events]
  (reduce
   (fn [eng {:keys [term op-id truth]}]
     (-> eng
         (event/add-event :belief {:term term
                                   :op-id op-id
                                   :truth truth})
         (update :time inc)))
   engine
   events))

(deftest induction-uses-consequent-truth
  (let [eng (-> (engine/create {})
                (assoc :time 0)
                (inject-seq [{:term [:A]}
                             {:term [:op 1] :op-id 1}
                             {:term [:G] :truth {:f 0.0 :c 0.9}}]))
        eng' (mining/run eng)
        w (get-in eng' [:implications [[:seq [:A] [:op 1]] [:G] 1] :w])]
    (is (= 0.0 (first w)))
    (is (> (second w) 0.3))))
