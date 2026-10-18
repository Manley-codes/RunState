package com.runstate.reflection;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Transport-neutral values shared by the future Android client and reflection service.
 */
public final class ReflectionContract {

    private ReflectionContract() {
    }

    /** Identifies one generation request and the Android run it belongs to. */
    public record RequestIdentifiers(String requestId, String runId) {

        public RequestIdentifiers {
            requireCanonicalUuid(requestId, "requestId");
            requireCanonicalUuid(runId, "runId");
        }
    }

    /** Early preparation contains one opted-in song identity, never completed or live run facts. */
    public record SongPreparationRequest(String preparationId, String trackTitle, String artistName) {
        public SongPreparationRequest {
            requireCanonicalUuid(preparationId, "preparationId");
            requireBoundedText(trackTitle, 200, "trackTitle");
            requireBoundedText(artistName, 200, "artistName");
            require(trackTitle.codePoints().noneMatch(value -> Character.isISOControl(value)
                            || value >= 0xd800 && value <= 0xdfff)
                    && artistName.codePoints().noneMatch(value -> Character.isISOControl(value)
                            || value >= 0xd800 && value <= 0xdfff),
                    "Song preparation identity must contain valid visible Unicode text.");
        }
        @Override public String toString() { return "SongPreparationRequest[identity=redacted]"; }
    }

    /** Optional Energy captured before the run. */
    public enum PreRunEnergy {
        UNKNOWN,
        LOW,
        MODERATE,
        HIGH
    }

    /** The system that supplied the completed run's distance evidence. */
    public enum MetricSource {
        FIXTURE,
        GPS
    }

    /** How much of the intended distance evidence was available at completion. */
    public enum TelemetryCoverage {
        COMPLETE,
        PARTIAL,
        UNAVAILABLE
    }

    /** The unit the runner saw when the run was finalized. */
    public enum DistanceUnit {
        MILES,
        KILOMETERS
    }

    /** Whether music was explicitly present, absent, or not recorded. */
    public enum MusicState {
        UNKNOWN,
        NO_MUSIC,
        MUSIC
    }

    /** Distinguishes supplied music context from one partial device metadata report. */
    public enum MusicProvenance {
        CONTROLLED,
        MEDIA_SESSION_PARTIAL
    }

    /** Editorial song context is supplied independently of the device's playback observation. */
    public enum SongCueSource { RUNSTATE_CURATED }

    /**
     * A bounded editorial connection for one identified song, without lyrics or audio instructions.
     * The cue does not establish that a passage was heard or that later playback will succeed.
     */
    public record SongCueContext(
            String cueId,
            SongCueSource source,
            String theme,
            String connectionNote
    ) {
        public SongCueContext {
            require(cueId != null && cueId.matches("[a-z0-9][a-z0-9_-]{0,79}"),
                    "cueId must be a lowercase catalog identifier of at most 80 characters");
            require(source == SongCueSource.RUNSTATE_CURATED,
                    "song cue requires RUNSTATE_CURATED provenance");
            requireBoundedText(theme, 120, "theme");
            requireBoundedText(connectionNote, 400, "connectionNote");
        }
    }

    /** This slice accepts one modeled hourly snapshot near the run's start. */
    public enum WeatherSource { OPEN_METEO }
    public enum WeatherResolution { HOURLY }

