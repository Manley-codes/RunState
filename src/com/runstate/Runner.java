package com.runstate;

import java.util.ArrayList;
import java.util.List;

/*
 * The Runner class represents a person using the app.
 *
 * A runner can log completed runs and review personal progress.
 */
public class Runner {

    // Unique identifier for each runner
    private int runnerId;

    // Name shown to other users in the app
    private String username;

    // Runner's real first name
    private String firstName;

    // Runner's real last name
    private String lastName;

    // City where the runner is located
    private String city;

    // State where the runner is located
    private String state;

    // Email used for account/login purposes
    private String email;

    // Stores all runs completed by this runner.
    private ArrayList<Run> runHistory;




    /*
     * Constructor
     *
     * Used to create a new Runner object.
     */
    public Runner(int runnerId, String username, String firstName,
                  String lastName, String city, String state,
                  String email) {

        this.runnerId = runnerId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.city = city;
        this.state = state;
        this.email = email;

        // Create an empty list for this runner's completed runs.
        this.runHistory = new ArrayList<Run>();

    }
    // This method lets other classes read the runner's ID.
    // The variable runnerId is private, so outside classes cannot access it directly
    public int getRunnerId() {
        return runnerId;
    }

    // This method lets other classes read the runner's username.
    // We use a getter instead of making username public
    public String getUsername() {
        return username;
    }

    // This method returns the name that should be shown publicly in the app.
    // For now, we are using username as the runner's display name.
    public String getDisplayName() {
        return username;
    }

    // Returns the city where the runner is located.
    public String getCity() {
        return city;
    }

    // Returns the state where the runner is located.
    public String getState() {
        return state;
    }

    // Returns the current history size so RunConsole can form a temporary next ID.
    public int getRunCount() {
        return runHistory.size();
    }

    // Returns this runner's completed runs as an unmodifiable snapshot, so other
    // classes (like ComparisonService) can read history without touching the real list.
    public List<Run> getRunHistory() {
        return List.copyOf(runHistory);
    }

    // Returns the average pace in minutes per mile over the last window runs.
    public double getRollingAveragePace(int window) {
        if (runHistory.isEmpty()) {
            return 0.0;
        }
        // The first index to include — skips older runs if the history is larger than window.
        int start = Math.max(0, runHistory.size() - window);
        // Accumulates the total pace across every run in the window.
        double total = 0.0;
        // Walks from the start index to the end, covering only the most recent runs.
        for (int i = start; i < runHistory.size(); i++) {
            // Adds this run's normalized pace so miles and kilometers compare fairly.
            total += runHistory.get(i).getPaceInMinutesPerMile();
        }
        // Divides by the actual number of runs summed, not the requested window size.
        return total / (runHistory.size() - start);
    }

    // Returns the average distance in miles over the last window runs.
    public double getRollingAverageDistance(int window) {
        if (runHistory.isEmpty()) {
            return 0.0;
        }
        // The first index to include — skips older runs if the history is larger than window.
        int start = Math.max(0, runHistory.size() - window);
        // Accumulates the total distance across every run in the window.
        double total = 0.0;
        // Walks from the start index to the end, covering only the most recent runs.
        for (int i = start; i < runHistory.size(); i++) {
            // Adds this run's distance converted to miles for a consistent unit.
            total += runHistory.get(i).getDistanceInMiles();
        }
        // Divides by the actual number of runs summed, not the requested window size.
        return total / (runHistory.size() - start);
    }

    // Returns a Run Style alert if a consistent above-average pattern is detected, or null if not.
    public String detectRunStyle(Run currentRun, double avgPace, double avgDistance) {

        // Need at least 10 previous runs to check for consistency.
        if (runHistory.size() < 11) {
            return null;
        }

        // Layer 1: current run must be faster AND farther than rolling average.
        if (currentRun.getPaceInMinutesPerMile() >= avgPace) {
            return null;
        }
        if (currentRun.getDistanceInMiles() <= avgDistance) {
            return null;
        }

        // Layer 2: post-run energy must be MODERATE or HIGH.
        if (currentRun.getPostRunEnergy() == null) {
            return null;
        }
        if (currentRun.getPostRunEnergy().getValue() < 2) {
            return null;
        }

        // Consistency gate: count how many of the 10 previous runs also qualified.
        int matchCount = 0;
        int windowStart = runHistory.size() - 11;
        int windowEnd = runHistory.size() - 2;
        for (int i = windowStart; i <= windowEnd; i++) {
            Run previous = runHistory.get(i);
            if (previous.getPaceInMinutesPerMile() < avgPace
                    && previous.getDistanceInMiles() > avgDistance
                    && previous.getPostRunEnergy() != null
                    && previous.getPostRunEnergy().getValue() >= 2) {
                matchCount++;
            }
        }

        // Threshold grows with run count — the app expects more consistency as history builds.
        int threshold;
        if (runHistory.size() <= 20) {
            threshold = 4;
        } else if (runHistory.size() <= 30) {
            threshold = 5;
        } else {
            threshold = 6;
        }

        if (matchCount < threshold) {
            return null;
        }

        // Pattern confirmed — build the alert.
        String alert = "Your Run Style is forming.\n"
                + "In " + matchCount + " of your last 10 runs, "
                + "you ran above your average and finished feeling strong.";

        // Layer 3: LOW → HIGH lift is always worth calling out.
        if (currentRun.getPreRunEnergy() == EnergyLevel.LOW
                && currentRun.getPostRunEnergy() == EnergyLevel.HIGH) {
            alert += "\nYou also have a habit of turning rough starts into strong finishes.";
        }

        return alert;
    }

