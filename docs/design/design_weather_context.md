---
name: design-weather-context
description: "Phase 5 Step 2 design — automatic weather context for runs via Open-Meteo. Locked decisions, open decisions, and file-by-file build plan."
metadata:
  type: project
---

# Phase 5, Step 2 — Weather context (✅ SHIPPED July 6–7, 2026 — kept as reference spec)

> As-built reality: see `design_weather_cleanup.md` (STATUS: BUILT & VERIFIED) and the
> "Current weather architecture" section of `project_current_state.md`. This file remains
> useful for the WMO code mapping, API URLs, and original decision reasoning.
> The RPE note at the bottom has since become a real design: `design_effort_cost.md`.

Goal: give the AI agent (`RunAgent`) weather context for each run so post-run responses can
reference real conditions ("five miles in that humidity is a different kind of effort").
Weather is derived automatically — the runner never types it.

This doc supersedes the older one-line note in `project_current_state.md` that said
"fetch weather in RunAgent.java." See **Architecture** below for why that changed.

---

## Locked decisions

1. **Source: Open-Meteo.** Free, no API key, no signup (non-commercial, <=10k calls/day). Two
   endpoints used: a geocoding endpoint (city -> lat/lon) and a weather endpoint.
2. **Automatic, no console prompt.** Derived from the runner's `city`/`state` (already on `Runner`)
   plus the run `date`.
3. **Architecture = fetch once at log time, store on the Run (Option B).** This matches how real
   apps (Strava, Garmin) work — weather is captured at the time of the run and stored ON the
   activity, never re-derived on each view. So weather is fetched during `logRun()`, written to the
   `Run` object and the DB, and `RunAgent` simply reads it back. It is NOT fetched live inside
   RunAgent.
4. **Three fields captured** (decision confirmed June 26, 2026):
   - `temperature` (double) — air temperature
   - `apparentTemperature` (double) — "feels like"; folds in humidity + wind
   - `weatherCondition` (String) — plain-English condition mapped from the WMO code
   Rationale: research ranks temperature ~40% and humidity ~26% of weather's effect on running.
   Plain air temp alone misses humidity — the variable that makes the agent's story real. Open-Meteo's
   apparent temperature captures it in one number.
5. **Units: Fahrenheit** (US runner). Request `temperature_unit=fahrenheit`.
6. **Granularity: daily.** We only have a date (no run start-time), so we request DAILY values, not
   hourly. This is also why "weather changed during the run" is intentionally out of scope here —
   see Phase 6 deferrals.
7. **Endpoint scope for v1: Forecast API only.** It serves recent past (today + up to ~92 days back)
   with no data gap. The Historical/Archive (ERA5) API is gap-free back to 1940 BUT lags ~5 days, so
   it is wrong for a run logged today. Old backdated runs (>~92 days) are treated as "weather not
   recorded" for now; wiring the archive endpoint is deferred.
8. **Failure never blocks logging.** Any failure (geocoding, network, timeout, bad response) leaves
   all three weather fields null. The run still logs and saves. The agent omits the weather line,
   exactly like a skipped music value. Use an `HttpClient` timeout so a slow API never hangs the console.

---

## Open decisions (need a call before building)

1. **Constructor growth vs. value object.** After music, `Run`'s constructor has 13 params. Adding
   3 weather fields makes it 16 — a real code smell.
   - Option A: keep appending the 3 fields as params (simple, consistent with what we've done).
   - Option B (recommended): group the 3 weather fields into a small `WeatherData` value object
     (composition) and add ONE param. Cleaner, caps the bloat, and is a good OOP teaching moment
     (the user is building composition confidence). Costs one new small class + a slightly different
     DB mapping.
   Decide this first — it changes the constructor signature and the storage code.
2. **Representative daily value.** Use the day's MEAN (`temperature_2m_mean` / `apparent_temperature_mean`)
   as the stored reading (recommended — least misleading), vs. the day's MAX (more evocative "heat"
   framing). Minor; mean is the safer default.

---

## Data flow

```
logRun()
  -> WeatherService.fetch(city, state, date)        // new class, isolates all HTTP/JSON
       -> geocode city/state  -> lat, lon
       -> forecast call (daily, start=end=date)     -> temp, feelsLike, weatherCode
       -> map weatherCode -> condition string
       -> returns {temperature, apparentTemperature, weatherCondition} OR all-null on any failure
  -> new Run(... , weather fields/object)            // stored on the Run
  -> RunStorage.saveRun(run)                          // persisted to DB
...later, when building the response...
  -> RunAgent.buildUserMessage() reads run.getTemperature()/getApparentTemperature()/getWeatherCondition()
     and adds a "Weather:" line; SYSTEM_PROMPT gets a rule to use it only when it genuinely fits.
```

