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
Summary of locked decisions (full detail + open decisions in design_weather_context.md):
- Fetch ONCE at log time and STORE on the Run (NOT live in RunAgent — supersedes the earlier note here).
- New isolated `WeatherService` class owns geocoding + the Open-Meteo call (SRP, like RunAgent).
- Three fields: temperature, apparentTemperature ("feels like" — captures humidity, the #2 factor), weatherCondition.
- Forecast API for recent runs (today + ~92 days); archive endpoint deferred. Failure never blocks logging.
- Open decisions before build: (1) append 3 params vs. a `WeatherData` value object [recommended]; (2) daily mean vs max reading.
- Competitive/RPE/privacy considerations captured in research_app_landscape.md.

**Visual prototype:**
RunState_intro.html and RunState Promo.html exist in project root — both gitignored (local only).
Do NOT use as final look yet — intentionally unfinished.
Colors, branding, and button hierarchy are strong. Runner animation unfinished.

---

**Handoff notes for Cowork — Phase 5**

Start here when picking up: Phase 5, Step 1 (music field). Everything below is what you need to design against.

**Key files to read before designing:**
- `src/com/runstate/Run.java` — data model, constructor signature, existing fields
- `src/com/runstate/RunStorage.java` — DB save/load pattern, existing SQL columns
- `src/com/runstate/RunConsole.java` — where the optional prompt goes (inside logRun())
- `src/com/runstate/RunAgent.java` — buildUserMessage() is where new fields get added to the API context
- `docs/AI_AGENT.md` — the data contract section shows the current user message format

**Things Cowork can't know without reading the files:**
- Run constructor takes 12 parameters in a specific order — new fields must go at the end
- RunStorage uses a specific INSERT/SELECT column order — new columns must be added consistently
- Runner already has city and state fields (needed for weather geocoding in Step 2)
- RunAgent.buildUserMessage() builds the user message as a String concat — new lines append to the end

**Workflow:**
Cowork designs the plan → user brings it to Claude Code → Claude Code verifies against real files before touching anything → build one file at a time.
Never execute a plan in Claude Code without that verification step first.

**Go-forward workflow (locked June 26, 2026):**
For each MAJOR task: plan and design fully in Cowork, THEN hand off to Claude Code before any code is written.
(Phase 5 Step 1 / music was an exception — designed AND built in Cowork by user request. Going forward,
major tasks are plan-here, build-there.)
