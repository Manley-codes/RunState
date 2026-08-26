---
name: design-run-response-system
description: Locked-in design rules for the post-run contextual response system
metadata:
  type: project
---

Every successfully saved run gets a contextual response. No exceptions.

## Mobile Run Complete delivery contract — prototype-validated August 22, 2026

**STATUS: CURRENT MOBILE DIRECTION, VALIDATED IN THE INTERACTIVE PROTOTYPE, NOT BUILT.** This
contract governs the future mobile flow. The shipped console agent still uses the implementation
described later in this file.

### Sequence

1. The run is durably saved locally before any success language or response preparation. `Run
   saved` means the phone can recover the run; it does not claim that server sync has finished.
2. Metrics appear immediately with a short audible factual receipt built from saved run facts. That
   receipt is deterministic confirmation, not the contextual reflection.
3. All three post-run Energy choices are visible and prominent while the receipt plays. Effort stays
   behind a quieter `EFFORT +` action that opens a dismissible lightweight sheet. Either, both or
   neither may be recorded.
4. While the runner sees the choices, RunState silently prepares a four-response Energy family from
   one shared factual and evidentiary foundation: `Spent`, `Feeling Good`, `Powered Up`, and a
   first-class no-selection response.
5. Choosing Energy immediately sends that choice into the visualizer. The selected post-run state
   lands beside the pre-run state, and its prepared reflection arrives directly out of the same
   choreography; there is no additional waiting period after the absorption completes.
6. If the runner chooses no Energy, a quiet window of approximately six seconds after the factual
   receipt selects the no-selection response. Opening the Effort sheet holds that path while the
   runner is interacting. No countdown or `Skip` button is shown.
7. Only the selected response is revealed, spoken and stored in Log History; the unused candidates
   disappear. A late Energy answer still saves for future learning but never replaces the response
   already committed to that run. No answer is required to receive a response.

### Durable selected-reflection record — DEFAULT

**Approved August 25, 2026. Future-mobile contract only; nothing is implemented.** Each completed
run may own exactly one selected reflection linked by the run's permanent UUID. Treat it as a
one-to-one child of the run; whether it eventually uses fields on the run or a separate local table
is an implementation decision.

The reflection has three states:

- **`PENDING`** — the run is safe, but its reflection is still being prepared or selected.
- **`READY`** — store the selected branch (`Spent`, `Feeling Good`, `Powered Up`, or no selection),
  the exact final text, and when it became final.
- **`FAILED`** — the run remains safe; only reflection preparation failed. Retry moves this same
  record back to `PENDING` and never saves or duplicates the run.

`PENDING → READY` is the normal path; `PENDING → FAILED → PENDING` is the retry path. Once `READY`,
the selected text is permanent for that historical run. Late Energy or Effort saves as future
evidence without replacing it. History reads the stored text exactly and never regenerates on open
or reload. Unused candidates are discarded. If the app closes while `PENDING`, it may continue the
same reflection work against the same saved run. Deleting the run removes its reflection through
the same local and synchronization lifecycle so no orphaned reply remains. Speech audio does not
need persistence; it may be produced later from the exact stored text.

### Preconstructed Energy response family — LOCKED DIRECTION

The four candidates share the same saved run facts, key moments and approved music/persona evidence,
but they are **not** restrained variations of one template. Energy may change the interpretation,
emphasis, structure, persona use, opening, middle or close. The differing material does not have to
sit at the end. The branches represent alternative conditional truths; they are not claims that are
meant to be simultaneously true.

The creative boundary is deliberately narrow:

- objective run and music facts remain consistent across all four candidates;
- each Energy candidate must honor its named state without turning that state into a grade of the
  run;
- no candidate may invent a run event, song event or other concrete evidence; and
- the no-selection candidate uses only known evidence and never calls attention to a skipped answer.

Within that boundary, the responses are allowed to diverge creatively. Evaluation should inspect
all four candidates side by side for factual consistency and judge each one for creative quality.

### Effort has a separate mobile job — LOCKED DIRECTION

`Smooth / Working / Heavy / Empty tank` remains optional and is still captured behind `EFFORT +`,
but it does **not** select, modify or regenerate the immediate post-run response. Effort is stored as
longitudinal evidence: it can support future comparable-run messages, Quiet Gains, runner learning,
RunStyle or Run Rhythm analysis, and a later response that accurately references how a past run
landed. Any cross-run or pattern claim still has to meet the applicable evidence threshold.

This is the product division: **Energy helps RunState speak to this run immediately. Effort helps
RunState understand the runner across runs.**

### Prototype-validated delivery states

- The deterministic factual receipt is spoken once after save succeeds. The fixture wording is:
  `Run saved. 4.2 miles in 38 minutes, 17 seconds.`
- `EFFORT +` opens a dismissible lightweight bottom sheet containing `Smooth / Working / Heavy /
  Empty Tank`. A choice returns to a compact state such as `EFFORT · HEAVY`. No group label is used.
- Effort stays available after the reflection arrives. Late Effort saves without changing,
  regenerating or replaying the reflection.
- Save failure keeps the metrics visible, with `RUN NOT SAVED` and `RETRY SAVE`; Energy, Effort and
  reflection remain locked until retry succeeds.
- Reflection failure preserves `RUN SAVED` and offers `REFLECTION UNAVAILABLE`, `RETRY REFLECTION`
  and `VIEW IN LOG HISTORY →`. Retrying reflection never saves or duplicates the run again.
- Run Complete and RunState Log History are separate views inside one permanent phone shell. History
  reads the same stored run record and exact reflection text. Back restores the completed screen
  without saving, rebuilding, replaying speech or resetting inputs.
- The reflection speaks once when it arrives. Entering History does not speak it again; `REPLAY` is
  deliberate.

