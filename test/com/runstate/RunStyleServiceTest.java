package com.runstate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Step 3 (RunStyle V1) tests for RunStyleService.analyze. These build small histories
 * and read the returned RunStyleInsight's package-private accessors (same package).
 *
 * Most stage tests use the State Lift family because it is INTRINSIC — a run supports it
 * whenever post > pre, with no dependence on other runs — so the stage thresholds can be
 * hit exactly. Efficiency Gain (which needs the strict comparison) gets its own test.
 * Runs used for State Lift leave effort null, which keeps the other two families from
 * forming and isolates the family under test.
 */
public class RunStyleServiceTest {

    private static final LocalDate BASE = LocalDate.parse("2026-01-01");

    // Full builder. Most tests use the thin wrappers below and ignore the rest.
    private static Run run(LocalDate date, String route, double distanceMiles, double durationMin,
                           EnergyLevel pre, EnergyLevel post, EffortLevel effort,
                           SurfaceType surface, RunCompany company, String shoe, MusicMode music,
                           WeatherData weather) {
        RunContext context = new RunContext(surface, company, shoe, music, null);
        return new Run(0, null, date, null, null,
                distanceMiles, DistanceUnit.MILES, durationMin,
                route, null, pre, post, context, weather, effort);
    }

    // A State-Lift-only run: no effort, no route, no context.
    private static Run lift(LocalDate date, EnergyLevel pre, EnergyLevel post) {
        return run(date, null, 3.0, 27.0, pre, post, null, null, null, null, null, null);
    }

    // A State-Lift run that also carries a surface (for facet/habit tests).
    private static Run liftSurface(LocalDate date, EnergyLevel pre, EnergyLevel post, SurfaceType surface) {
        return run(date, null, 3.0, 27.0, pre, post, null, surface, null, null, null, null);
    }

    // Convenience: supported lift (LOW -> HIGH) vs flat (MODERATE -> MODERATE).
    private static Run supportedLift(LocalDate date) {
        return lift(date, EnergyLevel.LOW, EnergyLevel.HIGH);
    }

    private static Run flatLift(LocalDate date) {
        return lift(date, EnergyLevel.MODERATE, EnergyLevel.MODERATE);
    }

    private static LocalDate day(int i) {
        return BASE.plusDays(i);
    }

    // --- Stage thresholds (State Lift) ---------------------------------------

    @Test
    void stateLiftReachesEarlyAtExactThreshold() {
        // 4 opportunities, latest 4 have 3 supported -> EARLY (3 of 4).
        List<Run> history = new ArrayList<>(List.of(
                flatLift(day(1)),
                supportedLift(day(2)),
                supportedLift(day(3)),
                supportedLift(day(4))));
        Run current = history.get(history.size() - 1);

        RunStyleInsight insight = RunStyleService.analyze(current, history);

        assertEquals(RunStyleFamily.STATE_LIFT, insight.getPrimary());
        assertEquals(RunStyleStage.EARLY, insight.getStage());
        assertEquals(3, insight.getSupportCount());
        assertEquals(4, insight.getOpportunityCount());
        assertTrue(insight.shouldAnnounce());  // first time it reaches EARLY
    }

    @Test
    void stateLiftReachesFormingAtExactThreshold() {
        // 5 opportunities, latest 5 have 4 supported -> FORMING (4 of 5), not ESTABLISHED.
        List<Run> history = new ArrayList<>(List.of(
                flatLift(day(1)),
                supportedLift(day(2)),
                supportedLift(day(3)),
                supportedLift(day(4)),
                supportedLift(day(5))));
        Run current = history.get(history.size() - 1);

        RunStyleInsight insight = RunStyleService.analyze(current, history);

        assertEquals(RunStyleStage.FORMING, insight.getStage());
        assertEquals(RunStyleFamily.STATE_LIFT, insight.getPrimary());
    }

