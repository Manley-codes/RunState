---
name: project-current-state
description: "Current development state of RunState — what's built, what's next, and key decisions locked in"
metadata:
  type: project
---

As of August 30, 2026, RunState remains one product and one Git repository with two implementation
areas: the intact working Java/Maven console application and an early native Android/Kotlin/Compose
foundation under `android/`. The mobile screens discussed below remain interactive design
prototypes; the Android application does not yet implement that designed journey.

## Current delivery resume point — August 30, 2026

- **Phase 3 Android implementation is now in progress.** Commit `ea43335` added the minimal Android
  shell and one static Compose screen, verified by building, installing and launching it on the
  Medium Phone emulator. Commits `93bacb9` and `505c89e` established an isolated in-memory
  `RunSessionStateMachine`; its enum names all five approved states, and its implemented behavior now
  covers guarded `NO_SESSION → COUNTDOWN → RUNNING ⇄ PAUSED → COMPLETED` transitions. Seventeen local
  JUnit tests pass, including rejection of repeated countdown, skipping directly to Running, invalid
  pause/resume attempts, completing before Paused and completing twice. This state machine is not
  connected to the Compose screen and does not yet save anything.

- Log History has a stable design foundation. Its most-recent-record quick peek was completed and
  accepted August 13: approximately 1.66 seconds of total visible expansion-and-collapse motion,
  with only about 0.10 seconds held fully open. Exact timing lives in `ui_phase_handoff.md`.
- The optional pre-run energy and Start-to-Active Run pass is stable enough to leave for now. Its
  current canonical record is `design_start_run_v7.md`; energy-domain details are in
  `design_state_scan.md`.
- The shoe control now has an accepted `Add Shoes` prototype with live search, saved/add/select
  behavior, selected-shoe confirmation and displayed mileage. Automatic exactly-once mileage remains
  a production requirement, some shoe images need later cutout cleanup, and removal is changing to a
  recoverable Undo that preserves past-run history. See `design_shoe_selection.md`.
- The active-run prototype includes pace, BPM, and elapsed time inside the circular instrument;
  large distance below; a live now-playing/current-run strip; and one Pause control. Pressing Pause
  reveals Stop and Play. Play resumes run tracking; a 1.5-second hold on Stop ends it and serves as
  the confirmation. For the first real demonstration, BPM is omitted unless a separately approved
  real sensor source exists; fixture BPM must never masquerade as a live measurement.
- Log History voice playback now has a successful six-persona Edge prototype for smooth, human
  delivery. It remains a testing surface, not a selected production TTS engine or bundle of reusable
  voice assets; details and open dependencies live in `ui_phase_handoff.md`.
- The mobile Run Complete direction now has a stable, fully exercised interactive prototype. It
  shows all three post-run Energy choices prominently and
  keeps Effort behind a quieter `EFFORT +` action rather than using sequential required questions.
  Either, both or neither may be recorded; ignored values remain unknown. After durable local save,
  metrics and a short factual audio confirmation appear while RunState prepares four responses from
  one factual foundation: `Spent`, `Feeling Good`, `Powered Up`, and no selection. Energy selects
  the one immediate response that is revealed and stored; the candidates may differ creatively in
  interpretation and structure, not only in their ending. Effort does not change that response. It
  is stored for future comparisons, Quiet Gains, runner learning, RunStyle or Run Rhythm analysis,
  and accurate references to past effort in later messages. Choosing Energy immediately sends its
  square into the visualizer and the selected reflection arrives from that motion. No Energy uses a
  quiet fallback window; Effort uses a lightweight sheet and may be entered late. Save/reflection
  failures, retry, reload and single-shell Log History continuity are settled in the prototype. The
  combined journey is published in the public design preview as **Finish a run**. The built console
  flow remains unchanged. See `design_run_response_system.md` and
  `design_effort_cost.md`.
- Saved-run management Pass 2 is implemented and verified as a user-facing Java console path through
  View Run History. History displays real database IDs, accepts ID selection and Back, and exposes
  post-run Energy/Effort updates plus confirmed deletion. Storage is called before memory changes;
  missing IDs and storage failures leave in-memory history untouched, explain that History may be
  stale, and end the console session. Deletion silently rebuilds historical PR flags in stable
  chronological order without AI, reflection, PR announcements, or RunStyle work. The complete
  Maven suite passes with 419 tests. A full editor for distance, route, date, weather, music, or
  other fields remains intentionally out of scope.
