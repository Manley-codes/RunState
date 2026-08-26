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
| 12 | **Structured song history** | Provider-neutral conceptual contract approved August 26, 2026; not implemented. Nothing below works without it |
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
| 26 | Taste vs run fit | DEFAULT minimum contract approved; the collection and prompting experience remains an experiment |

> ⚠️ **This layer is still the easiest part of the system to lose.**
>
> The register already says why: *"Everything else in the music layer is the app talking. This
> is the app listening. The trust ledger cannot earn roles without it."*
>
> A selection system with no feedback path makes picks and never learns whether they landed. It
> would guess forever with no correction, which makes *"as accurately as possible"*
> unachievable by construction.
>
> **The August 26 contract now protects the minimum: optional, separate whole-run Taste and Run fit
> evidence. How and when to collect it remains open.**

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

1. **Structured song history (#12)** — the whole system's floor. Its conceptual contract is
   approved below, but no console, database, provider or mobile implementation is authorized.
   Eight items sit behind it.
2. **Minimum feedback support (#26)** — preserve the optional whole-run Taste and Run fit signals
   alongside the history so the learning loop is not retrofitted later. The collection surface can
   still wait.
3. **Trust ledger (#13)** — the first judgment history and feedback make possible, and the piece
   that makes selection personal rather than generic.
4. **The decision simulator (#28)** — prove the choices feel intelligent before paying for any
   provider integration. Both source documents recommended this independently.
5. Everything else follows playback access and the mobile client.

## Structured song-history contract — DEFAULT

**Approved August 26, 2026. Conceptual only; nothing here authorizes implementation.** The
finished app both **chooses and watches**: it may prepare or queue music, and it observes what
actually played. Those are related but different facts and must never be collapsed into one
generic fact.

### One run owns the history

- Music history for a recorded run belongs to that run's permanent phone-generated UUID. It does
  not invent a second run identity.
- It follows the parent run's local-first recovery, deletion and synchronization lifecycle. A
  music-capture or music-sync failure never blocks timing, completing or saving the run.
- Trustworthy playback observations and RunState decisions become durable locally as the run
  happens; Run Complete is not their first save point.
- Recovery continues the same run-owned history from its last durable observations. Uncertain gaps
  remain gaps rather than being backfilled as if capture had continued.
- Deleting the run removes its music history through the same local-first deletion lifecycle so
  music records cannot remain orphaned or reappear after synchronization.
- The existing high-level distinction remains honest: `MUSIC`, `NO_MUSIC`, and not recorded are
  different states. An empty detailed history never silently means no music.

### What actually played

For each playback occurrence that can be supported by evidence, preserve enough information to
answer two questions later: **what was it, and when did it overlap this run?** Conceptually that
means:

- a provider-neutral identity reference when one exists, plus a title/artist display snapshot so
  History remains understandable if the provider is later unavailable;
- provenance and confidence for the identity rather than treating manual text, device observation
  and a provider event as equally exact;
- overall capture coverage so a partial, unavailable, unauthorized or unknown record is never
  presented as the run's complete soundtrack;
- its known start and end position on the run timeline, including honest partial or unknown
  boundaries; and
- a separate occurrence when the same track plays again later in the run.

Timing is part of structured history, not an optional enhancement. Exact timestamp representation
and precision remain implementation decisions. A current free-text music note remains valid partial
evidence; it must not be silently parsed into exact tracks or playback times.

### What RunState decided

Keep RunState's own music decisions separate from observed playback. Preserve, when applicable:

- what it decided to leave, present, place or queue, and when;
- the intended role or moment;
- the reason, evidence strength and which decision rules were active at the time; and
- whether the choice was presented, accepted, declined or left without a trustworthy outcome.

A choice does not prove the song played. Playback does not prove the runner accepted or liked the
choice. Link the two only when actual evidence supports that connection. This record exists so the
decision simulator and later learning can be judged honestly after rules change.

### Minimum feedback the history must support

The minimum is one optional whole-run music-feedback record with two independent answers:

| Signal | Runner-facing meaning | Minimum stored meanings |
| --- | --- | --- |
| **Taste** | How did you like the music? | Liked it / It was okay / Not for me / Unknown |
| **Run fit** | How did the music land in this run? | It felt right / Some fit and some did not / It felt wrong for this run / No clear effect / Unknown |

These phrases define the minimum meanings, not final UI copy, enum names or database values.

Either answer, both or neither may be recorded. No answer means `Unknown`, never the middle choice or
`No clear effect`. A no-music run is not applicable, not negative feedback. Skips, saves, Energy,
Effort, pace and completion remain separate evidence and never silently fill either answer.

This is whole-run feedback, not a requirement to grade every track. It may be added later without
regenerating the run's stored reflection. The collection surface, selective prompting policy and
any future track-level feedback remain later decisions.

### Truth and privacy boundaries

- Partial observations stay partial. Known identity with unknown timing is still useful, but it
  cannot support mile, split, opener, closer or complete-soundtrack claims.
- Raw playback, decisions and runner feedback are evidence. Track roles, relationship patterns and
  trust are later derived judgments that still must clear the existing confidence thresholds.
- Store no song audio, lyrics or provider credentials in run music history.
- This contract chooses no provider and approves no transfer of playback history, provider
  metadata or feedback to Anthropic or any other service. Any future transfer requires its own
  consent and privacy decision.

### Deliberately not decided here

No table, class, API payload, provider, identity-resolution algorithm, exact timestamp format,
screen, prompt timing, playlist logic or synchronization payload is selected. This contract also
does not replace the console's current free-text note or authorize automatic music capture. How
long to retain a prepared music choice when no run ever starts also remains a later decision.

---

## Open questions

1. How and when should the optional whole-run feedback be collected, and does track-level feedback
   ever earn a separate mechanism?
2. Does the decision simulator run before or after the trust ledger exists? It is more useful
   after, but it is cheaper before.
3. `Settle / Hold / Build` was the intended internal axis feeding layer 3. Manley set it aside
   on August 16. If assembly is built without it, what supplies run intent?
