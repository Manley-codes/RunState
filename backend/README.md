# RunState reflection backend

This directory is a standalone Java 17/Maven project containing the reflection contract, Anthropic
provider adapter, and a small HTTP server. Normal startup binds only to `127.0.0.1`.
Explicit `--remote` startup prepares an authenticated internal service for a private HTTPS ingress;
the repository does not deploy it or make that HTTP port safe to expose publicly.
Android's debug emulator build can connect locally through the host alias `10.0.2.2`.

For a physical Android phone connected by USB with debugging enabled, run
`adb reverse tcp:8787 tcp:8787` from the Android Studio terminal before opening the debug app.
The phone's debug build then connects to its own `127.0.0.1:8787`, which ADB forwards to this
backend on the computer. Keep the backend bound to computer loopback; this route does not require
opening port 8787 to the Wi-Fi network. The reverse mapping lasts only while the device's ADB
connection does. That local HTTP route remains debug-only. An explicitly configured hosted HTTPS
connection can be used by debug or release builds; an unconfigured release stays offline. The
phone's separately entered demo access code is kept in encrypted AndroidKeyStore-backed storage,
not built into the APK. The Anthropic key belongs only in the backend environment, never the phone.

Java records are used as small immutable data holders. A record exposes its values through generated
accessor methods and does not offer setters; its constructor can still reject invalid combinations.

## Individual access foundation — November 7, local only

Remote mode now supports a small, server-managed list of individual access codes. This is a
pilot foundation, not a public sign-up, subscription or spending-control system. It has not
been activated on the hosted service. Android's existing encrypted connection-code entry
already uses the same Bearer format; no APK credential or phone change is needed for this part.

Configure **one** access mode behind the existing private HTTPS ingress:

- `RUNSTATE_DEMO_TOKEN`: existing owner-only behavior. Never share it with recipients.
- `RUNSTATE_ACCESS_GRANTS_JSON`: a JSON array of individual grants. Do not also set the demo
  variable; the launcher rejects both together instead of silently falling back. A malformed
  list prevents startup, while `[]` deliberately denies all paid endpoints and retains health.

Each grant requires the first four fields. Set the optional fifth field explicitly for new recipients.

| Field | Meaning |
| --- | --- |
| `principalId` | Stable opaque recipient ID, 1–64 lowercase letters/digits/underscores/hyphens, starting with a letter or digit. Do not use an email or name. |
| `tokenSha256` | 64 lowercase hexadecimal characters: SHA-256 of that recipient's exact access code. The code must come from at least 32 cryptographically random bytes, encoded URL-safe; accepted code length is 43–128. A human-made password is not suitable. |
| `expiresAt` | Required ISO-8601 instant, for example `2026-12-01T00:00:00Z`; the code stops authorizing new requests at that instant. |
| `enabled` | Required JSON boolean. `false` disables the credential after this configuration is loaded. |
| `reflectionTier` | Optional: `tracking`, `trial` or `pilot`. Omitting it retains the full access of an older grant. |

The array permits up to 64 credentials and 32,768 characters. Duplicate fingerprints, unknown
or missing fields, duplicate JSON keys and incorrect types are rejected. Multiple different
credentials may share a principal during rotation; do not assign that principal to another
person. Keep the raw code private and deliver it separately; backend grant records store its
fingerprint only. No real codes were generated as part of this source change.

Removing/disabling a grant takes effect **after updated configuration is loaded by a server
restart/redeployment**, not immediately when an environment setting is edited. Expiry is
checked on every request, including cached-result retrieval. Already admitted provider work
is not cancelled mid-call. Access errors do not reveal whether a code is unknown, disabled or
expired. Keep Host/Origin checks, HTTPS ingress and credential redaction intact.

The server derives work ownership from the authenticated principal, never a caller-supplied
user ID. Reflection and preparation caches are partitioned by that identity. Individual-mode
preparation handles are also namespaced before every provider lookup, including multi-song
candidates; responses retain the client's original IDs. Owner/local modes retain their old
internal handles. Reflection prompts, song content, playback and voices are unchanged.

With private persistent storage, the backend can recover a written reflection for 24 hours
and replay its selected recording for seven days across restarts. Song preparation has a
separate in-memory cache. The monthly limits below cover new provider requests.

Security reference: [OWASP REST security guidance](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html).

## Monthly provider spending limits

The local trial and pilot use a separate allowance from the dollar limits below. Tracking recipients get no new provider work. A trial recipient explicitly starts a seven-day preview with two runs. An operator-assigned pilot gets eight runs per UTC calendar month. Starting a preview does not enroll the runner in billing. Keep each runner's principal stable across credential rotation.

A capable server advertises `X-RunState-Reflection-Access: 1` on public health. Authenticated clients read `GET /api/reflections/access`, opt in with `POST /api/reflections/trial`, and reserve one run with `POST /api/reflections/run-access`. Reusing the same run ID covers its authorized studies, written choices and one spoken choice. A held run normally releases its slot after 24 hours or at period end, whichever comes first. Early preparations are capped at 12 per run and 24 per trial or 96 per pilot month, including attempts on abandoned runs.

A `replayOnly: true` request to run-access checks the authenticated runner's saved written answer using just its run ID. On success the client can retry the original candidates request with `X-RunState-Replay-Only: 1`. This request cannot call the writer if the saved response expires in between. Older replies written before the run-ID index remain recoverable by exact original evidence on the original retry path. Saved answers and recordings have their own replay windows.

The private file `reflection-allowances.sqlite3` belongs alongside the replay databases. All replicas serving one allowance must share the same consistent store. The implemented setup supports a single service instance with a persistent disk.

The HTTP backend reserves the maximum cost of each outbound Anthropic, ElevenLabs or
Musixmatch request before sending it. Each reservation must fit both the authenticated
runner's remaining allowance and the app's remaining allowance. A song lookup, study and
final reflection can each use part of the budget. A timed-out send keeps its reservation
because the provider may already have processed it. Reading saved answers or audio uses
no allowance. Separately launched manual trial programs are outside these HTTP limits.

| Server setting | Amount in USD |
| --- | --- |
| `RUNSTATE_RUNNER_MONTHLY_USD` | Monthly cap for each authenticated runner. A shared owner code has one shared allowance. |
| `RUNSTATE_GLOBAL_MONTHLY_USD` | Monthly cap for the entire backend. |
| `RUNSTATE_ANTHROPIC_MAX_CALL_USD` | Upper bound for the largest enabled model request, including input and output. |
| `RUNSTATE_ELEVENLABS_MAX_CALL_USD` | Upper bound for synthesizing the longest permitted text with either voice. |
| `RUNSTATE_MUSIXMATCH_MAX_CALL_USD` | Upper bound for a catalog request. Use zero only for a verified plan with no incremental charge. |

Set both monthly caps and the ceilings for the providers you enable. Amounts accept up
to six decimal places. A zero monthly cap stops new calls. A missing provider ceiling
blocks calls to that provider. Verify the ceilings against the actual plans and request
sizes before rollout. Conservative reservations can use up an allowance ahead of the
vendor's billed usage. Fixed subscription and hosting fees are separate.

