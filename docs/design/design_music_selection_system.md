---
name: design-music-selection-system
description: LIVE architecture record for the music selection system — the six layers (evidence, judgment, rules, assembly, feedback, proof) that turn Manley's stated goal of accurate song choice into a buildable structure. Created August 16 2026 so the selection goal has a named home instead of being spread across a dozen register entries.
metadata:
  type: project
status: LIVE — read this file
---

# Music selection system — LIVE

**Read this file.** Not archived, not parked.

## October 19 — snippet completion resumed as the sole task

Local implementation now separates musical meaning from playback availability. A negotiated
v2 receipt can authorize the selected observed recording without claiming that Musixmatch
analysis or the writer supplied a creative landing. Exact accepted curated timings remain
first choice, then matched LRCLIB hooks, timed line windows and a short opening fallback.
The latter is explicitly not a verified chorus. Selected identity remains fixed with the
saved response; this is not permission to swap an unrelated song after writing.

Official Spotify App Remote replaces the unproven different-track media-session path when
connected. It has an explicit one-time connection action and prior-grant-only reconnection.
Premium is confirmed. The initially reported connection was clarified: the phone never reached
`READY`, repeatedly showed `NEEDS_AUTH`, and safe diagnostics found `UserNotAuthorizedException`
with no remembered grant. The existing Edge dashboard matched the configured public client ID,
redirect and Android SDK selection, but had no Android package registered. The correct
`com.runstate.mobile` package and Gradle-verified public debug fingerprint were added and saved
under the existing setup authorization; the dashboard confirms them. No secret was viewed.
This corrects registration, not the unproven phone grant or playback. Clearer failure copy and
safe category diagnostics are present; fresh authorization and the exact different-track,
stop/restoration and full voice handoff still need proof. Latest complete software checkpoint
is 201 backend/699 Android tests per variant and all three APK builds, with no failures/errors/skips;
the earlier 13 silent device cases passed. The latest authorization-status copy is installed, and
the user's Connect retry after the dashboard correction is pending. Current receipts and the
public fingerprint are in `project_current_state.md`.
No routine Git mutations, backend deployment or paid-provider calls have occurred in this slice.

## October 18 debrief — investigation parked for return soon

Manley asked to pause work here while preserving fresh feedback from his real run. Reflection
required Retry once, then he liked it; an instrumental replaced the song snippet while listening
to GNX. The selected recording and exact failure/fallback reasons have not been inspected for
that run. Do not attribute it automatically to the later Lose Yourself test's empty analysis or
Spotify loading failure. The run preceded the Room 16 installation; actual sources/permissions
must be checked before claiming why that run used a fallback. The full debrief is in
`project_current_state.md`.

His proposed pipeline is **prepare a group of suitable songs during the run → End Run selects
among them → reflection and short musical ending**. Early candidate studies and final-facts ranking
already implement much of this. One final winner currently serves all post-Energy branches.
The next design improvement to consider, not yet implemented, is choosing a **reflection/song
pair with a usable hook**, rather than selecting solely for meaning and discovering afterward
that it cannot play. A next suitable prepared candidate could be useful if the best match lacks
analysis/timing/playback, with honest fallback reasons and no fabricated source meaning.
Finish is a trigger, not a workaround for Spotify's loading capability. Coordinated background
jobs can do this; multiple autonomous agents are not necessary for this version. Preserve the
accepted writer/pairings and resume reliable transport/source coverage work without reopening an
unbounded creative-evaluation loop. No music/provider/phone changes were made in this debrief.

## Chosen-song playback and wider snippets — Building, installed/deployed foundation, October 18, 2026

The extension now carries exact observed Spotify recording identity, album/duration and default-off
future-run permission for broader lookup/loading. It is installed on the Moto at Room 16 and Live
on Render as `9b274e0`; health confirms snippet/study/preparation/selection capability 1. Software
and six focused silent Moto cases passed. A real chosen-song load timed out; phone loading and
complete pairing remain unresolved, and listening is unverified.
The older five-current-track limitation below describes the preceding selector checkpoint.

For the chosen song, optional LRCLIB lookup uses title/artist/album/duration, never a device URI or
run facts. Title/artist/album agreement and duration within two seconds are required, but do not
prove identical audio editions. Published lines can suggest a two-to-ten-second repeated/title-hook window.
The existing writer receives a description of that landing before it writes. No full LRC or raw
new lyric payload is sent to the model, persisted or returned to Android. Timings remain approximate;
the algorithm cannot promise a complete sung statement or a good audible transition.

Accepted curated timings stay fixed. Only baseline-verified recording IDs can use those timings for
cross-track loading; a different complete recording may instead obtain source-matched timing.
Missing or conflicting identity keeps conservative current-track behavior or no passage. Playback
prefers a prepare command, confirms the exact target and bounds the short passage. Play-from can
briefly sound at loading, restoration is best effort, and the previous queue is not guaranteed.
These are source/transport mechanics, not a DJ, playlist generator or proof every song has a hook.

