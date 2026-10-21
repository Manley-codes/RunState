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

## Optional running assistant — parked, October 20, 2026

Manley is considering making the current circle an entry point for an AI assistant. Possible
uses include asking about trails or scenery, browsing a useful list, or opening a search box.
A separate possibility is a circle in the History summary for questions such as which saved run
was fastest or longest, or other questions about the runner's history. **The History placement
is tentative, not a locked layout decision.** This is a later idea to preserve, not part of the
current local UI polish and not authorization to build an assistant now.

Keep the assistant optional. Existing navigation, run controls, settings and access to History
must remain directly usable without typing or speaking to AI. Trail answers would need suitable
sources; personal-history answers should use the runner's actual saved records and leave missing
information unknown. Decide the first useful question and entry point when this idea returns,
rather than adding a broad chat surface to every screen.

## RunState accounts — timing recommendation, October 20, 2026

Recommended timing is after the core running experience is stable, before inviting independent
external testers or introducing private cloud synchronization. A local guest demonstration
can remain useful without an account. This records a recommendation, not an approved account,
login or cloud-sync implementation for the current work.

Spotify authorization only connects the music service; it is not a RunState account and does not
establish ownership of saved RunState history. Before adding accounts, settle who owns each run,
how a guest's existing local history attaches to the chosen account, and how switching accounts
avoids mixing people's data. Preserve current run UUIDs, facts, reflections and related records
during that migration; signing in must not erase or silently replace local history.

## Activity recap after Save Activity — future direction, October 18, 2026

Manley wants Completion's main action to become **Save Activity**, followed by an activity recap
rather than immediately offering another run. His richer future screen could show the completed
route on a map, measured metrics, splits, supported PRs and an option to save/export an activity
image. Carry forward the clean, clear visual character he likes. This is a direction to revisit,
not an approved full screen/export implementation or a promise every metric is already available.

The immediate design dependency is truthful **Save Activity** behavior: automatically protect
the ended run as a recoverable activity, then let the runner explicitly finalize it. Existing
autosave currently happens before Completion and reflections, so renaming Start another run
would be misleading and removing durable saving would risk loss. Agree recovery, History visibility,
discard and exactly-once finalization before changing that lifecycle. Save should not require
Energy, Effort, music or a successful online reflection. Preserve existing saved activities.

Manley proposes moving **View in Log History** out of Completion into this later flow. Keep
replacement access usable before removing the existing link. Rich map imagery/export, achievement
presentation and polished splits can be added after the basic save/destination behavior is sound.
The full run debrief, proposed sequencing and unresolved Energy interaction decisions are in
`project_current_state.md` (October 18 real-run debrief).

## Suggested running places on Start — deferred October 16, 2026

**Status: recorded for later consideration, not an implementation task or a change in priorities.**
Manley asked to preserve this idea until more important work is finished. Revisit after progress
on the current GPS reliability and runner-state priorities; no delivery date or final scope is set.

**The idea:** keep the attractive static Start-screen imagery, but use it to introduce a suggested
running place, such as Hermann Park or Buffalo Bayou Trail. The featured place could change from
day to day, and a swipe indicator could let the runner browse another place. Later suggestions
could consider weather, crowding, how many people are there, or reviews. These are possible inputs,
not data sources or capabilities the app currently has.

**Potential value:** help someone decide where to run and discover an appropriate alternative,
rather than only rotate decorative backgrounds. This is place discovery; the separate Future Run
Suggestions entry below concerns coaching-like recommendations about run type and distance.

**Recommended first scope, still to decide:** a small curated set of places with a useful reason
to explore each one, known details such as surface or shade, and a way to keep a favorite. Daily
novelty should not displace a runner's familiar choice. Swipe only the place preview, keeping the
agent, Energy choices and Start button anchored. Keep Active focused on the current run and its
optional live map. This should remain a bounded addition to RunState's before/after state concept.

Clearly label a featured place as a suggestion (for example, "Explore · Hermann Park"), not the
runner's current location. Browsing a suggestion must not start a run or change where GPS records.
Weather, current crowd levels and reviews need suitable sources before influencing recommendations;
"usually busy" is different from "busy now," and high ratings alone do not establish running fit.
Pre-run Energy could contribute later, but Low Energy must not automatically imply a short run.

**Next decision when revisited:** choose the initial places and useful details, confirm reusable
imagery and data sources, and approve one bounded design/implementation slice. No trail navigation,
live crowd detection or recommendation engine is authorized by this note.

## Calorie indicator — later, nonpriority (October 16, 2026)

**Status: recorded at Manley's request for later consideration.** He wants a calorie indicator,
but explicitly said it is not a priority. No calculation, Android storage field or working native
indicator currently exists; this note does not authorize implementation or move it ahead of the
current pace and run-context work.

Any future value should be presented as an **estimate**, with its calculation or external source,
required inputs and uncertainty understood before a number is displayed. Do not invent a body
weight or use a decorative calorie ring as evidence that calories were measured.

Older visual notes mention a Completion calorie ring, while `design_start_run_v7.md` records a
proposal to replace the Active calorie slot with cadence. Keep those as design history: this
latest request preserves the calorie idea without selecting a screen, ring, formula or replacement.
Revisit when higher-priority run accuracy/context work is settled and approve one bounded slice.

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
