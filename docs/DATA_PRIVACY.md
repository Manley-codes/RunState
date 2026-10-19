# Data & Privacy

This page contains the original **Java console** privacy record and a separate, bounded
**Android street-map and weather** records below. It is not yet a complete privacy notice for the Android
app. In particular, the console's request fields and local fallback behavior must not be read
as a description of the Android reflection flow.

The Android app stores run history on the phone and uses a hosted RunState reflection backend
on Render. That backend can send approved run context, including optional eligible music
metadata, to Anthropic. See [the current Android checkpoint](design/project_current_state.md)
and [backend documentation](../backend/README.md) for those implemented boundaries. The older
console statement that RunState has no server does **not** apply to the Android app.

## Protected activities and optional notes — October 18, 2026

The new Save Activity flow keeps an ended run recoverable on the phone before the runner explicitly
finalizes it. Pending activities remain outside finalized History totals/insights; reopening can
recover the pending Completion screen. Room 17 adds a finalization flag and nullable optional
post-run note; older completed records remain finalized and their existing fields are preserved.
An optional note is limited to 280 characters, stored locally, and is not added to reflection,
song-study, weather or voice-service requests. Notes do not automatically become an Energy answer.
The later explicit “Not sure” response is retained as an unknown answer in the existing post-run
Energy field; automatic timeout leaves it unanswered. Neither is treated as a measured Energy level
by insights. Hiding the reflection panel until a response is a presentation change; early preparation
and previously authorized provider requests continue.
Ending a run still starts the existing authorized reflection process; pressing Save Activity is
not a new consent boundary for those already-authorized requests.

Optional every-mile announcements use existing GPS distance, average pace and active time, with
local Android speech and a remembered Off setting. Countdown speech and the brief Energy invitation
also use local speech. They add no audio recording, cloud request, location collection or music
sharing. Software and silent Moto checks passed, and all old fields in the 33 saved runs and related
tables were preserved. Audible/field confirmation remains; exact scope is in project_current_state.md.

## Android saved splits and Energy insight — October 17, 2026

The phone can derive mile/kilometer splits from an already recorded GPS run. New GPS completions
store a run-owned preparation marker; complete, consistent evidence can produce a frozen local
snapshot of distance boundaries, active durations, boundary timestamps, pace, display unit and
calculation version. This adds no location permission or sensor collection. It uses the existing
saved route; the new split tables add no coordinates. Upgrading to Room 13 leaves older run fields
and observations intact and does not backfill old split history. Split failure does not erase
the run, and deletion cascades to its owned split rows through the same local data lifecycle.

A local History card can count saved pre/post Energy pairs in up to 20 recent genuine GPS runs.
It appears only with at least 3 paired answers in one starting-Energy group. Missing answers stay
unknown; Samples and confirmed test output are excluded. It reports counts, not a diagnosis or
proof that a run caused a change. These calculations do not need AI and do not upload the run
history, split rows, aggregates or extra Energy data. Existing optional reflection sharing is
unchanged; neither new feature adds a backend request field, provider account or permission.

## Independent song-ending receipt — local implementation, October 19, 2026

The new snippet-availability code is local and not yet deployed to Render. It does not change the
accepted creative writer or establish successful live Spotify loading/listening. The older
installed/deployed checkpoint below remains historical; current activation and device verification
are tracked in `design/project_current_state.md`.

Existing music observation, sharing and **Find song snippets** permission still govern this path.
The new `allowSongSnippetFallbacks` request flag is sent only for an opted-in run, with the existing
lookup flag and shared observed candidates, after the backend advertises
`X-RunState-Song-Snippet-Fallbacks: 1`. This protocol flag does not silently enable a phone setting
or backfill old runs. An older server receives the previous compatible request. The client can
recheck a cached missing capability on a later request after thirty seconds, without a retry loop.

The same chosen song's title, artist, optional album and recording duration may reach LRCLIB.
An absent album no longer discards an otherwise valid local Spotify URI/duration identity; known
conflicting identities are still rejected. The Spotify URI stays local. LRCLIB still receives no
GPS, run facts, Energy, audio or full listening-history export, and no new external service or
provider subscription is introduced by this source change.

Missing Musixmatch analysis no longer has to prevent actual-song audio. If no analyzed candidate
has a usable recording, the backend can choose a usable observed candidate as `OBSERVED_FALLBACK`.
The reflection uses the ordinary writing path; the saved selection does not claim a contextual
match or musical understanding. The song is chosen before writing and remains bound to that
response. Validated analysis continues through the existing accepted writer when available.

The timing preference is verified local curated coordinates, a matched repeated/title hook,
a published timed-line window, then a short opening of the same observed recording if no usable
timing exists. A timed line is not claimed to be a chorus or a complete sentence; the opening is
not claimed to be a discovered hook. Opening fallback lasts at most eight seconds and preserves
the controller's two-second end margin. Raw LRC remains temporary backend memory, not a stored
lyric archive, model input or Android payload. Missing analysis can use timed lyrics to derive
numeric playback coordinates without treating them as invented meaning or persona.

