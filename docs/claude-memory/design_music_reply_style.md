---
name: design-music-reply-style
description: "Music reply craft reference (July 6, 2026; post-control direction added August 1, 2026; four-example calibration set approved August 2) — subordinate to design_music_intelligence_v1.md. The implemented July 30 policy failed live quality/trust tests; the July 31 Opus control showed a reachable creative core but weak selection, compression, and trust. The approved design target is creative wording that lands cleanly, music/run fusion, optional rather than mandatory techniques, separate creative/trust/readiness grading, and UI-open reply length. No prompt or code revision has implemented it yet."
metadata:
  type: project
---

# Music reply style — craft rules for the AI agent (July 6, 2026)

How the agent should reference music in post-run replies. Extends the Phase 5 Step 1
music feature (manual input).

**STATUS: POST-CONTROL DESIGN AND FOUR-EXAMPLE CALIBRATION SET APPROVED — NOT YET
IMPLEMENTED. CRAFT REFERENCE ONLY.**

The **July 30 baseline craft rules** — run and runner as subject, music as an inclusion-first
creative lens, light / featured / run-only registers, flexible rather than formulaic
construction, earned praise, accurate facts, short lyric references but no extended or garbled
reproduction, and no lasting pattern from one run — are **implemented and deterministically
verified** (256 tests green, July 30, 2026). The August 1 direction later in this file is not.

**The revised-prompt smoke has since run (July 30, 2026) and failed quality and trust.** The
craft rules are verified as *present in the prompt*; they are **not** verified as *achieved in
model behavior*. The smoke showed the model still asserting unsupported characteristics for
named tracks, collapsing openings onto one template, and closing on interchangeable coaching
filler — three things these rules exist to prevent. A separate creative-ceiling probe then
showed that removing the rules entirely did not fix it either. The July 31 Opus control
produced more promising creative material but still failed its approved trust and quality bar.
Treat this file as the evolving craft target, not as a description of observed behavior.

> **Canonical authority: [`design_music_intelligence_v1.md`](design_music_intelligence_v1.md).**
> Music Intelligence V1 planning is **COMPLETE and approved (July 27, 2026)**. That document
> is the single source of truth for purpose, contracts, prompt behavior, tests, and
> evaluation. Where this file conflicts with it, **the canonical plan wins**. This file is
> kept for craft reasoning and history; do not implement from it directly.

This document contains two distinct slices:
- **July 30 core craft baseline + early-user posture:** prompt-layer work only; no schema,
  console, provider API, or reply-persistence change. **That baseline slice is implemented and
  deterministically verified**, executed per the canonical plan's bounded implementation contract. Manual
  evaluation is underway and failing: the first valid smoke failed quality, the policy was
  revised, the revised-prompt smoke failed quality and trust, and the stronger-model control
  also failed its approved bar. The August 1 direction and August 2 four-example calibration set
  remain design-only. No further live run is approved.
- **Cross-run frequency balance:** a separate mechanism that cannot be prompt-only because the
  app stores neither AI replies nor a "music was referenced" signal. The canonical plan
  documents its future three-state outcome boundary; the **physical schema, rolling window,
  detection mechanism, and frequency implementation all remain deferred.**

References below to “Phase 6” mean later mobile-era data availability. Under the current
forward path, UI contract work and Spring Boot come before that mobile implementation.

## Post-control direction — approved August 1–2, 2026

This direction replaces the old goal of an “organized professional” voice and the blunt
“clarity over cleverness” hierarchy. Its four calibration replies were approved August 2. It is
**not yet a prompt or code change**.

### Voice target

The reply is fun, run-connected, deliberate, and polished. It should feel strategically and
cleverly constructed, not formal, scholarly, or like a song review. The short name for the
target is **creative wording that lands cleanly**.

### Clever clarity

Cleverness and clarity work together. Aim for a clearly understandable connection with room
for moderately to strongly clever phrasing. Most runners should understand it immediately or
after one quick beat. It may reward recognition, but it must not require rereading or an
explanation. Do not remove a strong creative idea merely to make the reply more literal.

### Fusion instead of explanation

When the material supports it, take a small recognizable shard of the music — a title idea,
persona, theme, tone, or brief accurate lyric fragment — and use it inside the run statement.
Do not routinely name the artist or song and then explain what it means. The run and music
should feel transformed together rather than delivered as two separate topics.

### Creative palette, not formulas

Persona tags, title fusion, performance-first openings, direct naming, contrast, run-only
reflection, and other techniques are all available. None is mandatory, none is always best,
and none receives a fixed sentence position. The goal is varied judgment, not rotating through
a visible template list.

### Reply-card fit result and what remains open

The August 2 low-fidelity fit test passed: the expanded card comfortably held the four-line
Larry June sample without crowding its separate music source. That density is a reference for the
approved replies, not a hard four-line, sentence-count, or word-count rule. The current
recommendation is to show the reply expanded immediately after a run and allow the runner to
collapse it; the collapsed state needs a clearer expansion cue.

