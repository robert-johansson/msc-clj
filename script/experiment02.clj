(ns experiment02
  (:require [msc.exp1-harness :as harness]))

(defn -main [& [path]]
  (let [output (or path "exp2_clj.csv")]
    (harness/export-exp2-csv! output)
    (println "Experiment 2 CSV written to" output)))
