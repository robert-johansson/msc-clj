# 9 Nov 2025 at 11:01
# MSC — Complete Logic and Architecture Specification (Language-Agnostic, Functional Model)

*(This document describes the Minimal Sensorimotor Component (MSC) architecture as a complete, language-agnostic specification suitable for a purely functional reimplementation — particularly in Clojure.)*

⸻

## 0. Executive Summary

### Problem MSC Solves

MSC (Minimal Sensorimotor Component) is the core learning and control loop of an adaptive agent. Its goal is to **learn temporal and procedural relationships** between events in its environment and use those to make **goal-directed decisions under uncertainty**. It achieves this through:
	1.	**Learning:** Deriving temporal and procedural implications (e.g., A ⇒ G or (&/, A, op) ⇒ G).
	2.	**Reasoning:** Combining implications to propagate goals backward (subgoaling) and beliefs forward (prediction).
	3.	**Decision:** Selecting operations whose learned consequences best satisfy current goals.
	4.	**Self-monitoring:** Feeding executed operations back as beliefs to enable procedural learning.

### Core Loop Overview

Inputs (beliefs + goals)
       ↓
Event ingestion
       ↓
Assumption of failure (tiny negative evidence)
       ↓
Mining (induction): learn temporal/procedural links
       ↓
Goal propagation (backward chaining)
       ↓
Decision (forward chaining)
       ↓
Operator execution + feedback event
       ↓
Next cycle

### Scope and Limits
* MSC implements **short-term sensorimotor learning** and **procedural reasoning** (NAL-6/7/8 equivalent in NARS theory).
* It does **not** implement full propositional inference or long-term conceptual abstraction.
* All memory structures are **bounded** — fixed-size FIFOs, tables, and concept limits.

Deliverable: A **self-contained agent kernel** that operates in real time using bounded evidence and confidence-based reasoning.

⸻

## 1. System Model (Conceptual)

### 1.1 World and Time
* Time is discrete (tick = integer), representing the global cycle count.
* Each event has a timestamp t.
* Operators have **latency** — their feedback appears one cycle later by design.

### 1.2 Observables
* **Beliefs:** Observed facts about the world (e.g., A. means A is true now).
* **Goals:** Desired states (e.g., A! means the agent wants A to become true).

### 1.3 Operations
* An **operation** (op-id) is an abstract action symbol the system can execute.
* Each has a term [:op op-id] and optional external effect function.
* When executed, it produces an **operation feedback belief** (recording the fact that it was executed).

### 1.4 Terms
* **Atomic terms:** Keywords (:A, :ball-left).
* **Sequential terms:** [:seq :A :B] representing (&/, A, B).
* **Procedural antecedents:** [:seq pre [:op id]].
* Sequence depth is limited to 3.

### 1.5 Uncertainty

Truth values use **frequency** f and **confidence** c, or equivalently **positive/negative evidence** (w+, w−).

⸻

## 2. Data Structures (Abstract)

### 2.1 Event

**Field**	**Meaning**
term	The concept term (e.g., :A, [:op 1])
kind	:belief or :goal
t	Timestamp
op-id	Operator ID (0 for none)
truth	{ :f , :c } truth value
stamp	Set of unique IDs (evidence provenance)
debug	Optional name for logs

**Invariants:** 0 ≤ f ≤ 1, 0 ≤ c < 1, stamps unique per event.

### 2.2 FIFO Windows
* Two circular buffers: one for beliefs, one for goals.
* Capacity = fifo-cap.
* kth-newest(fifo, k) gives the k-th newest event.

### 2.3 Implication Link

**Field**	**Meaning**
ante	Antecedent term
cons	Consequent term
op-id	Operator ID (0 for none)
dt	Average time delay
w+, w-	Evidence counts
stamp	Union of contributing event stamps

**Identity key:** [ante, cons, op-id].

### 2.4 Concept

Each concept holds recent spikes and relevant implications.

**Field**	**Meaning**
belief-spike	Latest belief event
goal-spike	Latest goal event
by-op	{ op-id → [Implication ...] } ranked by expectation
usage	{ :use-count , :last-used } for attention ranking

### 2.5 Global Engine Snapshot

Represents the full agent state.