Use the same private persistent directory (`RUNSTATE_SPEECH_REPLAY_DIR`) across restarts
on one hosted instance. The `provider-spend.sqlite3` file keeps totals by UTC calendar
month. Multiple independent disks would each have their own allowance. The container
sets `-Djdk.httpclient.redirects.retrylimit=1` at JVM startup so a dropped response
cannot automatically send the same paid request a second time. A paid server started
outside the container requires the same JVM option.

New calls return HTTP 429 when an allowance is insufficient, or 503 when a required
setting or the ledger is unavailable. A refusal before the first paid send allows a
later retry once capacity returns. If a written reflection has already used a paid send,
its replay reservation stays in place for the 24-hour window to prevent a second charge.

### Measure one complete test run

To rehearse the four-song and ten-song reflection samples from the repository root, run `.\backend\compare-sample-runs.ps1` in PowerShell. If Maven is outside your PATH, supply its executable with `-Maven`. The default command uses simulated provider responses. It exercises preparation, song choice, writing, one selected voice and retries. The sample excludes on-course announcements and music playback. A live comparison requires deliberate opt-in, privately configured provider keys and chosen spending limits.

On an isolated loopback backend, set `$env:RUNSTATE_LOCAL_USAGE_METER = '1'` before starting the usual server. Capture the console lines beginning with `RunStateUsage` for one run at a time. Include the start, all song preparations, the selected written reflection, and speech. Repeat for an ordinary run and a music-heavy run. Record whether the six fixed voice cues were already cached. The instrument is disabled in remote mode. A sample that uses simulated providers or only tracking cannot establish the cost of a paid reflection.

Price the reported input, output and cache tokens by model, then add voice usage at the account's rate and count Musixmatch requests against the plan. Include fixed fees separately. An attempted call with unknown usage needs the provider's billing record. Do not count missing usage as zero or add cache-write subtotals to their aggregate a second time. The meter emits counts and step labels only.

## Snippet availability v2 — local implementation, October 19, 2026

This source update separates **a playable ending from an observed song** from **evidence that the
writer used that song's analysis**. It is local code, not yet deployed to Render. Spotify account
setup, actual cross-track playback and end-to-end listening remain separate verification work;
software checks alone do not establish a completed music feature. The accepted study/writer
prompts, model choice, voices and audited curated timings remain unchanged.

`GET /health` and the actual candidates delivery advertise
`X-RunState-Song-Snippet-Fallbacks: 1` when supported. A capable Android client can add the top-level
`allowSongSnippetFallbacks: true` only alongside `allowSongSnippetLookup: true`, shared observed
`songCandidates`, and the existing run-scoped lookup permission. Older clients retain the v1
behavior and response vocabulary. New clients omit the new field for older servers; a cached
missing capability is rechecked on a later request after thirty seconds rather than pinned for
the process lifetime. The flag participates in existing request-ID conflict/idempotence checks.

The fallback-enabled path selects an analyzed candidate with a usable recording when available.
If none exists, it chooses a usable offered recording as `OBSERVED_FALLBACK` and generates the
ordinary reflection. This is not a context-ranked choice or an assertion of music understanding.
In particular, an analyzed candidate without recording identity no longer prevents another
observed, playable candidate from supplying an ending. The winner is fixed before writing;
playback does not silently substitute another song after the reflection is produced.

Playback timing uses these sources in order:

1. The phone's existing curated timing, when its exact observed Spotify ID is verified.
2. A matched LRCLIB `TITLE_HOOK` or `REPEATED_HOOK` window.
3. A matched `TIMED_LINE_WINDOW` when published timings exist without a qualifying repeat.
4. `RECORDING_OPENING_FALLBACK`, a short opening of that same observed recording when usable
   timing is absent. It starts at zero and lasts at most eight seconds, retaining the controller's
   two-second tail margin. At least four seconds of recording duration is required for this path.

Line timing is approximate; a line window is not labeled a chorus or guaranteed complete lyric.
The opening fallback is not described as a discovered hook or supplied to the writer as a
source-derived musical landing. LRCLIB can be used without Musixmatch analysis: source identity
and timing stand independently, while matched analysis remains an optional scoring input.
Album is optional; title/artist and duration matching remain required. The exact Spotify URI
stays on the phone and is never accepted from a server response as a playback instruction.

An opted-in response may carry `songSnippet.version: 2` with `source: "LRCLIB"`, or
`source: "RUNSTATE_RECORDING"` and `sourceId: "opening"` for an opening fallback. It still carries
the selected candidate ID, recording metadata and bounded numeric coordinates. `songCueId` is
present only when generation genuinely used the landing; an ordinary reflection's playback
receipt does not add that creative acknowledgement. Android stores the validated v2 receipt in
the existing reflection-evidence JSON, separately binds it to its local playback plan and can
prefer the exact verified curated timing. No database schema or lyric/audio storage is added.
Only a fresh validated delivery authorizes automatic playback; reopening does not replay, and
a retry without a winner clears a previously unused playback receipt.

The first shared software pass completed 199 backend tests and 695 Android tests per variant.
Additional review regressions cover mixed source availability, the end margin, contradictory
curated/remote cue claims and capability discovery after deployment; final receipts belong in
[project state](../docs/design/project_current_state.md). No live-provider or listening success is
claimed by this local implementation note.

## Early song preparation — October 18 local implementation

Manley approved sending one opted-in, actually observed song identity while a run is active so
the source lookup and song-only study can finish before Save. The app still writes the reflection
only from durably completed run facts. This is a new capability; the previously deployed service
does not gain it until the user pushes/promotes and deploys this backend. Device/network warm-path
timing has not yet been demonstrated for this change.

`GET /health` advertises `X-RunState-Song-Preparation: 1` when its provider supports the feature.
Authenticated `POST /api/reflections/song-study` accepts exactly:

```json
{
  "preparationId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "trackTitle": "Controlled track",
  "artistName": "Controlled artist"
}
```

It returns only `{preparationId, state: "READY" | "UNAVAILABLE", songStudyVersion: 1}`. Run facts,
Energy, weather, route, audio, lyrics, study notes and provider credentials are not accepted or
returned by this endpoint. Existing Host, Origin, bearer authentication, JSON/encoding, duplicate-key
and body-size checks apply. The title and artist are bounded to 200 characters each; live/remix
suffixes and collaboration credits are preserved. A repeated ID and matching full identity joins
or reuses the same attempt; a conflicting identity returns HTTP 409. Busy/capacity requests return
503 without starting work. There is one early-work slot within the two shared provider slots,
leaving capacity for a finished run or its voice; a one-slot test configuration denies early work.

The provider keeps at most 32 bounded song-material/validated-study results in process memory,
reusable for up to two hours from admission. Expired entries are lazily removed on subsequent
preparation, final-generation or health checks. They contain no run facts, generated run replies,
audio or credentials, and are never written to files, logs or a database. Server restarts lose
them. Its separate bounded HTTP preparation cache contains identity/status only and does not use
the existing 256 completed-request cache. This explicitly extends the older request-only source
lifetime for the approved early-preparation path; cold generation remains request-scoped.

