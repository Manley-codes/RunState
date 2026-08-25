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

    // Finds the saved Run with this database ID, or null when this in-memory
    // history has no match. Package-private keeps History selection orchestration
    // inside com.runstate without widening Runner's public API.
    Run findRunById(int runId) {
        for (Run run : runHistory) {
            if (run.getRunId() == runId) {
                return run;
            }
        }
        return null;
    }

    // Removes one saved Run with this database ID, then silently rebuilds the
    // remaining historical PR flags. Storage must confirm deletion before the
    // console calls this in-memory method.
    boolean removeRunById(int runId) {
        for (int index = 0; index < runHistory.size(); index++) {
            if (runHistory.get(index).getRunId() == runId) {
                runHistory.remove(index);
                rebuildPersonalRecordFlags();
                return true;
            }
        }
        return false;
    }

    // Returns a RunStyle announcement for this run, or null when nothing new should be
    // announced. All the detection logic now lives in RunStyleService (SRP, like
    // ComparisonService) — Runner just hands it this run plus the full history and relays
    // what comes back. getRunHistory() already includes currentRun, because addRun() runs
    // before this in logRun(), which is exactly what analyze() expects.
    public String detectRunStyle(Run currentRun) {
        RunStyleInsight insight = RunStyleService.analyze(currentRun, getRunHistory());
        return insight.shouldAnnounce() ? insight.getMessage() : null;
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

    // Replays the remaining runs from oldest to newest through the existing PR
    // rules. The date sort is stable, so same-day runs keep their established list
    // order. Passing false to storeRun prevents historical PR announcements.
    private void rebuildPersonalRecordFlags() {
        ArrayList<Run> remainingRuns = new ArrayList<>(runHistory);
        remainingRuns.sort((firstRun, secondRun) ->
                firstRun.getDate().compareTo(secondRun.getDate()));

        for (Run run : remainingRuns) {
            run.clearPersonalRecordFlags();
        }

        runHistory.clear();
        for (Run run : remainingRuns) {
            storeRun(run, false);
        }
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

            // Display the real database ID with the current run's short summary.
            System.out.println();
            System.out.println("Run ID: " + run.getRunId());
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
