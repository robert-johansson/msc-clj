Short answer: you don’t need a “forced-correct priming” phase to make MSC learn in Exp 1 if your loop is wired like the C harness. The original behavior acquires evidence during the normal cycle via (a) exploration/motor-babbling and (b) injecting op. feedback whenever an op is executed (or when the harness forces an op because none was chosen) — but it is not a dedicated “always-correct teaching step.” If you alternate forced-correct trials, you can mask bugs.

Here’s how to line it up cleanly:

What the reference loop relies on
	1.	Goal-scoped decision: consider rules only for the current goal G! and only if the precondition A. is true now.
	2.	Exploration when unsure: if no rule clears threshold, pick an op with some probability (motor-babbling) or the harness “forces an op” so that an op. event exists and procedural mining can happen.
	3.	Feedback closes the loop: the executed (or forced) op produces op. as a belief next tick; if G. subsequently happens, you get a positive induction; if not, you get only ε from assumption-of-failure (unless you explicitly inject a negative).

Key point: the harness does not need to force the correct op to bootstrap. Random/exploratory ops plus consistent op-feedback are sufficient for the procedural link ⟨(&/, A, op) ⇒ G⟩ to emerge.

Why “forced-correct priming” can hide issues
	•	It leaks the label: repeatedly pairing A with the correct op guarantees early high w+, so you might never notice if:
	•	procedural induction isn’t actually mining A → op → G triples correctly,
	•	your assumption-of-failure doesn’t trigger at the right moment,
	•	your decision layer isn’t gating by the current A. spike.
	•	It distorts early dynamics: a few high-confidence hits can swamp later ε penalties and make curves look great even if timing/sequencing is off.

If you want apples-to-apples with the C harness

Use one of these two training modes, both faithful to “normal feedback loop”:

Mode A — Pure exploration (recommended)
	•	No special priming.
	•	When nothing clears threshold: with probability p (e.g., 0.2) pick a random op; inject op. feedback next tick.
	•	Keep explicit negatives off (or very low-confidence) and rely on ε.

Mode B — Harness forces an op (not necessarily correct)
	•	If no decision: synchronously “force” one op (can be random, or cycling through ops), and inject op. feedback the same way as a real action.
	•	Don’t force the correct op systematically; otherwise you’re back to priming.

Both modes let the system discover the correct mapping through normal evidence accrual.

What to do if you keep a priming phase anyway
	•	Make it short (e.g., 5–10 trials total) and then turn it off.
	•	Log two curves (with and without priming) — the non-primed run should still converge; if it doesn’t, something’s wrong in mining, anticipation timing, or decision gating.

Acceptance tests to ensure you’re not masking a bug
	1.	No-priming convergence test
	•	Run N trials with exploration only.
	•	Expectation for the correct ⟨(&/, A_target, op_target) ⇒ G⟩ should rise steadily (monotone in expectation, c increasing).
	•	If it doesn’t, inspect: (a) did you inject op. every time an op fired (real or forced)? (b) does procedural mining require t(A) < t(op) < t(G) and store dt = t(G) − t(A)? (c) does assumption-of-failure apply once per (&/, A, op) anticipation, not per tick?
	2.	Forced-op neutrality test
	•	In “forced” trials, confirm you’re not always forcing the correct op.
	•	Over many trials, forced ops should be roughly uniform unless you intentionally bias them; the correct link should still dominate by higher w+/lower ε.
	3.	Decision gating test
	•	With two strong rules for the same goal (A₁→opL, A₂→opR), if only A₂. fired this tick, ensure opR wins even if the A₁ rule has the higher historical expectation. If not, you’re missing the precondition-is-true-now gate.
	4.	Negative feedback sensitivity test
	•	If you mimic the C harness’s explicit “wrong” injection G. with {f=0,c=0.9}, verify it doesn’t permanently crush learning (remember it adds ~9 to w−).
	•	Try a small-c negative (c≈0.01) or ε-only run; you should see similar asymptotic behavior without the early slam.

Practical recipe for your Clojure harness
	•	Remove the alternating forced-correct pattern for your reference run.
	•	Keep motor-babbling (:motor-babble ~0.2) and inject op. feedback every time an op is executed or forced.
	•	If you must force, make it random or round-robin, not always correct.
	•	Ensure assumption-of-failure fires once per anticipation instance (A for plain; (&/, A, op) for procedural), not per idle tick.
	•	Keep explicit negatives either disabled or low-confidence during development; enable the strong‐negative mode only when you want to reproduce the exact legacy curves and you’re confident your induction timing is correct.

⸻

If you share a short snippet of your training loop (the part that alternates priming with normal cycles), I’ll mark exactly what to change so your runs rely purely on the normal feedback loop — just like the C harness — while staying robust and reproducible.