The ordinary candidates request may include an optional **top-level** `songPreparationId` UUID.
It is outside `evidence`, participates in request-id conflict checks, and never enters either model
prompt. Matching ready/in-flight preparation supplies the same validated material and notes to the
unchanged writer with fresh final run facts. An unknown, expired, evicted or restarted handle uses
the same pinned song through the existing cold lookup → study → writer path. A failed early study
also permits this explicit final cold attempt; it does not trigger a background retry and may incur
another source/study charge. An intentional no-source result stays an ordinary text fallback with
no creative clip acknowledgement. Legacy clients have no clip; opted-in v2 clients may receive
the independent observed-song ending described above. Conflicting identities and invalid final output fail honestly. All stages
retain the shared 90-second generation budget and the existing exact cue/delivery acknowledgement.

The accepted study/writer prompt bytes, Opus model choice, four Energy branches, custom voice,
music entrance and 650 ms handoff remain unchanged. Offline tests compare warm and cold model
request bodies byte for byte and pin both accepted prompt hashes; that proves input equivalence,
not identical future model wording or a measured reduction in phone wait time.

**Latest hosted activation (October 17):** Manley committed/pushed and fast-forwarded `master` to
`2f3e77c`, then manually deployed it. Render deployment `dep-dba4qsm1egvs739c7j80` succeeded in52.2s;
public health confirms song-study, cue and configured custom-voice support. After an earlier
duration-metadata interruption and a narrow Android containment repair, the final Moto check
passed49.309s: actual normal HostedClient request from frozen fictional evidence, paired cue/version1,
selected Energy/exact-text persistence in isolated Room, custom cloud speech,656ms pause and the
Good as Hell catalog passage with verified stop/exact paused-position restoration. Recreating the
coordinator kept the exact text without another HTTP request/automatic clip. All32 actual saved
records/Room13/inspected fields were preserved. Manley accepts this new reflection, prefers the
original wording and flags the facts-to-reflection wait. Selected-only Android speech preparation
is installed/software-tested, but a subsequent timed attempt failed on a new+1ms Spotify duration
event after speech; cleanup paused correctly. The later bounded rounding/timing update passed
596 JVM tests per variant/three APKs and a separate music-only stop/restore probe, with no AI/voice
call or new reflection timing in that probe. No shorter wait or fresh-reflection retest is claimed.
Prompts/models/music gap stay unchanged;
this checks normal code paths with an isolated fixture, not a real run/full normal-UI journey.
The original duration change was not reproduced. See project state/research for the failure and
containment evidence; public health alone does not prove the audio sequence.

**Earlier local implementation (October 17):** Manley accepted three automatic listening examples
and authorized connecting their song-study/writer process to ordinary app requests. The normal
music path now reuses the exact accepted prompts with `claude-opus-5-5` by default. Both source
lookup and an actual paired result are required to authorize a known song passage. This earlier
local implementation checkpoint preceded the deployment and device checks recorded above.
The exact reference is [the accepted baseline](../docs/design/music_reflection_accepted_baseline.md);
[the development story](../docs/design/music_reflection_development_story.md) explains the route here.

**Historical hosted checkpoint (October 17):** `cb23da6` was verified Live on the existing Render service with
Musixmatch enrichment and the separate ElevenLabs reflection voice. A real hosted Moto check and
listener confirmation passed: generic facts → custom reflection voice → instrumental. All 32
saved runs/fields stayed unchanged. Earlier hosted Musixmatch checks used actual Lovely Day/Bill
Withers labels and returned four real candidates; the public response deliberately exposes no
analysis receipt. At that checkpoint, local prompt v1.8 and its music composition pipeline were **unfinished**:
Manley still rejects the writing and actual outputs contain unsupported run claims. A local
checkpoint commit preserves this work; it is not deployment approval. Work is paused at his
request. The later accepted study/writer integration above replaces that rejected music path;
do not restore v1.8 as a fallback. Render's current Auto Deploy setting has not been checked during
this integration. Pushing a service's tracked branch may start a build when Auto Deploy is enabled;
user controls that step. Current work is on `codex/oct17-runstate-checkpoint`; pushing that branch
does not update a service tracking another branch. Review/promotion precedes deployment.

## Contract included

**October 17 accepted engine:** `SongReflectionTrial` now supplies the shared song-only study and
four-branch writer used by the normal music provider. The two `song-*-trial-v1.txt` resources keep
their accepted bytes and names; the reproduction harness still supports explicit audition modes.
`MusixmatchSongAnalysis.lookupReflectionMaterial()` obtains bounded material for the normal path;
the explicitly approved early-preparation exception above can reuse it in bounded process memory.
No raw provider material or study notes are durably persisted. Offline tests use fictional
sources. The separate test-source reproduction harness requires `RUNSTATE_LIVE_SONG_TRIAL=1`,
private keys and explicit intent; it performs paid calls and never runs with `mvn test`.
The retired v1.8 baseline-comparison flag refuses before any calls. See the accepted baseline and
research notebook for exact examples, source limits and listener results.

- `RequestIdentifiers` carries a canonical request UUID and the canonical Android run UUID.
- `CompletedRunEvidence` carries completed epoch-millisecond timestamps, the IANA timezone recorded
  at the run's start, optional active duration, raw metric evidence, optional pre-run Energy, and
  optional music context with explicit provenance, and optional timestamped modeled weather.
- `RunMetricsEvidence` uses Android's existing meanings: distance is stored in meters, the display
  unit remains separate, and metric source and telemetry coverage describe provenance. Unknown
  metrics stay unknown; the contract does not add formatted distance or pace strings.
- Missing pre-run Energy defaults to `UNKNOWN`, never `MODERATE`. Missing music context defaults to
  `UNKNOWN`, which stays distinct from an explicit `NO_MUSIC` value.
- Music fields can carry controlled track, artist, and artist-description evidence when music is
  explicitly present. Omitted provenance on a `MUSIC` request defaults to `CONTROLLED` for existing
  clients. `MEDIA_SESSION_PARTIAL` requires a nonblank title and artist and prohibits an artist
  description. `UNKNOWN` and `NO_MUSIC` prohibit music metadata and provenance.
- The debug Android client sends `MEDIA_SESSION_PARTIAL` only when local observation and the
  separate, off-by-default music-sharing choice were both approved for that run. It freezes one
  eligible Spotify title and artist; this is a partial media-session report, not proof that a full
  song was heard. Other runs send `UNKNOWN`. No audio, lyrics, notification contents, route points,
  or listening-history list are added to the reflection request.
- `CandidateReflections` requires nonblank text for the exact keys `spent`, `feeling_good`,
  `powered_up`, and `no_selection`. Accepted text is retained exactly rather than trimmed or
  rewritten.

### Optional native hourly weather

`evidence.weatherContext` is optional. Omitted or JSON `null` means unknown; all older request
shapes remain valid. A supplied object requires exactly these eleven fields:

```json
{
  "source": "OPEN_METEO",
  "resolution": "HOURLY",
  "targetEpochMillis": 1780000000000,
  "validFromEpochMillis": 1779998400000,
  "validToEpochMillis": 1780002000000,
  "fetchedAtEpochMillis": 1780001800000,
  "temperatureCelsius": 21.5,
  "apparentTemperatureCelsius": 20.0,
  "relativeHumidityPercent": 48.5,
  "windSpeedKmh": 8.0,
  "weatherCode": 0
}
```

