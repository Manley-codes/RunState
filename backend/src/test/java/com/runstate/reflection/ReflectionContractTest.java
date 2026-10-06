package com.runstate.reflection;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.runstate.reflection.ReflectionContract.CandidateReflections;
import static com.runstate.reflection.ReflectionContract.CompletedRunEvidence;
import static com.runstate.reflection.ReflectionContract.DistanceUnit;
import static com.runstate.reflection.ReflectionContract.MetricSource;
import static com.runstate.reflection.ReflectionContract.MusicContext;
import static com.runstate.reflection.ReflectionContract.MusicState;
import static com.runstate.reflection.ReflectionContract.PreRunEnergy;
import static com.runstate.reflection.ReflectionContract.RequestIdentifiers;
import static com.runstate.reflection.ReflectionContract.RunMetricsEvidence;
import static com.runstate.reflection.ReflectionContract.TelemetryCoverage;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReflectionContractTest {

    private static final String REQUEST_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String RUN_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    @Test
    void acceptsKnownCompletedRunEvidence() {
        RequestIdentifiers identifiers = new RequestIdentifiers(REQUEST_ID, RUN_ID);
        RunMetricsEvidence metrics = new RunMetricsEvidence(
                5_000.25,
                MetricSource.GPS,
                TelemetryCoverage.PARTIAL,
                DistanceUnit.MILES
        );
        MusicContext music = new MusicContext(
                MusicState.MUSIC,
                "Run It Up",
                "Offset & Key Glock",
                "Controlled artist context"
        );

        CompletedRunEvidence evidence = new CompletedRunEvidence(
                1_780_000_000_000L,
                1_780_001_800_000L,
                "America/Chicago",
                1_650_000L,
                metrics,
                PreRunEnergy.LOW,
                music
        );

        assertAll(
                () -> assertEquals(REQUEST_ID, identifiers.requestId()),
                () -> assertEquals(RUN_ID, identifiers.runId()),
                () -> assertEquals(5_000.25, evidence.metrics().distanceMeters()),
                () -> assertEquals(MetricSource.GPS, evidence.metrics().source()),
                () -> assertEquals(TelemetryCoverage.PARTIAL, evidence.metrics().coverage()),
                () -> assertEquals(DistanceUnit.MILES, evidence.metrics().displayUnit()),
                () -> assertEquals(PreRunEnergy.LOW, evidence.preRunEnergy()),
                () -> assertEquals(music, evidence.musicContext())
        );
    }

    @Test
    void keepsMissingEvidenceUnknownInsteadOfInventingValues() {
        CompletedRunEvidence evidence = new CompletedRunEvidence(
                1_780_000_000_000L,
                1_780_000_600_000L,
                "America/Chicago",
                null,
                null,
                null,
                null
        );

        assertAll(
                () -> assertNull(evidence.activeDurationMillis()),
                () -> assertEquals(RunMetricsEvidence.unknown(), evidence.metrics()),
                () -> assertEquals(PreRunEnergy.UNKNOWN, evidence.preRunEnergy()),
                () -> assertEquals(MusicContext.unknown(), evidence.musicContext()),
                () -> assertEquals(MusicState.UNKNOWN, evidence.musicContext().state())
        );
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RequestIdentifiers(null, RUN_ID)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RequestIdentifiers("", RUN_ID)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RequestIdentifiers(REQUEST_ID.toUpperCase(), RUN_ID)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RequestIdentifiers(REQUEST_ID, "not-a-uuid"))
        );
    }

    @Test
    void rejectsInconsistentTimestampsAndInvalidTimezone() {
        assertAll(
                () -> assertInvalidEvidence(-1L, 10L, "America/Chicago", null),
                () -> assertInvalidEvidence(20L, 19L, "America/Chicago", null),
                () -> assertInvalidEvidence(10L, 20L, "America/Chicago", -1L),
                () -> assertInvalidEvidence(10L, 20L, "America/Chicago", 11L),
                () -> assertInvalidEvidence(10L, 20L, "", null),
                () -> assertInvalidEvidence(10L, 20L, "Not/A_Timezone", null)
        );
    }

    @Test
    void rejectsInvalidOrContradictoryMetricEvidence() {
        assertAll(
                () -> assertInvalidMetrics(-0.01, MetricSource.GPS,
                        TelemetryCoverage.COMPLETE, DistanceUnit.MILES),
                () -> assertInvalidMetrics(Double.NaN, MetricSource.GPS,
                        TelemetryCoverage.COMPLETE, DistanceUnit.MILES),
                () -> assertInvalidMetrics(Double.POSITIVE_INFINITY, MetricSource.GPS,
                        TelemetryCoverage.COMPLETE, DistanceUnit.MILES),
                () -> assertInvalidMetrics(1_000.0, null,
                        TelemetryCoverage.COMPLETE, DistanceUnit.KILOMETERS),
                () -> assertInvalidMetrics(null, MetricSource.FIXTURE,
                        TelemetryCoverage.PARTIAL, DistanceUnit.MILES),
                () -> assertInvalidMetrics(1_000.0, MetricSource.FIXTURE,
                        TelemetryCoverage.UNAVAILABLE, DistanceUnit.MILES)
        );
    }

    @Test
    void distinguishesUnknownNoMusicAndControlledMusic() {
        MusicContext noMusic = new MusicContext(MusicState.NO_MUSIC, null, null, null);
        MusicContext controlledMusic = new MusicContext(
                MusicState.MUSIC,
                "  exact track title  ",
                "Exact artist",
                "Exact artist description"
        );

        assertAll(
                () -> assertEquals(MusicState.UNKNOWN, MusicContext.unknown().state()),
                () -> assertEquals(MusicState.NO_MUSIC, noMusic.state()),
                () -> assertEquals("  exact track title  ", controlledMusic.trackTitle()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MusicContext(MusicState.UNKNOWN, "Track", null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MusicContext(MusicState.MUSIC, " ", null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MusicContext(MusicState.MUSIC, null, null, "Description"))
        );
    }

    @Test
    void requiresAllFourCandidateBranches() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CandidateReflections(null, "good", "up", "none")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CandidateReflections("spent", " ", "up", "none")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CandidateReflections("spent", "good", "", "none")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CandidateReflections("spent", "good", "up", "\n\t"))
        );
    }

    @Test
    void exposesExactBranchKeysAndPreservesAcceptedText() {
        String spent = "  Spent text stays padded.  ";
        String feelingGood = "Feeling good line one.\nLine two.";
        String poweredUp = "Powered up — exact punctuation.";
        String noSelection = "No selection is not Moderate.";

        CandidateReflections candidates = new CandidateReflections(
                spent,
                feelingGood,
                poweredUp,
                noSelection
        );
        List<String> componentNames = Arrays.stream(CandidateReflections.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertAll(
                () -> assertEquals(
                        List.of("spent", "feeling_good", "powered_up", "no_selection"),
                        componentNames
                ),
                () -> assertEquals(spent, candidates.spent()),
                () -> assertEquals(feelingGood, candidates.feeling_good()),
                () -> assertEquals(poweredUp, candidates.powered_up()),
                () -> assertEquals(noSelection, candidates.no_selection())
        );
    }

    private static void assertInvalidEvidence(
            long start,
            long finish,
            String timezone,
            Long activeDuration
    ) {
        assertThrows(IllegalArgumentException.class, () -> new CompletedRunEvidence(
                start,
                finish,
                timezone,
                activeDuration,
                null,
                null,
                null
        ));
    }

    private static void assertInvalidMetrics(
            Double distanceMeters,
            MetricSource source,
            TelemetryCoverage coverage,
            DistanceUnit displayUnit
    ) {
        assertThrows(IllegalArgumentException.class, () -> new RunMetricsEvidence(
                distanceMeters,
                source,
                coverage,
                displayUnit
        ));
    }
}
