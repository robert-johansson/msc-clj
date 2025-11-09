(ns msc.event-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc.engine :as engine]
            [msc.event :as event]))

(deftest ingest-populates-fifos-and-spikes
  (testing "belief ingestion tracks spikes and respects capacity"
    (let [eng (engine/create {:params {:fifo-cap 3 :spike-limit 2}})
          beliefs [{:term [:ball-left]}
                   {:term [:ball-left]}
                   {:term [:ball-left]}]
          eng' (event/ingest eng {:beliefs beliefs})
          spikes (get-in eng' [:concepts [:ball-left] :belief-spikes])]
      (is (= 2 (count spikes)))
      (is (= 3 (count (get-in eng' [:fifo :belief :items]))))
      (is (every? :stamp spikes))
      (is (= 3 (count (get-in eng' [:ingested :belief])))))))

(deftest ingest-goals-updates-concepts
  (let [eng (engine/create {})
        goals [{:term [:reward]}]
        eng' (event/ingest eng {:goals goals})]
    (is (= [:reward] (get-in eng' [:concepts [:reward] :term])))
    (is (= :goal (:kind (first (get-in eng' [:fifo :goal :items])))))
    (is (= 1 (count (get-in eng' [:ingested :goal]))))))
