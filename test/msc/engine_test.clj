(ns msc.engine-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc.engine :as engine]
            [msc.memory :as memory]))

(deftest create-initializes-state
  (testing "engine defaults"
    (let [eng (engine/create)]
      (is (= 0 (:time eng)))
      (is (= (:fifo-cap engine/default-params)
             (get-in eng [:params :fifo-cap])))
      (is (map? (get-in eng [:fifo :belief]))))))

(deftest step-increments-time
  (let [eng (engine/create)
        [eng' effects _] (engine/step eng {:beliefs [] :goals []})]
    (is (= 1 (:time eng')))
    (is (vector? effects))))

(deftest ingestion-persists-per-step
  (let [eng (engine/create)
        [eng1 _ _] (engine/step eng {:beliefs [{:term [:a]}]})
        [eng2 _ _] (engine/step eng1 {:beliefs []})]
    (is (= 1 (count (get-in eng1 [:ingested :belief]))))
    (is (empty? (get-in eng2 [:ingested :belief])))))

(deftest step-decides-when-rule-exists
  (let [eng (-> (engine/create {:ops {1 {:term [:op 1]}}})
                (memory/upsert-implication {:ante [:seq [:pre] [:op 1]]
                                            :cons [:g]
                                            :op-id 1
                                            :delta-w [3.0 0.0]
                                            :stamps #{1}
                                            :dt 1}))
        [_ effects _] (engine/step eng {:beliefs [{:term [:pre]}]
                                        :goals   [{:term [:g]}]})]
    (is (= 1 (:op-id (first effects))))))
