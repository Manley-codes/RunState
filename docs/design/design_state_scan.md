---
name: design-state-scan
description: "State Scan current choices as of August 13 2026 — optional LOW/MODERATE/HIGH energy tiles on the Start screen, visually tested, with the progressive-input ladder still open"
metadata:
  type: project
---

# State Scan

**Naming note.** *State Scan* still names the concept — capturing the state the runner brings into a
run — but as of August 8 2026 it is **not a screen**. The roadmap and `ui_phase_handoff.md` §6 still
reference it by that name, so the name is kept for findability.

---

## Current choices — August 13 2026

> **Status: CURRENT, not locked.** These choices have now been tested in an interactive Claude Design
> prototype and are stable enough to move on from this pass. They are not production behavior, and
> their visual execution remains adjustable. Reasoning is recorded so it can be re-examined, not to
> prevent revision.

**Pre-run energy is an optional three-tile control on the Start screen.** It is not a separate
screen and does not use a question or instruction label in the current version.

- **Three options, LOW / MODERATE / HIGH.** This one *is* settled, and not by this document — the
  shared three-level energy domain was closed earlier and is recorded in `creative_direction_ui.md`
  and `design_music_intelligence_v1.md`. It's shared with post-run energy and the comparison logic,
  so changing it is a data-model change rather than a screen decision. Wording and presentation on
  this screen are open.
- **No visible energy heading in the current version.** Earlier `Start energy` and `Choose Start
  Energy` proposals were removed through visual iteration. The tile labels carry the meaning.
- **Form: three adjacent, independent rounded-square tiles.** The earlier longer outer container was
  tested and abandoned. The current neutral material family is named **State Glass** in the tweak
  controls; palette variations remain open.
- **The central Start button stays active with no selection made.** Energy is bypassable.
- **A bypassed run stores energy as `unknown`** rather than defaulting to Moderate. The reason is
  that a default would feed State Lift a value nobody entered. If a later idea makes inferring a
  value worthwhile — a population prior, say — that's a fair thing to revisit. The requirement isn't
  that inference is forbidden; it's that an inferred value stays **visibly inferred to the runner**
  and never overwrites what they did or didn't enter.
- **No *Not running today* control.** It duplicates ordinary navigation.
- **Start remains the visually dominant action.** It is a larger black circular control beneath the
  tiles, flanked by smaller shoe and route/trail controls. Selecting energy does not start the run.
- **The detailed transition into active tracking is canonical elsewhere.** See
  `design_start_active_run.md` for countdown, metric hierarchy, visualizer, now-playing strip, and
  Pause → Stop / Play behavior.

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

**`unknown` is the current data choice when the runner bypasses energy.** The reason is to avoid
manufacturing a Moderate value and false State Lift evidence from data nobody entered. A future
inference approach may be reconsidered, but inferred and entered values must remain distinguishable.

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

The August 8 `Start energy` label and three blocks inside one longer container are also superseded by
the tested August 13 screen. The current version uses no energy heading, no parent selector panel,
and three adjacent independent tiles.

---

## Explicitly open — the next decision

**The progressive-input ladder now has a first working home.** It was State Scan's central design problem — what gets
asked versus prefilled — and `idea_organization_analysis.md` grades it the clearest value proposition
in any of the strategy documents (Problem 8, Distinct 8).

The shoe control now opens an accepted `Add Shoes` prototype with search, saved/add/select behavior
and per-shoe mileage; canonical detail lives in `design_shoe_selection.md`. This resolves deliberate
shoe entry, not the whole ladder. Real persistence and mileage updates remain unbuilt.

Route, surface, and run company still need decisions about where they are asked, suggested,
prefilled, corrected, and remembered. The route icon remains a visual entry point, not an answer,
and the remaining inputs are not automatically forced onto the Start screen.

Two further items that touch this screen, both recorded elsewhere and neither resolved here:

- **Environmental intelligence has no pre-run surface.** The engine only speaks after a run is saved.
  `idea_organization_analysis.md` §10 names pre-run as the obvious gap and a screen decision.
- **Correction has no home anywhere in the app.** The moment any inference is visible, the runner
  needs a way to say it is wrong.