The new `songSnippet.version: 2` receipt records the chosen identity, numeric timing, source and
method. LRCLIB timings use source `LRCLIB`; an opening uses `RUNSTATE_RECORDING` with method
`RECORDING_OPENING_FALLBACK`. The existing `songCueId` remains a separate acknowledgement that
the writer used an actual landing. A playback receipt with no creative acknowledgement is valid
and does not mislabel an ordinary reflection as an analysis-derived pairing. Android retains this
receipt and its local playback binding in existing reflection-evidence JSON; no Room schema,
credential storage, audio capture or downloaded song file is introduced. A fresh response can
authorize playback, reopening does not repeat it, and a retry with no winner clears an old unused
receipt. Exact verified curated timing can remain the local playback choice even when analysis
was unavailable.

The first shared software pass completed 199 backend tests and 695 Android tests per variant;
subsequent review fixes have additional regressions. These are software checks, not proof of a
new successful live music sequence. Final test and activation receipts are recorded in project state.

## Earlier broader song snippets — installed/deployed foundation, October 18, 2026

The implementation is installed on the Moto at Room 16 and deployed on Render as `9b274e0`.
The user's deployment succeeded; public health confirms the snippet capability. Development left
his active run untouched, and the subsequent installation preserved app data without uninstall/clear.
Actual Spotify loading has a failed test case and listening remains unverified. Manley approved one specific LRCLIB test;
the actual future-run snippet setting remains off.

Room 16 adds nullable Spotify track URI, album and duration to new media-session observations.
Only a canonical Spotify track URI is accepted; other apps gain no recording fields. The additive
migration preserved all old fields and left older observations unknown rather than fill them from
today's player. Five silent Moto checks passed. The new run-level
`song_snippet_lookup_allowed` column defaults to false for every older run and requires music
sharing. The **Find song snippets** setting also defaults off and applies to future runs; each new
run freezes that choice. Turning it off changes future authorization, not the frozen permission
on an already-started run. Disabling music sharing/observation clears the future snippet setting.

For opted-in requests, recording album/duration accompany the bounded candidate identities to
RunState. Only the chosen song's title, artist, album and duration are queried at LRCLIB for a
matching recording's published line timings. Matching checks title/artist/album and duration within
two seconds, not definitive audio-edition identity. The Spotify track URI stays local and is never
accepted from the backend as a playback command. LRCLIB receives no route, Energy, run facts or
full listening-history export. This is a new external metadata source; no account/subscription
is introduced by this path. Optional lookup has a five-second cap inside the existing deadline.

Raw timed lyrics exist temporarily in backend memory while deriving a short window. They are not
stored in Room, logged, returned to Android or added to the model prompt. The model receives an
original general description of the musical landing alongside the existing approved song-study
context. The persisted result is numeric timing, source ID/version and the selected local recording
binding, not lyric text or audio. Third-party retention remains subject to that provider's rules.

Opted-in playback can load the exact recording observed during that run and attempt to restore
the prior recording afterward. Spotify supplies all audio; RunState does not download/extract it.
Preparation is preferred; a play-from fallback can briefly sound before pause/seek. Restoration is
best effort and the queue may change. Pause during cleanup is also best effort, not proof an
asynchronous remote load has been cancelled. Recordings changed by the user are not silently owned.

The source-class Lose Yourself lookup succeeded with public song metadata and yielded source
`10913690`, 99,340–102,140 ms. It did not send a person's run or control Spotify. Final software
checks passed: 193 backend tests and 672 Android tests per debug/release variant, all without
failures/errors/skips, plus three APK builds (release unsigned). The installed Room 16 database
passed integrity checks; hashes of all old columns in ten old tables matched, including 33 runs,
3,540 route points and 1,407 music observations. New recording fields stayed NULL and all old runs
kept lookup off. Private temporary copies were automatically removed. An additional isolated
default-off/settings-dependency case passed in 4.376 seconds without changing real preferences,
bringing the silent cases to six. A later paused Spotify capture succeeded, and its actual recording
metadata matched LRCLIB source `18848635` with 99,260–101,480 ms timing. Two hosted attempts stopped
before audio or Spotify commands; Musixmatch matched the song but returned no analysis material.
A separate capability check blocked POST and made no AI/audio request. No successful paired
reflection/loading/listening result is claimed. A later music-only probe issued prepare but timed
out while Spotify remained on the previous paused recording; cleanup sent pause, with no verified
restoration. After a refreshed session, prepare and explicit play-URI also timed out on the previous
paused song; unsupported/redundant variants were not tried. The soundtrack entry also returned empty
Musixmatch analysis. Final checks found Spotify paused, all ten old-table column hashes unchanged,
33 saved runs and Room 16 integrity OK. The test package and isolated test database were removed,
private copies deleted and the main app reopened. No real preferences or new sharing were enabled;
the specific test consent did not enable future-run sharing. The necessary new
Room 16 schema contains structure only, and the new LRCLIB client/test files contain code, not
user records or captured lyrics. Existing accepted reflection/voice references remain unchanged.

## Android multi-song reflection preparation — Ready to test, October 18, 2026

