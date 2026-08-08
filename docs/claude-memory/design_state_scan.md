---
name: design-state-scan
description: "State Scan working choices as of August 8 2026 — pre-run energy is a bypassable selector on the Start screen, not its own screen. STATUS: CURRENT, not locked; all of it predates any build. Records the choices, the evidence behind them, what they reverse, and the progressive-input ladder question left open."
metadata:
  type: project
---

# State Scan

**Naming note.** *State Scan* still names the concept — capturing the state the runner brings into a
run — but as of August 8 2026 it is **not a screen**. The roadmap and `ui_phase_handoff.md` §6 still
reference it by that name, so the name is kept for findability.

---

## Current choices — August 8 2026

> **Status: CURRENT, not locked.** These are the working answers as of August 8, made before anything
> was built. Every one of them is open to change, and the reasoning below is recorded so it can be
> re-examined — not to stop it being re-argued. Nothing here outranks a better idea.

**Pre-run energy is a compact selector sitting directly above `Start Run` on the Start screen.**
Not a separate screen, not a question.

- **Three options, LOW / MODERATE / HIGH.** This one *is* settled, and not by this document — the
  shared three-level energy domain was closed earlier and is recorded in `creative_direction_ui.md`
  and `design_music_intelligence_v1.md`. It's shared with post-run energy and the comparison logic,
  so changing it is a data-model change rather than a screen decision. Wording and presentation on
  this screen are open.
- **The label is `Start energy`.** A noun, not an instruction. *Choose start energy* was considered
  and dropped: an instruction is a question wearing a different jacket, and this label appears before
  every run — repetition wears an instruction down faster than a name. *Pre-run energy* was also
  dropped as too clinical for a runner-facing label; it stays the internal/data-model term, which is
  what `AI_AGENT.md` and `DATA_PRIVACY.md` already use. **`Start energy` matches the console's own
  wording** — `CURRENT_RUN_FLOW.md` says *starting energy* — and ties to the `Start Run` button
  directly below it.
- **The options carry their own meaning.** No question, no instruction. Manley: the answers should
  indicate their purpose through the wording itself.
- **Form: three equal-width blocks inside one longer container block.** Not pills. Two reasons —
  pills read as filters rather than a one-of-three state selection, and **Log History already uses a
  pill for filtering**, so the same visual form doing a different job across two screens would
  collide. Blocks also give better touch targets.
- **`Start Run` stays active with no selection made.** Energy is bypassable.
- **A bypassed run stores energy as `unknown`** rather than defaulting to Moderate. The reason is
  that a default would feed State Lift a value nobody entered. If a later idea makes inferring a
  value worthwhile — a population prior, say — that's a fair thing to revisit. The requirement isn't
  that inference is forbidden; it's that an inferred value stays **visibly inferred to the runner**
  and never overwrites what they did or didn't enter.
- **No *Not running today* control.** It duplicates ordinary navigation.
- **`Start Run` remains the visually dominant action.** The container is what makes this work: it
  turns three competing tap targets into **one control** sitting above `Start Run`, rather than four
  things asking for attention on the most time-sensitive screen in the app. Watch-item for the build:
  if the container reads as a full frosted panel it may re-acquire the weight the grouping was meant
  to remove — something to judge by looking rather than to settle here.

---

## Why — the evidence, not the preference

**The console already behaves this way.** `CURRENT_RUN_FLOW.md`: the startup prompt can capture
starting energy and hold it for the next logged run, and Log Run asks for it during capture if the
answer is still pending. The flow diagram labels it **starting energy (optional)**. This decision is
the UI expression of shipped behavior rather than a new compromise.

**The cost of a bypass is bounded and known.** `design_comparison_logic_fix.md`: pre-run energy is
**not** a candidate-selection filter in the shipped V1, and `energyPool` — candidates where both pre-
and post-run energy are recorded — **backs State Lift only.** A run with no starting energy loses
that one signal. The rest of the run record and every other comparison signal are unaffected.

**`unknown` over a default is a principle, not a detail.** It is the data-layer form of the rule the
music work spent a month learning: never claim what you don't know. Fabricated telemetry was the most
persistent failure across every music evaluation. A silent default to Moderate would manufacture
false State Lift readings from data nobody entered.

**Screens cost more here than elsewhere.** `idea_organization_analysis.md` §10: the pre-run screen
has seconds, not minutes — design it for someone about to put the phone away.

---

## What this reverses

`docs/ui-design-brief-v1.md` lists **Screen 2 — Pre-Run Energy** as a dedicated screen. That brief is
superseded on this point. The reversal is deliberate.

An intermediate position was also considered and dropped: State Scan always appearing, *requiring* a
LOW/MODERATE/HIGH selection to continue, with a fourth *Not running today* action. It was withdrawn
because a required pre-run prompt blocks the runner at the moment friction costs most, and the exit
control duplicated navigation.

---

## Explicitly open — the next decision

**The progressive-input ladder has no home.** It was State Scan's central design problem — what gets
asked versus prefilled — and `idea_organization_analysis.md` grades it the clearest value proposition
in any of the strategy documents (Problem 8, Distinct 8).

Collapsing State Scan to a single energy control does not answer where shoes, route, surface, and run
company live. **That is the next decision and it is deliberately left open — it must not be squeezed
onto the Start screen by default.**

Two further items that touch this screen, both recorded elsewhere and neither resolved here:

- **Environmental intelligence has no pre-run surface.** The engine only speaks after a run is saved.
  `idea_organization_analysis.md` §10 names pre-run as the obvious gap and a screen decision.
- **Correction has no home anywhere in the app.** The moment any inference is visible, the runner
  needs a way to say it is wrong.
