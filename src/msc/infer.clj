(ns msc.infer
  "Learning pipeline utilities: induction and assumption-of-failure."
  (:require [msc.memory :as memory]))

(defn assumption-of-failure
  "Applies ε negative evidence to anticipated links when their consequents fail to
   materialize. `anticipated` is a seq of maps {:ante :cons :op-id :stamps :dt}."
  [engine anticipated eps]
  (reduce (fn [eng {:keys [ante cons op-id stamps dt]}]
            (memory/upsert-implication eng
                                       {:ante ante
                                        :cons cons
                                        :op-id op-id
                                        :delta-w [0.0 eps]
                                        :stamps stamps
                                        :dt dt}))
          engine
          anticipated))

(defn induce
  "Insert or revise an implication derived from observed evidence. `opts` mirrors
   `memory/upsert-implication` but defaults dw+ to 1.0 to represent a success."
  [engine {:keys [ante cons op-id stamps dt delta-w]
           :or {delta-w [1.0 0.0]}}]
  (memory/upsert-implication engine
                             {:ante ante
                              :cons cons
                              :op-id op-id
                              :delta-w delta-w
                              :stamps stamps
                              :dt dt}))