Manley authorized progressing from one prepared song to selecting a fitting reflection song from
music actually heard during the run. This extends the existing optional music-sharing path to a
maximum of 12 distinct Spotify title/artist identities observed PLAYING in that same opted-in run.
It does not collect music outside a run, request the account's listening history, or capture audio,
lyrics from Spotify or general notification contents. Both frozen per-run and current observation
and sharing choices, notification access and the unchanged configured destination still apply.

After 30 active seconds, eligible identities can be prepared sequentially through the authenticated
song-study route. Each early request contains the song identity and opaque preparation reference,
not run facts, Energy, GPS coordinates, weather or a full listening-history export. Musixmatch
matches/provides bounded song context; Anthropic performs the existing song-only study. The final
comparison adds approved final run facts and starting Energy to available matched meaning/themes/moods, then
selects one song for the accepted four-branch reflection writer. No separate song is selected for
each finishing-Energy answer. This is more metadata/provider use than the previous one-song path.

Room 15 stores durable per-identity claims and final selected-song context.
Migration preserves existing Room 14 rows and does not backfill claims for old runs. Run-owned
metadata follows local run deletion; no provider key or song audio belongs in those rows. Prepared
source/study material retains the existing bounded process-memory cache, not a new disk archive.
External providers retain data under their own settings. A saved selected-song identity does not
establish a complete listen or prove music affected the run.

At most 12 candidate preparations and one additional final comparison are permitted per run. More
studies increase API usage and can incur charges even when a run is discarded or a candidate is
not selected. Final source lookup is bounded to 30 seconds, ranking to 10 seconds, within the
existing 90-second total deadline. Only available matched material is used. One candidate skips
ranking; a failed rank uses the first available matched candidate, and no source yields an ordinary
reflection without invented song meaning. The prior single-song path remains the older-server
fallback. The exact accepted
writer/voices remain unchanged, and snippets still require one of five curated recordings to be
the same current Spotify track. This does not add queue editing or audio extraction.

The final Android build is installed and Render commit `7447c65` is Live; preparation/selection
capabilities both report version 1. Backend 173 tests, Android 645 tests per variant and all three APKs passed. The final
23-case silent Moto check included a real Room/production-serialization test with fake HTTPS,
two offered songs, a second-song winner and exact saved text/cue with no request or playback on
reopen. Actual Room 14→15 migration and the final Room 15 reinstall preserved every row and column
in the 10 non-Room-identity tables, including all 32 saved run rows. Temporary private copies and
test package were removed after both device checks. These checks sent no live-provider request
and do not establish genuine-run quality. The necessary new schema export at
`android/app/schemas/com.runstate.mobile.data.local.RunStateDatabase/15.json` contains schema
structure, not copied user records. See project state for full receipts.

A separate silent real-hosted check passed with fictional three-mile/32-minute morning-run facts,
starting Energy Moderate, and three explicitly supplied song identities: Stronger / Kanye West,
Lovely Day / Bill Withers and Good as Hell / Lizzo. All early preparations completed; the final
`CONTEXT_MATCH` choice was Lovely Day. That proves comparison of at least two available matched
analyses, not an exact count of three. The repeated identical request returned the same cached
result. This check used the configured real providers, no audio or Spotify controls, and no Room
run writes. All 32 actual saved run rows/every old-table field stayed unchanged on Room 15;
private copies/test package were removed and the normal app reopened. No new permanent file was
created for this verification. This is synthetic live-service evidence, not genuine-run or
listener-quality verification.

## Earlier single-song preparation — approved October 18, 2026

This is the historical installed/deployed one-song boundary, now superseded by the activated
multi-song extension above. Its earlier recorded results do not verify the new selector.

Manley approved an early cloud request after the first30 active seconds of a RUNNING run, with
both frozen per-run music opt-ins, current music preferences, notification access and an unchanged
hosted destination. He was told that AI charges can occur even for a discarded run, the first
eligible song might not be the last/best one, its snippet can be skipped if another recording is
current, and weak networks may still leave a wait. The feature is locally verified and installed:
160 backend/609 JVM tests per Android variant and packaged JAR/three APKs passed. Actual Moto
13→14 migration preserved all32 old records/every original field, adding only the empty claim
table/Room identity. Silent53 checks passed/5 gated skips with no provider calls; every actual
field matched afterward, private copies/test package were removed and normal app reopened on
Room 14. Manley later committed/pushed the feature and Render deployed `afd1bb5` successfully;
health confirmed the preparation capability. The complete controlled warm code-path check then
passed, including an actual early metadata request, fresh final facts, one selected synthesis,
song stop/restoration and exact-text reopen. Facts' natural completion to reflection player
handoff measured 2,245 ms, not exact audible onset. All 32 actual saved records and inspected
fields remained unchanged on Room 14, and private copies/test package were removed. The first
generation failure and earlier playback failures remain recorded in `design/project_current_state.md`;
this fixture is not genuine-run/full normal-UI proof and introduces no additional sharing scope.

Room14 adds `run_song_preparations`, one claim per run. It stores the preparation UUID, selected
title/artist, durable observation sequence, opaque destination fingerprint, claim time and status.
These metadata remain local with the run and cascade on deletion. Migration creates no claims
for old runs and does not rewrite previous observations, metrics or reflections. The fingerprint
binds a claim to its configured connection without storing another access code in the claim.