- The narrow Core Running Foundation Review is complete. It confirmed the completed-run console
  journey and identified four bounded gaps before mobile: comparison trust, durable active-session
  timestamps/state, local-first identity/sync, and a durable selected-reflection record. The
  comparison gap is fixed and verified. The active-session lifecycle, recovery and timestamp
  contract is approved and recorded in `run_initiation_register.md`. Its full in-memory state
  ordering through Completed now exists in the isolated Android state machine, but persistence,
  timestamps and recovery remain unimplemented. The
  phone-generated UUID and `PENDING_CREATE` / `SYNCED` / `PENDING_UPDATE` / `PENDING_DELETE`
  local-first contract is also approved there and not implemented. The one-to-one selected
  reflection with `PENDING` / `READY` / `FAILED` is approved in `design_run_response_system.md` and
  not implemented. All four review findings now have either a verified fix or an approved contract;
  that review is closed.
- The music feature inventory and prioritization review is complete. It separates the music
  evidence-and-feedback foundation from later playlist selection, live support and expression
  surfaces. A provider-neutral structured song-history contract is approved in
  `design_music_selection_system.md`: actual playback, RunState's decisions and optional whole-run
  Taste / Run fit feedback remain distinct under the permanent run UUID, with honest partial and
  unknown states. Current console behavior remains the manual music state plus optional free-text
  note. No provider, schema, API, screen, automatic capture, Java or mobile implementation is
  authorized by that contract.
- The broader mobile technical-contract gate is now also complete. `run_initiation_register.md`
  defines the shared timeline for accepted GPS observations, active-time distance and pace,
  automatic full and partial splits, partial/unavailable telemetry, and the completed-run
  composition under the permanent UUID. It also locks the first implementation boundary: native
  Android/Kotlin, Room as the on-phone source of truth, a foreground service for active sessions,
  and a minimal server for reflection plus later sync with credentials off-device. Saving and
  reflection remain separate; full RunStyle stays local. The Android shell and full in-memory
  session-state ordering now exist, while Room, the foreground service and the rest of this
  architecture remain approved contracts rather than implemented behavior.
- **Next delivery task:** introduce stable run identity and the Room-backed session boundary required
  before the visible app may claim that a run is Running.
  After that, continue toward the durable fixture journey through Log History and prove it survives
  reopening before introducing GPS or provider integration. Remaining Log History polish, further
  music intelligence and RunStyle V2 stay behind that foundation.
- No GPS tracking, BPM source, music-provider integration, active-session persistence, recovery,
  foreground service, or Reflection Engine has been built. The current Android screen is static,
  and its in-memory state rules are not connected to the interface.

**Completed phases:**
- Phase 1: Console app — energy system, opening prompt, post-run responses, rolling averages
- Phase 2: MySQL persistence — runs save and load between sessions (`RunStorage.java`, `runstate` DB schema)
- Phase 3: Your Run Style — original pattern detection (`detectRunStyle()` in Runner.java). SUPERSEDED by RunStyle V1, July 10, 2026 (see below).
- Phase 4: AI agent — `RunAgent.java` replaces `buildRunResponse()` with Anthropic API call (claude-haiku-4-5-20251001). Fallback to hardcoded logic on any failure. Gson added for JSON parsing. `Run.java` has `getRunner()` getter added.
- Phase 5 (Steps 1–2): AI context expansion — music context (manual input) + weather (Open-Meteo, `WeatherData` value object, persisted at log time). Built June 26; weather cleanup + TX→Texas fix shipped July 6–7, 2026.
- Effort Cost V1: post-run "How did that run land?" input — `EffortLevel` enum (Smooth/Working/Heavy/Empty tank + internal RPE ranges), persisted at log time, shown in run history and fed to the agent (prompt line + SYSTEM_PROMPT rule + offline fallback). Built July 8–9, 2026 (collector commit 8981b48 + display/agent follow-up).
- Comparison Repair V1: candidate-based run comparison (`ComparisonService` + `ComparisonInsight`) — 180-day/cap-10 selection (same route within the distance band first, then similar-distance fallback), median aggregation, four positive signals behind a negative pre-filter, and July 25 signal-specific energy/effort evidence pools and confidence — replaces the blended 20-run rolling-average flaw in the AI prompt + fallback. `detectRunStyle()` was later replaced by RunStyle V1. Built July 9, refined July 25, and given the shared-route distance guard August 25, 2026.

**Rename completed June 22, 2026:**
- Package renamed from `com.runclubapp` to `com.runstate` — all 7 source files updated
- `pom.xml` groupId → `com.runstate`, artifactId → `RunState`
- IntelliJ module name still shows "RunNet" — cosmetic only, fix via right-click → Rename in Project panel