The current implementation still asks for 2–3 sentences, but that remains a **provisional
implementation limit**, not a final product decision. Dynamic text sizing, contrast, tap targets,
screen-reader behavior, motion, and final compact-versus-expanded content remain later UI checks.
No prompt change is authorized by this fit result alone.

### Grading separation

Future diagnostics record creative value, factual trust, and app readiness independently. An
unsupported detail makes the current wording unready for the app, but it does not erase a
useful creative technique. Final release acceptance still requires trustworthy wording.

### Approved four-example calibration set — completed August 2, 2026

This is the **approved design source**, not prompt text and not permission by itself to change
`RunAgent.java`. Technique names help audit range; they are not a rule that each reply must use
exactly one technique or that a good technique may never repeat. The set stays compact so examples
teach voice without becoming a new checklist.

1. **Embedded reference — Manley-authored wording approved as a target.**
   - Source facts: 3.02 miles in 28:37 at 9:29 per mile; Low to High energy; Moderate effort;
     clear, 95°F; dirt trail; solo; no PR; Eminem — *Lose Yourself*.
   - Candidate: “Low energy in 95-degree heat had the odds looking slim, but you never lost the
     will, and you came back stronger than you left. 3.02 miles complete!”
   - The line works at two speeds: ordinary English without music knowledge, with an extra layer
     for someone who recognizes the source. The UI may show the source separately, but the spoken
     wording must still stand on its own.
2. **Short, hard run — Manley-authored wording approved as a target.**
   - Source facts: 2.01 miles in 19:59 at 9:57 per mile; Low to Spent energy; Heavy effort;
     clear, 91°F; dirt trail; solo; no PR or comparison; Kanye West — *Highs and Lows*.
   - Candidate: “2 miles trapped in 91 degrees, and you ignored your low energy—if you don’t
     already have a passion for running, it looks like it’s starting to grow.”
   - The line compresses the hard conditions into one warm interpretation and does not force a
     music reference merely because music was recorded.
3. **Performance first, music lightly supporting — Manley-authored wording approved as a
   target.**
   - Source facts: 4.60 miles in 40:29 at 8:48 per mile; Okay to Feeling Good energy; Heavy
     effort; clear, 76°F; flat concrete trail; solo; new longest-distance PR; Key Glock —
     *Let's Go*.
   - Candidate: “4.6 miles at 8:48—your longest run yet, Let’s Go! And you finished feeling good.
     Yea this the flex you think it is.”
   - The separate beats are intentional: the title supplies the burst, the finish state supplies
     the payoff, and the final sentence carries the song's flexing character. Do not merge them
     merely to normalize grammar or sentence flow.
4. **Ordinary run with a light music connection — Manley-authored wording approved as a target.**
   - Source facts: 2.75 miles in 28:49 at 10:29 per mile; Okay to Feeling Good energy; Easy effort;
     cloudy, 64°F; flat paved park loop; solo; no PR or comparison; Larry June —
     *Life Is Beautiful*.
   - Candidate: “2.75 miles at 10:29, just a vibe under a cloudy sky, and you came back feeling
     good. Ain’t life beautiful.”
   - The run remains ordinary without becoming insignificant. The title lands as a short final
     beat instead of becoming a description of the song.

The prompt examples should not carry every edge case. The evaluation fixtures remain responsible
for distinguishing track-not-noted, explicit no-music, not-recorded, legacy notes, injected text,
PR priority, stage posture, and comparison behavior.

Unfamiliar named music also remains an evaluation safety case, not a calibration example. When no
trustworthy connection is available, V1 may use a run-only reply instead of spending words merely
acknowledging that music existed. A future multi-song system should prefer a better-known fitting
candidate. Cross-genre transfer remains unproven and should be tested later rather than forced into
this first calibration set.

**Fixture-integrity warning:** candidate 1 uses the exact S1 facts and the same base facts as S12.
If it is later approved for the production prompt, S1 and S12 must be replaced before another
evaluation. Removing one displayed stat would not solve the contamination; the model would still
have been shown the same run and music pairing.

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
4. **Creative wording that lands cleanly.** Cleverness and clarity work together. Most runners
   should understand the connection immediately or after one quick beat; it must not require
   rereading or an explanation. Do not flatten a strong idea merely to make it more literal.
5. **Reference spectrum (revised July 30, 2026).** Direct artist/song naming, title wordplay,
   artist identity or public persona, recognizable themes, the music's tone/mood/character,
   contrast, and short lyric references are all available colors. Use one coherent idea rather
   than stacking unrelated moves. Prefer titles, persona, and themes before lyrics. A few
   accurate words or brief recognizable hook may be used; long passages, multiple lines,
   invented lyrics, garbled lyrics, and confident quotation under uncertainty are prohibited.
   The pre-release legal pass must revisit this development-phase permission.
6. **Artist reference can beat song reference.** When persona fits better than the track (the
   Larry June case), the artist may be the better material. This is an option, not a default.

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