    @Test
    void stateLiftReachesEstablishedAtExactThreshold() {
        // 7 opportunities, latest 7 have 6 supported -> ESTABLISHED (6 of 7).
        List<Run> history = new ArrayList<>(List.of(
                flatLift(day(1)),
                supportedLift(day(2)),
                supportedLift(day(3)),
                supportedLift(day(4)),
                supportedLift(day(5)),
                supportedLift(day(6)),
                supportedLift(day(7))));
        Run current = history.get(history.size() - 1);

        RunStyleInsight insight = RunStyleService.analyze(current, history);

        assertEquals(RunStyleStage.ESTABLISHED, insight.getStage());
    }

    @Test
    void belowEarlyThresholdIsNoPattern() {
        // 4 opportunities but only 2 supported -> no stage -> NONE.
        List<Run> history = new ArrayList<>(List.of(
                supportedLift(day(1)),
                supportedLift(day(2)),
                flatLift(day(3)),
                flatLift(day(4))));
        Run current = history.get(history.size() - 1);

        RunStyleInsight insight = RunStyleService.analyze(current, history);

        assertNull(insight.getPrimary());
        assertFalse(insight.shouldAnnounce());
    }

    // --- Announcement transitions --------------------------------------------

    @Test
    void announcesWhenPatternFirstReachesEarlyButNotAgainWhenItMerelyHolds() {
        // r1 flat, r2..r4 supported -> EARLY created on r4 (announce).
        List<Run> throughR4 = new ArrayList<>(List.of(
                flatLift(day(1)),
                supportedLift(day(2)),
                supportedLift(day(3)),
                supportedLift(day(4))));
        RunStyleInsight atR4 = RunStyleService.analyze(throughR4.get(3), throughR4);
        assertTrue(atR4.shouldAnnounce());
        assertEquals(RunStyleStage.EARLY, atR4.getStage());

        // r5 flat: still EARLY (latest 4 = r2,r3,r4,r5 -> 3 supported) but no advance.
        List<Run> throughR5 = new ArrayList<>(throughR4);
        throughR5.add(flatLift(day(5)));
        RunStyleInsight atR5 = RunStyleService.analyze(throughR5.get(4), throughR5);
        assertEquals(RunStyleStage.EARLY, atR5.getStage());
        assertFalse(atR5.shouldAnnounce());  // held, did not advance -> no repeat
    }

    // --- Point-in-time safety ------------------------------------------------

    @Test
    void aLaterRunDoesNotChangeAnEarlierPatternsCounts() {
        // Establish an EARLY State Lift (3 of 4 opportunities), then append a LATER run that
        // IS also a supported State Lift opportunity. Without point-in-time protection the
        // stage would advance to FORMING (4 of 5). With it, the earlier run's profile is
        // computed only from history through its own chronological position.
        List<Run> history = new ArrayList<>(List.of(
                flatLift(day(1)),
                supportedLift(day(2)),
                supportedLift(day(3)),
                supportedLift(day(4))));
        RunStyleInsight before = RunStyleService.analyze(history.get(3), history);
        assertEquals(RunStyleStage.EARLY, before.getStage());
        assertEquals(3, before.getSupportCount());
        assertEquals(4, before.getOpportunityCount());
        assertTrue(before.shouldAnnounce());  // advanced to EARLY — eligible as latest run

        List<Run> withLater = new ArrayList<>(history);
        withLater.add(supportedLift(day(5)));  // later supported opportunity — must not rewire past
        RunStyleInsight after = RunStyleService.analyze(history.get(3), withLater);

        assertEquals(RunStyleStage.EARLY, after.getStage());       // still EARLY, not FORMING
        assertEquals(3, after.getSupportCount());                  // still 3 supported
        assertEquals(4, after.getOpportunityCount());              // still 4 opportunities
        assertEquals(RunStyleFamily.STATE_LIFT, after.getPrimary());
    }

    // --- Backdated announcement policy (Task 3) ------------------------------

