---
name: design-run-response-system
description: Locked-in design rules for the post-run contextual response system
metadata:
  type: project
---

Every successfully saved run gets a contextual response. No exceptions.

## Completion rhythm follow-up — October 21, 2026

**Complete for agreed scope — installed; controlled phone facts pace and shorter handoff accepted.**
Manley confirmed “The pace feels right” and “The transition feels connected.” Preserve this tuning;
the broader completion flow remains Ready to test for real-run generation/preparation delays.
Manley judged the earlier
replay “pretty good,” with facts slightly too fast and the music entrance insufficiently rhythmic.
Completion facts alone now use 0.90 cloud playback speed at pitch 1, or 0.864 local fallback
instead of 0.96, through dedicated `speakCompletionFacts` / `playCompletionFactsAudio` paths.
Countdowns, distance announcements, Energy invitation, reflection voice, backend voice IDs,
accepted writer and clip remain unchanged. Retain the current 480 ms reflection entry envelope.

The intentional reflection-to-song handoff is **150 ms** in this installed revision, replacing the
earlier **650 ms** setting while preserving that accepted historical baseline below. The goal is
a brief connected breath; SDK preparation, seeking and playback can still make actual onset later.
Instrumentation now separates playback-command and first PLAYING-confirmation gaps, measures
facts duration, and observes pause through reflection. No new data sharing or creative rules.
Final checks passed 737 unit tests per variant, all three APKs and five focused silent Moto cases
(11.516 s); both Moto APKs were updated in place. The new exact-text native audition passed in
29.735 s, with RUN facts lasting 7,705 ms, a 6 ms facts-to-reflection platform call, and song-controller,
Play-command and PLAYING-confirmation gaps of 152/552/769 ms. Those are code/state boundaries,
not exact audible onset. Across the expanded hold observation through reflection, all 187 samples
were PAUSED and none PLAYING; no post-facts preparation wait occurred. Original PLAYING was restored
with 88 ms position error. Saved data/preferences remained unchanged. No new AI writing, schema,
backend or Git change. The user's acceptance applies to this controlled facts-pacing/musical-handoff
check; full real-run generation latency and prolonged preparation waits remain unproven.

## Untimed check-in and shared completion audio — October 21, 2026

**Ready to test — installed on Moto; controlled checks passed with qualified positive feedback.**
The separate facts-pacing/shorter-handoff revision above is installed and accepted for its bounded
controlled phone scope; broader completion and untimed waiting still need real-run verification.
This supersedes the six-second no-selection rule for new completions. The first deliberate
Energy answer, including Not sure, chooses the branch without a deadline. Still-PENDING,
unselected and unanswered legacy rows are promoted only when their activity is not finalized.
Already selected/READY reflections retain their exact text and branch.

Save Activity remains independent of answering or waiting for AI. After durable finalization,
an unanswered activity selects the neutral branch and leaves the Energy answer null. Not sure
is an explicit unknown answer, stored distinctly. Generation can finish afterward; local recovery
reconciles a missed Save callback, and failed generation retains the existing explicit retry path.
A later feedback change cannot replace the frozen reflection. The durable untimed marker uses
the existing deadline column; no schema change is needed.

Facts and reflection use separate speakers under one completion audio owner, retaining the music
pause during the check-in and any quiet preparation gap. Exact selected-text voice preparation
may overlap facts, without synthesizing the unused Energy branches or regenerating the writing.
Stop, departure, backgrounding or lost focus cancels the old delivery. The safe snippet handoff
releases the shared pause; queued callbacks from the old owner cannot revive it. Previously
playing music may resume only while the original recording/session/position is still owned.

Preserve the accepted writer and the chosen run/custom reflection voices. This installed checkpoint
used a **650 ms** reflection-to-song gap; the newer installed revision above uses **150 ms**. A musical
tail, new snippet endpoint and creative-rule changes are outside
this slice. An untimed answer can prolong the music pause, so Save/leave/Stop remain exits.
Debug stage/elapsed-time diagnostics add no song data, reflection text or new sharing.
Final unit suites passed 737 tests per variant, all three APKs built, and 59 distinct silent
emulator cases passed across the main run and an isolated cleanup-timeout retry. Moto installation
preserved all 44 completed activities and every old field checked; backend unchanged.

The controlled native audition passed in 29.438 seconds using RUN facts and the exact accepted
Lizzo reflection, with no new AI writing. Facts-to-reflection platform handoff was 5 ms; the native
player-start stage was 29 ms after facts completion, not an exact audible gap. Song-controller
handoff followed reflection completion by 653 ms. All 67 observed pre-reflection samples were
PAUSED (61 during facts); no idle wait was sampled because voice preparation had already finished.
The short hook completed and original PLAYING state/position were restored within 100 ms. The
user requested replay, not an acceptance verdict. That identical replay also passed in 29.928 s,
with a 4 ms platform handoff, 653 ms song handoff, 65 paused/zero playing observations before the
reflection and original PLAYING restoration within 155 ms. All old saved fields remained unchanged.
After replay, Manley called the result “pretty good” and requested the speed/rhythm adjustments
above. That qualified feedback does not establish broad acceptance of the new local timing.
Real-run generation latency and audible silence remain unverified; see `project_current_state.md`
for the full receipt and preflight harness repair.

