package com.runstate;

import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

/*
 * Storage-failure tests for RunStorage.
 *
 * These exercise the "honest failure" contract added in this feature: when the
 * database cannot be reached, saveRun/loadRuns must THROW a RunStorageException
 * (preserving the underlying SQLException as the cause), never swallow the error
 * and return normally.
 *
 * The key that makes this testable without a live MySQL is the injectable seam:
 * both methods have a package-private overload that takes a ConnectionProvider.
 * Because this test lives in the same package (com.runstate), it can reach that
 * overload and hand in a provider that fails on purpose. No database is touched.
 */
public class RunStorageTest {

    // The seam in action: a connection provider that always fails, exactly as a dead
    // database would. This ONE SQLException object is what we expect to find preserved
    // as the cause, so we keep a reference to assert identity later.
    private static final SQLException SIMULATED_DB_ERROR =
            new SQLException("simulated connection failure");
    private static final RunStorage.ConnectionProvider FAILING_PROVIDER =
            () -> { throw SIMULATED_DB_ERROR; };

    // Builds a minimal but valid Run for the save tests. Its getters are never actually
    // reached — the connection fails before saveRun touches the run — so the exact values
    // don't matter; they just have to construct a legal Run object.
    private static Run sampleRun() {
        return new Run(
                1, null, LocalDate.now(),
                null, null,
                3.0, DistanceUnit.MILES, 24.0,
                "Test Loop", null,
                null, null,
                RunContext.EMPTY, null, null
        );
    }

    // --- Load failures -------------------------------------------------------

    @Test
    void loadRuns_whenConnectionFails_throwsRunStorageException() {
        // Arrange — the failing provider stands in for an unreachable database.
        // Runner is null because loadRuns never reaches the row-building loop that uses it.
        // Act + Assert — the call must raise our domain exception, not return quietly.
        assertThrows(RunStorageException.class,
                () -> RunStorage.loadRuns(null, FAILING_PROVIDER));
    }

    @Test
    void loadRuns_whenConnectionFails_neverReturnsEmptyOrPartialHistory() {
        // The dangerous silent-failure mode is returning a list (empty or partial) that
        // looks like "no runs yet" and corrupts PRs/RunStyle. Prove the method throws
        // instead of ever returning normally.
        boolean returnedNormally;
        try {
            RunStorage.loadRuns(null, FAILING_PROVIDER);
            returnedNormally = true;   // reached only if NO exception was thrown
        } catch (RunStorageException e) {
            returnedNormally = false;
        }
        assertFalse(returnedNormally,
                "loadRuns must throw on failure, never return an empty or partial history");
    }

    @Test
    void loadRuns_whenConnectionFails_preservesSqlExceptionAsCause() {
        // Act — capture the thrown exception so we can inspect its cause.
        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.loadRuns(null, FAILING_PROVIDER));
        // Assert — assertSame checks it is the very same SQLException object (chaining),
        // not just an equal one. That's what feeds the honest "Details:" line.
        assertSame(SIMULATED_DB_ERROR, thrown.getCause());
    }

    // --- Save failures -------------------------------------------------------

    @Test
    void saveRun_whenConnectionFails_throwsRunStorageException() {
        // Act + Assert — a run only counts once durably saved, so a failed save must
        // surface as an exception the caller (RunConsole) is forced to handle.
        assertThrows(RunStorageException.class,
                () -> RunStorage.saveRun(sampleRun(), FAILING_PROVIDER));
    }

    @Test
    void saveRun_whenConnectionFails_preservesSqlExceptionAsCause() {
        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.saveRun(sampleRun(), FAILING_PROVIDER));
        assertSame(SIMULATED_DB_ERROR, thrown.getCause());
    }
}
