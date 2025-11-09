Below is a from‑scratch, step‑by‑step guide to building a purely functional, idiomatic Clojure port of MSC that will not get stuck on learning across long time gaps (e.g., the repeated A … 100 ticks … G case). I’ll over‑explain every step, give you concrete data shapes, function signatures, tiny tests you can run at each step, and call out the common failure modes that made earlier attempts stall.

Core stance
Don’t transliterate the C into Clojure. Re‑express MSC’s semantics as a pipeline of pure transforms over a single immutable engine value. Side‑effects (clock, RNG, operator I/O) live at the edges as returned “effects”.

⸻

Step 0 — Project skeleton (explicit and minimal)

deps.edn

{:paths ["src" "test"]
 :deps  {org.clojure/clojure {:mvn/version "1.11.3"}
         org.clojure/test.check {:mvn/version "1.1.1"}
         org.clojure/data.priority-map {:mvn/version "1.1.0"}}}

Namespaces (suggested)

msc.term          ; keywords <-> compact IDs, building [:seq a b] terms
msc.truth         ; (f,c) <-> (w+,w-) conversions, revision, expectation, projection
msc.stamp         ; event/implication stamps, overlap checks
msc.fifo          ; ring buffers (belief, goal)
msc.memory        ; concepts, implication tables indexed by consequent+op
msc.infer         ; mining: induction (plain/procedural), assumption-of-failure
msc.decide        ; decision & subgoaling (argmax desire), PRNG threading
msc.engine        ; the immutable "engine" map + (step engine inputs rng) -> [engine' effects rng']
msc.check         ; invariants & property tests

Run tests: clj -X:test (or clj -M:test depending on your runner).

⸻

Step 1 — Terms, sequences, operations (get the atoms right)

Why this matters: Everything you learn or revise hangs on structural term equality. Getting the shape right avoids “I couldn’t find the same hypothesis to revise” bugs.

Data shapes

;; atomic terms are keywords for ergonomics; under the hood we keep small ints
;; sequence (&/ a b) is a 2-tuple; we cap depth at 3, like MSC
;; operation-terms are [:op op-id], never strings

[:ball-left]                  ; atom
[:seq :ball-left :paddle-mid] ; (&/ ball-left paddle-mid)
[:seq :pre [:op 2]]           ; (&/, pre, op2)   -- for procedural antecedents

Interning (optional but recommended later for speed)

(ns msc.term)

(defn intern-init [] {:kw->id {} :id->kw []})
(defn intern-kw [{:keys [kw->id id->kw] :as intern} k]
  (if-let [id (kw->id k)] [intern id]
      (let [id (count id->kw)]
        [(-> intern
             (assoc :kw->id (assoc kw->id k id))
             (assoc :id->kw (conj id->kw k)))
         id])))

Tiny test: interning produces consistent IDs.

(let [[in1 idA] (intern-kw (intern-init) :A)
      [in2 idA2] (intern-kw in1 :A)]
  (assert (= idA idA2)))

Pitfall to avoid: Never include timestamps or truth inside the term. Terms are purely symbolic structures.

⸻

Step 2 — Truth math that always revises (no projection here)

Why this matters: Most failures on “100‑step gap” come from (a) mixing up (f,c) and w counts, or (b) decaying away the evidence during learning. Fix both now.

Representation
	•	Keep counts w+ and w- in your implication. They only increase by addition.
	•	Convert to (f,c) for ranking / display only.

Functions

(ns msc.truth)

(defn fc->w [{:keys [f c]}]
  (if (<= c 0.0) [0.0 0.0]
      (let [w (/ c (- 1.0 c))] [(* f w) (* (- 1.0 f) w)])))

(defn w->fc [[w+ w-]]
  (let [w (+ w+ w-)]
    (if (<= w 0.0) {:f 0.5 :c 0.0}
        {:f (/ w+ w) :c (/ w (inc w))})))

(defn expectation [{:keys [f c]}]
  (+ 0.5 (* c (- f 0.5))))

(defn revise-w [[w+ w-] [dw+ dw-]]
  [(+ (double w+) (double dw+))
   (+ (double w-) (double dw-))])

Tiny tests (sanity)

(assert (= (w->fc (revise-w [0 0] [1 0])) {:f 1.0 :c 0.5}))
(assert (> (:c (w->fc (revise-w [5 0] [1 0])))
           (:c (w->fc [5 0]))))

Non‑negotiable rule: Induction adds counts. Do not apply any temporal decay to the counts during learning. Projection (decay) is for using a link later, not for creating/revising it.

