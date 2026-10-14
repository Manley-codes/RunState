---
name: project-current-state
description: "Current development state of RunState — what's built, what's next, and key decisions locked in"
metadata:
  type: project
---

## Current Android checkpoint — October 13, 2026

**Continuing delivery pass — integrated checks passed; deployment pending:** Active Run
now centers a large fitted elapsed timer, with a clear distance/pace panel and naturally measured
Pause/Resume/hold-to-end controls below a scrolling body. This uses native drawing and existing
run states; no new image, live street map or coaching feature is implied.
GPS fixes are copied and timestamped as their callbacks arrive. Queued fixes tied to an old
listener/generation are discarded across pause or provider changes, protecting the run boundary;
this does not prove that the earlier long indoor/screen-off gaps are fixed. Speech now owns focus
and TTS callbacks per utterance, with separate initialization/playback deadlines. Receipt teardown
always releases ownership; reflection engine errors and History navigation do not replay a failed
reply or crash the delivery flow. Physical hearing and music duck/resume still need confirmation.
The [platform/tool map](../README.md#platforms-and-tools-used) explains which IDEs, development
assistants, local databases and hosted services contributed to this project.

The phone can now save an explicit HTTPS reflection address and a separate per-demo access code.
Both are encrypted with a device-bound AndroidKeyStore AES-GCM key in `reflection_connection`
preferences excluded from backup; the code is not the Anthropic API key and is never embedded in
the app. Invalid/corrupt settings fail closed, settings writes belong to the app through rotation,
and clearing the connection clears the music-sharing draft before waiting for storage. Hosted
connections are available in debug and release when configured; the computer/emulator bridge
remains debug-only. Release now has Internet permission but rejects cleartext and has no local
network permission or debug network-security configuration in its merged manifest; debug retains
its limited local allowlist. Setup discloses that the chosen endpoint receives new reflection
requests and explicit History retries with saved facts and any separately permitted music context.
Saving settings clears the sharing draft before the app-owned write waits, including through
rotation. One reflection owner also preserves local Energy/Effort while unconfigured;
no provider request occurs without a selected connection. Completion honestly asks for a connection,
and History offers explicit reflection retry without saving a second run or replacing a READY reply.

The backend's deliberate `--remote` mode checks the exact public Host and Bearer access code
before reading generation bodies. Limits are two provider calls at once, 256 retained request IDs,
32 queued tasks and 64 KiB request/success-response bodies. Duplicate protection remains in-memory
and single-instance. Java serves internal HTTP only: deployment requires private routing behind an
HTTPS ingress, not an exposed plain HTTP listener. Manley created the Render `runstate-reflections`
Starter service in Oregon at the approved $7/month plus AI usage in a free Hobby workspace. Its
configured address is `https://runstate-reflections.onrender.com`, matching the configured public
Host. It uses public `master`, Dockerfile `backend/Dockerfile`, context `backend`, blank root,
manual deploy and temporary TCP health checks; no GitHub installation grant was needed. Launch is explicitly FAKE,
and the hosted Anthropic key remains unset. No hosted AI or standalone phone request is verified.
The first Docker build ended with 43 tests, zero failures and one Linux socket-reset error in the
oversized-request security test. The raw test helper kept reading to EOF; a reset after a complete
HTTP response can invalidate that unnecessary read. Only that test reader was repaired
to read a bounded Content-Length response. The 403/413 JSON and zero-provider-call assertions
remain, with no skipped tests or production-security relaxation. All 22 endpoint tests and the
full 45-test backend suite passed on Windows. This repair awaits Manley's commit/push and a fresh
Render Linux build; the backend is not live and the Linux fix is not yet verified.
Private access configuration is kept out of the repository/docs. Hosted fake staging must pass
before a separate live-key step. The container uses an explicitly empty ENTRYPOINT and a CMD so the launch can
be overridden for fake staging. Finite JDK header/read/write/idle deadlines are configured;
the container image, public ingress and body-upload behavior have not been exercised.
The direct Java 17 process was checked locally in private `--remote --fake` mode using the exact
container startup flags: health returned 200, missing auth 401 and authenticated invalid JSON 400.
An incomplete-header client was closed after 15.1 seconds. This proves that local Java boundary
and header deadline, not a built Docker image, Render TLS or standalone phone delivery. Its test
access code existed only in process memory and was cleared; the server was stopped afterward.

Current local verification is 45 backend tests on Windows (including 22 endpoint tests),
284 JVM tests in each Android variant, all APK builds
and one isolated encrypted-settings test on the Moto. The full emulator screen/database suite
passed 88 tests; after the setup disclosure change, all three APKs rebuilt and the focused
connection-dialog test passed. The release merged manifest was checked for Internet permission,
no local-network permission, cleartext denied and no debug configuration.
A real MainActivity emulator walkthrough measured 0.04 mi, paused, moved roughly 111 m while
paused, and resumed with distance still 0.04 mi. New Running movement brought it to 0.06 mi;
hold-to-stop saved partial metrics at 8:14 elapsed/7:09 displayed active. Automatic no-selection
reached READY/FAKE; History reopened its exact text and a partial path with four sections from
the pause and manual-delay gaps. History leaves active time unknown under the conservative GPS
rule. This is simulated pause/reopen proof, not screen-off continuity or outdoor accuracy proof.
The existing `android/artifacts/run-gps-active-emulator.png` was updated and visually checked
without overlap at the default viewport. Both JVM variants were rerun after the final sharing-draft
edit: 284 tests each, zero failures/errors. The latest debug APK was installed on the Moto while
preserving app data. Test packages were removed from phone/emulator, temporary device review files
were removed and the headless emulator closed. The local fake servers stopped; ports 8787 and
8797 have no listeners. No secret or temporary diagnostic was retained in project source.
This continuing pass
adds only three necessary files: `ReflectionConnectionSettings.kt` for encrypted connection
storage, `backend/Dockerfile` for the hosted Java container and `backend/.dockerignore` for a strict
container-build allowlist. One price/form review image was created outside the repository in the
authorized visualizations folder; it adds no project source. Manley has committed/pushed the
earlier backend (`45d40cf`), Android (`0758f46`) and design checkpoint (`af98def`). The tested
response-reader repair and current documentation updates await his next commit/push; assistants
performed no Git mutations.

### Earlier live and optional-music checkpoints

The first functional live-reflection gate is now met: the Moto received newly generated Anthropic
text, selected and stored it, preserved it through late Energy feedback, and reopened the exact
text in History after a cold restart with the backend stopped. A small five-generation live check
exposed unit/time/provenance errors; deterministic provider facts and prompt v1.1 corrected the
controlled cases. That first gate passed 30 backend tests; the optional music extension below now
passes 35. These are functional checks, not broad reflection-quality proof. Full captured Spotify
history remains local; a separate approved per-run choice now permits one eligible title/artist.
The phone also verifies saves, audible sample receipts/reflections, feedback/History, completion
layout, GPS-choice retention and local outdoor GPS/Spotify observations. Start another run retains
explicit GPS while clearing Energy/music; fresh launch defaults to Sample and explicit GPS-off
remains. Debug, release and instrumented-test APK builds passed. Display-off GPS continuity,
distance accuracy, music duck/resume and a permitted music snippet remain unverified. Temporary
diagnostics/review files are cleaned up, no credentials were written into the project, and fake/live
backends are stopped. The main app remains on the latest expanded History record. No further walk
is requested this turn.

**Optional music sharing — implemented and phone-checked:** Manley separately approved an
OFF-by-default per-run choice to share one observed Spotify title/artist with the backend and
Anthropic. Both local observation and sharing must be selected. Room v10 stores the distinct
sharing flag; its 9→10 migration defaults every older run to false without changing existing data.
The new run chooses the newest eligible same-run PLAYING Spotify snapshot within run bounds,
requiring both title and artist. Its frozen context has `MEDIA_SESSION_PARTIAL` provenance and no
artist description. Read failure or missing consent/evidence keeps music UNKNOWN. Retry reuses
that same evidence; History never reconstructs or sends it again. Turning observation off, losing
Android access or starting another run clears the sharing draft. The sharing control is hidden
when the reflection bridge is unavailable.
An actual Moto GPS run had 4:35 elapsed, unavailable distance and 135 local media observations.
Its completion display showed 4:31 active reconstructed from lifecycle transitions, not proof of
uninterrupted active tracking. A read-only on-phone audit confirmed frozen MUSIC/MEDIA_SESSION_PARTIAL, title and
artist present, artist description absent, exactly five music fields, and a READY non-test reply.
The generated reply omitted the song; context delivery does not guarantee a song reference or
prove music affected the runner. No audio, lyrics, coordinates or listening history were shared;
the previously approved saved-run metric evidence is unchanged. Prompt v1.2 preserves the partial
metadata boundary. Final verification passed 35 backend tests, 280 debug and 280 release JVM tests,
debug/release/test APK builds and 20 permanent new phone migration/database/UI checks. One
temporary redacted diagnostic check also passed and was removed. New History context labels use
the stored frozen evidence and avoid claiming successful transmission or song use in the wording;
their unit checks passed. Final-APK cold-reopen on the Moto with the backend stopped retained the
exact live reply, partial-context label, metrics, Moderate pre-run Energy and 135-observation count.
The full reply/label fits the phone's scrollable History card. Actual audible delivery,
music duck/resume and snippets were not verified by this music-sharing check.

The final timing safeguard now sends unknown active duration for GPS evidence with PARTIAL or
UNAVAILABLE coverage even when pause/resume history is complete, matching History/recovery's
conservative boundary. Fixture and COMPLETE GPS still retain known active duration when their
timeline permits it. The earlier live music request's frozen evidence and exact saved reply
predate this correction and remain unchanged; no regeneration or additional paid call was made.
The two timing regressions passed in the final 280-per-variant suites with zero failures/errors;
all APK builds passed and the corrected debug APK was installed on the Moto. The only new file
for this sharing pass is Room's required version-10 schema export; no new source file was added.

The first native Start visual pass now uses the local `start.html` prototype's pale map image,
floating agent card, three Energy tiles and dark curved Start footer. The map is explicitly
illustrative; the place-specific RunStyle card and shoe mileage are not presented as real data.
The optional GPS and music-observation controls now describe their actual behavior.
Low, Moderate or High is optional,
has Undo, and is saved under the permanent run UUID at official start. Bypass and older runs
remain unknown. A failed start retry retains its original choice. "Start another run" returns
to Ready so the next run can make a separate choice. The saved choice feeds the backend
reflection evidence and appears as "Before run" in History; post-run Energy remains a separate
"After run" answer. Room version 5 adds this nullable field through an explicit migration.
The visual review image is `android/artifacts/run-start-energy-emulator.png`.

Start now offers an explicit, reversible GPS choice through **Turn GPS on/off**. Sample remains the fresh-launch default;
precise-location permission and enabled phone GPS are required before a GPS countdown can
become an official run. One tracking mode is frozen with the run UUID. The map artwork remains
an illustration even when GPS is recording; it does not draw a real route. A same-process
foreground location service owns a GPS start and its notification, follows durable pause/resume/
completion state, and records raw GPS fixes and cautious distance decisions in Room version 6.
Only accepted measured segments contribute distance. Partial GPS evidence is labeled as such;
no measured segment means unknown distance, and partial GPS never displays a confident pace.
After process death, reopening an unfinished GPS run offers an explicit tracking restart and
does not bridge the missing route interval. This is an emulator implementation checkpoint,
not an outdoor accuracy claim.
An emulator walkthrough exercised permission opt-in, simulated movement, Home/background,
Pause/Resume, a second measured section, hold-to-stop, foreground-service shutdown and
History after process restart. The example saved 0.03 mi as partial GPS; pace stayed unknown.
Screenshots are `android/artifacts/run-gps-start-emulator.png`,
`android/artifacts/run-gps-active-emulator.png`, and
`android/artifacts/run-gps-saved-emulator.png`. The Room store also guards against connecting
a distance segment across a rapid durable Pause/Resume even if the service misses that
intermediate state. History conservatively leaves active time unknown for partial GPS because
the stored record cannot prove continuous service coverage after a process interruption.

Start also offers **Observe music**, off by default and reversible before each run. It opens
Android's notification-access Settings when needed; that Android grant is broad, so the UI says
so. The service does not read notification contents. It attaches to active media sessions only
while Room shows exactly one unfinished run and that run is RUNNING with its own music opt-in.
Room version 7 saves timestamped source-app, optional title/artist, playback-state and callback
snapshots under the run UUID. Pausing detaches observation; Room refuses late callbacks and
non-opted-in writes. Missing events mean unknown coverage, not no music; these are observations,
not proved complete songs or accurate song start/end times. No audio, lyrics, playback control
or provider credentials are stored. This local-observation pass did not share music; the separate
Room v10 opt-in above now permits one eligible title/artist, with all other observations kept local.
Expanded Log History shows the last reported media title/artist/app and event count with an
explicit partial-evidence label, or says music is unknown when an opted-in run has no events.
Debug and release builds passed with 241 JVM tests; 15 focused Room/migration emulator tests,
the Start music-control test and the History evidence test passed. Android bound the granted
listener on the emulator without a startup error. Actual external-player behavior awaits a phone.

Running and Paused now have a first native visual pass in the same light-map/dark-control
family. The map becomes a circular, explicitly illustrative surface around truthful session
time, fixture distance and pace; the separate active time remains visible. Pause, Resume and
the 1.5-second hold-to-end keep their original coordinator callbacks and durable behavior.
This is a reviewable foundation, not a settled Active design; Manley had flagged that screen
for substantial redesign. The emulator image is `android/artifacts/run-active-emulator.png`.

The debug Android journey now saves a completed fixture run, offers optional post-run Energy,
selects one of four prepared replies (or the no-selection reply after about six seconds), and
stores the exact selected text in Room. Log History shows completed runs, frozen metrics,
later Energy feedback and that same saved text after app restart. Opening a ready History row
speaks its saved text through Android TextToSpeech when a voice engine is available. A late
Energy answer updates feedback without replacing the reply. Failed generation leaves the run
saved and offers a reflection-only retry. Room version 4 introduced reflection storage;
version 5 preserves it while adding pre-run Energy.

On Run Complete, a newly finished run now also speaks the exact selected reflection once when
it becomes ready. Reopening an older saved run stays quiet; leaving the screen or backgrounding
stops speech. Optional post-run Effort is a separate, quieter `EFFORT +` sheet with Smooth,
Working, Heavy and Empty Tank. It can be changed or cleared on Run Complete or in expanded Log
History. Room version 8 stores the answer under the completed run UUID, with older runs left
unknown. Effort neither selects nor changes the saved reflection. Opening the sheet holds the
quiet no-Energy selection until the runner dismisses or answers it.
The October 13 pass passed 245 debug and 245 release JVM tests, release and instrumented-test
APK builds, and 75 focused emulator tests across Run Complete/History, Room persistence and
migrations. These checks exercise speech dispatch with an injected speaker; actual phone voice
quality remains unverified. The final navigation-hold adjustment compiled and passed both JVM suites.

The next October 13 delivery pass adds a deterministic spoken save receipt from the completed
Room row. It names fixture or partial GPS distance honestly and uses saved elapsed time. A new
reflection can prepare its candidates and accept Energy while the receipt plays, but its reveal
waits for the receipt to finish, fail or be interrupted. The six-second no-Energy window then
starts once. Room v9 preserves whether that window has started; older selected reflections remain
unchanged. Reopening/rotation consumes interrupted receipts without repeating them, and explicit
retry can repair a failed window write using cached candidates without another provider call.
Android speech requests temporary audio focus, releases it on completion/error/stop, and has a
bounded stalled-engine timeout. History speech now stops on backgrounding. The factual receipt
uses normal system cadence; the reflection uses a slightly slower cadence. A distinctive custom
voice and actual phone audio quality remain later.

Expanded History also supports adding/changing post-run Energy on records that already own a
reflection. It preserves the exact selected text and does not replay speech. This local feedback
save also works in release builds. GPS History rows load a local recorded-path preview only when
opened; it draws accepted anchors/segments and preserves gaps rather than connecting across them.
The preview is explicitly partial, has no street map, and never upgrades the GPS coverage claim.
Active now distinguishes waiting for usable GPS movement from measured GPS recording.
Verification passed 264 debug and 264 release JVM tests, release/debug builds, 35 focused
Room/migration/screen emulator cases, then nine screen checks after the final audio changes.
An actual emulator walkthrough completed a fixture run, observed receipt-to-no-selection
delivery through Android TTS, saved Effort, changed late Energy in History, and reopened those
same values and exact text after process restart. A clean simulated GPS run recorded 0.04 mi
across two measured sections separated by a pause. Its History preview visibly preserves the
gap and keeps pace/active history time unknown for partial GPS. The updated review image is
`android/artifacts/run-history-reflection-emulator.png`. This is simulated GPS and fake-backend
verification; it does not establish outdoor accuracy, real Anthropic quality or phone audio quality.

The local backend labels each successful response `FAKE` or `ANTHROPIC`; Android uses that
field for its test-data disclosure. The debug emulator connects through `10.0.2.2`; the debug
phone route uses USB `adb reverse` to `127.0.0.1`. An October 13 physical-phone check installed
the debug app on Manley's Moto g play (2026), Android 16/API 36, and received fake-backend
reflections through that route. Manley heard the factual save receipt and an Energy-selected
reflection. A separate no-input run reached the no-selection reply automatically. Three clearly
fixture-backed test runs survived an app update and cold restart; the latest run retained Working
Effort, Powered Up late Energy, and its original exact no-selection text in expanded History.
The actual device exposed decoration overlap and small metric-label clipping. Completion now
positions decoration using measured fact bounds and keeps compact labels on one line. The final
`RunStateScreenTest#savedPhoneWidthKeepsDecorationClearAndMetricLabelsOnOneLine` regression passed
on the Moto with both default text and 1.3 font scale with a longer distance; debug and release
APK builds passed. This focused correction changed only the existing
`android/app/src/main/java/com/runstate/mobile/ui/RunCompleteScreen.kt` and
`android/app/src/androidTest/java/com/runstate/mobile/ui/RunStateScreenTest.kt`; it added no source
files and was not committed. Precise location while in use and active-run notifications were
granted through Android's normal prompts. Spotify is installed, signed in and playable. Manley
explicitly approved Android's broad notification-access grant after its scope was explained;
RunState's access and per-run Observe music choice are enabled. This approves local observation
only, not sending captured music to a provider. The first outdoor GPS/Spotify check is recorded
below. Release
still makes no local backend request. The backend suite passed 27
tests; Android debug and release builds passed, with 241 debug JVM tests at this checkpoint. The original fixture checkpoint
passed 29 focused database/migration and 35 focused Compose emulator tests. The GPS pass
passed 11 focused Room/coordinator tests and its Start-screen opt-in test. A broader connected
run passed 87 of 88 tests; its one screen-test teardown timeout passed when rerun alone. A manual fake-backend
walkthrough verified both the on-time Spent reply and the no-selection reply, late Energy
immutability, History display, and reopening after process restart. Screenshot:
`android/artifacts/run-history-reflection-emulator.png`.

The first outdoor Moto run saved 0.18 mi (295.58 m) as partial GPS, with 5:37 elapsed and 4:47
active time. An on-device redacted aggregate audit found 282 raw fixes: 75 accepted (two anchors
and 73 measured segments), with 207 remaining fixes ignored within their reported uncertainty.
The accepted segment sum and final cumulative value agree. Maximum accepted reported accuracy
was 3.7 m; that platform estimate does not establish measured-distance accuracy. A 43.620-second
pause and final 6.674-second pause account for the time difference. No accepted point fell outside
Running intervals, no measured segment bridged a pause, and resume began a fresh anchor. The
maximum fix gap without a lifecycle transition was 1.5 seconds.
The same run retained 220 local Spotify observations and four distinct reported titles, with no
observation outside its opted-in Running intervals or during a pause. These are partial callbacks,
not proof of four complete songs. Captured music was never sent to the reflection backend.
Saving while USB was disconnected preserved the run and honestly failed reflection delivery;
reconnecting and retrying produced the stored FAKE no-selection response. The completed count
remained four (three fixture checks and this outdoor run), without a retry duplicate.
Manley heard the factual GPS receipt but could not confirm music ducking/resume or whether the
original outdoor screen was locked. A separate controlled 108-second display-off check observed
the foreground service alive and reopening retained the same run, but no GPS fixes arrived within
that interval. Later fixes produced no measured distance and saved unavailable telemetry. This
does not verify GPS capture with the display off.

The returned three-minute follow-up produced two separate completed records: a 23.817-second GPS
run measuring 25.55 m (10 fixes, four accepted, one anchor/three segments, paused at 15.164 seconds),
then a 186.989-second fixture run with 182.194 seconds active and no GPS points. The short GPS
record's maximum fix gap without a pause was 1.923 seconds. It cannot establish three-minute
locked-screen tracking. The latest sample reflection became READY/FAKE/no-selection after USB
retry without adding another run; that audit counted seven completed records. Manley later
confirmed ending an attempt early and starting a new run. The previous Start another run callback
cleared the GPS draft, explaining why the new cycle used fixture mode. Inspection found no path
that downgrades an already stored GPS run.
Start now names the selected mode `GPS run` or `Sample run` and its action `Turn GPS on/off`.
Only the existing `StartRunScreen.kt` changed for this clarification; debug app/test APK builds and
the existing `startMakesGpsAnExplicitReversibleChoice` phone regression passed.

A later explicitly started GPS attempt saved 157.88 m of partial distance over 702.495 seconds.
Its on-device audit found 401 fixes, 18 accepted (five anchors/13 segments), 355 ignored within
accuracy, 25 inaccurate rejections and three implausible-jump rejections. Segment sum and final
cumulative distance agree, with no pause bridge or accepted distance outside Running. Its maximum
fix gap was 149 seconds, so this does not establish display-off continuity. Two separate fixture
attempts of 17.955 and 127.221 seconds had no GPS points. The last aggregate audit before the final
manual proof counted ten completed records.
The focused fix removes only the GPS-choice reset from `MainActivity.kt`'s Start another run
callback. Per-run Energy and music still reset, fresh launch defaults to Sample, and permission
guards/explicit GPS-off remain. The updated APK was installed without clearing data. On the Moto,
fresh Ready showed Sample; selecting GPS and Moderate Energy led through Start, GPS waiting with
the foreground service, Pause, hold Stop and Saved GPS-unavailable metrics (41 seconds elapsed,
13 seconds active), followed by automatic READY/FAKE/no-selection. Start another run then retained
GPS Ready/Turn GPS off, with Energy cleared and Observe music off; the completed GPS service had
stopped. This successful save is an additional indoor proof after the ten-record audit; the
database count was not audited again. Debug, release and instrumented-test APK builds passed.
The temporary redacted audit method is entirely removed; no new source files were added and no
further walk is requested this turn.

The first paid controlled request returned all four candidates but misread 5000.25 meters as five
miles and the local time as morning. `AnthropicReflectionProvider` now adds provider-only
`reflection_facts`: deterministic distance text with its sample/partial-GPS qualifier, exact
duration seconds, and ISO local dates/offsets from the recorded timezone. Raw metrics and the
Android/HTTP contract are unchanged. Prompt v1.1 requires those facts or omission. Backend mocks
cover both distance units, unknown/zero/tiny values, rounding, exact seconds and local daylight-saving
transitions. After the first correction, the controlled fixture case correctly described 3.11
sample miles and afternoon. A partial GPS case initially omitted the qualifier; embedding
provenance in the canonical distance phrase corrected all four branches to 0.18 miles recorded by
partial GPS and the supplied 11:25–11:30 p.m. local interval, with unknown pre-run Energy/music and
no confident pace claim. The complete check used five live generations: four controlled calls,
including the initial failure, and one phone request. Credentials were supplied privately to the
backend runtime without key content, copies or persistent environment configuration in the project.

The actual Moto live run had Low pre-run Energy, 1:54 elapsed/1:15 active, and unavailable GPS
distance because it had no usable points. Selecting Feeling Good produced ANTHROPIC text without
a fake-data label: "You started low on energy and still got out there. Finishing feeling good
after that says something about your resilience." Late Powered Up feedback preserved that selected
text. Expanded History showed Before Low/After Powered Up and the same reply. Force-stopping and
cold-reopening with the backend stopped retained the exact text and feedback. The existing native
TTS path is wired to live replies; Manley did not confirm hearing this new live check.
The first newly generated Android text plus durable-reopen milestone is met. This does not
establish broad quality, music-aware generation or full physical-run reliability.

**Still unverified or unbuilt:** GPS distance accuracy, locked-screen point continuity,
music-access revocation, music ducking/resume and a permitted reflection music snippet remain.
Fixture mode is available; a real route map and further Active refinement are future work.
Without both music flags Android still sends UNKNOWN; explicit sharing permits only one partial
Spotify title/artist. Its use in actual reply wording and broader music-aware quality remain open.
Temporary diagnostics and review files are cleaned up and both backends stopped;
no keys were added to the project. No work was staged, committed or pushed, and no new source files
were added for the phone/live corrections.

## Earlier reflection priority — October 7, 2026

Manley clarified that the music/reflection creative baseline is ready for a first functional
implementation, with further refinement later. Keep Anthropic and update the older writing prompt
and integration as needed. The next milestone is newly generated reflection text in Android from
controlled run/music inputs, then the exact selected reflection in durable Log History. A small
backend keeps credentials off the phone; storage is a component, not the entire deliverable.
This intentionally moves live generation ahead of GPS and automatic music observation.

The bounded implementation brief is at the top of `design_run_response_system.md`; examples,
writing guidance and honest test limitations remain in `research_music_reflection_generation.md`.
Do not restart the writing/research loop or the old 36-output gate before coding. Keep code changes
as bounded CLI handoffs that Manley can watch. The contract and provider-adapter foundations below,
the native Run Complete scaffold, loopback HTTP endpoint and an emulator-only fake-provider bridge
were implemented at that earlier checkpoint. Energy selection, durable reflection storage,
History, live Anthropic generation and physical-phone connectivity were still pending then; the
October 13 checkpoint above records their current verification. Old calendar targets below are
not renewed commitments.

## Local reflection API — reviewed October 7, 2026

- The standalone backend now exposes `POST /api/reflections/candidates` on `127.0.0.1` using the
  existing contract and provider adapter. It validates strict UTF-8 JSON, preserves the four exact
  candidate texts, limits the request body to 64 KiB, returns safe error codes and coalesces
  duplicate request IDs within one server process. The backend README documents its protocol and
  process-local duplicate cache.
- Eight fake-provider HTTP tests join the earlier eight contract and ten adapter tests. Maven's
  saved reports show 26 tests with zero failures, errors or skips. The fake-server smoke check used no
  Anthropic traffic. Android debug builds now call the fake endpoint through the emulator host alias;
  credentials and real generation were unverified at that October 7 checkpoint; the later live
  milestone above supersedes that limit.
- The October 7 hardening pass added an early local request guard. It accepts only `127.0.0.1`,
  `localhost` or the emulator's `10.0.2.2` Host with the actual listening port, and rejects every
  supplied Origin before reading the body or invoking the provider. The extra tests cover accepted
  and rejected headers. This is a browser-origin guard, not authentication against local programs.
  The process-local duplicate cache has no eviction or restart durability, so a later hosted service
  needs a separate design. No paid live call has yet verified the configured Anthropic provider.

## Android fake-reflection bridge — verified October 7, 2026

- After Room durably completes a run, the debug app re-reads that row by UUID and sends raw saved
  timestamps, timezone, metrics and provenance to the loopback-bound fake backend through
  `10.0.2.2:8787`. Pre-run Energy and music are explicitly `UNKNOWN`; formatted screen text is not
  used as evidence. The client validates matching identifiers and all four exact nonblank candidate
  strings before exposing the fake `no_selection` text.
- A process-owned coordinator starts the request outside Compose, holds the reply behind the
  six-second Saved quiet window and prevents rotation from issuing a duplicate call. Attempt state
  is stored per run, so a late success or failure for run A cannot replace run B's visible state.
  Failure offers a reflection-only retry and does not save another run.
- The panel is labeled `FAKE BACKEND · TEST DATA` and exists only in debug behavior. Release creates
  no reflection coordinator or request attempt and keeps the existing saved metrics and working
  `Start another run` action without fake text or Retry. Debug and release each passed 211 JVM tests;
  32 focused Compose emulator tests passed, and the Start → Pause → hold Stop walkthrough reached
  the exact fake reply with frozen fixture values.
- This is a local emulator integration proof, not the reflection feature milestone. Energy
  selection, durable reflection storage, Log History, live Anthropic generation and physical-phone
  connectivity remain unbuilt.

## Native Run Complete visual translation — verified and top layout accepted October 6, 2026

- Saved now opens a first-pass native Jetpack Compose Run Complete screen using the already saved fixture
  distance, elapsed time, active time and pace. Unknown metrics remain visibly unavailable;
  "Fixture metrics" identifies the controlled data. "Start another run" retains the working
  lifecycle callback. This proves the data-to-screen route, not the generated reflection feature.
- Manley reviewed the first emulator screenshot on October 6 and said it did not resemble his
  `C:\Users\Owner\OneDrive\RunState UI\Production\complete.html` design. A second bounded CLI
  pass replaced its centered metric cards and badge with the reference's offset condensed distance,
  compact metrics, inline saved status, grid/layered background and static completion ring. The
  updated emulator screenshot is `android/artifacts/run-complete-emulator.png`, beside the rendered
  reference `android/artifacts/reference-run-complete.png`. Manley accepted this top layout as the
  foundation for Energy and reflection. A debug-only fake reflection panel now occupies the lower
  area during emulator verification; the release layout remains sparse because Energy, durable
  reflection and History are not built.
- The second CLI pass reported 202 JVM tests and 61 emulator tests passing after one countdown
  timing test passed on isolated rerun. The last connected-test XML contains only that rerun, so
  the full-suite count is a CLI report rather than independently preserved XML evidence.
- Follow-up before shipping: darken the smallest essential metric labels and the fixture disclosure
  for readability. The saved-data route, honest unavailable states and restart action remain intact.
- The earlier CLI pass reported 202 JVM tests and 60 emulator tests passing. Its Medium Phone
  walkthrough completed Start, Pause and hold Stop, then displayed the frozen fixture values.
- The second pass made factual content scroll while keeping restart reachable, and uses a linear
  metric layout at large system text or on narrow screens. Continue testing on the physical phone.
- Manley expects access to a physical Android phone about a week after October 6 and plans to demo
  on it. Continue emulator implementation now, then install and exercise the full journey on the
  phone promptly when it arrives. The backend's demo connection method is still to be decided.

## Backend contract foundation — verified October 5, 2026

- `backend/` is now a standalone Java 17/Maven project with JUnit 5. Its immutable records define
  canonical request/run identifiers, completed-run evidence aligned with Android's timestamp,
  timezone, raw-distance, display-unit and metric-provenance meanings, optional pre-run Energy and
  explicitly controlled music evidence. Missing optional values remain unknown.
- The response contract requires exact nonblank text for `spent`, `feeling_good`, `powered_up` and
  `no_selection`; accepted text is preserved without trimming. Validation covers identifiers,
  timestamp order, IANA timezone resolution, active duration, metric consistency and music-state
  contradictions.
- IntelliJ IDEA's bundled Maven ran the backend suite successfully: 8 tests, 0 failures, 0 errors.
  This proves the local contract rules only. It cannot prove Android saved a run.
- That contract slice did not include an HTTP endpoint, Anthropic adapter, writing prompt, candidate
  selection, recovery or reflection persistence. The adapter and prompt arrived in the next bounded
  slice below; the reflection milestone remains incomplete.

## Anthropic provider adapter foundation - verified October 5, 2026

- `ReflectionProvider` now separates the app-facing generation operation from
  `AnthropicReflectionProvider`'s transport work. The adapter reads `ANTHROPIC_API_KEY` only from the
  backend environment, supports optional `ANTHROPIC_MODEL` configuration and redacts the key from
  request/configuration text and reported failures.
- Official Anthropic documentation was rechecked before implementation. The adapter uses the current
  Messages API `output_config.format` JSON-schema shape, which supports Haiku 4.5, and starts with the
  active pinned `claude-haiku-4-5-20251001` model. Initial limits are a 10-second connection timeout,
  45-second request timeout and 2,048 output tokens; these are starting bounds, not measured guarantees.
- The versioned `mobile-reflection-v1.txt` prompt carries the October 3 writing baseline and complete
  fictional calibration facts, keeps examples separate from current evidence, treats music text as
  data, and forbids invented history, comparisons, improvement, music causation, questions and
  below-average commentary. The old console word cap and Effort interpretation were not copied.
- The adapter sends only completed-run evidence, with raw units, provenance and unknown values intact;
  request/run identifiers stay outside the provider prompt. It requests all four exact branch keys in
  one call and accepts only an `end_turn` response containing one complete, nonblank string object.
- Gson 2.11.0 strict parsing now rejects raw control characters, unsupported escapes and invalid
  Unicode escapes in both the provider envelope and candidate output. Malformed syntax consistently
  becomes typed `INVALID_RESPONSE`; duplicate-key checks, branch completeness and exact accepted text
  remain intact.
- Non-success status, refusal, truncation, malformed or duplicate JSON, missing/extra keys, wrong value
  types, blank text, timeouts and connection failures become explicit failures. There is no automatic
  retry or successful canned fallback.
- IntelliJ IDEA's bundled Maven ran the complete backend suite successfully: 18 tests, 0 failures,
  0 errors. Ten adapter tests used dummy configuration and simulated transport responses; no live
  Anthropic traffic occurred. Real generation quality, credentials, network behavior and Android
  delivery were unverified at that adapter checkpoint, which did not complete the reflection
  milestone. The October 13 live-text and durable-reopen verification above records the later gate.

## Implemented baseline — verified against source October 3; last full test checkpoint September 15

RunState remains one product and one Git repository with three implementation areas: the intact
working Java/Maven console application, the native Android/Kotlin/Compose journey under `android/`,
and the local-endpoint Java/Maven reflection backend under `backend/`. The Android application now
completes the durable fixture lifecycle from a visible countdown through Running, Paused and Saved
and displays controlled fixture elapsed/active time, distance and average pace. The first native Run
Complete screen and an emulator-only fake-backend delivery bridge are now implemented. Energy
selection, durable reflection storage, Log History, live Anthropic generation and physical-phone
connectivity remain design work rather than implemented features.

## Previous delivery checkpoint — September 15, 2026

- **Phase 3 Android implementation is in progress, with the durable visible lifecycle complete.**
  Commit `a3f423b` added the process-owned official-start foundation, and commit `a01f2fb` connected
  the complete fixture lifecycle to Compose. Ready now enters a lifecycle-gated visible 3-2-1;
  reaching zero creates one canonical prepared row and shows Running only after Room saves it.
  Pause, Resume and Complete are application-scoped coordinator actions whose reservations survive
  the screen that requested them. A continuous 1.5-second Stop hold durably completes the run before
  Saved appears, and Start another run retires the completed machine into a fresh cycle. Cancel and
  Android Back still abandon only the non-durable countdown. Home pauses the countdown without an
  invisible start, returning or rotating restarts it at 3, and Running, Paused and Saved re-read the
  process-scoped truth after Activity recreation. This is still a functional fixture interface, not
  the final Start/Active/Run Complete visual design.

- **Android Room version 3 now preserves the same run through its durable lifecycle and final metric
  provenance.** The canonical
  UUID remains the `runs` primary key. `RunSessionStarter` saves the prepared initial Running row
  before advancing memory and returns an `ActiveRunSession` bound to the same UUID, machine and DAO.
  That owner serializes pause, resume and completion, writes each change through the transactional
  DAO first, and advances memory only after storage succeeds. Ordered `run_transitions` children
  preserve each pause/resume moment; completion records the finish and final checkpoint without
  creating a second run or duplicate completion event. Version 3 freezes distance, source, coverage,
  display unit and whether transition history is complete; older rows remain explicitly unknown where
  their history cannot support an honest calculation. Hand-written additive migrations preserve
  earlier rows without inventing missing values, and the exported version-1 and version-2 schemas
  remain unchanged beside version 3. Real-database tests prove
  rollback, persistence after reopen and the full starter-to-owner lifecycle bridge. Commit
  `e77c65b` added read-only active-row discovery: it returns every Running or Paused row, excludes
  completed history, and orders candidates by official start then UUID without choosing or changing
  one. Commit `99d3432` added the isolated recovery core: it distinguishes zero, one and many active
  rows, restores a fresh machine directly from exactly one Running or Paused row, and returns one
  owner without changing storage. A reopened-Room test proves reconstruction from the database file,
  not Android process death. Production now has one process-scoped Room instance and one
  `RunSessionCoordinator`: it serializes initialization/recovery against countdown and start,
  retains the one live owner, blocks inconsistent storage and retires a completed cycle when the
  next countdown begins. It now also owns the official-start and lifecycle-action reservations,
  keeping durable writes in the application scope and reporting the pre-action state until each
  write succeeds. MainActivity drives that coordinator through the full fixture lifecycle. The
  coordinator now also derives elapsed time, active time, controlled fixture distance and average
  pace from the durable row and cached transition history. Running and Paused refresh once per
  second only while visible; elapsed includes pauses, active time and distance freeze while paused,
  and Saved reads the frozen final distance. Every fixture value is labeled as fixture data, never
  GPS. Verification passes with 202 JVM tests and 59 local emulator tests. The Room migration-test
  runtime requires the existing serialization library to resolve at 1.8.1, and Compose tests on the
  API 37 emulator require the test-only Espresso 3.7.0; no AndroidX Startup pin or deprecated
  compile-time-R-class workaround remains. Canonical detail lives in
  `run_initiation_register.md`.

- Log History has a stable design foundation. Its most-recent-record quick peek was completed and
  accepted August 13: approximately 1.66 seconds of total visible expansion-and-collapse motion,
  with only about 0.10 seconds held fully open. Exact timing lives in `ui_phase_handoff.md`.
- The optional pre-run energy and Start-to-Active Run pass is stable enough to leave for now. Its
  current canonical record is `design_start_run_v7.md`; energy-domain details are in
  `design_state_scan.md`.
- The shoe control now has an accepted `Add Shoes` prototype with live search, saved/add/select
  behavior, selected-shoe confirmation and displayed mileage. Automatic exactly-once mileage remains
  a production requirement, some shoe images need later cutout cleanup, and removal is changing to a
  recoverable Undo that preserves past-run history. See `design_shoe_selection.md`.
- The active-run prototype includes pace, BPM, and elapsed time inside the circular instrument;
  large distance below; a live now-playing/current-run strip; and one Pause control. Pressing Pause
  reveals Stop and Play. Play resumes run tracking; a 1.5-second hold on Stop ends it and serves as
  the confirmation. For the first real demonstration, BPM is omitted unless a separately approved
  real sensor source exists; fixture BPM must never masquerade as a live measurement.
- Log History voice playback now has a successful six-persona Edge prototype for smooth, human
  delivery. It remains a testing surface, not a selected production TTS engine or bundle of reusable
  voice assets; details and open dependencies live in `ui_phase_handoff.md`.
- The mobile Run Complete direction now has a stable, fully exercised interactive prototype. It
  shows all three post-run Energy choices prominently and
  keeps Effort behind a quieter `EFFORT +` action rather than using sequential required questions.
  Either, both or neither may be recorded; ignored values remain unknown. After durable local save,
  metrics and a short factual audio confirmation appear while RunState prepares four responses from
  one factual foundation: `Spent`, `Feeling Good`, `Powered Up`, and no selection. Energy selects
  the one immediate response that is revealed and stored; the candidates may differ creatively in
  interpretation and structure, not only in their ending. Effort does not change that response. It
  is stored for future comparisons, Quiet Gains, runner learning, RunStyle or Run Rhythm analysis,
  and accurate references to past effort in later messages. Choosing Energy immediately sends its
  square into the visualizer and the selected reflection arrives from that motion. No Energy uses a
  quiet fallback window; Effort uses a lightweight sheet and may be entered late. Save/reflection
  failures, retry, reload and single-shell Log History continuity are settled in the prototype. The
  combined journey is published in the public design preview as **Finish a run**. The built console
  flow remains unchanged. See `design_run_response_system.md` and
  `design_effort_cost.md`.
- Saved-run management Pass 2 is implemented and verified as a user-facing Java console path through
  View Run History. History displays real database IDs, accepts ID selection and Back, and exposes
  post-run Energy/Effort updates plus confirmed deletion. Storage is called before memory changes;
  missing IDs and storage failures leave in-memory history untouched, explain that History may be
  stale, and end the console session. Deletion silently rebuilds historical PR flags in stable
  chronological order without AI, reflection, PR announcements, or RunStyle work. The complete
  Maven suite passes with 419 tests. A full editor for distance, route, date, weather, music, or
  other fields remains intentionally out of scope.
- The narrow Core Running Foundation Review is complete. It confirmed the completed-run console
  journey and identified four bounded gaps before mobile: comparison trust, durable active-session
  timestamps/state, local-first identity/sync, and a durable selected-reflection record. The
  comparison gap is fixed and verified. The active-session lifecycle, recovery and timestamp
  contract is approved and recorded in `run_initiation_register.md`. Its full in-memory state
  ordering through Completed now exists in the isolated Android state machine. The permanent UUID,
  official-start timestamp, start timezone, save-before-Running boundary, ordered pause/resume
  history, later checkpoints and completion finish are now implemented in Room, and one
  `ActiveRunSession` coordinates those durable changes with the in-memory machine. Read-only
  active-row discovery and the isolated zero/one/many recovery decision are implemented. One
  process-scoped coordinator now owns admission and identity, orders recovery before countdown/start
  and preserves the one returned owner. MainActivity now invokes recovery and displays the resulting
  admission state before offering Start. The
  `PENDING_CREATE` / `SYNCED` / `PENDING_UPDATE` /
  `PENDING_DELETE` local-first
  synchronization states remain approved there and not implemented. The one-to-one selected
  reflection with `PENDING` / `READY` / `FAILED` is approved in `design_run_response_system.md` and
  not implemented. All four review findings now have either a verified fix or an approved contract;
  that review is closed.
- The music feature inventory and prioritization review is complete. It separates the music
  evidence-and-feedback foundation from later playlist selection, live support and expression
  surfaces. A provider-neutral structured song-history contract is approved in
  `design_music_selection_system.md`: actual playback, RunState's decisions and optional whole-run
  Taste / Run fit feedback remain distinct under the permanent run UUID, with honest partial and
  unknown states. Current console behavior remains the manual music state plus optional free-text
  note. No provider, schema, API, screen, automatic capture, Java or mobile implementation is
  authorized by that contract.
- The broader mobile technical-contract gate is now also complete. `run_initiation_register.md`
  defines the shared timeline for accepted GPS observations, active-time distance and pace,
  automatic full and partial splits, partial/unavailable telemetry, and the completed-run
  composition under the permanent UUID. It also locks the first implementation boundary: native
  Android/Kotlin, Room as the on-phone source of truth, a foreground service for active sessions,
  and a minimal server for reflection plus later sync with credentials off-device. Saving and
  reflection remain separate; full RunStyle stays local. The Android shell, full in-memory
  session-state ordering, Room-backed durable lifecycle, UUID-bound active-session owner,
  active-row discovery, recovery core, production database, process-scoped admission coordinator and
  startup invocation now exist. The foreground service and the rest of this architecture remain
  approved contracts rather than implemented behavior.
- **Previous delivery planning step (superseded by the October 3 priority above):** add controlled fixture song/reflection data, then carry the
  durably completed run through Run Complete into Log History. GPS and real provider
  integration remain behind the complete Phase 3 fixture journey, as do remaining Log History polish,
  further music intelligence and RunStyle V2.
- No GPS tracking, BPM source, music-provider integration, production relaunch or
  process-death recovery, foreground service, synchronization, or Reflection Engine has been built.
  The current Android screen creates and operates one real Room-backed fixture run and displays
  controlled fixture metrics, but it does not yet collect real GPS execution metrics or carry a
  completed run into the designed reflection/history journey.

**Completed phases:**
- Phase 1: Console app — energy system, opening prompt, post-run responses, rolling averages
- Phase 2: MySQL persistence — runs save and load between sessions (`RunStorage.java`, `runstate` DB schema)
- Phase 3: Your Run Style — original pattern detection (`detectRunStyle()` in Runner.java). SUPERSEDED by RunStyle V1, July 10, 2026 (see below).
- Phase 4: AI agent — `RunAgent.java` replaces `buildRunResponse()` with Anthropic API call (claude-haiku-4-5-20251001). Fallback to hardcoded logic on any failure. Gson added for JSON parsing. `Run.java` has `getRunner()` getter added.
- Phase 5 (Steps 1–2): AI context expansion — music context (manual input) + weather (Open-Meteo, `WeatherData` value object, persisted at log time). Built June 26; weather cleanup + TX→Texas fix shipped July 6–7, 2026.
- Effort Cost V1: post-run "How did that run land?" input — `EffortLevel` enum (Smooth/Working/Heavy/Empty tank + internal RPE ranges), persisted at log time, shown in run history and fed to the agent (prompt line + SYSTEM_PROMPT rule + offline fallback). Built July 8–9, 2026 (collector commit 8981b48 + display/agent follow-up).
- Comparison Repair V1: candidate-based run comparison (`ComparisonService` + `ComparisonInsight`) — 180-day/cap-10 selection (same route within the distance band first, then similar-distance fallback), median aggregation, four positive signals behind a negative pre-filter, and July 25 signal-specific energy/effort evidence pools and confidence — replaces the blended 20-run rolling-average flaw in the AI prompt + fallback. `detectRunStyle()` was later replaced by RunStyle V1. Built July 9, refined July 25, and given the shared-route distance guard August 25, 2026.

**Rename completed June 22, 2026:**
- Package renamed from `com.runclubapp` to `com.runstate` — all 7 source files updated
- `pom.xml` groupId → `com.runstate`, artifactId → `RunState`
- IntelliJ module name still shows "RunNet" — cosmetic only, fix via right-click → Rename in Project panel

**RunStyle V1 — REBUILT July 10, 2026 (replaces the original "Your Run Style"):**
The old faster-AND-farther-vs-rolling-average funnel is GONE. RunStyle V1 is a living,
local, deterministic strategy profile in `RunStyleService` (`Runner.detectRunStyle(Run)`
delegates). Full spec: `design_runstyle_v1.md`. In brief:
- Three primary families — State Lift, Efficiency Gain, Controlled Finish — judged
  point-in-time (a run only counts where its family is measurable).
- Stages from the latest opportunities per family (EARLY 3/4, FORMING 4/5, ESTABLISHED
  6/7); strongest staged family is primary. PR and Demand-Explained color but never lead.
- Secondary context (surface/company/weather/music/shoes) only decorates as association
  facets (descriptive ≥3; comparative 5/5/80%/30pp; one condition + one personal) or a
  frequency-based habit line (≥5 of last 10, ≥70%). Context never creates a pattern.
- Announces only on a first advance or newly qualified facet — never repeats or downgrades.
- Never sent to the AI. Time-of-day identity: deferred by data availability, not by
  verdict — wanted eventually; revisit when start/end timestamps arrive (mobile phase).
  Full status wording in design_runstyle_v1.md.

**Completed sequence:**
1. Phase 5 — DONE (music + weather shipped June 26–July 7, 2026)
2. Effort Cost V1 — DONE (July 8–9, 2026)
3. Comparison Repair V1 — DONE (July 9, 2026); candidate-based comparison replaces the blended-average flaw (AI prompt + fallback; `detectRunStyle()` deferred)
4. Stabilization sprint — DONE (July 10, 2026); privacy/code alignment, RunAgent HTTP timeouts, DB password moved to env var, and the project's first unit tests (see handoff below)
5. **RunStyle V1 — DONE AND VERIFIED July 10, 2026 (see `design_runstyle_v1.md`).** Rebased `detectRunStyle()` off the faster-AND-farther rolling-average rule onto the identity-aligned profile: `RunContext` value object + four new context columns, `RunStyleService`/`RunStyleInsight` with three families / stages / facets / habit / point-in-time announcements, and a separate `ComparisonService.evaluateStrict` typed-evidence path. The general `analyze()` path stayed unchanged during that original build and was later refined by the July 25 signal-confidence task; the strict path remained separate. Wiring was swapped and the rolling-average snapshot deleted. Verification evidence: Manley applied the four-column MySQL migration, `mvn test` passed with 36 tests, and a live end-to-end log-run succeeded against the migrated database. Same-day ordering is deterministic in the current flow: the database loads by `run_date, run_id`, and Java's stable in-memory date sorts preserve that incoming order.
6. **Honest Database Failure Handling — DONE AND VERIFIED July 15, 2026.** RunState now treats a run as logged only after a durable DB save. Added checked `RunStorageException` (preserves the original `SQLException` as cause) and a package-private `ConnectionProvider` seam in `RunStorage`; `saveRun`/`loadRuns` throw instead of the old swallow-and-print. `App` reports a startup load failure (friendly message + `Details:` from the cause) and exits before the opening prompt/menu — never an empty or partial history. `RunConsole.logRun()` was reordered to save BEFORE `addRun`/PR/AI/RunStyle (kills the phantom-PR bug where a failed save had already announced a PR), and prints a recovery receipt on save failure (includes the "check Run History" duplicate warning), ending the session via a boolean return. Summary-before-reflection preserved. Tests: 5 new `RunStorageTest` cases via the injected failing provider (load/save throw, load never returns partial, both preserve the cause) — 41 tests green. All three manual acceptance tests passed live. Built on the project's first feature branch (`feature/honest-db-failure-handling`), merged fast-forward to master and pushed. Out of scope by design: migrations, retries, offline queue, mobile sync, doc reconciliation. Locked future direction — mobile phase uses durable local-first recording (stable run id, pending/synced states, duplicate-safe sync, server retry without losing the recorded run).

7. **Malformed Stored-Row Handling — DONE AND VERIFIED July 16, 2026 (code baseline `0af9524`).** Closes flow-audit items 1 and 2. Malformed persisted enum values now follow the same controlled startup-failure path as an unreachable database instead of escaping as an uncaught `IllegalArgumentException`. Added a private checked `StoredRunDecodeException` inside `RunStorage` (never escapes the class; wrapped in the public `RunStorageException` at the boundary, so `App`/`RunConsole` are unchanged) plus `decodeOptionalEnum`/`decodeRequiredEnum` generic helpers. All seven enum columns route through them: `distance_unit` (required), pre/post energy, surface, company, music mode, effort. Decode contract — exact enum names only, no trimming, casing, guessing, or defaulting; null stays valid for optional enums; any malformed row fails the WHOLE load (never partial); stored data is never mutated; causes preserved end to end. `inferMusicMode` now takes an already-decoded `MusicMode` rather than raw text, so a corrupt stored mode fails as corrupt instead of silently falling through to note-based inference (validation before inference). Added a package-private `readRuns(Runner, ResultSet)` seam — the second testing seam alongside `ConnectionProvider`: that one simulates an unreachable database, this one a reachable database holding bad data. Tests: 10 new (7 parameterized across every enum column, missing required unit, valid-row-then-malformed-row proving no partial history, and a legacy row with optional nulls + music inference that must still LOAD) via a `java.lang.reflect.Proxy` fake `ResultSet` — no MySQL touched, no corrupt rows written to real history. **51 tests green.** Startup guidance now reads "Check the database connection or stored run data..."; `CURRENT_RUN_FLOW.md` + `current-run-flow.svg` updated (markers 1 and 2 removed from the diagram, both rows marked Resolved in the table). Schema verified before enforcing: `distance_unit` is `NOT NULL` with zero null rows, so the required check cannot lock Manley out of existing history. Out of scope by design: no schema migration, no new dependencies, no public API change. Noted but NOT fixed: the `temperature` column has a stray default of `0` (harmless — `saveRun` always writes the column explicitly).

**Pre-Music Integrity Sprint — COMPLETED July 25, 2026. All 6 tasks done, 77 tests green.**

| Task | What shipped |
|---|---|
| 1 | Pace rollover fix — `RunAgent.formatPace` no longer formats `:60` when rounding pushes seconds to 60; rolls over to next whole minute instead. |
| 2 | Signal-specific comparison confidence — `ComparisonOutcome` value object (line, evidenceCount, confidencePhrase); separate `energyPool` and `effortPool` formed after candidate selection so each signal's count and confidence reflect only the runs where that signal is measurable. Tiers: 1 → "last comparable run"; 2–4 → "early signal"; 5–7 → "recent pattern"; 8+ → "strong personal pattern". |
| 3 | Backdated RunStyle announcement policy — `RunStyleService.analyze()` evaluates each run through its chronological prefix (indexOf + subList); only the genuinely last entry in sorted history is eligible to announce. Backdated runs save and contribute to future calculations silently; no historical-as-today announcement. |
| 4 | Failed-save orchestration regression — `saveAndCompleteRun()` extracted from `logRun()` (behavior-neutral); `saveRun()` and `buildRunResponse()` added as package-private delegates. `RunConsoleTest` proves a failing save leaves history untouched, PR flags unset, AI response unsent, and RunStyle unchecked. Flow-audit item 3 closed. |
| 5 | Context in summaries — `Run.getContextSummary()` private helper (parts-collector ArrayList, `String.join(" \| ", pieces)`) wired into `getRunSummary()` after pace/duration and before energy. Compact `Context:` line appears in post-run view, Run History, and recovery receipt. Nine `RunContextTest` cases; flow-audit item 4 closed. |
| 6 | Documentation reconciliation — active privacy, AI, comparison, response, RunStyle, project-memory, README, roadmap, and flow records reconciled with verified behavior. A post-sprint audit then removed remaining stale flow text, historical-as-current wording, and music-order contradictions. All four flow-audit findings are closed. |

Flow-audit findings: all four closed — items 1 and 2 (malformed-row handling, July 16); item 3 (save orchestration, Task 4); item 4 (context in summaries, Task 5).

**Historical Music Intelligence stopping point — August 2, 2026.** This section preserves the music
evidence and gate as they stood when that work paused. It is **not** the current project resume point;
the authoritative August 13 UI resume point and queue are at the top of this file.
- **V1 planning is complete and committed (`0f22c99`, July 27, 2026).** The canonical contract is
  `design_music_intelligence_v1.md` — purpose and boundary, closed foundational contracts,
  current evidence and reply behavior, deferred persistence boundary, bounded implementation
  contract, automated verification plan, manual model-evaluation plan, and execution order.
  Treat it as the single source of truth; other music docs point to it rather than restate it.
- **The original bounded prompt slice was implemented July 27, then revised from live smoke
  evidence and committed July 30 (`693bfb3`).** Production remains bounded to
  `RunAgent.java`; deterministic coverage lives in `RunAgentTest.java`, with the opt-in
  `MusicIntelligenceEvaluationRunner.java` as the separate manual-evaluation surface. What
  remains structurally true:
  `MUSIC_REPLY_RULES` separated from the general
  `SYSTEM_PROMPT` behind the `Music reply rules:` marker, a deterministic no-network
  `buildRequestBody(Run)` seam, the private `EARLY|ESTABLISHED` stage derivation, blank-safe
  `describeMusic`, the all-free-text-is-data instruction, a music-neutral fallback regression,
  and Gson-correct JSON serialization replacing the hand-written escaper.
- **The deterministic Maven gate is PASSING: 362 tests, 0 failures, 0 errors, 0 skipped**
  (clean July 31 run after the Opus response-handling correction). It verifies transport, placement, prompt content, evaluation-runner
  safety, and fixture integrity — **not** final model quality.
- **Active design, AI-agent, evaluation, privacy, roadmap, and status documents were reconciled
  July 30.** The creative revision sends no new data and adds no provider, so the privacy
  disclosure itself did not change; its prompt-source cross-reference was corrected.
- **Evaluation was incomplete when this lane paused.** Sanitized fixtures and the opt-in runner exist.
  One authentication-invalid launch produced fallback text only; the first valid 12-call smoke
  completed with zero fallbacks but failed product quality. The prompt was revised in response.
  **The revised-prompt smoke then ran July 30 — 12 calls, zero fallbacks — and also failed
  diagnostic quality and trust.** Prose and music participation improved, but the model still
  asserted unsupported characteristics for named tracks (S4 unfamiliar-music is the clearest
  case), openings collapsed onto one template, and closings returned to coaching filler.
  **No final run has occurred. Combined Music Intelligence V1 is NOT complete.**
- **A separate creative-ceiling probe ran July 30 at `6cb3075` — 12 completed calls,
  diagnostic only.** It swapped in a minimal creative system prompt as the single variable to
  test whether the production prompt was suppressing the model or the model simply cannot do
  this. **Minimal prompting improved the prose but did not produce reliable replies.** Both
  independent graders reached the pre-registered **0–3** band (Cowork 0 Hit / 1 Near-hit /
  11 Miss; Codex 2 Hit / 1 Near-hit / 9 Miss — preserved unreconciled) and both counted **nine
  hard-trust failures**, triggering the three-or-more trust override. **Manley found neither
  disputed reply app-worthy**, preferring S11-1 only if forced — a relative preference, not a
  Hit. Full record in `music_intelligence_creative_ceiling_probe.md`; raw transcript, request
  bodies, and smoke screenshots are preserved under `docs/design/evidence/`.
- **The separately approved stronger-model control completed July 31 — 12 usable Opus 5
  replies, diagnostic only.** Manley graded the shuffled packet before opening the scenario
  mapping; the model identity was already known. Manley's creative tally was **1 Hit / 7
  Near-hits / 4 Misses**; strict application of the control's old trust-collapsing label rule
  gives **1 / 3 / 8**. There were **6 clear hard-trust failures**. Both the pre-registered 0–3
  Hit band and the three-or-more trust-failure override apply: **Opus alone does not meet the
  bar.** The seven
  Near-hits are still useful evidence: creative ideas appeared, but the replies routinely
  over-explained the music, sounded too formal or literary, led with artist/title names too
  often, and lacked editing/compression. Full record:
  `music_intelligence_stronger_model_control.md`; exact transcript and grading packet are in
  `docs/design/evidence/`.
- **Future diagnostic grading keeps three questions separate:** creative value (Strong /
  Promising / Weak), trust (Pass / Fail—removable / Fail—load-bearing), and app readiness
  (Yes / No). A trust failure means that wording is not app-ready until corrected, but a useful
  creative move may still be harvested. Final V1 acceptance remains zero-tolerance for hard
  trust failures. Historical grades are not retroactively rewritten.
- **Energy domain — CLOSED.** The shared LOW/MODERATE/HIGH pre/post energy domain is retained.
  The four-state State Scan sketch is **superseded as a domain proposal**; later UI work may
  refine labels and presentation without changing stored meanings.
- **Display-independent revision — IMPLEMENTED August 2 at `b2227b2`.** The target is **creative wording that lands
  cleanly**: clever and clear, usually understood immediately or after a quick beat, with run
  facts and a small music shard often fused instead of followed by a song explanation. Build
  calibration candidates from Manley's successful rewrites and label the different techniques
  so one construction does not become the new template. Treat performance-first openings,
  persona tags, title fusion, direct naming, and music-free runner truth as optional colors, not
  mandatory formulas. Manley approved four exact calibration replies. Unfamiliar music remains a
  safety evaluation case rather than a teaching example. The hip-hop-only evidence does not prove
  genre transfer; test that later before claiming it. The approved prompt, calibration examples,
  controlled S1/S12 fixture replacements, and deterministic guards are committed at `b2227b2`;
  **382 tests passed and no live model call occurred.**
- **Reply-card fit — COMPLETED August 2:** short, medium, and longer candidates were checked in the
  expandable card. The result supports expanded-first with user-controlled collapse and
  idea-governed compression with no numeric sentence limit. This was a narrow measurement, not a
  full UI build or authorization for Spring Boot or mobile implementation.
- **Gate if Music Intelligence resumes:** Manley must separately approve any new 12-call smoke of
  the implemented revision. No fresh smoke or 36-call final evaluation is approved yet. This gate
  applies to renewed music evaluation; it does not block the active UI queue.
- **Separate lanes recorded at that stopping point:** the possible `Ready-ish` wording change,
  stronger persona preference, and the larger RunStyle redesign were not part of the bounded music
  revision. The old instruction to wait for music calibration before continuing UI work is
  superseded by the August 13 resume point. Existing RunStyle V1 remains the built behavior unless
  a separate V2 proposal is reviewed and explicitly approved.
- The future `REFERENCED / NOT_REFERENCED / UNKNOWN` reply-outcome boundary is **documented**
  in the canonical plan, but cross-run reference-frequency implementation (schema, rolling
  window, detection, prompt line) remains **deferred** — it is not part of the prompt slice.
- **Roadmap refinement approved August 1 — now active:** low-fidelity screen work may loop into the
  Core Running Foundation Review without waiting for every display-dependent music decision to be
  finalized in a terminal. The first screen experiment was the completed reply-card density test
  above. The **Core Running Foundation Review**
  remains tightly fenced to one question —
  is the central journey (record a run → preserve it safely → understand it → manage it → use
  it later) credible and structurally ready for a real interface? "Manage" (edit/delete a
  logged run) is the known thin spot. Output is a short gap list, not a feature hunt; "add
  every feature Strava has" is explicitly out.
- **Superseded delivery order, August 26:** do not insert a standalone Spring Boot migration or
  more rough-screen work before the mobile foundation. Build the native Android/Kotlin journey
  first with Room and fixture data, then add foreground-service GPS. Introduce only the minimal
  server boundary when reflection generation needs it; later sync may extend that seam. If Music
  Intelligence evaluation resumes, retain its separate approval gate rather than inserting it
  ahead of the mobile journey.
- The GPS phase delivers the approved time-aligned run telemetry, and automatic splits are built
  there as a **general running capability** shared by run screens and music intelligence. Canonical
  contract: `run_initiation_register.md`; historical decision record:
  `parked_feature_ideas.md`.
- Spotify integration and live-DJ behavior remain later possibilities with separate legal,
  privacy, provider, and platform dependencies.

*(Historical music snapshot updated August 2, 2026 after the revised prompt and calibration set were
committed at `b2227b2` with 382 deterministic tests green and no live call. If that lane resumes, its
next gate is a separate decision on a fresh 12-call diagnostic smoke. Combined V1 remains incomplete.)*

**Standing milestone — privacy / security / legal pass (added July 6, 2026):**
Before RunState is released, shared publicly, or gains multi-user/all-day-listening features,
a dedicated pass is REQUIRED covering: data privacy (what leaves the app, to whom, why —
extend docs/DATA_PRIVACY.md), consent tiers (run music vs all-day listening vs any cross-user
data), security (API keys out of source, DB credentials, dedicated DB user not root), and
legal basics (third-party API terms of service — Anthropic, Open-Meteo, Spotify, Last.fm/
ListenBrainz; health-adjacent data handling). Grows in weight with each phase — mobile + GPS
location data raises the bar again. Not a current blocker; IS a release blocker.

Feature map (July 6, 2026) — which features strongly trigger which concern:
- PRIVACY: all-day listening profiling (heaviest — needs its own opt-in tier); GPS routes +
  live location (Phase 6), including shared meetup beacons and any later live friend/group
  locations or trail playlist beacons; run/energy data → Anthropic API (live now, covered in
  DATA_PRIVACY.md); location → Open-Meteo (live now) — the **city name** goes to the geocoding
  endpoint and, **only after geocoding succeeds**, the **selected candidate's coordinates plus
  the run date** go to the forecast endpoint. The stored **state is used locally** to pick which
  returned candidate matches and is **never placed in either request**; a missing or blank city
  produces no Open-Meteo request at all. Authoritative as-built disclosure: DATA_PRIVACY.md.
  Also: cross-user aggregate content (Phase 7); run photos (share card/gallery).
- SECURITY: Anthropic API key out of source code; MySQL dedicated user, no root (existing
  rule); Spotify/Last.fm OAuth token storage; server + user accounts if Phase 7 happens.
- LEGAL: lyrics text licensing (Musixmatch paid, Genius scraping = ToS violation) — gates
  the lyric-trigger and lyric-aware reply features; third-party API terms (Spotify's
  data-use rules especially; Open-Meteo attribution); health-adjacent data (HR/effort —
  weight jumps at multi-user); user-generated content moderation if community playlists
  ship (Phase 7).

**Phase 5 as-built record — HISTORICAL (June 26, 2026 snapshot; superseded):**
The `musicContext` single-field model described below was replaced by `RunContext` with an
explicit `MusicMode` (MUSIC / NO_MUSIC / not recorded) plus optional note, persisted
separately, in RunStyle V1 (July 10, 2026). This section is preserved as a build record
only — the current music data model is documented in design_runstyle_v1.md and AI_AGENT.md.
Both features follow the same four-file pattern:
  Run.java -> add field | RunStorage.java -> add DB column | RunConsole.java -> add optional prompt | RunAgent.java -> add to user message

Step 1 — Music (manual input now, Spotify integration in mobile phase): **BUILT June 26, 2026**
- `musicContext` String field on Run.java (constructor param, last position; getter `getMusicContext()`; no setter — collected at log time like routeName)
- `music_context` column in RunStorage.java INSERT (11th `?`) and read in loadRuns via `rs.getString("music_context")`
- Optional "What were you listening to? (optional):" prompt in RunConsole.logRun(), stored null if skipped
- "Music:" line added to RunAgent.buildUserMessage(); music rule added to SYSTEM_PROMPT (reference artist/song only when it genuinely fits — never force)
- AI_AGENT.md data contract updated with the Music line
- The MySQL `music_context` migration was applied before the successful Phase 5 live verification.

**Future refinement — music reference variety (user note, June 26, 2026):**
When automation arrives (Spotify) and a runner plays the same song often, the agent must NOT keep
referencing that same song every run. Need variety logic — e.g. vary which track/artist gets called out,
or weight toward less-recently-mentioned songs — so references stay fresh. Belongs in Phase 5 advanced /
Phase 6 lyric-aware work, where the agent gains cross-run awareness. Not needed for manual Step 1.

Step 2 — Weather (automatic via Open-Meteo): **✅ SHIPPED. Cleanup completed July 6–7, 2026.**
Built June 26, 2026, then reconciled to full spec in the Step 2.5 cleanup (commits 8fa05c9 + 9ad3376),
with the TX→Texas geocoding fix following July 7. `design_weather_cleanup.md` is now the historical
build record; original spec is `design_weather_context.md`. What shipped:
- Fetch happens ONCE at log time and is STORED on the Run (persistence bug fixed — weather was lost before).
- Isolated `WeatherService` class owns geocoding + the Open-Meteo call (SRP, like RunAgent).
- `WeatherData` value object holds three fields: `temperature`, `apparentTemperature` ("feels-like"),
  `weatherCondition` — nullable `Double`, so 0.0 is a real temperature and null means "not recorded."
- Forecast API (today + ~92 days) instead of the archive API, so fresh runs get data.
- HttpClient connect + request timeout (5s) so a slow API never hangs the console.
- Failure never blocks logging — WeatherData stays all-null, the agent shows "Not available."
- State-based geocoding disambiguation now works: WeatherService canonicalizes abbreviations
  (`STATE_NAMES` map + `normalizeState`) so "TX" matches Open-Meteo's "Texas".
- Privacy doc written: `docs/DATA_PRIVACY.md`. AI_AGENT.md data contract updated.

**Constructor design decision — RESOLVED & BUILT (July 6, 2026):**
Chose Option B — a `WeatherData` value object groups temperature/apparentTemperature/weatherCondition
into one constructor param (composition) instead of appending 3 raw params. Shipped as `WeatherData.java`.

**Visual prototype:**
RunState_intro.html and RunState Promo.html exist in project root — both gitignored (local only).
Do NOT use as final look yet — intentionally unfinished.
Colors, branding, and button hierarchy are strong. Runner animation unfinished.

---

## Handoff — stabilization sprint complete (July 10, 2026)

**Where we are:** A four-task stabilization sprint shipped, each as its own commit, before
starting the Run Style redesign. All four are done and verified:

1. **Privacy doc ↔ code alignment.** `RunAgent.buildUserMessage()` no longer sends the runner's
   real username (a constant `"Runner"` label stands in); `DATA_PRIVACY.md` updated to match and
   its stale "rolling average pace/distance" bullet replaced with the candidate-based comparison
   summary that actually gets sent.
2. **RunAgent HTTP timeouts.** Added a reusable static `CLIENT` with a 5s connect timeout plus a
   5s per-request timeout (mirrors WeatherService). Verified a timeout throws an `Exception`
   subclass that the existing `catch` already handles → falls back instead of hanging.
3. **DB password out of source.** `RunStorage` reads `RUNSTATE_DB_PASSWORD` from the environment
   (same pattern as `ANTHROPIC_API_KEY`); throws a clear `SQLException` if unset. Still uses the
   dedicated `runstate_user`, never root. **Run-environment note:** the IntelliJ run config (and
   any shell that runs the app) must define `RUNSTATE_DB_PASSWORD` or the DB connection fails.
4. **First unit tests.** JUnit 5 (Jupiter) added, `test` scope. Tests live in a top-level `test/`
   dir (kept out of `src/` because the pom points `sourceDirectory` at `src`; set via
   `<testSourceDirectory>test</testSourceDirectory>`). `ComparisonServiceTest` has 9 tests
   covering candidate selection (recency window, cap-10, route-first, distance fallback), median
   aggregation (odd/even, outlier resistance vs mean), and the negative pre-filter (lower lift and
   unexplained higher effort filtered; explained PR effort allowed). Run with `mvn test` or the
   IntelliJ ▶ gutter arrows. Tested through the public `analyze(...)` only — internals stay private.

**Historical next step at this handoff — COMPLETED July 10, 2026:** the Run Style redesign
described here was subsequently built and verified. The current resume point is the music-work
section near the top of this document.

**Security-milestone reminder (unchanged):** rotating the DB password to a strong, unique value
belongs in the pre-release security pass — not the local-dev "basic" password used now.

---

## Handoff — next session entry point (July 7, 2026)

**Where we are:** Phase 5 is functionally complete. Step 1 (music) and Step 2 (weather) are both
built, committed, and pushed. The weather cleanup (Step 2.5) and the TX→Texas geocoding fix shipped
July 6–7, 2026 and are verified end-to-end. Privacy doc is done. No open weather work remains.
Manley is taking a break here to review the whole app before building deeper.

**Historical queue at this handoff — SUPERSEDED:** Effort Cost, Comparison Repair, stabilization,
and RunStyle V1 were completed after this note was written. This handoff also described the whole
music reply-style task as prompt-only; the corrected design separates its prompt-only craft rules
from its persistence-dependent cross-run frequency mechanism. The current resume point is the
music-work section near the top of this document.

**July 6, 2026 — UI phase paused; back to backend.**
UI/creative-direction exploration is paused (see creative_direction_ui.md v0.2 — §0 has the
locked July decisions reconciled from the prompt-iteration sessions). Moodboard is gitignored
(local only, like the HTML prototypes); creative_direction_ui.md is the surviving text record.
Historical focus at that time: the weather cleanup above, which has since shipped.
RESOLVED July 27, 2026 (was an open three-vs-four planning decision at this July 6 handoff):
the shared three-level energy domain is retained and the four-state State Scan sketch is
superseded as a domain proposal. Later UI work may refine labels and presentation without
changing stored meanings. Decision record: `design_music_intelligence_v1.md`.

**Current weather architecture (post-cleanup, for reference):**
- `WeatherData.java` — immutable value object (nullable `Double` temperature/apparentTemperature,
  String weatherCondition; final fields, no setters).
- `WeatherService.java` — `static WeatherData fetch(city, state, date)`; owns geocoding (forecast API),
  WMO code decoding, timeouts, and TX→Texas state canonicalization (`STATE_NAMES` + `normalizeState`).
- `Run.java` — one grouped `WeatherData` constructor parameter followed by `EffortLevel`;
  null-safe delegating weather getters.
- `RunConsole.logRun()` — fetches weather BEFORE saveRun so it persists.
- `RunStorage.java` — the current INSERT has 19 columns, including `apparent_temperature` and
  the later RunContext/effort fields; loadRuns reads the three weather columns via `rs.getObject`
  into a `WeatherData`.
- `RunAgent.java` — no longer fetches weather; `describeWeather(run)` formats the stored values.
`design_weather_context.md` still holds the original spec (WMO mapping, API URLs) for reference.
Note: `Runner.getCity()/getState()` exist (added June 26).

**Collab rules to follow:**
- One decision or one file at a time — never dump a full plan in one response
- Explain Java/OOP concepts as you build (value object, nullable Double, setObject vs setDouble, etc.)
- Present options with a recommendation; user decides before code is written
- Flag when it's a good time to commit
