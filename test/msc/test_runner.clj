(ns msc.test-runner
  (:require [clojure.test :as t]))

(def default-namespaces
  '[msc.truth-test
    msc.stamp-test
    msc.fifo-test
    msc.anticipation-test
    msc.memory-test
    msc.engine-test
    msc.goal-test
    msc.event-test
    msc.mining-test
    msc.infer-test])

(defn -main
  "Run clojure.test over the requested namespaces. Accepts optional namespace symbols
   on the command line; otherwise falls back to `default-namespaces`."
  [& ns-args]
  (let [targets (if (seq ns-args)
                  (map symbol ns-args)
                  default-namespaces)]
    (doseq [ns-sym targets]
      (require ns-sym))
    (let [result (apply t/run-tests targets)
          failures (+ (:fail result) (:error result))]
      (shutdown-agents)
      (System/exit (if (pos? failures) 1 0)))))