After a capability check, authenticated HTTPS POST `/api/reflections/song-study` sends only that
opaque preparation UUID and one title/artist. It sends no run ID, active time, Energy, GPS,
weather, audio, route or listening-history list. Musixmatch receives song identity/catalog data;
Anthropic receives bounded matched source for the existing song-only study. Final completion
still supplies the separately approved fresh run evidence to write four conditional reflections.
No final reflection, voice or audio is generated by the early study itself.

Unlike the earlier request-only source handling, the backend can retain prepared matched source
and validated interpretive notes in process memory, up to32 entries. The reuse TTL is2hours;
completed expired entries are purged on preparation/final-request/health checks, or all entries disappear
with the process. This is not a guarantee of erasure exactly at2hours. Source/study material is
not written to disk, returned to the phone, logged or stored in Room. External providers retain
data under their own account/service settings; RunState's memory policy does not control them.

The final transport can carry a matching opaque preparation reference outside model/run facts.
It keeps the same pinned observed song even if Spotify changes. A destination change suppresses
that music/reference; an older server receives no early metadata. Failed/interrupted claims are
not automatically retried in the background, including after process death. Expired, failed or
lost server preparation may require cold final generation and additional AI usage. Current-song
playback guards still skip an unavailable/different recording; no queue editing, song ranking,
new provider account or audio storage is introduced.

## Earlier song study and hosted verification — October17–18,2026

**Hosted activation and isolated normal-path check:** Manley promoted/deployed2f3e77c. The Moto
check loads its existing encrypted connection on-device; no access code is extracted to the host
or included in arguments/reports. Controlled sample rows and a fictional song observation stay in
an in-memory Room database, separate from saved History. Only this fictional completed evidence
and the existing one-song identity/cue go through the normal hosted reflection path; exact selected
text uses existing ElevenLabs speech. Temporary private safety copies under
`android/app/build/runstate-update-*` or `runstate-audition-*` support full before/after stored-field
comparison and are removed after preservation succeeds. All32 real records/Room13 fields matched
through installation and checks. The final49.309s Moto check completed the hosted request, matched
cue/version1, persisted selected text, played actual custom cloud speech and the Good as Hell
catalog passage, then verified exact paused-position restoration. A recreated coordinator retained
the exact text without another HTTP request or automatic passage. This is an isolated fictional
fixture, not a genuine-run/full normal-UI test. Manley accepts its new reflection while preferring
the original wording, and flags the facts-to-reflection wait for improvement.
Temporary private copy directories and test package were removed; root independently found zero
remaining `runstate-audition-*`/`runstate-update-*` directories. Music-only duration probes send no
backend/AI/voice request and write no run data. Diagnostics expose fixed stages/bounded timing
differences, not private configuration, song identity, lyrics, source payloads or generated text.

**Selected voice preparation installed; timing unresolved:** only the exact selected reflection may be sent to
the existing speech provider before remaining facts/intake permit playback. The bounded change
reuses that one synthesis in memory; it does not upload the unused candidate family, add a new
provider, regenerate text or create an audio file/cache. Obsolete preparation must be cancelled
on Stop, background, navigation or destination/retry/run replacement. Software/silent phone checks
passed; the first timed live fixture failed on a new+1ms duration event after speech, and cleanup
paused correctly. All32 real saved fields stayed unchanged and private copies/test were removed.
The later bounded rounding/timing update passed596 JVM tests per variant/three APK builds and
was installed with all32 real saved fields preserved. A separate music-only probe passed stop/
restore with0ms duration delta and no AI/voice request. No fresh reflection or shorter delivery
was proved by that probe, and no fewer billed calls is established. The later approved early
song-study feature above is separate from this earlier delivery repair.

**Later October 17 — normal-app integration built locally:** Manley accepted the three selected
listening examples and authorized connecting the same song-study/writer process to the app.
The normal provider now requests bounded matched material even for supported curated songs:
meaning, themes with supporting passages, moods, genres, whitelisted entities and an optional
text snippet. Musixmatch receives only song identity/catalog lookup data, never run facts,
coordinates, Energy, run IDs or a listening-history list. The first Anthropic call receives
song-only material; the second receives bounded original song context, validated interpretive
notes and the already approved completed-run evidence. Partial media and hourly weather retain
their provenance. This supersedes the older label-only/no-curated-lookup description below.

In this earlier non-prepared path, raw provider material and study notes remain request-scoped and are neither returned in the app
response nor saved in Room or logs. The existing four final candidate texts and frozen request
continue to use the phone's existing reflection persistence. This makes no claim about retention
by external providers. No new phone permission or provider key is added to Android. Both music
choices and an eligible same-run PLAYING observation remain required. A server-confirmed matching
cue and writer-version response are necessary before the new local passage can play; unavailable
analysis may return ordinary readable text without a clip. Restart never automatically replays
music. Five curated entrances do not establish whole-catalog song/hook selection.

At that earlier local integration checkpoint, no deployment, live provider request or phone data
change occurred. Subsequent hosted activation and the isolated-fixture check are recorded above. The exact accepted
reference and development story are in `docs/design/music_reflection_accepted_baseline.md` and
`docs/design/music_reflection_development_story.md`.

