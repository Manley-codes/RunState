---
name: design-effort-cost
description: "Console V1 built July 8–9 2026; current mobile direction shows post-run Energy choices at rest and keeps Effort behind a quieter optional action"
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

## Current mobile Run Complete direction — August 14, 2026

**STATUS: CURRENT UI DIRECTION, NOT BUILT.** This section governs the future mobile Run Complete
screen. The console record below remains accurate history, but its sequential prompts are not the
mobile interaction to reproduce.

The goal is to preserve two useful subjective signals without making the runner feel interviewed.
Energy and effort remain separate because they answer different things:

- **Energy:** how the runner feels after the run — `Spent / Feeling Good / Powered Up`.
- **Effort:** what the run cost — `Smooth / Working / Heavy / Empty tank`.

### August 16 correction to the August 14 addition pattern

The August 14 direction is **partly superseded**. Energy and Effort remain separate, optional and
independently skippable, but they no longer both rest behind collapsed `+` controls:

- Show all three post-run Energy choices — `Spent / Feeling Good / Powered Up` — clearly and
  prominently at rest while the factual receipt plays. Energy is the main invitation because it is
  closer to RunState's identity and can immediately inform the reflection.
- Keep only Effort collapsed behind the quieter `EFFORT +` action. Tapping it reveals
  `Smooth / Working / Heavy / Empty tank`, either inline or in a lightweight sheet. After selection,
  it may return to a compact confirmation such as `EFFORT · HEAVY`.
- The earlier working group label `ADD TO THIS RUN` is no longer a given. It made sense around two
  collapsed additions; with only Effort behind `+`, whether the screen still needs a group label is
  open for the Run Complete pass.
- Either value, both values, or neither may be recorded. Ignoring them stores `unknown`/`null`;
  never infer an answer, silently default one, or convert absence into a middle value.
- No `Skip` button is needed because continuing without choosing is the skip.
- Choosing Energy must not automatically open Effort. Neither input may block completion, delay
  leaving the screen, or appear as a required modal.

This accepts a deliberate tradeoff: optional input may collect fewer answers, but the answers are
more likely to be intentional than values entered only to dismiss a prompt. The Run Complete pass
should test whether visible Energy feels inviting rather than mandatory and whether the quieter
Effort action remains discoverable.

### Two-stage Run Complete response — August 16, 2026

**STATUS: CURRENT DIRECTION, NOT BUILT.** Run Complete now separates immediate confirmation from the
contextual reflection so the reply stays close to the finish without making Energy or Effort
mandatory.

1. Ending the run first completes a durable local save. The app may say `Run saved` only after that
   succeeds. On mobile, `saved` means recoverable on the phone; it must not imply that cloud sync has
   completed.
2. The completed-run metrics appear immediately. At the same time, RunState gives one short audible
   factual receipt assembled from saved run facts, such as distance and duration. This is not the AI
   reflection and does not require a generative-model call.
3. Post-run Energy choices are clearly visible during that receipt. `EFFORT +` remains available
   with lower prominence. Either value, both values, or neither may be entered.
4. The contextual reflection is requested a natural beat after the runner stops adding values. If
   the runner adds nothing, a longer fallback still requests the reflection so every saved run gets
   one. **No fixed number of seconds is specified.** The prototype must find the timing by testing
   the real stop-breathe-look-tap moment.
5. The reflection uses the Energy and Effort values present when its request begins. It is created
   once and never regenerated. Values entered afterward still save to the run and remain useful for
   future patterns and comparisons, but they do not rewrite that run's reflection.
6. Effort may change the reflection when it genuinely changes the run's story. RunState does not
   promise a more unique reply merely because Effort was entered.

This makes run persistence and reflection generation separate mobile operations. Saving the run
must not depend on the AI call, and the factual receipt fills the immediate emotional moment while
the optional input window and reflection request complete.

**Deeper Run Analysis remains an exploration, not a prerequisite.** A runner may later be offered a
reversible choice for more analysis — possibly during onboarding and again after the app has shown
concrete value — but the timing, wording, scope and defaults are not settled. Energy and Effort stay
individually skippable whether or not that broader mode ever exists.

**Discarded branch from this discussion:** sensor-derived or app-estimated demand is not an active
feature direction. Do not create an estimated-effort field, fallback or roadmap item from the
brainstorm. Runner-reported Effort remains exactly that: a runner report.

**Still open for the Run Complete design pass:** final group label, inline versus lightweight-sheet
presentation, exact visual hierarchy and motion, prototype-tuned timing for the input window and
fallback, and how discoverability/response quality will be tested. The late-input behavior is no
longer open: late values save for future learning and never regenerate the visible reflection.

**Out of scope for this screen pass:** rewriting or recalibrating the reflection itself. Run Complete
decides where and when the existing response appears and which optional values reach it; reply craft
remains in the separate AI and music-response lane.

## The gap

The app knows state in → what you did → state out, but not WHAT IT COST. Two runs can both
end "Feeling Good" — one smooth, one a fight — and today's agent can't tell them apart.
Effort is the missing axis. (Predicted by the session-RPE note in research_app_landscape:
"on-identity future analytic using data already captured — NOT scope creep.")

## Console V1 design — historical build record

The bullets in this section describe the shipped console collector and the reasoning used in July.
Where they differ from the August 14 mobile direction above, the mobile direction governs future UI.

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
- **Historical V1 scope:** the console input was not paywalled. This does not make manual entry
  mandatory in the future mobile UI; the current direction above offers both inputs to everyone and
  lets every runner ignore either one.
- **Historical V1 question budget:** the console flow used at most two quick taps (energy + effort).
  This is not a mobile-UI requirement and is superseded there by the optional mobile presentation.
  Energy cardinality is **settled at three shared levels**
  (July 27, 2026 — see `design_music_intelligence_v1.md`); the four-state State Scan is not a
  domain change. The August 14 direction resolves this portion of the flow by removing sequential
  required questions. The broader run-flow input budget remains a cross-screen concern for the
  progressive-input and Run Complete passes.

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
  **Historical note (July 8, 2026 constraint — SUPERSEDED, not current):** this step originally
  read "do NOT disturb the rolling-average-snapshot-before-addRun order." That snapshot was
  **deleted by RunStyle V1 on July 10, 2026**, so the constraint no longer exists and must not
  be reintroduced. The current orchestration is: **save safely → add to history → AI response →
  RunStyle announcement.**
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
