(ns msc.anticipation-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc.anticipation :as anticipation]))

(deftest activate-adds-anticipations
  (testing "belief spike activates expectations above threshold"
    (let [engine {:params {:prop-th 0.6}
                  :implications {[:a :g nil] {:ante [:a]
                                              :cons [:g]
                                              :op-id nil
                                              :expectation 0.7}
                                  [:b :g nil] {:ante [:b]
                                               :cons [:g]
                                               :op-id nil
                                               :expectation 0.5}}}
          event {:term [:a] :stamp 42}
          eng' (anticipation/activate engine [event])]
      (is (= 1 (count (:anticipations eng'))))
      (is (= #{42} (:stamps (first (:anticipations eng'))))))))

(deftest activate-procedural-antecedent
  (let [engine {:params {:prop-th 0.5}
                :concepts {[:a] {:belief-spikes [{:time 5}]}}
                :time 5
                :implications {[:proc :g 1] {:ante [:seq [:a] [:op 1]]
                                             :cons [:g]
                                             :op-id 1
                                             :expectation 0.8}}}
        op-event {:term [:op 1] :time 6 :stamp 8}
        eng' (anticipation/activate engine [op-event])]
    (is (= 1 (count (:anticipations eng'))))
    (is (= [:g] (:cons (first (:anticipations eng')))))))

(deftest procedural-needs-op-event
  (let [engine {:params {:prop-th 0.5}
                :concepts {[:a] {:belief-spikes [{:time 5}]}}
                :implications {[:proc :g 1] {:ante [:seq [:a] [:op 1]]
                                             :cons [:g]
                                             :op-id 1
                                             :expectation 0.8}}}
        a-event {:term [:a] :time 5}
        eng' (anticipation/activate engine [a-event])]
    (is (empty? (:anticipations eng')))))

(deftest consume-clears-queue
  (let [[pending eng] (anticipation/consume {:anticipations [{:key 1}]})]
    (is (= [{:key 1}] pending))
    (is (empty? (:anticipations eng)))))