**RunStyle V1 — REBUILT July 10, 2026 (replaces the original "Your Run Style"):**
The old faster-AND-farther-vs-rolling-average funnel is GONE. RunStyle V1 is a living,
local, deterministic strategy profile in `RunStyleService` (`Runner.detectRunStyle(Run)`
delegates). Full spec: `design_runstyle_v1.md`. In brief:
- Three primary families — State Lift, Efficiency Gain, Controlled Finish — judged
  point-in-time (a run only counts where its family is measurable).
- Stages from the latest opportunities per family (EARLY 3/4, FORMING 4/5, ESTABLISHED
  6/7); strongest staged family is primary. PR and Demand-Explained color but never lead.
- Secondary context (surface/company/weather/music/shoes) only decorates as association
  facets (descriptive ≥3; comparative 5/5/80%/30pp; one condition + one personal) or a
  frequency-based habit line (≥5 of last 10, ≥70%). Context never creates a pattern.
- Announces only on a first advance or newly qualified facet — never repeats or downgrades.
- Never sent to the AI. Time-of-day identity: deferred by data availability, not by
  verdict — wanted eventually; revisit when start/end timestamps arrive (mobile phase).
  Full status wording in design_runstyle_v1.md.

**Completed sequence:**
1. Phase 5 — DONE (music + weather shipped June 26–July 7, 2026)
2. Effort Cost V1 — DONE (July 8–9, 2026)
3. Comparison Repair V1 — DONE (July 9, 2026); candidate-based comparison replaces the blended-average flaw (AI prompt + fallback; `detectRunStyle()` deferred)
4. Stabilization sprint — DONE (July 10, 2026); privacy/code alignment, RunAgent HTTP timeouts, DB password moved to env var, and the project's first unit tests (see handoff below)
5. **RunStyle V1 — DONE AND VERIFIED July 10, 2026 (see `design_runstyle_v1.md`).** Rebased `detectRunStyle()` off the faster-AND-farther rolling-average rule onto the identity-aligned profile: `RunContext` value object + four new context columns, `RunStyleService`/`RunStyleInsight` with three families / stages / facets / habit / point-in-time announcements, and a separate `ComparisonService.evaluateStrict` typed-evidence path. The general `analyze()` path stayed unchanged during that original build and was later refined by the July 25 signal-confidence task; the strict path remained separate. Wiring was swapped and the rolling-average snapshot deleted. Verification evidence: Manley applied the four-column MySQL migration, `mvn test` passed with 36 tests, and a live end-to-end log-run succeeded against the migrated database. Same-day ordering is deterministic in the current flow: the database loads by `run_date, run_id`, and Java's stable in-memory date sorts preserve that incoming order.
6. **Honest Database Failure Handling — DONE AND VERIFIED July 15, 2026.** RunState now treats a run as logged only after a durable DB save. Added checked `RunStorageException` (preserves the original `SQLException` as cause) and a package-private `ConnectionProvider` seam in `RunStorage`; `saveRun`/`loadRuns` throw instead of the old swallow-and-print. `App` reports a startup load failure (friendly message + `Details:` from the cause) and exits before the opening prompt/menu — never an empty or partial history. `RunConsole.logRun()` was reordered to save BEFORE `addRun`/PR/AI/RunStyle (kills the phantom-PR bug where a failed save had already announced a PR), and prints a recovery receipt on save failure (includes the "check Run History" duplicate warning), ending the session via a boolean return. Summary-before-reflection preserved. Tests: 5 new `RunStorageTest` cases via the injected failing provider (load/save throw, load never returns partial, both preserve the cause) — 41 tests green. All three manual acceptance tests passed live. Built on the project's first feature branch (`feature/honest-db-failure-handling`), merged fast-forward to master and pushed. Out of scope by design: migrations, retries, offline queue, mobile sync, doc reconciliation. Locked future direction — mobile phase uses durable local-first recording (stable run id, pending/synced states, duplicate-safe sync, server retry without losing the recorded run).

