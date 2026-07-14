---
name: design-effort-cost
description: "✅ V1 BUILT July 8–9 2026 — post-run effort input ('How did that run land?'): energy vs effort axes, runner-native RPE mapping, Quiet Gains concept, question-budget rules"
metadata:
  type: project
---

# Effort Cost — "How did that run land?" (✅ V1 BUILT July 8–9, 2026)

**STATUS: V1 BUILT & COMMITTED.** Collector shipped in commit 8981b48 (EffortLevel enum,
Run field/setter, RunConsole prompt, RunStorage persistence); display + agent wiring
(getRunSummary Effort line, RunAgent prompt line + SYSTEM_PROMPT rule + fallback line) and
docs completed July 9, 2026. The plan below is kept as the build record. Next handoff:
design_comparison_logic_fix.md, which consumes this effort data.

From the Codex RPE session. Direction approved, built as specified (with the Option A setter
correction noted under Key changes).

## The gap

The app knows state in → what you did → state out, but not WHAT IT COST. Two runs can both
end "Feeling Good" — one smooth, one a fight — and today's agent can't tell them apart.
Effort is the missing axis. (Predicted by the session-RPE note in research_app_landscape:
"on-identity future analytic using data already captured — NOT scope creep.")

## Design (locked in principle)

- ONE quick post-run choice, right after post-run energy, before the AI reply:
  **"How did that run land?"** 1 Smooth / 2 Working / 3 Heavy / 4 Empty tank / 0 Skip
- Optional but visibly valuable — if answered, the agent reply uses it immediately
  ("Three miles steady, but you marked it Heavy in this heat — harder than the numbers look").
- NEVER presented as "RPE" — runner-native words only. Internal mapping:
  Smooth = RPE 1–3 · Working = 4–5 · Heavy = 6–7 · Empty tank = 8–10.
- Energy and effort stay SEPARATE axes (no blended question) — the combos ARE the insight:
  Feeling Good + Smooth = controlled fitness · Feeling Good + Heavy = earned resilience ·
  Spent + Empty tank + PR = costly but meaningful. Energy enum untouched (protects Run Style).
- User-facing framing: **Energy = how you finished. Effort = what it cost.**
- Capture immediately — the post-run moment is the app's magic (and matches the RPE-timing
  science in research_app_landscape). A later "settled check: still feel Heavy?" is a
  mobile-phase nicety; it never holds the post-run reply hostage.
- NOT paywalled: the input is core — the app needs the data from everyone to learn.
  Premium = deeper interpretation (effort trends, load patterns, route/weather/music cost
  analysis, fatigue warnings).
- **Question-budget rule:** default post-run flow = TWO quick taps max (energy + effort).
  Deep reflection is opt-in ritual. Reconcile with the State Scan 4-state open question when
  UI resumes — decide the whole run-flow question budget ONCE, together.

## Quiet Gains (product concept this data unlocks)

Progress made visible before pace/distance show it: "Same route, same distance — landed
lighter than last week. That's progress your pace won't show yet." Serves beginners (who
improve before their numbers do), embodies never-shame-a-down-day, passes the runner-native
test. RATION IT — celebrated sparingly or it becomes noise. Also a cold-start asset: early
progress signals for brand-new users.

## V1 build handoff (reviewed July 7, 2026 — Codex draft + Cowork review, approved by Manley)

Collector only — capture, persist, display, feed the agent. Comparison repair is the NEXT
handoff and depends on this data existing.

**Verify first (before any code):** `git status` clean; `SHOW COLUMNS FROM runs;` to
confirm actual energy column names before writing the migration.

**Key changes**
- New `EffortLevel` enum in `com.runstate` — constants `LOW_COST / MODERATE_COST /
  HIGH_COST / MAX_COST`; display labels `Smooth / Working / Heavy / Empty tank` (working
  copy, replaceable without touching stored values); internal RPE ranges as fields on each
  constant (1–3 / 4–5 / 6–7 / 8–10), invisible to the user. OOP lesson: enum carrying data.
- `Run.java`: nullable `EffortLevel effortLevel` — getter + `setEffortLevel(...)` setter,
  **mirroring `postRunEnergy`** (CORRECTED mid-build July 7, 2026; the original "no setter"
  instruction was wrong). The codebase's real convention: data known BEFORE construction
  (route, music, weather) is immutable; data collected in the post-run reflection AFTER the
  summary (post-run energy, effort) uses a setter because the Run already exists. The
  construct-early/learn-later shape is a known wart — refactor candidate for the Spring
  Boot era (build the Run after the reflection questions, or use a builder). NOT V1 scope.
- MySQL (manual, before Java): `ALTER TABLE runs ADD COLUMN effort_level VARCHAR(30) NULL
  AFTER <verified post-run energy column>;`
- `RunStorage.java`: save/load enum name exactly like energy values; legacy NULL rows load safely.

**Behavior**
- `RunConsole.logRun()`: ask immediately after post-run energy, before `saveRun(run)`.
  Do NOT disturb the rolling-average-snapshot-before-addRun order.
  Prompt: "How did that run land?" / 0. Skip / 1. Smooth / 2. Working / 3. Heavy / 4. Empty tank
- History: `Effort: Smooth` only when recorded (parallels the "Energy:" line).
- `RunAgent.buildUserMessage()`: `Effort: Smooth (LOW_COST)` or `Not recorded` — matches
  the energy `label (LEVEL)` convention.
- SYSTEM_PROMPT: energy = how the runner finished, effort = what the run demanded;
  reference effort only when it genuinely fits the run's story — never force it (same rule
  as music/weather); pattern language only; high effort must never imply a bad run.
- Fallback: ONE optional effort-aware line — LOW_COST "That landed controlled." /
  MODERATE_COST none / HIGH_COST "That was heavier than the numbers alone show." /
  MAX_COST "That took a lot out of you, and getting it done matters." Skip the line when
  it duplicates the energy-based sentiment (e.g. Spent + MAX_COST).

**Docs (part of done):** AI_AGENT.md data contract gains the Effort line; this file gets
marked V1 BUILT with date; project_current_state.md status line + queue pointer moves to
the comparison fix.

**Out of scope:** no comparison rebuild, no detectRunStyle() changes, no Run Type, no
final label wording, no "RPE" anywhere in the console UI.

**Test plan:** each choice saves/reloads/shows; skip → NULL, loads, hidden in history;
legacy NULL rows load; agent message correct both ways; fallback works with
ANTHROPIC_API_KEY unset; loadRuns() passes the new last constructor param; app compiles,
log flow intact.

**Collab rules apply:** one file at a time, approval each step, explain the OOP (enum with
fields, nullable reference, constructor evolution), flag commit points.

## Tie-ins

- **design_comparison_logic_fix.md** — Effort supplied the missing measure used by the
  comparison repair, which was subsequently built July 9, 2026.
- Runner-native test: "How did that run land?" passes; "Rate exertion 1–10" fails.
- Positioning: strengthens the core intelligence layer (state-aware reflection) directly.

## Sequencing

Historical sequencing is complete: weather, Effort Cost V1, Comparison Repair V1,
stabilization, and RunStyle V1 have all shipped. See `project_current_state.md` for the
current music-work resume point.
