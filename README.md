# RunState

A Java 17 console app for logging completed runs, tracking personal progress, and receiving
a contextual AI response after every saved run. Learning project and portfolio piece.

---

## What it does

You log a completed run through an interactive console menu. RunState stores it, checks for
personal records, and responds with a short AI-generated message grounded in what actually
happened — the energy shift, the effort, how it compares to similar past runs.

---

## Features

**Logging a run**
- Manually log a completed run: date, distance (miles or kilometers), duration, route
- Optional pre-run and post-run energy levels (I'm Here / Ready-ish / Let's Go! and
  Spent / Feeling Good / Powered Up)
- Optional post-run effort cost (Smooth → Empty tank)
- Optional run context: surface (road, trail, track, treadmill, mixed), company (solo or with
  others), shoe label, and explicit music state (Music / No music) with a free-text note

**Persistence and history**
- Runs save to MySQL between sessions — history is durable and load-protected
- Complete-history startup protection: a failed or malformed load exits cleanly rather than
  pretending history is empty
- Save-first durability: PRs, AI response, and RunStyle are only generated after the run is
  confirmed saved; a save failure prints a recovery receipt and ends the session
- Run history displayed newest-first as compact runner-friendly cards, including recorded
  context

**Personal records**
- Longest distance PR and fastest average pace PR tracked automatically
- Unit-normalized: miles and kilometers compared on a common scale
- PRs announced only after a successful save

**AI response**
- A short contextual response from the Anthropic API (claude-haiku-4-5-20251001) after every
  saved run — 2–3 sentences grounded in the actual run data
- Includes energy, effort, run context, music, automatic daily-mean weather, and a
  candidate-based comparison when matching past runs exist
- Local fallback response when the API is unavailable or the key is unset — logging never fails
- Five-second connection and request timeouts

**Weather**
- Automatic daily-mean weather (temperature, apparent temperature, condition) via Open-Meteo
  for the logged run date and runner location
- Fetched once at log time and stored with the run — no re-fetch, no blocking on failure

**Comparisons**
- Candidate-based: up to 10 comparable runs (same route or similar distance, last 180 days)
- Four positive/explanatory signals — State Lift, Quiet Gain, Same-Cost/Better, Demand
  Explained — each with its own evidence count and confidence tier
- Negative outcomes are filtered before the prompt; below-average results are never sent

**RunStyle V1**
- Local, deterministic strategy profile: three families (State Lift, Efficiency Gain,
  Controlled Finish), stages (Early → Forming → Established), facets, and habit lines
- Evaluated point-in-time — a backdated run contributes to future calculations but never
  produces an announcement on entry
- Association language only; never causal, never sent to the AI

---

## Prerequisites

- Java 17
- Maven
- MySQL with a dedicated RunState database user and schema
- Environment variable `RUNSTATE_DB_PASSWORD` set to the database user's password
- Environment variable `ANTHROPIC_API_KEY` set for AI responses (optional — falls back locally)

---

## Running the app

```
mvn compile exec:java -Dexec.mainClass=com.runstate.App
```

Main class: `com.runstate.App`

---

## Running tests

```
mvn test
```

---

## Concepts practiced

- Classes and objects, constructors, encapsulation
- Enums with fields and methods
- Immutable value objects (composition pattern)
- Checked exceptions and exception hierarchies
- Generics and generic helper methods
- Service separation (RunAgent, RunStyleService, WeatherService, ComparisonService)
- Testing seams: ConnectionProvider, ResultSet proxy, package-private console delegates
- JUnit 5 unit tests without live external dependencies
- HTTP client with timeouts
- Scanner input and input validation
- ArrayList, loops, helper methods, median aggregation

---

## Technologies

- Java 17
- Maven
- MySQL / JDBC
- Gson (JSON parsing for Anthropic API responses)
- Java HTTP client (`java.net.http`)
- JUnit 5 (Jupiter)
- IntelliJ IDEA
- Git / GitHub

---

## Next direction

**Music Intelligence V1 planning is approved** and the prompt, sanitized evaluation fixtures,
and opt-in evaluation runner are implemented. The first valid smoke test exposed a generic,
music-avoidant voice; the approved creative-policy revision is committed at `693bfb3`, with
**deterministic verification complete at 256 tests, 0 failures, 0 errors, 0 skipped**. The
canonical contract is `docs/claude-memory/design_music_intelligence_v1.md`.
That planning settled the energy-domain question: RunState **retains the shared three-level**
(LOW / MODERATE / HIGH) energy domain, and the four-state State Scan sketch is superseded as a
domain proposal.

**Manual model evaluation is underway but not accepted, so combined Music Intelligence V1 is
not complete.** An authentication-invalid launch produced no model evidence; the first valid
12-call smoke failed product quality and drove the revision. No revised-prompt smoke or final
evaluation has run. The locked path from here is:

1. Explicitly approve and conduct a fresh 12-call smoke against the revised prompt.
2. Review that smoke as a diagnostic and correct only real problems.
3. Separately approve and conduct the 36-output final evaluation.
4. Reconcile independent review against the locked rubric and obtain Manley's final decision.
5. **Only after combined Music Intelligence V1 is complete** — implementation, deterministic
   verification, manual evaluation, independent review reconciliation, final documentation, and
   Manley's approval, as defined by the canonical plan — run the tightly fenced Core Running
   Foundation Review: check whether the central journey
   (record → preserve safely → understand → manage → use later) is ready for a real interface,
   and produce a short gap list rather than a feature hunt.
6. Resume UI design so the State Scan, history, and post-run reply screens define what the backend
   must return.
7. Migrate/design the Spring Boot API from those screen contracts.
8. Build the mobile client and GPS/automatic-tracking layer against that API.

Spotify integration and live-DJ behavior remain later possibilities with separate legal, privacy,
provider, and platform dependencies.

---

## Author

Manley Johnson
