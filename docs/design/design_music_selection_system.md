---
name: design-music-selection-system
description: LIVE architecture record for the music selection system — the six layers (evidence, judgment, rules, assembly, feedback, proof) that turn Manley's stated goal of accurate song choice into a buildable structure. Created August 16 2026 so the selection goal has a named home instead of being spread across a dozen register entries.
metadata:
  type: project
status: LIVE — read this file
---

# Music selection system — LIVE

**Read this file.** Not archived, not parked.

---

## The goal, in Manley's words

Stated plainly on **August 16, 2026**, and flagged by him as something he had never written
down before:

> *"I want to construct a unique, beneficial in whatever way, a system to develop playlist or
> present song choices as accurately as possible to fit the runner."*

**Why this needed its own file.** Every earlier music statement was either a feature
(`trust ledger`, `pre-run playlist brain`) or a boundary (*"not a general-purpose music
service," "meaning-sync, not beat-sync"*). None of them named the **aim** those features serve.
Without the aim written down, the register reads as a list of a dozen unrelated music ideas
rather than one system with parts.

**The test this gives you.** For any music idea: *does this make song choice more accurate for
this runner, and through which layer?* An idea that can't answer isn't necessarily bad — it may
serve reflection, record-keeping or expression — but it is not part of this system and should
not compete with it for sequencing.

**What "accurate" means here, and what it does not.** It means fitting this runner's state,
intent and history. It does **not** mean matching steps per minute — tempo remains out by stage,
not by verdict (`music_feature_register.md` §1). And it does not mean predicting performance;
the overclaim guard holds throughout.

---

## The six layers

Numbers refer to the August 16 2026 music inventory. Status vocabulary matches
`music_feature_register.md`.

### 1. Evidence — what the system knows

| # | Item | Status |
| --- | --- | --- |
| 12 | **Structured song history** | Buildable in the console today. Nothing below works without it |
| 18 | Relationship web | ARCHITECTURE — songs connected to effort, weather, energy, finishes |
| 31 | Skip detection | The live negative signal. Spotify does not expose skips; needs first-party playback |
| 41 | All-day listening profile | Heaviest privacy item in the project; needs its own consent tier |

### 2. Judgment — turning evidence into a verdict on a song

| # | Item | Status |
| --- | --- | --- |
| 13 | **Trust ledger and track roles** | Two dimensions: what a song might do, and how confident we are *for this runner in this context* |
| 14 | Rested vs unfamiliar | Two selection inputs — time since last played, and fit to current state. A rested proven track is the strongest opener available |
| 33 | Lyric-trigger push vocabulary | Which lyrical themes recur around strong efforts. Legally gated — lyrics are licensed |

### 3. Rules — constraints on where a song may go

| # | Item | Status |
| --- | --- | --- |
| 22 | Risk-managed placement | DEFAULT. New songs mid-run only; proven tracks open and close |
| 23 | Picky gatekeeper | PRINCIPLE (personality). The app *clears* music for the run; it does not recommend |
| 24 | Phase-aware structure | DEFAULT. Opener, steady middle, lift held in reserve |

Also binding here: **Protect Momentum** (PRINCIPLE) — the only automatic actions are LEAVE and
QUEUE_NEXT. RunState never cuts a song.

### 4. Assembly — producing the playlist or the next track

| # | Item | Status |
| --- | --- | --- |
| 21 | Pre-run playlist brain | ACCEPTED north star. The main assembler |
| 25 | Deep Run Crate | CANDIDATE. A deeper mode of the same, prepared the night before |
| 32 | Live DJ adaptation | Mobile only — needs live pace |
| 29 | I Need a Push | EXPERIMENT, leading. Selection at one specific moment |
| 39 | Shared music-context service | The plumbing every assembler reads from. Design once, deliberately |

### 5. Feedback — finding out whether it was right

| # | Item | Status |
| --- | --- | --- |
| 26 | Taste vs run impact | EXPERIMENT. Two separate questions: did you like the songs, and did they fit this run |

> ⚠️ **This layer is one item deep, and it is the weak point of the whole system.**
>
> The register already says why: *"Everything else in the music layer is the app talking. This
> is the app listening. The trust ledger cannot earn roles without it."*
>
> A selection system with no feedback path makes picks and never learns whether they landed. It
> would guess forever with no correction, which makes *"as accurately as possible"*
> unachievable by construction. It is also the layer most likely to be dropped — least
> enjoyable to build, and the only one that asks the runner for something.
>
> **Do not let this layer stay at one item without a deliberate decision.**

### 6. Proof — how Manley can tell it works

| # | Item | Status |
| --- | --- | --- |
| 28 | The decision simulator | Print the choice and its reasoning instead of changing songs. Judge the decisions on paper before any integration exists |
| 16 | RunStyle Sound — personal view | The visible evidence that the system learned something real |

### Community scale — same system, many runners

| # | Item | Status |
| --- | --- | --- |
| 43 | Cross-user suggestions | ON-VISION, waiting on the platform |
| 44 | Browsable community playlists with run context | ON-VISION, waiting on the platform |

---

## What is *not* part of this system

Not demoted. These serve reflection, record-keeping, or expression, and several are among
Manley's favorites. They simply should not be confused with selection or compete with it for
sequencing.

**15** reply variety · **17** RunStyle Sound export · **19** songs a runner makes their own ·
**20** Soundtrack Log · **27** Music Agent Workspace · **30** personal anchor language ·
**34 / 35** split music context and song location · **36** reflective-song selection *(picks a
song for the **reply**, not for playback)* · **37** now-playing strip · **38** adaptive voice ·
**40** song snippet after the reply · **42** ordinary listening · **45** trail playlist beacons

---

## Machinery that already exists

Built for other purposes, structurally the same. **Extend rather than rebuild.**

**Candidate-based comparison** (`ComparisonService`) — 180-day window, cap of ten, same-route
runs inside the distance band first with similar-distance fallback, median aggregation, and
separate evidence pools per signal. Given a run, it finds comparable runs. Song selection needs
precisely that move: *find runs like this one, look at what played.*

**Confidence thresholds** (`RunStyleService`) — a descriptive minimum of three supporting runs;
comparative minimums of five with, five without, an 80% support rate, and a 30-point lead. A
calibrated answer to *how much evidence before we act*. Selection needs the same discipline so
a song is never cleared off two coincidences. Without thresholds this becomes astrology.

Both are already covered by tests.

---

## Build order implied by the layers

Not a roadmap. This is what the dependencies allow.

1. **Structured song history (#12)** — the whole system's floor. Console-buildable today, in
   Java and MySQL. Eight items sit behind it.
2. **Trust ledger (#13)** — the first thing history makes possible, and the piece that makes
   selection personal rather than generic.
3. **The decision simulator (#28)** — prove the choices feel intelligent before paying for any
   provider integration. Both source documents recommended this independently.
4. **Feedback (#26)** — must not be deferred indefinitely; see the warning in layer 5.
5. Everything else follows playback access and the mobile client.

**Note on what #12 must store.** Manley confirmed on August 16 that the finished app both
**chooses and watches** — it constructs playlists and queues songs, *and* observes what
actually played. Structured song history therefore needs room for two kinds of record: what
played and when, and what the app chose, why, and whether the runner accepted it. Building only
the first means retrofitting the second, and schema is the one thing in this project that is
genuinely expensive to change later.

---

## Open questions

1. Does the feedback layer (#26) get a second mechanism, or does one deliberately suffice?
2. What exactly counts as structured song history — title and artist only, or timestamps too?
   (Also open in `music_feature_register.md` §8.)
3. Does the decision simulator run before or after the trust ledger exists? It is more useful
   after, but it is cheaper before.
4. `Settle / Hold / Build` was the intended internal axis feeding layer 3. Manley set it aside
   on August 16. If assembly is built without it, what supplies run intent?