The target must equal the run's official start. The valid interval is the containing UTC hour,
including its start and excluding its end; fetch time cannot precede the target. All values must
be finite and bounded; the condition must be a supported WMO code. Missing components, extra
fields, coordinates, partial objects, unsupported sources and daily resolution are rejected.
The snapshot estimates weather near the start; it does not prove whole-run conditions or how
the runner felt. Coordinates are not accepted by this contract, and the backend does no weather
lookup. Android's separately approved lookup supplies the frozen facts.

The provider receives this provenance and interval, plus backend-converted Fahrenheit values
and local valid times. Prompt v1.4 may recognize useful pleasant or adverse weather while
prohibiting weather-caused Energy/performance claims. Missing weather is omitted from model
facts. No existing saved reflection is rewritten by this backend change.

Deploy the compatible backend before an Android build sends a non-null `weatherContext` to the
hosted service. The previous strict server rejects this new field; Android must omit null
weather fields for old-server compatibility. Local source/tests do not update Render.

October 16 local verification: bundled Maven `clean test` passed all 52 backend tests, with
zero failures, errors or skips. Added checks cover old/missing/null weather, strict required
fields, numeric/time/source validation, coordinate rejection, fake-provider HTTP delivery,
Fahrenheit conversion and prompt transmission. Providers were simulated; this does not prove
live weather-reflection writing quality or deploy the new contract/prompt.

## Anthropic adapter

`AnthropicReflectionProvider` returns one four-branch family from a completed evidence snapshot.
Without usable song analysis it uses the ordinary single-call prompt
`src/main/resources/prompts/mobile-reflection-v1.txt`. With matched song material it uses the
accepted two-call song study and writer described below; the four texts still share one response.

Backend environment configuration:

- `ANTHROPIC_API_KEY` is required. It is sent only in the provider request header and is never stored
  in source or included in adapter exception messages.
- `ANTHROPIC_MODEL` is optional. It defaults to the pinned Haiku 4.5 model
  `claude-haiku-4-5-20251001` and can be changed without editing code.
- `ANTHROPIC_SONG_WRITER_MODEL` is optional and defaults to `claude-opus-5-5` for both song-study
  and writing calls. It is independent of the ordinary model and the older editor override.
- `ANTHROPIC_MUSIC_EDITOR_MODEL` remains accepted for the explicit reproduction harness. It no
  longer selects the normal app music writer. Do not change `ANTHROPIC_MODEL` merely to activate music.

### Optional reflection voice (ElevenLabs)

