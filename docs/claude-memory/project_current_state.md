---
name: project-current-state
description: "Current development state of RunState — what's built, what's next, and key decisions locked in"
metadata:
  type: project
---

As of June 25, 2026, the app is a working Java console app named **RunState**.

**Completed phases:**
- Phase 1: Console app — energy system, opening prompt, post-run responses, rolling averages
- Phase 2: MySQL persistence — runs save and load between sessions (`RunStorage.java`, `runstate` DB schema)
- Phase 3: Your Run Style — pattern detection (`detectRunStyle()` in Runner.java, wired into RunConsole.logRun())
- Phase 4: AI agent — `RunAgent.java` replaces `buildRunResponse()` with Anthropic API call (claude-haiku-4-5-20251001). Fallback to hardcoded logic on any failure. Gson added for JSON parsing. `Run.java` has `getRunner()` getter added.

**Rename completed June 22, 2026:**
- Package renamed from `com.runclubapp` to `com.runstate` — all 7 source files updated
- `pom.xml` groupId → `com.runstate`, artifactId → `RunState`
- IntelliJ module name still shows "RunNet" — cosmetic only, fix via right-click → Rename in Project panel

**Your Run Style — locked-in design (built):**
- Minimum 11 total runs (10 previous) before anything fires
- Layer 1: pace AND distance both above 20-run rolling average
- Layer 2: post-run energy MODERATE or HIGH
- Consistency gate: 4+ of last 10 previous runs also qualify (threshold grows: 4/10 at 11-20 runs, 5/10 at 21-30, 6/10 at 31+)
- Layer 3: LOW→HIGH pre/post always adds a bonus line
- Named styles (Morning Charger etc.) deferred to mobile phase — no time-of-day data yet

**Next steps (in order):**
1. Phase 5: AI agent context expansion — music first, then weather (locked in June 25, 2026)
2. Mobile UI — futuristic/warm transition concept, GPS tracking

**Phase 5 plan (locked in, ready to build):**
Both features follow the same four-file pattern:
  Run.java -> add field | RunStorage.java -> add DB column | RunConsole.java -> add optional prompt | RunAgent.java -> add to user message

Step 1 — Music (manual input now, Spotify integration in mobile phase): **BUILT June 26, 2026**
- `musicContext` String field on Run.java (constructor param, last position; getter `getMusicContext()`; no setter — collected at log time like routeName)
- `music_context` column in RunStorage.java INSERT (11th `?`) and read in loadRuns via `rs.getString("music_context")`
- Optional "What were you listening to? (optional):" prompt in RunConsole.logRun(), stored null if skipped
- "Music:" line added to RunAgent.buildUserMessage(); music rule added to SYSTEM_PROMPT (reference artist/song only when it genuinely fits — never force)
- AI_AGENT.md data contract updated with the Music line
- DB MIGRATION REQUIRED on the user's MySQL: `ALTER TABLE runs ADD COLUMN music_context VARCHAR(255) NULL;` (run manually — saveRun will fail until this exists)

**Future refinement — music reference variety (user note, June 26, 2026):**
When automation arrives (Spotify) and a runner plays the same song often, the agent must NOT keep
referencing that same song every run. Need variety logic — e.g. vary which track/artist gets called out,
or weight toward less-recently-mentioned songs — so references stay fresh. Belongs in Phase 5 advanced /
Phase 6 lyric-aware work, where the agent gains cross-run awareness. Not needed for manual Step 1.

Step 2 — Weather (automatic via Open-Meteo): **BUILT June 26, 2026 — but with deviations from spec.**
Two bugs (weather never persisted to DB; archive API instead of forecast, so fresh runs get nothing)
plus design drift (no WeatherService, no apparentTemperature, primitive double, setters, no timeout).
**See design_weather_cleanup.md for the reconciliation plan, locked July 6, 2026.**
Original spec: design_weather_context.md.
Summary of locked decisions (full detail in design_weather_context.md):
- Fetch ONCE at log time and STORE on the Run (not live in RunAgent).
- New isolated `WeatherService` class owns geocoding + the Open-Meteo call (SRP, like RunAgent).
- Three fields: `temperature`, `apparentTemperature` ("feels-like" — folds in humidity), `weatherCondition`.
- Forecast API for recent runs (today + ~92 days); archive endpoint deferred.
- HttpClient timeout required — a slow API must never hang the console.
- Failure never blocks logging — all three fields stay null, agent omits the weather line.
- Privacy doc written: `docs/DATA_PRIVACY.md`. AI_AGENT.md data contract updated.

**One open decision remaining before writing any code:**
Constructor design — after music, Run has 13 params. Adding 3 weather fields makes 16.
  - Option A: append 3 raw params (simple, consistent)
  - Option B (recommended): group into a `WeatherData` value object — one param, cleaner, good OOP lesson
  Resolve this FIRST in the next session. Present to user and get their call before touching any file.

**Visual prototype:**
RunState_intro.html and RunState Promo.html exist in project root — both gitignored (local only).
Do NOT use as final look yet — intentionally unfinished.
Colors, branding, and button hierarchy are strong. Runner animation unfinished.

---

## Handoff — next session entry point (July 6, 2026)

**Where we are:** Phase 5 Step 1 (music) is built and committed. Phase 5 Step 2 (weather) is BUILT
but deviates from spec — see design_weather_cleanup.md. Privacy doc is done. No open decisions remain.

**First thing to do in the next session:**
WeatherData value object decision RESOLVED (July 6, 2026 — value object chosen). Next backend
session: follow `design_weather_cleanup.md` — verify git status and DB columns FIRST, then build
one file at a time with Manley's approval at each step.

**July 6, 2026 — UI phase paused; back to backend.**
UI/creative-direction exploration is paused (see creative_direction_ui.md v0.2 — §0 has the
locked July decisions reconciled from the prompt-iteration sessions). Moodboard is gitignored
(local only, like the HTML prototypes); creative_direction_ui.md is the surviving text record.
Current focus: the weather cleanup above.
NOTE FOR LATER (not a current focus): the UI "State Scan" concept implies FOUR pre-run energy
states, but the backend energy system is THREE levels. Open question — resolve when UI work
resumes, before Phase 6. Do not change the backend enum for it now.

**Build order:** superseded — follow the build order in `design_weather_cleanup.md` (8 steps,
WeatherData.java first, commit points marked). The list that used to live here described the
original from-scratch build and no longer matches the code.
Note: Runner.getCity()/getState() already exist (added June 26) — no Runner.java step needed.

**Key files to read before touching any code:** the 5 source files listed in
`design_weather_cleanup.md`, plus that doc itself and `design_weather_context.md` (original spec —
WMO mapping and API URLs still live there). Facts that changed since the old list was written:
Run's constructor is now 15 params (music + temperature + weatherCondition), RunStorage INSERT
has 13 columns, and RunAgent already has a Weather line + ~90 lines of weather fetch code that
the cleanup will remove.

**Collab rules to follow:**
- One decision or one file at a time — never dump a full plan in one response
- Explain Java/OOP concepts as you build (value object, nullable Double, setObject vs setDouble, etc.)
- Present options with a recommendation; user decides before code is written
- Flag when it's a good time to commit
