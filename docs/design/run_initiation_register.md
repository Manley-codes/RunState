---
name: run-initiation-register
description: LIVE register for the run-initiation system — Prepare to Detect (armed mode), provisional detection, warm-up discrimination, session orchestration, and the at-home/at-trail preparation flows. Same status vocabulary as the music feature register. Created August 4 2026 so this material has a live home rather than sitting in a parked file.
metadata:
  type: project
---

# Run initiation register — LIVE

**Read this file.** Not archived, not parked.

**What this system is.** Everything that removes friction from *starting* a run — arming the app,
recognizing when the run actually begins, and handing the runner into a prepared session.

**Where it came from.** Manley's own observation from five years of run club: the group takes off
fast, people are wearing gloves or armbands, and nobody wants to be the one standing there operating
a phone while everyone else is already running. The friction is not only pressing start — it is also
finding and starting the music.

**Its dependency, stated once.** Every entry here needs a mobile client with motion sensors,
location, and background execution. **None of it is buildable in the console era.** That is a
sequencing fact, not a judgment on the ideas — provisional detection is the best-grounded problem in
the project and also the hardest thing in the app.

---

## Status vocabulary

Same as `music_feature_register.md`: **PRINCIPLE**, **DEFAULT**, **EXPERIMENT**, **NEEDS REVIEW**,
**REJECTED**.

---

## Prepare to Detect (armed mode) — EXPERIMENT, leading

An intentional, temporary armed state. The runner signals *"I plan to run soon; be ready."* That
intent is what makes automatic detection realistic — it improves privacy, constrains battery use,
and creates a clear moment to load the expected session.

**The runner experience**

1. **Prepare once** — at home, in a parking lot, or before a run-club warm-up.
2. **Confirm only what matters** — RunState fills in known context, offers easy corrections, and
   asks at most one human-only question.
3. **Put the phone away** — visible but quiet armed state, with the session resources ready.
4. **Warm up normally** — running-like motion may become a candidate, but nothing is saved or
   announced.
5. **Begin the real run** — sustained forward running raises confidence; RunState restores the
   buffered beginning, starts the session, and gives a brief audio cue.
6. **Correct when necessary** — manual start, cancel, edit, and delete stay simple and available.

**MVP behavior**

- Explicit opt-in each time. No constant background surveillance by default.
- **A time limit.** The armed window expires automatically — 30, 60, or 90 minutes as candidate
  defaults, runner-configurable. Without it, someone arms at home, changes plans, and triggers a run
  hours later.
- Clear armed indicator on the lock screen or a system surface where the platform allows it.
- Outdoor forward-running scope first. Indoor and treadmill are unsupported or manual-only until
  proven.
- Quiet candidate detection with a short rolling buffer.
- Retroactive start from the estimated takeoff point once confidence is high.
- Audio or haptic confirmation **only after confirmation**, never for every candidate.
- Manual start remains the guaranteed fallback.

**What the mode may prepare** *(options behind it, not promises)*: location permissions and tracking
configuration, expected route or recognized start area, likely shoes and run type, audio cue
settings and headphone state, a chosen or generated music session, cached weather and offline-safe
session metadata, and the smallest useful pre-run message.

### Forgotten-headphones reminder — EXPERIMENT *(added August 31, 2026)*

Based on Manley's experience of leaving for a run without the headphones he intended to bring,
RunState may use the explicitly entered preparation or armed window to notice that the runner's
expected headphones are not connected or otherwise visible through permitted device signals. When
that happens, it may give one gentle reminder before departure when possible: *Headphones aren't
connected — do you have them?*

This is an absence-and-uncertainty inference only. RunState may know that expected headphones do not
appear connected; it does not know whether they are at home, in a car, in a bag or somewhere else,
and must never claim their location. The check is optional, dismissible and limited to the
runner-initiated preparation window. It never blocks manual start, detection, tracking, intentional
silence or a run without headphones. Exact platform signals and feasibility remain a later mobile
test rather than a promise of reliable object tracking.

### Three ways to start — EXPERIMENT *(added August 4, 2026)*

The runner chooses how the run begins:

1. **Manual** — start the armed system by hand.
2. **Voice phrase** — a set phrase through the headphones starts it.
3. **Detection** — let the system recognize the run's start.

This matters because it keeps control with the runner without abandoning the vision. On a day when
detection misbehaves, the feature still works. It also serves runners who simply do not want the app
watching for them.

### Preparation assisted by the runner model — EXPERIMENT *(added August 4, 2026)*

Once enough evidence exists, choosing a trail at home lets the app prepare against what it already
knows about the runner there.

Manley's example: *he usually struggles at this trail, so surface a note to stretch well and hydrate
— especially if the app also detects it is hot.* It could show or speak his last performance on that
route.

**Escalating alertness.** Armed at home is a low-attention state. Arriving at the recognized trail
can move the app into a more alert mode. On detecting the run: *activity started, playlist
initiated.*

**Guardrails that already apply:** calibrated language rather than performance prediction — *"heat
and hills have made this route more demanding for you"*, never *"this will be a difficult run."* And
the safety rule: never encourage a runner to push through pain, illness, or heat risk.

**Depends on:** trail choice as a feature (future), the runner model having route-level evidence, and
weather at the start location.

---

## Provisional detection — EXPERIMENT

The system should not answer *run or not run* in a single instant. It holds a hypothesis.

| State | System behavior | Runner experience |
| --- | --- | --- |
| **Armed** | Monitor a limited signal set, keep resources ready | Phone can stay away; cancellable |
| **Watching** | Buffer a candidate and collect evidence | Usually silent; no official run yet |
| **Confirmed** | Restore estimated start, begin session services | Brief "run started" cue; tracking active |
| **Dismissed** | Discard the candidate, return to armed | Warm-up or brief activity disappears |
| **Timed out** | Stop monitoring at the end of the armed window | Clear notice; no session created |

**Why buffering matters.** Waiting for confidence would normally lose the first part of the run. A
rolling buffer preserves recent location and motion samples long enough to estimate when takeoff
began. Buffer length is a platform-testing question; the strategy depends on the behavior, not a
fixed number of seconds.

**Confirmation comes from a cluster of signals, not one threshold:** sustained forward displacement
from the start area, speed and cadence consistent with running for that person, a path that expands
rather than staying a tight cluster, continuity long enough to exclude a drill, plausible direction
and distance patterns, and the absence of an immediate stop or return.

**Dismissal when evidence breaks:** movement stops quickly, displacement stays small, the path
oscillates in a tight radius, or the pattern repeatedly resembles a drill.

---

## Warm-up versus actual run — EXPERIMENT

GPS alone is not enough. Warm-up movements can resemble running in cadence and acceleration, while
trees, buildings, or group movement make location noisy.

| Signal | Warm-up tendency | Actual-run tendency | Caution |
| --- | --- | --- | --- |
| Forward displacement | Low or returns to origin | Sustained and expanding | GPS drift fakes small movement |
| Path shape | Clustered or reversing | Continuous progression | Tracks and switchbacks loop |
| Cadence | High but brief | Stable across time | Varies by runner |
| Speed | Bursting or near zero average | Sustained above baseline | Hills and crowds lower speed |
| Duration | Short drill sets | Longer continuous motion | Short runs and intervals are edge cases |
| Start-area radius | Remains nearby | Leaves the area | Small loops stay nearby |
| Stop pattern | Frequent stops | Continues after takeoff | Traffic lights interrupt |

**Rule of thumb:** enter Watching when motion resembles running. Confirm only when the runner also
travels forward in a sustained, plausible path. Dismiss when motion is brief, local, inconsistent,
or followed by a return to warm-up behavior.

**Edge cases that must be handled:** run-club takeoff with crowding and a slow first minute; a
traffic light shortly after starting *(Manley flagged this one as needing real review)*; intervals
and strides near one area; track running where loops stay geographically close; treadmill with no
forward displacement (manual-only until a separate sensor strategy exists); urban canyon or dense
trees lowering confidence; and a short accidental jog that should vanish rather than become a saved
run.

