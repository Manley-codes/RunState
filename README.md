# RunState

**A Java console application that logs completed runs and responds to what actually happened.**

RunState is a run tracker built around one idea: the numbers matter less than what they meant.
It records a run, protects that record, compares it against your own history, and returns a short
contextual response grounded in the actual data — the energy shift, the effort cost, how this run
sits against similar past runs.

Learning project and portfolio piece. Java 17, Maven, MySQL, JUnit 5.

---

## What it does

You log a completed run through an interactive console menu. RunState stores it durably, checks
for personal records, fetches the weather for that date and location, compares the run against
similar past runs, and generates a short response about it.

| Area | Behavior |
|---|---|
| **Logging** | Date, distance (mi/km), duration, route, plus optional pre- and post-run energy, effort cost, surface, company, shoes, and music state |
| **Persistence** | MySQL via JDBC. History is durable and load-protected |
| **Personal records** | Longest distance and fastest average pace, unit-normalized across miles and kilometers |
| **Weather** | Automatic daily-mean temperature and conditions via Open-Meteo, fetched once at log time and stored with the run |
| **Comparisons** | Up to 10 comparable runs (same route or similar distance, last 180 days), producing four explanatory signals with evidence counts and confidence tiers |
| **RunStyle** | A local, deterministic strategy profile across three families and three maturity stages |
| **Response** | 2–3 sentences from the Anthropic API grounded in the run's real data, with a local fallback when the API is unavailable |

---

## Engineering notes

These are the decisions I would want to talk through in an interview.

**Save-first durability.** Personal records, the generated response, and RunStyle analysis are
produced *only* after MySQL confirms the save. A save failure prints a recovery receipt containing
the full run — including recorded context — and ends the session rather than leaving a run that
looks logged but isn't. There is a dedicated regression test that injects a failing save delegate
and asserts every piece of post-save work is suppressed, so a future refactor cannot quietly
reorder it.

**Complete-history load protection.** A failed or malformed history load exits cleanly with an
explanation naming the offending run and column. It never degrades into "you have no runs yet."
Stored enum text must match a constant exactly — RunState does not trim, re-case, or guess,
because a silently defaulted value would poison personal records and RunStyle from then on.

**Testing seams over mocking frameworks.** Storage is tested through a package-private
`ConnectionProvider` overload, and `ResultSet` — a ~190-method interface — is faked with a
`java.lang.reflect.Proxy` that implements the five methods actually used. The console's post-save
orchestration is reachable through package-private delegates. The full suite runs with **no
database and no API key**.

**Association, never causation.** Surface, shoes, company, music, and weather are recorded as
context and described as associations. They never create or strengthen a RunStyle. The RunStyle
profile itself is computed locally and deterministically and is never sent to the model.

**Failure handling is graded by consequence.** A weather or API failure degrades to a fallback,
because neither determines whether the run was durably recorded. A storage failure does not
degrade — it stops.

---

## Test suite

- **383 tests, 0 failures, 0 errors, 0 skipped** — last verified August 18, 2026
- 9 test classes: 175 `@Test` methods plus 18 `@ParameterizedTest` methods, expanding to 383
  executed cases at run time
- **7,276 lines of test code against 4,082 lines of source**
- No live MySQL instance and no API key required
- Live-API evaluation harnesses are separate classes with `main()` methods, deliberately outside
  the Surefire naming pattern so they never run as part of the suite

```
mvn test
```

---

## Architecture

```
App ──► RunConsole ──► RunStorage ──────► MySQL
              │
              ├──────► WeatherService ──► Open-Meteo
              ├──────► ComparisonService
              ├──────► RunStyleService
              └──────► RunAgent ────────► Anthropic API
```

Service separation is deliberate. `RunAgent` owns everything that talks to the model, including
prompt construction. `RunStyleService` owns profile detection and `Runner` only delegates to it.
`ComparisonService` filters candidates and grades evidence before any of it reaches a prompt.

A rendered diagram of the full current run flow, including failure paths, is in
[docs/CURRENT_RUN_FLOW.md](docs/CURRENT_RUN_FLOW.md).

---

## Getting started

**Prerequisites**

- Java 17
- Maven
- MySQL, with a dedicated RunState database user and schema
- `RUNSTATE_DB_PASSWORD` — the database user's password
- `ANTHROPIC_API_KEY` — optional; without it the app uses its local fallback response

**Run**

```
mvn compile exec:java -Dexec.mainClass=com.runstate.App
```

**Test**

```
mvn test
```

---

## Documentation

The design record is the most substantial part of this project. Decisions are written with an
explicit status, the reasoning behind them, and what would reopen them.

**[Start here → docs/README.md](docs/README.md)** — a guided reading path for anyone reviewing
this repository.

Highlights:

- [Current run flow](docs/CURRENT_RUN_FLOW.md) — the verified as-is behavior, with a flowchart
- [AI agent design](docs/AI_AGENT.md) — identity, prompt architecture, and constraints
- [Data and privacy](docs/DATA_PRIVACY.md) — exactly what leaves the machine, and when
- [ADR-001: RunStyle surfacing](docs/design/adr_001_runstyle_surfacing.md) — a rejected design,
  and why

---

## Current status

The console application is built and working. Phases 1 through 5 — console app and energy system,
MySQL persistence, RunStyle detection, the AI agent, and context expansion for music and weather —
are complete.

**Music Intelligence V1 is implemented but not accepted.** Its evaluation harness was built, run,
and blind-graded, and the feature did not meet the quality bar that was set for it before testing
began. That result is documented rather than worked around. The full record, including what was
tried and what the graders found, is in
**[docs/EVALUATION_RECORD.md](docs/EVALUATION_RECORD.md)**.

Current work is UI design for a mobile client — defining what screens need so the backend contract
follows from real interface requirements rather than the reverse. A Spring Boot API and a mobile
client with GPS tracking follow from there.

---

## Technologies

Java 17 · Maven · MySQL / JDBC · Gson · `java.net.http` · JUnit 5 (Jupiter) · IntelliJ IDEA · Git

---

## Author

**Manley Johnson** — CS student, SNHU
