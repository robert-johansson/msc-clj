Great questions—these two choices affect both correctness and how “MSC-like” your port feels.

⸻

1) Decision gating (who gets considered?)

Short answer: MSC does not pick the global highest-expectation rule. It gates decisions by the current goal and the currently true precondition.

What the decision layer actually does:
	1.	Goal-scoped search.
For a posted/active goal G!, it looks only in the concept of G (index-by-consequent) for implications of the form ⟨(&/, A, op) ⇒ G⟩ (and possibly plain ⟨A ⇒ G⟩ if you allow direct effects).
	2.	Antecedent must be true now.
A rule is a candidate only if the precondition A. currently has a belief spike (i.e., it fired in this cycle/window). In other words, the “contextualized operation” (&/, A, op) must be satisfied now.
	3.	Arg-max among eligible candidates.
Among those candidates, it computes desire (typically the link’s expectation) and picks the max, if it clears the decision threshold.

Implications:
	•	You do need some notion of “active terms” (belief spikes) for antecedents, but you don’t need a separate bespoke whitelist for decision gating. The gating naturally falls out of:
	•	“goal spike is G!” → search only G’s concept;
	•	“precondition spike is A.” → only (&/, A, op) rules where A spiked are eligible.
	•	The attention/priority queue affects which concepts get processed sooner, but it is not a substitute for the precondition-must-be-true check. Even a very strong rule is ignored if its A isn’t currently true.

Acceptance check:
With two learned rules for the same goal G—⟨(&/, A1, opL) ⇒ G⟩ (E=0.9) and ⟨(&/, A2, opR) ⇒ G⟩ (E=0.8)—if only A2. fired now, the decision must choose opR, not opL, even though opL’s rule has higher expectation.

⸻

2) Stimulus injection order in Experiment 1 (one or both stimuli?)

Short answer: In the reference harness, both stimuli for the trial are injected, sequentially, every trial. Your Clojure harness should reflect that if you want apples-to-apples behavior.

Why both:
	•	Exp 1’s trial structure is “A1-left / A2-right” or “A1-right / A2-left”. The pair is the sensory context; the operation is chosen to match that pairing.
	•	Injecting only the “target” stimulus makes life easier for the learner (fewer distractors), but it diverges from the reference dynamics and can inflate results by removing confounders the original system had to ignore/demote.

How to schedule:
	•	Each input advances one cycle, so do:

t:   A1-left.     (belief)
t+1: A2-right.    (belief)
t+2: G!           (or keep G! resident)

…then decision/forced op, then op-feedback at t+?+1, then outcome G. (or explicit negative if you’re matching the strong-negative mode).

Will the non-target stimulus pollute learning?
	•	Procedural links are fine: they require the op in the antecedent (&/, A, op) and check A. now, so only the A co-occurring with the executed op tends to form a strong rule.
	•	Plain A⇒G links may get induced for both stimuli, but:
	•	the “wrong” one accrues assumption-of-failure ε regularly (it anticipates G after A but often doesn’t get it),
	•	while the “right” one accrues successes. Over time, expectation of the wrong link stays low and is evicted if tables are bounded.

Acceptance check:
Run a block where both stimuli are always injected. After N trials:
	•	The procedural link ⟨(&/, A_target, op_target) ⇒ G⟩ has high E.
	•	The plain link ⟨A_distractor ⇒ G⟩ has much lower E (and may be evicted if top-K is enforced).

⸻

Practical guidance for your port
	•	Decision gating:
Keep your current “active term” bookkeeping (belief spikes). Have decision enumerate only rules under G whose A has a current spike. That matches MSC.
	•	Stimulus order (Exp 1):
Inject both stimuli, sequentially, every trial. If you want a “clean antecedent” mode for debugging, keep it behind a flag (but don’t call it the reference).
	•	Logs to confirm behavior:
	•	On each trial, log which A spiked at decision time and which rule fired.
	•	Track expectations for ⟨(&/, A1, opL) ⇒ G⟩ and ⟨(&/, A2, opR) ⇒ G⟩ separately; the “right” one should steadily dominate.
	•	Watch plain ⟨A_distractor ⇒ G⟩; it should remain low or be evicted.

If you want, paste your current decision routine’s candidate enumeration, and I’ll mark exactly where to enforce “goal-scoped + precondition spike” gating to ensure it’s strictly MSC-compliant.
