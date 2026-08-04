---
name: parked-music-recommendation
description: Music suggestion direction (July 2026) — phase-aware playlist agent accepted as a later north star; combined Music Intelligence V1 (slice + tests + evaluation + approval) and then the Core Running Foundation Review precede UI, Spring Boot, and later mobile live adaptation
metadata:
  type: project
---

# Music suggestion direction — accepted vision + parked ideas (July 2, 2026)

> **Forward-path clarification — July 25, expanded July 26; status reconciled July 30, 2026:**
> References below to “Phase 6” mean mobile-era capabilities, not the immediate next task.
> **Music Intelligence V1 planning, revised prompt, sanitized fixtures, and opt-in runner are
> implemented with the deterministic gate passing** (`design_music_intelligence_v1.md`; 256
> tests green, July 30, 2026). The first valid smoke failed quality and drove the revision, and
> the **revised-prompt smoke then ran July 30 and also failed** quality and trust; a separate
> creative-ceiling probe reached its pre-registered 0–3 branch.
> The **next gate is a separately designed and separately approved stronger-model control,
> which is NOT approved**, then—if sound—the separately approved
> final evaluation. The locked
> execution path from here is evaluation → **combined V1 complete** → tightly fenced Core
> Running Foundation Review → resume UI design → Spring Boot API → mobile/GPS. The review
> begins only after combined V1, not merely after the Java slice. Live adaptation remains in
> that later mobile stage.

