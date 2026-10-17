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

## Contract included

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

`AnthropicReflectionProvider` sends one evidence snapshot to the Anthropic Messages API and requests
all four candidates in one structured JSON response. The writing instructions are versioned in
`src/main/resources/prompts/mobile-reflection-v1.txt`.

Backend environment configuration:

- `ANTHROPIC_API_KEY` is required. It is sent only in the provider request header and is never stored
  in source or included in adapter exception messages.
- `ANTHROPIC_MODEL` is optional. It defaults to the pinned Haiku 4.5 model
  `claude-haiku-4-5-20251001` and can be changed without editing code.

The initial limits are a 10-second connection timeout, a 45-second request timeout, and 2,048 output
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
JSON body. Only requests containing a cue receive an additional `songCueId` in the success envelope,
equal to that request's cue identifier. Clients check this acknowledgment before using the reply
with the corresponding local passage; old request/response shapes remain unchanged.

### Optional automatic song analysis (Musixmatch)

The backend can now look up an identified, unprepared song automatically before generating its
reflection. Set `MUSIXMATCH_API_KEY` in the backend environment to enable this path. Without that
key, reflection behavior stays unchanged. Keep this credential out of Android, Git, request bodies
and logs. For a local check, a key saved in Windows **User** environment settings must be explicitly
loaded into the terminal/service environment before starting Java; a previously running server
does not inherit later environment changes. Hosted Render configuration is separate.

For MUSIC with both title and artist and no existing curated cue, the adapter calls the documented
`matcher.track.get` endpoint, verifies the returned title/artist without removing recording-version
suffixes, then calls `track.lyrics.analysis.get` for its numeric Musixmatch track ID. Each HTTPS
request has a four-second deadline and a 128 KiB response limit, with no redirects, retries or cache.
Both HTTP status and the provider's embedded status must succeed. Authentication, access, missing
analysis, mismatched identity or malformed responses fall back to the ordinary reflection path.

Only bounded `themes.main_themes[].theme` and `moods.main_moods` labels are retained. Quotes,
free-text meaning/explanations, entities and lyric text are discarded. Only title/artist and the
matched provider ID go to Musixmatch; no run IDs, metrics, Energy, weather, route or listening-history
list is sent there. Prompt v1.6 receives a backend-added `music_context.song_analysis` object with
`source`, `track_title`, `artist_name`, `provider_track_id`, `themes`, and `moods`. These are data about
the song, never instructions or evidence of the runner's feelings/performance. The mobile input
contract and the four-candidate output schema are unchanged. An accepted curated cue takes
precedence and performs no additional provider lookup.

This implements automatic song information for the one supplied identity. It does not rank a whole
run's songs, find hook timestamps, create a local playback plan, or select another Spotify track.
An explicit live check passed for **Lovely Day / Bill Withers** using the actual account and no
prepared song notes. It verified that fetched themes/moods entered the actual Claude request and
generated four nonblank candidates for synthetic 3-mile/32-minute sample facts. This proves that
one automatic connection, not whole-catalog coverage or subjective quality. The normal suite passed
79 checks with the additional opt-in case skipped; that live case then passed separately. Manley
handles the approved $49/month Basic purchase, not the application.

To repeat this paid integration check deliberately, provide both backend environment keys and run:

```powershell
mvn '-Dtest=AnthropicReflectionProviderTest#liveUnpreparedSongAnalysisFeedsTheRealReflectionRequest' `
    '-Drunstate.liveMusicAnalysis=true' test
```

It makes real matcher/analysis calls and one real Claude generation, stopping before Claude if
analysis is unavailable. Default tests make no live calls. Its bounded report contains public song
labels and one sample reply, no keys or raw provider bodies. Hosted activation requires pushed
backend code, Render's own `MUSIXMATCH_API_KEY` and deployment; Windows User settings apply locally.

Success response shape:

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
- `server_busy` when both provider slots are occupied, and `server_capacity` when the bounded
  process-local request cache is full;
- `provider_timeout`, `provider_unavailable`, and `provider_failure` for provider outcomes; and
- `internal_error` for an unexpected server failure.

Error responses never include request bodies, provider bodies, prompts, credentials, or underlying
exception messages.

### Duplicate-request behavior

The server keeps an in-memory map keyed by `requestId`. Concurrent identical requests share one
provider call, and later identical requests receive the same cached success or failure. Reusing an
ID with different identifiers or evidence returns `request_id_conflict` without calling the provider.

The same limits apply locally and remotely: two simultaneous provider calls, 256 retained request
IDs, eight HTTP workers and a queue of 32 tasks. Request and success-response bodies are each at
most 64 KiB; oversized provider output becomes a safe cached `provider_failure`. A new request
that would exceed provider concurrency receives `503 server_busy` and is not cached, so retrying
the same ID later does not create a second generation. At 256 retained IDs, new IDs receive
`503 server_capacity`; retained successes, failures and conflicting-ID checks still work.

There is deliberately no automatic eviction. Expiring an ID and generating it again would silently
make a second potentially paid call for the same request. Deliberate restart clears the cache and
its duplicate protection, so an old ID may generate again afterward. Use one service instance for
the demo; multiple replicas do not share this map. Durable idempotency, user accounts and distributed
admission belong to a later service design. For a deliberate new attempt after a cached provider
failure, use a new canonical request UUID. Do not interpret `server_capacity` as permission to
automatically restart or repeat paid work.

## Start the local server

The default mode constructs `AnthropicReflectionProvider` and marks successes `ANTHROPIC`.
`--fake` selects the fixed local provider and marks successes `FAKE`. Keep `ANTHROPIC_API_KEY` only in the
backend process environment; never put it in Android, JSON, source, or command-line arguments.
`ANTHROPIC_MODEL` remains optional. `RUNSTATE_REFLECTION_PORT` may override the default port `8787`.

From `backend/`, start the production provider with Maven:

```powershell
mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  -Dexec.mainClass=com.runstate.reflection.ReflectionBackendMain
```

For IntelliJ, run `ReflectionBackendMain` with the same backend-only environment variables.

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
15 seconds); allow the upstream response enough time for the provider's 45-second timeout. Disable
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
| `sun.net.httpserver.maxReqTime` | 15 seconds to finish reading a request, including headers/body |
| `sun.net.httpserver.maxRspTime` | 55 seconds for response generation and writing |
| `sun.net.httpserver.timerMillis` | 1000 ms request/response deadline polling |
| `sun.net.httpserver.maxReqHeaders` | 32 distinct header names |
| `sun.net.httpserver.maxReqHeaderSize` | 16384 bytes for request line/header-list accounting |
| `jdk.httpserver.maxConnections` | 40 open connections |
| `sun.net.httpserver.maxIdleConnections` | 8 retained idle connections |
| `sun.net.httpserver.idleInterval` | 15 seconds idle |
| `sun.net.httpserver.clockTick` | 1000 ms idle polling |

OpenJDK 17u's default request/response deadlines are disabled, so bounded workers alone would not
release slow readers. These are verified implementation settings, not portable public API
guarantees. Its periodic timer closes timed-out connections; the 55-second response setting leaves
room for the 45-second provider timeout inside Android's 60-second read timeout. Scheduling is
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
