---
name: music-ingredients
description: Reference-only holding file for RunState music ideas. Use when music touches current work; do not implement directly from this file.
metadata:
  type: project
---

# Music Ingredients

Reference only. Do not implement from this file directly.

Status: ACTIVE FOR REVIEW now that core stabilization and RunStyle V1 are complete. On any
music-related task, review this file first and flag anything that would be cheaper to include
during that task than to retrofit later ("this fits this task, waiting complicates things" —
Manley decides). This remains an ingredients file, not an execution plan or an archive.

Use this file when current work touches music, privacy, comparison data, Spotify/device
integration, AI-agent behavior, or run-culture positioning. Its job is to preserve the
best music ideas without pulling the project away from the core build.

The core prerequisites that previously kept music parked are complete. **Music Intelligence V1
planning is approved, the prompt and opt-in evaluation surface are implemented, and the revised
creative policy passes 256 deterministic tests** (July 30, 2026; `693bfb3`). The canonical
contract is `design_music_intelligence_v1.md`; implement from it, not this ingredients file.

**Combined Music Intelligence V1 remains incomplete.** An authentication-invalid launch
produced no model evidence. The first valid 12-call smoke failed product quality and triggered
the approved music-forward revision. The next task is a separately approved fresh smoke,
followed—if sound—by the separately approved final evaluation. A passing deterministic gate is
not model-quality evidence.

The canonical plan records the future three-state reply-outcome and persistence boundary
(`REFERENCED` / `NOT_REFERENCED` / `UNKNOWN`) for cross-run reference frequency, but that
mechanism's **implementation remains deferred** — no schema, rolling window, detection, or
prompt line is part of the V1 slice.

## Core principle

RunState is a running app with a strong music layer, not a general-purpose music service.

In the music layer, RunState treats music as something that supports the run and reveals
useful information about the runner. Music serves two independent jobs (July 26, 2026):
ordinary listening and running convenience — complete on its own, with no obligation to
produce insight — and optional music intelligence and learning, the signature layer. The
boundary is "not a general-purpose music service," not "every feature must teach something."
Music is a signature expression of RunState's running culture identity, but the app's larger
purpose remains: track runs, compare runs, understand the runner's state, and make the
runner feel like the product was built for runners.

## Ingredients to preserve

- **Music as evidence:** music should help RunState learn about effort, skips, strong
  finishes, low-energy starts, heat, route, pace changes, overused songs, and turnaround
  moments.
- **Music Agent Workspace:** an optional deeper place where the runner can work with the AI
  agent to shape run music. This is not a default fitness playlist screen.
- **Relationship web, not labeled playlists:** keep song-to-run relationships internal
  instead of exposing many visible bins like Push Songs, Rescue Songs, or Power Songs.
- **Music pacing / anticipation pacing:** preserve the emotional lifespan of good music by
  pacing discovery, saving some tracks for the right run, and creating something to look
  forward to.
- **Deep playlist preparation:** for meaningful runs, RunState may prepare something the
  night before or an hour before, once enough run and music data exists.
- **RunState DJ:** if opted in, all music can participate in the DJ experience through
  strategic next-song ordering. This does not mean constant switching.
- **Protect momentum:** usually do not cut the current song. Queue or prepare the next move
  unless the runner explicitly asks for a stronger intervention.
- **I Need a Push:** an acute override, not the whole feature. It should be quick, serious,
  and temporary, then return to the normal DJ flow.
- **Skip detection as signal:** one skip wakes the system up; repeated skips plus run
  context create stronger confidence.
- **Sense broadly, act cautiously:** use many signals, but avoid overreacting or pretending
  certainty.
- **Record now, interpret later:** live behavior can make cautious choices, but deeper
  meaning should be validated after the run.
- **No-music runs still matter:** support non-music runs with mental anchors or
  self-suggestions when appropriate.
- **Meaning-sync, not beat-sync:** RunState's current direction syncs music to the runner's
  state, intent, and history — not steps per minute. Tempo/BPM matching is **out of current
  scope by stage, not by verdict**: it is not a fit for the current build stage, and no
  current work should depend on it or prepare for it — but the design must not foreclose it.
  Keep room in the music/data layer so tempo-based features remain possible; if tempo
  matching shows a real strength later in development, it can be added then, or justify
  deliberate reconstruction. Practical constraint for any future version: per-track tempo
  data is not currently accessible via Spotify (Audio Features deprecated Nov 2024), so it
  would need its own data source.
- **Settle / Hold / Build:** the only music question a runner should ever face — "What should
  the music do for you today?" Three answers. Ideally the agent proposes one from pre-run
  energy and history ("Sounds like a settle morning?") and the runner confirms with one tap.
- **Trust ledger:** songs earn roles over runs. A track that carried strong finishes gets
  quietly cleared for the final mile; new tracks audition mid-run first. History tags the
  library, not the user. Extends music confidence from a rule into a system.
- **Audible restraint:** the DJ's do-nothing decisions can surface in the post-run reflection
  ("I left the music alone through the middle — you didn't need me"). Restraint becomes
  visible personality, not just absence.
- **A DJ who reads a crowd of one:** the run-culture anchor is the race-course DJ at mile 20.
  RunState's version has only ever played for you. Candidate north-star line for the live
  feature.

