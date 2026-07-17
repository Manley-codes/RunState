package com.runstate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    // --- Fake ResultSet ------------------------------------------------------

    /*
     * A test-only ResultSet backed by ordered row maps.
     *
     * ResultSet is an interface with ~190 methods; implementing it normally would mean
     * writing ~190 method bodies to use five of them. Proxy builds an object at runtime
     * that claims to implement ResultSet and routes EVERY call to the handler below, so
     * we answer only what readRuns actually calls and let the rest return null.
     *
     * This is the second seam: FAILING_PROVIDER above simulates an unreachable database,
     * this simulates a reachable database holding bad data. Neither touches MySQL, and
     * no corrupt row is ever written to real history.
     */
    private static ResultSet fakeResultSet(List<Map<String, Object>> rows) {
        return (ResultSet) Proxy.newProxyInstance(
                RunStorageTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new InvocationHandler() {
                    // Starts at -1 because next() advances BEFORE the first read, exactly
                    // like a real JDBC cursor.
                    private int index = -1;

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "next":
                                index++;
                                return index < rows.size();
                            case "getString":
                                return (String) rows.get(index).get((String) args[0]);
                            case "getInt":
                                return (Integer) rows.get(index).get((String) args[0]);
                            case "getDouble":
                                return (Double) rows.get(index).get((String) args[0]);
                            case "getObject":
                                return rows.get(index).get((String) args[0]);
                            default:
                                return null;
                        }
                    }
                });
    }

    // One valid, fully populated row. Each test copies it and corrupts exactly one column,
    // so a failure can only be caused by the thing that test is about.
    private static Map<String, Object> validRow(int runId) {
        Map<String, Object> row = new HashMap<>();
        row.put("run_id", runId);
        row.put("run_date", LocalDate.of(2026, 7, 16));
        row.put("distance", 5.0);
        row.put("distance_unit", "MILES");
        row.put("duration", 40.0);
        row.put("route_name", "Memorial Park");
        row.put("pre_run_energy", "HIGH");
        row.put("post_run_energy", "MODERATE");
        row.put("effort_level", "LOW_COST");
        row.put("music_mode", "MUSIC");
        row.put("surface_type", "TRAIL");
        row.put("run_company", "SOLO");
        return row;
    }

    // --- Malformed stored rows -----------------------------------------------

    /*
     * @ParameterizedTest runs this ONE method once per @CsvSource row, so all seven enum
     * columns get identical coverage and a failure names the exact column that broke.
     *
     * The bad values are deliberately realistic corruption: wrong case ("high"), a
     * plausible-but-wrong constant ("PAVEMENT"), a typo ("MUSICC"). RunState must reject
     * every one rather than trim, re-case, or guess its way to an answer.
     */
    @ParameterizedTest
    @CsvSource({
            "distance_unit,   MILEZ",
            "pre_run_energy,  high",
            "post_run_energy, VERY_HIGH",
            "surface_type,    PAVEMENT",
            "run_company,     ALONE",
            "music_mode,      MUSICC",
            "effort_level,    CHEAP"
    })
    void readRuns_whenAnyEnumColumnHasInvalidValue_throwsRunStorageException(
            String column, String badValue) {
        Map<String, Object> row = validRow(1);
        row.put(column, badValue);

        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.readRuns(null, fakeResultSet(List.of(row))));

        // The row/column diagnostic must survive to the cause — this is the text that
        // reaches the "Details:" line at startup.
        assertTrue(thrown.getCause().getMessage()
                        .contains("Run 1 has invalid value \"" + badValue + "\" in " + column),
                "Unexpected diagnostic: " + thrown.getCause().getMessage());
    }

    @Test
    void readRuns_whenRequiredDistanceUnitIsMissing_throwsRunStorageException() {
        // distance_unit is the one required enum: a distance with no unit is meaningless,
        // so a null must fail rather than default to MILES or KILOMETERS.
        Map<String, Object> row = validRow(1);
        row.put("distance_unit", null);

        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.readRuns(null, fakeResultSet(List.of(row))));

        assertEquals("Run 1 is missing required value in distance_unit",
                thrown.getCause().getMessage());
    }

    @Test
    void readRuns_whenValidRowIsFollowedByMalformedRow_failsEntireLoad() {
        // Closes flow-audit item 2: proves a failure AFTER a row has already decoded
        // successfully still yields NO partial history — the whole load throws.
        Map<String, Object> good = validRow(1);
        Map<String, Object> bad = validRow(2);
        bad.put("effort_level", "CHEAP");

        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.readRuns(null, fakeResultSet(List.of(good, bad))));

        // Naming run 2 proves run 1 decoded fine first and was then discarded.
        assertTrue(thrown.getCause().getMessage().contains("Run 2"),
                "Expected the second row to be the failure: " + thrown.getCause().getMessage());
    }

    @Test
    void readRuns_whenLegacyRowHasOptionalNulls_loadsWithInferredMusicMode() throws Exception {
        // The safety net for strictness: a legitimate legacy row must still LOAD. Every
        // optional enum is null (skipped, or written before the column existed), and the
        // music note with no stored mode infers MUSIC per design_runstyle_v1.
        Map<String, Object> row = validRow(1);
        row.put("pre_run_energy", null);
        row.put("post_run_energy", null);
        row.put("surface_type", null);
        row.put("run_company", null);
        row.put("effort_level", null);
        row.put("music_mode", null);
        row.put("music_context", "Kanye");

        List<Run> runs = RunStorage.readRuns(null, fakeResultSet(List.of(row)));

        assertEquals(1, runs.size());
        assertNull(runs.get(0).getPreRunEnergy());
        assertNull(runs.get(0).getEffortLevel());
        assertEquals(MusicMode.MUSIC, runs.get(0).getMusicMode());
    }
}