7. **Malformed Stored-Row Handling — DONE AND VERIFIED July 16, 2026 (code baseline `0af9524`).** Closes flow-audit items 1 and 2. Malformed persisted enum values now follow the same controlled startup-failure path as an unreachable database instead of escaping as an uncaught `IllegalArgumentException`. Added a private checked `StoredRunDecodeException` inside `RunStorage` (never escapes the class; wrapped in the public `RunStorageException` at the boundary, so `App`/`RunConsole` are unchanged) plus `decodeOptionalEnum`/`decodeRequiredEnum` generic helpers. All seven enum columns route through them: `distance_unit` (required), pre/post energy, surface, company, music mode, effort. Decode contract — exact enum names only, no trimming, casing, guessing, or defaulting; null stays valid for optional enums; any malformed row fails the WHOLE load (never partial); stored data is never mutated; causes preserved end to end. `inferMusicMode` now takes an already-decoded `MusicMode` rather than raw text, so a corrupt stored mode fails as corrupt instead of silently falling through to note-based inference (validation before inference). Added a package-private `readRuns(Runner, ResultSet)` seam — the second testing seam alongside `ConnectionProvider`: that one simulates an unreachable database, this one a reachable database holding bad data. Tests: 10 new (7 parameterized across every enum column, missing required unit, valid-row-then-malformed-row proving no partial history, and a legacy row with optional nulls + music inference that must still LOAD) via a `java.lang.reflect.Proxy` fake `ResultSet` — no MySQL touched, no corrupt rows written to real history. **51 tests green.** Startup guidance now reads "Check the database connection or stored run data..."; `CURRENT_RUN_FLOW.md` + `current-run-flow.svg` updated (markers 1 and 2 removed from the diagram, both rows marked Resolved in the table). Schema verified before enforcing: `distance_unit` is `NOT NULL` with zero null rows, so the required check cannot lock Manley out of existing history. Out of scope by design: no schema migration, no new dependencies, no public API change. Noted but NOT fixed: the `temperature` column has a stray default of `0` (harmless — `saveRun` always writes the column explicitly).

**Pre-Music Integrity Sprint — COMPLETED July 25, 2026. All 6 tasks done, 77 tests green.**

| Task | What shipped |
|---|---|
| 1 | Pace rollover fix — `RunAgent.formatPace` no longer formats `:60` when rounding pushes seconds to 60; rolls over to next whole minute instead. |
| 2 | Signal-specific comparison confidence — `ComparisonOutcome` value object (line, evidenceCount, confidencePhrase); separate `energyPool` and `effortPool` formed after candidate selection so each signal's count and confidence reflect only the runs where that signal is measurable. Tiers: 1 → "last comparable run"; 2–4 → "early signal"; 5–7 → "recent pattern"; 8+ → "strong personal pattern". |
| 3 | Backdated RunStyle announcement policy — `RunStyleService.analyze()` evaluates each run through its chronological prefix (indexOf + subList); only the genuinely last entry in sorted history is eligible to announce. Backdated runs save and contribute to future calculations silently; no historical-as-today announcement. |
| 4 | Failed-save orchestration regression — `saveAndCompleteRun()` extracted from `logRun()` (behavior-neutral); `saveRun()` and `buildRunResponse()` added as package-private delegates. `RunConsoleTest` proves a failing save leaves history untouched, PR flags unset, AI response unsent, and RunStyle unchecked. Flow-audit item 3 closed. |
| 5 | Context in summaries — `Run.getContextSummary()` private helper (parts-collector ArrayList, `String.join(" \| ", pieces)`) wired into `getRunSummary()` after pace/duration and before energy. Compact `Context:` line appears in post-run view, Run History, and recovery receipt. Nine `RunContextTest` cases; flow-audit item 4 closed. |
| 6 | Documentation reconciliation — active privacy, AI, comparison, response, RunStyle, project-memory, README, roadmap, and flow records reconciled with verified behavior. A post-sprint audit then removed remaining stale flow text, historical-as-current wording, and music-order contradictions. All four flow-audit findings are closed. |

Flow-audit findings: all four closed — items 1 and 2 (malformed-row handling, July 16); item 3 (save orchestration, Task 4); item 4 (context in summaries, Task 5).

**Historical Music Intelligence stopping point — August 2, 2026.** This section preserves the music
evidence and gate as they stood when that work paused. It is **not** the current project resume point;
the authoritative August 13 UI resume point and queue are at the top of this file.
- **V1 planning is complete and committed (`0f22c99`, July 27, 2026).** The canonical contract is
  `design_music_intelligence_v1.md` — purpose and boundary, closed foundational contracts,
  current evidence and reply behavior, deferred persistence boundary, bounded implementation
  contract, automated verification plan, manual model-evaluation plan, and execution order.
  Treat it as the single source of truth; other music docs point to it rather than restate it.
- **The original bounded prompt slice was implemented July 27, then revised from live smoke
  evidence and committed July 30 (`693bfb3`).** Production remains bounded to
  `RunAgent.java`; deterministic coverage lives in `RunAgentTest.java`, with the opt-in
  `MusicIntelligenceEvaluationRunner.java` as the separate manual-evaluation surface. What
  remains structurally true:
  `MUSIC_REPLY_RULES` separated from the general
  `SYSTEM_PROMPT` behind the `Music reply rules:` marker, a deterministic no-network
  `buildRequestBody(Run)` seam, the private `EARLY|ESTABLISHED` stage derivation, blank-safe
  `describeMusic`, the all-free-text-is-data instruction, a music-neutral fallback regression,
  and Gson-correct JSON serialization replacing the hand-written escaper.
