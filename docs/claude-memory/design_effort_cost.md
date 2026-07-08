---
name: design-effort-cost
description: "CANDIDATE next backend build — post-run effort input ('How did that run land?'): energy vs effort axes, runner-native RPE mapping, Quiet Gains concept, question-budget rules"
metadata:
  type: project
---

# Effort Cost — "How did that run land?" (CANDIDATE, July 7, 2026)

From the Codex RPE session. Status: approved direction, not yet committed to build.

## The gap

The app knows state in → what you did → state out, but not WHAT IT COST. Two runs can both
end "Feeling Good" — one smooth, one a fight — and today's agent can't tell them apart.
Effort is the missing axis. (Predicted by the session-RPE note in research_app_landscape:
"on-identity future analytic using data already captured — NOT scope creep.")

## Design (locked in principle)

- ONE quick post-run choice, right after post-run energy, before the AI reply:
  **"How did that run land?"** 1 Smooth / 2 Working / 3 Heavy / 4 Empty tank / 0 Skip
- Optional but visibly valuable — if answered, the agent reply uses it immediately
  ("Three miles steady, but you marked it Heavy in this heat — harder than the numbers look").
- NEVER presented as "RPE" — runner-native words only. Internal mapping:
  Smooth = RPE 1–3 · Working = 4–5 · Heavy = 6–7 · Empty tank = 8–10.
- Energy and effort stay SEPARATE axes (no blended question) — the combos ARE the insight:
  Feeling Good + Smooth = controlled fitness · Feeling Good + Heavy = earned resilience ·
  Spent + Empty tank + PR = costly but meaningful. Energy enum untouched (protects Run Style).
- User-facing framing: **Energy = how you finished. Effort = what it cost.**
- Capture immediately — the post-run moment is the app's magic (and matches the RPE-timing
  science in research_app_landscape). A later "settled check: still feel Heavy?" is a
  mobile-phase nicety; it never holds the post-run reply hostage.
- NOT paywalled: the input is core — the app needs the data from everyone to learn.
  Premium = deeper interpretation (effort trends, load patterns, route/weather/music cost
  analysis, fatigue warnings).
- **Question-budget rule:** default post-run flow = TWO quick taps max (energy + effort).
  Deep reflection is opt-in ritual. Reconcile with the State Scan 4-state open question when
  UI resumes — decide the whole run-flow question budget ONCE, together.

## Quiet Gains (product concept this data unlocks)

Progress made visible before pace/distance show it: "Same route, same distance — landed
lighter than last week. That's progress your pace won't show yet." Serves beginners (who
improve before their numbers do), embodies never-shame-a-down-day, passes the runner-native
test. RATION IT — celebrated sparingly or it becomes noise. Also a cold-start asset: early
progress signals for brand-new users.

## Build shape (the established four-file pattern + one enum)

`EffortLevel` enum (new) → `Run.java` field → `RunStorage` column (`effort_level`) →
`RunConsole` optional prompt → `RunAgent` user-message line + system-prompt rule.
OOP lesson: enum carrying an internal numeric range, kept invisible to the user.

## Tie-ins

- **design_comparison_logic_fix.md** — this provides the effort measure that fix has been
  waiting for ("effort efficiency as the right direction"). Build effort input before or
  with that fix.
- Runner-native test: "How did that run land?" passes; "Rate exertion 1–10" fails.
- Positioning: strengthens the core intelligence layer (state-aware reflection) directly.

## Sequencing

Weather is shipped. Candidates for the next backend thread: (a) effort input + comparison
fix (core-strengthening, all-local, no new APIs), or (b) Phase 6 lyric-aware replies
(gated by the lyrics-licensing legal flag). Manley decides; no deadline.
