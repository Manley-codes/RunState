package com.runstate;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * RunStorage handles reading and writing runs to the MySQL database.
 *
 * This class uses JDBC — Java's standard API for talking to databases.
 * Every database operation follows the same pattern:
 *   1. Open a Connection to the database
 *   2. Prepare a SQL statement
 *   3. Execute it and handle the result
 *   4. Close everything (handled automatically by try-with-resources)
 */
public class RunStorage {

    // The JDBC URL tells Java which database driver to use, where MySQL is running,
    // and which schema to connect to.
    private static final String URL = "jdbc:mysql://localhost:3306/runstate";
    private static final String USER = "runstate_user";

    // Opens and returns a live connection to the database.
    // Marked private because only this class needs to call it.
    private static Connection getConnection() throws SQLException {
        // The password is read from the environment, never hardcoded in source — the same
        // approach RunAgent uses for ANTHROPIC_API_KEY. Set RUNSTATE_DB_PASSWORD in the run
        // environment (IntelliJ run config or the shell). See docs/DATA_PRIVACY.md / CLAUDE.md.
        String password = System.getenv("RUNSTATE_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new SQLException("RUNSTATE_DB_PASSWORD environment variable not set");
        }
        return DriverManager.getConnection(URL, USER, password);
    }

    // The injectable seam. Production passes the real getConnection above; a test can
    // pass a lambda that throws, simulating a dead database without touching MySQL.
    // Package-private (no modifier) so tests in this package can supply their own.
    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /*
     * Signals that one stored row could not be decoded into a Run.
     *
     * PRIVATE and nested on purpose: this type never leaves RunStorage. The row-reading
     * loop catches it and wraps it in the public RunStorageException, so App and
     * RunConsole keep speaking only the storage vocabulary they already know.
     *
     * CHECKED (extends Exception) so the compiler forces the read loop to handle a
     * malformed row instead of letting it escape as an uncaught runtime error.
     *
     * The message carries the row-level diagnostic (which run, which column, what value)
     * that ends up on the "Details:" line at startup.
     */
    private static class StoredRunDecodeException extends Exception {

        // For a missing required value: nothing threw, so there is no cause to preserve.
        StoredRunDecodeException(String message) {
            super(message);
        }

        // For an invalid value: the cause is the original IllegalArgumentException from
        // Enum.valueOf, kept underneath so the low-level detail is never lost.
        StoredRunDecodeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /*
     * Saves one completed run to the database.
     *
     * A PreparedStatement uses ? placeholders instead of inserting values
     * directly into the SQL string. This is safer and prevents SQL injection.
     */
    // Public entry point: production always uses the real database connection.
    public static void saveRun(Run run) throws RunStorageException {
        saveRun(run, RunStorage::getConnection);
    }

    // Package-private worker: the connection source is injected (the seam).
    static void saveRun(Run run, ConnectionProvider connectionProvider) throws RunStorageException {
        String sql =  "INSERT INTO runs (run_date, start_time, end_time, distance, distance_unit, " +
                "duration, route_name, route_location, pre_run_energy, post_run_energy, music_context, " +
                "music_mode, surface_type, shoe_label, run_company, " +
                "temperature, apparent_temperature, weather_condition, effort_level) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // try-with-resources automatically closes the connection and statement
        // when the block ends, even if an error occurs.
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, run.getDate());
            stmt.setString(2, null);
            stmt.setString(3, null);
            stmt.setDouble(4, run.getDistance());
            stmt.setString(5, run.getDistanceUnitEnum().name());
            stmt.setDouble(6, run.getDuration());
            stmt.setString(7, run.getRouteName());
            stmt.setString(8, null);
            // Store the enum constant name ("HIGH", "LOW", "MODERATE") or null if skipped.
            stmt.setString(9, run.getPreRunEnergy() != null ? run.getPreRunEnergy().name() : null);
            stmt.setString(10, run.getPostRunEnergy() != null ? run.getPostRunEnergy().name() : null);
            // Free-text music note, or null if the runner skipped the prompt.
            stmt.setString(11, run.getMusicContext());
            // Secondary context (RunStyle V1). Enums store their constant name ("TRAIL",
            // "SOLO", "NO_MUSIC"), or null when the runner skipped that answer. Shoe label
            // is free text. music_mode is separate from the music note above on purpose:
            // NO_MUSIC (deliberately silent) must stay distinct from null (never recorded).
            stmt.setString(12, run.getMusicMode() != null ? run.getMusicMode().name() : null);
            stmt.setString(13, run.getSurface() != null ? run.getSurface().name() : null);
            stmt.setString(14, run.getShoeLabel());
            stmt.setString(15, run.getRunCompany() != null ? run.getRunCompany().name() : null);
            stmt.setObject(16, run.getTemperature());
            stmt.setObject(17, run.getApparentTemperature());
            stmt.setString(18, run.getWeatherCondition());
            // Store the effort enum constant name ("LOW_COST", "MAX_COST", ...), or null if skipped.
            stmt.setString(19, run.getEffortLevel() != null ? run.getEffortLevel().name() : null);