**The evaluation dataset is the whole game.** Not a large generic running archive — a deliberately
labeled collection of the confusing moments: dynamic warm-ups, high knees, jumping jacks, walking,
short jogs, run-club takeoffs, slow starts, traffic-light stops, loops, hills, and noisy GPS. Each
trial records the true takeoff time and whether the system should confirm, dismiss, or stay
uncertain.

---

## At-home versus at-trail preparation — EXPERIMENT

Different moments, different jobs.

**At home — decision support.** There is time to consider route, timing, shoes, hydration, weather,
or intent. The app supports a decision without becoming a setup form: confirm the plan, assemble
known context, ask one meaningful question, offer at most one or two useful actions, then arm.

**At the trail — get out of the way.** Phone friction matters more. Recognize the start context,
confirm only what is uncertain, ask briefly if at all, prepare silently, arm and wait. The runner
can put the phone away before drills, conversation, or the group's takeoff.

### Group meetup beacons / specific start locations — EXPERIMENT *(added August 9, 2026)*

RunState may eventually place photo-backed location beacons on a dedicated route, map, or meetup
surface so a runner can find the **specific place** where a friend, running group, race team, or
event is gathering. The problem is smaller and more concrete than navigation: the runner may have
reached the correct park or race but still cannot find the group's tent, trail entrance, parking
area, landmark, or actual setup.

**Initial shape:** a static meetup point shared by an organizer, friend, event invite, or run link,
optionally paired with a recognizable photo and a short location detail. Circular map beacons came
from an earlier map-based Start-screen exploration and remain a compatible future visual seed, not
part of the current Start/Active Run screen. They identify practical destinations, not profile
activity, social status, or automatic people tracking.

**Later extension:** an explicitly enabled live friend or group location can use the same beacon
language, but it is a separate, heavier capability. It requires accounts or invite identity,
real-time infrastructure, battery testing, clear visibility and expiration controls, and the
standing privacy/security/legal pass.

**Guardrails:** never broadcast a runner's location automatically; share only with invited people
or a defined group; make who can see the location and for how long explicit; no feed, follower
mechanics, leaderboard, or friend comparison. The value is arriving and starting with less
friction, not turning RunState into a social network or run-club platform.

**Adaptive question policy** — the progressive-input ladder in practice:

| Information | Default | When to ask |
| --- | --- | --- |
| Trail or route | Detect or suggest from location and history | Low confidence, or the runner plans elsewhere |
| Weather | Fetch automatically | Only if unavailable or conditions changed unexpectedly |
| Shoes | Suggest from recent pattern | Multiple plausible pairs, or frequent corrections |
| Feeling | Ask optionally | Useful before advice; never inferred as fact |
| Run intent | Recall or suggest | When intent changes the session or the music |
| Music | Recall last successful mode | Discovery, experiment, or explicit change |

---

## Core run-session lifecycle and time contract — DEFAULT

**Approved August 25, 2026; implementation status updated September 6.** The Android foundation now
contains the five-state enum and guarded in-memory
`NO_SESSION → COUNTDOWN → RUNNING ⇄ PAUSED → COMPLETED` ordering rules. Room version 2 implements the
durable side of that same lifecycle: the initial row is saved before Running, pause and resume update
that row and append ordered child events, and completion updates the same row with its finish and
final checkpoint. Each later change is one database transaction, so a failed child write rolls back
the parent update. `RunSessionStarter` now returns one UUID-bound `ActiveRunSession` that serializes
pause/resume/completion, writes Room first and advances the same in-memory machine only after storage
succeeds. The owner and Room remain unconnected to the screen. This contract starts when a run
becomes official.
The experimental Armed and Watching detection states above stay separate and do not create a run.

`No session → Countdown → Running ⇄ Paused → Completed`

- **No session** — preparation only; nothing has been recorded.
- **Countdown** — a visual transition. The official run has not started and no session is saved.
- **Running** — the run officially begins. Persist the local session immediately.
- **Paused** — keep the same session saved, but stop adding active running time.
- **Completed** — the hold-to-end action finishes; durably save the completed run before success
  language. Resume is an action from Paused to Running, not its own state.

