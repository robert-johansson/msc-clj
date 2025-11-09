(ns msc.acceptance-test
  (:require [clojure.test :refer [deftest is]]
            [msc.engine :as engine]))

(defn- run-step [engine inputs]
  (first (engine/step engine inputs)))

(defn- idle-cycles [engine n]
  (loop [eng engine
         i 0]
    (if (>= i n)
      eng
      (recur (run-step eng {:beliefs [] :goals []}) (inc i)))))

(deftest hundred-tick-confidence-grows
  (let [trials 5
        gap 100
        eng-final
        (loop [eng (engine/create {})
               t 0]
          (if (= t trials)
            eng
            (let [eng (run-step eng {:beliefs [{:term [:A]}]})
                  eng (idle-cycles eng (dec gap))
                  eng (run-step eng {:beliefs [{:term [:G]}]})
                  eng (idle-cycles eng 1)]
              (recur eng (inc t)))))]
    (let [record (get-in eng-final [:implications [[:A] [:G] nil]])
          {:keys [f c]} (:truth record)]
      (is record)
      (is (> f 0.9))
      (is (> c 0.7)))))