## Early song study before completion — approved October18,2026

Manley approved moving matched song understanding earlier, with possible early AI charges for
discarded work and no guarantee that the first eligible song will still be current at the end.
The local implementation waits30 active seconds, requires both frozen/current music choices,
access and the same hosted destination, then durably claims one eligible observed song once.
The early request contains no run facts or audio. Completion supplies fresh final evidence to
the unchanged four-branch writer; a valid prepared study removes the need to study that song
again. Source/notes can remain in bounded server memory for reuse, as disclosed in DATA_PRIVACY.

Failed, expired or process-lost preparation can fall back to cold same-song generation with
extra AI charges. There is no automatic background retry or new song ranking. Changed connection
destinations suppress the pinned music; an older server receives no early upload. Recorded
playback remains current-song only, so a later different track can skip the clip. Selected voice
preparation, audible facts/intake ownership and650ms reflection-to-song pause remain unchanged.
Final160 backend/609 JVM tests per Android variant and packaged JAR/three APKs passed. Moto13→14
preserved32 old records/fields and left the new claim table empty; silent53 checks passed with5
gated skips and no provider calls. Actual fields were unchanged afterward and temporary copies/
test package removed. User commits/push, Render deployment and the real warm timing check remain
pending; no early provider call, measured latency improvement or live activation is claimed.

## Accepted writer and selected voice preparation — October 18, 2026

The accepted song-study/four-branch writer is connected to normal hosted requests; it supersedes
the rejected v1.8 composition below. Preserve the accepted prompts, Opus5.5 configuration and
examples in `music_reflection_accepted_baseline.md`. The new normal-hosted reflection passed
Manley's listening judgment, although he prefers the original wording. He finds the pause from
facts to reflection too long. This is different from the650ms reflection-to-song gap, which stays.

The bounded delivery change in progress prepares only the exact selected reflection's voice in
memory as soon as that text is available, even if remaining facts or visual intake still hold
audible playback. Preparation must not speak early, synthesize all four Energy candidates,
regenerate the reflection or change the selected text. Playback reuses that synthesis once.
Stop, background, navigation, destination/retry/run replacement invalidate obsolete preparation;
returning to the screen must not revive it. No audio file or persistent speech cache is added.

Preparation passed591 JVM tests per variant/three APK builds, was installed and passed27 silent
Moto checks with5 gated skips. The first timed live check completed facts/custom voice but failed
on a newly measured+1ms Spotify duration change; cleanup paused correctly. No facts-to-reflection
interval was emitted because the timing report followed the failing clip assertion. Manley still
finds the wait too long. Final-cleanup timing reporting and strict±1ms rounding handling now pass
596 JVM tests per variant/three APK builds;±2ms still rejects. The update was installed without
altering32 real saved records. A music-only probe finished/restored exactly with0ms duration delta;
it did not reproduce the1ms event, retest fresh reflection delivery or measure a facts-to-reflection
gap. This does not speed the fresh Musixmatch/song-study/writer work itself, and no shorter
observed wait is claimed. The later approved early song-study feature above is separate from
this smaller voice-preparation change.

## Historical music composition repair — October 17, 2026

Manley rejected the first Stronger audition as sounding the same as the pre-Musixmatch replies.
Song analysis must be a central creative input, not optional decoration on a stats recap. Quality
currently takes precedence over generation speed. The local v1.8 backend now retains optional
bounded Musixmatch song-meaning prose as well as themes/moods, then separates composition,
editing and final factual review. Generated connections/drafts never become source evidence;
only the reviewed four-branch result can leave the provider. The original facts voice, custom
reflection voice, intake/selection ownership and Spotify handoff stay unchanged.

No lyric body/quotation/entity fields are retained. Double-quoted passages are removed from the
optional meaning prose, but this cannot guarantee absence of unquoted lyric fragments or resolve
the provider-contract uncertainty. Original wording and factual safeguards still apply. Old
absolute delete-the-music/never-explain-meaning tests are not restored. Actual outputs, observed
failures and subjective quality are recorded separately in the research notebook; a successful
schema/request alone is not proof of a good reflection. Manley still rejects the writing; the
local pipeline remains unfinished and is not approved for hosted activation. He paused work
after a separate freer-persona experiment, also unaccepted and not installed. Resume the music
reflection's creative/factual quality before considering a deployment.

## Spoken delivery roles — clarified October 17, 2026

The first generic voice announces saved run facts only: current implementation covers distance,
elapsed time and average pace when complete saved evidence supports it. Energy selections,
Effort, song commentary and interpretation belong outside that factual receipt. Unavailable
metrics, including the not-yet-built calorie estimate, stay omitted. The selected reflection
then uses the custom ElevenLabs voice, followed by the appropriate existing music path.
The receipt has its own modestly quieter/slower local delivery; it does not change phone volume.
After Manley's Stronger replay feedback, selected Spotify passages now have a **650 ms pause**
after natural reflection completion. Speech focus is retained until the handoff; Stop, close,
replacement or focus loss cancels it, and current eligibility is rechecked before playback.
Both cloud reflection voice and local fallback use this owner-controlled pause. The delay is
native playback timing, never a prompt instruction or a change to the saved reflection text.
Manley confirmed the revised three-part sequence flowed well. A soft Spotify passage fade remains
open; current transport does not provide RunState an isolated gain control. Playback-stop
regressions, the intermittent failed check and subsequent verified replay are recorded in
`project_current_state.md` and `research_music_reflection_generation.md`.

