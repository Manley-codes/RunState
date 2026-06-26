---
name: design-run-response-system
description: Locked-in design rules for the post-run contextual response system
metadata:
  type: project
---

Every completed run gets a contextual response. No exceptions.

**Core principles:**
- Never imply a run was bad — the runner showed up, that counts
- PRs are still celebrated separately from Your Run Style
- The feeling question appears after the run summary (same log flow, not a separate screen)
- LOW → HIGH energy lift always adds a second line on top of the main message
- Never mention below-average performance — stay quiet if numbers are down

**Current implementation (Phase 4):**
- `RunAgent.buildRunResponse(run, avgPace, avgDistance)` is the public entry point
- Tries Anthropic API call first (claude-haiku-4-5-20251001)
- Falls back silently to `buildFallbackResponse()` on any failure
- Fallback logic lives in `RunAgent.java` — the table below describes it

**Fallback response logic:**

| Performance | Post-run feeling | Response |
|---|---|---|
| PR | Spent | "You really pushed yourself — and it showed. [PR]. Feeling spent after that makes sense." |
| PR | Feeling Good | "Strong run. [PR] and you're still feeling good." |
| PR | Powered Up | "[PR] and you finished strong. That's a great day." |
| PR | Skipped | "[PR]. Strong effort." |
| No PR | Powered Up | "Strong all-around run. You finished feeling great." |
| No PR | Feeling Good | "Solid run. Good effort today." |
| No PR | Spent | "You gave everything today. Good job getting it done." |
| No PR | Skipped | "Good job getting a run in today. Every run counts." |

**LOW → HIGH always adds:**
"See what getting active can do. You started rough and finished feeling great."

**Your Run Style (pattern feature — built in Phase 3):**
- Stays quiet until minimum run threshold (11 total runs, 10 previous)
- Elimination funnel: Layer 1 = both pace AND distance above rolling average (last 20 runs) → Layer 2 = post-run energy MODERATE or HIGH → Layer 3 = pre-run energy as context
- LOW → HIGH pre/post always notable regardless of other factors
- Consistency gate: 4+ of last 10 previous runs also qualify
- New Run Style Alert fires after a qualifying run
