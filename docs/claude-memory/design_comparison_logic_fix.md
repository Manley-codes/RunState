---
name: design-comparison-logic-fix
description: "✅ V1 BUILT July 9 2026 — candidate-based comparison (ComparisonService/ComparisonInsight) replaces the blended-average flaw in the AI prompt + fallback; detectRunStyle untouched"
metadata:
  type: project
---

**STATUS: V1 BUILT July 9, 2026 (paths 1 & 2 only; commit pending — Manley commits).**
Shipped as a new `ComparisonService` + `ComparisonInsight` pair: 180-day / cap-10 candidate
selection (route-first, distance-fallback), median aggregation, confidence tiers, and four
positive signals (State Lift as start-to-finish energy lift; Quiet Gain; Same-Cost/Better;
Demand Explained) behind a negative pre-filter. `RunAgent.buildUserMessage` + the fallback now
consume it; the rolling-average lines/flags are removed and the SYSTEM_PROMPT "Using history"
rule updated. **Path #3, `detectRunStyle()`, was intentionally NOT touched** — deferred to the
later identity redesign. The plan below is kept as the build record.

> UPDATE July 7, 2026: the effort measure this fix has been waiting for now has a design —
> see design_effort_cost.md ("How did that run land?"). Build them together or effort-first.
>
> HARVESTED July 7, 2026 (from archived ENERGY_STATE_DESIGN.md): **Run Type**
> (Easy / Steady / Speed / Long Run / Race — optional, selected pre-run from a secondary
> area, never asked after completion, never auto-inferred) is the third leg of fair
> comparison alongside effort data. Comparing a sprint day against a long-run day is the
> exact blended-average flaw this fix exists for. Consider capturing run type with or
> after the effort input.

## What's wrong

The current comparison passes a pre-labeled fact to the AI and uses it mechanically in fallback logic:

```
Pace this run vs average: Above average
Distance this run vs average: Above average
```

These labels are derived from a blended 20-run rolling average that mixes easy runs, long runs, tempo, and sprints. "Above average pace" often just means "today was a short fast day" — not "I got fitter." The LLM cannot know the difference because the distortion happens before the input arrives.

The system prompt instruction ("reference trends only when meaningful") controls whether the LLM *mentions* the comparison — it does not fix the comparison itself. The AI path is better-masked, not actually sound.

Additionally, "faster AND farther than average simultaneously" rewards a physiologically unusual combination. The two healthiest run types — easy long run (farther, slower) and hard short workout (faster, shorter) — can never trigger Run Style. The detector is blind to good training.

## Three code paths that need fixing (Phase 5.5)

**1. `buildUserMessage()` in RunAgent.java** — highest leverage
- Currently: sends `aboveAvgPace` / `aboveAvgDistance` boolean labels
- Fix: send effort-relative framing instead — "pace at similar effort vs. past similar runs"
- This is the root fix. Both AI path and fallback improve from a single change here.

**2. `buildFallbackResponse()` in RunAgent.java**
- Currently: mechanically appends "You ran farther and faster than usual" when flags are true
- Fix: remove the mechanical performance note, or reframe around effort efficiency
- Minor impact (fallback path only), but it's the most literal expression of the flaw

**3. `detectRunStyle()` in Runner.java**
- Currently: requires faster AND farther than rolling average + moderate/high energy
- Fix: rebase qualifying criteria — options include showing up consistently, LOW→HIGH lift habit, or effort efficiency (same pace, less effort over time)
- The consistency gate and earned/quiet character should stay — only what counts as qualifying changes

## The right long-term direction (Option A)

The gold-standard amateur improvement signal is: *same pace, less effort over time*.

Pre/post energy is essentially RPE. The app already captures both halves (pace + energy) but currently uses energy only for message tone. Redirecting it as a progress metric is:
- Correct (scientifically)
- Always positively framed (so "never mention below average" comes for free)
- Built from data already in the model

**Why:** Agreed June 2026 after research review. Not blocking UI phase — treat as Phase 5.5 polish pass.

---