    @Test
    void backdatedRunHasHistoricalStageButDoesNotAnnounce() {
        // r1 flat, r2–r4 supported, r5 is added LATER chronologically.
        // analyze(r4, {r1..r5}): r4 is at index 3, not last (r5 is last) -> not eligible.
        // But r4's historical profile through its own position: 3 of 4 = EARLY.
        List<Run> history = new ArrayList<>(List.of(
                flatLift(day(1)),
                supportedLift(day(2)),
                supportedLift(day(3)),
                supportedLift(day(4)),   // the "backdated" run under test
                supportedLift(day(5)))); // genuinely later run
        Run backdated = history.get(3);  // day 4, at index 3 of 4

        RunStyleInsight insight = RunStyleService.analyze(backdated, history);

        assertEquals(RunStyleFamily.STATE_LIFT, insight.getPrimary());
        assertEquals(RunStyleStage.EARLY, insight.getStage());
        assertEquals(3, insight.getSupportCount());
        assertEquals(4, insight.getOpportunityCount());
        assertFalse(insight.shouldAnnounce());  // backdated: eligible=false, no alert
        assertNull(insight.getMessage());
    }

    @Test
    void backdatedFacetAppearsInInsightButDoesNotAnnounce() {
        // r1 flat/trail, r2–r4 supported/trail, r5 is genuinely later.
        // At r4's position the trail facet is newly qualified (3 supporting runs).
        // The facet appears in the structured result, but no announcement is emitted.
        List<Run> history = new ArrayList<>(List.of(
                liftSurface(day(1), EnergyLevel.MODERATE, EnergyLevel.MODERATE, SurfaceType.TRAIL),
                liftSurface(day(2), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL),
                liftSurface(day(3), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL),
                liftSurface(day(4), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL),
                liftSurface(day(5), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL)));
        Run backdated = history.get(3);  // day 4 — not last

        RunStyleInsight insight = RunStyleService.analyze(backdated, history);

        assertFalse(insight.shouldAnnounce());
        assertNull(insight.getMessage());
        // Historical facet is still present in the structured result.
        assertTrue(insight.getFacetLines().stream()
                .anyMatch(l -> l.toLowerCase().contains("trail")));
    }

    @Test
    void nextLatestRunAfterBackfillTreatsBackfillAsPartOfItsBaseline() {
        // Build a 5-run history: r1 flat, r2–r4 supported, r5 supported.
        // analyze(r4) -> backdated, no announcement.
        // analyze(r5) -> r5 is the genuine latest. With r4 counted, 4 of the latest 5 are
        // supported -> FORMING. r5 advances from EARLY and correctly announces.
        List<Run> history = new ArrayList<>(List.of(
                flatLift(day(1)),
                supportedLift(day(2)),
                supportedLift(day(3)),
                supportedLift(day(4)),   // "backdated" run
                supportedLift(day(5)))); // genuinely latest
        Run backdated = history.get(3);
        Run latest    = history.get(4);

        RunStyleInsight backdatedInsight = RunStyleService.analyze(backdated, history);
        assertFalse(backdatedInsight.shouldAnnounce());  // backdated: suppressed

        RunStyleInsight latestInsight = RunStyleService.analyze(latest, history);
        assertEquals(RunStyleStage.FORMING, latestInsight.getStage());  // 4 of 5
        assertTrue(latestInsight.shouldAnnounce());  // genuine advancement
    }

    @Test
    void sameDayAppendedRunRemainsEligibleToAnnounce() {
        // r1 flat (day 1), r2 supported (day 2), r3 supported (day 4), r4 supported (day 4).
        // r3 and r4 share the same date. Stable sort preserves input order so r4 stays last.
        // r4 is the same-day newly-appended run — eligible to announce the EARLY stage.
        List<Run> history = new ArrayList<>(List.of(
                flatLift(day(1)),
                supportedLift(day(2)),
                supportedLift(day(4)),   // r3: same date as r4, entered first
                supportedLift(day(4)))); // r4: same date, appended last
        Run sameDayRun = history.get(3); // the one appended last

        RunStyleInsight insight = RunStyleService.analyze(sameDayRun, history);

        assertEquals(RunStyleStage.EARLY, insight.getStage());
        assertTrue(insight.shouldAnnounce());  // appended last -> eligible
    }

