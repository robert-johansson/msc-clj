(ns experiment09
  (:require [msc.exp1-harness :as harness]))

(defn -main [& [path]]
  (let [output (or path "exp09_clj.csv")]
    (harness/export-exp09-csv! output)
    (println "Experiment 0.9 CSV written to" output)))
