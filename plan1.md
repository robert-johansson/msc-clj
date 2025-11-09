Here’s a long, detailed orientation message for a developer starting the MSC → Clojure port project. It’s meant to give deep clarity — not just what to code, but how to think about the port so you don’t fall into the traps that made previous attempts fail.

⸻

🧠 Developer Guide: Porting MSC to Idiomatic, Purely Functional Clojure

⸻

1. Start with Mindset: You Are Re-expressing, Not Translating

MSC (Minimal Sensorimotor Component) is conceptually simple but procedurally subtle.
It was written in C — imperatively, mutably, and tightly optimized.
Your job is not to transliterate C to Clojure.
Your job is to re-express MSC’s logic in a clear, pure, immutable dataflow model.

Here’s how to frame your mental model:

C version	Clojure version
Global state structs in memory	A single immutable engine map
Side-effecting updates	Return new engine with updated values
rand() exploration	Thread RNG explicitly
Arrays and pointers	Persistent vectors, maps, sets
In-place revision	Functional combination (return new counts)
Incremental loops	reduce or recursive step function

Think of MSC as a state machine, and your Clojure version as a pure function:

(defn step [engine inputs rng]
  ;; returns [engine' effects rng']
)

Every line of logic in the C version can be mapped to a small pure transformation of this structure.

⸻

2. Grasp MSC’s Core Loop Before Anything Else

MSC runs on a tight, repeating cycle of learning and acting.

Here’s the conceptual pipeline:

Inputs (Beliefs + Goals)
        ↓
 [Ingest into Memory]
        ↓
 [Assumption of Failure]
        ↓
 [Mining (Induction): Build ⟨A ⇒ G⟩ or ⟨(&/, A, op) ⇒ G⟩]
        ↓
 [Goal Propagation (Back-chain)]
        ↓
 [Decision (Forward-chain)]
        ↓
 [Operator Execution + Feedback Event]
        ↓
 Next Cycle

Each cycle processes:
	1.	New sensory beliefs (“this happened”).
	2.	New or existing goals (“I want this”).
	3.	Inferred or revised implications (temporal and procedural).
	4.	Potential actions (decisions with confidence thresholds).
	5.	Feedback (operator events are fed back as beliefs).

The Clojure engine should explicitly model this dataflow in a single step.

⸻

3. Represent the Whole Engine as Immutable Data

Design your data model like this (EDN-style):

{
 :time 0
 :params {:beta 0.8 :decision-th 0.501 :prop-th 0.501 :eps 0.005
          :prop-iters 5 :table-size 20 :fifo-cap 20 :motor-babble 0.2}
 :rng rng-instance
 :fifo {:belief (fifo 20)
        :goal (fifo 20)}
 :concepts {}             ;; term -> concept record
 :implications {}         ;; [ante cons op-id] -> {:w+ …, :w- …}
 :next-stamp-id 0
 :ops {1 {:term [:op 1] :fn :left}
       2 {:term [:op 2] :fn :right}}
}

Why this works:
	•	You can test any part in isolation.
	•	You can serialize the whole engine as EDN.
	•	You can replay or diff snapshots between cycles.
	•	You never mutate anything — every function returns an updated copy.

⸻

4. Understand Truth Calculus First — It’s the Foundation

MSC’s “intelligence” is 100% determined by how truth values and evidence counts evolve.

Truth Representation

Two equivalent forms:

Symbolic form	Meaning
(f, c)	Frequency & confidence
(w+, w-)	Positive & negative evidence counts

Conversions:

w = c / (1 − c)
w+ = f × w
w− = (1 − f) × w
f = w+ / (w+ + w−)
c = (w+ + w−) / (1 + w+ + w−)

Golden Rule:

Only add evidence; never decay or rescale evidence during learning.

Revision = adding evidence:

w+′ = w+ + Δw+
w−′ = w− + Δw−

Expectation (used to rank links):

E = c × (f − 0.5) + 0.5

Confidence c is bounded, monotonic, and grows with total evidence.

If you observe the same relation 100 times, confidence approaches 1.0 —
but gradually, never instantly.

⸻

5. Stamps and Independence

Stamps prevent merging dependent evidence.
	•	Every event gets a unique integer stamp ID.
	•	An implication stores the union of its contributing stamps.
	•	A revision happens only if the new stamps are independent:

independent? = intersection(stamp_existing, stamp_new) = ∅



If you reuse stamps incorrectly (for example, using the same IDs for every trial),
MSC’s logic will refuse to revise — confidence will stay frozen.

💡 Test this early. Add an assertion that stamps never repeat within a run.

⸻

6. How MSC Learns (Mining Logic)

Temporal induction (plain)

If A@t₀ and G@t₁ → learn ⟨A ⇒ G⟩ with:

Δw+ = 1.0
dt = t₁ − t₀

Procedural induction

If A@t₀, then op@t₁, then G@t₂ → learn ⟨(&/, A, op) ⇒ G⟩ with:

Δw+ = 1.0
dt = t₂ − t₀

Never decay the evidence by Δt — store dt for later use.

Always revise, not replace, the implication:
	•	Find same [ante, cons, op-id].
	•	Add Δw+.
	•	Update dt as weighted average by total evidence.

Why this is the hardest part

Most LLM-written ports mess up by:
	1.	Projecting truth (β^dt) during induction (wrong — it kills confidence).
	2.	Keying the implication by [ante, cons, op-id, dt] (wrong — prevents revision).

Fix both and you’ll see confidence climb across trials.

⸻

7. Assumption of Failure

MSC doesn’t wait for “time-outs” — it assumes that if an anticipated consequence hasn’t appeared yet, it probably won’t.

When a precondition spikes (e.g., A.), for each learned link ⟨(&/, A, op) ⇒ G⟩:

If expectation(link) ≥ threshold (0.501) → add a tiny negative evidence:

Δw− = ε = 0.005

This gently lowers confidence over time if G never occurs, but it’s weak enough that later successes dominate.

⸻

8. Concept Memory: Index by Consequent

Every Concept is indexed by the term it represents (usually a consequent/postcondition).

Each concept holds:
	•	A few most recent spikes (belief & goal).
	•	by-op tables of implications that predict this concept (⟨(&/, pre, op) ⇒ this⟩).
	•	A usage score (frequency × recency).

Tables are ranked by expectation, truncated to top-K (default 20).
When full, weakest entries are dropped — guaranteeing bounded memory.

⸻

9. Goal Propagation

When a goal G! enters the system:
	•	Look at each link ⟨(&/, A, op) ⇒ G⟩.
	•	Back-chain to subgoal A! (occurrence time adjusted by stored dt).
	•	Merge with any existing A! via revision or by taking stronger if overlapping.
	•	Repeat for up to prop-iters rounds (default 5).

This creates a cascade of subgoals that represent how to get to G given known procedural rules.

⸻

10. Decision Logic (Forward-chaining)

At each cycle:
	1.	For each goal G!, look at its concept.
	2.	For each op-ID:
	•	Check if ⟨(&/, A, op) ⇒ G⟩ exists.
	•	If current A. belief is present, compute desire:

desire = expectation(link)


	3.	Choose operation with highest desire ≥ decision-th.
	4.	Emit effect {op-id n, term [:op n], at time}.

If no link crosses threshold, exploration (motor babbling) may trigger instead.

The operation’s feedback event is injected next tick as a belief:

(:term [:op n], :kind :belief, :op-id n)

That’s how MSC learns procedural implications automatically.

⸻

11. Exploration (Motor Babbling)

A fallback for when no learned op is confident enough.

Probability: p = motor-babble (e.g., 0.2).

When triggered, pick a random operation, emit it as an effect, and rely on feedback to start building procedural memory.

Thread the RNG explicitly for reproducibility:

[ rng' p ] = next-rand rng
(if (< p 0.2) (emit-random-op) nil)


⸻

12. The Cycle in Order (MSC’s Heartbeat)
	1.	Ingest new events
	•	Add new beliefs/goals to FIFOs.
	•	Update concept spikes and usage.
	2.	Assume failure
	•	Add tiny Δw− for anticipated links.
	3.	Mine
	•	Induce ⟨A ⇒ G⟩ and ⟨(&/, A, op) ⇒ G⟩.
	4.	Propagate goals
	•	Back-chain via implications.
	5.	Decide
	•	Forward-chain to action; emit effects.
	6.	Cleanup
	•	Clear spikes; increment time; rebuild attention priorities.

Each step returns a new engine (updated memory) and a list of effects.

⸻

13. Parameters You Must Get Right

Param	Default	Purpose
beta	0.8	Decay rate for temporal projection (use only when predicting)
decision-th	0.501	Minimum desire to act
prop-iters	5	Number of back-chaining rounds
eps	0.005	Tiny negative evidence for assumption of failure
table-size	20	Max implications per (goal, op)
fifo-cap	20	Event window size
motor-babble	0.2	Random exploration chance

Tune only if necessary; these defaults are empirically stable.

⸻

14. Most Common Failure Modes

Symptom	Cause	Fix
Confidence never increases	Projection applied during induction	Remove β decay in learning
Each trial creates a new link	Implication key includes dt or truth	Key only by [ante, cons, op-id]
No revision ever happens	Stamps overlap every time	Generate fresh IDs per event
Link confidence drops sharply	ε too high	Reduce to 0.001–0.01
Never learns operations	Missing op feedback events	Inject op belief next tick

Put these in bold in your README so future devs never waste days rediscovering them.

⸻

15. Testing: The 100-Tick Confidence Test

Run this before touching environments.
	1.	Generate events:

A@t=0, G@t=100
A@t=200, G@t=300
... 10 times ...


	2.	Each A→G pair independent stamps.
	3.	Expected result:

Metric	Expected
w+	10
w−	~0.5 (if 100 assumptions, ε=0.005)
f	~0.952
c	~0.913
E	~0.913

If you don’t get a monotonically increasing expectation curve, stop — you broke the induction logic.

⸻

16. Development Plan (Recommended Order)
	1.	Implement and test truth math (f,c) ↔ (w+,w−) conversions.
	2.	Implement stamps and independence tests.
	3.	Implement FIFO and event structure.
	4.	Implement implication upsert and revision logic.
	5.	Implement induction (plain + procedural).
	6.	Implement assumption of failure.
	7.	Implement goal propagation.
	8.	Implement decision logic and effects output.
	9.	Add motor babbling and RNG threading.
	10.	Integrate into full step.

After each stage, run micro-tests and the 100-tick scenario.

⸻

17. Debugging and Logging

Print (or log to file) expectation, f, c, w+, w−, dt for one tracked implication each cycle.

When things go wrong, the problem almost always shows in these metrics:
	•	If c stays low → evidence not added.
	•	If f oscillates around 0.5 → successes and failures mix.
	•	If dt jumps → you’re re-creating links instead of revising.

Visualize confidence curves to verify learning stability.

⸻

18. Architecture in Clojure (Recap)

Each subsystem is a namespace of pure functions:

Namespace	Responsibility
msc.truth	Truth math, revision, expectation
msc.stamp	Stamps, independence
msc.fifo	Event buffers
msc.memory	Implication storage, concept tables
msc.infer	Induction and assumption of failure
msc.decide	Goal propagation and decision
msc.engine	Step orchestration
msc.test	Acceptance & regression tests

All pure except msc.engine.step, which emits side effects.

⸻

19. Reproducibility
	•	Always seed RNG: (java.util.SplittableRandom. 42).
	•	Determinism is testable: same input + seed → same results.
	•	Dump engine snapshots to EDN to replay learning runs.

⸻

20. Endgame: Validation with MSC Experiments 1–3

Once your port passes the 100-tick confidence test, you can re-implement the three experiments:
	•	Experiment 1: Single relation A→G with variable delay.
	•	Experiment 2: Sequences of length 2–3 (A B G).
	•	Experiment 3: Distractors and long gaps.

Reproduce CSVs and plot curves (confidence vs trial).
If your curves match the original docs, your Clojure port is faithful.

⸻

✅ Final Advice
	1.	Embrace functional purity. Make step return data, not side effects.
	2.	Unit test every math function — treat the truth calculus as sacred.
	3.	Add invariants:
	•	Counts never decrease.
	•	All w+, w− ≥ 0.
	•	No duplicate keys.
	•	FIFO bounded.
	4.	Visualize learning. Seeing the confidence curve rise confirms everything works.
	5.	Document every transformation. Future contributors must understand the why, not just the what.

When done, you’ll have a Clojure engine that is mathematically identical to MSC’s behavior — but clean, pure, deterministic, and testable.

⸻

Would you like me to follow this with the full formal MSC Logic Specification (the 20-section document we planned) written out as a complete text next? That would be the definitive companion to this developer’s guide.
