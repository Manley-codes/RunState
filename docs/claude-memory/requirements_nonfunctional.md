---
name: requirements-nonfunctional
description: "Non-functional requirements (July 2026) — trust, reliability, and feel while the intelligence grows; priorities are phase-dependent"
metadata:
  type: project
---

# Non-functional requirements (July 7, 2026)

What the app must be, not what it does. Judge new work against these. Priorities are
PHASE-DEPENDENT — the order below is for the current phase (single-user, local, pre-release);
security and privacy jump to the top the day the repo goes public or anything server-shaped
exists. Scalability is deliberately deferred, not forgotten — it re-enters at Phase 7
(multi-user platform); #9 and the Spring Boot migration are its foundations.

| # | Requirement | The bar | Status |
|---|-------------|---------|--------|
| 1 | Data reliability | A run saves fully or fails clearly — never corrupts existing history. History is the product's value. | Held so far; formalize when schema changes |
| 2 | Data backup / export | The runner can export their history (CSV is enough). One dev-machine MySQL wipe must not erase months of runs. | NOT built — cheap, do early |
| 3 | API cost control | One run log = bounded API calls. Every background/analysis feature gets an explicit call budget. A bug must never loop against a paid API. | Informal — formalize before playlist agent |
| 4 | Reply latency | Post-run reply renders in a few seconds; a slow API never blocks the flow. The emotional window IS the product. | Fallback exists; no timeout target on agent call yet |
| 5 | Privacy & consent | Clear separation: local data / sent to AI / sent to weather-music APIs / requires explicit opt-in. | DATA_PRIVACY.md live; tiers grow per the feature map in project_current_state.md |
| 6 | Graceful failure | External-service failure never prevents logging a run. | BUILT (AI fallback, weather nulls/timeouts) — keep the pattern |
| 7 | Honest insight | Patterns described as associations unless evidence supports more. No overclaiming on effort, music, weather, improvement. | Documented across agent rules — enforce in every new agent feature |
| 8 | Low-friction reflection | Default post-run flow answerable in ~10 seconds (two quick taps). Depth is opt-in ritual. | Rule locked in design_effort_cost.md |
| 9 | Maintainability & testability | External-service logic isolated from console/history logic (WeatherService pattern); services testable without live APIs. Pays off at the Spring Boot migration. | Pattern established — hold it |
| 10 | Security | Secrets from env vars or local config, never committed source. | API key correct; MySQL password still hardcoded (RunStorage.java:24) — fix before public |
| 11 | Localization readiness | Units and user-facing strings stay separable from logic (store numbers, format at display). Full localization deferred. NOTE: the agent's voice must be RE-CRAFTED per language/running culture by a native voice, not translated — budget accordingly. | Seam rule now; feature much later (run culture is local — Strava-in-Rio observation, July 2026) |

Source: Codex NFR session July 7, 2026 (items 1, 5–10) + additions (2–4, phase-dependency rule).