**Recovery is behavior, not another stored state.** If startup finds the same unfinished session in
Running or Paused, RunState restores only information confirmed through its last durable checkpoint.
It must not invent distance or active time for an uncertain gap. The runner may continue that same
session or end and save it; neither path creates a duplicate. Discard remains an explicit open
decision tied to accidental starts and must never happen silently. The local identity format is a
separate contract defined immediately below.

**Time contract:** store the official start, optional finish, timezone at the start, every pause and
resume transition, and the last durable checkpoint. Elapsed time is start-to-finish including
pauses. Active duration includes only Running intervals and is the duration used for pace. The
visible timer is calculated from this stored timeline; it is not the source of truth. Future
confirmed detection may supply a buffered estimated takeoff as the official start without changing
this lifecycle.

**Stored time format — added August 31, 2026.** Every calendar timestamp named in that contract —
the official start, the optional finish, each pause and resume transition, and each durable
checkpoint — is stored as epoch milliseconds. The timezone at the official start is stored
separately as an IANA zone ID such as `America/Chicago`. Historical time-of-day labels must be
rendered from the run's own stored start timezone, never from the device's current timezone: a run
that was a morning run in Houston stays a morning run when the phone later travels or its zone
setting changes.

**Monotonic timing is boot-scoped — added August 31, 2026.** Monotonic values, taken from the
platform's steadily increasing since-boot clock, are valid only within the same device boot and must
never be compared across a device restart. They may sharpen ordering and elapsed measurement inside
one live session, but they are not a durable record of when anything happened. Durable recovery
therefore anchors to the saved epoch-based checkpoints rather than to any monotonic value, and the
no-invention rule above applies across the gap: neither distance nor active duration may be
manufactured for an interval the checkpoints do not cover.

---

## Local-first run identity and synchronization — DEFAULT

**Approved August 25, 2026; local identity implementation updated September 4.** The Room run row
requires canonical lowercase UUID text and uses it directly as the primary key. The initial Running
insert accepts the already-prepared identity rather than generating or replacing it, a duplicate
UUID aborts without overwriting the original row, and durable pause, resume and completion operations
update that same UUID instead of creating another run. Production generation, active-session
ownership, recovery and all synchronization behavior remain to be implemented. The server may keep
an internal database key, but the phone and server use this UUID as the run's stable external
identity and duplicate-safe synchronization key.

**The UUID is the Room primary key — added August 31, 2026.** On Android that permanent
phone-generated UUID is the primary key of the run row itself. Do not introduce a separate
auto-generated numeric Room identity beside it: a second identity would give one run two names, and
reopen precisely the duplication and mismatch problems the UUID exists to prevent. One run is one
run row. Telemetry points, splits, structured music history and the selected reflection stay owned
child records that reference that UUID rather than being folded into it. This governs the Android
store only; the Java console's existing MySQL `run_id` is a separate system and is unaffected.

Active sessions remain local in the first mobile foundation. After completion, the run carries one
of four synchronization states:

- **`PENDING_CREATE`** — durably saved on the phone; server creation is not yet confirmed.
- **`SYNCED`** — the server has confirmed the current stored version.
- **`PENDING_UPDATE`** — a locally edited synced run still needs its change confirmed.
- **`PENDING_DELETE`** — locally deleted and hidden, but retained as a small deletion marker until
  the server confirms removal so an old server copy cannot reappear.

Local storage always changes first; network work follows. Repeating an operation with the same UUID
must confirm or update that run rather than insert a duplicate. A network error leaves the operation
pending with retry information; it does not erase the run or become a permanent end state. `Run
saved` means durable on the phone, not synchronized. Retry scheduling, multi-device conflicts,
accounts and server implementation remain later decisions.

---

## Mobile foundation architecture boundary — DEFAULT

**Approved August 26, 2026; implementation status updated September 6, 2026.** This decision
established the Android-first direction. Implementation later began after separate explicit
approval. The Android/Kotlin/Compose project exists as a static shell plus isolated in-memory
state-order rules. Room 2.8.4 and KSP now back a version-2 database with the parent `runs` table and
ordered `run_transitions` children; both schemas are exported under `android/app/schemas`. A separate
Android CI job runs the JVM tests, assembles the debug app and compiles the instrumented-test APK on
every push and pull request; it does not run an emulator. Verification passed with 47 JVM tests and
19 local emulator tests.

