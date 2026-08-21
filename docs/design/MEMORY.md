# RunState project memory

Index only. One line per file — detail lives in the file. Keep it that way.

---

## Open items — what's outstanding and what unlocks it

| Item | Unlocks when | Detail lives in |
| --- | --- | --- |
| **Progressive-input ladder is partially resolved** — shoe search/add/select/mileage now has a Start-screen home; route, surface and company remain open | **NEXT UI PASS** | `design_shoe_selection.md` + `design_state_scan.md` |
| Run Complete screen — after durable save and the factual receipt, RunState prepares `Spent / Feeling Good / Powered Up / no selection` response candidates from one factual foundation; Energy selects the immediate response, while quieter `EFFORT +` stores longitudinal evidence only. Exact layout, motion and fallback timing still need the pass | After remaining State Scan planning | `design_run_response_system.md` + `design_effort_cost.md` + `ui_phase_handoff.md` |
| Log History refinement / QA pass — empty states, `REPLY | SPLITS` expanded-record mockup, `REPLAY` decision, dawn-vs-sun glyphs | After Run Complete; before Foundation Review | `ui_phase_handoff.md` §4 |
| **Core Running Foundation Review** — one question: is record → preserve → understand → manage → use-later ready for a real interface | After the rough screens and Log History refinement | `project_current_state.md` |
| **Music feature inventory and prioritization** — review the full music layer before choosing what to design or build next; RunStyle Sound is one candidate, not the predetermined next screen | After Foundation Review | `design_music_selection_system.md` + `music_feature_register.md` |
| RunStyle V2 strategy review — Manley's replacement direction | After rough screens exist | `idea_organization_analysis.md` |
| **Edit / delete a logged run** — the known hole. No UPDATE or DELETE exists; INSERT and SELECT do | Any time, doesn't queue | `ui_phase_handoff.md` |
| Does a RunStyle summary recompute from history, or read a stored insight log? | Before any summary surface is designed | `adr_001_runstyle_surfacing.md` |
| What counts as a PR — the term covers several achievements | Before the PRs filter ships | `ui_phase_handoff.md` |
| **Log History empty states** — two of them: a rich cold-start preview, and a minimal empty-filter state. Metrics panel has the same cold-start hole | Has to exist before the screen ships | `ui_phase_handoff.md` §4 |
| **Future Run Suggestions needs a ruling** — parked against the scope-watch resistance to proactive coaching nudges | Before any pre-run suggestion is designed | `parked_feature_ideas.md` |
| Settle / Hold / Build — NEEDS REVIEW, recommended shape is axis-internal with no prompt | Manley deciding | `music_feature_register.md` |
| Structured song history — the narrowest gap to a real music system | Any time | `music_feature_register.md` |
| Music final 36-output evaluation — never run, all gates stand | If music resumes | `music_intelligence_v1_evaluation.md` |
| **Backend gaps the log screen surfaced** — start time, BPM, splits, rolling-pace comparison | Mobile phase, except start time | `ui_phase_handoff.md` §5 |
| Parked non-music ideas — Top Run Highlights, support messages, body feedback, context fields. Automatic splits is ACCEPTED/LATER, not parked | Each entry names its own unlock | `parked_feature_ideas.md` |

⚠️ **Nothing visual is locked.** Read the open-mind section at the top of `creative_direction_ui.md`
before advising on look. Direction is found by making, not specifying.

⚠️ **Roles this phase:** Manley plans with ChatGPT · Cowork reviews · Claude Design executes ·
Claude Code if needed · Manley decides.

---

## Current work

- **[UI phase handoff](ui_phase_handoff.md)** — ⭐ **START HERE.** State of the UI phase, the accepted
  1.66-second Log quick peek, settled Log decisions, current queue, and surfaced backend gaps.
- **[Start Run V7](design_start_run_v7.md)** — ⭐ what the Start screen actually does now: map
  foundation, RunStyle card, energy absorbed into the ring, map-becomes-timer collapse, hold to end.
  Written plainly. Read this, not the file below.
- [Start and Active Run](design_start_active_run.md) — HISTORY. The August 13 version. Kept for the
  reasoning that still holds; its visual description does not.
- **[Public design preview](design_preview_build.md)** — the two hosted screens, how to update them,
  and what testing on a real phone surfaced.
