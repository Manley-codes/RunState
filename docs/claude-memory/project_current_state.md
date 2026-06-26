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

Step 1 — Music (manual input now, Spotify integration in mobile phase):
- Add musicContext String field to Run.java
- Add music_context column to DB in RunStorage.java
- Add optional "What were you listening to?" prompt in RunConsole.java
- Add music line to user message in RunAgent.java

Step 2 — Weather (automatic via Open-Meteo — free, no API key, supports historical dates):
- Add temperature (double) and weatherCondition (String) fields to Run.java
- Add columns to DB in RunStorage.java
- Fetch from Open-Meteo in RunAgent.java using Runner city/state + run date (no RunConsole prompt needed)
- Add weather line to user message in RunAgent.java

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
