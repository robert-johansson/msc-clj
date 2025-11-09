(ns msc.stamp
  "Stamp helpers track causal independence between events."
  (:require [clojure.set :as set]))

(defn fresh
  "Return [engine' stamp-id], incrementing :next-stamp-id in the engine map.
   Accepts any map; missing counter defaults to 0."
  [engine]
  (let [next-id (:next-stamp-id engine 0)]
    [(assoc engine :next-stamp-id (inc next-id)) next-id]))

(defn independent?
  "True when two stamp collections share no IDs."
  [existing incoming]
  (let [existing (set (or existing #{}))
        incoming (set (or incoming #{}))]
    (empty? (set/intersection existing incoming))))

(defn union
  "Return the set union of multiple stamp collections, discarding nils."
  [& stamp-collections]
  (->> stamp-collections
       (remove nil?)
       (mapcat seq)
       set))