            stmt.executeUpdate();

        } catch (SQLException e) {
            // Wrap the low-level cause in our domain exception. The caller can no longer
            // continue as if the save succeeded — the compiler won't let it.
            throw new RunStorageException("Could not save run", e);
        }
    }

    /*
     * Loads all runs from the database and returns them as a list.
     *
     * A ResultSet works like a cursor — rs.next() moves to each row one at a time.
     * We read each column by name and reconstruct a Run object from the values.
     */
    // Public entry point: production always uses the real database connection.
    public static List<Run> loadRuns(Runner runner) throws RunStorageException {
        return loadRuns(runner, RunStorage::getConnection);
    }

    // Package-private worker: the connection source is injected (the seam).
    static List<Run> loadRuns(Runner runner, ConnectionProvider connectionProvider) throws RunStorageException {
        // run_id is the tiebreak so same-day runs load in a deterministic order — the
        // point-in-time RunStyle logic depends on "which run came first" being stable.
        String sql = "SELECT * FROM runs ORDER BY run_date, run_id";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            return readRuns(runner, rs);

        } catch (SQLException e) {
            // Throw instead of returning the half-built list — a partial history would
            // silently corrupt PR flags and RunStyle. The plan requires all-or-nothing.
            throw new RunStorageException("Could not load runs", e);
        }
    }

    /*
     * Package-private row-iteration seam.
     *
     * The caller supplies the ResultSet, so a test can feed rows in without a live
     * database — the ConnectionProvider seam simulates an unreachable database, this one
     * simulates a reachable database holding bad data. Production passes the real
     * ResultSet from loadRuns.
     *
     * Declares RunStorageException rather than the private decode exception because tests
     * live outside this class and must be able to name what they catch; the private type
     * stays sealed inside RunStorage.
     */
    static List<Run> readRuns(Runner runner, ResultSet rs) throws SQLException, RunStorageException {
        List<Run> runs = new ArrayList<>();

        try {
            while (rs.next()) {
                int runId = rs.getInt("run_id");
                LocalDate date = rs.getObject("run_date", LocalDate.class);
                String startTime = rs.getString("start_time");
                String endTime = rs.getString("end_time");
                double distance = rs.getDouble("distance");
                DistanceUnit distanceUnit = decodeRequiredEnum(
                        DistanceUnit.class, rs.getString("distance_unit"), runId, "distance_unit");
                double duration = rs.getDouble("duration");
                String routeName = rs.getString("route_name");
                String routeLocation = rs.getString("route_location");

                // Optional enum columns are decoded through decodeOptionalEnum: null stays
                // null (the runner skipped the question, or this is a legacy row), and any
                // non-null value must match an enum constant exactly.
                EnergyLevel preRunEnergy = decodeOptionalEnum(
                        EnergyLevel.class, rs.getString("pre_run_energy"), runId, "pre_run_energy");

                EnergyLevel postRunEnergy = decodeOptionalEnum(
                        EnergyLevel.class, rs.getString("post_run_energy"), runId, "post_run_energy");

                // Weather columns are nullable. getObject(..., Double.class) returns a real
                // Double or null — unlike getDouble, which turns a SQL NULL into 0.0 and would
                // reintroduce the "0.0 means missing" ambiguity we just designed away.
                Double temperature = rs.getObject("temperature", Double.class);
                Double apparentTemperature = rs.getObject("apparent_temperature", Double.class);
                String weatherCondition = rs.getString("weather_condition");

                // Rebuild the weather bundle from its three columns (composition again).
                WeatherData weather = new WeatherData(temperature, apparentTemperature, weatherCondition);

                // Optional free-text music note; getString returns null if the column is empty.
                String musicContext = rs.getString("music_context");

                // Secondary context columns (RunStyle V1). Each enum decodes through the
                // optional helper, staying null when the column is null (skipped, or a legacy
                // row from before these columns existed). Shoe label is plain text.
                SurfaceType surface = decodeOptionalEnum(
                        SurfaceType.class, rs.getString("surface_type"), runId, "surface_type");

                RunCompany company = decodeOptionalEnum(
                        RunCompany.class, rs.getString("run_company"), runId, "run_company");

                String shoeLabel = rs.getString("shoe_label");

                // Music mode is validated BEFORE the legacy inference rule runs: a corrupt
                // stored mode must fail as corrupt, never fall through and get quietly
                // re-inferred from the music note. Inference for legacy rows lives in
                // inferMusicMode (extracted so it can be unit-tested without a live database).
                MusicMode storedMusicMode = decodeOptionalEnum(
                        MusicMode.class, rs.getString("music_mode"), runId, "music_mode");
                MusicMode musicMode = inferMusicMode(storedMusicMode, musicContext);

                // Rebuild the context bundle from its columns (composition, like weather).
                RunContext context = new RunContext(surface, company, shoeLabel, musicMode, musicContext);

                // Optional effort level; null when skipped or a legacy row.
                EffortLevel effortLevel = decodeOptionalEnum(
                        EffortLevel.class, rs.getString("effort_level"), runId, "effort_level");

                Run run = new Run(runId, runner, date, startTime, endTime,
                        distance, distanceUnit, duration, routeName, routeLocation,
                        preRunEnergy, postRunEnergy, context, weather, effortLevel);
                runs.add(run);
            }

        } catch (StoredRunDecodeException e) {
            // A stored row is malformed. Wrap our private diagnostic in the public storage
            // exception so App's existing friendly boundary handles it — same controlled
            // path as a dead connection. The row/column detail rides along as the cause and
            // reaches the "Details:" line. The half-built list is discarded: never partial.
            throw new RunStorageException("Could not load runs", e);
        }

        return runs;
    }

    /*
     * Decodes one OPTIONAL stored enum column.
     *
     * null is a valid stored answer — the runner skipped the question, or this is a
     * legacy row written before the column existed — so null passes straight through.
     *
     * A non-null value must match an enum constant EXACTLY. No trimming, no case
     * conversion, no guessing, no defaulting: stored text that is not a real constant
     * means the history itself is corrupted, and RunState says so instead of inventing
     * a value that would silently poison PRs and RunStyle.
     *
     * <T extends Enum<T>> makes this work for every enum column; the Class<T> token is
     * how the method knows which enum to look the text up in at runtime.
     */
    private static <T extends Enum<T>> T decodeOptionalEnum(
            Class<T> type, String storedValue, int runId, String column)
            throws StoredRunDecodeException {

        if (storedValue == null) {
            return null;
        }

        try {
            return Enum.valueOf(type, storedValue);
        } catch (IllegalArgumentException e) {
            // Keep the original enum exception as the cause — the low-level detail stays
            // available underneath our row-level diagnostic.
            throw new StoredRunDecodeException(
                    "Run " + runId + " has invalid value \"" + storedValue + "\" in " + column, e);
        }
    }

    /*
     * Decodes one REQUIRED stored enum column (currently distance_unit).
     *
     * null is NOT a valid answer here — a distance with no unit is meaningless, and every
     * pace, PR, and RunStyle judgment downstream would be built on a guess. So this adds
     * exactly one rule on top of decodeOptionalEnum and delegates everything else to it,
     * keeping one single implementation of "stored text -> enum constant".
     */
    private static <T extends Enum<T>> T decodeRequiredEnum(
            Class<T> type, String storedValue, int runId, String column)
            throws StoredRunDecodeException {

        if (storedValue == null) {
            // No cause to pass: nothing threw. We looked at the column and found it empty.
            throw new StoredRunDecodeException(
                    "Run " + runId + " is missing required value in " + column);
        }

        return decodeOptionalEnum(type, storedValue, runId, column);
    }

    /*
     * Decides the MusicMode for a loaded row. Package-private and static (no database
     * needed) so it can be unit-tested directly.
     *
     * Takes an already-decoded MusicMode, not raw stored text: the caller validates the
     * column first, so this method is purely the rule and a corrupt stored mode can never
     * reach it and be silently re-inferred from the music note.
     *
     * Rules (see design_runstyle_v1):
     *   - A stored mode always wins — a new row that recorded MUSIC or NO_MUSIC.
     *   - No stored mode but a music NOTE exists → a legacy row that clearly HAD music,
     *     so infer MUSIC.
     *   - Both null → the mode was never recorded; stay null. We NEVER infer NO_MUSIC,
     *     because the absence of a note is not evidence of deliberate silence.
     */
    static MusicMode inferMusicMode(MusicMode storedMode, String musicNote) {
        if (storedMode != null) {
            return storedMode;
        }
        if (musicNote != null) {
            return MusicMode.MUSIC;
        }
        return null;
    }
}
