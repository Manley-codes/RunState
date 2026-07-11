---
name: design-run-response-system
description: Locked-in design rules for the post-run contextual response system
metadata:
  type: project
---

Every completed run gets a contextual response. No exceptions.

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
energy-vs-effort split in design_effort_cost.md). NOTE: the UI State Scan concept proposes
different pre-run labels and FOUR states — open question, see creative_direction_ui §0.

**Core principles:**
- Never imply a run was bad — the runner showed up, that counts
- PRs are still celebrated separately from Your Run Style
- The feeling question appears after the run summary (same log flow, not a separate screen)
- LOW → HIGH energy lift always adds a second line on top of the main message
- Never mention below-average performance — stay quiet if numbers are down

**Current implementation (Phase 4):**
- `RunAgent.buildRunResponse(run, avgPace, avgDistance)` is the public entry point
- Tries Anthropic API call first (claude-haiku-4-5-20251001)
- Falls back silently to `buildFallbackResponse()` on any failure
- Fallback logic lives in `RunAgent.java` — the table below describes it

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

**RunStyle (pattern feature — REBUILT as RunStyle V1, July 10, 2026):**
The original Phase 3 "Your Run Style" (faster-AND-farther vs a 20-run rolling average,
consistency gate, LOW→HIGH bonus) was REPLACED. That elimination funnel rewarded a
physiologically unusual combination and was blind to good easy/long and hard/short training.
RunStyle V1 (see [[design-runstyle-v1]]) is a living, local, deterministic strategy profile:
- Three primary families — State Lift, Efficiency Gain, Controlled Finish — each judged
  point-in-time; a run "supports" a family only when that family is measurable.
- Stages from the latest opportunities per family (EARLY 3/4, FORMING 4/5, ESTABLISHED 6/7);
  strongest staged family becomes primary (PR and Demand-Explained can color, never lead).
- Secondary context (surface/company/weather/music/shoes) only decorates as association
  facets or a frequency-based habit line — it can never create or strengthen a pattern.
- Announces only on a first advance or a newly qualified facet; never repeats or downgrades.
- Lives in `RunStyleService`; `Runner.detectRunStyle(Run)` delegates. Never sent to the AI.
