---
name: design-music-reply-style
description: "Music reply craft reference (July 6, 2026; reconciled July 27, 2026; status updated July 28, 2026) — subordinate to the canonical design_music_intelligence_v1.md contract; the core prompt-only craft rules are implemented and deterministically verified, manual evaluation has not started, and cross-run frequency implementation remains deferred."
metadata:
  type: project
---

# Music reply style — craft rules for the AI agent (July 6, 2026)

How the agent should reference music in post-run replies. Extends the Phase 5 Step 1
music feature (manual input).

**STATUS: CORE CRAFT RULES IMPLEMENTED — MANUAL EVALUATION NOT STARTED. CRAFT REFERENCE ONLY.**

The core craft rules below — run fact first, music supporting rather than replacing, the
one-sentence music ceiling, convergence-scaled confidence, no taste evaluation, no causation,
no fabrication, no exact or near-exact lyrics, and no lasting pattern from one run — are
**implemented in the V1 prompt slice and deterministically verified** (198 tests, 0 failures,
0 errors, 0 skipped, July 27, 2026). **Manual model evaluation has not started**, so the rules
are proven to be *present in the prompt*, not proven to be *followed by the model*.

> **Canonical authority: [`design_music_intelligence_v1.md`](design_music_intelligence_v1.md).**
> Music Intelligence V1 planning is **COMPLETE and approved (July 27, 2026)**. That document
> is the single source of truth for purpose, contracts, prompt behavior, tests, and
> evaluation. Where this file conflicts with it, **the canonical plan wins**. This file is
> kept for craft reasoning and history; do not implement from it directly.

This document contains two distinct slices:
- **Core craft rules + early-user posture:** prompt-layer work only; no schema, console,
  provider API, or reply-persistence change. **This slice is implemented and deterministically
  verified**, executed per the canonical plan's bounded implementation contract. The manual
  evaluation gate that follows it has not started.
- **Cross-run frequency balance:** a separate mechanism that cannot be prompt-only because the
  app stores neither AI replies nor a "music was referenced" signal. The canonical plan
  documents its future three-state outcome boundary; the **physical schema, rolling window,
  detection mechanism, and frequency implementation all remain deferred.**

References below to “Phase 6” mean later mobile-era data availability. Under the current
forward path, UI contract work and Spring Boot come before that mobile implementation.

## The anti-pattern vs. the north star

**Anti-pattern (real output, July 2026):** "Till I Collapse by Eminem was a great song choice."
It EVALUATES the song — a compliment about the user's taste, disconnected from the run.

