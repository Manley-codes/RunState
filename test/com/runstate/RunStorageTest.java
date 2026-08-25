package com.runstate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
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
 * Storage behavior and failure tests for RunStorage.
 *
 * These exercise the "honest failure" contract added in this feature: when the
 * database cannot be reached, each storage operation must THROW a
 * RunStorageException (preserving the underlying SQLException as the cause),
 * never swallow the error and return normally. Generated IDs and affected-row
 * results are exercised through the same no-database boundary.
 *
 * The key that makes this testable without a live MySQL is the injectable seam:
 * the storage methods have package-private overloads that take a ConnectionProvider.
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

    /*
     * A recording JDBC test double for save/update/delete.
     *
     * Connection and PreparedStatement are large interfaces, so dynamic proxies let the
     * tests implement only the calls RunStorage actually makes. The recorded SQL and
     * parameter maps prove what would be sent to MySQL, while configured row counts and
     * SQLExceptions exercise success, missing-ID, and failure paths without a database.
     */
    private static final class RecordingJdbc {
        private final Map<Integer, Object> parameters = new HashMap<>();
        private final Map<Integer, Integer> nullSqlTypes = new HashMap<>();

        private String preparedSql;
        private Integer generatedKeysFlag;
        private int affectedRows = 1;
        private boolean generatedKeyAvailable = true;
        private long generatedKey = 42L;
        private SQLException executeFailure;
        private SQLException generatedKeyReadFailure;

        RunStorage.ConnectionProvider provider() {
            return this::connection;
        }

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    RunStorageTest.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "prepareStatement":
                                preparedSql = (String) args[0];
                                if (args.length == 2 && args[1] instanceof Integer) {
                                    generatedKeysFlag = (Integer) args[1];
                                }
                                return preparedStatement();
                            case "close":
                                return null;
                            default:
                                throw unexpectedJdbcCall(method);
                        }
                    });
        }

        private PreparedStatement preparedStatement() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    RunStorageTest.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "setString":
                            case "setObject":
                            case "setDouble":
                            case "setInt":
                                parameters.put((Integer) args[0], args[1]);
                                return null;
                            case "setNull":
                                parameters.put((Integer) args[0], null);
                                nullSqlTypes.put((Integer) args[0], (Integer) args[1]);
                                return null;
                            case "executeUpdate":
                                if (executeFailure != null) {
                                    throw executeFailure;
                                }
                                return affectedRows;
                            case "getGeneratedKeys":
                                return generatedKeys();
                            case "close":
                                return null;
                            default:
                                throw unexpectedJdbcCall(method);
                        }
                    });
        }

        private ResultSet generatedKeys() {
            return (ResultSet) Proxy.newProxyInstance(
                    RunStorageTest.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    new InvocationHandler() {
                        private boolean advanced;

                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args)
                                throws SQLException {
                            switch (method.getName()) {
                                case "next":
                                    if (advanced) {
                                        return false;
                                    }
                                    advanced = true;
                                    return generatedKeyAvailable;
                                case "getLong":
                                    if (generatedKeyReadFailure != null) {
                                        throw generatedKeyReadFailure;
                                    }
                                    return generatedKey;
                                case "wasNull":
                                    return false;
                                case "close":
                                    return null;
                                default:
                                    throw unexpectedJdbcCall(method);
                            }
                        }
                    });
        }

        private static UnsupportedOperationException unexpectedJdbcCall(Method method) {
            return new UnsupportedOperationException(
                    "Unexpected JDBC call in test double: " + method.getName());
        }
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

    // --- Save generated IDs and failures ------------------------------------

    @Test
    void saveRun_whenInsertSucceeds_assignsGeneratedIdAndRequestsGeneratedKeys()
            throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.generatedKey = 87L;
        Run run = sampleRun();

        RunStorage.saveRun(run, jdbc.provider());

        assertEquals(87, run.getRunId());
        assertEquals(Statement.RETURN_GENERATED_KEYS, jdbc.generatedKeysFlag);
        assertTrue(jdbc.preparedSql.startsWith("INSERT INTO runs"));
    }

    @Test
    void saveRun_whenGeneratedKeyIsMissing_throwsAndLeavesOriginalIdUnchanged() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.generatedKeyAvailable = false;
        Run run = sampleRun();

        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.saveRun(run, jdbc.provider()));

        assertEquals(1, run.getRunId());
        assertTrue(thrown.getCause() instanceof SQLException);
    }

    @Test
    void saveRun_whenGeneratedKeyCannotBeRead_preservesCauseAndLeavesOriginalIdUnchanged() {
        RecordingJdbc jdbc = new RecordingJdbc();
        SQLException keyFailure = new SQLException("simulated generated-key read failure");
        jdbc.generatedKeyReadFailure = keyFailure;
        Run run = sampleRun();

        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.saveRun(run, jdbc.provider()));

        assertSame(keyFailure, thrown.getCause());
        assertEquals(1, run.getRunId());
    }

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

    @Test
    void saveRun_whenStatementExecutionFails_preservesSqlExceptionAsCause() {
        RecordingJdbc jdbc = new RecordingJdbc();
        SQLException executeFailure = new SQLException("simulated insert failure");
        jdbc.executeFailure = executeFailure;
        Run run = sampleRun();

        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.saveRun(run, jdbc.provider()));

        assertSame(executeFailure, thrown.getCause());
        assertEquals(1, run.getRunId());
    }

    // --- Update post-run feedback -------------------------------------------

    @Test
    void updateRunFeedback_whenOneRowMatches_bindsEnergyEffortAndRunId() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();

        boolean updated = RunStorage.updateRunFeedback(
                73, EnergyLevel.HIGH, EffortLevel.HIGH_COST, jdbc.provider());

        assertTrue(updated);
        assertEquals(
                "UPDATE runs SET post_run_energy = ?, effort_level = ? WHERE run_id = ?",
                jdbc.preparedSql);
        assertEquals("HIGH", jdbc.parameters.get(1));
        assertEquals("HIGH_COST", jdbc.parameters.get(2));
        assertEquals(73, jdbc.parameters.get(3));
    }

    @Test
    void updateRunFeedback_whenAnswersAreCleared_explicitlyBindsSqlNulls() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();

        boolean updated = RunStorage.updateRunFeedback(73, null, null, jdbc.provider());

        assertTrue(updated);
        assertTrue(jdbc.parameters.containsKey(1));
        assertTrue(jdbc.parameters.containsKey(2));
        assertNull(jdbc.parameters.get(1));
        assertNull(jdbc.parameters.get(2));
        assertEquals(Types.VARCHAR, jdbc.nullSqlTypes.get(1));
        assertEquals(Types.VARCHAR, jdbc.nullSqlTypes.get(2));
        assertEquals(73, jdbc.parameters.get(3));
    }

    @Test
    void updateRunFeedback_whenNoRowMatches_returnsFalse() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.affectedRows = 0;

        boolean updated = RunStorage.updateRunFeedback(
                404, EnergyLevel.MODERATE, EffortLevel.MODERATE_COST, jdbc.provider());

        assertFalse(updated);
    }

    @Test
    void updateRunFeedback_whenConnectionFails_preservesSqlExceptionAsCause() {
        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.updateRunFeedback(
                        73, EnergyLevel.HIGH, EffortLevel.HIGH_COST, FAILING_PROVIDER));

        assertSame(SIMULATED_DB_ERROR, thrown.getCause());
    }

    @Test
    void updateRunFeedback_whenStatementExecutionFails_preservesSqlExceptionAsCause() {
        RecordingJdbc jdbc = new RecordingJdbc();
        SQLException executeFailure = new SQLException("simulated update failure");
        jdbc.executeFailure = executeFailure;

        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.updateRunFeedback(
                        73, EnergyLevel.HIGH, EffortLevel.HIGH_COST, jdbc.provider()));

        assertSame(executeFailure, thrown.getCause());
    }

    // --- Delete saved run ----------------------------------------------------

    @Test
    void deleteRun_whenOneRowMatches_bindsRunIdAndReturnsTrue() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();

        boolean deleted = RunStorage.deleteRun(73, jdbc.provider());

        assertTrue(deleted);
        assertEquals("DELETE FROM runs WHERE run_id = ?", jdbc.preparedSql);
        assertEquals(73, jdbc.parameters.get(1));
    }

    @Test
    void deleteRun_whenNoRowMatches_returnsFalse() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.affectedRows = 0;

        boolean deleted = RunStorage.deleteRun(404, jdbc.provider());

        assertFalse(deleted);
    }

    @Test
    void deleteRun_whenConnectionFails_preservesSqlExceptionAsCause() {
        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.deleteRun(73, FAILING_PROVIDER));

        assertSame(SIMULATED_DB_ERROR, thrown.getCause());
    }

    @Test
    void deleteRun_whenStatementExecutionFails_preservesSqlExceptionAsCause() {
        RecordingJdbc jdbc = new RecordingJdbc();
        SQLException executeFailure = new SQLException("simulated delete failure");
        jdbc.executeFailure = executeFailure;

        RunStorageException thrown = assertThrows(RunStorageException.class,
                () -> RunStorage.deleteRun(73, jdbc.provider()));

        assertSame(executeFailure, thrown.getCause());
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