- [Shoe selection and mileage](design_shoe_selection.md) — accepted `Add Shoes` prototype,
  Start-screen selection, automatic completed-run mileage contract, and pending recoverable Undo.
- [State Scan](design_state_scan.md) — pre-run energy is three optional LOW/MODERATE/HIGH tiles on
  the Start screen. No heading or outer selector panel; bypass stores `unknown`, never Moderate.
- [Creative direction / UI](creative_direction_ui.md) — primary visual vision plus the August 13
  Start/Active Run working proof; open-mind rules remain active.
- [Idea organization analysis](idea_organization_analysis.md) — every loose idea, collapsed into four
  systems and graded.

## Live registers

- **[Music selection system](design_music_selection_system.md)** — ⭐ the aim under the whole music
  layer, stated by Manley August 16 2026: song choice as accurate as possible for this runner. Six
  layers — evidence, judgment, rules, assembly, feedback, proof — with every contributing item placed.
  Read this before sequencing any music work.
- [Music feature register](music_feature_register.md) — named music decisions, each with a status and
  an execution path.
- [Run initiation register](run_initiation_register.md) — Prepare to Detect, provisional detection,
  at-home vs at-trail flows. All needs mobile.
- [ADR-001 — RunStyle surfacing](adr_001_runstyle_surfacing.md) — the multi-card destination is
  rejected; RunStyle is a background engine.

## Project state and scope

- [Current development state](project_current_state.md) — what's built, what's next, roadmap order.
- [User goals and scope](user_goals.md) — core goals, phases, locked product positioning.
- [Collaboration style](collab_style.md) — working preferences, pace, commit habits.
- Learning and collaboration prompt — approval boundary and pacing rules. Local-only
  (gitignored); intentionally not part of the published repository.
- [Non-functional requirements](requirements_nonfunctional.md) — 11 quality bars with status.
- [App landscape](research_app_landscape.md) — competitive research, RPE science, scope-creep watch.

## Built and shipped

- [Current console run flow](../CURRENT_RUN_FLOW.md) — verified as-is startup-to-insight flow.
- [Comparison logic](design_comparison_logic_fix.md) — BUILT. Candidate-based comparison with
  recency and confidence.
- [RunStyle V1](design_runstyle_v1.md) — BUILT + VERIFIED. Local deterministic profile, never sent
  to the AI.
- [Effort cost](design_effort_cost.md) — console collector BUILT; future mobile Run Complete uses
  visible Energy to select the immediate prepared response and quieter optional `EFFORT +` as
  longitudinal evidence. Quiet Gains remains the main unlock.
- [Weather cleanup](design_weather_cleanup.md) — SHIPPED. Persistence fix, forecast API,
  WeatherService.
- [Post-run response system](design_run_response_system.md) — canonical mobile response-family
  contract plus the shipped console response and RunStyle rules.

## Music Intelligence — stopped at a stable direction, August 3 2026

- [Music Intelligence V1 plan](design_music_intelligence_v1.md) — the canonical plan.
- [Evaluation record](music_intelligence_v1_evaluation.md) — fixtures, gates, and the full
  diagnostic history. Combined V1 is not complete.
- [Music reply style](design_music_reply_style.md) — craft reference. Assert vs observe; music as a
  supply of language; selection as a capability.
- [Creative ceiling probe](music_intelligence_creative_ceiling_probe.md) — diagnostic, failed its bar.
- [Stronger-model control](music_intelligence_stronger_model_control.md) — diagnostic, Opus alone
  did not meet the bar.
- [Music suggestion direction](parked_music_recommendation.md) — playlist agent as a later north
  star, plus API research.
- [Music ingredients](music_ingredients.md) — source material for the register. Not the place to
  look first.

## Reference and parked

- [Future feature decisions](parked_feature_ideas.md) — non-music parked ideas. Each entry names what
  unlocks it.
- [Weather context design](design_weather_context.md) — historical reference; as-built lives in
  `design_weather_cleanup.md`.
- Archive note: BRANDING, FEATURES, IDEAS, UNIQUE_IDEAS, ENERGY_STATE_DESIGN in `docs/` are
  ARCHIVED. Harvested here; do not treat as live.
