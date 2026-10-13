# RunState local reflection backend

This directory is a standalone Java 17/Maven project containing the reflection contract, Anthropic
provider adapter, and a small local HTTP server. The server binds only to `127.0.0.1`; it is not
publicly hosted. Android's debug emulator build can connect through the host alias `10.0.2.2`.

For a physical Android phone connected by USB with debugging enabled, run
`adb reverse tcp:8787 tcp:8787` from the Android Studio terminal before opening the debug app.
The phone's debug build then connects to its own `127.0.0.1:8787`, which ADB forwards to this
backend on the computer. Keep the backend bound to computer loopback; this route does not require
opening port 8787 to the Wi-Fi network. The reverse mapping lasts only while the device's ADB
connection does. Release builds do not include the reflection bridge or its network permissions.
The Anthropic key, when live mode is used, belongs only in the computer's backend environment.

Java records are used as small immutable data holders. A record exposes its values through generated
accessor methods and does not offer setters; its constructor can still reject invalid combinations.

## Contract included

- `RequestIdentifiers` carries a canonical request UUID and the canonical Android run UUID.
- `CompletedRunEvidence` carries completed epoch-millisecond timestamps, the IANA timezone recorded
  at the run's start, optional active duration, raw metric evidence, optional pre-run Energy, and
  optional music context with explicit provenance.
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
epoch timestamp. Prompt version `mobile-reflection-v1.2` also distinguishes sample distance, partial
GPS and unavailable metrics, and limits claims made from partial media-session reports. It may omit
a music reference when the evidence is weak. Structured-output validation checks shape and preserves exact text;
it does not prove every generated statement is factual or that a real GPS distance is accurate.

## Local HTTP API

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
- `request_rejected` when Host or Origin fails the local request guard;
- `method_not_allowed` and `not_found` for unsupported HTTP routes;
- `request_id_conflict` when one request ID is reused with different run evidence;
- `provider_timeout`, `provider_unavailable`, and `provider_failure` for provider outcomes; and
- `internal_error` for an unexpected server failure.

Error responses never include request bodies, provider bodies, prompts, credentials, or underlying
exception messages.

### Duplicate-request behavior

The server keeps an in-memory map keyed by `requestId`. Concurrent identical requests share one
provider call, and later identical requests receive the same cached success or failure. Reusing an
ID with different identifiers or evidence returns `request_id_conflict` without calling the provider.

This protection is intentionally process-local for the current loopback demonstration: it does not
survive a server restart and has no persistence or eviction policy. A durable multi-process
idempotency store belongs to a later hosted-service design. For a deliberate new attempt after a
cached provider failure, use a new canonical request UUID.

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

## Run the tests

From this directory, run:

```powershell
mvn test
```

If Maven is not on `PATH`, use IntelliJ IDEA's Maven tool window or its bundled Maven executable.
The project can be opened independently by selecting this directory or its `pom.xml` in IntelliJ.

Endpoint tests start the server on an operating-system-assigned loopback port and inject fake
providers. They cover configured generation modes, exact success text, invalid and oversized requests, duplicate IDs,
unsupported methods, and safe provider failures without contacting Anthropic.

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
Release builds do not call this backend.

Automated tests use dummy configuration and fake or simulated provider responses; they make no
live Anthropic request. Separate bounded live checks exercised credentials, unit/time/provenance
grounding and phone delivery, but do not establish general generation quality. The server has no
database, authentication, TLS, durable idempotency, or public-hosting configuration. The Host/Origin
guard does not authenticate arbitrary local programs. The full music-aware demonstration remains
incomplete; current verification and remaining work are recorded in
[`project_current_state.md`](../docs/design/project_current_state.md).
