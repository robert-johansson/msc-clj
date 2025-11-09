(ns msc.fifo
  "Bounded FIFO structures used for belief/goal queues."
  (:require [clojure.core :as core]))

(defn create
  "Create a FIFO with fixed positive capacity. Optional `initial` sequence is trimmed
   to the newest entries."
  ([capacity]
   (create capacity []))
  ([capacity initial]
   (assert (pos? capacity) "FIFO capacity must be positive")
   {:capacity capacity
    :items    (vec (take-last capacity initial))}))

(defn count-items [{:keys [items]}]
  (count items))

(defn full? [{:keys [capacity items]}]
  (>= (count items) capacity))

(defn push
  "Add a new item to the FIFO, evicting the oldest when capacity is exceeded."
  [fifo item]
  (let [{:keys [capacity items]} fifo
        updated (conj items item)
        overflow (max 0 (- (count updated) capacity))
        trimmed (if (pos? overflow)
                  (subvec updated overflow)
                  updated)]
    (assoc fifo :items trimmed)))

(defn peek-oldest [{:keys [items]}]
  (first items))

(defn peek-newest [{:keys [items]}]
  (peek items))

(defn take-all
  "Return [items fifo'] with FIFO cleared."
  [fifo]
  [(:items fifo) (assoc fifo :items [])])

(defn to-seq [{:keys [items]}]
  (seq items))
