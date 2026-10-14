# RunState project memory

Index only. One line per file — detail lives in the file. Keep it that way.

---

## Open items — what's outstanding and what unlocks it

| Item | Unlocks when | Detail lives in |
| --- | --- | --- |
| **Progressive-input ladder is partially resolved** — shoe search/add/select/mileage now has a Start-screen home; route, surface and company remain open | Deferred; does not block Foundation Review | `design_shoe_selection.md` + `design_state_scan.md` |
| Log History refinement / QA pass — empty states, `REPLY | SPLITS` expanded-record mockup, `REPLAY` decision, dawn-vs-sun glyphs. Later polish, not a prerequisite for the Android fixture foundation | After the Android fixture journey is durable | `ui_phase_handoff.md` §4 |
| **Android journey / current priority October 13** — GPS/speech safeguards, redesigned Active and encrypted hosted setup are implemented. 43 backend tests, 284 JVM tests per variant, 88 full emulator screen/database checks, a final connection-dialog check, APK builds and a Moto encryption check passed; simulated GPS pause/History and direct Java 17 authenticated remote-fake/header-timeout checks passed. Latest debug APK is on the Moto; test packages/temp device files are removed, emulator closed and local servers stopped. Render is signed in with the $7 Starter form prepared, but no service/charge/secret was created; user commit/push/private configuration precede deployment. Docker/Render TLS/standalone delivery, screen-off GPS, accuracy, audible handoff/snippets and broad quality remain unverified | User Git/private setup, then hosted fake/live verification; preserve frozen replies, separate music consent and conservative GPS timing | `project_current_state.md` + `design_run_response_system.md` + `run_initiation_register.md` |
| **Music feature inventory and prioritization** — completed August 26; the music ideas resolve into an evidence-and-feedback foundation before selection, live support or expression surfaces | Defer further music work behind the Android fixture journey | `design_music_selection_system.md` + `music_feature_register.md` |
| RunStyle V2 strategy review — Manley's replacement direction | After rough screens exist | `idea_organization_analysis.md` |
| **Full run editing beyond feedback** — native History supports Energy/Effort updates; console History also supports deletion by ID. Distance, route, date, weather, music, and other field editing remains intentionally out of scope | Later product review | `ui_phase_handoff.md` |
| Does a RunStyle summary recompute from history, or read a stored insight log? | Before any summary surface is designed | `adr_001_runstyle_surfacing.md` |
| What counts as a PR — the term covers several achievements | Before the PRs filter ships | `ui_phase_handoff.md` |
| **Log History empty states** — two of them: a rich cold-start preview, and a minimal empty-filter state. Metrics panel has the same cold-start hole | Has to exist before the screen ships | `ui_phase_handoff.md` §4 |
| **Future Run Suggestions needs a ruling** — parked against the scope-watch resistance to proactive coaching nudges | Before any pre-run suggestion is designed | `parked_feature_ideas.md` |
| Settle / Hold / Build — NEEDS REVIEW, recommended shape is axis-internal with no prompt | Manley deciding | `music_feature_register.md` |
| **Structured song history** — Room v7 preserves opt-in media-session observations. Room v10 separately records optional cloud sharing of one eligible same-run Spotify title/artist; newest partial context is frozen for reflection and all other observations stay local. Occurrence reconstruction, coverage assessment, decisions and Taste/Run fit feedback remain | Validate access/revocation and coverage, then decide how partial evidence is displayed and used | `design_music_selection_system.md` + `music_feature_register.md` |
| **Music snippet with spoken reflection** — Manley wants a snippet paired with the voice reply when it fits. A manual prototype felt promising, but automated sourcing/timing and a permitted provider route remain open; observed Spotify metadata does not authorize playback, and no snippet is built | After voice reflection and music evidence are stable, choose a permitted source and test the handoff; do not block current observation work | `research_music_reflection_generation.md` |
| Music historical 36-output evaluation — never run; not a prerequisite for October 3 first-version implementation | Retain as historical evidence, not a reason to restart the creative loop; use bounded implementation checks | `music_intelligence_v1_evaluation.md` + `design_run_response_system.md` |
| **Backend gaps the log screen surfaced** — start time, location points and splits now have mobile contracts; BPM is omitted unless a real source is separately approved; rolling-pace display remains later | Android implementation, except the deferred display items | `ui_phase_handoff.md` §5 + `run_initiation_register.md` |
| Parked non-music ideas — Top Run Highlights, support messages, body feedback, context fields. Automatic splits is ACCEPTED/LATER, not parked | Each entry names its own unlock | `parked_feature_ideas.md` |

⚠️ **Nothing visual is locked.** Read the open-mind section at the top of `creative_direction_ui.md`
before advising on look. Direction is found by making, not specifying.

⚠️ **Roles this phase:** Manley plans with ChatGPT · Cowork reviews · Claude Design executes ·
Claude Code if needed · Manley decides.

---

## Current work

- **[UI phase handoff](ui_phase_handoff.md)** — ⭐ **START HERE.** State of the UI phase, the accepted
  Run Complete flow, Log decisions, current queue, and surfaced backend gaps.
- **[Start Run V7](design_start_run_v7.md)** — ⭐ what the Start screen actually does now: map
  foundation, RunStyle card, energy absorbed into the ring, map-becomes-timer collapse, hold to end.
  Written plainly. Read this, not the file below.
- [Start and Active Run](design_start_active_run.md) — HISTORY. The August 13 version. Kept for the
  reasoning that still holds; its visual description does not.
- **[Public design preview](design_preview_build.md)** — the hosted Start and Run Complete-to-Log
  History journey, how to update them, and what testing on a real phone surfaced.
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
- [Run initiation register](run_initiation_register.md) — mobile lifecycle/time, permanent UUID,
  Android/Room/foreground-service boundary, GPS/splits/completed-run contract, plus later detection
  experiments.
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
- [Effort cost](design_effort_cost.md) — console collector BUILT; mobile Run Complete now uses
  visible Energy to select the immediate prepared response and quieter optional `EFFORT +` as
  separately saved longitudinal evidence. Quiet Gains remains a later unlock.
- [Weather cleanup](design_weather_cleanup.md) — SHIPPED. Persistence fix, forecast API,
  WeatherService.
- [Post-run response system](design_run_response_system.md) — prototype-validated mobile
  response-family and delivery contract plus the shipped console response and RunStyle rules.

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

## Music/reflection research — resumed September 2026

- [Music-aware reflection generation research](research_music_reflection_generation.md) — October 3 creative baseline retained; October 13 first live Android text and durable reopen verified through a small five-generation check. General generation quality and music-aware delivery remain unproven.

## Reference and parked

- [Future feature decisions](parked_feature_ideas.md) — non-music parked ideas. Each entry names what
  unlocks it.
- [Weather context design](design_weather_context.md) — historical reference; as-built lives in
  `design_weather_cleanup.md`.
- Archive note: BRANDING, FEATURES, IDEAS, UNIQUE_IDEAS, ENERGY_STATE_DESIGN in `docs/` are
  ARCHIVED. Harvested here; do not treat as live.
