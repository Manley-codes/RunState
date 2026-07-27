---
name: requirements-nonfunctional
description: "Non-functional requirements (July 2026) — trust, reliability, and feel while the intelligence grows; priorities are phase-dependent"
metadata:
  type: project
---

# Non-functional requirements (July 7, 2026 — updated July 26, 2026)

What the app must be, not what it does. Judge new work against these. Priorities are
PHASE-DEPENDENT — the order below is for the current phase (single-user, local, pre-release);
security and privacy jump to the top the day the repo goes public or anything server-shaped
exists. Scalability is deliberately deferred, not forgotten — it re-enters with the Spring
Boot foundation and grows again at the multi-user horizon. The locked transition is: the first
music slice and its evaluation → **combined Music Intelligence V1 complete** → Core Running
Foundation Review → UI contract work → Spring Boot API → mobile/GPS. The review begins only
after combined V1 (implementation, verification, evaluation, review reconciliation,
documentation, and Manley's approval), not merely after the Java slice.

| # | Requirement | The bar | Status |
|---|-------------|---------|--------|
| 1 | Data reliability | A run saves fully or fails clearly — never corrupts existing history. History is the product's value. | **BUILT (July 2026).** All-or-nothing startup load (malformed rows fail the entire load, never partial); malformed enum rejection with a named column on the details line; checked `RunStorageException` at every DB boundary; save-first ordering (`saveRun` before `addRun`/PR/AI/RunStyle); recovery receipt on save failure (includes recorded context). Backup/export remains open and must not be inserted into this sprint. |
| 2 | Data backup / export | The runner can export their history (CSV is enough). One dev-machine MySQL wipe must not erase months of runs. | NOT built — cheap, do early |
| 3 | API cost control | One run log = bounded API calls. Every background/analysis feature gets an explicit call budget. A bug must never loop against a paid API. | **Current path is bounded:** one Open-Meteo weather fetch per log-run attempt; at most one Anthropic request per successfully logged run; no retry loop. Provider/music budgets remain future work (formalize before playlist agent). |
| 4 | Reply latency | Post-run reply renders in a few seconds; a slow API never blocks the flow. The emotional window IS the product. | **Timeouts built:** 5-second connect timeout on the shared HTTP client; 5-second per-request timeout on the Anthropic call. Local fallback returns immediately on any failure. No measured end-to-end SLO yet. |
| 5 | Privacy & consent | Clear separation: local data / sent to AI / sent to weather-music APIs / requires explicit opt-in. | `DATA_PRIVACY.md` live and reconciled with actual outbound prompt (July 25, 2026) — Anthropic and Open-Meteo disclosures current. Provider/mobile consent tiers remain future release work per the feature map in project_current_state.md. |
| 6 | Graceful failure | External-service failure never prevents logging a run. | **BUILT.** AI fails open (local fallback); weather fails open (all-null `WeatherData`, "Not available" in prompt); database durability failures stop clearly with a recovery receipt and session exit. Keep the pattern. |
| 7 | Honest insight | Patterns described as associations unless evidence supports more. No overclaiming on effort, music, weather, improvement. | **Built across the stack:** negative-comparison pre-filter (below-average signals never reach the prompt); signal-specific evidence counts and confidence tiers; RunStyle uses association-only language with stage/facet thresholds. Enforce in every new agent feature. |
| 8 | Low-friction reflection | Default post-run flow answerable in ~10 seconds (two quick taps). Depth is opt-in ritual. | Two post-run questions (energy, effort) structurally implemented per `design_effort_cost.md`. Timing not formally measured. |
| 9 | Maintainability & testability | External-service logic isolated from console/history logic (WeatherService pattern); services testable without live APIs. Pays off at the Spring Boot migration. | **Pattern established and expanded:** `ConnectionProvider` seam (DB), `readRuns(Runner, ResultSet)` seam (row decoding), `saveRun`/`buildRunResponse` console delegates (Task 4). **77 tests green (July 25, 2026).** Hold the pattern. |
| 10 | Security | Secrets from env vars or local config, never committed source. | `ANTHROPIC_API_KEY` read from environment (`RunAgent.java`). `RUNSTATE_DB_PASSWORD` read from environment (`RunStorage.java`). Dedicated `runstate_user` — never root auth for MySQL. Credential rotation and release security audit remain open. |
| 11 | Localization readiness | Units and user-facing strings stay separable from logic (store numbers, format at display). Full localization deferred. NOTE: the agent's voice must be RE-CRAFTED per language/running culture by a native voice, not translated — budget accordingly. | Seam rule now; feature much later (run culture is local — Strava-in-Rio observation, July 2026) |

Source: Codex NFR session July 7, 2026 (items 1, 5–10) + additions (2–4, phase-dependency rule). Updated July 25, 2026 to reflect Pre-Music Integrity Sprint results.