**Earlier isolated trial (historical):**

The explicitly invoked `SongReflectionTrialTest` main harness is separate from the phone and
hosted reflection path. It can request one matched song's bounded supporting theme passages,
meaning, moods, genre, whitelisted entity descriptions and optional text snippet from Musixmatch.
It sends that material to Anthropic for a song-only study, then sends validated study notes and
fictional run facts for four experimental reflections. The October 17 checks used no real run
history, coordinates, account/listening history or phone observation. The model's artist context
is interpretive, not verified biographical data.

Raw provider material and study notes stay in RunState request memory; they are not written to
source files, Maven test reports or the phone database. The console receipt shows counts and a
limited generated excerpt, not raw supporting passages or all study prose. These local boundaries
do not claim zero retention by either external provider. Normal `mvn test`, backend startup and
phone use do not enable this experiment. It is not a deployment, a new phone-sharing preference,
a public-use clearance claim or a change to the accepted audio sequence.

## Android curated song passage — October 16, 2026

**Optional backend analysis integration:** a backend configured with `MUSIXMATCH_API_KEY` can send
the single supplied song title/artist to Musixmatch and request analysis for its matched provider
track ID. It sends no run ID, GPS, weather, metrics, Energy or listening-history list to Musixmatch.
Only short theme/mood labels are passed onward to Anthropic with the existing reflection context;
quotes, lyric text, free-text explanations and entities are discarded, with no analysis cache or new
song-history storage. Known curated cues bypass this lookup. The integration is disabled without
the backend key. Manley separately saved that key on Render and deployed `bfc64ad`; local Windows
credential settings alone do not configure hosting. No phone preference changed. Broader
tester-facing consent remains separate.

**Selected reflection voice, live and verified October 17:** Manley chose his existing ElevenLabs
designed voice for reflections while retaining local Android speech for saved facts. The optional
server-side speech path sends the exact selected reflection text to ElevenLabs; that text can
contain approved run/weather/music context already used in the reflection. It does not need the
route, a full listening-history list or all four unused candidates. Connected-account voice lookup
was read-only. After Manley privately created/saved the backend key and pushed the voice code,
the hosted phone check sent one selected, newly generated fictional sample reflection to
ElevenLabs and played its returned audio successfully; no saved History was uploaded. Android
plays the returned bounded audio from memory and releases it on completion/cancellation; neither
the phone nor backend writes or caches generated audio. This does not remove provider-side
retention under the ElevenLabs account/service settings. The standard request does not opt into
enterprise-only zero-retention mode. Deliberate repeated Read requests can consume speech credits.
Manley saved the backend key privately on Render; deployment and hearing passed at `cb23da6`.
Broader tester-facing disclosure remains separate. No provider key is sent to or stored on the phone.

The first automated passage version uses the existing optional music-observation and separate
reflection-sharing choices. A known song must appear in eligible same-run Spotify observations.
Selection stays local and sends one approved title/artist with partial-report provenance, plus
RunState's own bounded editorial theme/connection note, to the reflection backend and Anthropic.
It does not send a listening-history list, recording, lyrics, Spotify account credential or artist
profile. Editorial interpretation is separate from what the device actually observed.

The playback title/artist, selected start and bounded duration are frozen in the phone's existing
reflection-evidence JSON and stripped from the network request as local transport data. A matching
server cue acknowledgement is required for automatic passage playback. An older hosted server
receives the previous compatible request instead. Existing saved reflections are not rewritten.

Using the existing notification-listener access, RunState can send ordinary pause/seek/play
commands to the matching current Spotify media session. No new Android permission, SDK account,
audio capture, recording extraction, other-song selection or queue mutation is added. Spotify's
app plays its own audio. Successful speech is required before a passage starts; leaving/stopping
the screen or changed playback cancels ownership. Normal completion attempts and verifies the
earlier position and playing/paused state; an interruption never automatically resumes music.
Missing access, a different song or unsupported controls skip the passage. History stays voice-only.
Initial coverage is one curated song; this is not an automatic whole-catalog meaning/chorus engine.

## Android street map — October 16, 2026

The GPS Start and Active screens offer an optional **Live map**, rendered by MapLibre Native
13.7.1. The normal Start screen uses the original static illustration, and the normal Active
screen emphasizes run controls and metrics. Opening those normal screens does not initialize
the street map or request its tiles. The illustration and corner tree shadows are artwork,
not evidence of the runner's surroundings. History's existing offline path preview remains
separate and does not request street tiles.

**October 18 correction, installed and checked:** the visible GPS Start screen now
prepares location automatically with the existing precise-location permission and location enabled.
It requests foreground GPS updates at 1-second intervals even with the static artwork showing.
This uses battery while Start is visible; it is not background tracking or automatic run detection.
Opening/closing the optional map, countdown and countdown cancellation retain the same listener.
History, Settings, backgrounding and Sample mode stop preparation, and it is removed before the
official recorder takes over. The previous Finding GPS gate, timed wait and Start anyway choice
are superseded: an intentional Start proceeds through countdown without waiting for signal quality.