**North star (Manley's mockup, transcribed — image kept out of repo):**
> "4.2 at 8:24 before the city woke up — eleven seconds under your rolling pace.
> Larry June on Cedar Trail at sunrise: the taste matched the discipline."
> (Now playing: "6am In Sausalito" — Larry June, Orange Print)

Why it works: it never names the song; it references the ARTIST and lets circumstances
carry the weight. The connection is a CONVERGENCE — 6am-titled song + sunrise run +
artist context + a real stat anchoring it.

**SUPERSEDED phrase (July 27, 2026): "the taste matched the discipline."** That clause
**evaluates the runner's taste**, which V1 prohibits outright. The mockup is preserved above
as history, but this phrase must not be reproduced or imitated.

**What remains useful in the mockup** is the structure, not the compliment: the **run fact**
leading, the **artist/music context**, and the **convergence of circumstances** that makes the
reference earned. Keep those; drop the taste praise.

NOTE: the mockup itself needs time-of-day + a now-playing integration — both Phase 6. **V1 has
neither** — only run date and derived season exist today. The STYLE rules below are portable to
today's manual feature; the mockup's time-of-day framing is not.

## Craft rules (for SYSTEM_PROMPT revision)

1. **Never evaluate the song choice.** No "great song choice," no taste compliments.
   Connect what the song/artist is ABOUT to what the run WAS.
2. **Convergence scaling.** Boldness of the reference scales with how many run details
   converge with the music (effort, energy arc, grind, title/persona fit). Note: **"mood" is
   not a distinct available field** — V1 has energy and effort, not a separate stored mood
   signal, and the prompt must never imply otherwise.
   One thin link → stay quiet or stay light. Multiple links → lean in confidently.
   **Corrected July 27, 2026:** convergence is **not** the frequency regulator. It controls
   **fit and confidence within the current reply** only. **Cross-run repetition control is a
   separate, deferred mechanism** — see the canonical plan's deferred persistence boundary.
3. **Stat first, poetry second.** Earn trust with the run fact, then get creative.
   The anti-pattern inverts this (style first, substance never).
4. **Clarity over cleverness.** The connection must land for someone who only
   half-knows the song. No deep-cut logic puzzles.
5. **Reference spectrum (corrected July 7, 2026; SUPERSEDED for V1 on July 27, 2026).**
   Default: creative, theme-fitting references a listener instantly recognizes as the song
   (the north star above) — but recognition beats wordplay; if a reference drifts too far from
   the song, it loses the idea entirely, and special wordplay should never be forced when the
   run details don't earn it.

   **V1 RULE — replaces the older selective allowance:** V1 must **not quote, generate, or
   closely reproduce exact or near-exact lyric lines**, including proverb-grade ones. The prior
   "exact or NEAR-exact lines ARE allowed selectively" ruling **does not apply to V1**.
   Grounded **artist, song, and thematic references remain eligible** — the ban is on
   reproducing the words, not on referring to the music. The broader lyric and licensing
   question (Musixmatch/Genius, distinctive vs proverb-grade lines) stays **deferred** to the
   legal milestone; V1 simply does not depend on it.
6. **Artist reference can beat song reference.** When persona fits better than the
   track (the Larry June case), reference the artist, not the song.

## Early-user leniency (decided July 6, 2026)

Discovery problem: a new user who types music and gets ignored concludes the field is
decorative and stops using it. The feature must prove it's listening early.

Rule: keep the quality bar, change the default POSTURE.

**Exact contract (reconciled July 27, 2026 — replaces "roughly the first 10" and "week one"):**
- **`EARLY`** = total successfully saved history size **1–10 inclusive**, counting the current
  run at response time.
- **`ESTABLISHED`** = size **11 or greater**.
- **No Runner attached, or saved-history size 0** = **no stage line at all**; normal selective
  posture applies.
- `EARLY` changes **search posture only, never the quality threshold**. The agent looks harder
  for a genuine link; it does not accept a weaker one.
- A music reference is **never mandatory at any stage**. The earlier claim that a genuine link
  "almost always exists" is **withdrawn** — when no genuine link exists, silence is correct.

A forced hollow reference is worse than silence — leniency means trying harder, not accepting
worse.

## Cross-run frequency balance (separate persistence-dependent mechanism)

The agent is stateless — it cannot know it referenced music three replies in a row, so
it cannot self-regulate.

**Reconciled to the canonical future contract (July 27, 2026).** The V1 plan records the
minimal future persisted outcome as three states describing **the completed reply, not the
run's music state**:

- `REFERENCED` — the reply is known to have mentioned music, an artist, a song, or the
  deliberate absence of music.
- `NOT_REFERENCED` — the reply is known not to have mentioned any of those.
- `UNKNOWN` — the outcome cannot be established reliably, including rows predating the signal.

Rules that come with it:
- **`UNKNOWN` is never treated as `NOT_REFERENCED`.** Frequency may use only known outcomes.
- **Do not persist full AI reply text** merely to calculate reference frequency.
- The **rolling window size, physical schema, and auditable detection mechanism remain
  deferred** until the frequency feature is intentionally scheduled.
- **No frequency prompt line is part of V1.**

**Withdrawn (do not implement now):** the earlier candidate of sending
`"Music referenced in X of the last Y replies"` or `"Runs logged so far: N"` into
`buildUserMessage()`. V1 sends only the `EARLY|ESTABLISHED` stage label — never an exact count
or raw history.

**Scope limit — corrected July 27, 2026.** The three-state outcome can regulate **how often
music is referenced at all**. It **cannot** prevent repeated references to the **same song**,
because it records only whether music was referenced — not which track. Same-song variety (the
June 26 note in project_current_state.md, which matters once Spotify automation exists) would
require a deliberately designed **song-identity signal**, and **remains deferred**.

## Out of scope here

Reflective-song SELECTION from full listening history — see the addition in
parked_music_recommendation.md (Phase 6: needs run start-time + play timestamps).
