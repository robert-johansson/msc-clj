(ns msc.truth
  "Truth calculus helpers for MSC. All functions are pure and never mutate inputs.")

(def ^:private default-truth {:f 0.5 :c 0.0})
(def ^:private evidential-horizon 1.0)
(def ^:private projection-decay 0.8)

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
       :c (/ sum (+ sum evidential-horizon))})))

(defn- w->c [w]
  (if (<= w 0.0)
    0.0
    (/ w (+ w evidential-horizon))))

(defn- c->w [c]
  (if (<= c 0.0)
    0.0
    (/ (* evidential-horizon c)
       (- 1.0 c))))

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

(defn project
  "Project confidence across `dt` cycles using MSC's exponential decay."
  [truth dt]
  (let [c (double (or (:c truth) (:c default-truth)))
        diff (Math/abs (double (or dt 0)))
        factor (Math/pow projection-decay diff)]
    (assoc truth :c (* c factor))))

(defn eternalize
  "Apply MSC Truth_Eternalize to shrink confidence."
  [truth]
  (let [c (double (or (:c truth) (:c default-truth)))]
    (assoc truth :c (w->c c))))

(defn induction
  "MSC Truth_Induction followed by Truth_Eternalize."
  [ante-truth cons-truth]
  (let [f1 (double (or (:f cons-truth) (:f default-truth)))
        f2 (double (or (:f ante-truth) (:f default-truth)))
        c1 (double (or (:c cons-truth) (:c default-truth)))
        c2 (double (or (:c ante-truth) (:c default-truth)))
        w (* f2 c1 c2)
        c (w->c w)]
    (eternalize {:f f1 :c c})))