- **The deterministic Maven gate is PASSING: 362 tests, 0 failures, 0 errors, 0 skipped**
  (clean July 31 run after the Opus response-handling correction). It verifies transport, placement, prompt content, evaluation-runner
  safety, and fixture integrity — **not** final model quality.
- **Active design, AI-agent, evaluation, privacy, roadmap, and status documents were reconciled
  July 30.** The creative revision sends no new data and adds no provider, so the privacy
  disclosure itself did not change; its prompt-source cross-reference was corrected.
- **Evaluation was incomplete when this lane paused.** Sanitized fixtures and the opt-in runner exist.
  One authentication-invalid launch produced fallback text only; the first valid 12-call smoke
  completed with zero fallbacks but failed product quality. The prompt was revised in response.
  **The revised-prompt smoke then ran July 30 — 12 calls, zero fallbacks — and also failed
  diagnostic quality and trust.** Prose and music participation improved, but the model still
  asserted unsupported characteristics for named tracks (S4 unfamiliar-music is the clearest
  case), openings collapsed onto one template, and closings returned to coaching filler.
  **No final run has occurred. Combined Music Intelligence V1 is NOT complete.**
- **A separate creative-ceiling probe ran July 30 at `6cb3075` — 12 completed calls,
  diagnostic only.** It swapped in a minimal creative system prompt as the single variable to
  test whether the production prompt was suppressing the model or the model simply cannot do
  this. **Minimal prompting improved the prose but did not produce reliable replies.** Both
  independent graders reached the pre-registered **0–3** band (Cowork 0 Hit / 1 Near-hit /
  11 Miss; Codex 2 Hit / 1 Near-hit / 9 Miss — preserved unreconciled) and both counted **nine
  hard-trust failures**, triggering the three-or-more trust override. **Manley found neither
  disputed reply app-worthy**, preferring S11-1 only if forced — a relative preference, not a
  Hit. Full record in `music_intelligence_creative_ceiling_probe.md`; raw transcript, request
  bodies, and smoke screenshots are preserved under `docs/design/evidence/`.
- **The separately approved stronger-model control completed July 31 — 12 usable Opus 5
  replies, diagnostic only.** Manley graded the shuffled packet before opening the scenario
  mapping; the model identity was already known. Manley's creative tally was **1 Hit / 7
  Near-hits / 4 Misses**; strict application of the control's old trust-collapsing label rule
  gives **1 / 3 / 8**. There were **6 clear hard-trust failures**. Both the pre-registered 0–3
  Hit band and the three-or-more trust-failure override apply: **Opus alone does not meet the
  bar.** The seven
  Near-hits are still useful evidence: creative ideas appeared, but the replies routinely
  over-explained the music, sounded too formal or literary, led with artist/title names too
  often, and lacked editing/compression. Full record:
  `music_intelligence_stronger_model_control.md`; exact transcript and grading packet are in
  `docs/design/evidence/`.
- **Future diagnostic grading keeps three questions separate:** creative value (Strong /
  Promising / Weak), trust (Pass / Fail—removable / Fail—load-bearing), and app readiness
  (Yes / No). A trust failure means that wording is not app-ready until corrected, but a useful
  creative move may still be harvested. Final V1 acceptance remains zero-tolerance for hard
  trust failures. Historical grades are not retroactively rewritten.
- **Energy domain — CLOSED.** The shared LOW/MODERATE/HIGH pre/post energy domain is retained.
  The four-state State Scan sketch is **superseded as a domain proposal**; later UI work may
  refine labels and presentation without changing stored meanings.
- **Display-independent revision — IMPLEMENTED August 2 at `b2227b2`.** The target is **creative wording that lands
  cleanly**: clever and clear, usually understood immediately or after a quick beat, with run
  facts and a small music shard often fused instead of followed by a song explanation. Build
  calibration candidates from Manley's successful rewrites and label the different techniques
  so one construction does not become the new template. Treat performance-first openings,
  persona tags, title fusion, direct naming, and music-free runner truth as optional colors, not
  mandatory formulas. Manley approved four exact calibration replies. Unfamiliar music remains a
  safety evaluation case rather than a teaching example. The hip-hop-only evidence does not prove
  genre transfer; test that later before claiming it. The approved prompt, calibration examples,
  controlled S1/S12 fixture replacements, and deterministic guards are committed at `b2227b2`;
  **382 tests passed and no live model call occurred.**