The selected reflection can use Manley's existing designed **`1st test voice`**, fixed voice ID
`DocoybWhaSQMUIs7gLNe` and model `eleven_multilingual_v2`. This does not change the Android
saved-run-facts voice or generate new reflection wording. Configure `ELEVENLABS_API_KEY` only on
the backend. Hosted reflection speech also requires `RUNSTATE_SPEECH_REPLAY_DIR`, an absolute
path to a private directory on persistent storage. For example, for a single Render instance
with a persistent disk mounted at `/var/data`, use `/var/data/runstate/speech`. The container's
user (UID 10001) needs write access. Confirm the mount survives redeploys before enabling hosted
speech. See [Render persistent disks](https://render.com/docs/disks). Local loopback mode defaults
to `~/.runstate/speech-replay`. Written reflections and speech open separate databases in this
directory. If the directory is unavailable, both paid endpoints decline new work. The phone
can still display saved reflections and use its device voice.
`--fake` never enables the real speech provider. The Android app uses the same saved hosted
connection/access code and requires no ElevenLabs credential of its own.

`GET /health` adds `X-RunState-Reflection-Voice: 1` only when configured. The new guarded endpoint
is `POST /api/reflections/speech`, accepting exactly `{"text":"Exact selected reflection"}`.
Text must be nonblank valid Unicode and at most 4,000 characters; it is not trimmed or rewritten.
Existing Host/Origin, remote bearer, method, query, media/encoding and 64 KiB request limits apply
before synthesis. Successful responses are `audio/mpeg`, nonempty and at most 2 MiB, with
`Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. Failures use the existing sanitized
JSON error style. New voice generation shares the existing two-provider-call admission limit.

The backend records a reservation in SQLite before contacting ElevenLabs, then saves the MP3
before returning it. For seven days from successful synthesis, completion retries and History
replays with the same authenticated identity, exact text and voice can reuse those bytes.
Replaying does not extend the window. After expiry a new synthesis can use credits. A shared
owner access code represents one identity. Individual grants keep runners' recordings separate.

If the server stops after the provider may have charged but before it saves the audio, retries
receive HTTP 503 for that reservation's seven-day window. A duplicate request arriving while
the first call is in progress can retry after it finishes. The store holds 128 entries total,
including unfinished reservations. When full, existing audio stays available and new speech
requests receive HTTP 503 until space opens. Expired entries are cleared on startup or later
requests. These limits apply only to selected reflection speech.

`ElevenLabsReflectionSpeech` uses the fixed official HTTPS conversion endpoint, an API-key header,
MP3 44.1 kHz/128 kbps, a three-second connect timeout and a twenty-second complete-response budget.
It follows no redirects and performs no automatic retries. Only `text` and `model_id` are sent in
the JSON body. Voice settings are not overridden, preserving the voice's stored settings.
Provider exceptions, bodies, keys and selected text are not logged. Provider-side retention still
follows the ElevenLabs account/service settings. The documented zero-retention option is enterprise-only, so this
implementation does not claim or request it. See the official
[speech endpoint documentation](https://elevenlabs.io/docs/api-reference/text-to-speech/convert).

Android synthesizes only the final selected text when speech is requested, not all four Energy
candidates. Facts use local Android TTS. Reflection audio is played from memory, and genuine
completion feeds the existing music handoff. Missing/older/unavailable service or an unplayable
response can fall back to local speech before cloud audio starts. Errors after audible playback,
Stop, navigation and focus loss never repeat speech or start music. A History replay uses the
saved reflection text and can play its eligible music excerpt. Configure a usage limit on the
separate speech-only RunState API key. Backend tests use fake speech transports.

At the original October voice checkpoint, the Android update was installed and Render's private key was
saved. Manley committed/pushed through `cb23da6`; that version deployed successfully in 55.2 seconds
and public health advertises voice support. A separate gated real Moto check passed in 40.732
seconds: local sample facts completed, actual cloud MP3 was accepted and naturally completed with
no local reflection fallback, then the instrumental completed. Manley confirmed all three sounded
right. All 32 saved runs/fields were unchanged and temporary test material was removed. Capability
alone does not establish synthesis; this separate real check does. It proves the selected voice
sequence, not song ranking or automatic passage selection for other recordings.

The Anthropic adapter's initial limits are a 10-second connection timeout, a 45-second request timeout, and 2,048 output
tokens. These are starting safety bounds, not measured latency or quality guarantees. Failures are
reported explicitly; the adapter performs no automatic retry and returns no successful canned fallback.

The provider adds `reflection_facts` to its internal writing input, calculated from the validated
evidence: a distance phrase in the saved display unit including sample/partial-GPS provenance,
elapsed/known active seconds, and local start/finish
datetimes in the recorded timezone. The HTTP contract and original raw fields remain unchanged.
Android leaves active duration unknown for partial or unavailable GPS coverage, matching History:
complete transition records alone cannot prove active tracking across a service interruption.
This keeps the writer from interpreting 5,000 meters as five miles or guessing local time from an
epoch timestamp. Prompt version `mobile-reflection-v1.4` retains the sample-distance, partial-GPS,
unavailable-metric and partial-media limits. Its no-selection branch recognizes known run facts
without inventing post-run feelings, commenting on an unanswered Energy choice, or asking for one.
It may omit a music reference when the evidence is weak. Structured-output validation checks shape and preserves exact text;
it does not prove every generated statement is factual or that a real GPS distance is accurate.

**October 14 deployment checkpoint:** Manley manually deployed latest GitHub `master`, reported
Render Succeeded/Live and confirmed commit `bcde0c4`, replacing `85cbfab`. That deployed commit includes v1.3;
the Dockerfile copies full `src/`, including the prompt, and runs Maven test/package. Public
`/health` returned 200 with `{"status":"ok"}`. Latest Render build logs were not inspected;
health verifies liveness, not writing compliance. A subsequent connected-Moto Sample separately
passed one bounded live v1.3 writing/cold-reopen check. With 9.384 seconds elapsed, 5.378 seconds
active, 16.134 m FIXTURE, unknown pre-Energy/null post-Energy and music observation/sharing off,
its exact READY/NO_SELECTION/non-FAKE text was:

> You completed a sample run in just over 9 seconds at 6:57 p.m. That's time on your feet.

It matches sample/time evidence and contains no invented/missing feeling or question. One fresh
run was used, without an Energy answer or assistant Retry; no billed-request count was independently
established. Cold History reopen retained exact text/request/finalization/evidence, Online service,
21 completed/0 active and all prior 20 IDs. Stored field hashes matched across restart; temporary
read-only snapshots were removed. No new credential/file/source change/test rerun/Git mutation.
This is one output pass, not broad model compliance or new hearing confirmation. Previously saved
replies remain unchanged; mocked tests still verify instruction transmission rather than live quality.

**Subsequent Spotify compatibility check:** A second fresh v1.3 Sample finalized
READY/NO_SELECTION/non-FAKE: about 0.01 mi FIXTURE, 9.400 s elapsed/5.379 s active, no Energy
answer or music observation/sharing. Its exact reply was:

> You covered 0.01 miles of sample distance in under 10 seconds. A brief run, wrapped up at 7:05 p.m.

Manley heard facts → reflection → instrumental while user-started Spotify became quieter or
paused and returned normally by itself. Phone focus logs support the handoff; Spotify was PLAYING
afterward. RunState issued no Spotify play command and sent no song metadata/audio to the model.
The audit found 22 completed/0 active and every prior 21 run/reflection/transition/location field
hash unchanged. Temporary copies were removed, stay-awake 0 restored and no GPS service remained;
no source change, test rerun, Git mutation or persistent file was required. These are two bounded no-selection output
passes with one Moto/session audio compatibility pass, not broad quality, wider compatibility,
calls/Bluetooth or distance accuracy. First-run cold-reopen proof remains separate. Active visual
review remains open after the later bounded native pass recorded in current state; actual-song
sourcing remains open.

## HTTP API

`POST /api/reflections/candidates` accepts one request identifier, one saved-run identifier, and one
completed-run evidence snapshot. It returns the identifiers, a `generationMode` of `FAKE` or
`ANTHROPIC`, and the exact four texts produced by the configured `ReflectionProvider`. The launcher
sets the mode from its provider configuration; it is never guessed from prefixes or other text.

The endpoint accepts only `application/json` request bodies up to 64 KiB. JSON must be UTF-8, use
unique keys, stay within the documented shape, and satisfy the existing contract constructors.
Unknown optional evidence remains unknown; it is never replaced with a sample value.

### Local request guard

Before reading the body or calling the provider, the server requires exactly one `Host` header. Its
port must equal the port the server is actually listening on, and its host name must be one of
`127.0.0.1`, `localhost`, or the Android emulator's host alias `10.0.2.2`. Missing, duplicate,
malformed, differently named, or wrong-port Host values are rejected.

Native Android requests normally do not carry the browser `Origin` header. `Origin` may therefore
be absent, but any supplied value is rejected, including `null` or an empty value. Rejection happens
before JSON parsing and returns the stable `request_rejected` error without calling the provider.

This is a narrow local-development guard, not authentication. An arbitrary program running locally
can set these headers itself. The rule must not be treated as sufficient protection for a public,
proxied, or multi-user server; those deployments require a separate authenticated design.

Example request shape using controlled values:

```json
{
  "requestId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "runId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
  "evidence": {
    "officialStartEpochMillis": 1780000000000,
    "finishEpochMillis": 1780001800000,
    "startTimezoneId": "America/Chicago",
    "activeDurationMillis": 1650000,
    "metrics": {
      "distanceMeters": 5000.25,
      "source": "FIXTURE",
      "coverage": "COMPLETE",
      "displayUnit": "MILES"
    },
    "preRunEnergy": "LOW",
    "musicContext": {
      "state": "MUSIC",
      "trackTitle": "Controlled track",
      "artistName": "Controlled artist",
      "artistDescription": "Controlled artist description",
      "provenance": "CONTROLLED"
    }
  }
}
```

### Optional curated song cue

An optional `musicContext.songCue` adds independently curated song context for the same identified
track. It is omitted or null for older clients and contains exactly:

```json
{
  "cueId": "run_it_up_v1",
  "source": "RUNSTATE_CURATED",
  "theme": "Self-directed momentum",
  "connectionNote": "Recognize completing this run without inventing struggle."
}
```

`cueId` is a lowercase catalog identifier (`[a-z0-9][a-z0-9_-]{0,79}`). The theme and connection
note are nonblank, at most 120 and 400 characters respectively, and require MUSIC with a title and
artist. They are original editorial notes, never lyrics, recording URLs, passage offsets or
playback instructions. MEDIA_SESSION_PARTIAL remains partial even when a cue is present. Prompt
v1.5 can use those notes to land the ending around the song's theme without promising playback,
inventing musical effects on the runner or overriding the selected Energy. Playback ownership,
passage timing and matching the local plan to this frozen cue remain Android responsibilities;
the response still contains exactly the same four candidate strings. Existing clients remain
accepted. A client must omit this field when talking to an older strict server.

`GET /health` advertises support through `X-RunState-Song-Cue: 1`, preserving the existing health
JSON body. The current server also advertises `X-RunState-Song-Study: 1`. A successful matched
study/writer result may acknowledge a recognized cue through `songCueId` and the same study
version on the actual POST response. The server never blindly echoes an arbitrary requested cue.
Android requires the corresponding acknowledgment and delivery version before a new paired clip;
health alone cannot authorize playback across a server rollback. Missing analysis can still return
ordinary text without acknowledgment; the phone keeps it readable and plays no analysis-paired passage.
The opted-in v2 playback receipt described above can separately authorize an observed-song ending.
Unknown/mismatched cues are not acknowledged. Old request/response shapes remain supported.

### Automatic song study and reflection writing (Musixmatch + Claude)

Set `MUSIXMATCH_API_KEY` in the backend environment. Keys saved in local Windows User settings
do not configure Render; existing hosting keys remain private and unchanged by this source update.

For MUSIC with a title and artist, the adapter requests bounded song material even when a curated
landing is supplied. Four of the five prepared passage songs use verified original Musixmatch IDs;
each ID is fetched again and its exact returned title, artist and numeric ID must match. Sunday
Best and other songs use
`matcher.track.get` and exact identity verification. Case/spacing may normalize; remix/live/edit
suffixes and collaborator names are never removed. Source gaps do not authorize another recording.

The material contains bounded theme labels/supporting passages, moods, interpretation of meaning,
genres, whitelisted entities and optional `track.snippet.get` lyric text. A snippet here is text,
not the Spotify recording. Each source HTTPS call has a four-second bound, 128 KiB maximum,
no redirects, automatic retries or durable cache. Only title/artist and matched catalog ID go
to Musixmatch, without run facts, identifiers, Energy, coordinates or listening-history lists.

The normal music provider reuses the accepted `SongReflectionTrial` engine:

1. Song-only study receives the matched material, never run facts. It creates bounded notes on
   attitude, delivery, title idea, imagery, optional interpretive artist context and verified
   short source echoes.
2. The writer receives those validated notes, original bounded meaning/themes/moods, completed
   run facts with partial-media/hourly-weather provenance, allowed anchors and an exactly matched
   editorial musical landing when available. It returns four conditional Energy branches.
   Only the final reflection strings are returned to Android; study/source material stays in memory.

Both calls default to `claude-opus-5-5` through `ANTHROPIC_SONG_WRITER_MODEL`, independent of
`ANTHROPIC_MODEL` and legacy `ANTHROPIC_MUSIC_EDITOR_MODEL`. The accepted prompt files remain
unchanged and SHA-256 tests protect them. The normal app uses AUTO creative direction. Explicit
DIRECT/PERSONA_ONLY/BLEND modes remain in the reproduction harness, not as user-facing selections.
Production landing notes omit the fictional test's morning/evening assertions and meet the actual run.

The complete song path shares 90 seconds including lookup; each Claude call is capped at the
configured 45 seconds. Android candidate reads allow 100 seconds, and the Docker response bound
is 95 seconds. No automatic generation retry, three-stage v1.8 fallback, or handwritten replacement
is used. Missing/mismatched/unavailable analysis returns ordinary single-call text with no paired
cue acknowledgment; the v2 path can still provide an independently classified observed-song ending.
Invalid study/writer/schema/source-copy output or interruption fails honestly
through existing app retry; the app retains the saved run and does not authorize its passage.

A recognized cue requires exact catalog ID, song title/artist and editorial definition. The normal
provider acknowledges it only after actual matched study/writer success; the actual response carries
`X-RunState-Song-Study: 1`. Provider-result validation, duplicate-request caching and Android checks
bind that acknowledgment to the frozen request. A future/older writer cannot simply echo a new cue
and cause a clip. If no cue is supplied or recognized, matched music can still shape the text.

The five curated passage choices are Sunday Best/Surfaces (15.5–25.5 s), Lovely Day/Bill Withers
(58.03–63.90 s), Stronger/Kanye West (0–10 s), Good as Hell/Lizzo (48.91–53.55 s), and God's Plan/Drake
(37.30–44.60 s). These are checked entrances, not an automatic hook detector. The phone requires an
eligible same-run PLAYING report, both music choices, the matching current Spotify session and
available controls. No arbitrary-song selection, queue editing, recording extraction or full-run
song ranking was added. History Read stays voice-only. Saved text is not regenerated by this update.

`mvn test` uses simulated providers and makes no paid calls. The explicitly gated test-source
`SongReflectionTrialTest` main harness remains available to reproduce the accepted creative
directions with deliberately supplied fictional scenarios/private runtime keys; do not enable it
in CI or repeat it as a substitute for normal-app verification. The retired v1.8 baseline-comparison
flag refuses before calls. There is no need to regenerate the three accepted references.

See [the accepted baseline](../docs/design/music_reflection_accepted_baseline.md) for the exact
texts, facts, original identities, passage descriptions, audio settings and prompt fingerprints.
The [research notebook](../docs/design/research_music_reflection_generation.md) preserves earlier
failed attempts and actual listening/device receipts. Three accepted pairings establish useful
creative direction, not perfect factual review, all-song coverage or broad tester validation.

Successful response shape (without a paired cue; a confirmed pair adds `songCueId`):

```json
{
  "requestId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "runId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
  "generationMode": "ANTHROPIC",
  "candidates": {
    "spent": "Exact provider text",
    "feeling_good": "Exact provider text",
    "powered_up": "Exact provider text",
    "no_selection": "Exact provider text"
  }
}
```

Errors use a stable envelope such as
`{"error":{"code":"invalid_request","message":"..."}}`. Current codes are:

- `invalid_json`, `invalid_request`, `request_too_large`, and `unsupported_media_type` for request
  problems;
- `request_rejected` when Host or Origin fails the selected mode's guard;
- `unauthorized` for a missing, invalid or repeated remote Authorization header;
- `method_not_allowed` and `not_found` for unsupported HTTP routes;
- `request_id_conflict` when one request ID is reused with different run evidence;
- `reflection_pending` when a written request has already been reserved and has no saved answer yet;
- `reflection_storage_unavailable` when paid writing cannot safely open or use its replay store;
- `server_busy` when the provider slots are occupied, and `server_capacity` when the
  request store reaches its limit;
- `provider_timeout`, `provider_unavailable`, and `provider_failure` for provider outcomes; and
- `internal_error` for an unexpected server failure.

Error responses never include request bodies, provider bodies, prompts, credentials, or underlying
exception messages.

### Retrying a written reflection

When the phone loses a reply, tapping **Retry** sends its saved run evidence with a fresh
`requestId`. The backend recognizes the authenticated runner, run ID and validated inputs,
including music-sharing choices and the order of the offered songs. It saves the four generated
answers and the accompanying song details before sending the reply. For 24 hours after that
save, the phone can receive the same answers with its current request ID, even across a server
restart. Reusing an ID for different evidence returns HTTP 409. Retrying never extends the
window. Once the phone has saved its reflection, History can keep displaying it after expiry.

Before the first provider call, the backend records the request in SQLite. If it stops after
that point without saving a usable answer, it withholds another paid generation for the
reservation's 24-hour window. A request rejected before admission can be retried when the
service is available. The response and request limits remain 64 KiB each. The written store
holds up to 256 operations and remembers up to 64 accepted request IDs per operation.
It clears expired entries on startup or subsequent requests.
This protection covers the generation of the written answers. Song preparation and cumulative
provider spending have separate lifecycles.

For hosted generation, point `RUNSTATE_SPEECH_REPLAY_DIR` at the private persistent disk
described in the reflection voice setup above. Written results have their own database there,
`written-reflections.sqlite3`, and do not require a speech API key. Without usable storage,
the paid writing endpoint returns HTTP 503 before it contacts the writer. The local `--fake`
mode keeps its in-memory request cache.

## Start the local server

The default mode constructs `AnthropicReflectionProvider` and marks successes `ANTHROPIC`.
`--fake` selects the fixed local provider and marks successes `FAKE`. Keep `ANTHROPIC_API_KEY` only in the
backend process environment; never put it in Android, JSON, source, or command-line arguments.
`ANTHROPIC_MODEL` remains optional. `RUNSTATE_REFLECTION_PORT` may override the default port `8787`.

For a paid local run, configure the monthly limits above. In PowerShell, append the
one-attempt flag to the Maven JVM options for this session and start from `backend/`:

```powershell
$env:MAVEN_OPTS = "$env:MAVEN_OPTS -Djdk.httpclient.redirects.retrylimit=1"
mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  -Dexec.mainClass=com.runstate.reflection.ReflectionBackendMain
```

For IntelliJ, add `-Djdk.httpclient.redirects.retrylimit=1` to the VM options for
`ReflectionBackendMain` and supply the same backend-only environment variables.

For a safe local smoke check that makes no Anthropic call, start the explicit fake provider:

```powershell
mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  -Dexec.mainClass=com.runstate.reflection.ReflectionBackendMain `
  -Dexec.args=--fake
```

Then send this controlled request from a second terminal:

```powershell
$body = @'
{
  "requestId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "runId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
  "evidence":{
    "officialStartEpochMillis":1780000000000,
    "finishEpochMillis":1780001800000,
    "startTimezoneId":"America/Chicago",
    "activeDurationMillis":1650000,
    "metrics":{
      "distanceMeters":5000.25,
      "source":"FIXTURE",
      "coverage":"COMPLETE",
      "displayUnit":"MILES"
    },
    "preRunEnergy":"LOW",
    "musicContext":{"state":"UNKNOWN"}
  }
}
'@

Invoke-RestMethod `
  -Uri 'http://127.0.0.1:8787/api/reflections/candidates' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body
```

The response must contain `"generationMode": "FAKE"` and four visibly `[FAKE]` texts. Clients should
use `generationMode` for labeling, not inspect or rewrite the candidate text. Stop the server with
`Ctrl+C`.

## Prepare a hosted demonstration

Nothing here deploys a service, opens a public port or creates a hosting account. Use the remote
mode only behind a hosting ingress/reverse proxy that terminates **HTTPS**, forwards the expected
public `Host` and `Authorization`, and keeps the application's internal HTTP listener inaccessible
from the public network. Do not publish `8787` directly or port-forward this server through a home
router. For a proxy on the same computer, bind the app to `127.0.0.1`; a managed container ingress
normally needs `0.0.0.0` inside its private container network.

`--remote` enables this boundary; `--remote --fake` uses the same authentication with fixed
`FAKE` responses and no Anthropic traffic. No arguments and `--fake` retain the local loopback
mode even when remote environment variables are present. Configure secrets in the hosting
platform's secret/environment settings, without files, command-line values, Docker build arguments
or Git changes:

| Variable | Meaning |
| --- | --- |
| `RUNSTATE_DEMO_TOKEN` | Required opaque per-demo client access code. Generate at least 32 cryptographically random bytes and encode as URL-safe Base64 without padding (43 characters), or as 64 hex characters. Accepted format is 43–128 ASCII letters, digits, `_` or `-`; length/format validation cannot prove randomness. |
| `RUNSTATE_REFLECTION_PUBLIC_HOST` | Required exact public HTTPS authority, such as `demo.example.com`, optionally `:443` or another HTTPS port. Do not include scheme, path, credentials or query. The proxy must preserve this Host. A default `:443` is equivalent to an omitted port. |
| `RUNSTATE_REFLECTION_BIND_ADDRESS` | Optional numeric IPv4 bind address. Defaults to `0.0.0.0` **only in explicit remote mode**; set `127.0.0.1` for a proxy on the same host. Hostnames and IPv6 are not accepted in this bounded deployment. |
| `RUNSTATE_REFLECTION_PORT` | Optional internal port, 1–65535. It takes priority over the hosting platform's `PORT`. Remote mode uses `PORT` when provided; otherwise it uses `8787`. Local mode ignores `PORT`. |
| `ANTHROPIC_API_KEY` | Backend-only secret for live provider mode. Not needed for `--remote --fake`. |
| `ANTHROPIC_MODEL` | Optional model override, unchanged from local live mode. |
| `ANTHROPIC_SONG_WRITER_MODEL` | Optional normal music study/writer model; defaults to accepted `claude-opus-5-5`. Existing keys suffice. |
| `ANTHROPIC_MUSIC_EDITOR_MODEL` | Reproduction-harness setting only; does not override the normal song writer. |
| `MUSIXMATCH_API_KEY` | Optional backend-only song-analysis key; missing/unavailable source yields ordinary text without passage authorization. |
| `ELEVENLABS_API_KEY` | Optional backend-only key for the selected reflection voice. |

The Android endpoint is `https://<configured-public-host>/api/reflections/candidates`, with
`Authorization: Bearer <demo-access-code>`. The code must match the server's `RUNSTATE_DEMO_TOKEN`.
The request/response JSON stays identical to local mode. A single header is required, with the
exact `Bearer ` prefix. Token comparison uses constant-time byte comparison; errors never echo
tokens, request evidence or underlying provider exceptions. Any supplied browser Origin remains
rejected, so this API has no browser/CORS client path. Untrusted `X-Forwarded-*` headers establish
neither HTTPS, an allowed Host nor authentication.

Remote-only `GET /health` returns `{"status":"ok"}` without authentication or provider work,
but still requires the configured public Host and no Origin. It proves process liveness only;
it does not prove that the demo code, Anthropic key, billing, generated text or phone connection
works. Configure a TCP liveness probe if a host cannot preserve the expected Host for its HTTP
probe. Validate authentication and exact `FAKE` delivery through the candidate endpoint before
enabling the live provider.

Render's HTTP probes use the service's `onrender.com` Host unless a verified custom domain exists,
in which case Render chooses one custom domain. Therefore `/health` is compatible when
`RUNSTATE_REFLECTION_PUBLIC_HOST` is that exact authority. Keep the first demo on its supplied
`onrender.com` address without custom domains; after deployment, verify its actual HTTP probe.
Render also supports default TCP probes. This is documentation verification, not an executed
Render health check. [Render health-check documentation](https://render.com/docs/health-checks).

The ingress must also bound request headers/body size, slow body uploads and connection count,
and rate-limit traffic. Allow at most 64 KiB JSON and use a finite body/header timeout (for example
15 seconds); allow the upstream response enough time for the music pipeline's 90-second budget. Disable
request/response-body logging and Authorization-header logging in the proxy/platform. The JDK HTTP
server has a bounded worker queue, so extreme ingress overload may close a connection; provider
admission overload normally returns the explicit 503 errors above. Forwarded headers are never a
replacement for private network isolation and edge TLS.

The demo code protects one private demonstration; it is not general user authentication and should
not be distributed in a public APK or public website. Anyone who has it can use the same bounded
provider allowance. Rotate it in the hosting secrets and re-enter it on authorized phones if it is
exposed. Keep one service instance and avoid automatic scaling until durable idempotency exists.

### Container build inputs

The necessary deployment artifacts are `backend/Dockerfile` and `backend/.dockerignore`.
The Dockerfile builds/tests Java 17 sources, copies only runtime classes/dependencies, runs as a
non-root numeric user with a bounded Java heap, and starts explicit `--remote` mode through a
complete exec-form `CMD`. Its explicit empty `ENTRYPOINT` removes inherited wrappers and avoids
Render's conflicting command-override descriptions. No token or Anthropic
key is in the image. The `.dockerignore` allows only the POM, source tree and Dockerfile into the
context, excluding local `.m2`, `target`, credential files and documentation attachments.

The container also sets these OpenJDK 17 HTTP implementation properties **at JVM startup**:

| Startup property | Demo setting |
| --- | --- |
| `jdk.httpclient.redirects.retrylimit` | 1 total outbound HTTP attempt per send on OpenJDK 17 |
| `sun.net.httpserver.maxReqTime` | 15 seconds to finish reading a request, including headers/body |
| `sun.net.httpserver.maxRspTime` | 95 seconds for response generation and writing |
| `sun.net.httpserver.timerMillis` | 1000 ms request/response deadline polling |
| `sun.net.httpserver.maxReqHeaders` | 32 distinct header names |
| `sun.net.httpserver.maxReqHeaderSize` | 16384 bytes for request line/header-list accounting |
| `jdk.httpserver.maxConnections` | 40 open connections |
| `sun.net.httpserver.maxIdleConnections` | 8 retained idle connections |
| `sun.net.httpserver.idleInterval` | 15 seconds idle |
| `sun.net.httpserver.clockTick` | 1000 ms idle polling |

OpenJDK 17u's default request/response deadlines are disabled, so bounded workers alone would not
release slow readers. These are verified implementation settings, not portable public API
guarantees. Its periodic timer closes timed-out connections; the 95-second response setting leaves
room for the 90-second shared music budget inside Android's 100-second read timeout. Individual
Claude calls remain capped at 45 seconds. Scheduling is
periodic, not an exact real-time guarantee. [OpenJDK 17u configuration](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/jdk.httpserver/share/classes/sun/net/httpserver/ServerConfig.java),
[deadline enforcement](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/jdk.httpserver/share/classes/sun/net/httpserver/ServerImpl.java),
[header accounting](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/jdk.httpserver/share/classes/sun/net/httpserver/Request.java).

Use the Docker startup command for remote hosting. A bare Java/IDE remote launch must supply the same
`-D` flags before the server implementation initializes; changing them after creating a server does
not configure that existing JVM. Local launches and tests are not globally reconfigured by app
code. No in-process property assertion is claimed as a socket-timeout test: the chosen container
runtime still needs a controlled slow-upload/deadline check; the current Live deployment and
health response do not establish that separate boundary.

A controlled local Java 17 `--remote --fake` launch with these startup flags verified health `200`,
missing-token `401`, authenticated malformed-body `400`, and closure of an incomplete-header
connection after 15.1 seconds. Its temporary test token and process were cleared afterward.
This verifies that local runtime check, not the Docker image, a slow-body upload or Render ingress.

For the first Render fake deployment, set **Docker Command** to this complete command; `--fake`
alone is not an executable and must not be used as the override:

```text
java -Xmx256m -Dsun.net.httpserver.maxReqTime=15 -Dsun.net.httpserver.maxRspTime=55 -Dsun.net.httpserver.timerMillis=1000 -Dsun.net.httpserver.maxReqHeaders=32 -Dsun.net.httpserver.maxReqHeaderSize=16384 -Djdk.httpserver.maxConnections=40 -Dsun.net.httpserver.maxIdleConnections=8 -Dsun.net.httpserver.idleInterval=15 -Dsun.net.httpserver.clockTick=1000 -cp "/app/classes:/app/lib/*" com.runstate.reflection.ReflectionBackendMain --remote --fake
```

Keep `ANTHROPIC_API_KEY` absent during staging, confirm the startup identifies the fake provider,
then verify the authenticated four-candidate `FAKE` response. For a later deliberate live deploy,
configure the backend-only Anthropic key and **clear Docker Command** so the image's full default
`CMD` starts live `--remote` mode with the same limits. The form tooltip mentions overriding both
`CMD` and `ENTRYPOINT`, while published guidance describes overriding `CMD`; with an explicitly
empty entrypoint and a complete command, neither interpretation appends a second Java executable
or leaves `--fake` as the process name. [Render Docker command guidance](https://render.com/docs/docker#building-from-a-dockerfile),
[Docker CMD/ENTRYPOINT semantics](https://docs.docker.com/reference/dockerfile/#understand-how-cmd-and-entrypoint-interact).

Use `backend/` as the container build context and supply the environment through the hosting
platform at runtime. `EXPOSE 8787` is image metadata, not a request to publish the port. The Docker
image and actual host ingress must be built and verified separately before deployment; source
tests alone do not verify image downloads, platform networking, TLS or Android HTTPS delivery.

## Run the tests

From this directory, run:

```powershell
mvn test
```

If Maven is not on `PATH`, use IntelliJ IDEA's Maven tool window or its bundled Maven executable.
The project can be opened independently by selecting this directory or its `pom.xml` in IntelliJ.

Endpoint tests start the server on an operating-system-assigned loopback port and inject fake
providers. They cover configured generation modes, exact success text, invalid and oversized requests,
duplicate IDs, unsupported methods and safe provider failures. Remote-mode tests also bind only to
loopback and use a dummy demo code: they cover authentication before parsing, Host/Origin/forwarded
header boundaries, liveness, provider admission and cache capacity without contacting Anthropic.

The first Render image build ran 43 tests and failed on a socket reset in the oversized-request
test, whose raw client incorrectly waited for TCP EOF after an HTTP response. The test client now
reads bounded headers and exactly the declared `Content-Length`, with no read beyond a complete
response. Deterministic stream checks verify that a trailing reset cannot invalidate a complete
response, while premature EOF/reset, oversized framing and conflicting framing still fail.
The actual HTTP status/JSON and zero-provider-call assertions remain; no server guard, provider
logic or Docker test execution is bypassed. The corrected image still requires a new Linux/Render
build to confirm that platform outcome. After freshly compiling the changed tests, the focused
22 endpoint tests and full 45-test suite passed locally on Windows with zero failures/errors/skips.

## Boundary

The contract can validate the evidence it receives, but it cannot prove that Android saved the run.
The debug Android client completes locally before requesting candidates and persists the exact
selected response. An October 13 Moto phone check delivered a newly generated Anthropic response;
late Energy changes and History after a cold restart with the backend stopped preserved its text.
The first generated-text/persistence milestone is exercised. A further opted-in Moto run verified
a saved `MUSIC` / `MEDIA_SESSION_PARTIAL` snapshot with one title and artist and a real Anthropic
response. That response did not mention the song; accepting music context does not guarantee a
music reference. History reports the saved context without claiming a complete listening record.
Local observation alone does not permit sharing, and existing records migrate with sharing off.
Hosted HTTPS can be explicitly configured in debug or release builds. The local computer/emulator
HTTP route remains debug-only; the default unconfigured release makes no reflection request.

Automated tests use dummy configuration and fake or simulated provider responses; they make no
live Anthropic request. Separate bounded live checks exercised credentials, unit/time/provenance
grounding and phone delivery, but do not establish general generation quality. The server has no
database, in-process TLS, durable idempotency or multi-user account system. Its optional remote
mode requires a separately deployed private HTTPS ingress and per-demo token. The Render service
is deployed at `bcde0c4` with public health and one live v1.3 Sample/cold-reopen check passed;
general writing quality and slow-body/ingress deadline testing remain unverified. The local Host/Origin guard still does
not authenticate arbitrary local programs. The full music-aware demonstration remains
incomplete; current verification and remaining work are recorded in
[`project_current_state.md`](../docs/design/project_current_state.md).