## First functional Android reflection version — approved direction October 3, 2026

**October 14 Spotify follow-up — one physical compatibility pass:** Manley started Spotify, then
a second fresh Sample (about 0.01 mi FIXTURE, 9.400 s elapsed/5.379 s active) finalized
READY/NO_SELECTION/non-FAKE with no Energy answer or music observation/sharing:

> You covered 0.01 miles of sample distance in under 10 seconds. A brief run, wrapped up at 7:05 p.m.

Manley heard facts → reflection → instrumental; Spotify became quieter or paused and returned
normally by itself. Focus logs showed reflection abandonment followed by cue music focus 21 ms
later, cue abandonment after 10.488 s and Spotify full-gain request 106 ms afterward; Spotify was
PLAYING. RunState issued no Spotify play command or song/audio/model sharing. A read-only audit
found 22 completed/0 active with every prior 21 run/reflection/transition/location field hash
unchanged. Temporary copies were removed, stay-awake 0 restored and no GPS service remained.
No code edit or test rerun was needed. This one Moto/session pass does not cover wider audio
compatibility, calls/Bluetooth or distance accuracy. v1.3 now has two bounded no-selection outputs;
the first cold-reopen proof below remains separate. The later bounded native Active pass is
recorded in `project_current_state.md`; larger Active design review remains open.

**October 14 deployment follow-up — one live v1.3 Sample/cold-reopen check passed:** Manley manually deployed
latest GitHub `master` and reported Render Succeeded/Live at `bcde0c4`, replacing `85cbfab`.
Public `/health` returned 200/ok. That commit contains prompt `mobile-reflection-v1.3`; the Dockerfile
copies its source resources and runs Maven test/package. Latest build logs were not inspected;
health does not certify writing quality. A subsequent connected-Moto Sample separately produced
this exact READY/NO_SELECTION/non-FAKE reply, with post-run Energy null and music sharing off:

> You completed a sample run in just over 9 seconds at 6:57 p.m. That's time on your feet.

The saved facts were 9.384 seconds elapsed, 5.378 seconds active, 16.134 m FIXTURE and
America/Chicago timezone. The reply is factual and omits invented/unanswered feelings/questions.
One fresh run was used, without an Energy answer or assistant Retry; a billed-request count was
not independently established. Cold restart retained Online service, exact History text and all
request/finalization/evidence fields. Stable read-only main/WAL checks found 21 completed/0 active,
all prior 20 IDs retained and every stored run/reflection/transition/location field hash unchanged
across restart. Temporary copies were removed, no persistent file/credential/source edit/Git
mutation occurred and no tests needed rerunning. This is one bounded writing pass, not general
quality or hearing confirmation for that first check. The subsequent Spotify/audio proof above
adds one-Moto compatibility and v1.3 hearing; the later native pass is recorded in current state.

**October 14 continuation — GPS capture passed; bounded wording/navigation polish:**
The latest Moto walk saved 0.08 miles of partial GPS over 2:24 elapsed and reopened its exact
live no-selection reply and one-section recorded path after process restart. Fresh fixes span
the tested 110.786-second screen-off interval with zero stale decisions after the timed CPU wake
safeguard. This verifies capture for that device/session, not distance accuracy or wider reliability.
All measured GPS runs still receive the deliberate PARTIAL label; their frozen reflection evidence
and History active time stay conservative. See `project_current_state.md` for the field aggregates.

Prompt `mobile-reflection-v1.3` makes the no-selection boundary explicit: recognize known
run facts, leave post-run feelings out, do not comment that they are unknown/unreported/unrecorded/
unselected, and do not ask how the run felt or request Energy. Other branches and the sample,
partial-GPS and partial-media limits are unchanged. No stored reply is rewritten. The resource
header is the version source; the production provider's transport/configuration behavior is unchanged.
All 45 backend tests passed, including the captured-request checks for the new instructions.
Those checks do not prove generated writing compliance. A private local setup attempt made zero
AI requests because it could not obtain usable configuration. The subsequent `bcde0c4` deployment
above now includes v1.3; its subsequent bounded live Sample writing verification passed.

History now scrolls to the top only when its first run identity changes. Initial loading/rotation
and Energy/Effort updates keep the reader's position; navigation does not auto-expand, speak or
regenerate a new row. All eleven focused History checks passed, with 287 JVM tests per Android
variant and all APK builds. The only necessary new source file in the GPS repair was its focused
wake-lease test; this wording/navigation polish introduced no new source file or schema.

**October 14 instrumental continuation — approved source, integration and Moto hearing passed:**
Manley's final decision is to use a short original instrumental until a suitable actual song and
permitted playback route are chosen. Completion sequences saved receipt → exact reflection voice
→ one 10-second bundled cue, only after successful reflection TTS `onDone` and while that same
run/attempt remains visible. Plain History autoplay remains voice-only. A visible 48 dp `Stop
audio` button cancels the sequence. Stop, Start another run, History navigation, lifecycle pause
and disposal/rotation close owned playback and reject late callbacks; there is no automatic resume.
Failed/interrupted/timed-out TTS, denied focus and player initialization failure do not produce a cue.
MediaPlayer prepares asynchronously with transient music focus, pause-on-duck behavior, volume
0.22 and a 15-second deadline. The cue is labelled `Eleven Music` on Completion.