The preview stays in local memory: it creates no saved run row, route, distance or running clock,
and sends no location to reflection/backend providers. Its points are never imported into the
saved route. Map display can use an accurate cached location no more than 30 minutes old, clearly
labeled last known; only a usable source fix within 15 seconds is labeled current. Static artwork
does not initialize the map or request tiles; only an opened live map can request area resources
as described below. Thus automatic foreground GPS preparation and map-provider network requests
have different boundaries.

Before service dispatch, backgrounding or recreation cancels an unsaved countdown and cannot
start it later. Once the handoff has already been sent, a same-application-session marker can
survive rotation only to track that existing handoff; it does not authorize another run. This
correction adds no permission, provider, route-upload feature or database-schema change.
Both replacement 632-test suites and three APKs passed. All 31 unique selected phone checks
passed across runs after a test-only scheduling correction. Installation/checks preserved all
32 completed records, every inspected field across 11 tables and Room 14; temporary private
verification copies and the test package were removed. Actual Android foreground GPS registration
was observed with no run recorder active. Normal ignored outputs under `android/app/build` were
needed for builds/tests; no new permanent project file or schema was created. The earlier readiness
gate's results are historical. Outdoor acquisition, distance accuracy, pace/splits and screen-off
behavior remain unverified; receipts are in `design/project_current_state.md`.
Missing location permission or evidence is not replaced with a fabricated position. During a
run, the map uses the existing recorded GPS evidence; the renderer does not start a second
location recorder.

Opening Live map with an actual location anchor can request vector map data and label glyphs
from `https://tiles.openfreemap.org` over HTTPS. The inline RunState style uses the stable
`/planet` TileJSON endpoint, which identifies the provider's current weekly tile set, and
`/fonts/Noto%20Sans%20Regular/{range}.pbf` for map labels. There is no external style, sprite,
terrain source or RunState-backend proxy. Without an eligible anchor,
the app does not request an arbitrary location's tiles as if that were the runner's position.
The tile address identifies the viewed area and zoom level; the provider also receives the
connection's IP address and an app-identifying User-Agent. Following the position or panning
can request additional areas. Consequently, **local route storage does not mean that viewing
the street map reveals no location information to an external service**. OpenFreeMap may use
a CDN. Its access logs omit IP addresses by default, but error logs and temporary security
logs can include them; see the provider's [privacy policy](https://openfreemap.org/privacy/).

RunState draws the route locally. Map requests do not include saved route-point lists, run
IDs, Energy answers, reflection text, music metadata, audio, or backend access codes. There
is no route-upload, navigation or map-account feature in this slice. A stale or paused marker
is identified as the last recorded position rather than represented as a fresh location.

MapLibre caches viewed map resources locally under its normal HTTP caching behavior; the
cache itself can reveal areas previously viewed. The map cache file `mbgl-offline.db` and
its `-wal`, `-shm` and `-journal` sidecars are excluded from Android cloud backup and device
transfer. That is separate from the existing run-database exclusion. App-controlled map
request logging and the SDK action journal are disabled. No offline-area download or tile
prefetch feature is enabled. These controls do not remove the provider's network logs.

