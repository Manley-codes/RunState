# RunState Documentation

A guided path through this project's design record, written for someone reviewing the repository
rather than working in it.

The short version: RunState's documentation is more detailed than a console app strictly requires,
on purpose. Decisions are written down with their reasoning and an explicit status, so that a
choice made in June can be re-examined in August without anyone reconstructing why it was made.

---

## If you only read four files

**1. [CURRENT_RUN_FLOW.md](CURRENT_RUN_FLOW.md)** — *what the application actually does*

The verified as-is behavior of one complete run, with a flowchart covering failure paths. It also
carries a resolved-audit table: four gaps that were found in a self-audit, what each one really
risked, and how each was closed. Start here to understand the system.

**2. [AI_AGENT.md](AI_AGENT.md)** — *the most interesting design problem in the project*

How the post-run response is constructed: the agent's intended voice, the two-constant prompt
architecture, and the constraints — including why below-average performance is filtered out before
the prompt is built rather than being handled by instructing the model to be tactful.

**3. [design/adr_001_runstyle_surfacing.md](design/adr_001_runstyle_surfacing.md)** — *a decision record, including a rejected design*

An architecture decision record covering how RunStyle should surface to the runner. The
multi-card destination was rejected; RunStyle became a background engine instead. Worth reading
for the reasoning that produced a "no."

**4. [EVALUATION_RECORD.md](EVALUATION_RECORD.md)** — *how quality gets judged here*

A feature was built, evaluated against a rubric written before testing began, blind-graded by two
independent graders, and did not pass. That result is recorded rather than worked around.

---

## How decision status works

Documents in this project use explicit status words, because vague status is how a paused idea
gets mistaken for a dead one:

| Status | Meaning |
|---|---|
| **Current** | The working answer as of the date recorded. A reference, not an authority against a new idea. This is the default. |
| **Locked** | Settled, and the document names what would reopen it. |
| **Parked** | Deliberately set aside. The entry names what unlocks it. |
| **Out of current scope** | Not the right focus at this stage. Not a judgment on merit. |
| **Superseded** | Replaced by a named successor. Kept as history. |
| **Rejected** | Considered and declined, with reasons. |

Superseded documents keep a banner at the top pointing at whatever replaced them, and are kept
rather than deleted.

---

## Map of the documentation

### Built and verified

| Document | Covers |
|---|---|
| [CURRENT_RUN_FLOW.md](CURRENT_RUN_FLOW.md) | As-is console behavior, flowchart, resolved audit |
| [AI_AGENT.md](AI_AGENT.md) | Agent identity, prompt architecture, constraints |
| [DATA_PRIVACY.md](DATA_PRIVACY.md) | Exactly what leaves the machine, when, and what a failure does or doesn't guarantee |
| [design/design_runstyle_v1.md](design/design_runstyle_v1.md) | RunStyle V1 — local deterministic profile, never sent to the model |
| [design/design_comparison_logic_fix.md](design/design_comparison_logic_fix.md) | Candidate-based comparison with recency and confidence tiers |
| [design/design_weather_cleanup.md](design/design_weather_cleanup.md) | Weather as shipped |
| [design/design_run_response_system.md](design/design_run_response_system.md) | Post-run response and RunStyle logic |

### Design records and decisions

| Document | Covers |
|---|---|
| [design/adr_001_runstyle_surfacing.md](design/adr_001_runstyle_surfacing.md) | ADR — RunStyle as background engine; multi-card destination rejected |
| [design/creative_direction_ui.md](design/creative_direction_ui.md) | Visual direction for the mobile client |
| [design/design_start_active_run.md](design/design_start_active_run.md) | Start-to-active-run screen foundation |
| [design/design_state_scan.md](design/design_state_scan.md) | Pre-run energy capture |
| [design/design_shoe_selection.md](design/design_shoe_selection.md) | Shoe selection and mileage accrual |
| [design/design_effort_cost.md](design/design_effort_cost.md) | Effort cost capture and the Quiet Gains unlock |
| [design/requirements_nonfunctional.md](design/requirements_nonfunctional.md) | Eleven quality bars, each with a status |
| [design/idea_organization_analysis.md](design/idea_organization_analysis.md) | Every loose idea, collapsed into four systems and graded |

### Music Intelligence

| Document | Covers |
|---|---|
| [EVALUATION_RECORD.md](EVALUATION_RECORD.md) | **Start here** — what was built, how it was judged, why it did not pass |
| [design/design_music_intelligence_v1.md](design/design_music_intelligence_v1.md) | The canonical plan and contract |
| [design/music_intelligence_v1_evaluation.md](design/music_intelligence_v1_evaluation.md) | Fixtures, gates, full diagnostic history |
| [design/design_music_selection_system.md](design/design_music_selection_system.md) | The aim under the whole music layer, in six layers |
| [design/music_feature_register.md](design/music_feature_register.md) | Named music decisions, each with a status and execution path |
| [design/evidence/](design/evidence/) | Raw grading packets, probe outputs, smoke transcripts, screenshots |

### Scope and research

| Document | Covers |
|---|---|
| [design/user_goals.md](design/user_goals.md) | Core goals, phases, product positioning |
| [design/research_app_landscape.md](design/research_app_landscape.md) | Competitive research, RPE science, scope-creep watch |
| [design/parked_feature_ideas.md](design/parked_feature_ideas.md) | Parked ideas — each names what would unlock it |
| [design/run_initiation_register.md](design/run_initiation_register.md) | Run detection and initiation flows |

### History

`ROADMAP.md` is a June 2026 snapshot and `ui-design-brief-v1.md` is superseded — both carry
banners saying so. `Archived/` holds earlier branding, feature, and idea documents that have been
harvested into the current records. All are kept as history and are not live.

---

## A note on `design/MEMORY.md`

That file is a working index maintained during active development. It tracks open items and what
unlocks each one, and it is written for continuity between working sessions rather than for a
first-time reader. It is genuinely useful for understanding what is unresolved — but this file,
not that one, is the intended starting point.