{
 :time 123
 :params {...}
 :rng rng
 :fifo {:belief fifo :goal fifo}
 :concepts {term → Concept}
 :implications {[ante cons op-id] → Implication}
 :next-stamp-id 4242
 :ops {id → {:term [:op id] :fn external-fn}}
}


⸻

## 3. Truth & Evidence Calculus

### 3.1 Definitions

Truth (f, c) is defined as:

f = w+ / (w+ + w−)
c = (w+ + w−) / (1 + w+ + w−)

Inverse conversion:

w = c / (1 − c)
w+ = f × w
w− = (1 − f) × w

### 3.2 Revision Rules

Revision = addition of evidence:

w+' = w+ + Δw+
w−' = w− + Δw−

Counts never decrease. The resulting (f, c) increases monotonically.

### 3.3 Temporal Projection

Used only for **using** links, not learning them:

project(f, c, β, dt): c' = c × β^|dt|

### 3.4 Expectation

E = c × (f − 0.5) + 0.5

Used for ranking implications and decisions.

### 3.5 Acceptance Tests

**Input**	**Expected**
w+=(1, w-=0)	f=1, c=0.5, E=0.75
After 10 successes	c≈0.91, E≈0.95
After 10 successes + 100 anticipations ε=0.005	w+=10, w-=0.5, f≈0.952, c≈0.913


⸻

## 4. Stamps & Evidence Independence

### 4.1 Stamp Semantics

Each event has a unique stamp ID. Implication stamp = union of the two (or three) event stamps that created it.

### 4.2 Independence Check

Revision allowed only if:

intersection(stamp_existing, stamp_new) = ∅

### 4.3 Failure Modes
* Reusing stamps between trials blocks revision.
* Overlapping stamps across events lead to skipped updates.

### 4.4 Acceptance Test

Two identical pairs with independent stamps → revise (counts add). Same stamps → skip.

⸻

## 5. Event Ingestion & Cycle Ordering

### 5.1 Cycle Contract

One cycle = atomic sequence:
	1.	Ingest inputs (beliefs + goals).
	2.	Apply assumption of failure.
	3.	Mine new implications.
	4.	Propagate goals.
	5.	Decide and output effects.
	6.	Cleanup + increment time.

### 5.2 Ingest Rules
* Timestamp all inputs with engine.time.
* Push into FIFOs.
* Update spikes and usage counts.

### 5.3 Cleanup
* Clear temporary spikes.
* Rebuild concept priorities (by usage).

⸻

## 6. Mining (Induction) Logic

### 6.1 Plain Temporal Induction

Given A@t₀, G@t₁:

Δw+ = 1.0
dt = t₁ − t₀

Upsert ⟨A ⇒ G⟩ under key [A, G, 0].

### 6.2 Procedural Induction

Given A@t₀, op@t₁, G@t₂:

Δw+ = 1.0
dt = t₂ − t₀

Upsert ⟨(&/, A, op) ⇒ G⟩ under [[:seq A [:op op-id]], G, op-id].

### 6.3 Windowing

Search back through FIFO within max length fifo-cap.

### 6.4 What Never Happens

❌ No projection/decay during induction.
❌ No dt or truth in identity key.
❌ No evidence subtraction.

### 6.5 Acceptance Tests

**Scenario**	**Expected**
10 A→G pairs 100 apart	w+=10, f≈0.95, c≈0.91
Mixed success/failure	w+, w− accumulate correctly


⸻

## 7. Assumption of Failure

### 7.1 Rationale

Negative evidence added preemptively when expected consequences don’t appear.

### 7.2 Trigger

When precondition spikes and link’s expectation ≥ 0.501:

Δw− = ε (0.001–0.01)

### 7.3 Acceptance Tests

After 100 unfulfilled predictions (ε=0.005): w−=0.5, confidence modestly decreases.

⸻

## 8. Concept Tables & Bounded Resources

### 8.1 Indexing by Consequent

Implications stored under **consequent** term for efficient goal-backchaining.

### 8.2 Ranking & Eviction
* Rank by expectation.
* Keep top table-size (default 20).
* Evict weakest on overflow.

### 8.3 Attention / Usage

usefulness = use-count / (time-since-last-use + 1)
normalized = usefulness / (1 + usefulness)

### 8.4 Acceptance Test

Add >20 implications → weakest 5 removed; ordering stable by E.