    // --- Efficiency Gain can be primary --------------------------------------

    @Test
    void efficiencyGainBecomesPrimaryWhenPaceImprovesAtEqualEffort() {
        // Same route + distance, same MODERATE_COST effort, pace improving each run so each
        // is faster than the median of its predecessors -> Same-Cost/Better fires. Energy is
        // MODERATE -> LOW so State Lift and Controlled Finish stay silent, isolating Efficiency.
        double[] paces = {10.0, 9.0, 8.0, 7.0, 6.0};
        List<Run> history = new ArrayList<>();
        for (int i = 0; i < paces.length; i++) {
            history.add(run(day(i + 1), "Loop", 3.0, paces[i] * 3.0,
                    EnergyLevel.MODERATE, EnergyLevel.LOW, EffortLevel.MODERATE_COST,
                    null, null, null, null, null));
        }
        Run current = history.get(history.size() - 1);

        RunStyleInsight insight = RunStyleService.analyze(current, history);

        assertEquals(RunStyleFamily.EFFICIENCY_GAIN, insight.getPrimary());
        assertEquals(RunStyleStage.EARLY, insight.getStage());
        assertTrue(insight.shouldAnnounce());
    }

    // --- PR / Demand Explained never create a pattern ------------------------

    @Test
    void demandExplainedAloneIsNeverAPrimaryPattern() {
        // Spent finishes (post LOW) so Controlled Finish never fires; no pace/effort
        // improvement so Efficiency never fires; a PR + higher final effort is pure Demand
        // Explained. Demand Explained is not a family, so there must be no primary pattern.
        List<Run> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            EffortLevel effort = i == 4 ? EffortLevel.HIGH_COST : EffortLevel.LOW_COST;
            Run r = run(day(i + 1), "Loop", 3.0, 27.0,
                    EnergyLevel.MODERATE, EnergyLevel.LOW, effort,
                    null, null, null, null, null);
            history.add(r);
        }
        Run current = history.get(history.size() - 1);
        current.markFastestAveragePaceRecord();

        RunStyleInsight insight = RunStyleService.analyze(current, history);

