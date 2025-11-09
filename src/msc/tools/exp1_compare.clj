(ns msc.tools.exp1-compare
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn load-csv [path]
  (with-open [r (io/reader path)]
    (let [[header & rows] (csv/read-csv r)
          keys (map keyword header)
          rows (remove #(every? str/blank? %) rows)]
      (doall
       (map (fn [row]
              (zipmap keys row))
            rows)))))

(defn parse-double* [s]
  (Double/parseDouble (or s "0")))

(def metrics
  {:exp_a1_left [:exp_a1_left]
   :exp_a1_right [:exp_a1_right]
   :exp_a2_left [:exp_a2_left]
   :exp_a2_right [:exp_a2_right]})

(defn rmse [xs ys]
  (Math/sqrt
   (/ (reduce + (map #(let [d (- %1 %2)] (* d d)) xs ys))
      (double (count xs)))))

(defn compare-files [baseline ours]
  (for [[label [col]] metrics]
    (let [b (map #(parse-double* (% col)) baseline)
          o (map #(parse-double* (% col)) ours)]
      {:metric label
       :rmse (rmse b o)
       :baseline-last (last b)
       :ours-last (last o)})))

(defn -main [& [baseline ours]]
  (if (and baseline ours)
    (let [b (load-csv baseline)
          o (load-csv ours)]
      (doseq [{:keys [metric rmse baseline-last ours-last]} (compare-files b o)]
        (println (format "%s rmse=%.4f baseline=%.3f ours=%.3f"
                         (name metric) rmse baseline-last ours-last))))
    (println "Usage: clj -M -m msc.tools.exp1-compare baseline.csv ours.csv")))
