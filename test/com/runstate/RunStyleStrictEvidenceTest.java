package com.runstate;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Step 2 (RunStyle V1) tests for ComparisonService.evaluateStrict — the strict
 * candidate selection and the typed measurable/supported evidence. These run through
 * the package-private evaluateStrict(...) directly (same package), inspecting the
 * StrictEvidence it returns.
 *
 * The existing 9 ComparisonServiceTest cases still cover analyze(); this path is
 * separate and must not disturb them (both suites run together to prove that).
 */
public class RunStyleStrictEvidenceTest {

    // Builds a Run with a route AND surface (both drive strict selection). Company,
    // shoes, music are irrelevant here, so the context carries only the surface.
    private static Run run(LocalDate date, String route, SurfaceType surface,
                           double distanceMiles, double durationMin,
                           EnergyLevel pre, EnergyLevel post, EffortLevel effort) {
        RunContext context = new RunContext(surface, null, null, null, null);
        return new Run(
                0, null, date, null, null,
                distanceMiles, DistanceUnit.MILES, durationMin,
                route, null, pre, post, context, null, effort);
    }

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    // --- Strict candidate selection ------------------------------------------

    @Test
    void rejectsSameRouteOutsideDistanceBandAndFallsBackToSurface() {
        // Current: Cedar Trail, TRAIL surface, 3.0 mi -> band = max(0.5, 0.6) = 0.6.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", SurfaceType.TRAIL, 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        // Same route but 6.0 mi — WAY outside the band, so the route tier must reject it
        // (strict always requires similar distance). Its surface differs too.
        Run sameRouteFar = run(today.minusDays(1), "Cedar Trail", SurfaceType.ROAD, 6.0, 54.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        // Different route, but same TRAIL surface and 3.1 mi (within band) — the surface tier.
        Run sameSurfaceNear = run(today.minusDays(2), "River Loop", SurfaceType.TRAIL, 3.1, 27.9,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        List<Run> history = List.of(sameRouteFar, sameSurfaceNear);

        StrictEvidence evidence = ComparisonService.evaluateStrict(current, history);

        // Route tier found nothing in-band, so selection falls to same surface: 1 candidate.
        assertEquals(1, evidence.getCandidateCount());
        assertEquals("same surface", evidence.getBasis());
    }

    @Test
    void prefersSameRouteWhenWithinBand() {
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", SurfaceType.TRAIL, 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        // Same route, 3.2 mi (within 0.6 band) — the route tier should take it even though
        // its surface differs.
        Run sameRouteNear = run(today.minusDays(1), "Cedar Trail", SurfaceType.ROAD, 3.2, 28.8,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        // A same-surface, similar-distance run that would win tier 2 — but tier 1 fires first.
        Run sameSurfaceNear = run(today.minusDays(2), "River Loop", SurfaceType.TRAIL, 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        List<Run> history = List.of(sameRouteNear, sameSurfaceNear);

        StrictEvidence evidence = ComparisonService.evaluateStrict(current, history);

        assertEquals(1, evidence.getCandidateCount());
        assertEquals("same route", evidence.getBasis());
    }

    @Test
    void fallsBackToSimilarDistanceWhenNoRouteOrSurfaceMatch() {
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", SurfaceType.TRAIL, 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        // Different route AND different surface, but 3.1 mi (within band) — tier 3 only.
        Run nearDistance = run(today.minusDays(1), "River Loop", SurfaceType.ROAD, 3.1, 27.9,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        List<Run> history = List.of(nearDistance);

        StrictEvidence evidence = ComparisonService.evaluateStrict(current, history);

        assertEquals(1, evidence.getCandidateCount());
        assertEquals("similar distance", evidence.getBasis());
    }

    @Test
    void noComparableCandidatesReturnsNone() {
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", SurfaceType.TRAIL, 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        // Only run in history is the far same-route run — nothing lands in any tier.
        Run tooFar = run(today.minusDays(1), "Cedar Trail", SurfaceType.TRAIL, 9.0, 81.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        List<Run> history = List.of(tooFar);

        StrictEvidence evidence = ComparisonService.evaluateStrict(current, history);

        assertEquals(0, evidence.getCandidateCount());
        assertFalse(evidence.hasAnyMeasurable());
    }

    // --- Typed evidence: measurable vs supported -----------------------------

    @Test
    void effortSignalsAreMeasurableButNotSupportedWhenFlat() {
        // Current and candidates identical: same route/distance/pace, flat energy, same
        // MODERATE_COST effort. The effort signals CAN be judged (measurable) but none
        // fire (no lower effort, not faster, not higher-with-reason).
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Track", SurfaceType.TRACK, 1.0, 10.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST);
        List<Run> history = List.of(
                run(today.minusDays(1), "Track", SurfaceType.TRACK, 1.0, 10.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST),
                run(today.minusDays(2), "Track", SurfaceType.TRACK, 1.0, 10.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST));

        StrictEvidence evidence = ComparisonService.evaluateStrict(current, history);

        // Measurable: every signal had the data to be judged.
        assertTrue(evidence.isMeasurable(RunStyleSignal.QUIET_GAIN));
        assertTrue(evidence.isMeasurable(RunStyleSignal.SAME_COST_BETTER));
        assertTrue(evidence.isMeasurable(RunStyleSignal.STATE_LIFT));
        // Supported: none fired on an identical run.
        assertFalse(evidence.isSupported(RunStyleSignal.QUIET_GAIN));
        assertFalse(evidence.isSupported(RunStyleSignal.SAME_COST_BETTER));
        assertFalse(evidence.isSupported(RunStyleSignal.STATE_LIFT));
    }

    @Test
    void sameCostBetterFiresWhenFasterAtEqualEffort() {
        // Same effort as the candidates, but faster pace (9 vs 10) -> SAME_COST_BETTER fires.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Track", SurfaceType.TRACK, 1.0, 9.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST);
        List<Run> history = List.of(
                run(today.minusDays(1), "Track", SurfaceType.TRACK, 1.0, 10.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST),
                run(today.minusDays(2), "Track", SurfaceType.TRACK, 1.0, 10.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST));

        StrictEvidence evidence = ComparisonService.evaluateStrict(current, history);

        assertTrue(evidence.isSupported(RunStyleSignal.SAME_COST_BETTER));
        // Not a lower-effort run, so Quiet Gain stays unfired.
        assertFalse(evidence.isSupported(RunStyleSignal.QUIET_GAIN));
    }

    @Test
    void demandExplainedFiresOnHigherEffortWithPr() {
        // Higher effort than the candidates (HIGH_COST vs LOW_COST) AND a PR -> the extra
        // cost is explained, so DEMAND_EXPLAINED fires.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Track", SurfaceType.TRACK, 1.0, 10.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.HIGH_COST);
        current.markFastestAveragePaceRecord();
        List<Run> history = List.of(
                run(today.minusDays(1), "Track", SurfaceType.TRACK, 1.0, 10.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.LOW_COST),
                run(today.minusDays(2), "Track", SurfaceType.TRACK, 1.0, 10.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.LOW_COST));

        StrictEvidence evidence = ComparisonService.evaluateStrict(current, history);

        assertTrue(evidence.isSupported(RunStyleSignal.DEMAND_EXPLAINED));
    }
}