One new bundled MP3 was generated once through ElevenLabs Music v2; the app makes no ElevenLabs
request and stores no new credential. No new reflection generation, network request, Room field
or schema migration is introduced. Provenance lives in `research_music_reflection_generation.md`.
The final build passed 287 debug/287 release JVM tests and all three APK builds. All 76 emulator
screen cases passed, including ten new audio checks and actual-player playback. The first full
run exposed a real Stop touch intercepted by the fixed Start another run footer; a synchronized
isolated rerun confirmed it. A measured footer sibling/weighted scroll viewport fixed production
layout; the real touch assertion remains, and the isolated check plus full 76-case rerun passed.
Moto installed without reset. A new 0.05-mi Sample run received one hosted v1.2 live response
family; Manley heard receipt → exact live reflection → instrumental and said it was "all in
order and landed perfectly." No Energy selection or music sharing was needed. Cue playback is
local, adding no generation call. Normal stay-awake 0 was restored with no GPS service/CPU lock.
Actual-song pairing, Spotify content playback and wider external audio compatibility remain unverified; the
temporary cue is not evidence that those are implemented. This is a good commit point; the user
still owns Git. This listening result predates the v1.3 deployment; the later v1.3 check adds one
writing/cold-reopen pass, not a new listening confirmation.

**October 13 continuing hosted delivery — live phone text, audible sequence and reopen verified:**
Render now generates real reflections for the Moto over HTTPS. The phone
accepts an explicit HTTPS endpoint and a 43–128-character URL-safe per-demo access code, distinct
from the backend-only Anthropic key. AndroidKeyStore AES-GCM encrypts both settings; their
preferences are excluded from backup, corrupt settings fail closed and application-owned writes
survive rotation. Debug may use the local bridge; either build may use explicitly configured HTTPS.
Release permits Internet but denies cleartext and has no local-network permission. No secret is
embedded in the APK. The same local owner saves Energy/Effort without a configured connection;
generation waits for a selected connection, and Completion/History expose honest setup/retry states.
Retry preserves the saved run, frozen evidence and any already READY exact text.
Setup now discloses that the chosen endpoint receives new requests and explicit History retries
containing saved facts and any separately permitted frozen music context. Saving clears the
sharing draft before the app-owned write waits, including across rotation.

Remote Java startup is deliberate (`--remote`): exact public Host and Bearer validation precede
generation-body parsing. Two simultaneous provider calls, 256 retained IDs, 32 queued tasks and
64 KiB request/success-response bodies bound the demonstration server. It supplies internal HTTP,
not TLS; private HTTPS ingress and single-instance deployment are required. Duplicate protection
is memory-only and resets with the server. Render Starter at $7/month plus AI usage is running
in Oregon with `/health` liveness monitoring. Initial Docker/FAKE staging passed all 45 Linux
tests and public HTTPS health/auth/candidate checks. After code rotation, a labelled phone FAKE
Sample reply/reopen also passed. Manley then privately entered the hosted Anthropic key and the
FAKE launch override was cleared. The same committed `85cbfab` reached Live in 18.5 seconds
at October 13, 11:33:22 p.m. CDT, using the Dockerfile's `--remote` CMD and Anthropic provider
on internal port 10000. No hosted key value entered the assistant session/project.
The container has empty ENTRYPOINT plus CMD and finite JDK header/read/write/idle deadlines.
An earlier direct Java 17 private
remote-fake process with those exact startup flags returned health 200, missing-auth 401 and
authenticated invalid-body 400; it closed an incomplete header after 15.1 seconds. The ephemeral
code/process were cleared and stopped. The subsequent actual Linux image/public HTTPS/phone
checks establish hosted delivery; body-upload/deadline behavior through public ingress is not
separately stress-verified. Health monitoring alone does not prove provider, voice or GPS quality.

Speech focus, callbacks and completion now belong to each utterance, with separate initialization
and playback deadlines. Receipt cleanup releases ownership; engine failures/navigation do not
replay a failed reflection or crash the journey. Automated verification now includes 45 backend
tests on Windows/Render Linux, 284 JVM tests per Android variant, 88 full emulator screen/database cases, all APK builds
and one Moto encryption check. After the final disclosure change, three APKs rebuilt and one
focused connection-dialog case passed. A simulated GPS MainActivity run preserved distance through
paused movement, automatically selected/stored a FAKE no-selection reply, and reopened its exact
text and partial path in History. History conservatively kept partial-GPS active time unknown.
The refreshed Active screenshot was visually checked without overlap. Both JVM suites reran with
284 passing tests after the last sharing-draft edit; the latest debug APK is installed on the Moto.
Test packages/device review files were removed, the headless emulator closed and local servers
stopped with no listeners on 8787/8797, and the local USB 8787 forwarding is removed.
The first hosted live Sample run (11:35 p.m., 0.08 mi/57 s elapsed/44 s active) automatically
received a real reply that qualified sample distance/Tuesday night. Manley missed its audio and
requested a repeat: 0.02 mi/24 s elapsed/10 s active received another real no-selection reply,
and he confirmed hearing saved facts followed by that reflection. Its latest exact text, metrics
and encrypted online connection survived a cold restart with no FAKE label. This turn added one
FAKE plus two live Sample records, with prior data retained and two paid phone generations.
No music sharing/audio/coordinates were sent in these checks. The phone remains on latest
expanded History and USB stay-awake is restored to original 0.

