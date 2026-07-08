---
name: parked-music-recommendation
description: Music suggestion direction (July 2026) — phase-aware playlist agent accepted as north star (pre-run brain first, live adaptation at Phase 6); discovery + collaborative filtering parked; Spotify API constraints
metadata:
  type: project
---

# Music suggestion direction — accepted vision + parked ideas (July 2, 2026)

Manley explored music-suggestion features for RunState. Discussion evolved from a
generic recommender into a context-aware playlist agent. Judged against [[user-goals]]
scope discipline and [[research-app-landscape]] identity rule (deepen feeling-and-voice,
don't chase parity).

## ACCEPTED north star — phase-aware playlist agent (on-identity)

An AI agent builds a run playlist from context only RunState holds:
- Pre-run energy/state (already core RunState data)
- Favorite genres + past run/music/energy history
- Time and day of the run
- Run phase: opener to start strong → steady-rhythm middle → boost track held in
  reserve for a mid-run slump

Sports-science backing: music tempo matched to cadence and motivational music
measurably lower perceived exertion (RPE) — phases responding to different music is
evidence-based, see RPE notes in [[research-app-landscape]].

**Two-slice sequencing (key decision):**
1. **Pre-run brain (buildable in console era, Phase 5 music direction):** given pre-run
   energy, time of day, genre prefs, and history, the agent assembles a phase-structured
   playlist BEFORE the run. No GPS needed.
2. **Live adaptation (Phase 6, mobile only):** detecting a mid-run slowdown requires live
   GPS pace. Real-time queueing waits for the mobile phase. Same feature, two phases.

**UX rule:** never interrupt the current song — queue the NEXT track instead. A wrong
cut mid-run is worse than no adaptation.

## Spotify API facts (verified July 2026)

- DEAD for new apps: Recommendations, Audio Features, Related Artists (deprecated
  Nov 2024, still unavailable). Skip events not exposed either.
  https://developer.spotify.com/blog/2024-11-27-changes-to-the-web-api
- STILL AVAILABLE: playback control + Add Item to Playback Queue (Premium required;
  no queue read/remove; ordering not guaranteed with other Player calls).
  https://developer.spotify.com/documentation/web-api/reference/add-to-queue
- OPEN QUESTION: per-track BPM/energy data must come from elsewhere (e.g. GetSongBPM
  or light user tagging) since Audio Features is gone. Unsolved design question.

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

**How to apply:** steer music work toward the pre-run playlist brain first; live adaptation
only at Phase 6; if discovery or cross-user ideas resurface, point back here. Reply craft
rules live in design_music_reply_style.md.
