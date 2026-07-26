---
name: design-comparison-logic-fix
description: "✅ V1 BUILT July 9 2026 — candidate-based comparison (ComparisonService/ComparisonInsight) replaces the blended-average flaw in the AI prompt + fallback; detectRunStyle untouched"
metadata:
  type: project
---

**STATUS: V1 BUILT AND SHIPPED.**
- Paths 1 and 2 — shipped July 9, 2026 as a new `ComparisonService` + `ComparisonInsight` pair:
  180-day / cap-10 candidate selection (route-first, distance-fallback), median aggregation, and
  four positive signals (State Lift, Quiet Gain, Same-Cost/Better, Demand Explained) behind a
  negative pre-filter. `RunAgent.buildUserMessage` + the fallback now consume it; rolling-average
  lines/flags removed; SYSTEM_PROMPT "Using history" rule updated.
- Path 3 (`detectRunStyle`) — resolved by RunStyle V1, July 10, 2026 (see `design_runstyle_v1.md`).
  The faster-AND-farther rolling-average path was replaced with a three-family stage/facet/habit
  profile; `Runner.detectRunStyle(Run)` now delegates to `RunStyleService`.
- Task 2 (Pre-Music Integrity Sprint, July 25, 2026) extended the comparison contract with
  signal-specific evidence and confidence — see the as-built section below.

The plan below is kept as the historical build record.

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

## Historical: three code paths that were fixed (Phase 5.5)

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

**Note (July 25, 2026):** energy and effort are now separate axes. Energy is how the runner
*finished* (pre/post energy levels). Effort is what the run *cost* (`EffortLevel` — Smooth →
Empty tank). They are not the same as RPE, though effort is RPE-adjacent. The original note
that "pre/post energy is essentially RPE" was accurate at the time this was written; it is no
longer correct now that effort cost is a distinct field.

Redirecting the effort axis as a progress metric is:
- Correct (scientifically)
- Always positively framed (so "never mention below average" comes for free)
- Built from data already in the model

**Why:** Agreed June 2026 after research review. Not blocking UI phase — treat as Phase 5.5 polish pass.

---

## V1 build record (reviewed July 7, 2026 — Codex draft + Cowork review, parameters chosen by Manley; shipped July 9, 2026)

**Effort Cost V1 was a prerequisite** (display + RunAgent wiring + docs) — completed before
this build. Fixed AI prompt + fallback only; `detectRunStyle()` was explicitly NOT touched
(later identity redesign, completed July 10 as RunStyle V1). Run Type stays parked. Industry
validation: same-route matching = Strava Matched Activities; effort-normalized comparison =
Relative Effort; session-RPE literature supports the effort axis.

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
  `Comparable run basis: same route | similar distance` /
  `Positive comparison signals:` / one or more `- [signal line] [evidence-bearing comparable
  runs: N; confidence: tier]` lines / optional hedged context note when relevant
  ("warm weather may explain higher effort" — explanation, never causation).
  NOTE: confidence and evidence count are per-signal (see Task 2 as-built below).
  The old global `Comparable runs found: N` / `Confidence: <tier>` lines were replaced.

**Fallback:** remove "You ran farther and faster than usual." One comparison note max, only
when confidence ≥ single-run AND the signal is positive/explanatory. Never below-average.
Never causation from weather/music/shoes/route.

**Response shape:** what happened → what it cost → what it revealed. Metrics stay visible;
comparison is the interpretation layer, not a replacement.

**Docs (part of done):** AI_AGENT.md data contract rewritten (removed + added lines above);
this file marked V1 BUILT with date; project_current_state.md status + queue pointer.

**Test plan (planned at design time):** each signal type returns correctly (Quiet Gain /
State Lift / Same-Cost / Demand-Explained); 0 comparables → no insight; 1 comparable →
"last comparable run" wording; negative deltas never appear in the prompt (pre-filter test);
route case/whitespace variants match; NULL-effort rows join non-effort signals only; runs
older than 180 days excluded; cap-10 respected; fallback works with ANTHROPIC_API_KEY unset;
legacy rows load.

**Verified tests (July 25, 2026, 17 tests in `ComparisonServiceTest`):** candidate selection
(recency window, cap-10, route-first, distance fallback), median aggregation (odd/even, outlier
resistance), negative pre-filter (lower lift and unexplained higher effort filtered; explained
PR effort allowed), separate energy and effort pools, pool-isolation (each signal's count and
median from its own pool), confidence tier boundaries at 1, 2, 5, and 8 runs.

---

## Task 2 as-built: signal-specific evidence and confidence (July 25, 2026)

The V1 handoff used a single global evidence count and confidence tier for the whole comparison
block. Task 2 replaced this with per-signal pools, counts, and tiers — so the runner
and the AI both see which signals have strong backing and which are tentative.

**Evidence pools (separate per signal family):**
- `energyPool` — candidates where both pre- and post-run energy are recorded. Backs State Lift only.
- `effortPool` — candidates where effort is recorded. Backs Quiet Gain, Same-Cost/Better, and Demand Explained.

Each pool is formed after candidate selection (route-first / distance-fallback, 180-day / cap-10).
A candidate missing the required field is silently excluded from that pool without affecting others.

**Per-signal contract:**
- `ComparisonOutcome` — immutable value object: `line` (formatted signal text), `evidenceCount`
  (size of the relevant pool), `confidencePhrase` (tier text from that pool's count).
- `ComparisonInsight` holds `List<ComparisonOutcome>` replacing the old flat fields. `NONE` uses
  `List.of()`.
- `formatComparison` in `RunAgent` formats each outcome as:
  `- [line] [evidence-bearing comparable runs: N; confidence: tier]`

**Confidence tiers (same thresholds, now per-pool):**
| Pool size | Phrase |
|---|---|
| 1 | last comparable run |
| 2–4 | early signal |
| 5–7 | recent pattern |
| 8+ | strong personal pattern |

**No zero-evidence signal:** a signal only fires when its pool has at least one run. A signal
never uses a count or median from the other pool.

**No refill after cap-10:** the 10-candidate cap applies once before pool formation. Pools are
subsets of candidates, not a second selection pass.

**Collab rules applied:** one file at a time, approval each step, explained the OOP (immutable
value objects, ArrayList pool isolation, median on small lists), flagged commits.
