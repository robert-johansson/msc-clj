(ns experiment03
  (:require [msc.exp1-harness :as harness]))

(defn -main [& [path]]
  (let [output (or path "exp3_clj.csv")]
    (harness/export-exp3-csv! output)
    (println "Experiment 3 CSV written to" output)))