- **Reply-card fit — COMPLETED August 2:** short, medium, and longer candidates were checked in the
  expandable card. The result supports expanded-first with user-controlled collapse and
  idea-governed compression with no numeric sentence limit. This was a narrow measurement, not a
  full UI build or authorization for Spring Boot or mobile implementation.
- **Gate if Music Intelligence resumes:** Manley must separately approve any new 12-call smoke of
  the implemented revision. No fresh smoke or 36-call final evaluation is approved yet. This gate
  applies to renewed music evaluation; it does not block the active UI queue.
- **Separate lanes recorded at that stopping point:** the possible `Ready-ish` wording change,
  stronger persona preference, and the larger RunStyle redesign were not part of the bounded music
  revision. The old instruction to wait for music calibration before continuing UI work is
  superseded by the August 13 resume point. Existing RunStyle V1 remains the built behavior unless
  a separate V2 proposal is reviewed and explicitly approved.
- The future `REFERENCED / NOT_REFERENCED / UNKNOWN` reply-outcome boundary is **documented**
  in the canonical plan, but cross-run reference-frequency implementation (schema, rolling
  window, detection, prompt line) remains **deferred** — it is not part of the prompt slice.
- **Roadmap refinement approved August 1 — now active:** low-fidelity screen work may loop into the
  Core Running Foundation Review without waiting for every display-dependent music decision to be
  finalized in a terminal. The first screen experiment was the completed reply-card density test
  above. The **Core Running Foundation Review**
  remains tightly fenced to one question —
  is the central journey (record a run → preserve it safely → understand it → manage it → use
  it later) credible and structurally ready for a real interface? "Manage" (edit/delete a
  logged run) is the known thin spot. Output is a short gap list, not a feature hunt; "add
  every feature Strava has" is explicitly out.
- **Superseded delivery order, August 26:** do not insert a standalone Spring Boot migration or
  more rough-screen work before the mobile foundation. Build the native Android/Kotlin journey
  first with Room and fixture data, then add foreground-service GPS. Introduce only the minimal
  server boundary when reflection generation needs it; later sync may extend that seam. If Music
  Intelligence evaluation resumes, retain its separate approval gate rather than inserting it
  ahead of the mobile journey.
- The GPS phase delivers the approved time-aligned run telemetry, and automatic splits are built
  there as a **general running capability** shared by run screens and music intelligence. Canonical
  contract: `run_initiation_register.md`; historical decision record:
  `parked_feature_ideas.md`.
- Spotify integration and live-DJ behavior remain later possibilities with separate legal,
  privacy, provider, and platform dependencies.

*(Historical music snapshot updated August 2, 2026 after the revised prompt and calibration set were
committed at `b2227b2` with 382 deterministic tests green and no live call. If that lane resumes, its
next gate is a separate decision on a fresh 12-call diagnostic smoke. Combined V1 remains incomplete.)*

**Standing milestone — privacy / security / legal pass (added July 6, 2026):**
Before RunState is released, shared publicly, or gains multi-user/all-day-listening features,
a dedicated pass is REQUIRED covering: data privacy (what leaves the app, to whom, why —
extend docs/DATA_PRIVACY.md), consent tiers (run music vs all-day listening vs any cross-user
data), security (API keys out of source, DB credentials, dedicated DB user not root), and
legal basics (third-party API terms of service — Anthropic, Open-Meteo, Spotify, Last.fm/
ListenBrainz; health-adjacent data handling). Grows in weight with each phase — mobile + GPS
location data raises the bar again. Not a current blocker; IS a release blocker.

Feature map (July 6, 2026) — which features strongly trigger which concern:
- PRIVACY: all-day listening profiling (heaviest — needs its own opt-in tier); GPS routes +
  live location (Phase 6), including shared meetup beacons and any later live friend/group
  locations or trail playlist beacons; run/energy data → Anthropic API (live now, covered in
  DATA_PRIVACY.md); location → Open-Meteo (live now) — the **city name** goes to the geocoding
  endpoint and, **only after geocoding succeeds**, the **selected candidate's coordinates plus
  the run date** go to the forecast endpoint. The stored **state is used locally** to pick which
  returned candidate matches and is **never placed in either request**; a missing or blank city
  produces no Open-Meteo request at all. Authoritative as-built disclosure: DATA_PRIVACY.md.
  Also: cross-user aggregate content (Phase 7); run photos (share card/gallery).