The source-class check matched Lose Yourself to LRCLIB `10913690` and a 99.340–102.140 s title-hook
window; it is not an audio audition. Final checks passed 193 backend tests and 672 Android tests
per debug/release variant, all without failures/errors/skips, plus three APK builds (release
unsigned). All 60 controller cases passed after new user-seek regressions exposed and corrected
overbroad pending-load cleanup. The in-place Room 16 upgrade passed five storage/permission cases
and an actual database check: all old fields across ten tables, including 33 saved runs, were
unchanged; new recording fields remained unknown and old-run lookup stayed off. A sixth isolated
settings case passed without changing real preferences. Phone loading, complete hosted generation
with broader timing and listener quality remain unverified. The first actual-recording test captured
paused Lose Yourself and obtained LRCLIB `18848635` timing (99.260–101.480 s). Both hosted attempts
stopped before audio: Musixmatch matched the song but its analysis response was an empty array,
so the diagnostic attempt returned an ordinary reflection without selected song/cue/snippet.
Capability discovery succeeded; advertised Spotify controls are not proof of loading. The original
soundtrack entry also returned empty analysis; its demo was excluded. A music-only prepare probe
failed with `TRACK_LOAD_TIMEOUT`, still on the previous paused recording, no verified target/clip;
cleanup sent pause but returned `INTERRUPTED`/`restored=false`. After a refreshed session, default
prepare and explicit play-URI also timed out on the previous paused song; redundant prepare-URI
and unsupported media-ID probes were omitted. No parser/source-alias fix or working cross-track
playback is established. Final Spotify state was paused; test package/database/private copies
were removed, main app reopened and every old field across ten tables/33 runs remained intact.
The specific LRCLIB approval left the future-run setting off. Official Spotify Android App Remote
is a next candidate, not implemented or proven available for this account; analysis coverage needs
its own sourced fallback. Full receipts belong in
[project state](project_current_state.md).

## Current reflection selector — Ready to test, October 18, 2026

This bounded implementation serves item **36**, choosing a song for the post-run reflection.
It does not implement the broader playlist or live-DJ system below. The new capability is installed
and deployed; an older server still receives the compatible single-song fallback.

1. With run-scoped and current observation/sharing choices enabled, shortlist up to 12 distinct
   Spotify title/artist identities observed PLAYING in that run. Do not infer a full listen,
   preference or performance effect from a metadata observation.
2. After 30 active seconds, prepare eligible songs sequentially using matched Musixmatch context
   and the accepted Opus study. Room 15 adds durable per-identity claims, retains
   existing Room 14 records and does not fabricate historical candidates.
3. At completion, compare available matched meaning/themes/moods against final run facts and
   starting Energy in at most one additional model call. Choose one candidate, not a new unheard song.
4. Feed that winner into the unchanged accepted writer, producing the four finishing-Energy
   branches. The chosen song is shared across branches, not selected separately for each answer.
5. Persist the exact choice with the reflection/History. Final source lookup has a 30-second budget,
   ranking a 10-second cap, both within the existing 90-second total deadline. One available source
   skips ranking; a ranking failure uses the first available matched source. No available source
   uses an ordinary reflection without invented music meaning. Older servers keep the single-song path.

This can improve fit beyond automatically taking the first song, but it has not yet demonstrated
better choices in a real run. Up to 12 source/study attempts cost more than one; discarded or
unused studies can still incur charges. Capacity/deadline limits mean not every reported song is
necessarily analyzed. Software checks cover evidence membership, deduplication, fallback,
old-data preservation and saved-choice stability. The real-provider synthetic selection check below
passed; actual run selection and reflection quality still need their own checks.

Snippets remain five curated current-track passages. Selection does not authorize queue changes
or locate a hook in an arbitrary song, so a selected reflection song may have no audible passage.
The final Moto build is installed and Render commit `7447c65` is Live with selection capability 1.
Backend 173 tests, Android 645
tests per variant/all three APKs and 23 final silent Moto cases passed. The fake-HTTPS two-song
integration selected the second candidate and reopened exact saved text without another request.
All 32 saved runs and every old table field survived Room 14→15. A later silent real-hosted check
prepared three songs and chose the second, Lovely Day, for fictional morning-run facts. Its
`CONTEXT_MATCH` result proves at least two matched analyses were ranked, not that all three were
necessarily compared. Final generation took 16,269 ms; the identical cached response took 176 ms.
No audio, Spotify control or real run write occurred; all actual saved fields stayed unchanged.
These are controlled checks, not genuine-run or listener-quality proof. Full receipts and status definitions are in
[project state](project_current_state.md).

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
| 12 | **Structured song history** | Broader contract approved August 26; partial Android observations exist. Complete occurrences/coverage/feedback remain unimplemented; bounded reflection ranking is Ready to test above. |
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

