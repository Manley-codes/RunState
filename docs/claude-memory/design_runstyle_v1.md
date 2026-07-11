---
name: design-runstyle-v1
description: "LOCKED July 10 2026 — RunStyle V1: living personal strategy profile (State Lift / Efficiency Gain / Controlled Finish) with secondary context, facets, and habit identity; replaces the faster-AND-farther detectRunStyle"
metadata:
  type: project
---

**STATUS: LOCKED design, reviewed & agreed July 10, 2026.**
Build progress (July 10, 2026):
- Step 0 — design doc: DONE.
- Step 1 — context enums (SurfaceType/RunCompany/MusicMode) + RunContext value
  object + persistence (4 new columns, legacy music inference) + console Run-context
  section + AI lines + privacy/AI docs + tests: CODE-COMPLETE, verified via javac +
  a JUnit-platform launcher (no Maven locally). PENDING: the manual MySQL migration
  and a live end-to-end log-run (needs DB access Manley has), and Manley's commit.
- Step 2 — typed evidence (RunStyleSignal) + StrictEvidence value object +
  ComparisonService.evaluateStrict strict candidate path (route+distance →
  surface+distance → distance tiers, distance always required, cap 10) + tests:
  CODE-COMPLETE. analyze() untouched; its 9 tests still green. PENDING: commit.
- Step 3 — RunStyleService + RunStyleInsight + RunStyleFamily/RunStyleStage +
  three families (point-in-time) + stages (3/4, 4/5, 6/7) + primary selection +
  announcement transitions (evaluate full vs before-current, diff) + facets
  (descriptive >=3, comparative 5/5/80%/30pp, one condition + one personal) +
  habit line (>=5 of last 10, >=70%) + tests: CODE-COMPLETE, 13 new tests, 36 total
  green via the launcher. NOT yet wired into Runner/RunConsole (that is Step 4), so
  app behavior is unchanged until then. PENDING: commit.
- Step 4 — CODE-COMPLETE. detectRunStyle now delegates to RunStyleService (params +
  faster-AND-farther logic gone); rolling-average snapshot deleted from RunConsole.logRun;
  CLAUDE.md rolling-average rule replaced with a RunStyle/SRP note; the old Run Style
  sections in design_run_response_system.md and project_current_state.md replaced with
  RunStyle V1. 36 tests green; the real wired path was exercised in-process (State Lift
  EARLY announcement prints; too-little-data returns null). getRollingAveragePace/Distance
  remain in Runner but are now unused. PENDING (Manley, needs DB): the MySQL migration and
  a live end-to-end log-run; and commit. Tests ran via a JUnit-platform launcher (no local
  Maven), so `mvn test` still wants a confirming run on Manley's machine.

Do not re-litigate the decisions below; flag anything that contradicts the actual
code when reached. Supersedes the old "Your Run
Style" faster-AND-farther rule in [[design-run-response-system]] and closes
path #3 (`detectRunStyle`) deferred from [[design-comparison-logic-fix]].

## Product intent

RunStyle is a **living personal strategy profile** showing what repeatedly
accompanies productive outcomes. It is NOT a fixed runner archetype, a training
classification, or a causal claim.

- **Primary patterns:** State Lift, Efficiency Gain, Controlled Finish.
- **Secondary context only:** weather, surface, company, music, shoes. These
  can NEVER create a RunStyle or raise primary confidence.
- RunStyle may express runner-native identities (trail-oriented, solo-oriented,
  music-supported) through natural, evidence-based sentences — never fixed
  titles or branded archetypes. **"Trail Runner" (and any branded title) is
  banned.**
- Always distinguish **habit** (what the runner does frequently) from
  **association** (what accompanies productive outcomes).
- Existing general run comparisons (`ComparisonService.analyze()`) remain
  UNCHANGED.
- RunStyle stays **local and deterministic**; the profile is **never sent to
  the AI**.

## Data and interfaces

**New enums**
- `SurfaceType` — ROAD, TRAIL, TRACK, TREADMILL, MIXED
- `RunCompany` — SOLO, WITH_OTHERS
- `MusicMode` — MUSIC, NO_MUSIC (null = not recorded)

**New immutable value object `RunContext`** (NOT "RunConditions")
- Holds: surface, company, shoe label, music mode, and the existing music text.
- Replaces the loose `musicContext` constructor param on `Run`.
- `Run` keeps null-safe delegating getters — same pattern as `WeatherData`
  (guard the null bundle, then guard the null field).