- SECURITY: Anthropic API key out of source code; MySQL dedicated user, no root (existing
  rule); Spotify/Last.fm OAuth token storage; server + user accounts if Phase 7 happens.
- LEGAL: lyrics text licensing (Musixmatch paid, Genius scraping = ToS violation) — gates
  the lyric-trigger and lyric-aware reply features; third-party API terms (Spotify's
  data-use rules especially; Open-Meteo attribution); health-adjacent data (HR/effort —
  weight jumps at multi-user); user-generated content moderation if community playlists
  ship (Phase 7).

**Phase 5 as-built record — HISTORICAL (June 26, 2026 snapshot; superseded):**
The `musicContext` single-field model described below was replaced by `RunContext` with an
explicit `MusicMode` (MUSIC / NO_MUSIC / not recorded) plus optional note, persisted
separately, in RunStyle V1 (July 10, 2026). This section is preserved as a build record
only — the current music data model is documented in design_runstyle_v1.md and AI_AGENT.md.
Both features follow the same four-file pattern:
  Run.java -> add field | RunStorage.java -> add DB column | RunConsole.java -> add optional prompt | RunAgent.java -> add to user message

Step 1 — Music (manual input now, Spotify integration in mobile phase): **BUILT June 26, 2026**
- `musicContext` String field on Run.java (constructor param, last position; getter `getMusicContext()`; no setter — collected at log time like routeName)
- `music_context` column in RunStorage.java INSERT (11th `?`) and read in loadRuns via `rs.getString("music_context")`
- Optional "What were you listening to? (optional):" prompt in RunConsole.logRun(), stored null if skipped
- "Music:" line added to RunAgent.buildUserMessage(); music rule added to SYSTEM_PROMPT (reference artist/song only when it genuinely fits — never force)
- AI_AGENT.md data contract updated with the Music line
- The MySQL `music_context` migration was applied before the successful Phase 5 live verification.

**Future refinement — music reference variety (user note, June 26, 2026):**
When automation arrives (Spotify) and a runner plays the same song often, the agent must NOT keep
referencing that same song every run. Need variety logic — e.g. vary which track/artist gets called out,
or weight toward less-recently-mentioned songs — so references stay fresh. Belongs in Phase 5 advanced /
Phase 6 lyric-aware work, where the agent gains cross-run awareness. Not needed for manual Step 1.

Step 2 — Weather (automatic via Open-Meteo): **✅ SHIPPED. Cleanup completed July 6–7, 2026.**
Built June 26, 2026, then reconciled to full spec in the Step 2.5 cleanup (commits 8fa05c9 + 9ad3376),
with the TX→Texas geocoding fix following July 7. `design_weather_cleanup.md` is now the historical
build record; original spec is `design_weather_context.md`. What shipped:
- Fetch happens ONCE at log time and is STORED on the Run (persistence bug fixed — weather was lost before).
- Isolated `WeatherService` class owns geocoding + the Open-Meteo call (SRP, like RunAgent).
- `WeatherData` value object holds three fields: `temperature`, `apparentTemperature` ("feels-like"),
  `weatherCondition` — nullable `Double`, so 0.0 is a real temperature and null means "not recorded."
- Forecast API (today + ~92 days) instead of the archive API, so fresh runs get data.
- HttpClient connect + request timeout (5s) so a slow API never hangs the console.
- Failure never blocks logging — WeatherData stays all-null, the agent shows "Not available."
- State-based geocoding disambiguation now works: WeatherService canonicalizes abbreviations
  (`STATE_NAMES` map + `normalizeState`) so "TX" matches Open-Meteo's "Texas".
- Privacy doc written: `docs/DATA_PRIVACY.md`. AI_AGENT.md data contract updated.

**Constructor design decision — RESOLVED & BUILT (July 6, 2026):**
Chose Option B — a `WeatherData` value object groups temperature/apparentTemperature/weatherCondition
into one constructor param (composition) instead of appending 3 raw params. Shipped as `WeatherData.java`.

**Visual prototype:**
RunState_intro.html and RunState Promo.html exist in project root — both gitignored (local only).
Do NOT use as final look yet — intentionally unfinished.
Colors, branding, and button hierarchy are strong. Runner animation unfinished.

---

## Handoff — stabilization sprint complete (July 10, 2026)

**Where we are:** A four-task stabilization sprint shipped, each as its own commit, before
starting the Run Style redesign. All four are done and verified:

1. **Privacy doc ↔ code alignment.** `RunAgent.buildUserMessage()` no longer sends the runner's
   real username (a constant `"Runner"` label stands in); `DATA_PRIVACY.md` updated to match and
   its stale "rolling average pace/distance" bullet replaced with the candidate-based comparison
   summary that actually gets sent.
