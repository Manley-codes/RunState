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
     * Explicitly controlled music evidence. Optional text is data and is preserved exactly.
     */
    public record MusicContext(
            MusicState state,
            String trackTitle,
            String artistName,
            String artistDescription
    ) {

        public MusicContext {
            state = state == null ? MusicState.UNKNOWN : state;
            requireOptionalText(trackTitle, "trackTitle");
            requireOptionalText(artistName, "artistName");
            requireOptionalText(artistDescription, "artistDescription");

            if (state != MusicState.MUSIC) {
                require(trackTitle == null && artistName == null && artistDescription == null,
                        state + " music context cannot include track or artist details");
            }

            require(artistDescription == null || artistName != null,
                    "artistDescription requires an artistName");
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
            MusicContext musicContext
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