## V1 build handoff (reviewed July 7, 2026 — Codex draft + Cowork review, parameters chosen by Manley)

**Depends on Effort Cost V1 being fully complete** (incl. the outstanding display + RunAgent
wiring + docs steps). Fixes AI prompt + fallback only; `detectRunStyle()` is explicitly NOT
touched (later identity redesign). Run Type stays parked. Industry validation: same-route
matching = Strava Matched Activities; effort-normalized comparison = Relative Effort;
session-RPE literature supports the effort axis.

**New class: comparison helper (value object + selector), isolated like WeatherService (SRP).**
Summarizes evidence for RunAgent; all selection/aggregation logic lives here, NOT in RunAgent.

**Candidate selection (per current run):**
- Previous runs only (exclude current). **Recency: last 180 days, capped at the 10 most
  recent matches** (Manley, July 7 2026).
- Prefer same route when present. Route matching normalizes first: trim + case-fold
  ("Cedar Trail " == "cedar trail"). Fuzzy matching out of scope V1. Rationale note:
  same-route implicitly controls terrain/hills — RunState has no elevation data.
- Fallback: similar distance within `max(0.5 mi, 20% of current distance)` — sanity-check
  this band against the real runs table during build; adjust if typical runs find <2 candidates.
- Same pre-run energy = stronger match when available. Never infer Run Type.
- Legacy NULL-effort rows participate in state/distance/route signals, not effort signals.

**Aggregation: MEDIAN of comparable runs** for pace/effort/state deltas (robust at small n).

**Confidence tiers:** 0 → no comparison insight · 1 → "last comparable run" language, never
"pattern" · 2–4 → early signal · 5–7 → recent pattern · 8+ → strong personal pattern.

**Productive signals, priority order (identity-aligned):**
1. State lift — similar start, better post-run energy / stronger pre→post change
2. Quiet Gain — similar route/distance/output, lower effort cost
3. Same cost, better output — similar effort + comparable distance, faster pace or stronger finish
4. Demand explained — high effort justified by distance, PR, heat, route (never "bad run")

**NEGATIVE-OUTCOME PRE-FILTER (critical rule):** the helper filters BEFORE the prompt.
Negative deltas (finished lower, higher effort, slower) are omitted or collapsed to
"no notable comparison" — never sent to the AI with a prompt rule as the only safety.
Same philosophy as the original fix: distortion is handled before input arrives.

**Data contract changes in `buildUserMessage()`:**
- REMOVE entirely (Manley, July 7 2026): rolling avg pace line, rolling avg distance line,
  and both above/below flags. The candidate evidence replaces them — keeping averages risks
  the old flaw re-entering via "faster than your average" phrasing.
- ADD (only when comparables exist and signal survives the pre-filter):
  `Comparable run basis: same route, similar distance, same pre-run energy` /
  `Comparable runs found: N` / `Confidence: <tier>` / positive outcome lines
  (state / effort / performance, median-based) / hedged context note when relevant
  ("warm weather may explain higher effort" — explanation, never causation).

**Fallback:** remove "You ran farther and faster than usual." One comparison note max, only
when confidence ≥ single-run AND the signal is positive/explanatory. Never below-average.
Never causation from weather/music/shoes/route.

**Response shape:** what happened → what it cost → what it revealed. Metrics stay visible;
comparison is the interpretation layer, not a replacement.

**Docs (part of done):** AI_AGENT.md data contract rewritten (removed + added lines above);
this file marked V1 BUILT with date; project_current_state.md status + queue pointer.

**Test plan:** each signal type returns correctly (Quiet Gain / State Lift / Same-Cost /
Demand-Explained); 0 comparables → no insight; 1 comparable → "last comparable run" wording;
negative deltas never appear in the prompt (pre-filter test); route case/whitespace variants
match; NULL-effort rows join non-effort signals only; runs older than 180 days excluded;
cap-10 respected; fallback works with ANTHROPIC_API_KEY unset; legacy rows load.

**Collab rules apply:** one file at a time, approval each step, explain the OOP (static
selector vs value object, streams/filtering if used, median on small lists), flag commits.
