package com.runstate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RunConsoleTest {

    // --- Test doubles -----------------------------------------------------------

    // Intercepts addRun and detectRunStyle so the test can assert they were or
    // were not called, without needing MySQL or the Anthropic API.
    private static class TrackingRunner extends Runner {
        boolean addRunCalled = false;
        boolean detectRunStyleCalled = false;

        TrackingRunner() {
            super(1, "tester", "Test", "Runner", "Houston", "TX", "test@test.com");
        }

        @Override
        public void addRun(Run run) {
            addRunCalled = true;
            super.addRun(run);
        }

        @Override
        public String detectRunStyle(Run run) {
            detectRunStyleCalled = true;
            return "RunStyle-sentinel";
        }
    }

    // Overrides the two package-private delegates so saveAndCompleteRun() can
    // be called without touching MySQL or the Anthropic API. saveRun() always
    // throws; buildRunResponse() returns a sentinel string.
    private static class FailingSaveConsole extends RunConsole {
        boolean saveAttempted = false;
        boolean responseBuilt = false;

        FailingSaveConsole(Runner runner) {
            super(runner);
        }

        @Override
        void saveRun(Run run) throws RunStorageException {
            saveAttempted = true;
            throw new RunStorageException(
                    "simulated save failure",
                    new SQLException("simulated save failure"));
        }

        @Override
        String buildRunResponse(Run run) {
            responseBuilt = true;
            return "response-sentinel";
        }
    }

    // --- Helper -----------------------------------------------------------------

    private static Run makeRun(Runner runner, LocalDate date,
                               double distanceMiles, double durationMin) {
        return makeRun(runner, date, distanceMiles, durationMin, null);
    }

    private static Run makeRun(Runner runner, LocalDate date,
                               double distanceMiles, double durationMin, RunContext context) {
        return new Run(0, runner, date, null, null,
                distanceMiles, DistanceUnit.MILES, durationMin,
                null, null, null, null, context, null, null);
    }

    // --- Test -------------------------------------------------------------------

    // When the durable save fails, every post-save step must be suppressed:
    // in-memory history stays clean, no PR flags set, no AI response printed,
    // no RunStyle check triggered, and the recovery receipt is shown.
    @Test
    void saveAndCompleteRun_whenSaveFails_suppressesAllPostSaveWork() {
        TrackingRunner runner = new TrackingRunner();

        // Baseline silently loaded — no PR announcements, no RunStyle.
        // The unsaved run is farther and faster, so it WOULD set both PR flags
        // if addRun were incorrectly called after a failed save.
        Run baseline = makeRun(runner, LocalDate.of(2026, 1, 1), 3.0, 27.0); // 9 min/mi
        runner.loadRun(baseline);

        RunContext ctx = new RunContext(SurfaceType.TRAIL, RunCompany.SOLO,
                "Pegasus 41", MusicMode.MUSIC, "Midnight Marauders");
        Run unsaved = makeRun(runner, LocalDate.of(2026, 7, 25), 5.0, 35.0, ctx); // 7 min/mi

        FailingSaveConsole console = new FailingSaveConsole(runner);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(captured));
            boolean result = console.saveAndCompleteRun(unsaved);
            String output = captured.toString();

            // Method itself reports failure.
            assertFalse(result);

            // The save delegate was invoked (proof the try block ran).
            assertTrue(console.saveAttempted);

            // In-memory history was NOT modified — only the baseline is present.
            assertFalse(runner.addRunCalled);
            assertEquals(1, runner.getRunHistory().size());

            // PR flags were never set because addRun never ran.
            assertFalse(unsaved.isLongestDistanceRecord());
            assertFalse(unsaved.isFastestAveragePaceRecord());

            // No PR announcement line.
            assertFalse(output.contains("PR!"));

            // AI response was not built and its sentinel is absent from output.
            assertFalse(console.responseBuilt);
            assertFalse(output.contains("response-sentinel"));

            // RunStyle was not checked and its sentinel is absent from output.
            assertFalse(runner.detectRunStyleCalled);
            assertFalse(output.contains("RunStyle-sentinel"));

            // The recovery receipt was printed with all required lines.
            assertTrue(output.contains("could not confirm"));
            assertTrue(output.contains("check Run History"));
            assertTrue(output.contains("Re-enter"));
            assertTrue(output.contains("simulated save failure"));

            // Recorded context appears in the recovery receipt via getRunSummary().
            assertTrue(output.contains(
                    "Context: Trail | Solo | Shoes: Pegasus 41 | Music: Midnight Marauders"));
        } finally {
            System.setOut(original);
        }
    }
}