**The durable lifecycle and its in-memory rules now meet in one active-session type.** The parent row
uses canonical UUID text as its primary key and stores the official start, IANA start timezone,
latest checkpoint and optional finish. Pause and resume append sequence-ordered child rows while
updating the parent state; completion writes a finish only on a Completed row and creates no duplicate
run or completion event. Room transactions prevent half-saved lifecycle changes, and the explicit
version-1-to-2 migration preserves old rows without inventing finish times or transition history.
`ActiveRunSession` owns one immutable UUID, holds one per-instance lifecycle mutex and never caches a
row; every action validates memory, updates Room and only then advances the same state machine.
Production wiring must still guarantee exactly one owner and register `MIGRATION_1_2` when its
database builder is introduced. Cancellation after a successful durable write but before its
in-memory transition remains a recovery concern. Active-row discovery, relaunch or process-death
recovery, the foreground service, telemetry and the server boundary remain unimplemented. The Java
console and MySQL database are unaffected.

- Build Android first with Kotlin and native Android UI/platform services.
- Room is the authoritative on-phone store. The interface observes durable state; it does not own
  the run clock or keep the only copy of an active run.
- A foreground service owns the active Running/Paused session, its timer, GPS capture and durable
  checkpoints so navigation or screen recreation cannot end the run.
- Local save is the source of truth. The minimum server boundary handles reflection generation and
  later duplicate-safe synchronization; it is not required for timing or completing a run.
- Provider and model credentials remain off the phone.
- Run saving and reflection generation remain separate operations. A reflection failure never
  rolls back or weakens a saved run.
- Android platform text-to-speech is the first-demonstration speech source for the factual receipt
  and exact stored reflection text. If it is unavailable or disabled, text remains sufficient and
  the run continues; hosted or persona-specific speech remains a later evaluation.
- Full RunStyle calculation stays local and is not sent to the reflection model.
- The production reflection model is selected through bounded quality and trust testing. The model
  used to help write code is not automatically the model used inside RunState.

**Database versioning — added August 31; implementation updated September 4, 2026.** Android database
version 1 remains the initial baseline, and version 2 adds the optional finish plus ordered pause and
resume history through the explicit `MIGRATION_1_2` migration.
Exported Room schemas are preserved in the repository as the record of what each version looked
like, and every future version increment requires an explicit written migration. Destructive
migration must not be used as a shortcut: dropping and rebuilding the database would silently erase
recorded runs, which contradicts the local-save-is-the-source-of-truth rule above. The generated
version-1 schema is preserved byte-for-byte beside version 2 under `android/app/schemas`.

Accounts, multi-device conflict resolution, broad cloud history and provider integration remain
outside this foundation.

---

## Time-aligned run telemetry and completed-run contract — DEFAULT

**Approved August 26, 2026. Future-mobile contract only; nothing is implemented.** GPS points,
automatic splits and song-play events use the same permanent run UUID and the same saved run
timeline. No subsystem invents its own version of when the run happened.

### Location observations

Each durable location observation preserves enough source evidence to explain the route later:

- the permanent run UUID and stable order within that run;
- when the platform observed the point, plus a monotonic ordering/time marker when available;
- latitude, longitude and reported accuracy; and
- optional platform-provided altitude, speed or bearing when available. Missing values remain
  unknown rather than becoming zero.

Only observations received while the session is `Running` may add route distance. `Paused`
intervals remain part of elapsed time but add no active time, route distance or split progress.
Every observation keeps whether it was accepted for route/distance use; rejected or uncertain
points never silently influence the displayed run. Exact GPS-jump thresholds are a Phase 4
implementation-and-field-test decision, not part of this contract.

Observations become durable locally in bounded checkpoints while the run is active. Recovery
continues the same ordered stream under the same run UUID from the last trustworthy checkpoint.
Duplicate delivery or retry must not create a second point. GPS gaps remain gaps: RunState never
invents coordinates or claims a precise path through an unobserved interval. The completed run
records whether telemetry was complete, partial or unavailable so later screens and evidence can
stay honest.