Manley explored music-suggestion features for RunState. Discussion evolved from a
generic recommender into a context-aware playlist agent. Judged against
[user_goals.md](user_goals.md) scope discipline and
[research_app_landscape.md](research_app_landscape.md) identity rule (deepen feeling-and-voice,
don't chase parity).

## ACCEPTED north star — phase-aware playlist agent (on-identity)

An AI agent builds a run playlist from evidence RunState holds today plus inputs that may be
added in later phases.
Present evidence (exists in the app today):
- Pre-run energy/state (core RunState data)
- Per-run music mode + optional listening note (RunContext)
- **Run date and derived season only. NO USABLE TIME OF DAY** (corrected July 27, 2026):
  `Run` retains `startTime`/`endTime` fields and the database retains `start_time`/`end_time`
  columns, but the **current console supplies and persists no usable times** — `saveRun` writes
  null to both. V1 may therefore use **only date and derived season**, and must never guess or
  approximate a time of day.
Future candidate inputs (do NOT exist yet). **Resolved July 27, 2026: none were added to V1** —
V1 uses existing current-run evidence only, and acquisition for these remains a later-phase
problem.
- Favorite genres
- Structured music history (which songs, across which runs)
- Run phase: opener to start strong → steady-rhythm middle → boost track held in
  reserve for a mid-run slump (full phase detection needs time-aligned telemetry)

Sports-science backing: music tempo matched to cadence and motivational music
measurably lower perceived exertion (RPE) — phases responding to different music is
evidence-based, see RPE notes in
[research_app_landscape.md](research_app_landscape.md).

**Two-slice sequencing (key decision):**
1. **Pre-run brain — a LATER north-star slice; the full proposed version is not buildable from
   current data (corrected July 27, 2026).** The original description below ("buildable in
   console era") assumed inputs the app does not supply: **no usable time of day** (fields and
   columns exist, but the console persists nulls), **no genre preferences**, and **no
   structured playback history**. It is **not part of Music Intelligence V1**. As originally
   written: given pre-run energy, time of day, genre prefs, and
   history, the agent assembles a phase-structured playlist BEFORE the run. No GPS needed.
2. **Live adaptation (Phase 6, mobile only):** detecting a mid-run slowdown requires live
   GPS pace. Real-time queueing waits for the mobile phase. Same feature, two phases.

**UX rule:** never interrupt the current song — queue the NEXT track instead. A wrong
cut mid-run is worse than no adaptation.

## LOCKED design rules — pre-run playlist brain (July 6, 2026, from Codex brainstorm)

- **Risk-managed placement:** new/unfamiliar songs go MID-RUN only — trusted, proven tracks
  open the run and close it. Never make the first song or the final-push song experimental.
  Root insight: "new music before a run feels risky" — a bad song at mile 3 can kill the
  rhythm; the app reduces risk by controlling WHERE new songs appear. Principle name:
  **music confidence**.
- **Agent music personality — picky gatekeeper:** RunState doesn't recommend music, it
  *clears music for the run*. It filters hard; restraint is the premium feel.
- **Internal gates, not user-facing bins:** selection categories (own-history-proven,
  same-DNA new, deep cuts, last-mile-intensity, low-risk-new) are agent-internal logic.
  Do NOT expose them as named user-facing playlist categories — six labeled bins is a
  dashboard; the brand is restraint.

## CANDIDATE — Deep Run Crate / deep playlist mode (July 6, 2026; working name)

User-invoked deeper playlist-generation mode, built ON TOP of the pre-run brain (comes after
the basic playlist agent exists, not before):
- **Two tiers:** Quick Run Mix = fast, low-friction, mostly trusted. Deep Run Crate = slower,
  intentional pre-run analysis for meaningful runs (long runs, comeback runs, race prep,
  low-energy starts, early mornings, music-central days).
- **User-chosen timing:** generate an hour before or the NIGHT BEFORE — anticipation is part
  of the product. (Realistic, scoped version of the original "agent preps a playlist ahead
  of your run" instinct.)
- **Earned, not default (cold-start principle applied):** the mode only appears once adequate
  run+music data exists. Unlocking it is a milestone moment, like Run Style at run 11.
  Before unlock: honest progress framing, never faked depth.
- **Inputs:** everything the pre-run brain already uses (run/energy/music history, energy
  flips, trusted artists, skips [Phase 6], time/weather/route/expected length when available)
  — analyzed harder, plus same-running-DNA new songs *within the documented data-source
  limits* (Last.fm/ListenBrainz similarity is thin; "running DNA beyond genre" is aspiration,
  not yet a data source). Lyric-theme signals only later, under the lyric-trigger rules.
- **User-facing output = summary, not bins** (consistent with internal-gates rule):
  "Cleared for today's run: 42 minutes. Mostly trusted. 4 new tracks. Final push protected."
- All LOCKED rules above apply: music confidence over discovery, risk-managed placement,
  overclaim guard (pattern language, never "this will make you faster").
- **Naming note:** "crate" collides with the possible Phase 7 community "running crates"
  concept — if both ship, one needs a different name. Working name only.

## FILED for Phase 6 — lyric-trigger "push vocabulary" (July 6, 2026)

Certain words/lyrical themes trigger the runner to push harder. The agent learns a personal
"push vocabulary" (defiance, winning, proving-people-wrong, calm focus...) from which lyric
themes recur around strong efforts. Highly original — no app maps lyric language to effort.
- Same Phase 6 dependency as reflective-song selection above: time-aligned run and
  playback telemetry (splits are one derived summary — see the general splits entry in
  parked_feature_ideas.md). File and build them together.
- **Overclaim guard:** pattern language only — "this kind of lyric keeps showing up around
  your strongest finishes" — NEVER causal claims ("this lyric made you faster").
- **LEGAL FLAG:** lyrics text is licensed content (Musixmatch = paid API; scraping Genius
  violates ToS). Gates this feature; see the privacy/security/legal milestone in
  project_current_state.md.

## Spotify API facts (verified July 2026)

- DEAD for new apps: Recommendations, Audio Features, Related Artists (deprecated
  Nov 2024, still unavailable). Skip events not exposed either.
  https://developer.spotify.com/blog/2024-11-27-changes-to-the-web-api
- STILL AVAILABLE: playback control, Add Item to Playback Queue, AND Get the User's Queue
  (queue read was added — earlier note here said it didn't exist; corrected July 10, 2026).
  Premium required for queue writes; ordering not guaranteed with other Player calls.
  https://developer.spotify.com/documentation/web-api/reference/add-to-queue ·
  https://developer.spotify.com/documentation/web-api/reference/get-queue
- OPEN QUESTION: per-track BPM/energy data must come from elsewhere (e.g. GetSongBPM
  or light user tagging) since Audio Features is gone. Unsolved design question.
- POLICY FLAG: Spotify's developer policy restricts feeding Spotify-derived data into AI
  and building profiles from their content. Formal review item before any deep integration —
  belongs to the standing privacy/security/legal milestone in project_current_state.md.

## PARKED — reflection-to-song preview handoff (August 3, 2026)

The Soundtrack Log prototype introduced a strong later experience: after the app finishes reading
an expanded AI reflection aloud, it may hand off to a **short, provider-authorized preview or snippet
of the connected song**. The intent is to let the reflection land and then let the soundtrack answer,
not to turn the reply into a music player or interrupt the spoken message.

- This is a future mobile/provider-integration capability, not Music Intelligence V1 and not current
  console behavior.
- Use only playback or preview access explicitly supplied by the connected music platform with the
  required account permissions and licensing. Never bundle, scrape, recreate, or generate a substitute
  for copyrighted song audio.
- The reflection must remain complete without audio. If a preview is unavailable, restricted, or the
  user has audio disabled, omit it cleanly with no degraded reply.
- `Replay` repeats the reflection. A later prototype must decide whether song-preview replay is a
  separate control. Collapsing the record, choosing another record, or pressing stop ends whichever
  reflection or preview is currently playing before the next selection begins.
- Exact Spotify, Apple Music, and other provider capabilities must be re-verified against their current
  APIs, platform rules, and licensing terms at implementation time. Privacy/security/legal review is a
  release gate.

## PARKED — discovery of new songs

Recommendation engine for unfamiliar music: not viable (API gone, N=1 can't beat
Spotify at discovery). Discovery is Spotify's job; RunState curates from the user's
own library, which is deep and mostly forgotten anyway.

## PARKED — cross-user suggestions (collaborative filtering)

Manley's idea: suggest songs other users with similar taste performed well to —
collaborative filtering with a performance twist. Parked, not rejected:
- Needs a user base, shared server, accounts, consent for health+music data (heavy privacy weight)
- Cold-start: useless until hundreds of active users
- A platform, not a feature — "step 12 while we're on step 5"
The single-user Phase 5 data model (songs↔runs↔energy) is exactly what collaborative
filtering would consume later, so building N=1 first keeps the door open at no cost.

**Extended July 6, 2026 — reclassified ON-VISION, waiting only on the platform:** per the
culture-community clarification in user_goals.md, these join the same Phase 7 pile as
anonymous/aggregate culture content (no profiles, no ranking):
- Community shoe stats — real retirement mileage across RunState runners per shoe model
- Browsable community playlists with run context ("what other runners ran well to")
Single-user versions ship first (see creative_direction_ui.md §0 "Culture details as
designed moments"); these are the community layers on top.

## FILED for Phase 6 — reflective-song selection from full run listening history (July 6, 2026)

Future versions: the agent looks across ALL songs played during a run and picks the
REFLECTIVE one to build the reply around. Key spec nuance decided now so future-us
builds the right thing: "reflective" is NOT "most played" — it is *what was playing
during the moment that defined the run* (the hard mile, the energy flip, the finish).
Requires: run start-time capture + per-track play timestamps relative to the run
timeline. Both are existing Phase 6 deferrals (see design_weather_context.md deferrals
and the mobile phase). How the reply should USE the chosen song is governed by
design_music_reply_style.md.

## FILED for Phase 6+ — all-day taste profiling + experimental discovery playlists (July 6, 2026)

With explicit consent, observe listening OUTSIDE runs via Spotify recently-played polling
(still available), bucketed by time-of-day + weekday/weekend — do NOT infer activities
(commute/studying): creepy, low value. Agent builds a taste profile from song *combinations*,
not just tracks — LLM strength, on-identity. Profile powers:
- (a) smarter pre-run brain (morning-you vs night-you)
- (b) NEW-song discovery via open similarity sources (Last.fm track/artist getSimilar,
  ListenBrainz — alive but thinner quality than Spotify; the deep profile is what makes them
  usable — the two halves need each other; this partially unlocks the PARKED discovery idea above)
- (c) the surprise moment: "I built you a playlist for this morning's run — want to try it?" —
  opt-in, agent voice.
Skip data returns in Phase 6 as first-party data (RunState controls the queue, sees its own
skips — no API needed). Feedback loop: correlate experimental playlists with post-run energy;
never frame a down run as the playlist's fault (stay-positive rule). PRIVACY: all-day listening
is a major consent expansion beyond run music — separate explicit opt-in tier + DATA_PRIVACY.md
update when built. Sequencing: layers ON TOP of the pre-run playlist brain; do not start before
it exists.
APIs: https://www.last.fm/api/show/track.getSimilar · https://listenbrainz.readthedocs.io/en/latest/users/api-compat.html

**How to apply (order reconciled July 30, 2026):** Music Intelligence V1 planning, revised
prompt, sanitized fixtures, and opt-in runner are implemented
(`design_music_intelligence_v1.md`; 256 tests green), but the revised-prompt smoke has run and
failed, as has the separate creative-ceiling probe. The locked immediate order is now a
**separately designed and separately approved stronger-model control (not approved) →
separately approved final evaluation → independent review and Manley's decision** → **combined
V1 complete** → Core Running Foundation Review → UI → Spring Boot → mobile/GPS.

The pre-run playlist brain remains a **later north star** that approved music slices build
toward — **not the next task**, and **the full proposed version is not buildable from current
data**, since no usable time of day is persisted and genre preferences and structured playback
history do not exist. Live adaptation only at Phase 6; if discovery or cross-user ideas
resurface, point back here. Reply craft rules live in design_music_reply_style.md, subordinate
to the canonical V1 plan.

Note the Foundation Review sequencing: it begins only after **combined Music Intelligence V1**
is complete, not merely after the Java prompt slice.
