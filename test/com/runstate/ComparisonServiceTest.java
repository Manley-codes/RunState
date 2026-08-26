package com.runstate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/*
 * The project's first unit tests, targeting ComparisonService — pure logic, no I/O.
 *
 * Every test follows Arrange / Act / Assert:
 *   Arrange — build the inputs
 *   Act     — call the method under test
 *   Assert  — state what must be true; JUnit fails the test if it isn't
 *
 * selectCandidates() and its helpers are private, so we never call them directly.
 * We verify selection through the ONLY public door, analyze(), by inspecting the
 * ComparisonInsight it returns (comparable count + basis). Testing behavior through
 * the public API keeps these tests valid even if the internals are refactored.
 *
 * Trigger note: analyze() returns NONE (count 0) unless a positive signal survives
 * the pre-filter. To make the candidate count observable, every "current" run below
 * is LOW->HIGH (a +2 energy lift) while candidates are MODERATE->MODERATE (0 lift).
 * That fires exactly one signal — State Lift — so the count surfaces cleanly. Effort
 * is left null, which silences all effort-based signals.
 */
public class ComparisonServiceTest {

    // --- Test data factory ---------------------------------------------------
    // The Run constructor takes 15 arguments; a comparison only cares about a few.
    // These helpers fill the boilerplate with sensible defaults so each test can
    // show just the fields that matter (DRY — Don't Repeat Yourself, for tests).

    // Builds a Run for comparison tests. Distance is given in MILES (unit = MILES,
    // whose multiplier is 1.0), so getDistanceInMiles() returns exactly this value
    // and pace = duration / distance. Fields irrelevant to comparison are defaulted;
    // a test that needs an extra (a PR flag, weather) sets it on the returned Run.
    private static Run run(LocalDate date, String route, double distanceMiles,
                           double durationMin, EnergyLevel pre, EnergyLevel post,
                           EffortLevel effort) {
        return new Run(
                0,                              // runId — irrelevant here
                null,                           // runner — analyze() takes history directly
                date,
                null, null,                     // start/end time — unused by comparison
                distanceMiles, DistanceUnit.MILES,
                durationMin,
                route,
                null,                           // routeLocation — unused
                pre, post,
                null,                           // RunContext — unused here (coerced to EMPTY)
                null,                           // weather — set per-test when needed
                effort
        );
    }

    // Shorthand so tests read "2026-06-01" instead of LocalDate.parse(...) each time.
    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    // --- Candidate selection tests -------------------------------------------

    @Test
    void excludesRunsOutsideRecencyWindow() {
        // Arrange: current run, plus three same-route candidates at 10, 179, and 181
        // days before it. The recency window is 180 days, so the 181-days-ago run
        // falls just outside the cutoff and must be dropped.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 27.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        Run inWindowRecent = run(today.minusDays(10), "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run inWindowEdge = run(today.minusDays(179), "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run outOfWindow = run(today.minusDays(181), "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        List<Run> history = List.of(inWindowRecent, inWindowEdge, outOfWindow);

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: only the two in-window runs count as comparable.
        // State Lift fires (LOW->HIGH current, both candidates are energy-complete),
        // so the energy pool size equals the selected candidate count here.
        assertEquals(2, insight.getOutcomes().get(0).getEvidenceCount());
    }