### Distance, pace and automatic splits

- Total distance is derived from accepted Running observations. The visible distance counter is a
  view of that saved evidence, not a separate source of truth.
- Average pace uses active duration and accepted distance. Elapsed duration remains available
  separately and includes pauses.
- Splits are automatic running data, not music data. They are derived from the accepted cumulative
  distance timeline in the runner's selected mile or kilometer unit.
- Each full split preserves its distance boundary, boundary time on the shared run timeline, active
  duration, pace and derivation version. Completion also preserves the final partial split; a run
  shorter than one unit therefore still has one partial split.
- The finalized split snapshot used by History and reflection evidence is stored with the completed
  run. Future algorithm changes do not silently rewrite an old run. Any later repair or
  recalculation must be explicit and versioned.
- Split calculation is derived local work. Failure to prepare it never unsaves the run; it retries
  from the same durable accepted points without duplicating the run. If the GPS evidence is too
  incomplete, splits are unavailable rather than fabricated.

Song location is derived only when a trustworthy song interval overlaps this same run timeline.
One song may span several splits and one split may contain several songs. A missing or partial GPS
or music timeline cannot support a precise mile claim.

### The completed run

Completing a run changes the existing durable session to `Completed`; it does not copy it into a
new record or generate a new UUID. Before `Run saved`, the phone must be able to recover:

- the UUID, official start, finish, start timezone, pause intervals, elapsed duration and active
  duration;
- accepted distance, average pace when measurable, display unit and telemetry coverage;
- durable location observations and the finalized-or-retryable split result;
- captured run context such as route, surface, company, shoes and weather when known;
- optional pre/post Energy and Effort, preserving unknown when unanswered;
- structured music history when available; and
- local synchronization state.

The selected reflection remains a separate one-to-one child prepared only after this local save.
Late Energy, Effort or music feedback updates the same UUID and may mark the run pending update, but
never regenerates a `READY` reflection. Deleting the parent run removes its owned telemetry, splits,
music history and reflection through the same local-first deletion lifecycle.

A run may complete with partial GPS or unavailable music. Those limitations reduce which claims
RunState may make; they do not erase an otherwise trustworthy run. `Run saved` means this local
completed-run composition is durable, not that every optional enrichment exists or cloud sync has
finished.

---

## Session orchestration — DEFAULT

On entering Running — by manual start now or confirmed detection later — restore any trusted
buffered beginning, begin the timer and route recording, persist the local session immediately so a
crash cannot erase the start, play a restrained audio or haptic confirmation, begin the selected
music if ready and permitted, attach
prepared context, and switch from detection sampling to run-tracking sampling.

**Critical-path rule — PRINCIPLE.** Timer, route recording, session persistence, and manual control
are the core. Music, weather, AI messages, and visual flourishes are enhancements that **must fail
independently.**

| Failure | Required behavior |
| --- | --- |
| Music unavailable | Start tracking anyway; explain afterward without blocking the run |
| Network lost | Use cached context, save locally, sync later |
| Weather fetch fails | Omit the observation; never invent or delay |
| Audio output unavailable | Use haptic or system confirmation; keep tracking |
| Location confidence poor | Keep observing or fall back to manual; never claim a precise restored path without evidence |
| App suspended | Respect platform limits; state clearly what can be relied on |

---

## Open questions

1. The simplest name for the armed mode — Prepare to Detect, Ready for Run, Prepare to Run, or
   something else.
2. Which outdoor starts define initial scope: solo, run-club, trail, track, or a subset.
3. How long the armed window stays active, and whether the runner chooses or accepts a default.
4. Traffic-light stops shortly after takeoff — flagged by Manley as needing real review.
5. Whether Prepare to Detect is a RunState feature or the product's new center. Manley's position,
   August 2026: **let it play out naturally rather than deciding now.** Noted because the source
   strategy's final recommendation reads as the second, which would displace *log a run → track how
   you felt → learn something meaningful* as the core.
