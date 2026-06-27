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

Step 2 — Weather (automatic via Open-Meteo): **DESIGNED June 26, 2026 — see design_weather_context.md for the full spec.**
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

## Handoff — next session entry point (June 26, 2026)

**Where we are:** Phase 5 Step 1 (music) is built and committed. Phase 5 Step 2 (weather) is fully
designed and ready to build. Privacy doc is done. One open decision remains before writing any code.

**First thing to do in the next session:**
Present open decision #1 from `design_weather_context.md` — WeatherData value object vs. raw params.
Give the recommendation (value object), explain the OOP concept, get the user's call. Then start building.

**Build order after the decision is made:**
1. MySQL ALTER TABLE (run manually before any Java — 3 new columns)
2. Runner.java — add `getCity()` and `getState()` getters (fields exist, getters are missing)
3. WeatherData.java (new) — only if value object option is chosen
4. WeatherService.java (new) — geocoding + forecast call + WMO mapping, HttpClient timeout, all failures return null
5. Run.java — add weather field(s), constructor param(s), getters
6. RunStorage.java — extend INSERT and loadRuns() for the 3 new columns
7. RunConsole.java — call WeatherService.fetch() after date is read, pass result to Run constructor
8. RunAgent.java — add Weather line to buildUserMessage(), add weather rule to SYSTEM_PROMPT

**Key files to read before touching any code:**
- `src/com/runstate/Run.java` — current constructor is 13 params
- `src/com/runstate/Runner.java` — has city/state fields, no getters yet
- `src/com/runstate/RunStorage.java` — INSERT has 11 columns currently
- `src/com/runstate/RunConsole.java` — logRun() is where the fetch call goes, after readRunDate()
- `src/com/runstate/RunAgent.java` — buildUserMessage() and SYSTEM_PROMPT both need a weather addition
- `docs/claude-memory/design_weather_context.md` — full spec, locked decisions, WMO mapping, API URLs

**Collab rules to follow:**
- One decision or one file at a time — never dump a full plan in one response
- Explain Java/OOP concepts as you build (value object, nullable Double, setObject vs setDouble, etc.)
- Present options with a recommendation; user decides before code is written
- Flag when it's a good time to commit