**Writing/navigation follow-ups:** The repeated `no_selection` reply said the after-run feeling
"remains unrecorded", despite the existing instruction not to mention unanswered Energy. This is
a bounded writing-quality miss, not a factual/transport failure. No prompt fix was made; keep
the exact stored reply unchanged. History reused an older scroll anchor after a new save, with
the newest row above and visible after scrolling up; optional navigation polish, no data loss.
Continuous screen-off GPS, outdoor accuracy, music duck/resume/snippets and general writing
quality remain unverified. Work is paused at Manley's request; on resume, outdoor locked-screen
GPS validation comes first, with these polish items later.

**Earlier October 13 computer-connected checkpoint:** A newly generated Anthropic reflection reached the Moto phone and its
exact selected text survived late Energy feedback and History after a cold restart with the backend
stopped. The first generated-text/persistence milestone is now exercised. Live checks exposed unit
and partial-GPS wording errors; the backend now supplies calculated distance/time/provenance facts
with prompt version `mobile-reflection-v1.1`. The subsequent separately approved music-sharing
pass uses v1.2 and Room v10: both OFF-by-default per-run flags allow one eligible Spotify
title/artist with MEDIA_SESSION_PARTIAL provenance, while other observations remain local.
An actual Moto request confirmed that frozen context and a READY live reply, which omitted the
song. These bounded checks do not establish general writing quality, audible music handoff or
the full music-aware demonstration. Details are in `project_current_state.md`.

**October 13 optional music-input boundary:** The saved run's local observation and separate
sharing choices must both be true. Select the newest eligible same-run PLAYING Spotify snapshot
inside the official run bounds, with a known title and artist. Send only that one title/artist,
MUSIC state, MEDIA_SESSION_PARTIAL provenance and a null artist description. An empty/unreadable
capture remains UNKNOWN, never NO_MUSIC. The evidence snapshot is immutable under the UUID;
retry reuses it and History only reads it. No audio, lyrics, notification contents, route points
or full listening history enter this packet. This metadata cannot prove a full song, exact playback
timing, a soundtrack or music causation, and the model may omit it. Room v10 migrates older sharing
choices to false; draft sharing clears on observation-off, access revocation or a new run. The UI
hides the choice when its reflection bridge is unavailable. History labels describe the frozen
context without asserting a successful request or music use in the reply. Verification includes
35 backend tests, 280 JVM tests per Android variant and 20 permanent phone checks; actual audible
music delivery/snippets remain unverified.

**GPS timing boundary:** For GPS PARTIAL or UNAVAILABLE coverage, new reflection evidence keeps
active duration null even when stored transitions are complete. A reconstructed active timer
cannot prove uninterrupted tracking; elapsed duration remains available from saved start/finish.
Fixture and COMPLETE GPS may retain known active duration. The earlier live music check and its
exact stored reply predate this safeguard; never rewrite that frozen evidence or regenerate its
reply to apply it. No extra paid call was made for this correction.

**Target: an end-to-end generated-text feature, not storage-only or a canned-response demo.** Manley
accepted the current creative baseline for implementation and wants to improve it after the feature
works. Keep Anthropic as the writing provider. Bring live text generation forward using explicitly
controlled run/music inputs; real GPS and automatic playback capture are not prerequisites.
This changes the earlier delivery order, not the factual boundaries or save-before-reflection rule.
No code is implemented by this brief; propose and approve bounded edits before execution.

### Existing integration and what changes

`src/com/runstate/RunAgent.java` already calls `https://api.anthropic.com/v1/messages`, reading
`ANTHROPIC_API_KEY` from the environment. The source currently names `claude-haiku-4-5-20251001`,
a 256-token response budget and a five-second request timeout. These are verified code settings,
not a new model recommendation, availability check or suitable limits for four mobile candidates.

Reuse relevant provider/client lessons, not the console class wholesale. Implement a small backend
boundary so private credentials never enter the Android app. Choose the backend stack, runtime
configuration, bounded timeouts/output budget and local test connection in the implementation plan.
Do not open an unauthenticated public paid-generation endpoint; public hosting is not required for
the initial local demonstration. Recheck current official provider documentation when coding.

Update the writing instructions for the mobile use case using the research note's October 3
baseline and accepted examples. Audit rather than inherit the old 35-word cap, instruction to speak
with unqualified authority, Effort-based immediate interpretation, and examples whose statements
go beyond their supplied facts (e.g. a solo run does not prove an empty park). Preserve useful
anti-invention and free-text-as-data protections. Do not change console behavior silently; identify
any shared refactor or console prompt edit explicitly in the proposed code slice.

### First-version boundary

1. Android durably completes the run first. Build an immutable evidence snapshot from that saved
   record: identity, timestamps/timezone, metrics with units and provenance, known pre-run Energy,
   and explicitly controlled music context or the separately consented partial Spotify snapshot
   defined above. Unsupported fields remain unknown. Do not send full
   RunStyle, raw GPS history or unrelated personal data. History-based claims require supplied
   verified comparisons; otherwise omit them.