    /** Lookup coordinates are excluded from reflection evidence; null context means unknown weather. */
    public record WeatherContext(
            WeatherSource source,
            WeatherResolution resolution,
            long targetEpochMillis,
            long validFromEpochMillis,
            long validToEpochMillis,
            long fetchedAtEpochMillis,
            double temperatureCelsius,
            double apparentTemperatureCelsius,
            double relativeHumidityPercent,
            double windSpeedKmh,
            int weatherCode
    ) {
        public WeatherContext {
            require(source == WeatherSource.OPEN_METEO
                            && resolution == WeatherResolution.HOURLY,
                    "weather requires OPEN_METEO HOURLY provenance");
            require(targetEpochMillis >= 0 && validFromEpochMillis >= 0 && fetchedAtEpochMillis >= targetEpochMillis,
                    "weather timestamps must be nonnegative and fetched no earlier than the target");
            require(validFromEpochMillis % 3_600_000L == 0
                            && validFromEpochMillis <= Long.MAX_VALUE - 3_600_000L
                            && validToEpochMillis == validFromEpochMillis + 3_600_000L
                            && targetEpochMillis >= validFromEpochMillis && targetEpochMillis < validToEpochMillis,
                    "weather must identify the UTC hour containing its target time");
            requireFiniteRange(temperatureCelsius, -100, 70, "temperatureCelsius");
            requireFiniteRange(apparentTemperatureCelsius, -150, 100, "apparentTemperatureCelsius");
            requireFiniteRange(relativeHumidityPercent, 0, 100, "relativeHumidityPercent");
            requireFiniteRange(windSpeedKmh, 0, 500, "windSpeedKmh");
            require(switch (weatherCode) {
                case 0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65,
                        66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99 -> true;
                default -> false;
            }, "weatherCode must be a supported WMO condition code");
        }
    }

    /**
     * Raw completed metric evidence. Null components together mean the evidence is unknown.
     */
    public record RunMetricsEvidence(
            Double distanceMeters,
            MetricSource source,
            TelemetryCoverage coverage,
            DistanceUnit displayUnit
    ) {

        public RunMetricsEvidence {
            require(
                    distanceMeters == null || (Double.isFinite(distanceMeters) && distanceMeters >= 0.0),
                    "distanceMeters must be unknown or a finite, nonnegative value"
            );

            boolean hasAnyMetricEvidence = distanceMeters != null
                    || source != null
                    || coverage != null
                    || displayUnit != null;

            if (hasAnyMetricEvidence) {
                require(source != null && coverage != null && displayUnit != null,
                        "known metric evidence requires source, coverage, and displayUnit together");

                if (coverage == TelemetryCoverage.UNAVAILABLE) {
                    require(distanceMeters == null,
                            "unavailable telemetry cannot claim a distance");
                } else {
                    require(distanceMeters != null,
                            coverage + " telemetry must include its measured distance");
                }
            }
        }

        public static RunMetricsEvidence unknown() {
            return new RunMetricsEvidence(null, null, null, null);
        }
    }

    /**
     * Music evidence with an explicit source boundary. Text is data and is preserved exactly.
     */
    public record MusicContext(
            MusicState state,
            String trackTitle,
            String artistName,
            String artistDescription,
            MusicProvenance provenance,
            SongCueContext songCue
    ) {

        public MusicContext {
            state = state == null ? MusicState.UNKNOWN : state;
            requireOptionalText(trackTitle, "trackTitle");
            requireOptionalText(artistName, "artistName");
            requireOptionalText(artistDescription, "artistDescription");

            if (state != MusicState.MUSIC) {
                require(trackTitle == null && artistName == null && artistDescription == null,
                        state + " music context cannot include track or artist details");
                require(provenance == null,
                        state + " music context cannot include music provenance");
                require(songCue == null,
                        state + " music context cannot include a song cue");
            } else {
                // Older controlled clients omitted this field; device observations must opt in explicitly.
                provenance = provenance == null ? MusicProvenance.CONTROLLED : provenance;
            }

            if (provenance == MusicProvenance.MEDIA_SESSION_PARTIAL) {
                require(trackTitle != null && artistName != null,
                        "partial media-session music requires trackTitle and artistName");
                require(artistDescription == null,
                        "partial media-session music cannot include artistDescription");
            }

            require(artistDescription == null || artistName != null,
                    "artistDescription requires an artistName");
            require(songCue == null || (trackTitle != null && artistName != null),
                    "song cue requires trackTitle and artistName");
        }

        /** Keeps existing music clients source compatible without adding editorial evidence. */
        public MusicContext(MusicState state, String trackTitle, String artistName,
                String artistDescription, MusicProvenance provenance) {
            this(state, trackTitle, artistName, artistDescription, provenance, null);
        }

        /** Keeps the pre-provenance controlled-music contract source compatible. */
        public MusicContext(
                MusicState state,
                String trackTitle,
                String artistName,
                String artistDescription
        ) {
            this(state, trackTitle, artistName, artistDescription, null);
        }

        public static MusicContext unknown() {
            return new MusicContext(MusicState.UNKNOWN, null, null, null);
        }
    }

