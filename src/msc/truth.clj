(ns msc.truth
  "Truth calculus helpers for MSC. All functions are pure and never mutate inputs.")

(def ^:private default-truth {:f 0.5 :c 0.0})

(defn fc->w
  "Convert {:f f :c c} into evidence counts [w+ w-]. When confidence is zero,
   no evidence has been seen yet, so both counts are zero."
  [{:keys [f c]}]
  (let [f (double (or f (:f default-truth)))
        c (double (or c (:c default-truth)))]
    (if (or (<= c 0.0) (>= c 1.0))
      (if (<= c 0.0)
        [0.0 0.0]
        ;; clamp: confidence must remain < 1.0, approximate with large weight
        (let [w 1.0e9]
          [(* f w) (* (- 1.0 f) w)]))
      (let [w (/ c (- 1.0 c))]
        [(* f w) (* (- 1.0 f) w)]))))

(defn w->fc
  "Convert evidence counts `[w+ w-]` into {:f f :c c}. Handles zero-evidence
   inputs by returning the default truth value."
  [[w+ w-]]
  (let [w+ (double (max 0.0 (or w+ 0.0)))
        w- (double (max 0.0 (or w- 0.0)))
        sum (+ w+ w-)]
    (if (<= sum 0.0)
      default-truth
      {:f (/ w+ sum)
       :c (/ sum (inc sum))})))

(defn expectation
  "Return MSC expectation for a truth map {:f f :c c}. Frequency defaults to 0.5,
   confidence defaults to 0.0."
  [{:keys [f c]}]
  (let [f (double (or f (:f default-truth)))
        c (double (or c (:c default-truth)))]
    (+ 0.5 (* c (- f 0.5)))))

(defn revise-w
  "Add two evidence count vectors. All counts are clamped to be non-negative."
  [[w+ w-] [dw+ dw-]]
  [(max 0.0 (+ (double (or w+ 0.0))
               (double (or dw+ 0.0))))
   (max 0.0 (+ (double (or w- 0.0))
               (double (or dw- 0.0))))])

(defn revise-fc
  "Add evidence expressed as truth maps {:f :c}, returning updated truth map."
  [truth delta]
  (-> (revise-w (fc->w truth) (fc->w delta))
      (w->fc)))