## What the relationship web may connect

Internally, RunState can connect music to:

- run phase
- pace and cadence
- skips
- weather
- effort cost
- pre-run and post-run energy
- route
- time of day
- familiarity
- overuse
- comparable runs
- strong finishes
- hard miles
- lyric or theme patterns

These connections should feed run comparison and reflection with pattern language, never
causal certainty.

## Examples of the right feel

- "I made something for tomorrow."
- "Final push protected."
- "You keep reaching for this sound when rough starts turn around."
- "This track showed up right where the run changed."
- "Want me to save this one for your next hard finish?"

## Things to avoid

- Spotify with pace data.
- Soul Pacer with a cleaner UI.
- A dashboard of music bins.
- A playlist generator with a run tracker attached.
- A generic AI chat room.
- A stack of default fitness playlists.
- A feature-dense product where the core running purpose gets buried.
- Tempo/BPM matching at the current stage (see the meaning-sync bullet for status — room
  intentionally preserved for later evaluation).
- Automatic mid-song interruption, even as an opt-in aggressive mode. LEAVE and QUEUE_NEXT
  are the only automatic actions; immediate skips are runner-initiated only.
- Mid-run prompts or dialogs. Revisit only if a watch UI ever exists.

Avoid exposing too many names too early. Possible terms like Deep Run Crate, Push Songs,
Rescue, Power Songs, Run Soundtracks, DJ Mode, or Playlist Lab may survive later, but too
many visible labels will make the app feel crowded.

## Current best structure

1. **Core RunState:** track runs, compare runs, understand the runner.
2. **Ordinary listening and playback convenience:** an independently valuable music job. It
   does **not** require Music Intelligence, produces no obligation to generate insight, and
   **must never be degraded or made intentionally inferior** to pressure adoption of the
   intelligence layer. Real provider and playback work remains deferred.
3. **Music Intelligence Layer:** learn how music connects to run state, effort, energy, and
   patterns. **Optional**, but intended to be **strongly recommended** in later product
   presentation — recommended because it is genuinely good, never by weakening the
   alternative.
4. **Music Agent Workspace:** optional depth where the runner helps the agent understand
   taste, intent, upcoming runs, and music fatigue.
5. **Earned Music Moments:** special outputs that appear when deserved before, during, after,
   or across runs.
6. **Future Community Layer:** anonymous aggregate music context only; no profiles, follower
   mechanics, or ranking.

## How this connects to run comparisons

Music should feed comparison, not overshadow it.

Examples:

- "Your hot-weather runs with familiar high-energy tracks tend to hold steadier."
- "You skipped more than usual before this pace drop."
- "This artist keeps appearing around low-energy starts that finish strong."
- "Your final mile was stronger than similar runs, and the song playing there fits your
  known push pattern."

Use pattern language. Do not claim that music caused the result.

## Build-order rule

When music work resumes, features are prioritized by what can be executed with data and
APIs that exist today. Nothing is dropped for being far off — it just waits its turn. Any
significant new music idea gets a full analysis pass (fit, cost, conflicts with locked
rules) before it's filed, same as the DJ document.

## Recommended order

1. Core stabilization and RunStyle V1 — COMPLETE.
2. Music Intelligence V1 planning — **COMPLETE (July 27, 2026)**, including the three-vs-four
   pre-run energy decision, which closed at **three shared levels**. Canonical contract:
   `design_music_intelligence_v1.md`.
3. Prompt, deterministic tests, sanitized fixtures, and opt-in evaluation runner —
   **IMPLEMENTED**. The first valid smoke failed quality; the approved creative-policy revision
   is committed at `693bfb3`, with **256 tests green**. The frequency mechanism stays deferred.
4. **NEXT:** obtain explicit approval for the revised-prompt 12-call smoke. If it is sound,
   separately approve the 36-output final evaluation, reconcile independent review, and obtain
   Manley's final decision. **Only then** is combined Music Intelligence V1 complete.
5. Run the tightly fenced Core Running Foundation Review **only after COMBINED Music
   Intelligence V1 is complete** (implementation, deterministic verification, manual
   evaluation, independent review reconciliation, final documentation, and Manley's approval —
   not merely after the Java slice); inspect only the
   record → preserve safely → understand → manage → use-later journey and produce a short gap
   list, not a feature hunt (full fence in project_current_state.md).
6. Resume UI design after the review; use the real State Scan, history, and reply
   screens to define the Spring Boot API contracts.
7. Build the Spring Boot API before the mobile client, then build mobile/GPS against it.
8. Build a tiny music decision simulator before Spotify or live-DJ integration.
9. Treat Spotify or other providers as playback/history pipes, not the music brain.
10. Add skips, pace drops, and queue control later in the mobile phase.

## Phrases to preserve

- Music as evidence
- Protect momentum
- Sense broadly, act cautiously
- Record now, interpret later
- Relationship web, not labeled playlists
- Music pacing
- Anticipation pacing
- Final push protected
- Picky gatekeeper
- Music confidence
- The app clears music for the run
- Earned moments
- This song showed up where the run changed
- Not Spotify with pace data
- A runner alone at 5am still feels like they belong to something
