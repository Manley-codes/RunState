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
- Phase 5 (Steps 1–2): AI context expansion — music context (manual input) + weather (Open-Meteo, `WeatherData` value object, persisted at log time). Built June 26; weather cleanup + TX→Texas fix shipped July 6–7, 2026.

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
1. Phase 5 — DONE (music + weather shipped June 26–July 7, 2026)
2. Phase 6: lyric-aware music responses (Genius/Musixmatch) — scoped in AI_AGENT.md + design_music_reply_style.md
3. Phase 5.5 (parked, not blocking): comparison-logic fix — see design_comparison_logic_fix.md
4. Mobile UI — futuristic/warm transition concept, GPS tracking

**Standing milestone — privacy / security / legal pass (added July 6, 2026):**
Before RunState is released, shared publicly, or gains multi-user/all-day-listening features,
a dedicated pass is REQUIRED covering: data privacy (what leaves the app, to whom, why —
extend docs/DATA_PRIVACY.md), consent tiers (run music vs all-day listening vs any cross-user
data), security (API keys out of source, DB credentials, dedicated DB user not root), and
legal basics (third-party API terms of service — Anthropic, Open-Meteo, Spotify, Last.fm/
ListenBrainz; health-adjacent data handling). Grows in weight with each phase — mobile + GPS
location data raises the bar again. Not a current blocker; IS a release blocker.

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

## Handoff — next session entry point (July 7, 2026)

**Where we are:** Phase 5 is functionally complete. Step 1 (music) and Step 2 (weather) are both
built, committed, and pushed. The weather cleanup (Step 2.5) and the TX→Texas geocoding fix shipped
July 6–7, 2026 and are verified end-to-end. Privacy doc is done. No open weather work remains.
Manley is taking a break here to review the whole app before building deeper.

**First thing to do in the next session:**
Nothing weather-related is pending. The next backend thread is Phase 6 — lyric-aware music responses
(Genius/Musixmatch), scoped in `AI_AGENT.md` and `design_music_reply_style.md`. Also parked, not
blocking: the Phase 5.5 comparison-logic fix (`design_comparison_logic_fix.md`).

**July 6, 2026 — UI phase paused; back to backend.**
UI/creative-direction exploration is paused (see creative_direction_ui.md v0.2 — §0 has the
locked July decisions reconciled from the prompt-iteration sessions). Moodboard is gitignored
(local only, like the HTML prototypes); creative_direction_ui.md is the surviving text record.
Current focus: the weather cleanup above.
NOTE FOR LATER (not a current focus): the UI "State Scan" concept implies FOUR pre-run energy
states, but the backend energy system is THREE levels. Open question — resolve when UI work
resumes, before Phase 6. Do not change the backend enum for it now.

**Current weather architecture (post-cleanup, for reference):**
- `WeatherData.java` — immutable value object (nullable `Double` temperature/apparentTemperature,
  String weatherCondition; final fields, no setters).
- `WeatherService.java` — `static WeatherData fetch(city, state, date)`; owns geocoding (forecast API),
  WMO code decoding, timeouts, and TX→Texas state canonicalization (`STATE_NAMES` + `normalizeState`).
- `Run.java` — one `WeatherData` constructor param (last position); null-safe delegating getters.
- `RunConsole.logRun()` — fetches weather BEFORE saveRun so it persists.
- `RunStorage.java` — INSERT has 14 columns incl. `apparent_temperature` (setObject for nullable
  Doubles); loadRuns reads the three weather columns via `rs.getObject` into a `WeatherData`.
- `RunAgent.java` — no longer fetches weather; `describeWeather(run)` formats the stored values.
`design_weather_context.md` still holds the original spec (WMO mapping, API URLs) for reference.
Note: `Runner.getCity()/getState()` exist (added June 26).

**Collab rules to follow:**
- One decision or one file at a time — never dump a full plan in one response
- Explain Java/OOP concepts as you build (value object, nullable Double, setObject vs setDouble, etc.)
- Present options with a recommendation; user decides before code is written
- Flag when it's a good time to commit
