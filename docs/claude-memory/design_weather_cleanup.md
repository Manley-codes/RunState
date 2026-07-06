---
name: design-weather-cleanup
description: "Phase 5 Step 2.5 — weather refactor plan: fix persistence bug + forecast API, extract WeatherService, add WeatherData value object. Locked July 6, 2026."
metadata:
  type: project
---

# Phase 5, Step 2.5 — Weather cleanup (PLANNED, NOT BUILT)

The weather feature was built June 26, 2026 (commits f283791, 18bac99, 6033913) but drifted
from the locked design in design_weather_context.md. This doc is the reconciliation plan.
Scope decision (July 6, 2026): FULL spec alignment, with the WeatherData value object.

## The two bugs (why this matters)

1. **Weather is never persisted.** RunConsole.logRun() calls RunStorage.saveRun() at line ~132,
   but weather isn't fetched until RunAgent.buildRunResponse() at line ~135. Every DB row gets
   temperature=0.0, weather_condition=NULL. Weather exists in memory for one response, then is lost.
2. **Wrong endpoint.** Code calls the archive API (archive-api.open-meteo.com), which lags ~5 days.
   A run logged today has no data yet → silent failure → "Not available". The design chose the
   forecast API (api.open-meteo.com/v1/forecast) precisely because it covers today + ~92 days back.

## Design drift being corrected

- ~90 lines of HTTP/geocode/JSON live in RunAgent.java → extract to new WeatherService (SRP)
- apparentTemperature (feels-like) missing → add (humidity is the point of the feature)
- primitive double temperature, 0.0 = "unavailable" → nullable Double (0.0 is a real temp)
- setTemperature()/setWeatherCondition() setters on Run → remove; immutable like musicContext
- No HttpClient timeout → add (slow API must never hang the console)
- temperature_2m_max → temperature_2m_mean (open decision #2: mean, least misleading)
- Geocoding uses count=1 city-only → ambiguity risk (wrong Springfield); use state to disambiguate

## Locked decisions (July 6, 2026)

- Full spec alignment in one coherent refactor (not bugs-only)
- Open decision #1 RESOLVED: **WeatherData value object** (Option B) — one immutable class
  holding temperature, apparentTemperature, weatherCondition; Run takes ONE param (composition)
- Run keeps delegating getters (getTemperature() etc.) so RunAgent's read path barely changes
- Signature: `static WeatherData fetch(String city, String state, LocalDate date)` — city AND state
- Geocoding disambiguation: request `count=5` candidates, filter results by `admin1` field
  matching the runner's state (case-insensitive), fall back to first result if no match.
  NOTE: Open-Meteo's geocoding API has NO state query parameter — admin1 filtering is the
  correct approach; do not append a state param to the URL.
- All other decisions carry over unchanged from design_weather_context.md

## Verify FIRST, before touching any code (sessions have lost cross-session context)

1. `git status` — commit or intentionally set aside any uncommitted work before starting
2. `SHOW COLUMNS FROM runs;` on the MySQL runstate DB — confirm which weather columns exist.
   Expected present: temperature, weather_condition. Expected missing: apparent_temperature.
3. Re-read the current state of all 5 source files listed below — do not trust this doc's
   line numbers blindly; other sessions may have moved things.

## DB migration (manual, before the Java changes)

```sql
ALTER TABLE runs ADD COLUMN apparent_temperature DOUBLE NULL;
```
(temperature and weather_condition already exist per the June 26 build — verify in step 2 above.)
Existing rows keep NULL/0.0 weather — acceptable; treat as "not recorded."

## Build order (ONE file at a time, commit points marked, Manley approves EACH step)

1. **WeatherData.java (new)** — immutable value object: three final fields (Double, Double,
   String), constructor, getters, no setters. Teaching: value object, wrapper vs primitive.
2. **WeatherService.java (new)** — `static WeatherData fetch(String city, String state, LocalDate date)`.
   Move geocoding + WMO mapping out of RunAgent; switch to forecast API with
   daily=weather_code,temperature_2m_mean,apparent_temperature_mean; admin1-based state
   disambiguation (see locked decisions); add HttpClient.connectTimeout + request timeout (~5s).
   ANY failure returns all-null WeatherData.
   → commit ("Add WeatherData and WeatherService")
3. **Run.java** — replace temperature/weatherCondition fields with one WeatherData field;
   constructor param (last position); delete the two setters; keep/add delegating getters
   returning nulls safely when WeatherData is null. Teaching: composition, delegation.
4. **RunConsole.logRun()** — call WeatherService.fetch() after date is read, pass result into
   new Run(...). BEFORE saveRun — this is the bug fix. No console prompt.
5. **RunStorage.java** — INSERT gains apparent_temperature (setObject for nullable Doubles);
   loadRuns() reads three columns into a WeatherData and passes ONE arg to the constructor.
   Teaching: setObject vs setDouble, rs.getObject for nullable reads.
   → commit ("Persist weather at log time via WeatherData")
6. **RunAgent.java** — DELETE fetchWeather/geocodeCity/fetchWeatherForDate/decodeWeatherCode
   and the fetchWeather(run) call; buildUserMessage() weather line now includes feels-like.
   → commit ("Extract weather out of RunAgent")
7. **docs/AI_AGENT.md** — update Weather line in the data contract (feels-like added).
8. Manual test: log a run dated today → weather appears AND survives restart/reload.

## Out of scope (unchanged deferrals)

Archive endpoint for old backdated runs, hourly granularity, run start-time capture,
humidity-pace insights — all remain Phase 6 (see design_weather_context.md).