2. Backend calls Anthropic with versioned writing instructions and representative examples. Request
   four explicitly keyed texts for Spent, Feeling Good, Powered Up and no selection, using one common
   factual foundation. One structured request is the starting proposal, not four mandatory API calls.
   Each candidate is conditional on its branch, not a claim that the runner reported all four states.
3. Validate response structure, branch completeness and basic content constraints before accepting
   it. Mechanical validation cannot certify subjective quality or eliminate every hallucination.
   Malformed/empty/provider-error responses must not become READY. No automatic creative judge or
   unbounded repair/regeneration loop in this version.
4. Android retains the existing optional Energy selection/no-selection behavior and commits only
   the selected branch, exact text and finalization time under the original UUID. Use the durable
   PENDING/READY/FAILED contract below; protect against duplicate requests, stale replies and retries
   overwriting a finalized result. Plan pending-work and selection recovery across screen recreation
   and app restart explicitly, without claiming in-memory coroutine ownership survives process death.
5. Show the selected text in a minimal Run Complete view and read that same text in Log History,
   including after reopening. API failure leaves the run saved and offers bounded reflection retry,
   never a second run save. Do not present a fallback as a successful generated reflection.

**Text first is an intentional staging decision.** ElevenLabs remains the provisional audition tool;
spoken receipt/reflection and polished animation in the full delivery contract below remain later
unless separately scoped. Define a clear text-stage equivalent of the existing quiet no-selection
window in the UI slice; do not invent a required new question or silently change Energy semantics.
Optional Effort does not select/regenerate the immediate response and must not block this milestone.
The accepted examples span different runs and are not a ready-made four-branch family for one run.

### Small implementation check, not another writing campaign

Reuse the existing ordinary, Spent and verified-longest cases, plus unknown/no-music and missing-input
cases. Check all four output branches for consistent facts, appropriate Energy and no invented history.
Use mocked provider responses for automated tests. A separately approved, bounded real Anthropic
smoke test checks the actual integration; report its sample quality honestly, not as broad validation.
Exercise timeout, offline/error/malformed output, retry, duplicate/stale response handling, unchanged
saved-run counts, exact selected-text persistence and reopening History without regeneration.
The first functional milestone is complete only when newly generated text reaches Android and the
stored selection survives reopen. Storage tests or a backend-only success do not complete that milestone.

**Keep later:** GPS/music capture, Spotify snippets, ElevenLabs API integration, new runner questions,
fine-tuning, automatic creative grading, full visual redesign and unrestricted artist research.
Physical Android access is required before real outdoor GPS/music-observation testing, not before
this emulator-based generated-text version. No new 36-output gate or manually polished four-example
family is required before implementation. This does not remove basic factual/safety acceptance checks.

## Mobile Run Complete delivery contract — prototype-validated August 22, 2026

**STATUS: CORE NATIVE DELIVERY IMPLEMENTED OCTOBER 13, 2026; OCTOBER 21 UNTIMED/SHARED-AUDIO
UPDATE IS INSTALLED AND READY TO TEST.** The October 21 section supersedes older deadline/audio ownership
details below. This contract governs the mobile flow; the shipped console agent uses the later
console implementation description.

### Sequence

1. The ended run is durably protected locally before response preparation. Explicit Save Activity
   finalizes it separately; neither local protection nor finalization claims server synchronization.
2. Metrics appear immediately with a short audible factual receipt built from saved run facts. That
   receipt is deterministic confirmation, not the contextual reflection.
3. All three post-run Energy choices are visible and prominent while the receipt plays. Effort stays
   behind a quieter `EFFORT +` action that opens a dismissible lightweight sheet. Either, both or
   neither may be recorded.
4. While the runner sees the choices, RunState silently prepares a four-response Energy family from
   one shared factual and evidentiary foundation: `Spent`, `Feeling Good`, `Powered Up`, and a
   first-class no-selection response.
5. Choosing Energy immediately sends that choice into the visualizer. The selected post-run state
   lands beside the pre-run state, and its prepared reflection arrives directly out of the same
   choreography. There is no added selection timer after absorption; unfinished generation or
   voice preparation can still require a wait.
6. New completions wait indefinitely for the first deliberate Energy or Not sure answer. Save
   Activity without an answer selects the neutral branch after durable finalization, leaving the
   Energy field unanswered. The former approximately six-second rule remains historical only.
7. Only the selected response is revealed, spoken and stored in Log History; the unused candidates
   disappear. A late Energy answer still saves for future learning but never replaces the response
   already committed to that run. No answer is required to save the activity or receive its neutral
   response once preparation succeeds.

### Durable selected-reflection record — DEFAULT

**Approved August 25, 2026; native Room storage implemented October 12–13.** Each completed
run may own exactly one selected reflection linked by the run's permanent UUID. Treat it as a
one-to-one child of the run; whether it eventually uses fields on the run or a separate local table
is an implementation decision.

The reflection has three states:

- **`PENDING`** — the run is safe, but its reflection is still being prepared or selected.
- **`READY`** — store the selected branch (`Spent`, `Feeling Good`, `Powered Up`, or no selection),
  the exact final text, and when it became final.
- **`FAILED`** — the run remains safe; only reflection preparation failed. Retry moves this same
  record back to `PENDING` and never saves or duplicates the run.

