---
name: design-runstyle-v1
description: "LOCKED July 10 2026 — RunStyle V1: living personal strategy profile (State Lift / Efficiency Gain / Controlled Finish) with secondary context, facets, and habit identity; replaces the faster-AND-farther detectRunStyle"
metadata:
  type: project
---

**STATUS: BUILT AND VERIFIED July 10, 2026.**

All four implementation steps are complete and committed:
- Step 1 added the context enums, `RunContext`, four persistence columns, console capture,
  AI/privacy documentation, legacy music inference, and tests.
- Step 2 added typed RunStyle evidence and the strict candidate path while leaving
  `ComparisonService.analyze()` unchanged at that time. The July 25 signal-confidence repair
  later changed the general `analyze()` evidence pools without changing the strict path.
- Step 3 added `RunStyleService`, `RunStyleInsight`, the three families and stages, facets,
  habits, point-in-time evaluation, announcements, and tests.
- Step 4 wired `Runner.detectRunStyle()` to the service, removed the faster-and-farther and
  rolling-average path, and synchronized the related documentation.

Verification evidence: Manley applied the four-column MySQL migration, `mvn test` passed
with 36 tests, and a live end-to-end log-run succeeded against the migrated database.
Same-day ordering is deterministic in the current flow: `RunStorage` loads by
`run_date, run_id`, and the stable Java date sorts preserve that incoming order.

Follow-up cleanup on the same day removed the dead rolling-average methods from `Runner`
and synchronized `AI_AGENT.md` with the real prompt.

Do not re-litigate the decisions below; flag anything that contradicts the actual
code when reached. Supersedes the old "Your Run
Style" faster-AND-farther rule in [design_run_response_system.md](design_run_response_system.md) and closes
path #3 (`detectRunStyle`) deferred from
[design_comparison_logic_fix.md](design_comparison_logic_fix.md).

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

- During the original RunStyle V1 build, keep `ComparisonService.analyze()` and its
  route-first behavior unchanged. Add typed internal evidence (STATE_LIFT, QUIET_GAIN,
  SAME_COST_BETTER, DEMAND_EXPLAINED) and a **package-private strict RunStyle
  path** that records which signals were measurable even when none fired. July 25 follow-up:
  Task 2 later revised only the general comparison pools; this strict path stayed separate.
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
a title. **Time-of-day identity — deferred by data availability, not by verdict.**
Wanted eventually: it's identity-rich (morning-runner culture) and feeds RunStyle
facets, the music taste split, and reflection. Deferred because start_time/end_time
are stored null today and manual console entry isn't worth the friction. Revisit
when timestamps arrive (mobile phase). When built, it enters as an association
facet under existing RunStyle rules — context decorates, never creates a pattern.

## Behavior

- **Announce only** when the current run first creates an EARLY pattern,
  advances a pattern to FORMING/ESTABLISHED, or first qualifies a new facet.
  No counters, no repeats, no downgrades, no negative patterns — ever.
- **Backdated entry policy (Task 3, July 25 2026):** A manually entered
  backdated run is saved and included in future RunStyle calculations, but it
  never produces a RunStyle announcement at entry. RunStyle evaluates each run
  only through its deterministic chronological position (the prefix of history
  up to and including that run), so later runs cannot alter that run's stage,
  counts, facets, or habit. Only the run that is the final entry in the sorted
  history is eligible to announce. Same-day runs appended last remain eligible
  because the stable date sort preserves incoming order.
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
  Style sections in [design_run_response_system.md](design_run_response_system.md) and
  [project_current_state.md](project_current_state.md).

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
text infers MUSIC; normalization matches case/space variants only; `analyze()` remained
unchanged during this original build; strict path rejects same-route outside the distance band and uses
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

## Historical pre-build reconciliation (performed July 10, 2026, Step 0)

This was the Step 0 snapshot before implementation. Every item below was resolved during
RunStyle V1: database loads now order by date and ID; Java's stable date sort preserves
incoming/list position for same-day runs; `RunContext` replaced the loose music string;
the INSERT now has 19 columns; and the old rolling-average wiring was removed.

Original observations retained for provenance:

- `Runner.detectRunStyle` still had average parameters, and `RunConsole` still created a
  rolling-average snapshot solely for that call.
- `RunStorage.loadRuns` ordered only by date.
- `Run` still carried loose `String musicContext` constructor data.
- `RunStorage.saveRun` had 15 columns before the four RunContext columns.
- `Runner.storeRun` sorted by date only. The accepted resolution was stable list position as
  the in-memory same-day tiebreak, paired with database loading by `run_date, run_id`.