### Processing consequence

Durable run saving and response preparation remain separate operations. The response family may
begin only after local save succeeds; AI or speech failure must never undo or block the saved run.
The implementation may prepare the family in one structured request or another measured form, but
the runner-facing behavior above is the contract. A visible `BUILDING REFLECTION` state is needed
only when the selected response is not ready when its reveal is due.

**Canonical energy labels (harvested July 7, 2026 from archived ENERGY_STATE_DESIGN.md):**

| Internal level | Pre-run label | Post-run label |
|---|---|---|
| Low | I'm Here | Spent |
| Moderate | Ready-ish | Feeling Good |
| High | Let's Go! | Powered Up |

One shared internal scale, different labels per moment; skipped answers stored null and
omitted from output. Labels are working wording, not final. Founding principle: **energy is
an observation, never a run-quality grade** — Spent after a marathon is an achievement, and
"energy decreased → bad run" must never appear (this principle later grew into the
energy-vs-effort split in design_effort_cost.md). NOTE (RESOLVED July 27, 2026): the shared
three-level energy domain above is **reaffirmed and closed**. The UI State Scan concept's
FOUR states are **superseded as a domain change**; later label and presentation refinement
remains UI work and does not alter these stored meanings. Decision record:
`design_music_intelligence_v1.md`.

**Core principles:**
- Never imply a run was bad — the runner showed up, that counts
- PRs are still celebrated separately from Your Run Style
- The feeling question appears after the run summary (same log flow, not a separate screen)
- LOW → HIGH energy lift always adds a second line on top of the main message
- Never mention below-average performance — stay quiet if numbers are down

**Current console implementation:**
- `RunAgent.buildRunResponse(Run)` is the public entry point (single Run argument — no avgPace
  or avgDistance params; those rolling-average inputs were removed with Comparison Repair V1)
- Tries Anthropic API first (claude-haiku-4-5-20251001); 5-second connect timeout on the shared
  HTTP client; 5-second per-request timeout on the Anthropic call
- Falls back silently to `buildFallbackResponse()` on any failure — the app never breaks because
  the API is down or the key is unset
- Candidate-based comparison lookup runs locally before the API call; `ComparisonService.analyze()`
  selects up to 10 comparable runs (same route within the distance band first, then
  similar-distance fallback, 180-day window), forms
  separate energy and effort pools, and returns only positive/explanatory signals — each with
  its own evidence count and confidence tier. Nothing from below-average runs reaches the prompt.
- Response is generated only after the run is durably saved; the `saveAndCompleteRun` boundary
  ensures the API is never called for a run that failed to persist.

**What the prompt includes per run:**
- Energy (pre and post)
- Effort cost
- Run context (surface, company, shoes)
- Music state and note
- Daily-mean weather (when available)
- PRs
- Per-signal comparison metadata (basis, signal lines each with evidence count and confidence)

**RunStyle is never sent to the AI.** It is computed locally in `RunStyleService` and stays on
the machine.

**Fallback response logic:**

| Performance | Post-run feeling | Response |
|---|---|---|
| PR | Spent | "You really pushed yourself — and it showed. [PR]. Feeling spent after that makes sense." |
| PR | Feeling Good | "Strong run. [PR] and you're still feeling good." |
| PR | Powered Up | "[PR] and you finished strong. That's a great day." |
| PR | Skipped | "[PR]. Strong effort." |
| No PR | Powered Up | "Strong all-around run. You finished feeling great." |
| No PR | Feeling Good | "Solid run. Good effort today." |
| No PR | Spent | "You gave everything today. Good job getting it done." |
| No PR | Skipped | "Good job getting a run in today. Every run counts." |

**LOW → HIGH always adds:**
"See what getting active can do. You started rough and finished feeling great."

**Effort fallback additions (after the main line):**
- LOW_COST: "That landed controlled."
- HIGH_COST: "That was heavier than the numbers alone show."
- MAX_COST (unless post-energy is already Spent): "That took a lot out of you, and getting it done matters."
- MODERATE_COST: no extra line.

**First positive/explanatory comparison note** from `ComparisonInsight` (candidate-based,
pre-filter already applied) is appended when available.

**RunStyle (pattern feature — REBUILT as RunStyle V1, July 10, 2026):**
The original Phase 3 "Your Run Style" (faster-AND-farther vs a 20-run rolling average,
consistency gate, LOW→HIGH bonus) was REPLACED. That elimination funnel rewarded a
physiologically unusual combination and was blind to good easy/long and hard/short training.
RunStyle V1 (see [design_runstyle_v1.md](design_runstyle_v1.md)) is a living, local,
deterministic strategy profile:
- Three primary families — State Lift, Efficiency Gain, Controlled Finish — each judged
  point-in-time; a run "supports" a family only when that family is measurable.
- Stages from the latest opportunities per family (EARLY 3/4, FORMING 4/5, ESTABLISHED 6/7);
  strongest staged family becomes primary (PR and Demand-Explained can color, never lead).
- Secondary context (surface/company/weather/music/shoes) only decorates as association
  facets or a frequency-based habit line — it can never create or strengthen a pattern.
- Announces only on a first advance or a newly qualified facet; never repeats or downgrades.
- Lives in `RunStyleService`; `Runner.detectRunStyle(Run)` delegates. Never sent to the AI.

**Backdated RunStyle policy (Task 3, July 25, 2026):**
A manually entered backdated run is saved and included in future RunStyle calculations, but it
never produces a RunStyle announcement at entry. `RunStyleService.analyze()` evaluates each run
only through its deterministic chronological position (the prefix of history up to and including
that run), so later runs cannot alter its stage, counts, facets, or habit. Only the run that is
the final entry in the sorted history is eligible to announce.
