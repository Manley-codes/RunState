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
    private static final String PASSWORD = "runstate";

    // Opens and returns a live connection to the database.
    // Marked private because only this class needs to call it.
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /*
     * Saves one completed run to the database.
     *
     * A PreparedStatement uses ? placeholders instead of inserting values
     * directly into the SQL string. This is safer and prevents SQL injection.
     */
    public static void saveRun(Run run) {
        String sql = "INSERT INTO runs (run_date, start_time, end_time, distance, distance_unit, " +
                     "duration, route_name, route_location, pre_run_energy, post_run_energy, music_context, " +
                     "temperature, weather_condition) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // try-with-resources automatically closes the connection and statement
        // when the block ends, even if an error occurs.
        try (Connection conn = getConnection();
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
            stmt.setDouble(12, run.getTemperature());
            stmt.setString(13, run.getWeatherCondition());

            stmt.executeUpdate();

        } catch (SQLException e) {
            System.out.println("Could not save run: " + e.getMessage());
        }
    }

    /*
     * Loads all runs from the database and returns them as a list.
     *
     * A ResultSet works like a cursor — rs.next() moves to each row one at a time.
     * We read each column by name and reconstruct a Run object from the values.
     */
    public static List<Run> loadRuns(Runner runner) {
        List<Run> runs = new ArrayList<>();
        String sql = "SELECT * FROM runs ORDER BY run_date ASC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int runId = rs.getInt("run_id");
                LocalDate date = rs.getObject("run_date", LocalDate.class);
                String startTime = rs.getString("start_time");
                String endTime = rs.getString("end_time");
                double distance = rs.getDouble("distance");
                DistanceUnit distanceUnit = DistanceUnit.valueOf(rs.getString("distance_unit"));
                double duration = rs.getDouble("duration");
                String routeName = rs.getString("route_name");
                String routeLocation = rs.getString("route_location");

                // valueOf() converts the stored string back into the matching enum constant.
                // The null check handles optional fields that were skipped when logging.
                String preStr = rs.getString("pre_run_energy");
                EnergyLevel preRunEnergy = preStr != null ? EnergyLevel.valueOf(preStr) : null;

                String postStr = rs.getString("post_run_energy");
                EnergyLevel postRunEnergy = postStr != null ? EnergyLevel.valueOf(postStr) : null;

                // Temperature in Fahrenheit fetched from Open-Meteo at the time of the run.
                double temperature = rs.getDouble("temperature");

                // Short weather description fetched from Open-Meteo, or null if unavailable.
                String weatherCondition = rs.getString("weather_condition");

                // Optional free-text music note; getString returns null if the column is empty.
                String musicContext = rs.getString("music_context");

                Run run = new Run(runId, runner, date, startTime, endTime,
                        distance, distanceUnit, duration, routeName, routeLocation,
                        preRunEnergy, postRunEnergy, musicContext,temperature, weatherCondition);

                runs.add(run);
            }

        } catch (SQLException e) {
            System.out.println("Could not load runs: " + e.getMessage());
        }

        return runs;
    }
}