⸻

## 9. Goal Propagation (Back-chaining)

### 9.1 From G! to A!

For each link ⟨(&/, A, op) ⇒ G⟩:

A! (time = now − dt)

### 9.2 Spike Merge Rules
* Independent stamps → revise.
* Overlapping → take stronger truth.

### 9.3 Iteration Control

Repeat for prop-iters (default 5) or until no new goals.

### 9.4 Acceptance Tests

Verify that multi-step dependencies back-chain correctly; confidence stable.

⸻

## 10. Decision Making

### 10.1 Candidate Enumeration

For each goal G!, scan by-op tables for ⟨(&/, A, op) ⇒ G⟩.

### 10.2 Desire Computation

desire = expectation(link)

### 10.3 Threshold & Selection

If desire ≥ decision-th, choose op with highest desire.

### 10.4 Effects & Feedback

Emit effect { :op-id, :term [:op id], :at time } and inject op-belief next tick.

### 10.5 Acceptance Tests

Check that the correct op triggers when desire ≥ threshold.

⸻

## 11. Exploration (Motor Babbling)

### 11.1 Probability & RNG

If no candidate exceeds threshold:

with probability p=motor-babble (0.2) → pick random op.

### 11.2 Interaction with Learning

Executed ops create feedback beliefs that enable procedural learning.

### 11.3 Acceptance Tests

Run with fixed RNG seed → deterministic sequence.

⸻

## 12. Parameters & Tuning

**Param**	**Default**	**Description**
β	0.8	Temporal projection decay (prediction only)
decision-th	0.501	Decision trigger threshold
prop-th	0.501	Minimum strength for propagation
prop-iters	5	Max propagation depth
ε	0.005	Negative evidence per failed anticipation
table-size	20	Max implications per (goal, op)
fifo-cap	20	FIFO event window
motor-babble	0.2	Random exploration probability


⸻

## 13. Functional Architecture Mapping (Clojure)

### 13.1 Engine Value

A single immutable map representing all system state. Each step returns a new map.

### 13.2 Purity Boundaries
* Inputs in → pure functions → effects out.
* RNG and I/O are threaded explicitly.

### 13.3 Module Contracts

**Module**	**Responsibility**
truth	conversions, revision, projection
stamp	independence checks
fifo	ring buffer operations
memory	concept tables, implication upsert
infer	induction, assumption of failure
decide	goal propagation, decision
engine	main cycle orchestrator

### 13.4 Anti-transliteration Rules

❌ Never mutate.
❌ Never apply β decay during learning.
❌ Never store dt/truth in identity keys.
❌ Never skip op-feedback events.

### 13.5 Property-based Tests
* Counts monotonic.
* Keys = [ante cons op-id] only.
* No NaNs, bounded tables.

⸻

## 14. Worked Traces

### 14.1 Simple A→G (Δt=100)

**Trial**	**A time**	**G time**	**Δt**	**w+**	**w−**	**f**	**c**	**E**
1	0	100	100	1	0	1.0	0.5	0.75
2	200	300	100	2	0	1.0	0.67	0.83
5	…	…	100	5	0	1.0	0.83	0.92
10	…	…	100	10	0	1.0	0.91	0.95

Expectation rises steadily; confidence asymptotes toward 1.

### 14.2 Procedural Case

Trial: A@0, op1@1, G@5
→ learn ⟨(&/, A, op1) ⇒ G⟩
Repeat 10× → confidence ~0.91
Goal G! triggers decision op1!

### 14.3 With Assumption of Failure

100 anticipations → w−=0.5, confidence dips slightly; next success recovers.

### 14.4 Goal Propagation Example

Goal G! → via ⟨(&/, A, op) ⇒ G⟩ → back-chain to A! → decision op! → op-feedback → G. learned.

⸻

## 15. Reference Experiments

### 15.1 Experiment 1: Single Relation
* Generate repeated A→G pairs.
* Expect monotonic confidence growth.

### 15.2 Experiment 2: Sequence Length 2–3
* A B G patterns.
* Confirm MSC mines correct chain ⟨A ⇒ B⟩, ⟨B ⇒ G⟩.

### 15.3 Experiment 3: Distractors & Long Gaps
* Inject random distractor beliefs.
* Verify MSC maintains correct w+, w− for real dependencies.