**Migration (run manually on MySQL BEFORE the Java persistence change):**
```sql
ALTER TABLE runs
  ADD COLUMN music_mode   VARCHAR(30)  NULL AFTER music_context,
  ADD COLUMN surface_type VARCHAR(30)  NULL AFTER music_mode,
  ADD COLUMN shoe_label   VARCHAR(100) NULL AFTER surface_type,
  ADD COLUMN run_company   VARCHAR(30)  NULL AFTER shoe_label;
```

**Legacy rows:** infer MUSIC only when `music_context` is populated; two null
music fields = "Not recorded"; **never infer NO_MUSIC**.

**Console:** one optional "Run context" section with the pre-run details, a
single skip-all action, each answer individually skippable — Surface, Shoes
(reusable free-text label), Company, Sound (ask the music text only AFTER
Music). Post-run stays exactly two questions (energy, effort).

**Normalization:** shoe/music text matched with trim + case-fold. No fuzzy.

**Ordering:** change `loadRuns` to `ORDER BY run_date, run_id`. Same-day runs
ordered by run_date then run_id **everywhere**. During the build, align the
in-memory `runId` with the DB `run_id` (or use list position as tiebreak) —
today `RunConsole` assigns `runCount+1`, which can misorder against DB ids with
gaps. This must be deterministic.

## Detection

- Keep `ComparisonService.analyze()` and its route-first tests **byte-for-byte
  behavior-identical**. Add typed internal evidence (STATE_LIFT, QUIET_GAIN,
  SAME_COST_BETTER, DEMAND_EXPLAINED) and a **package-private strict RunStyle
  path** that records which signals were measurable even when none fired.
- **Strict candidates:** previous runs within 180 days; similar distance ALWAYS
  required using `max(0.5 mi, 20%)`; prefer same route + similar distance, then
  same surface + similar distance, then similar distance; cap 10.
- **New `RunStyleService.analyze(current, history)`** returning immutable
  `RunStyleInsight`. `Runner.detectRunStyle(Run)` delegates to it; **remove the
  avgPace/avgDistance params and the faster-AND-farther logic.**
- **POINT-IN-TIME RULE (critical):** when a past run is evaluated as an
  opportunity, its comparison basis is only the runs that preceded it (run_date,
  then run_id). Future information must never leak into past pattern counts.

**Three families** (a run is an "opportunity" for a family only when that family
is measurable; missing data is never a failure):
- **State Lift:** pre and post energy recorded; supports when post > pre
  (direct lift, not vs median — habitual lifts must stay detectable).
- **Efficiency Gain:** strict comparison effort-evaluable; supports when Quiet
  Gain or Same-Cost/Better fires.
- **Controlled Finish:** post energy + effort recorded; supports when post is
  moderate/high, effort low/moderate, and energy did not decline when pre
  exists.

**Stages** over the latest opportunities per family: EARLY = 3 of 4, FORMING =
4 of 5, ESTABLISHED = 6 of 7. Highest stage wins.

**Primary pattern selection:** stage, then support rate, then support count,
then latest support, then tie priority **Efficiency Gain > State Lift >
Controlled Finish**.

- PR and Demand Explained may **color** the current message but never raise
  stage or become primary. Weather stays demand context only (>=80°F warm,
  <=32°F cold); it never alters selection, stage, or primary pattern.

**Facets (association layer):**
- Descriptive wording after the same context appears in **>=3 primary-supporting
  runs**.
- Comparative wording needs **>=5 opportunities WITH and >=5 WITHOUT** the
  context, **>=80% support rate** with it, and a **>=30 percentage-point lead**.
- Max **one condition facet** (surface/company/weather) + **one personal facet**
  (music/shoes).
- Contrasted beats descriptive; then support count, recency; ties prefer
  surface > company > weather and music > shoes.

**Habit identity (separate from facets):** its own optional line, capped at one,
shown only when a context was recorded on **>=5 of the last 10 runs** AND
present in **>=70% of the runs where it was recorded**. Wording is
frequency-based ("Trails are becoming a consistent part of how you run"), never
a title. Morning/time-of-day identity is OUT (start_time/end_time are stored
null today).

## Behavior

- **Announce only** when the current run first creates an EARLY pattern,
  advances a pattern to FORMING/ESTABLISHED, or first qualifies a new facet.
  No counters, no repeats, no downgrades, no negative patterns — ever.
