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

The core prerequisites that previously kept music parked are complete. The current resume
point is to either apply the independent prompt-only music reply craft rules or define Music
Intelligence V1 from these ingredients. Their order is not locked. The persistence-dependent
cross-run reference-frequency mechanism should wait for the V1 evidence contract.

## Core principle

RunState is not becoming a music app.

In the music layer, RunState treats music as something that supports the run and reveals
useful information about the runner. Music is a signature expression of RunState's running
culture identity, but the app's larger purpose remains: track runs, compare runs, understand
the runner's state, and make the runner feel like the product was built for runners.

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
- **Meaning-sync, not beat-sync:** Weav Run and RockMyRun already own tempo-matching, and it
  needs data RunState can't get. RunState's lane is syncing music to the runner's state,
  intent, and history — not their steps per minute. BPM matching is permanently out, not
  deferred.
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
- Tempo/BPM matching in any form (wrong lane — see meaning-sync).
- Automatic mid-song interruption, even as an opt-in aggressive mode. LEAVE and QUEUE_NEXT
  are the only automatic actions; immediate skips are runner-initiated only.
- Mid-run prompts or dialogs. Revisit only if a watch UI ever exists.

Avoid exposing too many names too early. Possible terms like Deep Run Crate, Push Songs,
Rescue, Power Songs, Run Soundtracks, DJ Mode, or Playlist Lab may survive later, but too
many visible labels will make the app feel crowded.

## Current best structure

1. **Core RunState:** track runs, compare runs, understand the runner.
2. **Music Intelligence Layer:** learn how music connects to run state, effort, energy, and
   patterns.
3. **Music Agent Workspace:** optional depth where the runner helps the agent understand
   taste, intent, upcoming runs, and music fatigue.
4. **Earned Music Moments:** special outputs that appear when deserved before, during, after,
   or across runs.
5. **Future Community Layer:** anonymous aggregate music context only; no profiles, follower
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
2. Choose the immediate music task: independent reply craft rules or Music Intelligence V1
   planning. Either order is valid; do not bundle in the frequency mechanism yet.
3. Turn the selected V1 decisions into a bounded execution plan, then document only the
   decisions Manley approves.
4. Build a tiny decision simulator before Spotify or mobile integration.
5. Treat Spotify or other providers as playback/history pipes, not the music brain.
6. Build live GPS, skips, pace drops, and queue control later in the mobile phase.

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
