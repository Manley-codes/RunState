---
name: parked-feature-ideas
description: "Non-music future feature decisions — parked ideas plus accepted/later automatic splits and time-aligned telemetry"
metadata:
  type: project
---

# Future feature decisions (non-music)

Harvested July 7, 2026 from the archived June docs (UNIQUE_IDEAS.md, IDEAS.md). Music ideas
live in parked_music_recommendation.md. This file now holds TWO categories (July 26, 2026):
**parked** (worth remembering, not promised; ≠ rejected — each entry notes what unlocks it)
and **accepted/later** (committed to the vision, waiting for its proper phase — currently
the automatic-splits/telemetry entry).

## Top Run Highlights — strongest of the batch

Top ~3 all-around best runs as badge/reward-style cards — balancing distance, pace, post-run
energy, effort, weather, music — NOT just fastest/longest. Embodies "the best run is not
always the fastest" (runner-native, on-identity, feeds state-aware reflection). Effort-cost
data (design_effort_cost.md) would make the "all-around" scoring honest. Phase 6 UI-era:
the badge presentation is the point. Related craft note: creative_direction §11 wants the
Run Style reveal to feel earned — same energy here.

## Future Run Suggestions — parked WITH a tension to resolve

Pre-run suggestion of a general run type/range from patterns ("your best-feeling runs are
3–5 easy miles"). TENSION: research_app_landscape scope-watch explicitly resists
"proactive/adaptive coaching nudges." The June docs' version is deliberately soft
(patterns and options, never instructions, never medical/coaching claims). Needs a ruling
if ever revived; do not build casually. Adjacent: the pre-run playlist brain is the
approved cousin of this idea.

## Support Messages (text) — voice version SCRAPPED July 7, 2026

Selected friends/supporters attach a short text message to a run or race day. Passes the
culture-community line: invited circle, not social graph, no feed, no follower mechanics.
Voice messages: scrapped (heavy, gimmicky) — decision by Manley, July 7, 2026.
Unlocks: any account/server infrastructure (Phase 7-ish), or even a lightweight local
version (partner types a message pre-race). Emotional payoff is the point.

## Body Feedback / Discomfort Patterns — KEEP, LOW PRIORITY (July 7, 2026)

Patterns between effort, discomfort, pace, distance, context ("you often report knee
discomfort after faster runs over 4 miles"). Awareness only — never diagnosis, never
causal claims; persistent pain → professional. HEAVY health-data weight: joins the
privacy/legal feature map (project_current_state.md) the moment it's considered.
Explicitly labeled low priority right now.

## Automatic splits / time-aligned telemetry — CONTRACT APPROVED/LATER, general running capability

**Status: accepted for the mobile/GPS phase — not parked, not music-only.** Decision made
July 26, 2026 (Codex analysis + Claude review, confirmed by Manley): automatic mile/km
splits are a first-class *running* feature — pacing visibility, portion comparison, start
fast/finish strong shape — valuable even for runners who never touch music intelligence.
Previously the docs mentioned splits only as a music/Phase 6 dependency; this entry makes
the general-purpose capability explicit.

**Architecture rule (one shared system):** GPS/time data → core time-aligned run telemetry
→ automatic split calculation → consumed by BOTH general run screens AND music
intelligence. Music must never build its own separate split system; advanced music features
(reflective-song selection, hard-mile/turnaround detection, push vocabulary) consume the
shared telemetry. Label for those music ideas: "requires time-aligned telemetry" (more
precise than "requires splits" — splits are one derived summary of telemetry).

**The provider-neutral mobile contract was approved August 26, 2026; nothing is built.** Canonical
detail now lives in `run_initiation_register.md`. In short: location observations and music use one
saved run timeline; only accepted Running points add distance; active time excludes pauses; full
mile/km splits plus the final partial split are stored as a versioned completed-run snapshot; and
missing telemetry produces unavailable evidence rather than invented detail. Exact GPS filtering,
Room schema, UI presentation and provider implementation remain later. Timestamped location and
playback data still belong to the standing privacy/security/legal milestone before release.

## Small context fields (capture-when-natural)

- Solo / partner / group — one optional field; future pattern dimension ("longer with a
  partner"). Build only as part of a context-fields pass.
- Run Type (Easy/Steady/Speed/Long/Race) — harvested into design_comparison_logic_fix.md
  (it's a fair-comparison dimension, not a standalone feature).
- Trail/route photo gallery — merged with the run-picture gallery already parked in
  creative_direction_ui §12.