⸻

## 16. Verification & Test Plan
* **Unit tests:** truth calculus, stamps, FIFO, upsert.
* **Property tests:** monotonic counts, independence.
* **Scenario tests:** worked traces 14.1–14.4.
* **Determinism:** identical outputs for same seed.
* **Regression:** golden traces CSVs.

⸻

## 17. Failure Modes & Debug Playbook

**Symptom**	**Cause**	**Remedy**
Confidence flat	Projection during induction	Remove



# MSC — Complete Logic and Architecture Specification (Language-Agnostic, Functional Model)

*(Continued: Sections 17–19)*

⸻

## 17. Failure Modes & Debug Playbook

### Overview

Even with correct logic, certain implementation choices can silently break MSC’s behavior. These failures always manifest as one of a few predictable symptoms: flat confidence curves, runaway forgetting, or never-firing decisions. Each issue maps to a small violation of the logical rules defined in previous sections.

### 17.1 Common Failure Modes

**Symptom**	**Likely Cause**	**Remedy**
Confidence flat or never increases	Projection applied during induction	Remove β-decay when learning; store dt unmodified
Multiple identical implications	Key includes dt or truth	Restrict key to [ante, cons, op-id] only
Revision never happens	Stamp overlap or reuse	Assign new stamps per event; ensure independence before revision
Confidence collapses after few anticipations	ε too large	Keep ε between 0.001–0.01
Never learns operations	Missing op feedback beliefs	Always inject op feedback event next cycle
Decisions fire randomly	RNG not seeded or threaded	Thread deterministic RNG and seed it once
Unbounded memory	No eviction policy	Enforce table-size and FIFO caps
Subgoaling loops endlessly	No propagation limit	Use prop-iters (default 5)

### 17.2 Debugging Strategy
	1.	**Log Evidence Counts** – print (w+, w−, f, c, E) for one implication each cycle.
	2.	**Visualize Confidence** – graph E over trials; should rise and plateau.
	3.	**Trace Stamps** – verify independence; overlapping stamps explain blocked revisions.
	4.	**Track Decisions** – log each executed op, its desire, and resulting feedback event.

### 17.3 Automatic Invariant Checks

Embed runtime assertions:
* assert(w+ ≥ 0 ∧ w− ≥ 0)
* assert(0 ≤ f ≤ 1 ∧ 0 ≤ c < 1)
* assert(len(by-op) ≤ table-size)
* assert(no duplicate [ante, cons, op-id])
* assert(all(stamps unique))

Violations of these invariants signal a broken induction or memory layer.

⸻

## 18. Glossary & Notation

**Term**	**Meaning**
**Antecedent (A)**	The cause or condition in an implication ⟨A ⇒ B⟩
**Consequent (G)**	The effect or goal predicted by an implication
**Belief event (A.)**	Observation that A holds at time t
**Goal event (A!)**	Desire that A become true
**Operation (op-id)**	Executable action symbol, e.g. [:op 1]
**Implication**	Learned temporal/procedural rule between terms
**Truth (f,c)**	Frequency & confidence describing reliability
**Evidence counts (w+, w−)**	Accumulated positive/negative support
**Expectation (E)**	Scalar ranking = c·(f−½)+½
**Projection**	Time-decay of confidence when predicting, never during learning
**Stamp**	Unique identifiers tracking evidence provenance
**FIFO**	Fixed-length event buffer for temporal mining
**Concept**	Memory node representing a term and its links
**Goal propagation**	Backward chaining of goals through implications
**Decision threshold**	Minimum expectation needed to execute an operation
**Assumption of failure**	Automatic addition of tiny negative evidence when expected posts do not appear
**Motor babbling**	Random exploratory execution when no confident operation exists
**Engine**	Immutable structure holding full MSC state per tick
**Cycle**	One atomic reasoning–acting iteration
**AIKR**	Adaptive Intelligence under Knowledge and Resource limits – MSC’s design constraint

Notation conventions:
* Temporal implication: ⟨A ⇒ B⟩
* Procedural implication: ⟨(&/, A, op) ⇒ B⟩
* Belief: A.
* Goal: A!
* Operation: op!
* Time offset Δt = t(B) − t(A)
* Projection: β^|Δt|

⸻

## 19. Change Log & Rationale Notes