`PENDING → READY` is the normal path; `PENDING → FAILED → PENDING` is the retry path. Once `READY`,
the selected text is permanent for that historical run. Late Energy or Effort saves as future
evidence without replacing it. History reads the stored text exactly and never regenerates on open
or reload. Unused candidates are discarded. If the app closes while `PENDING`, it may continue the
same reflection work against the same saved run. Deleting the run removes its reflection through
the same local and synchronization lifecycle so no orphaned reply remains. Speech audio does not
need persistence; it may be produced later from the exact stored text.

### Preconstructed Energy response family — LOCKED DIRECTION

The four candidates share the same saved run facts, key moments and approved music/persona evidence,
but they are **not** restrained variations of one template. Energy may change the interpretation,
emphasis, structure, persona use, opening, middle or close. The differing material does not have to
sit at the end. The branches represent alternative conditional truths; they are not claims that are
meant to be simultaneously true.

The creative boundary is deliberately narrow:

- objective run and music facts remain consistent across all four candidates;
- each Energy candidate must honor its named state without turning that state into a grade of the
  run;
- no candidate may invent a run event, song event or other concrete evidence; and
- the no-selection candidate uses only known evidence and never calls attention to a skipped answer.

Within that boundary, the responses are allowed to diverge creatively. Evaluation should inspect
all four candidates side by side for factual consistency and judge each one for creative quality.

### Effort has a separate mobile job — LOCKED DIRECTION

`Smooth / Working / Heavy / Empty tank` remains optional and is still captured behind `EFFORT +`,
but it does **not** select, modify or regenerate the immediate post-run response. Effort is stored as
longitudinal evidence: it can support future comparable-run messages, Quiet Gains, runner learning,
RunStyle or Run Rhythm analysis, and a later response that accurately references how a past run
landed. Any cross-run or pattern claim still has to meet the applicable evidence threshold.

This is the product division: **Energy helps RunState speak to this run immediately. Effort helps
RunState understand the runner across runs.**

### Prototype-validated delivery states

- The deterministic factual receipt is spoken once after save succeeds. The fixture wording is:
  `Run saved. 4.2 miles in 38 minutes, 17 seconds.`
- `EFFORT +` opens a dismissible lightweight bottom sheet containing `Smooth / Working / Heavy /
  Empty Tank`. A choice returns to a compact state such as `EFFORT · HEAVY`. No group label is used.
- Effort stays available after the reflection arrives. Late Effort saves without changing,
  regenerating or replaying the reflection.
- Save failure keeps the metrics visible, with `RUN NOT SAVED` and `RETRY SAVE`; Energy, Effort and
  reflection remain locked until retry succeeds.
- Reflection failure preserves `RUN SAVED` and offers `REFLECTION UNAVAILABLE`, `RETRY REFLECTION`
  and `VIEW IN LOG HISTORY →`. Retrying reflection never saves or duplicates the run again.
- Run Complete and RunState Log History are separate views inside one permanent phone shell. History
  reads the same stored run record and exact reflection text. Back restores the completed screen
  without saving, rebuilding, replaying speech or resetting inputs.
- The reflection speaks when it arrives. **October 6, 2026 update:** Manley wants opening a run in
  History to speak its stored reflection automatically as well. History must read that stored text,
  never regenerate it. The role of `REPLAY` remains for later UI refinement. As of October 12,
  opening a ready History row invokes Android speech on the exact stored text when a voice engine
  is available. As of October 13, a newly completed run also speaks the exact selected reflection
  once on arrival; reopening an older saved run stays quiet, and leaving/backgrounding stops it.
  The subsequent October 13 pass implements the deterministic spoken receipt from saved facts.
  At that historical checkpoint, receipt completion/failure/interruption started the quiet window
  once; the October 21 untimed policy above supersedes that timer. The reflection cannot
  reveal or overlap that receipt. Room v9 preserves window state and recovery never replays a
  consumed receipt. Speech requests temporary Android audio focus and returns it on completion,
  error or stop. Physical-phone audio quality has not been checked.

### Post-run voice contrast — DIRECTION, NOT YET PROTOTYPED *(added August 31, 2026)*

Keep the deterministic factual receipt neutral, familiar and consistent. Give the selected
contextual reflection — especially the API-generated unique reply — the more characterful personal
delivery through a clearly different voice, cadence or both. The voice still reads the exact stored
text and never changes, embellishes or overrides its facts.

This contrast belongs only to the saved post-run reflection. It does not define live-run
interruptions, milestone cues, coaching, alerts or navigation speech; those remain separate ideas
with different timing and safety constraints. The direction came from a run during which Manley was
using Strava, Nike Run Club and adidas Running at the same time: one app appeared to switch from its
usual factual voice to a markedly different voice after final save, possibly with an Australian
accent. The source app and accent are unconfirmed, so neither the provider nor a particular accent
is locked by this observation.

### Processing consequence

Durable run saving and response preparation remain separate operations. The response family may
begin only after local save succeeds; AI or speech failure must never undo or block the saved run.
The implementation may prepare the family in one structured request or another measured form, but
the runner-facing behavior above is the contract. A visible `BUILDING REFLECTION` state is needed
only when the selected response is not ready when its reveal is due.

**Canonical energy labels (harvested July 7, 2026 from archived ENERGY_STATE_DESIGN.md):**

