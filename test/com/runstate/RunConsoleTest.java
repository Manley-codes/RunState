package com.runstate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Scanner;

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

    // Supplies scripted console input and records every saved-run storage call.
    // The captured in-memory values at call time prove that persistence happens
    // before RunConsole mutates the selected Run.
    private static class TrackingManagementConsole extends RunConsole {
        private final Runner runner;

        int updateCalls = 0;
        int deleteCalls = 0;
        int capturedRunId = -1;
        EnergyLevel capturedEnergy;
        EffortLevel capturedEffort;
        EnergyLevel energyInMemoryAtUpdateCall;
        EffortLevel effortInMemoryAtUpdateCall;
        int historySizeAtDeleteCall = -1;
        boolean selectedPresentAtDeleteCall = false;
        boolean responseBuilt = false;

        boolean updateResult = true;
        boolean deleteResult = true;
        RunStorageException updateFailure;
        RunStorageException deleteFailure;

        TrackingManagementConsole(Runner runner, String input) {
            super(runner, new Scanner(input));
            this.runner = runner;
        }

        @Override
        boolean updateRunFeedback(
                int runId, EnergyLevel postRunEnergy, EffortLevel effortLevel)
                throws RunStorageException {
            updateCalls++;
            capturedRunId = runId;
            capturedEnergy = postRunEnergy;
            capturedEffort = effortLevel;

            Run storedRun = runner.findRunById(runId);
            if (storedRun != null) {
                energyInMemoryAtUpdateCall = storedRun.getPostRunEnergy();
                effortInMemoryAtUpdateCall = storedRun.getEffortLevel();
            }

            if (updateFailure != null) {
                throw updateFailure;
            }
            return updateResult;
        }

        @Override
        boolean deleteRun(int runId) throws RunStorageException {
            deleteCalls++;
            capturedRunId = runId;
            historySizeAtDeleteCall = runner.getRunHistory().size();
            selectedPresentAtDeleteCall = runner.findRunById(runId) != null;

            if (deleteFailure != null) {
                throw deleteFailure;
            }
            return deleteResult;
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

    private static Run makeSavedRun(
            Runner runner, int runId, LocalDate date,
            double distanceMiles, double durationMin,
            EnergyLevel postRunEnergy, EffortLevel effortLevel) {
        return new Run(runId, runner, date, null, null,
                distanceMiles, DistanceUnit.MILES, durationMin,
                "Test Route " + runId, null, EnergyLevel.MODERATE, postRunEnergy,
                RunContext.EMPTY, null, effortLevel);
    }

    private static String captureOutput(Runnable action) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(captured));
            action.run();
            return captured.toString();
        } finally {
            System.setOut(original);
        }
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    // --- Test -------------------------------------------------------------------

    @Test
    void manageRunHistory_displaysRealRunIdsAndZeroReturnsToMainMenu() {
        TrackingRunner runner = new TrackingRunner();
        runner.loadRun(makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST));
        TrackingManagementConsole console = new TrackingManagementConsole(runner, "0\n");

        boolean[] result = new boolean[1];
        String output = captureOutput(() -> result[0] = console.manageRunHistory());

        assertTrue(result[0]);
        assertTrue(output.contains("Run ID: 73"));
        assertTrue(output.contains("0. Back to Main Menu"));
        assertEquals(0, console.updateCalls);
        assertEquals(0, console.deleteCalls);
    }

    @Test
    void manageRunHistory_whenHistoryIsEmpty_returnsWithoutReadingInput() {
        TrackingRunner runner = new TrackingRunner();
        TrackingManagementConsole console = new TrackingManagementConsole(runner, "");

        boolean[] result = new boolean[1];
        String output = captureOutput(() -> result[0] = console.manageRunHistory());

        assertTrue(result[0]);
        assertTrue(output.contains("has no runs yet"));
        assertEquals(0, console.updateCalls);
        assertEquals(0, console.deleteCalls);
    }

    @Test
    void manageRunHistory_invalidRunIdNeverCallsStorageAndAllowsAnotherSelection() {
        TrackingRunner runner = new TrackingRunner();
        runner.loadRun(makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, null, null));
        TrackingManagementConsole console = new TrackingManagementConsole(runner, "999\n0\n");

        boolean[] result = new boolean[1];
        String output = captureOutput(() -> result[0] = console.manageRunHistory());

        assertTrue(result[0]);
        assertTrue(output.contains("No saved run has Run ID 999"));
        assertEquals(0, console.updateCalls);
        assertEquals(0, console.deleteCalls);
        assertEquals(1, runner.getRunHistory().size());
    }

    @Test
    void manageRunHistory_energyUpdatePersistsFirstPreservesEffortAndRemainsSelected() {
        TrackingRunner runner = new TrackingRunner();
        Run run = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.LOW, EffortLevel.HIGH_COST);
        runner.loadRun(run);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n1\n4\n4\n0\n");

        boolean[] result = new boolean[1];
        String output = captureOutput(() -> result[0] = console.manageRunHistory());

        assertTrue(result[0]);
        assertEquals(1, console.updateCalls);
        assertEquals(73, console.capturedRunId);
        assertEquals(EnergyLevel.HIGH, console.capturedEnergy);
        assertEquals(EffortLevel.HIGH_COST, console.capturedEffort);
        assertEquals(EnergyLevel.LOW, console.energyInMemoryAtUpdateCall);
        assertEquals(EffortLevel.HIGH_COST, console.effortInMemoryAtUpdateCall);
        assertEquals(EnergyLevel.HIGH, run.getPostRunEnergy());
        assertEquals(EffortLevel.HIGH_COST, run.getEffortLevel());
        assertTrue(countOccurrences(output, "4. Back to Run History") >= 2);
        assertTrue(output.contains("Powered Up"));
    }

    @Test
    void manageRunHistory_effortUpdatePersistsFirstPreservesEnergyAndRemainsSelected() {
        TrackingRunner runner = new TrackingRunner();
        Run run = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.MODERATE, EffortLevel.LOW_COST);
        runner.loadRun(run);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n2\n5\n4\n0\n");

        boolean[] result = new boolean[1];
        String output = captureOutput(() -> result[0] = console.manageRunHistory());

        assertTrue(result[0]);
        assertEquals(1, console.updateCalls);
        assertEquals(73, console.capturedRunId);
        assertEquals(EnergyLevel.MODERATE, console.capturedEnergy);
        assertEquals(EffortLevel.MAX_COST, console.capturedEffort);
        assertEquals(EnergyLevel.MODERATE, console.energyInMemoryAtUpdateCall);
        assertEquals(EffortLevel.LOW_COST, console.effortInMemoryAtUpdateCall);
        assertEquals(EnergyLevel.MODERATE, run.getPostRunEnergy());
        assertEquals(EffortLevel.MAX_COST, run.getEffortLevel());
        assertTrue(countOccurrences(output, "4. Back to Run History") >= 2);
        assertTrue(output.contains("Empty tank"));
    }

    @Test
    void manageRunHistory_clearEnergyWritesNullAndPreservesEffort() {
        TrackingRunner runner = new TrackingRunner();
        Run run = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.HIGH, EffortLevel.MODERATE_COST);
        runner.loadRun(run);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n1\n1\n4\n0\n");

        captureOutput(() -> assertTrue(console.manageRunHistory()));

        assertEquals(1, console.updateCalls);
        assertNull(console.capturedEnergy);
        assertEquals(EffortLevel.MODERATE_COST, console.capturedEffort);
        assertNull(run.getPostRunEnergy());
        assertEquals(EffortLevel.MODERATE_COST, run.getEffortLevel());
    }

    @Test
    void manageRunHistory_clearEffortWritesNullAndPreservesEnergy() {
        TrackingRunner runner = new TrackingRunner();
        Run run = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.LOW, EffortLevel.MAX_COST);
        runner.loadRun(run);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n2\n1\n4\n0\n");

        captureOutput(() -> assertTrue(console.manageRunHistory()));

        assertEquals(1, console.updateCalls);
        assertEquals(EnergyLevel.LOW, console.capturedEnergy);
        assertNull(console.capturedEffort);
        assertEquals(EnergyLevel.LOW, run.getPostRunEnergy());
        assertNull(run.getEffortLevel());
    }

    @Test
    void manageRunHistory_cancelingEnergyAndEffortMakesNoStorageCallOrMemoryChange() {
        TrackingRunner runner = new TrackingRunner();
        Run run = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.LOW, EffortLevel.HIGH_COST);
        runner.loadRun(run);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n1\n5\n2\n6\n4\n0\n");

        captureOutput(() -> assertTrue(console.manageRunHistory()));

        assertEquals(0, console.updateCalls);
        assertEquals(0, console.deleteCalls);
        assertEquals(EnergyLevel.LOW, run.getPostRunEnergy());
        assertEquals(EffortLevel.HIGH_COST, run.getEffortLevel());
    }

    @Test
    void manageRunHistory_cancelingDeleteMakesNoStorageCallOrMemoryChange() {
        TrackingRunner runner = new TrackingRunner();
        Run run = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.LOW, EffortLevel.HIGH_COST);
        runner.loadRun(run);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n3\n2\n4\n0\n");

        captureOutput(() -> assertTrue(console.manageRunHistory()));

        assertEquals(0, console.deleteCalls);
        assertSame(run, runner.findRunById(73));
        assertEquals(1, runner.getRunHistory().size());
    }

    @Test
    void manageRunHistory_successfulDeletePersistsFirstAndRemovesOnlySelectedRun() {
        TrackingRunner runner = new TrackingRunner();
        Run selected = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.LOW, EffortLevel.HIGH_COST);
        Run remaining = makeSavedRun(
                runner, 74, LocalDate.of(2026, 8, 21),
                4.0, 34.0, EnergyLevel.HIGH, EffortLevel.MODERATE_COST);
        runner.loadRun(selected);
        runner.loadRun(remaining);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n3\n1\n0\n");

        captureOutput(() -> assertTrue(console.manageRunHistory()));

        assertEquals(1, console.deleteCalls);
        assertEquals(73, console.capturedRunId);
        assertEquals(2, console.historySizeAtDeleteCall);
        assertTrue(console.selectedPresentAtDeleteCall);
        assertNull(runner.findRunById(73));
        assertSame(remaining, runner.findRunById(74));
        assertEquals(1, runner.getRunHistory().size());
    }

    @Test
    void manageRunHistory_missingDatabaseIdOnUpdateLeavesMemoryUnchangedAndEndsManagement() {
        TrackingRunner runner = new TrackingRunner();
        Run run = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.LOW, EffortLevel.HIGH_COST);
        runner.loadRun(run);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n1\n4\n");
        console.updateResult = false;

        boolean[] result = new boolean[1];
        String output = captureOutput(() -> result[0] = console.manageRunHistory());

        assertFalse(result[0]);
        assertEquals(EnergyLevel.LOW, run.getPostRunEnergy());
        assertEquals(EffortLevel.HIGH_COST, run.getEffortLevel());
        assertSame(run, runner.findRunById(73));
        assertTrue(output.contains("no longer exists in MySQL"));
        assertTrue(output.contains("Run History may be stale"));
        assertTrue(output.contains("Restart RunState"));
    }

    @Test
    void manageRunHistory_updateStorageExceptionLeavesMemoryUnchangedAndPrintsCause() {
        TrackingRunner runner = new TrackingRunner();
        Run run = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.LOW, EffortLevel.HIGH_COST);
        runner.loadRun(run);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n2\n5\n");
        console.updateFailure = new RunStorageException(
                "simulated update failure", new SQLException("update low-level cause"));

        boolean[] result = new boolean[1];
        String output = captureOutput(() -> result[0] = console.manageRunHistory());

        assertFalse(result[0]);
        assertEquals(EnergyLevel.LOW, run.getPostRunEnergy());
        assertEquals(EffortLevel.HIGH_COST, run.getEffortLevel());
        assertSame(run, runner.findRunById(73));
        assertTrue(output.contains("Run History may be stale"));
        assertTrue(output.contains("Restart RunState"));
        assertTrue(output.contains("Details: update low-level cause"));
    }

    @Test
    void manageRunHistory_missingDatabaseIdOnDeleteLeavesMemoryUnchangedAndEndsManagement() {
        TrackingRunner runner = new TrackingRunner();
        Run run = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.LOW, EffortLevel.HIGH_COST);
        runner.loadRun(run);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n3\n1\n");
        console.deleteResult = false;

        boolean[] result = new boolean[1];
        String output = captureOutput(() -> result[0] = console.manageRunHistory());

        assertFalse(result[0]);
        assertSame(run, runner.findRunById(73));
        assertEquals(1, runner.getRunHistory().size());
        assertTrue(output.contains("no longer exists in MySQL"));
        assertTrue(output.contains("Run History may be stale"));
        assertTrue(output.contains("Restart RunState"));
    }

    @Test
    void manageRunHistory_deleteStorageExceptionLeavesMemoryUnchangedAndPrintsCause() {
        TrackingRunner runner = new TrackingRunner();
        Run run = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.LOW, EffortLevel.HIGH_COST);
        runner.loadRun(run);
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "73\n3\n1\n");
        console.deleteFailure = new RunStorageException(
                "simulated delete failure", new SQLException("delete low-level cause"));

        boolean[] result = new boolean[1];
        String output = captureOutput(() -> result[0] = console.manageRunHistory());

        assertFalse(result[0]);
        assertSame(run, runner.findRunById(73));
        assertEquals(1, runner.getRunHistory().size());
        assertTrue(output.contains("Run History may be stale"));
        assertTrue(output.contains("Restart RunState"));
        assertTrue(output.contains("Details: delete low-level cause"));
    }

    @Test
    void manageRunHistory_updateAndDeleteNeverInvokeAiPrAnnouncementsOrRunStyle() {
        TrackingRunner runner = new TrackingRunner();
        Run baseline = makeSavedRun(
                runner, 72, LocalDate.of(2026, 8, 19),
                3.0, 30.0, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST);
        Run selected = makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                5.0, 40.0, EnergyLevel.LOW, EffortLevel.HIGH_COST);
        runner.loadRun(baseline);
        runner.loadRun(selected);
        TrackingManagementConsole console = new TrackingManagementConsole(
                runner, "73\n1\n4\n2\n5\n3\n1\n0\n");

        String output = captureOutput(() -> assertTrue(console.manageRunHistory()));

        assertEquals(2, console.updateCalls);
        assertEquals(1, console.deleteCalls);
        assertFalse(console.responseBuilt);
        assertFalse(runner.addRunCalled);
        assertFalse(runner.detectRunStyleCalled);
        assertFalse(output.contains("New longest distance PR!"));
        assertFalse(output.contains("New fastest average pace PR!"));
        assertFalse(output.contains("response-sentinel"));
        assertFalse(output.contains("RunStyle-sentinel"));
    }

    @Test
    void start_whenHistoryStorageTrustFails_endsConsoleSession() {
        TrackingRunner runner = new TrackingRunner();
        runner.loadRun(makeSavedRun(
                runner, 73, LocalDate.of(2026, 8, 20),
                3.0, 27.0, EnergyLevel.LOW, EffortLevel.HIGH_COST));
        // 4 skips opening Energy, 2 opens History, then the update reports a missing
        // database ID. The trailing 3/4 would run only if start() incorrectly continued.
        TrackingManagementConsole console =
                new TrackingManagementConsole(runner, "4\n2\n73\n1\n4\n3\n4\n");
        console.updateResult = false;

        String output = captureOutput(console::start);

        assertFalse(output.contains("Current Personal Records for"));
        assertFalse(output.contains("See you after your next run."));
        assertTrue(output.contains("Restart RunState"));
    }

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
