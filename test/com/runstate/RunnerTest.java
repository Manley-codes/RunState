package com.runstate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RunnerTest {

    private static Runner runner() {
        return new Runner(1, "tester", "Test", "Runner",
                "Houston", "TX", "tester@example.com");
    }

    private static Run run(int runId, Runner runner, LocalDate date,
                           double distanceMiles, double paceMinutesPerMile) {
        return new Run(runId, runner, date, null, null,
                distanceMiles, DistanceUnit.MILES,
                distanceMiles * paceMinutesPerMile,
                null, null, null, null, null, null, null);
    }

    @Test
    void findRunById_returnsTheStoredRunAndNullForAMissingId() {
        Runner runner = runner();
        Run first = run(101, runner, LocalDate.of(2026, 8, 1), 3.0, 10.0);
        Run second = run(305, runner, LocalDate.of(2026, 8, 2), 4.0, 9.0);
        runner.loadRun(first);
        runner.loadRun(second);

        assertSame(second, runner.findRunById(305));
        assertNull(runner.findRunById(999));
    }

    @Test
    void removeRunById_whenIdIsMissing_leavesHistoryUntouched() {
        Runner runner = runner();
        Run first = run(101, runner, LocalDate.of(2026, 8, 1), 3.0, 10.0);
        Run second = run(305, runner, LocalDate.of(2026, 8, 2), 4.0, 9.0);
        runner.loadRun(first);
        runner.loadRun(second);
        List<Run> before = runner.getRunHistory();

        assertFalse(runner.removeRunById(999));
        assertEquals(before, runner.getRunHistory());
        assertSame(first, runner.getRunHistory().get(0));
        assertSame(second, runner.getRunHistory().get(1));
    }

    @Test
    void removeRunById_removesOnlyTheSelectedRun() {
        Runner runner = runner();
        Run first = run(101, runner, LocalDate.of(2026, 8, 1), 3.0, 10.0);
        Run selected = run(202, runner, LocalDate.of(2026, 8, 2), 4.0, 9.0);
        Run last = run(303, runner, LocalDate.of(2026, 8, 3), 5.0, 8.0);
        runner.loadRun(first);
        runner.loadRun(selected);
        runner.loadRun(last);

        assertTrue(runner.removeRunById(202));

        List<Run> remaining = runner.getRunHistory();
        assertEquals(2, remaining.size());
        assertSame(first, remaining.get(0));
        assertSame(last, remaining.get(1));
        assertNull(runner.findRunById(202));
    }

    @Test
    void removeRunById_silentlyClearsAndRecomputesHistoricalPrFlags() {
        Runner runner = runner();
        Run first = run(1, runner, LocalDate.of(2026, 8, 1), 2.0, 12.0);
        Run second = run(2, runner, LocalDate.of(2026, 8, 2), 5.0, 8.0);
        Run third = run(3, runner, LocalDate.of(2026, 8, 3), 4.0, 7.0);
        runner.loadRun(first);
        runner.loadRun(second);
        runner.loadRun(third);

        assertTrue(second.isLongestDistanceRecord());
        assertTrue(second.isFastestAveragePaceRecord());
        assertFalse(third.isLongestDistanceRecord());
        assertTrue(third.isFastestAveragePaceRecord());

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        boolean removed;
        try {
            System.setOut(new PrintStream(captured));
            removed = runner.removeRunById(1);
        } finally {
            System.setOut(original);
        }

        assertTrue(removed);
        assertEquals("", captured.toString());
        assertFalse(second.isLongestDistanceRecord());
        assertFalse(second.isFastestAveragePaceRecord());
        assertFalse(third.isLongestDistanceRecord());
        assertTrue(third.isFastestAveragePaceRecord());
    }

    @Test
    void removeRunById_preservesSameDayOrderDuringPrRebuild() {
        Runner runner = runner();
        LocalDate sameDay = LocalDate.of(2026, 8, 10);
        Run sameDayFirst = run(10, runner, sameDay, 3.0, 10.0);
        Run laterDay = run(30, runner, sameDay.plusDays(1), 2.0, 11.0);
        Run sameDaySecond = run(20, runner, sameDay, 4.0, 9.0);

        runner.loadRun(sameDayFirst);
        runner.loadRun(laterDay);
        runner.loadRun(sameDaySecond);

        assertEquals(List.of(sameDayFirst, sameDaySecond, laterDay), runner.getRunHistory());
        assertTrue(runner.removeRunById(30));

        assertEquals(List.of(sameDayFirst, sameDaySecond), runner.getRunHistory());
        assertFalse(sameDayFirst.isLongestDistanceRecord());
        assertFalse(sameDayFirst.isFastestAveragePaceRecord());
        assertTrue(sameDaySecond.isLongestDistanceRecord());
        assertTrue(sameDaySecond.isFastestAveragePaceRecord());
    }

    @Test
    void displayRunHistory_showsRealRunIdsNewestFirst() {
        Runner runner = runner();
        runner.loadRun(run(101, runner, LocalDate.of(2026, 8, 1), 3.0, 10.0));
        runner.loadRun(run(909, runner, LocalDate.of(2026, 8, 2), 4.0, 9.0));

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(captured));
            runner.displayRunHistory();
        } finally {
            System.setOut(original);
        }

        String output = captured.toString();
        assertTrue(output.contains("Run ID: 101"));
        assertTrue(output.contains("Run ID: 909"));
        assertTrue(output.indexOf("Run ID: 909") < output.indexOf("Run ID: 101"));
    }
}
