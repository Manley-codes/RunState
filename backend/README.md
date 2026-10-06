# RunState reflection backend foundation

This directory is a standalone Java 17/Maven project for the future RunState reflection service.
It currently contains the transport-neutral data contract plus an Anthropic provider adapter; it is
not an HTTP server and is not connected to Android.

Java records are used as small immutable data holders. A record exposes its values through generated
accessor methods and does not offer setters; its constructor can still reject invalid combinations.

## Contract included

- `RequestIdentifiers` carries a canonical request UUID and the canonical Android run UUID.
- `CompletedRunEvidence` carries completed epoch-millisecond timestamps, the IANA timezone recorded
  at the run's start, optional active duration, raw metric evidence, optional pre-run Energy, and
  explicitly controlled music context.
- `RunMetricsEvidence` uses Android's existing meanings: distance is stored in meters, the display
  unit remains separate, and metric source and telemetry coverage describe provenance. Unknown
  metrics stay unknown; the contract does not add formatted distance or pace strings.
- Missing pre-run Energy defaults to `UNKNOWN`, never `MODERATE`. Missing music context defaults to
  `UNKNOWN`, which stays distinct from an explicit `NO_MUSIC` value.
- Music fields can carry controlled track, artist, and artist-description evidence when music is
  explicitly present. Their existence does not approve any fixture, song, artist, or description.
- `CandidateReflections` requires nonblank text for the exact keys `spent`, `feeling_good`,
  `powered_up`, and `no_selection`. Accepted text is retained exactly rather than trimmed or rewritten.

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

## Run the tests

From this directory, run:

```powershell
mvn test
```

If Maven is not on `PATH`, use IntelliJ IDEA's Maven tool window or its bundled Maven executable.
The project can be opened independently by selecting this directory or its `pom.xml` in IntelliJ.

## Boundary

The contract can validate the evidence it receives, but it cannot prove that Android saved the run.
Android save ordering, candidate selection, recovery, and reflection persistence remain later
integration work. Automated adapter tests use dummy configuration and simulated provider responses;
they make no live Anthropic request and do not verify real generation quality, credentials, network
behavior, or Android delivery. This project still has no HTTP endpoint or database. The adapter and
prompt foundation are implemented, but the reflection milestone remains incomplete.
