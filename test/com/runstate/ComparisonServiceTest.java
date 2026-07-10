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
                null,                           // musicContext — unused
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
        assertEquals(2, insight.getComparableCount());
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
        assertEquals(10, insight.getComparableCount());
    }

    @Test
    void prefersSameRouteOverSimilarDistance() {
        // Arrange: current run on Cedar Trail at 3.0 mi. History has three same-route
        // runs at clearly DIFFERENT distances (6/7/8 mi — outside the distance band),
        // plus two different-route runs at the SAME 3.0 mi (which distance-matching
        // would otherwise catch). Route matching should win, selecting only the three.
        LocalDate today = date("2026-06-01");
        Run current = run(today, "Cedar Trail", 3.0, 27.0,
                EnergyLevel.LOW, EnergyLevel.HIGH, null);
        Run sameRouteA = run(today.minusDays(1), "Cedar Trail", 6.0, 54.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run sameRouteB = run(today.minusDays(2), "Cedar Trail", 7.0, 63.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run sameRouteC = run(today.minusDays(3), "Cedar Trail", 8.0, 72.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run otherRouteSameDistanceA = run(today.minusDays(4), "River Loop", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        Run otherRouteSameDistanceB = run(today.minusDays(5), "River Loop", 3.0, 27.0,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE, null);
        List<Run> history = List.of(sameRouteA, sameRouteB, sameRouteC,
                otherRouteSameDistanceA, otherRouteSameDistanceB);

        // Act
        ComparisonInsight insight = ComparisonService.analyze(current, history);

        // Assert: exactly the three same-route runs, and the basis says so.
        assertEquals(3, insight.getComparableCount());
        assertEquals("same route", insight.getBasis());
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
        assertEquals(2, insight.getComparableCount());
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
        assertTrue(insight.getOutcomeLines().get(0).contains("PR"));
    }
}