Note the SRP parallel: just as `RunAgent` isolates the AI API call, a new `WeatherService` isolates
the weather API call. Keep all HTTP/geocoding/JSON parsing in `WeatherService`, not in `RunConsole`.

---

## Open-Meteo request details (verify exact field names against live docs at build time)

Geocoding:
```
GET https://geocoding-api.open-meteo.com/v1/search?name={city}&count=1&language=en&format=json
-> results[0].latitude, results[0].longitude   (first match; ambiguity risk acceptable for v1)
```

Weather (Forecast API, recent dates):
```
GET https://api.open-meteo.com/v1/forecast
    ?latitude={lat}&longitude={lon}
    &daily=weather_code,temperature_2m_mean,apparent_temperature_mean
    &temperature_unit=fahrenheit&timezone=auto
    &start_date={date}&end_date={date}
-> daily.weather_code[0], daily.temperature_2m_mean[0], daily.apparent_temperature_mean[0]
```

Reuses Phase 4 machinery: `java.net.http.HttpClient` + gson `JsonParser`. Only new concept is the
two-step call (geocode then weather) and reading values out of the `daily` arrays.

WMO weather_code -> condition string (simplified grouping for the helper):
```
0          -> Clear
1          -> Mainly clear
2          -> Partly cloudy
3          -> Overcast
45,48      -> Fog
51,53,55   -> Drizzle
56,57      -> Freezing drizzle
61,63,65   -> Rain
66,67      -> Freezing rain
71,73,75   -> Snow
77         -> Snow grains
80,81,82   -> Rain showers
85,86      -> Snow showers
95         -> Thunderstorm
96,99      -> Thunderstorm with hail
```

---

## File-by-file build plan (one file at a time; verify against real files first)

Assumes Open decision #1 resolves to a value object; adapt if "append params" is chosen.

1. **`WeatherData.java` (new, if value-object option)** — small immutable holder for
   `temperature`, `apparentTemperature`, `weatherCondition`; getters; nulls allowed.
2. **`WeatherService.java` (new)** — `static WeatherData fetch(String city, String state, LocalDate date)`.
   Does geocoding + forecast call + WMO mapping. Returns an all-null `WeatherData` on any exception.
   Owns the `HttpClient` timeout. All HTTP/JSON lives here.
3. **`Run.java`** — add the weather field(s) (one `WeatherData` param, or three primitives), assign in
   constructor (new params LAST), add getters. No setters (known at log time, like route/music).
4. **`RunStorage.java`** — add `temperature`, `apparent_temperature`, `weather_condition` columns to the
   INSERT (`?` placeholders + setters) and read them in `loadRuns()`; pass into the `Run` constructor.
   DB migration required (see below).
5. **`RunConsole.logRun()`** — after collecting run details, call `WeatherService.fetch(...)` and pass
   the result into `new Run(...)`. No prompt. Silent and non-blocking.
6. **`RunAgent.java`** — add a `Weather:` line to `buildUserMessage()` (temp / feels-like / condition,
   or "Not recorded"); add a SYSTEM_PROMPT rule: reference weather only when it genuinely fits the run
   (effort, energy, the story) — never force it. (Same pattern proven with music.)
7. **`docs/AI_AGENT.md`** — add the `Weather:` line to the data-contract section.

DB migration (run manually on the MySQL `runstate` DB before first use):
```sql
ALTER TABLE runs
  ADD COLUMN temperature DOUBLE NULL,
  ADD COLUMN apparent_temperature DOUBLE NULL,
  ADD COLUMN weather_condition VARCHAR(64) NULL;
```

---

## Phase 6 deferrals (filed, do NOT build now)

- Intra-run weather change / time-aware sampling — needs run start-time + continuous sampling, which
  only exist with GPS/live tracking.
- On-device temperature sensors (Garmin-style).
- Humidity-adjusted pace INSIGHTS ("your hot-humid runs run ~X sec/mile slower") — an analytics layer,
  not just context.
- Archive (ERA5) endpoint for old backdated runs.
- Optional run start-time capture (would improve weather accuracy; deferred to keep scope tight).

---

## Privacy note — DONE (June 26, 2026)

`docs/DATA_PRIVACY.md` created. Covers what data leaves the app, to whom (Anthropic API, Open-Meteo),
and why. `docs/AI_AGENT.md` data contract section updated to reference it and show the Weather line.
Both committed. No further action needed before building.

---

## RPE / effort-load insight (filed for future — do NOT build now)

The user's energy 1–3 scale is a friendlier version of RPE (Rate of Perceived Exertion). Research
finding: `effort × duration` is a recognized session-RPE training-load metric. Using existing data
(energy level + duration), this could become a simple "effort load" number — a meaningful analytic
squarely in the "learn something from your run" mission. Worth revisiting in a later phase once the
weather and current backlog features are shipped. Not scope for now.