    /**
     * Evidence from one completed run. It describes supplied data; it does not prove a save occurred.
     */
    public record CompletedRunEvidence(
            long officialStartEpochMillis,
            long finishEpochMillis,
            String startTimezoneId,
            Long activeDurationMillis,
            RunMetricsEvidence metrics,
            PreRunEnergy preRunEnergy,
            MusicContext musicContext,
            WeatherContext weatherContext
    ) {

        public CompletedRunEvidence {
            require(officialStartEpochMillis >= 0,
                    "officialStartEpochMillis must be nonnegative");
            require(finishEpochMillis >= officialStartEpochMillis,
                    "finishEpochMillis cannot precede officialStartEpochMillis");
            requireResolvableZoneId(startTimezoneId);

            long elapsedDurationMillis = finishEpochMillis - officialStartEpochMillis;
            require(activeDurationMillis == null
                            || (activeDurationMillis >= 0 && activeDurationMillis <= elapsedDurationMillis),
                    "activeDurationMillis must be unknown or between zero and elapsed duration");

            metrics = metrics == null ? RunMetricsEvidence.unknown() : metrics;
            preRunEnergy = preRunEnergy == null ? PreRunEnergy.UNKNOWN : preRunEnergy;
            musicContext = musicContext == null ? MusicContext.unknown() : musicContext;
            require(weatherContext == null || weatherContext.targetEpochMillis() == officialStartEpochMillis,
                    "weather target must match officialStartEpochMillis");
        }

        /** Keeps clients without native weather source compatible and weather unknown. */
        public CompletedRunEvidence(long officialStartEpochMillis, long finishEpochMillis,
                String startTimezoneId, Long activeDurationMillis, RunMetricsEvidence metrics,
                PreRunEnergy preRunEnergy, MusicContext musicContext) {
            this(officialStartEpochMillis, finishEpochMillis, startTimezoneId, activeDurationMillis,
                    metrics, preRunEnergy, musicContext, null);
        }
    }

    /** The four conditional texts returned for one shared factual foundation. */
    public record CandidateReflections(
            String spent,
            String feeling_good,
            String powered_up,
            String no_selection
    ) {

        public CandidateReflections {
            requireCandidateText(spent, "spent");
            requireCandidateText(feeling_good, "feeling_good");
            requireCandidateText(powered_up, "powered_up");
            requireCandidateText(no_selection, "no_selection");
        }
    }

    private static void requireCanonicalUuid(String candidate, String fieldName) {
        require(candidate != null, fieldName + " must be a canonical lowercase UUID");

        try {
            require(UUID.fromString(candidate).toString().equals(candidate),
                    fieldName + " must be a canonical lowercase UUID");
        } catch (IllegalArgumentException unparseable) {
            throw new IllegalArgumentException(
                    fieldName + " must be a canonical lowercase UUID: " + candidate,
                    unparseable
            );
        }
    }

    private static void requireResolvableZoneId(String candidate) {
        require(candidate != null && !candidate.isBlank(),
                "startTimezoneId must be a resolvable timezone id");

        try {
            ZoneId.of(candidate);
        } catch (DateTimeException unresolvable) {
            throw new IllegalArgumentException(
                    "startTimezoneId must be a resolvable timezone id: " + candidate,
                    unresolvable
            );
        }
    }

    private static void requireOptionalText(String candidate, String fieldName) {
        require(candidate == null || !candidate.isBlank(),
                fieldName + " must be absent or contain nonblank text");
    }

    private static void requireBoundedText(String candidate, int maximum, String fieldName) {
        require(candidate != null && !candidate.isBlank() && candidate.length() <= maximum,
                fieldName + " must contain nonblank text of at most " + maximum + " characters");
    }

    private static void requireFiniteRange(double value, double minimum, double maximum, String fieldName) {
        require(Double.isFinite(value) && value >= minimum && value <= maximum,
                fieldName + " must be finite and between " + minimum + " and " + maximum);
    }

    private static void requireCandidateText(String candidate, String branchKey) {
        require(candidate != null && !candidate.isBlank(),
                branchKey + " must contain nonblank candidate text");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