    @Test
    void capsCandidatesAtTen() {
        // Arrange: twelve eligible same-route candidates, all within recency.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 27.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        List<Run> history = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            history.add(run(today.minusDays(i), "Cedar Trail", 3.0, 27.0,
                    EnergyLevel.MODERATE, EnergyLevel.MODERATE, null));
        }

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: the cap of 10 holds even though 12 were eligible.
        assertEquals(10, insight.getOutcomes().get(0).getEvidenceCount());
    }

    @Test
    void prefersSameRouteWithinDistanceBandOverOtherRoutes() {
        // Arrange: current run on Cedar Trail at 3.0 mi. Two same-route runs are inside
        // the distance band and one is clearly outside it. Two different-route runs are
        // also inside the band. Route matching should win, but only for comparable lengths.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 27.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        Run sameRouteA = run(today.minusDays(1), "Cedar Trail", 3.2, 28.8,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run sameRouteB = run(today.minusDays(2), "Cedar Trail", 2.8, 25.2,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run sameRouteFar = run(today.minusDays(3), "Cedar Trail", 8.0, 72.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run otherRouteSameDistanceA = run(today.minusDays(4), "River Loop", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run otherRouteSameDistanceB = run(today.minusDays(5), "River Loop", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        List<Run> history = List.of(sameRouteA, sameRouteB, sameRouteFar,
                otherRouteSameDistanceA, otherRouteSameDistanceB);

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: only the two same-route runs inside the band are selected.
        assertEquals(2, insight.getOutcomes().get(0).getEvidenceCount());
        assertEquals("same route", insight.getBasis());
    }

    @Test
    void fallsBackToSimilarDistanceWhenSameRouteRunsAreOutsideDistanceBand() {
        // Arrange: the same-route history is 6/7/8 miles, so none is comparable to
        // today's 3 miles. Two other-route runs are inside the distance band.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 27.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        Run sameRouteA = run(today.minusDays(1), "Cedar Trail", 6.0, 54.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run sameRouteB = run(today.minusDays(2), "Cedar Trail", 7.0, 63.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run sameRouteC = run(today.minusDays(3), "Cedar Trail", 8.0, 72.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run nearDistanceA = run(today.minusDays(4), "River Loop", 3.2, 28.8,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run nearDistanceB = run(today.minusDays(5), "Park Loop", 2.8, 25.2,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        List<Run> history = List.of(
                sameRouteA, sameRouteB, sameRouteC, nearDistanceA, nearDistanceB);

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: the far same-route runs are excluded and the distance fallback is used.
        assertEquals(2, insight.getOutcomes().get(0).getEvidenceCount());
        assertEquals("similar distance", insight.getBasis());
    }

    @Test
    void fallsBackToSimilarDistanceWhenNoRouteMatch() {
        // Arrange: current run on Cedar Trail at 3.0 mi (band = max(0.5, 0.6) = 0.6).
        // No candidate shares the route, so selection falls back to similar distance:
        // 3.2 and 2.8 mi are within the band; 6.0 mi is outside and must be excluded.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 27.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        Run nearDistanceA = run(today.minusDays(1), "River Loop", 3.2, 28.8,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run nearDistanceB = run(today.minusDays(2), "River Loop", 2.8, 25.2,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run farDistance = run(today.minusDays(3), "River Loop", 6.0, 54.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        List<Run> history = List.of(nearDistanceA, nearDistanceB, farDistance);

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: the two within-band runs, matched by distance rather than route.
        assertEquals(2, insight.getOutcomes().get(0).getEvidenceCount());
        assertEquals("similar distance", insight.getBasis());
    }

    // --- Median aggregation tests --------------------------------------------
    // These isolate median(pace) by holding everything else constant so that the
    // ONLY signal able to fire is Same-Cost/Better, which does iff the current pace
    // is faster (lower) than the median candidate pace. Every run therefore shares:
    //   effort MODERATE_COST  -> effort == median effort -> "same cost" always true,
    //                            and Quiet Gain / Demand-Explained stay silent
    //   energy MODERATE->MODERATE (lift 0) -> State Lift stays silent
    //   route "Track", distance 1.0 mi -> all selected; pace == duration exactly
    // So hasInsight() becomes a clean readout of "current pace < median pace".

    // Helper: a candidate for the median tests whose pace equals the given value
    // (distance 1.0 mi, so pace = duration = paceValue). Flat energy + MODERATE_COST.
    private static Run pacedCandidate(LocalDate date, double paceValue) {
        return run(date, "Track", 1.0, paceValue,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST);
    }

    @Test
    void oddCandidateCountUsesMiddleValueNotMean() {
        // Arrange: three candidate paces [9, 10, 20]. Median = 10, but mean = ~13
        // (dragged up by the 20 outlier). The current pace is 11 — slower than the
        // median, but faster than the mean.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Track", 1.0, 11.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST);
        List<Run> history = List.of(
                pacedCandidate(today.minusDays(1), 9.0),
                pacedCandidate(today.minusDays(2), 10.0),
                pacedCandidate(today.minusDays(3), 20.0));

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: no insight. 11 is slower than the median (10), so nothing fires.
        // If the code used the mean (~13) instead, 11 would look "faster" and fire —
        // so this proves both middle-value selection AND outlier resistance.
        assertFalse(insight.hasInsight());
    }

    @Test
    void evenCandidateCountAveragesTwoMiddleValues() {
        // Arrange: four candidate paces [8, 10, 12, 14]. Even count -> median is the
        // average of the two middle values, (10 + 12) / 2 = 11.
        LocalDate today = date("2026-06-01");
        List<Run> history = List.of(
                pacedCandidate(today.minusDays(1), 8.0),
                pacedCandidate(today.minusDays(2), 10.0),
                pacedCandidate(today.minusDays(3), 12.0),
                pacedCandidate(today.minusDays(4), 14.0));

        // Act + Assert: a current pace of 10.9 is faster than the 11 median -> fires;
        // 11.1 is slower -> does not. The pair brackets the median at exactly 11,
        // ruling out a bug that just grabbed one of the two middle values (10 or 12).
        Run justFaster = run(date("2026-06-01"), "Track", 1.0, 10.9,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST);
        Run justSlower = run(date("2026-06-01"), "Track", 1.0, 11.1,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST);

        assertTrue(ComparisonService.analyze(justFaster, history).hasInsight());
        assertFalse(ComparisonService.analyze(justSlower, history).hasInsight());
    }

    // --- Negative pre-filter tests -------------------------------------------
    // The critical safety rule: negative outcomes (finished lower, slower, or higher
    // effort with no reason) are dropped BEFORE the prompt is built — never turned
    // into a line for the AI to soften. These prove the filter actually filters, and
    // that it stays precise: an *explained* higher effort is still allowed through.

    @Test
    void lowerEnergyLiftIsFilteredOut() {
        // Arrange: candidates all climbed LOW->HIGH (lift +2); today only held flat
        // MODERATE->MODERATE (lift 0) — a worse energy outcome than usual.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        List<Run> history = List.of(
                run(today.minusDays(1), "Cedar Trail", 3.0, 27.0,
                        EnergyLevel.LOW, EnergyLevel.HIGH, null),
                run(today.minusDays(2), "Cedar Trail", 3.0, 27.0,
                        EnergyLevel.LOW, EnergyLevel.HIGH, null),
                run(today.minusDays(3), "Cedar Trail", 3.0, 27.0,
                        EnergyLevel.LOW, EnergyLevel.HIGH, null));

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: a smaller lift than usual produces silence, not a negative remark.
        assertFalse(insight.hasInsight());
    }

    @Test
    void unexplainedHigherEffortIsFilteredOut() {
        // Arrange: today cost clearly MORE (HIGH_COST vs candidates' LOW_COST) but
        // with no PR and the same distance — no legitimate reason for the extra cost.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.HIGH_COST);
        List<Run> history = List.of(
                run(today.minusDays(1), "Cedar Trail", 3.0, 27.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.LOW_COST),
                run(today.minusDays(2), "Cedar Trail", 3.0, 27.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.LOW_COST),
                run(today.minusDays(3), "Cedar Trail", 3.0, 27.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.LOW_COST));

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: unexplained higher effort is dropped — it never reads as a bad run.
        assertFalse(insight.hasInsight());
    }

    @Test
    void higherEffortWithPrIsExplainedNotFiltered() {
        // Arrange: identical to the unexplained case, except today set a PR — now the
        // higher effort HAS a legitimate explanation and should be allowed through.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.HIGH_COST);
        current.markFastestAveragePaceRecord();
        List<Run> history = List.of(
                run(today.minusDays(1), "Cedar Trail", 3.0, 27.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.LOW_COST),
                run(today.minusDays(2), "Cedar Trail", 3.0, 27.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.LOW_COST),
                run(today.minusDays(3), "Cedar Trail", 3.0, 27.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.LOW_COST));

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: the explained higher effort fires, and the line names the PR reason.
        assertTrue(insight.hasInsight());
        assertTrue(insight.getOutcomes().get(0).getLine().contains("PR"));
    }

    @Test
    void energySignalCountReflectsOnlyEnergyCompleteCandidates() {
        // 8 same-route candidates — only 2 have both pre+post energy.
        // The other 6 have null pre-energy and are excluded from the energy pool.
        // State Lift must fire with evidence count 2, not 8.
        LocalDate today = date("2026-06-01");
        // Current: LOW→HIGH, lift +2. Beats any MODERATE→MODERATE candidate (lift 0).
        Run current = run(today, "Cedar Trail", 3.0, 24.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        List<Run> history = new ArrayList<>();
        history.add(run(today.minusDays(1), "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null));
        history.add(run(today.minusDays(2), "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null));
        for (int i = 3; i <= 8; i++) {
            history.add(run(today.minusDays(i), "Cedar Trail", 3.0, 27.0,
                    null, EnergyLevel.MODERATE, null));
        }

        ComparisonInsight insight = ComparisonService.analyze(current, history);

        assertTrue(insight.hasInsight());
        ComparisonOutcome stateLift = insight.getOutcomes().get(0);
        assertTrue(stateLift.getLine().contains("energy lift"));
        assertEquals(2, stateLift.getEvidenceCount());
        assertEquals("early signal", stateLift.getConfidencePhrase());
    }

    @Test
    void effortPoolExcludesMissingEffortFromPaceMedian() {
        // Two effort-bearing candidates: pace 9 min/mile.
        // Six non-effort candidates: pace 1 min/mile (unrealistically fast).
        // If all 8 fed the pace median, median = 1 min/mile and current (8 min/mile)
        // would NOT be faster — Same-Cost/Better would stay silent.
        // Correct: effort pool (2 runs) gives median 9, signal fires with count 2.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 24.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST);
        List<Run> history = new ArrayList<>();
        history.add(run(today.minusDays(1), "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST));
        history.add(run(today.minusDays(2), "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST));
        for (int i = 3; i <= 8; i++) {
            history.add(run(today.minusDays(i), "Cedar Trail", 3.0, 3.0,
                    EnergyLevel.MODERATE, EnergyLevel.MODERATE, null));
        }

        ComparisonInsight insight = ComparisonService.analyze(current, history);

        assertTrue(insight.hasInsight());
        ComparisonOutcome outcome = insight.getOutcomes().get(0);
        assertTrue(outcome.getLine().contains("faster"));
        assertEquals(2, outcome.getEvidenceCount());
        assertEquals("early signal", outcome.getConfidencePhrase());
    }

    @Test
    void simultaneousStateLiftAndEffortOutcomesRetainSeparateCounts() {
        // 8 candidates — all effort-bearing (MODERATE_COST), only first 3 energy-complete.
        // State Lift draws from the 3-run energy pool; Same-Cost/Better from the 8-run
        // effort pool. The two outcomes must each carry their own count and confidence tier.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 24.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, EffortLevel.MODERATE_COST);
        List<Run> history = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            history.add(run(today.minusDays(i), "Cedar Trail", 3.0, 27.0,
                    EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST));
        }
        for (int i = 4; i <= 8; i++) {
            history.add(run(today.minusDays(i), "Cedar Trail", 3.0, 27.0,
                    null, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST));
        }

        ComparisonInsight insight = ComparisonService.analyze(current, history);

        assertEquals(2, insight.getOutcomes().size());
        ComparisonOutcome stateLift = insight.getOutcomes().get(0);
        assertTrue(stateLift.getLine().contains("energy lift"));
        assertEquals(3, stateLift.getEvidenceCount());
        assertEquals("early signal", stateLift.getConfidencePhrase());
        ComparisonOutcome sameCostBetter = insight.getOutcomes().get(1);
        assertTrue(sameCostBetter.getLine().contains("faster"));
        assertEquals(8, sameCostBetter.getEvidenceCount());
        assertEquals("strong personal pattern", sameCostBetter.getConfidencePhrase());
    }

    @Test
    void confidenceTierBoundary_oneRun() {
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 24.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        List<Run> history = List.of(
                run(today.minusDays(1), "Cedar Trail", 3.0, 27.0,
                        EnergyLevel.MODERATE, EnergyLevel.MODERATE, null));

        ComparisonInsight insight = ComparisonService.analyze(current, history);

        assertEquals(1, insight.getOutcomes().get(0).getEvidenceCount());
        assertEquals("last comparable run", insight.getOutcomes().get(0).getConfidencePhrase());
    }

    @Test
    void confidenceTierBoundary_twoRuns() {
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 24.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        List<Run> history = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            history.add(run(today.minusDays(i), "Cedar Trail", 3.0, 27.0,
                    EnergyLevel.MODERATE, EnergyLevel.MODERATE, null));
        }

        ComparisonInsight insight = ComparisonService.analyze(current, history);

        assertEquals(2, insight.getOutcomes().get(0).getEvidenceCount());
        assertEquals("early signal", insight.getOutcomes().get(0).getConfidencePhrase());
    }

    @Test
    void confidenceTierBoundary_fiveRuns() {
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 24.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        List<Run> history = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            history.add(run(today.minusDays(i), "Cedar Trail", 3.0, 27.0,
                    EnergyLevel.MODERATE, EnergyLevel.MODERATE, null));
        }

        ComparisonInsight insight = ComparisonService.analyze(current, history);

        assertEquals(5, insight.getOutcomes().get(0).getEvidenceCount());
        assertEquals("recent pattern", insight.getOutcomes().get(0).getConfidencePhrase());
    }

    @Test
    void confidenceTierBoundary_eightRuns() {
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 24.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        List<Run> history = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            history.add(run(today.minusDays(i), "Cedar Trail", 3.0, 27.0,
                    EnergyLevel.MODERATE, EnergyLevel.MODERATE, null));
        }

        ComparisonInsight insight = ComparisonService.analyze(current, history);

        assertEquals(8, insight.getOutcomes().get(0).getEvidenceCount());
        assertEquals("strong personal pattern", insight.getOutcomes().get(0).getConfidencePhrase());
    }

    @Test
    void effortSignalCountReflectsOnlyEffortBearingCandidates() {
        // Arrange: 8 same-route candidates within recency. Only the first two have
        // effort recorded. The other six have null effort and must not count toward
        // the effort signal's evidence.
        // Current run: same effort as effort-bearing candidates but faster pace → fires
        // Same-Cost/Better (effort signal).
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 24.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST);
        List<Run> history = new ArrayList<>();
        // Two effort-bearing candidates (pace 27 min = 9 min/mile)
        history.add(run(today.minusDays(1), "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST));
        history.add(run(today.minusDays(2), "Cedar Trail", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST));
        // Six candidates with no effort recorded
        for (int i = 3; i <= 8; i++) {
            history.add(run(today.minusDays(i), "Cedar Trail", 3.0, 27.0,
                    EnergyLevel.MODERATE, EnergyLevel.MODERATE, null));
        }

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: the effort signal fired, but its evidence count is 2, not 8.
        assertTrue(insight.hasInsight());
        ComparisonOutcome effortOutcome = insight
                .getOutcomes().get(0);
        assertEquals(2, effortOutcome.getEvidenceCount());
        assertEquals("early signal", effortOutcome.getConfidencePhrase());
    }
}
