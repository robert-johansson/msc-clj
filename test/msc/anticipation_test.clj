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

(deftest consume-clears-queue
  (let [[pending eng] (anticipation/consume {:anticipations [{:key 1}]})]
    (is (= [{:key 1}] pending))
    (is (empty? (:anticipations eng)))))