        assertNull(insight.getPrimary());
        assertFalse(insight.shouldAnnounce());
    }

    // --- Facets --------------------------------------------------------------

    @Test
    void descriptiveFacetQualifiesAtThreeSupportingRuns() {
        // EARLY State Lift where all 3 supporting runs were on TRAIL -> descriptive surface facet.
        List<Run> history = new ArrayList<>(List.of(
                liftSurface(day(1), EnergyLevel.MODERATE, EnergyLevel.MODERATE, SurfaceType.TRAIL),
                liftSurface(day(2), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL),
                liftSurface(day(3), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL),
                liftSurface(day(4), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL)));
        Run current = history.get(history.size() - 1);

        RunStyleInsight insight = RunStyleService.analyze(current, history);

        assertEquals(1, insight.getFacetLines().size());
        assertTrue(insight.getFacetLines().get(0).toLowerCase().contains("trail"));
    }

    @Test
    void comparativeFacetEarnsContrastWordingAtTheFiveFiveEightyThirtyBar() {
        // 10 State Lift opportunities: 5 TRAIL all supported, 5 ROAD only 1 supported.
        // rate(with=TRAIL)=1.0 (>=0.80), lead over ROAD (0.20) = 0.80 (>=0.30) -> contrasted.
        // The ROAD runs go FIRST and the supported TRAIL runs last, so the recent window
        // still holds a stage (stages read the latest opportunities, not the lifetime rate).
        List<Run> history = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            history.add(liftSurface(day(i), EnergyLevel.MODERATE, EnergyLevel.MODERATE, SurfaceType.ROAD));
        }
        history.add(liftSurface(day(5), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.ROAD));
        for (int i = 6; i <= 10; i++) {
            history.add(liftSurface(day(i), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL));
        }
        Run current = history.get(history.size() - 1);

        RunStyleInsight insight = RunStyleService.analyze(current, history);

        assertEquals(1, insight.getFacetLines().size());
        assertTrue(insight.getFacetLines().get(0).contains("more on trail"));
    }

    @Test
    void atMostOneConditionAndOnePersonalFacet() {
        // Supporting runs all carry TRAIL + SOLO (two conditions) and shoes (one personal).
        // Surface must win the single condition slot over company; shoes fill the personal
        // slot -> exactly two lines, and no company line.
        List<Run> history = new ArrayList<>();
        history.add(run(day(1), null, 3.0, 27.0, EnergyLevel.MODERATE, EnergyLevel.MODERATE, null,
                SurfaceType.TRAIL, RunCompany.SOLO, "Pegasus 41", null, null));
        for (int i = 2; i <= 4; i++) {
            history.add(run(day(i), null, 3.0, 27.0, EnergyLevel.LOW, EnergyLevel.HIGH, null,
                    SurfaceType.TRAIL, RunCompany.SOLO, "Pegasus 41", null, null));
        }
        Run current = history.get(history.size() - 1);

        RunStyleInsight insight = RunStyleService.analyze(current, history);

        List<String> facets = insight.getFacetLines();
        assertEquals(2, facets.size());
        String joined = String.join(" | ", facets).toLowerCase();
        assertTrue(joined.contains("trail"));
        assertTrue(joined.contains("pegasus"));
        assertFalse(joined.contains("solo"));  // company lost the single condition slot to surface
    }

    // --- Habit ---------------------------------------------------------------

    @Test
    void habitLineAppearsWhenAContextDominatesRecentRuns() {
        // Established State Lift with every run on TRAIL -> surface recorded on all of the
        // last 10 and 100% TRAIL, so the habit line is earned.
        List<Run> history = new ArrayList<>(List.of(
                liftSurface(day(1), EnergyLevel.MODERATE, EnergyLevel.MODERATE, SurfaceType.TRAIL),
                liftSurface(day(2), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL),
                liftSurface(day(3), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL),
                liftSurface(day(4), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL),
                liftSurface(day(5), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL),
                liftSurface(day(6), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL),
                liftSurface(day(7), EnergyLevel.LOW, EnergyLevel.HIGH, SurfaceType.TRAIL)));
        Run current = history.get(history.size() - 1);

        RunStyleInsight insight = RunStyleService.analyze(current, history);

        assertTrue(insight.getHabitLine() != null);
        assertTrue(insight.getHabitLine().toLowerCase().contains("regular part"));
    }

    // --- Weather never changes confidence ------------------------------------

    @Test
    void weatherDoesNotChangeStageOrPrimaryOrSupport() {
        // Identical EARLY State Lift histories, one with warm weather on every run. Weather
        // is demand context only — stage, primary, and support count must be unchanged.
        WeatherData warm = new WeatherData(85.0, 85.0, "Hot");
        List<Run> plain = new ArrayList<>(List.of(
                flatLift(day(1)), supportedLift(day(2)), supportedLift(day(3)), supportedLift(day(4))));
        List<Run> warmHistory = new ArrayList<>(List.of(
                run(day(1), null, 3.0, 27.0, EnergyLevel.MODERATE, EnergyLevel.MODERATE, null, null, null, null, null, warm),
                run(day(2), null, 3.0, 27.0, EnergyLevel.LOW, EnergyLevel.HIGH, null, null, null, null, null, warm),
                run(day(3), null, 3.0, 27.0, EnergyLevel.LOW, EnergyLevel.HIGH, null, null, null, null, null, warm),
                run(day(4), null, 3.0, 27.0, EnergyLevel.LOW, EnergyLevel.HIGH, null, null, null, null, null, warm)));

        RunStyleInsight plainInsight = RunStyleService.analyze(plain.get(3), plain);
        RunStyleInsight warmInsight = RunStyleService.analyze(warmHistory.get(3), warmHistory);

        assertEquals(plainInsight.getStage(), warmInsight.getStage());
        assertEquals(plainInsight.getPrimary(), warmInsight.getPrimary());
        assertEquals(plainInsight.getSupportCount(), warmInsight.getSupportCount());
    }
}