    // Stores a newly logged run and requests that any new PRs be announced.
    public void addRun(Run run) {
        // true tells storeRun this run is new and its PR messages should be printed.
        storeRun(run, true);
    }

    // Stores existing sample/history data without announcing its old PRs again.
    public void loadRun(Run run) {
        // false keeps PR flags accurate but prevents old announcements at startup.
        storeRun(run, false);
    }

    // Shares PR and storage logic between a newly logged run and a loaded run.
    private void storeRun(Run run, boolean announcePersonalRecords) {
        // Check whether this run is the runner's longest distance so far.
        if (isLongestDistanceRecord(run)) {
            // Mark the run so its summary can show the PR later.
            run.markLongestDistanceRecord();
            // Only a newly logged run should announce the PR in the console.
            if (announcePersonalRecords) {
                System.out.println("New longest distance PR!");
            }
        }
        // Check whether this run is the runner's fastest average pace so far.
        if (isFastestAveragePaceRecord(run)) {
            // Mark the run so its summary can show the PR later.
            run.markFastestAveragePaceRecord();
            // Loaded history keeps its PR flag without replaying the announcement.
            if (announcePersonalRecords) {
                System.out.println("New fastest average pace PR!");
            }
        }
        // Store the completed run after checking it against previous runs.
        runHistory.add(run);
        // sort compares two Run dates at a time and places earlier dates first.
        runHistory.sort((firstRun, secondRun) ->
                firstRun.getDate().compareTo(secondRun.getDate()));
    }

    // Checks whether the new run is longer than every previous run in the history.
    private boolean isLongestDistanceRecord(Run newRun) {

        // If there are no previous runs, there is nothing to beat yet.
        if (runHistory.size() == 0) {
            return false;
        }

        // Loop through each previous run.
        for (Run previousRun : runHistory) {

            // Both distances are converted to miles before this PR comparison.
            if (previousRun.getDistanceInMiles() >= newRun.getDistanceInMiles()) {
                return false;
            }
        }

        // If no previous run was as long, this run is a new longest distance PR.
        return true;
    }

    // Checks whether the new run has a faster average pace than every previous run.
    private boolean isFastestAveragePaceRecord(Run newRun) {

        // If there are no previous runs, there is nothing to beat yet.
        if (runHistory.size() == 0) {
            return false;
        }

        // Loop through each previous run.
        for (Run previousRun : runHistory) {

            // Both paces use minutes per mile; a lower number is faster.
            if (previousRun.getPaceInMinutesPerMile() <=
                    newRun.getPaceInMinutesPerMile()) {
                return false;
            }
        }

        // If no previous run was as fast, this run is a new fastest average pace PR.
        return true;
    }

    // Displays every run stored in this runner's run history.
    public void displayRunHistory() {

        // If the list is empty, tell the user there are no runs yet.
        if (runHistory.size() == 0) {
            System.out.println(username + " has no runs yet.");
            return;
        }

        // Print a heading before showing the runs.
        System.out.println("Run History for " + username + ":");

       // Start at the last index because the newest run is added to the end of the list.
        for (int i = runHistory.size() - 1; i >= 0; i--) {

            // Get the run stored at the current index.
            Run run = runHistory.get(i);

            // Display a short summary of the current run.
            System.out.println(run.getRunSummary());
        }
    }

    // Displays the runner's current personal records.
    public void displayPersonalRecords() {

        // If there are no runs, there are no personal records to show.
        if (runHistory.size() == 0) {
            System.out.println(username + " has no personal records yet.");
            return;
        }

        // Start by assuming the first run is the longest.
        Run longestRun = runHistory.get(0);
        // Start by assuming the first run has the fastest average pace.
        Run fastestPaceRun = runHistory.get(0);

        // Loop through every run in the history.
        for (Run run : runHistory) {

            // Normalized mile values allow either original unit to win correctly.
            if (run.getDistanceInMiles() > longestRun.getDistanceInMiles()) {
                longestRun = run;
            }

            // Normalized pace prevents kilometer values from creating a false PR.
            if (run.getPaceInMinutesPerMile() <
                    fastestPaceRun.getPaceInMinutesPerMile()) {
                fastestPaceRun = run;
            }
        }

        // Display the longest distance record.
        System.out.println("Current Personal Records for " + username + ":");
        System.out.println("Longest Distance: " + longestRun.getFormattedDistance() + " " + longestRun.getDistanceUnit());
        // Display the fastest average pace record.
        System.out.println("Fastest Average Pace: " + fastestPaceRun.getFormattedPace() + " min/" + fastestPaceRun.getPaceUnit());
    }

    /*
     * Displays public/basic runner information.
     *
     * For now, we are including email
     * because we are just testing in the console.
     *
     * Later, we may decide not to show private contact information.
     */
    public void displayRunnerInfo() {
        System.out.println("Runner ID: " + runnerId);
        System.out.println("Username: " + username);
        System.out.println("Name: " + firstName + " " + lastName);
        System.out.println("Location: " + city + ", " + state);
        System.out.println("Email: " + email);

    }
}
