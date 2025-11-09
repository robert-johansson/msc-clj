(ns msc.truth-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc.truth :as truth]))

(deftest fc-w-roundtrip
  (testing "frequency/confidence ↔ evidence conversions"
    (doseq [pair [{:f 1.0 :c 0.5}
                  {:f 0.25 :c 0.3}
                  {:f 0.9 :c 0.8}]]
      (let [w (truth/fc->w pair)
            roundtrip (truth/w->fc w)]
        (is (<= (Math/abs (- (:f pair) (:f roundtrip))) 1e-9))
        (is (<= (Math/abs (- (:c pair) (:c roundtrip))) 1e-9))))))

(deftest expectation-monotonic
  (testing "expectation tracks confidence"
    (let [low {:f 0.8 :c 0.1}
          high {:f 0.8 :c 0.9}]
      (is (< (truth/expectation low)
             (truth/expectation high))))))

(deftest revise-w-adds-evidence
  (testing "revision never decreases evidence"
    (let [result (truth/revise-fc {:f 0.1 :c 0.2}
                                  {:f 1.0 :c 0.5})
          base (truth/fc->w {:f 0.1 :c 0.2})
          revised (truth/fc->w result)]
      (is (< (first base) (first revised)))
      (is (<= (second base) (second revised))))))