### 19.1 Architectural Rationale
* **Bounded memory:** All MSC structures are capped to guarantee constant-time operation under AIKR constraints.
* **Evidence accumulation:** Confidence arises from monotonic addition of counts; no global normalization or decay ensures stability over long sequences.
* **Index by consequent:** Decision and subgoaling both require direct access from goal→rules, minimizing lookup time.
* **Pure dataflow design:** All logic can be expressed as transformations on immutable structures, aligning perfectly with functional programming models such as Clojure.
* **Explicit RNG threading:** Reproducibility is crucial; no hidden global state.
* **Assumption of failure:** Provides negative feedback without external timers; essential for online credit assignment.
* **Temporal projection only on use:** Maintains clear separation between learning (add evidence) and reasoning (discount over time).

### 19.2 Implementation Lessons
* Transliteration of C code leads to subtle coupling errors; re-express semantics instead.
* Use EDN serialization of the engine for easy replay and diffing.
* Visual diagnostics (plots of E vs trial) are the most reliable correctness check.

### 19.3 Historical Notes

MSC originated as a minimal NARS sensorimotor core and later evolved into OpenNARS-for-Applications (ONA). The architectural decisions captured here mirror those that allowed ONA to operate in real-time domains with bounded computational budgets.

### 19.4 Future Extensions
* Integrate long-term concept abstraction.
* Multi-step planning beyond current propagation depth.
* Adaptive parameter tuning based on environment stability.
* Integration with symbolic perception pipelines.

⸻

## Appendix A – Quick-Reference Tables

### A.1 Parameter Defaults

**Name**	**Default**	**Range**	**Purpose**
β	0.8	0–1	Temporal projection decay
decision-th	0.501	0–1	Action trigger threshold
prop-th	0.501	0–1	Subgoal activation threshold
prop-iters	5	≥1	Max goal propagation depth
ε	0.005	0–0.05	Negative evidence strength
table-size	20	≥1	Max implications per (op, goal)
fifo-cap	20	≥1	Temporal window length
motor-babble	0.2	0–1	Exploration probability

### A.2 Truth Equations Summary

f = w+ / (w+ + w−)
c = (w+ + w−) / (1 + w+ + w−)
E = c·(f − 0.5) + 0.5
w = c / (1 − c)
w+ = f·w
w− = (1 − f)·w
project: c' = c·β^|Δt|


⸻

## Appendix B – Acceptance Test Checklist
	1.	**100-tick confidence test** – expectation rises monotonically across repeated A→G pairs.
	2.	**Stamp independence** – revision occurs only for non-overlapping stamps.
	3.	**Procedural induction** – ⟨(&/, A, op) ⇒ G⟩ confidence mirrors plain induction after feedback events.
	4.	**Assumption of failure** – tiny ε negatives cause gradual decay without erasing strong links.
	5.	**Goal propagation** – back-chaining creates appropriate subgoals within prop-iters limit.
	6.	**Decision threshold** – operations execute only when E ≥ decision-th.
	7.	**Motor babbling** – random exploration occurs ~p fraction of time with fixed seed reproducibility.
	8.	**Determinism** – identical seeds and inputs produce identical traces.

⸻

## Appendix C – System Diagram (Textual)

           +----------------------+
           |   External Inputs    |
           | (beliefs, goals, ops)|
           +----------+-----------+
                      |
                      v
              +---------------+
              |  Event Ingest |
              +---------------+
                      |
                      v
            +--------------------+
            | Assumption Failure |
            +--------------------+
                      |
                      v
              +----------------+
              |   Mining/Induc |
              +----------------+
                      |
                      v
            +----------------------+
            | Goal Propagation     |
            +----------------------+
                      |
                      v
                +-------------+
                |  Decision   |
                +-------------+
                      |
          +-----------+-----------+
          |                       |
          v                       v
  +-----------------+     +-----------------+
  | Execute Op/Emit | --> | Feedback Belief |
  +-----------------+     +-----------------+
                      |
                      v
                +-------------+
                | Next Cycle  |
                +-------------+


⸻

### End of Specification

This document now represents a complete, language-agnostic logical description of the **Minimal Sensorimotor Component (MSC)** architecture, including all operational rules, mathematical foundations, and validation criteria necessary for an accurate, purely functional reimplementation.
