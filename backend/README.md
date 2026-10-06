# RunState reflection backend foundation

This directory is a standalone Java 17/Maven project for the future RunState reflection service.
The current slice implements only the transport-neutral data contract and its validation.

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
integration work. This project currently has no HTTP endpoint, Anthropic call, writing prompt, or
database. The contract foundation is implemented; generation and Android integration remain to be
built, so this slice does not complete the reflection milestone.