| Internal level | Pre-run label | Post-run label |
|---|---|---|
| Low | I'm Here | Spent |
| Moderate | Ready-ish | Feeling Good |
| High | Let's Go! | Powered Up |

One shared internal scale, different labels per moment; skipped answers stored null and
omitted from output. Labels are working wording, not final. Founding principle: **energy is
an observation, never a run-quality grade** — Spent after a marathon is an achievement, and
"energy decreased → bad run" must never appear (this principle later grew into the
energy-vs-effort split in design_effort_cost.md). NOTE (RESOLVED July 27, 2026): the shared
three-level energy domain above is **reaffirmed and closed**. The UI State Scan concept's
FOUR states are **superseded as a domain change**; later label and presentation refinement
remains UI work and does not alter these stored meanings. Decision record:
`design_music_intelligence_v1.md`.

**Core principles:**
- Never imply a run was bad — the runner showed up, that counts
- PRs are still celebrated separately from Your Run Style
- The feeling question appears after the run summary (same log flow, not a separate screen)
- LOW → HIGH energy lift always adds a second line on top of the main message
- Never mention below-average performance — stay quiet if numbers are down

**Current console implementation:**
- `RunAgent.buildRunResponse(Run)` is the public entry point (single Run argument — no avgPace
  or avgDistance params; those rolling-average inputs were removed with Comparison Repair V1)
- Tries Anthropic API first (claude-haiku-4-5-20251001); 5-second connect timeout on the shared
  HTTP client; 5-second per-request timeout on the Anthropic call
- Falls back silently to `buildFallbackResponse()` on any failure — the app never breaks because
  the API is down or the key is unset
- Candidate-based comparison lookup runs locally before the API call; `ComparisonService.analyze()`
  selects up to 10 comparable runs (same route within the distance band first, then
  similar-distance fallback, 180-day window), forms
  separate energy and effort pools, and returns only positive/explanatory signals — each with
  its own evidence count and confidence tier. Nothing from below-average runs reaches the prompt.
- Response is generated only after the run is durably saved; the `saveAndCompleteRun` boundary
  ensures the API is never called for a run that failed to persist.

**What the prompt includes per run:**
- Energy (pre and post)
- Effort cost
- Run context (surface, company, shoes)
- Music state and note
- Daily-mean weather (when available)
- PRs
- Per-signal comparison metadata (basis, signal lines each with evidence count and confidence)

**RunStyle is never sent to the AI.** It is computed locally in `RunStyleService` and stays on
the machine.

**Fallback response logic:**

| Performance | Post-run feeling | Response |
|---|---|---|
| PR | Spent | "You really pushed yourself — and it showed. [PR]. Feeling spent after that makes sense." |
| PR | Feeling Good | "Strong run. [PR] and you're still feeling good." |
| PR | Powered Up | "[PR] and you finished strong. That's a great day." |
| PR | Skipped | "[PR]. Strong effort." |
| No PR | Powered Up | "Strong all-around run. You finished feeling great." |
| No PR | Feeling Good | "Solid run. Good effort today." |
| No PR | Spent | "You gave everything today. Good job getting it done." |
| No PR | Skipped | "Good job getting a run in today. Every run counts." |

**LOW → HIGH always adds:**
"See what getting active can do. You started rough and finished feeling great."

**Effort fallback additions (after the main line):**
- LOW_COST: "That landed controlled."
- HIGH_COST: "That was heavier than the numbers alone show."
- MAX_COST (unless post-energy is already Spent): "That took a lot out of you, and getting it done matters."
- MODERATE_COST: no extra line.

**First positive/explanatory comparison note** from `ComparisonInsight` (candidate-based,
pre-filter already applied) is appended when available.

**RunStyle (pattern feature — REBUILT as RunStyle V1, July 10, 2026):**
The original Phase 3 "Your Run Style" (faster-AND-farther vs a 20-run rolling average,
consistency gate, LOW→HIGH bonus) was REPLACED. That elimination funnel rewarded a
physiologically unusual combination and was blind to good easy/long and hard/short training.
RunStyle V1 (see [design_runstyle_v1.md](design_runstyle_v1.md)) is a living, local,
deterministic strategy profile:
- Three primary families — State Lift, Efficiency Gain, Controlled Finish — each judged
  point-in-time; a run "supports" a family only when that family is measurable.
- Stages from the latest opportunities per family (EARLY 3/4, FORMING 4/5, ESTABLISHED 6/7);
  strongest staged family becomes primary (PR and Demand-Explained can color, never lead).
- Secondary context (surface/company/weather/music/shoes) only decorates as association
  facets or a frequency-based habit line — it can never create or strengthen a pattern.
- Announces only on a first advance or a newly qualified facet; never repeats or downgrades.
- Lives in `RunStyleService`; `Runner.detectRunStyle(Run)` delegates. Never sent to the AI.

**Backdated RunStyle policy (Task 3, July 25, 2026):**
A manually entered backdated run is saved and included in future RunStyle calculations, but it
never produces a RunStyle announcement at entry. `RunStyleService.analyze()` evaluates each run
only through its deterministic chronological position (the prefix of history up to and including
that run), so later runs cannot alter its stage, counts, facets, or habit. Only the run that is
the final entry in the sorted history is eligible to announce.