⸻

Step 3 — Stamps that actually permit revision

Why this matters: If stamps overlap because you reused them, revision correctly refuses to merge. Then “confidence never climbs.”

Stamp rules
	•	Each event gets a fresh integer stamp ID.
	•	An implication’s stamp is the union of the two events’ stamp sets that produced it.
	•	You may revise an implication with new evidence iff the new union does not overlap the old implication’s stamp.

Functions

(ns msc.stamp
  (:require [clojure.set :as set]))

(defn fresh [engine] ; engine holds :next-stamp-id
  (let [id (:next-stamp-id engine 0)]
    [(assoc engine :next-stamp-id (inc id)) id]))

(defn independent? [Simp Sevt]
  (empty? (set/intersection (or Simp #{}) (or Sevt #{}))))

(defn union [S1 S2] (set/union (or S1 #{}) (or S2 #{})))

Tiny test

(assert (independent? #{1 2} #{3}))
(assert (not (independent? #{1 2} #{2 7})))


⸻

Step 4 — Events & Ring buffers (the window MSC mines)

Why this matters: Procedural rules rely on seeing pre, then an op event, then post later. You need to reliably access short suffixes of the stream.

Event shape

{:term t :kind :belief|:goal :t tick :op-id 0|int
 :truth {:f 1.0 :c 0.9} :stamp #{...} :debug "A@17"}

Ring buffer (pure)

(ns msc.fifo)

(defn make [cap] {:buf (vec (repeat cap nil)) :head 0 :len 0 :cap cap})

(defn push [{:keys [buf head len cap] :as q} x]
  (let [buf' (assoc buf head x)
        head' (mod (inc head) cap)
        len' (min cap (inc len))]
    (assoc q :buf buf' :head head' :len len')))

(defn kth-newest
  "k=0 newest, 1 next, …; returns nil if k>=len"
  [{:keys [buf head len cap]} k]
  (when (< k len)
    (let [idx (mod (- head (inc k)) cap)]
      (buf idx))))

Tiny test

(-> (make 3) (push :a) (push :b) (push :c) (push :d)
    (kth-newest 0)) ; => :d


⸻

Step 5 — Implications indexed by consequent + op

Why this matters: Decision & subgoaling look up “How do I get G (goal)?” That’s a lookup by consequent (and operation), not by antecedent. If you index by antecedent, you’ll miss the right link or spend time scanning everything.

Implication record

{:ante a-term
 :cons g-term
 :op-id 0|int
 :dt 100.0             ; average ∆t from A to G
 :w+ 3.0, :w- 0.2
 :stamp #{...}}

Index key & upsert

(ns msc.memory)

(defn imp-key [{:keys [ante cons op-id]}] [ante cons op-id])

(defn upsert-imp
  "Merge by adding counts; weighted-average dt; union stamps."
  [index {:keys [ante cons op-id dt w+ w- stamp] :as new}]
  (let [k [ante cons op-id]
        old (get index k)
        w+0 (:w+ old 0.0), w-0 (:w- old 0.0)
        W0 (+ w+0 w-0), dW (+ w+ w-)
        dt' (if (pos? (+ W0 dW))
              (/ (+ (* W0 (double (:dt old 0.0))) (* dW (double dt)))
                 (+ W0 dW))
              dt)
        w+1 (+ w+0 w+), w-1 (+ w-0 w-)
        stamp' (clojure.set/union (:stamp old #{}) (or stamp #{}))]
    (assoc index k {:ante ante :cons cons :op-id op-id
                    :dt dt' :w+ w+1 :w- w-1 :stamp stamp'})))

Tiny test (revision happens)

(let [k [:A :G 0]
      idx (upsert-imp {} {:ante :A :cons :G :op-id 0 :dt 100 :w+ 1 :w- 0 :stamp #{10}})
      idx (upsert-imp idx {:ante :A :cons :G :op-id 0 :dt 100 :w+ 1 :w- 0 :stamp #{11}})]
  (assert (= 2.0 (get-in idx [k :w+]))))

Pitfall: If your key includes dt or truth, you will never find the same implication again. Keep the key strictly [ante cons op-id].

⸻

Step 6 — Induction (mining) without killing evidence

Plain induction: from A@t0 and G@t1 make/update ⟨A ⇒ G⟩ with Δw+ = 1.0, dt = t1 - t0.
Procedural induction: If you observe A@t0, then an operation event op@t1, then G@t2, learn/update ⟨(&/, A, op) ⇒ G⟩ with Δw+ = 1.0, dt = t2 - t0.

Crucial rule: Do not project/decay counts by β^dt here. Store dt for use later. Evidence is evidence regardless of how long the gap was.

Implementation outline

(ns msc.infer
  (:require [msc.memory :as mem]
            [msc.stamp :as st]))

(defn induce-success
  "Assumes independence has been checked; adds Δw+ to the link."
  [index {:keys [ante cons op-id dt stamp]}]
  (mem/upsert-imp index {:ante ante :cons cons :op-id (or op-id 0)
                         :dt dt :w+ 1.0 :w- 0.0 :stamp stamp}))

(defn mine-plain [index pre post]
  ;; pre/post are events {:term ... :t ... :stamp ...}
  (let [stamp (st/union (:stamp pre) (:stamp post))]
    (induce-success index {:ante (:term pre) :cons (:term post)
                           :dt (- (:t post) (:t pre)) :stamp stamp})))

(defn mine-proc [index pre op post]
  ;; op is an event with :op-id > 0
  (let [stamp (reduce st/union [(:stamp pre) (:stamp op) (:stamp post)])]
    (induce-success index {:ante [:seq (:term pre) [:op (:op-id op)]]
                           :cons (:term post)
                           :op-id (:op-id op)
                           :dt (- (:t post) (:t pre))
                           :stamp stamp})))

Tiny test (your 100‑step case)

(let [eA {:term :A :t 0 :stamp #{1}}
      eG {:term :G :t 100 :stamp #{2}}
      idx (mine-plain {} eA eG)
      idx (mine-plain idx (assoc eA :stamp #{3}) (assoc eG :stamp #{4}))]
  (assert (= 2.0 (get-in idx [[:A :G 0] :w+])))
  (assert (= 100.0 (Math/round (get-in idx [[:A :G 0] :dt])))))


⸻

Step 7 — Assumption of failure that is small but cumulative

Why this matters: You need lightweight negative evidence when an anticipated post doesn’t show up, but it must be tiny so genuine successes can overcome it.

Rule (simple, effective):
	•	When a precondition spikes and you have a link ⟨(&/, pre, op) ⇒ post⟩ whose expectation is currently high enough to anticipate post soon, immediately revise that link with Δw- = ε (e.g., ε=0.005) and a fresh stamp.
	•	If post arrives later, the Δw+ = 1 success outweighs prior tiny negatives. If not, expectation decays.

Function

(defn assume-failure [index {:keys [ante cons op-id]} eps new-stamp]
  (let [k [ante cons op-id]
        old (get index k)]
    (if old
      (mem/upsert-imp index {:ante ante :cons cons :op-id op-id
                             :dt (:dt old) :w+ 0.0 :w- eps :stamp new-stamp})
      index)))

Tiny test

(let [k [:A :G 0]
      idx (mem/upsert-imp {} {:ante :A :cons :G :op-id 0 :dt 100 :w+ 2 :w- 0 :stamp #{5}})
      idx (assume-failure idx {:ante :A :cons :G :op-id 0} 0.01 #{6})]
  (assert (= 0.01 (get-in idx [k :w-]))))

Pitfall: Setting ε too large (like 0.2) will swamp successes. Keep it tiny.

⸻

Step 8 — Concepts and Tables (bounded, ranked by expectation)

Why this matters: MSC keeps only the top K implications per (consequent, op‑id) by expectation. This preserves constant‑time decision making.

Concept record

{:term :G
 :belief-spike nil
 :goal-spike   nil
 :by-op {0 (vector-of strongest implications for G)
         2 (vector-of strongest ⟨(&/ pre op2) ⇒ G⟩)}
 :usage {:use-count 0 :last-used 0}}

Maintain top‑K (e.g., K=20)

(defn insert-ranked [v imp K]
  (->> (conj v imp)
       (sort-by #(msc.truth/expectation (msc.truth/w->fc [(:w+ %) (:w- %)])) >)
       (take K)
       vec))

Pitfall: Forgetting to evict weakest entries → unbounded memory, slow decisions.

⸻

Step 9 — The engine map and a single step function

Why this matters: Everything becomes testable if the engine is a single immutable value and step is a pure transform that returns a new engine + effects + rng’.

Engine shape

{:time 0
 :params {:beta 0.8 :decision-th 0.501 :prop-iters 5 :eps 0.005
          :table-size 20 :fifo-cap 20 :motor-babble 0.2}
 :rng (java.util.SplittableRandom. 42)
 :fifo {:belief (msc.fifo/make 20) :goal (msc.fifo/make 20)}
 :concepts {}             ; term -> Concept
 :imp-index {}            ; [ante cons op-id] -> Implication
 :next-stamp-id 0
 :ops {1 {:term [:op 1] :name :left}
       2 {:term [:op 2] :name :right}}}

Inputs are a vector of new events to ingest this tick (sensor beliefs, goals, or op‑feedback events).
Effects are “please execute op‑id now” messages that the host applies outside.

Signature

(defn step
  "Processes exactly one MSC cycle over current inputs.
   Returns [engine' effects rng']."
  [engine inputs rng] ...)

What step does (in this order), mirroring MSC’s cycle:
	1.	Ingest new beliefs/goals: push into FIFOs, set concept spikes, update usage.
	2.	Assumption of failure: for each implication whose pre spiked now, add ε to its w-.
	3.	Mine implications from FIFO windows (plain + procedural) and upsert into imp-index, then into concept tables (:by-op) ranked by expectation (top‑K).
	4.	Propagate goals prop-iters rounds: back‑chain ⟨(&/, pre, op) ⇒ goal⟩ to subgoal pre! at time shifted by dt; merge goal spikes (revise or take stronger if stamps overlap).
	5.	Decide: for current goal spikes, check matching pre belief spikes; compute desire per op; take argmax; if ≥ threshold → effect {:op-id o :term [:op o] :at (:time engine)} and schedule op‑feedback event next tick (the host will append it to inputs as a belief).
	6.	Advance time by 1; clear transient spikes.

Important purity detail: step only returns effects; it does not call your operators. That keeps the engine pure and fully testable.

⸻

Step 10 — Using a link (when to apply projection)

Why this matters: Another common failure is to project/decay the counts at learning time. Don’t. Apply decay only when predicting across time.

Projection for use

(defn project-truth [{:keys [f c]} beta dt]
  {:f f :c (* c (Math/pow beta (Math/abs (double dt))))})

Desire calculation (simple, correct):
	•	Turn link counts into truth: tv = w->fc [w+ w-]
	•	(Optionally) project the premise belief or the predicted post to align times.
	•	Desire = expectation(tv) (you can factor in the projected premise truth multiplicatively if you like; the basic MSC/ONA decision uses the link’s own expectation).

Tiny test (projection doesn’t kill evidence):

(let [tv (msc.truth/w->fc [10 0.5])]
  (assert (> (:c (msc.truth/w->fc [10 0.5])) 0.9))
  (assert (< (:c (project-truth tv 0.8 100)) (:c tv)))) ; projection only on use


⸻

Step 11 — Deterministic RNG for motor‑babbling

Why this matters: Undebuggable randomness ruins reproducibility. Always thread RNG explicitly.

(defn rand01 [^java.util.SplittableRandom rng]
  (let [^long x (.nextLong rng)]
    [ (java.util.SplittableRandom. x)
      (/ (double (bit-and x 0xFFFFFFFFFFFF)) (double 0x1000000000000)) ]))

When no learned candidate exceeds threshold, compare p to :motor-babble and, if chosen, emit a random op effect.

⸻

Step 12 — Milestone tests (these catch the classic failures)

All of these should pass before you wire an environment.

12.1 Revision across 100‑tick gaps (the one that keeps failing)
	•	Setup: inject A. at t=0 and G. at t=100; repeat 10 times with fresh stamps each time.
	•	Expect: final implication ⟨A ⇒ G⟩ has w+ = 10, w- = ε·(anticipations), dt ≈ 100, c ≈ 10.5/11.5 ≈ 0.913 if you also added 100 anticipations with ε=0.005.
(Computation: w+ = 10, w- = 0.5 ⇒ w = 10.5, c = w/(1+w) = 10.5/11.5 ≈ 0.913, f = 10/10.5 ≈ 0.952, expectation = c*(f-0.5)+0.5 ≈ 0.913.)

Why this works: You added Δw+ for each success; you didn’t discount them by β^100. If your c doesn’t climb, you’re still decaying counts or failing to revise the same implication.

12.2 Independence works (revision not accidentally blocked)
	•	Reuse the exact same stamps for a second trial.
	•	Expect: revision does not occur (w+ unchanged).
	•	Use fresh stamps; Expect: revision does occur (w+ increments).
If both revise or both skip, your stamp handling is wrong.

12.3 Key stability (you actually find the hypothesis)
	•	Learn once and inspect your index keys.
	•	Add a second identical success with a different dt (e.g., 90 instead of 100).
	•	Expect: the same [ante cons op-id] entry is revised, and dt moves to the weighted average.
If a new entry appears, your key includes mutable fields (dt, truth, stamp) — fix it.

12.4 Assumption of failure is small but visible
	•	With a single learned implication (w+=1), apply 50 anticipations with ε=0.005 and no posts.
	•	Expect: w-=0.25, c decreases modestly, not catastrophically. A subsequent success (Δw+=1) should bring c back up distinctly.
If anticipation crushes the link, ε is too big.

12.5 Determinism
	•	Run the same input & RNG seed twice.
	•	Expect identical engine snapshots and effects. If not, you have hidden state.

⸻

Step 13 — Wiring decision and operator feedback (without side‑effects)

Rule: step returns effects; the host executes them; then the host appends an op‑feedback event (a belief with the op’s term) to the next tick’s inputs. This mirrors MSC’s self‑observation of actions and enables learning of ⟨(&/, pre, op) ⇒ post⟩.

Effect shape

{:op-id 2 :term [:op 2] :at (:time engine)}

Host loop (sketch)

(loop [eng (new-engine) rng (java.util.SplittableRandom. 7) inputs []]
  (let [[eng' effects rng'] (msc.engine/step eng inputs rng)
        op-events (for [{:keys [op-id term]} effects]
                    {:kind :belief :term term :op-id op-id
                     :truth {:f 1.0 :c 0.9} :t (inc (:time eng)) :stamp #{(rand-int 1e9)}})]
    (recur eng' rng' op-events)))

Pitfall: If you forget to insert op‑feedback events, you will never learn any procedural rule.

⸻

Step 14 — Goal propagation (subgoals)

Minimum viable behavior to match MSC/ONA:
	•	For a goal G! and link ⟨(&/, A, op) ⇒ G⟩ with stored dt, back‑chain to A! at time now - dt.
	•	Merge incoming goals at A by revise (if stamps independent) or choose stronger (if overlapping).
	•	After each propagation round, try decision again (maybe a strong A! plus a current A. belief makes op! cross threshold).

Keep prop-iters small (e.g., 5).

⸻

Step 15 — Parameters that won’t sabotage you
	•	:beta (projection decay on use): 0.8 is fine.
	•	:decision-th / :prop-th: ~0.501
	•	:eps (assumption of failure): 0.001–0.01
	•	:table-size: 20
	•	:fifo-cap: 20
	•	:motor-babble: 0.2 (reduce as learning stabilizes)

If behavior looks “forgetful,” check you didn’t apply :beta during induction.

⸻

Step 16 — Invariants you can assert in code
	•	Counts monotonicity: w+, w- never decrease.
	•	Key stability: imp-key is always [ante cons op-id].
	•	Table boundedness: Every concept’s :by-op vectors have ≤ table-size.
	•	FIFO boundedness: :len ≤ :cap.
	•	No NaNs: truth values are finite; 0 ≤ f ≤ 1, 0 ≤ c < 1.

These invariants catch 90% of the subtle bugs early.

⸻

Step 17 — A minimal Experiment‑1 reproducer

You can build a tiny driver that:
	1.	Emits repeated blocks that include the A … G relation with variable delays.
	2.	At the end of each block, records w+, w-, f, c, expectation, and dt for ⟨A ⇒ G⟩ into a CSV.
	3.	Plots confidence vs. trial. You should see a monotonic rise (with slight dips if you induce many anticipations between successes).

If that curve is flat, go back to Steps 2–7; one of the “don’ts” is still in your code.

⸻

Final “gotcha” checklist (pin this where you code)
	•	⬜ Induction adds counts. Never multiply counts by β^dt.
	•	⬜ Keys are [ante cons op-id] only. Never include dt, truth, or stamp.
	•	⬜ Fresh stamps for every event; implication stamp = union; revision requires independence from the old implication stamp.
	•	⬜ Index by consequent (+ op-id), keep top‑K by expectation.
	•	⬜ Assumption of failure uses a tiny ε (0.001–0.01).
	•	⬜ RNG is explicit & seeded; step is pure and only returns effects.
	•	⬜ Projection (β) is applied only when using a link (prediction), not when learning it.
	•	⬜ Operator feedback events are injected next tick; otherwise you never learn procedural rules.
	•	⬜ Write and run the 100‑step gap test before anything else. If it passes, the rest tends to fall into place.

⸻

If you want, share the specific pieces of your current Clojure code where induction and revision happen. I’ll plug them into this scaffold and mark the exact lines that need to change to make the 100‑step case (and Experiments 1–3) pass.