2. **RunAgent HTTP timeouts.** Added a reusable static `CLIENT` with a 5s connect timeout plus a
   5s per-request timeout (mirrors WeatherService). Verified a timeout throws an `Exception`
   subclass that the existing `catch` already handles → falls back instead of hanging.
3. **DB password out of source.** `RunStorage` reads `RUNSTATE_DB_PASSWORD` from the environment
   (same pattern as `ANTHROPIC_API_KEY`); throws a clear `SQLException` if unset. Still uses the
   dedicated `runstate_user`, never root. **Run-environment note:** the IntelliJ run config (and
   any shell that runs the app) must define `RUNSTATE_DB_PASSWORD` or the DB connection fails.
4. **First unit tests.** JUnit 5 (Jupiter) added, `test` scope. Tests live in a top-level `test/`
   dir (kept out of `src/` because the pom points `sourceDirectory` at `src`; set via
   `<testSourceDirectory>test</testSourceDirectory>`). `ComparisonServiceTest` has 9 tests
   covering candidate selection (recency window, cap-10, route-first, distance fallback), median
   aggregation (odd/even, outlier resistance vs mean), and the negative pre-filter (lower lift and
   unexplained higher effort filtered; explained PR effort allowed). Run with `mvn test` or the
   IntelliJ ▶ gutter arrows. Tested through the public `analyze(...)` only — internals stay private.

**Historical next step at this handoff — COMPLETED July 10, 2026:** the Run Style redesign
described here was subsequently built and verified. The current resume point is the music-work
section near the top of this document.

**Security-milestone reminder (unchanged):** rotating the DB password to a strong, unique value
belongs in the pre-release security pass — not the local-dev "basic" password used now.

---

## Handoff — next session entry point (July 7, 2026)

**Where we are:** Phase 5 is functionally complete. Step 1 (music) and Step 2 (weather) are both
built, committed, and pushed. The weather cleanup (Step 2.5) and the TX→Texas geocoding fix shipped
July 6–7, 2026 and are verified end-to-end. Privacy doc is done. No open weather work remains.
Manley is taking a break here to review the whole app before building deeper.

**Historical queue at this handoff — SUPERSEDED:** Effort Cost, Comparison Repair, stabilization,
and RunStyle V1 were completed after this note was written. This handoff also described the whole
music reply-style task as prompt-only; the corrected design separates its prompt-only craft rules
from its persistence-dependent cross-run frequency mechanism. The current resume point is the
music-work section near the top of this document.

**July 6, 2026 — UI phase paused; back to backend.**
UI/creative-direction exploration is paused (see creative_direction_ui.md v0.2 — §0 has the
locked July decisions reconciled from the prompt-iteration sessions). Moodboard is gitignored
(local only, like the HTML prototypes); creative_direction_ui.md is the surviving text record.
Historical focus at that time: the weather cleanup above, which has since shipped.
RESOLVED July 27, 2026 (was an open three-vs-four planning decision at this July 6 handoff):
the shared three-level energy domain is retained and the four-state State Scan sketch is
superseded as a domain proposal. Later UI work may refine labels and presentation without
changing stored meanings. Decision record: `design_music_intelligence_v1.md`.

**Current weather architecture (post-cleanup, for reference):**
- `WeatherData.java` — immutable value object (nullable `Double` temperature/apparentTemperature,
  String weatherCondition; final fields, no setters).
- `WeatherService.java` — `static WeatherData fetch(city, state, date)`; owns geocoding (forecast API),
  WMO code decoding, timeouts, and TX→Texas state canonicalization (`STATE_NAMES` + `normalizeState`).
- `Run.java` — one grouped `WeatherData` constructor parameter followed by `EffortLevel`;
  null-safe delegating weather getters.
- `RunConsole.logRun()` — fetches weather BEFORE saveRun so it persists.
- `RunStorage.java` — the current INSERT has 19 columns, including `apparent_temperature` and
  the later RunContext/effort fields; loadRuns reads the three weather columns via `rs.getObject`
  into a `WeatherData`.
- `RunAgent.java` — no longer fetches weather; `describeWeather(run)` formats the stored values.
`design_weather_context.md` still holds the original spec (WMO mapping, API URLs) for reference.
Note: `Runner.getCity()/getState()` exist (added June 26).

**Collab rules to follow:**
- One decision or one file at a time — never dump a full plan in one response
- Explain Java/OOP concepts as you build (value object, nullable Double, setObject vs setDouble, etc.)
- Present options with a recommendation; user decides before code is written
- Flag when it's a good time to commit