RunState's original map style colors actual source features separately: sage ground/landscape,
cream buildings, blue water and distinct dashed paths. These colors are cartographic choices,
not a land-access assessment or a claim that every sage area is a park. OpenFreeMap supplies
OpenStreetMap data using the OpenMapTiles schema. The map displays linked OpenFreeMap,
OpenMapTiles and OpenStreetMap attribution and identifies RunState in requests. No account
or API key is required. This public service is best effort, without an availability guarantee;
tracking and run controls do not depend on map tiles loading. A map-loading failure does
not mean GPS recording has failed. See the
[OpenFreeMap terms](https://openfreemap.org/tos/), [integration guide](https://openfreemap.org/quick_start/)
and the
[platform/tool record](README.md#platforms-and-tools-used).

## Android weather near the run start — October 16, 2026

`Run settings` → **Weather near my run** is a separate remembered choice, **off by default**.
Turning it on applies to future GPS runs; it does not upload old routes or retrieve historical
weather for old runs. Location permission, optional street-map use and music consent do not turn
this choice on. Turning it off stops capture for future runs; it does not erase saved weather or
change a reflection whose evidence was already frozen.

After one eligible accepted GPS point is saved within the first two minutes, the phone may make
one direct HTTPS request to `https://api.open-meteo.com/v1/forecast`. Latitude and longitude are
rounded to **two decimal places before transmission**, giving roughly kilometre-scale precision
that varies with latitude. This is approximate location, not anonymous information. The provider
also sees the connection IP address. The URL includes the requested run-start UTC hour, selected
weather variables and unit/time-format choices. No full route, run ID, Energy, music, reflection,
backend access code or Anthropic key is included. A redirect is not followed, and RunState does
not log the request URL or provider exception text.

The saved result is an **hourly modeled estimate near the start**, including temperature,
feels-like temperature, humidity, wind, condition, provider/resolution, the target run start,
the valid hour and fetch time. Its Room row is linked to the run's permanent UUID. The weather
snapshot contains no latitude/longitude; precise GPS route storage elsewhere in the run database
is a separate feature. Completion and expanded History use the saved facts, with the hour shown
in the run's saved timezone. There is no continuous weather polling or background weather-only
location acquisition. Samples, missing early fixes and failed lookups never create made-up weather.

When an initial reflection is prepared, an already-ready snapshot may be included in the
request to **RunState on Render and Anthropic**, alongside the other approved run facts.
Only weather facts and their time/source metadata are sent, **not lookup coordinates**.
RunState does not wait for weather before saving or generating a reflection. Retry/reopen reuse
the original evidence; late weather does not silently rewrite an existing reply.

Provider and CC BY 4.0 licence links appear beside the weather display on Completion and expanded
History. The bundled notices identify the data source and RunState's unit/condition-label display
conversions. See [Open-Meteo's licence record](https://open-meteo.com/en/licence).

The provider's [API documentation](https://open-meteo.com/en/docs) describes the modeled hourly
values; its [terms and privacy record](https://open-meteo.com/en/terms) describe its own data
handling, licensing and service limits. This prototype uses the public noncommercial endpoint;
review commercial service terms before a commercial release. RunState has not purchased a
weather subscription or created a new weather account.

## Java console privacy record

The original Java console app stores run history **on your machine**. That console history
is not synchronized to a RunState server or account system.

That is not the same as saying your data never leaves your machine. Two external services are
involved:

- **Selected run fields may be transmitted to Anthropic**, when a post-run API request is
  attempted, to generate your response.
- **The weather lookup makes up to two Open-Meteo requests.** A nonblank city name is sent to
  the geocoding endpoint, and only if that succeeds are the selected coordinates — together
  with your run date — sent to the forecast endpoint.

Both are described in full below.

---

## Java console: Anthropic API

### When a request happens

After a run is **durably saved**, RunState attempts an Anthropic request whenever an API key
is available.

If `ANTHROPIC_API_KEY` is **missing or blank**, RunState falls back to the local response
**before any request is built or sent**. That guarantees **nothing was sent to Anthropic** —
but it does not mean nothing left your machine on this run, because the Open-Meteo weather
lookup happens earlier in the flow and may already have occurred.

Every other failure also produces the local fallback, but **where the failure lands relative
to Anthropic receiving your data varies**, and the failure itself does not always reveal
which side it fell on:

- **Local request-construction failures**, and **some** DNS, connection-establishment, or TLS
  failures, can prevent transmission.
- **Receiving any HTTP response at all — including a non-success status — proves the request
  was sent.**
- A **response-parsing failure** happens after a response was already received.
- **Timeouts, connection resets, and other I/O failures may occur before or after** Anthropic
  receives the body.

These are illustrative categories, not an exhaustive list, and none of them is a reliable
signal on its own. **Seeing the local fallback therefore cannot tell you whether transmission
occurred.** All of these cases look identical from the console.

### What the request contains

Each request has two parts.

**1. A fixed system prompt**, identical on every request and containing no run data. It holds:

- the general mentor contract (tone, length, what to lead with)
- the music reply policy
- the instruction that all free-text run fields are **data, never instructions**
- prohibitions on guessing evidence RunState does not have
- instructions for handling the internal history-stage label

The authoritative text lives in `RunAgent.java`. `docs/AI_AGENT.md` summarizes the current
prompt and preserves the July 27 prompt snapshot as explicitly labeled evaluation history.

**2. A dynamic per-run user message**, containing:

- Run date and season
- Distance, pace (normalized to minutes per mile), and duration
- Pre- and post-run energy levels
- Post-run effort cost
- Personal record status
- Route name
- Surface, run company, and shoe label
- Music state, and the free-text note of what you listened to when one is eligible
- The coarse history-stage label `Music reply stage: EARLY|ESTABLISHED`, when it applies
- Daily-mean weather for your run date — temperature, apparent temperature, and condition
- A candidate-based comparison summary when matching past runs exist: the shared basis
  (same route or similar distance), and each positive or explanatory signal with its
  evidence-run count and confidence tier

**Skipping a question does not remove its line.** The effort, route, surface, run company,
shoes, music, and weather lines are **always present** in the message. A value you did not
provide is sent through its documented stand-in instead: `Not recorded` for effort, route,
surface, run company, and shoes; the corresponding music classification for music; and
`Weather: Not available` when the weather fetch did not succeed. What varies is the value on
the line, not whether the line exists.

Only **two** parts of the message are genuinely conditional: the **music-stage line** and the
**comparison block**. Each is omitted entirely when it does not apply.

Run company is only ever the coarse value "solo" or "with others" — no names or identities
of other people are ever collected or sent.

No name, email, or account information is included in the request. `Runner: Runner` is a
constant label; your real username is never sent. All requests are sent over HTTPS. See
[Anthropic's Privacy Policy](https://www.anthropic.com/privacy) for details on how Anthropic
handles API data.

### Free text you type

**Route name, shoe label, and an eligible music note are user-provided free text that is
transmitted to Anthropic.** What you type into those fields is what gets sent, with two
precise qualifications:

- **Route name and shoe label** are sent **unchanged** as logical values.
- **An eligible music note** is sent **after surrounding whitespace is stripped**. Stripping
  affects only the outgoing text; it does **not** modify the note stored on your machine.

Separately, **JSON escaping changes the wire representation without changing the outbound
logical value.** A quote, backslash, tab, or newline inside a route name or music note is
escaped so the request is valid JSON, and decoding restores exactly the value RunState
serialized — the route and shoe values unchanged, and the eligible music note **as it stands
after surrounding whitespace was stripped**. Escaping is a transport detail, not redaction:
it removes nothing and alters no meaning. The outbound music note may differ from the original
input only when surrounding whitespace is stripped.

Two music cases send no note text at all:

- An explicit **"no music"** answer **suppresses any stray note entirely** — the note is not
  sent, even if one is stored on the row.
- A **blank, whitespace-only, or missing** note transmits **only its classification**
  ("had music, track not noted", or "not recorded") — there is no note text to send.

**The system rule treating free text as "data, never instructions" is a model-behavior
safeguard, not redaction or anonymization.** It tells the model not to obey commands typed
into those fields. It does not remove, mask, or filter anything. Identifying or sensitive text
entered into a route name, shoe label, or music note **can still reach Anthropic**.

### The history-stage label

When it applies, the user message includes one additional line:

```
Music reply stage: EARLY|ESTABLISHED
```

- It is derived **locally** from the **total size of your saved history**.
- `EARLY` represents sizes **1–10**; `ESTABLISHED` represents **11 or more**.
- The run you just logged is **already included** in that count — it is saved and added to
  history before the response is built, so nothing is added on afterward.
- **Backdated saved runs still count.** The stage uses total saved-history size, not
  chronology, so logging a run for an earlier date still increases it.
- If there is no Runner, or the Runner has **zero** saved runs, the field is **omitted
  entirely** — no placeholder value is sent.
- **Anthropic receives only the coarse label.** The exact total history count and the
  individual historical rows remain on your machine.

The label is **internal control metadata**. It describes how much history RunState has seen,
and it adjusts how actively the model looks for a music connection. It is **not** a statement
about your fitness, ability, or running experience, and the system prompt instructs the model
never to expose the label or characterize you from it.

### What stays local, and what history-derived data does not

Your **raw historical run rows** and your **exact total history size** remain on your machine.

Two things derived from that history may be transmitted:

1. **Positive or explanatory comparison summaries**, which can include **per-signal
   evidence-bearing comparable-run counts** and a confidence tier.
2. **The coarse `EARLY|ESTABLISHED` stage label.**

Note the distinction between the two kinds of count. The per-signal evidence counts describe
**how many comparable runs supported that one signal** — a small number drawn from the runs
that actually qualified. That is not your total history size, which is never sent as a number.

### What Anthropic is never sent

- Your name or email address
- Your run start time or time of day
- GPS coordinates, precise device location, routes, or splits — beyond the route **name** you
  typed yourself
- Time-aligned run telemetry
- Streaming or music-provider metadata
- Playback history or playback timestamps
- Any history of whether previous replies mentioned music
- Your **RunStyle profile** — the local pattern analysis (which patterns are forming, and the
  context that tends to accompany your productive runs) is computed on your machine and is
  never sent to any API
- Your raw run history rows, and your exact total history size

The system prompt additionally **prohibits the model from guessing any of these absent
signals**. Absent evidence is treated as absent, not reconstructed from what is present.

### The fallback response

The fallback response generator is **local, deterministic, and music-neutral**. It produces
the same response regardless of your music state, and no music note text ever appears in it.

**Calling the fallback generator itself performs no network request.** But as described under
"When a request happens" above, receiving a fallback response from the normal path does
**not**, on its own, tell you whether Anthropic received your run data. Only the missing or
blank key guarantees that nothing was sent to Anthropic — and even then, the Open-Meteo
lookup earlier in the flow may already have happened.

---

## Java console: Open-Meteo API

The weather lookup makes **up to two requests**, and they do not send the same thing. Either
may not happen at all.

**0. No city, no request.** If your stored city is **missing or blank**, RunState makes **no
Open-Meteo request** and simply records no weather for the run.

**1. Geocoding.** A **nonblank city name** is sent to Open-Meteo's geocoding endpoint, which
returns up to five candidate locations. **Your stored state is not included in that request.**
It is used **locally**, on your machine, to pick which returned candidate matches — so that
"Springfield" resolves to the right one. The state never appears in either request URL.

**2. Forecast.** This request happens **only if geocoding succeeded and returned a usable
candidate**. If it returned nothing, RunState stops there and no forecast request is made.
Otherwise the **latitude and longitude of the selected candidate**, together with your **run
date**, are sent to Open-Meteo's forecast endpoint to retrieve daily-mean conditions.

Those coordinates are **Open-Meteo's own published coordinates for the city it matched** —
they are **not** device GPS coordinates and not your precise location. RunState never reads
device location.

**Timing.** The weather lookup happens **before the run is durably saved** — the weather
becomes part of the run at construction. A later save failure therefore does **not** undo an
Open-Meteo request that has already been made.

Open-Meteo is a free, open-source weather service — no account or API key required. See
[open-meteo.com](https://open-meteo.com) for details.

---

RunState is a learning and portfolio project. The two external APIs in the console record
above power its AI response and weather context features; this does not enumerate the
Android app's separate hosted services or its map requests.
