---
name: project-current-state
description: "Current development state of RunState — what's built, what's next, and key decisions locked in"
metadata:
  type: project
---

As of July 30, 2026, the app is a working Java console app named **RunState**.

**Completed phases:**
- Phase 1: Console app — energy system, opening prompt, post-run responses, rolling averages
- Phase 2: MySQL persistence — runs save and load between sessions (`RunStorage.java`, `runstate` DB schema)
- Phase 3: Your Run Style — original pattern detection (`detectRunStyle()` in Runner.java). SUPERSEDED by RunStyle V1, July 10, 2026 (see below).
- Phase 4: AI agent — `RunAgent.java` replaces `buildRunResponse()` with Anthropic API call (claude-haiku-4-5-20251001). Fallback to hardcoded logic on any failure. Gson added for JSON parsing. `Run.java` has `getRunner()` getter added.
- Phase 5 (Steps 1–2): AI context expansion — music context (manual input) + weather (Open-Meteo, `WeatherData` value object, persisted at log time). Built June 26; weather cleanup + TX→Texas fix shipped July 6–7, 2026.
- Effort Cost V1: post-run "How did that run land?" input — `EffortLevel` enum (Smooth/Working/Heavy/Empty tank + internal RPE ranges), persisted at log time, shown in run history and fed to the agent (prompt line + SYSTEM_PROMPT rule + offline fallback). Built July 8–9, 2026 (collector commit 8981b48 + display/agent follow-up).
- Comparison Repair V1: candidate-based run comparison (`ComparisonService` + `ComparisonInsight`) — 180-day/cap-10 selection (route-first, distance-fallback), median aggregation, four positive signals behind a negative pre-filter, and July 25 signal-specific energy/effort evidence pools and confidence — replaces the blended 20-run rolling-average flaw in the AI prompt + fallback. `detectRunStyle()` was later replaced by RunStyle V1. Built July 9 and refined July 25, 2026.

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

**Current resume point — Music Intelligence V1 REVISED PROMPT IMPLEMENTED — NEXT QUALITY SMOKE PENDING:**
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
- **The deterministic Maven gate is PASSING: 256 tests, 0 failures, 0 errors, 0 skipped**
  (clean July 30 run). It verifies transport, placement, prompt content, evaluation-runner
  safety, and fixture integrity — **not** final model quality.
- **Active design, AI-agent, evaluation, privacy, roadmap, and status documents were reconciled
  July 30.** The creative revision sends no new data and adds no provider, so the privacy
  disclosure itself did not change; its prompt-source cross-reference was corrected.
- **Evaluation is underway, not complete.** Sanitized fixtures and the opt-in runner exist.
  One authentication-invalid launch produced fallback text only; the first valid 12-call smoke
  completed with zero fallbacks but failed product quality. The prompt was revised in response.
  **No revised-prompt smoke and no final run have occurred. Combined Music Intelligence V1 is
  NOT complete.**
- **Energy domain — CLOSED.** The shared LOW/MODERATE/HIGH pre/post energy domain is retained.
  The four-state State Scan sketch is **superseded as a domain proposal**; later UI work may
  refine labels and presentation without changing stored meanings.
- **Next task when Manley resumes:** explicitly approve and run one fresh 12-call smoke against
  the revised prompt. Review it as a diagnostic. If it is sound, separately approve the
  36-output final evaluation, then reconcile independent review and Manley's final decision.
  Every live mode keeps its own approval gate.
- The future `REFERENCED / NOT_REFERENCED / UNKNOWN` reply-outcome boundary is **documented**
  in the canonical plan, but cross-run reference-frequency implementation (schema, rolling
  window, detection, prompt line) remains **deferred** — it is not part of the prompt slice.
- **After COMBINED Music Intelligence V1 is complete** — implementation, deterministic
  verification, manual evaluation, independent review reconciliation, final documentation, and
  Manley's approval, as defined by the canonical plan; **not merely after the Java slice** —
  run the **Core Running Foundation Review** — LOCKED into
  the roadmap July 26, 2026 (Codex proposal, Manley confirmed). Tightly fenced: one question —
  is the central journey (record a run → preserve it safely → understand it → manage it → use
  it later) credible and structurally ready for a real interface? "Manage" (edit/delete a
  logged run) is the known thin spot. Output is a short gap list, not a feature hunt; "add
  every feature Strava has" is explicitly out.
- Then: resume UI design using `creative_direction_ui.md`. Let the
  State Scan, history, and post-run reply screens define the backend payloads.
- Then: design/migrate the Spring Boot API from those screen contracts.
- Then: build the mobile client and GPS/automatic-tracking layer against Spring Boot.
  The GPS phase delivers time-aligned run telemetry, and automatic splits are built there
  as a **general running capability** (one shared system; run screens and music intelligence
  both consume it — full decision record in parked_feature_ideas.md, July 26, 2026 entry).
- Spotify integration and live-DJ behavior remain later possibilities with separate legal,
  privacy, provider, and platform dependencies.

*(Resume point rewritten July 30, 2026 after the first valid smoke, approved creative-policy
revision, S11 fixture decontamination, and clean 256-test gate. The July 28
evaluation-not-started resume point is superseded.)*

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
  live location (Phase 6); run/energy data → Anthropic API (live now, covered in
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
