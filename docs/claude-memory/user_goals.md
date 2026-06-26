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

**Final product vision (locked in June 2026):**
Manual input in the finished app is minimal by design:
- Post-run energy (how they feel) — always asked, core to the app's identity, cannot be automated
- Lower-priority optional details (shoe choice, notes, etc.) — future, low priority
Everything else is automatic: distance/pace/duration via GPS, weather via API, music via Spotify/device.
Manual logging in the console app is development scaffolding only — not the final UX.
The console app is a build-and-test tool, not the finished product.

**Signature feature direction (locked in June 2026):**
Music is the primary identity feature — go deep on one thing rather than shipping many features halfway.
Weather included but secondary to music.
Both feed the AI agent for more personal, specific post-run responses.

**Scope discipline (explicitly agreed):**
No new large outside-of-scope ideas. The core is:
log a run → track how you felt → learn something meaningful from it.
Everything else is a layer on top of that core.

**Why:** User acknowledged tendency to expand scope. Agreed to stay disciplined. This boundary protects the learning experience and keeps the project finishable.
