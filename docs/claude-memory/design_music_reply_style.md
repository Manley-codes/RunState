---
name: design-music-reply-style
description: "Music reply craft reference (July 6, 2026; creative policy revised July 30, 2026) — subordinate to design_music_intelligence_v1.md; inclusion-first music-forward voice, three creative registers, flexible craft tendencies, and the development-phase lyric boundary are implemented and deterministically verified at 256 tests. The July 30 revised-prompt smoke ran and failed quality/trust, so these craft rules are verified as present in the prompt but NOT accepted in model behavior."
metadata:
  type: project
---

# Music reply style — craft rules for the AI agent (July 6, 2026)

How the agent should reference music in post-run replies. Extends the Phase 5 Step 1
music feature (manual input).

**STATUS: REVISED CRAFT RULES IMPLEMENTED — SMOKE RAN AND FAILED. CRAFT REFERENCE ONLY.**

The revised craft rules below — run and runner as subject, music as an inclusion-first creative
lens, light / featured / run-only registers, flexible rather than formulaic construction,
earned praise, accurate facts, short lyric references but no extended or garbled reproduction,
and no lasting pattern from one run — are **implemented and deterministically verified**
(256 tests green, July 30, 2026).

**The revised-prompt smoke has since run (July 30, 2026) and failed quality and trust.** The
craft rules are verified as *present in the prompt*; they are **not** verified as *achieved in
model behavior*. The smoke showed the model still asserting unsupported characteristics for
named tracks, collapsing openings onto one template, and closing on interchangeable coaching
filler — three things these rules exist to prevent. A separate creative-ceiling probe then
showed that removing the rules entirely did not fix it either. Treat this file as the craft
target, not as a description of observed behavior.

> **Canonical authority: [`design_music_intelligence_v1.md`](design_music_intelligence_v1.md).**
> Music Intelligence V1 planning is **COMPLETE and approved (July 27, 2026)**. That document
> is the single source of truth for purpose, contracts, prompt behavior, tests, and
> evaluation. Where this file conflicts with it, **the canonical plan wins**. This file is
> kept for craft reasoning and history; do not implement from it directly.

This document contains two distinct slices:
- **Core craft rules + early-user posture:** prompt-layer work only; no schema, console,
  provider API, or reply-persistence change. **This slice is implemented and deterministically
  verified**, executed per the canonical plan's bounded implementation contract. Manual
  evaluation is underway and failing: the first valid smoke failed quality, the policy was
  revised, and the revised-prompt smoke then failed quality and trust as well. The next live
  branch is a separately approved stronger-model control, which is not approved.
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

**Taste boundary revised July 30, 2026.** Detached grading such as "great song choice" remains
prohibited because it says nothing about the run. An **earned relational interpretation** is
allowed when it describes how the music's identity, character, tone, or mood fits this run.
That makes "the taste matched the discipline" a boundary example rather than an automatic ban:
it works only when the supplied run and music material genuinely earn the relationship.

NOTE: the mockup itself needs time-of-day + a now-playing integration — both Phase 6. **V1 has
neither** — only run date and derived season exist today. The STYLE rules below are portable to
today's manual feature; the mockup's time-of-day framing is not.

## Craft rules (for SYSTEM_PROMPT revision)

1. **No detached taste grading.** No "great song choice." Connect what the song or artist
   brings to what the run was. Earned relational language is allowed.
2. **Register calibration.** Boldness scales with the material (effort, energy arc, heat,
   title/persona fit). **Runner mood is not a stored field**; the policy may still describe
   the recorded music's own mood or tone.
   One thin link → light accent. Multiple links → featured connection. Genuine no-fit →
   run-only. Unfamiliar named music → neutral acknowledgment by default, unless even that
   weakens the reply.
   **Corrected July 27, 2026:** convergence is **not** the frequency regulator. It controls
   **fit and confidence within the current reply** only. **Cross-run repetition control is a
   separate, deferred mechanism** — see the canonical plan's deferred persistence boundary.
3. **Substance before formula.** A vivid fact often opens well, but no sentence position is
   mandatory. The response must combine or interpret supplied material rather than recite a
   stat and attach generic encouragement.
4. **Clarity over cleverness.** The connection must land for someone who only
   half-knows the song. No deep-cut logic puzzles.
5. **Reference spectrum (revised July 30, 2026).** Direct artist/song naming, title wordplay,
   artist identity or public persona, recognizable themes, the music's tone/mood/character,
   contrast, and short lyric references are all available colors. Use one coherent idea rather
   than stacking unrelated moves. Prefer titles, persona, and themes before lyrics. A few
   accurate words or brief recognizable hook may be used; long passages, multiple lines,
   invented lyrics, garbled lyrics, and confident quotation under uncertainty are prohibited.
   The pre-release legal pass must revisit this development-phase permission.
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
- For usable named music, the posture is **start from inclusion**: look for the right light or
  featured role rather than treating silence as the safest default. Run-only remains valid for
  genuine no-fit, state-forbidden music, or unfamiliar music whose neutral acknowledgment would
  weaken the reply.

A fabricated semantic connection is still worse than run-only. Inclusion changes the creative
default, not the truth standard.

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