**Approved August 26, 2026.** This remains the broader conceptual contract. An October 12
opt-in Android first pass now stores media-session snapshots under the run UUID and labels them
partial in History; it does not reconstruct complete song occurrences, control playback, collect
feedback or transmit observed music to the reflection backend. The October 13 Room v10 extension
now implements the narrow, separately approved one-snapshot sharing exception below; the rest of
the captured history stays local. The
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

**Current clarification — October 16:** the October 15 remembered-settings direction supersedes
the early reset-every-run wording below. Observation and optional sharing preferences are now
remembered; each new run still stores its own permission flags, and old records gain no consent.
Changing/clearing the reflection connection resets sharing. No preference was changed during the
October 16 Spotify work. The existing title/artist cloud path is technically implemented but its
provider clearance is unresolved: Spotify's API/SDK rules restrict metadata ingestion into AI,
analysis and automated audio segues. The Android-observation distinction and exact questions are
recorded in [the Spotify checkpoint](research_music_reflection_generation.md#spotify-feasibility-checkpoint--october-16-2026).
Do not expand that path, add SDK-derived metadata or infer music/run relationships on an assumption
of permission. The current local reliability/launcher work neither clears nor expands cloud use.

**Historical October 13 limited sharing approval:** Manley approved a separate optional, per-run choice to
share one observed Spotify title and artist with RunState's backend and Anthropic for a reflection.
It is off by default, resets for the next run, and does not grant sharing for old records. The
implementation records the choice separately from local observation and selects one eligible
same-run PLAYING Spotify snapshot: the newest eligible event within run bounds, with both title
and artist. Both observation and sharing flags are required. Room v10 defaults all migrated consent
to false. Selection is frozen in the reflection evidence and reused on retry/History; unreadable
or absent evidence remains UNKNOWN. The draft clears on observation-off, access revocation and
each new run, and is hidden without the reflection bridge. `MEDIA_SESSION_PARTIAL` provenance limits it to reported metadata;
it does not prove a complete song, timing, a soundtrack, or an effect on the runner. No audio,
lyrics, notification contents, route coordinates or full listening history are included. All other
observations remain local. Playlist decisions and snippets remain separate work.

A Moto run with 135 local observations confirmed the five-field MUSIC/MEDIA_SESSION_PARTIAL
packet with title/artist present and no artist description, and reached a READY non-test reply.
That reply omitted the song. This verifies the limited input path, not full-song timing, song use
in wording, music causation, audible duck/resume or snippets. New History labels describe stored
frozen context without claiming it was sent successfully or used in the final wording.
That saved live reply/evidence predates the final GPS timing guard and remains unchanged. New
reflection requests omit active duration for PARTIAL/UNAVAILABLE GPS rather than treating a
reconstructed timer as proof of uninterrupted tracking; fixture/COMPLETE GPS retain known timing.

**Continuing connection/audio pass:** Optional sharing keeps the same two per-run consent flags
and one frozen partial Spotify title/artist. The prepared hosted path uses a separate encrypted
per-demo connection code, never an on-phone Anthropic key. Generation has no provider request
without a selected connection, while local Energy/Effort still save. Clearing the connection clears
the draft sharing choice before the settings write waits; access revocation, observation-off and
new runs also clear it. Existing READY replies and their frozen music evidence remain unchanged.
Connection setup discloses that new requests and explicit History retries send saved facts and
any separately permitted frozen music context to the chosen endpoint. Saving the setup also clears
the sharing draft before waiting, including through rotation; it creates no new sharing consent.
Speech ownership/deadlines are hardened, but actual hearing and music duck/resume remain unverified.
No song audio enters AI or a new playback path. Observed Spotify metadata does not grant snippet
rights; automated segues/previews need a permitted provider route before implementation. No snippet
is built. Integrated emulator screen/database checks passed 88 cases, and the final setup-dialog
check passed after rebuilding all APKs. Render sign-in/form preparation is complete, but no service,
charge or secret was created. User commit/push and private connection setup precede hosted fake
staging. Final sharing-draft changes passed both 284-test JVM suites and the latest debug APK is
installed on the Moto; test packages/temp device files are removed and local servers stopped.
The local Java 17 remote-fake auth/header-deadline check passed; Docker/public/standalone delivery
remain unverified, as do actual music hearing/ducking and snippets.

- Partial observations stay partial. Known identity with unknown timing is still useful, but it
  cannot support mile, split, opener, closer or complete-soundtrack claims.
- Raw playback, decisions and runner feedback are evidence. Track roles, relationship patterns and
  trust are later derived judgments that still must clear the existing confidence thresholds.
- Store no song audio, lyrics or provider credentials in run music history.
- Beyond the explicit October 13 one-title/artist exception, this contract approves no transfer
  of playback history, other provider metadata or feedback. Any broader transfer requires its own
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
