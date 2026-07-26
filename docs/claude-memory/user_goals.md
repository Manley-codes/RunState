---
name: user-goals
description: Core goals for the RunState project — what success looks like and what to protect against
metadata:
  type: user
---

**Background:**
- CS sophomore at SNHU, concentration in Software Engineering (bachelors)
- Completing CS 230 (Operating Platforms); starting CS 255 (System Analysis & Design) and MAT 230 (Discrete Math) in June 2026
- Career goal: full-stack, focused on backend for now
- MySQL experience: comfortable with CREATE TABLE, INSERT, SELECT — not proficient but functional
- Java: comfortable with basics, building OOP confidence

**Primary goals (in priority order):**
1. Learning experience — Java, OOP, software design, AI/agents
2. Fun and unique app with a clear identity
3. Strong portfolio piece to show employers
4. Foundation for AI agent integration (one focused agent, not over-engineered)

**Natural phases agreed on:**
1. Console app — core features, energy system, opening prompt, rolling averages (done)
2. Persistence — runs survive between sessions (done)
3. Your Run Style — pattern detection with real data (done)
4. AI agent — replace buildRunResponse() with agent call (done)
5. Phase 5: AI agent context expansion — music (manual now, Spotify later), weather (automatic via Open-Meteo)
6. Mobile UI — GPS tracking, automatic run detection

**Forward execution path — LOCKED July 25, 2026:**
The historical phase list above describes the product layers, but the implementation seam is
now explicit: Music Intelligence V1 planning → one bounded console music slice → resume UI
design → Spring Boot API → mobile client with GPS/automatic tracking. UI design resumes before
Spring Boot so real screen needs shape endpoint and payload contracts; Spring Boot is built
before the mobile client. Spotify/live-DJ depth remains later than this first mobile foundation.

**Final product vision (locked in June 2026):**
Manual input in the finished app is minimal by design:
- Post-run energy (how they feel) — always asked, core to the app's identity, cannot be automated
- Lower-priority optional details (shoe choice, notes, etc.) — future, low priority
Everything else is automatic: distance/pace/duration via GPS, weather via API, music via Spotify/device.
Manual logging in the console app is development scaffolding only — not the final UX.
The console app is a build-and-test tool, not the finished product.

**Signature feature direction (locked in June 2026; refined July 6, 2026):**
Music is the signature feature — go deep on one thing rather than shipping many features halfway.
Weather included but secondary to music.
Both feed the AI agent for more personal, specific post-run responses.
Refinement (July 6): music is the signature EXPRESSION of the reflection core, not the core
itself — music features must always serve state-aware reflection (see positioning below).

**Product positioning — LOCKED July 6, 2026 (do not re-litigate; point identity questions here):**
Third articulation of RunState's identity (after research_app_landscape's strategic read and
creative_direction_ui §1) — all three agree, so this is final:
- Foundation/world: RUN CULTURE — the world the app lives inside (rituals, objects like shoes,
  language, values, environment, culture-community). Not vibes — built into how the app thinks.
- Core intelligence / the job: STATE-AWARE REFLECTION — "RunState helps you understand what
  your run meant, not just what you recorded."
- Signature expression: MUSIC IDENTITY — the most memorable flavor inside that job.
- Trust contract: PRIVACY-FIRST PERSONAL INSIGHT — routes, bodies, habits, taste are personal;
  respecting that is part of the culture.
Positioning line: "RunState is built from running culture outward. It helps runners understand
the state they brought into a run, what the run demanded, and what they carried out of it."
North-star sentence (Manley-endorsed, July 7, 2026): **"A runner alone at 5am with no followers
and no feed still feels like they belong to something."** Culture lives in the product's voice
and values, not in a crowd — personal in value, cultural in voice, communal only ever in texture.

**Runner-native test (adopted July 6, 2026) — apply to every future differentiating feature:**
"Would this still make sense if the user did not run?" If yes, be suspicious — it may be
generic wellness/fitness/social. ("How do you feel today?" = generic; "How are we starting
this run?" = runner-native. "Mood trend" = generic; "You keep turning rough starts into
strong finishes" = runner-native.)
CAVEAT: the test filters DIFFERENTIATING features, not fundamentals — pace/distance would
make sense for a cyclist and are still mandatory. Suspicion, not automatic rejection.

**Scope discipline (explicitly agreed):**
No new large outside-of-scope ideas. The core is:
log a run → track how you felt → learn something meaningful from it.
Everything else is a layer on top of that core.

**Community — clarified July 6, 2026 ("culture-community YES, network-community NO"):**
RunState is not a community-purpose app, but running culture includes community, and the
user is NOT restricting features that reflect it. The only hard line: direct social-media
mechanics — profiles with followers, following pages, feeds-as-social-graph, DMs,
head-to-head comparison. Welcome: indirect/ambient community — anonymous, AGGREGATE culture
content (e.g., the mileage the community typically retires a shoe at; playlists other
runners ran well to, no identities attached). Nobody is ranked, nobody is watched.
Sequencing unchanged: any community content still requires the multi-user platform
(Phase 7 horizon, see parked_music_recommendation.md); single-user versions come first.

**Why:** User acknowledged tendency to expand scope. Agreed to stay disciplined. This boundary protects the learning experience and keeps the project finishable.
