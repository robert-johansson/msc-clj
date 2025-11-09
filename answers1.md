Awesome—these are the exact knobs that make a port feel “identical” to the C harness. Here’s how to line them up, with concrete timelines you can copy.

⸻

1) Stimulus encoding in Exp1 (two stimuli per trial)

TL;DR: Inject them sequentially on consecutive cycles (not “same-tick”), then issue the goal.
	•	In the C harness, every MSC_AddInputBelief(...) advances one cycle. So “two stimuli” in a trial naturally land at t and t+1, not the same timestamp.
	•	Treat them as two distinct belief events, each with its own fresh stamp.
	•	Issue the goal after both are in (typically the next cycle).

Canonical per-trial schedule (simple version):

t:     A1-left.        ; belief
t+1:   A2-right.       ; belief
t+2:   G!              ; goal (or leave G! resident across trials if your setup does that)
t+3..t+K: (idle or other events in the gap, depending on your experiment design)

Why not same-tick?
MSC’s core loop is “one input → one cycle.” Simultaneous sampling would either (a) require batching semantics the C loop doesn’t use or (b) produce identical timestamps that complicate FIFO mining. Sequential is faithful and keeps mining deterministic.

⸻

2) Operator feedback after forced decisions

Short answer: Treat forced op feedback exactly like real feedback—same event shape, same stamp rules, no special flags.
	•	In the C harness, when the system fails to decide, the harness still calls MSC_AddInputBelief(op) to ensure an operation event is present. That event must:
	•	Be a regular belief with term [:op id] and op-id = id.
	•	Carry a fresh stamp (like any event).
	•	Participate in procedural induction just like real feedback.
	•	Do not tag it as “fallback” in the logic. Any special-case handling would break learning of ⟨(&/, A, op) ⇒ G⟩ when exploration/forcing is the only way to accumulate op evidence early on.

Timeline when forced:

t:     A.             ; precondition belief
t+1:   (no decision chosen)
t+1:   op.            ; forced op feedback injected by harness (still a belief)
t+2:   (maybe G. or not, depending on outcome)

That op. must be eligible to form (&/, A, op) for procedural anticipation and induction.

⸻

3) Negative feedback frequency & magnitude in Exp1

You have two choices depending on your goal:

A) Match the original harness behavior (for apples-to-apples reproduction)
	•	Keep injecting G. with {f=0, c=0.9} on incorrect trials, as the C harness does.
	•	Be aware this is very strong negative evidence:
	•	{f=0, c=0.9} ⇒ w = c/(1−c) = 9, so it adds ≈9 to w− in one shot.
	•	This can dominate early unless you also get successes soon. If your training distribution is sparse or noisy, confidence may tank temporarily—that’s expected in the original setup.

B) Stability-oriented port (recommended for general use)
	•	Rely primarily on assumption-of-failure (ε) and, if you still want explicit negatives, use a small-confidence negative, e.g. {f=0, c=0.01} (adds ~0.0101 to w−).
	•	This preserves the intended “tiny but cumulative” penalty style and avoids overwhelming the counts.

Pragmatic compromise (configurable):
	•	Add a parameter :explicit-negative-c in your port:
	•	0.9 to reproduce the reference curves.
	•	0.01 (or nil to disable) for robust learning while developing.
	•	Keep stamps independent for every injected negative so revision actually applies; don’t reuse stamp IDs.

⸻

Concrete acceptance checks (so you know you’re aligned)
	1.	Stimulus timing test
	•	Inject A1 at t, A2 at t+1, Goal at t+2.
	•	Verify both beliefs appear as distinct events in the FIFO with increasing timestamps and different stamps.
	2.	Forced op feedback test
	•	Create a trial where no decision is made, then inject op. as forced feedback at the same cycle end.
	•	Verify your mining sees the triple A@t, op@t+1, G@t+2 and creates/updates ⟨(&/, A, op) ⇒ G⟩ (i.e., w+ increments and dt ≈ 2).
	3.	Negative feedback magnitude switch
	•	Run a small block with “incorrect” trials using {f=0,c=0.9}, and another with {f=0,c=0.01} (or ε-only).
	•	Confirm the large-c run adds ~9 w− per mistake; the small-c run adds ~0.01 w−.
	•	Ensure assumption-of-failure still adds ε once per anticipation instance (not per tick).

⸻

Drop-in pseudo-schedule you can adopt

Training trial with decision; no long intra-trial gaps:

t:     A1. / A.        (first stimulus)
t+1:   A2.             (second stimulus, if Exp1 uses two per trial)
t+2:   G!              (optionally, set a resident goal once instead)
t+3:   (engine decides) → effects: op!
t+4:   op.             (feedback belief injected next cycle)
t+5:   G. or ¬G        (environment outcome)

Training trial with forced decision (no decision fired):

t:     A1.
t+1:   A2.
t+2:   G!
t+3:   (no op chosen)
t+3:   op.             (forced op feedback, treat like normal op belief)
t+4:   G. or ¬G

Incorrect trial explicit negative (reference mode):

t+4:   G. with {f=0,c=0.9}    ; strong negative update

Incorrect trial stable mode (port-friendly):

t+4:   G. with {f=0,c=0.01}   ; or skip explicit negative, rely on ε


⸻

If you want, paste a tiny snippet of your trial driver where you enqueue stimuli, decisions, and feedback. I’ll annotate exact lines to ensure the timestamps, stamps, and anticipation triggers match these rules 1:1.