- **Deterministic wording:**
  - Early — "A RunStyle pattern is beginning to show."
  - Forming — "Your RunStyle is forming."
  - Established — "This is becoming part of your RunStyle."
  - Followed by support/opportunity counts, the primary observation, qualified
    facets, and the habit line when earned.
- **History view** adds one optional compact line, e.g.
  "Context: Trail | Solo | Shoes: Pegasus 41 | No music".
- **AI input (`buildUserMessage`)** adds Surface, Run company, Shoes, and an
  unambiguous Music line; `SYSTEM_PROMPT` may reference them only when relevant,
  association language only. Update `docs/DATA_PRIVACY.md` and `AI_AGENT.md`:
  these fields now leave the app to Anthropic; no companion identities collected
  or sent. **The RunStyle profile itself is never sent.**
- **Doc reconciliation (same commit as the detector swap):** remove the
  CLAUDE.md "rolling average snapshot before addRun()" rule and delete the
  now-dead snapshot in `RunConsole.logRun` (lines ~120-122); replace the old Run
  Style sections in [[design-run-response-system]] and [[project-current-state]].

## Execution order (each numbered step = one commit, flagged to Manley)

1. Context enums + `RunContext` + migration + persistence + console prompts + AI
   lines + privacy/AI_AGENT docs + legacy-loading tests.
2. Typed comparison evidence + strict candidate path, proving `analyze()`
   unchanged (existing 9 tests still green, untouched).
3. `RunStyleService` + `RunStyleInsight` + three families + stages +
   announcement transitions + facet/habit analysis + tests.
4. Swap `detectRunStyle` wiring, delete rolling-average snapshot, reconcile
   CLAUDE.md + design docs, full `mvn test`, end-to-end log-run against migrated
   MySQL.

**Tests must cover:** every context choice saves/reloads/displays/reaches the
AI; skips and legacy nulls safe; NO_MUSIC distinct from missing; legacy music
text infers MUSIC; normalization matches case/space variants only; `analyze()`
unchanged; strict path rejects same-route outside the distance band and uses
surface fallback; missing data = non-opportunity; each family hits each stage at
exact thresholds; habitual lifts stay detectable; point-in-time evaluation (a
future run never changes a past count); same-day ordering deterministic;
PR/Demand-Explained never primary; facet thresholds incl. the 5/5/80%/30pp
comparative bar; max two facets + one habit line; weather never changes
confidence; alerts only on transitions, never downgrades; AI-key-unset fallback
safe.

## Collaboration rules (non-negotiable)

- Learning project. One file at a time, approval before each step; each numbered
  step above is SEVERAL approval-sized moves. Explain the Java/OOP as we go
  (value object, enums, package-private, streams).
- Never replace existing code with placeholder comments; preserve real logic
  lines and modify only what's requested.
- Do NOT run git commit or push. Flag commit points and give the full
  step-by-step PowerShell commit walkthrough each time — Manley runs the commits.
- Never mention below-average performance in any run-facing output.

## Reconciliation with current code (verified July 10, 2026, Step 0)

Confirmed the handoff matches the code, with one gap to resolve during the build:

- `Runner.detectRunStyle(Run, double avgPace, double avgDistance)` — the params
  are removed in Step 4; only `RunConsole.logRun` (line ~149) calls it.
- The rolling-average snapshot (`RunConsole.logRun` ~120-122) feeds only that
  call, so it becomes dead exactly when the params go.
- `runId = runner.getRunCount() + 1` (`RunConsole.logRun` ~103) is the misorder
  risk; also `RunStorage.saveRun` never reads back the DB auto-increment key, so
  the in-memory id is always a guess. Deciding how to align these is part of
  Step 1/4.
- `RunStorage.loadRuns` currently `ORDER BY run_date ASC` → change to
  `run_date, run_id`.
- The `Run` constructor's loose `String musicContext` (position 13) is consumed
  in three places: `RunConsole.logRun`, `RunStorage.loadRuns`, and
  `ComparisonServiceTest` — all three change when `RunContext` lands.
- `RunStorage.saveRun` INSERT is 15 columns today → 19 after the four new ones.
- **GAP (not in the handoff):** `Runner.storeRun` sorts run history by DATE ONLY
  (`Runner.java` ~237-238), with no `run_id` tiebreak. For the point-in-time
  rule to be deterministic in memory — not just on DB reload — this comparator
  must also apply the `run_id` (or list-position) tiebreak. Fold into the
  ordering work in Step 1.
