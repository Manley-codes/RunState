package com.runclubapp;

import java.util.ArrayList;

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


   // Adds one completed run to this runner's run history.
    public void addRun(Run run) {

        // Check whether this run is the runner's longest distance so far.
        if (isLongestDistanceRecord(run)) {

            // Mark the run so its summary can show the PR later.
            run.markLongestDistanceRecord();

            // Immediately tell the user they set a new longest distance PR.
            System.out.println("New longest distance PR!");
        }

        // Check whether this run is the runner's fastest average pace so far.
        if (isFastestAveragePaceRecord(run)) {

            // Mark the run so its summary can show the PR later.
            run.markFastestAveragePaceRecord();

            // Immediately tell the user they set a new fastest average pace PR.
            System.out.println("New fastest average pace PR!");
        }

        // Store the completed run after checking it against previous runs.
        runHistory.add(run);
    }

    // Checks whether the new run is longer than every previous run in the history.
    private boolean isLongestDistanceRecord(Run newRun) {

        // If there are no previous runs, there is nothing to beat yet.
        if (runHistory.size() == 0) {
            return false;
        }

        // Loop through each previous run.
        for (Run previousRun : runHistory) {

            // If a previous run has the same or longer distance, the new run is not a PR.
            if (previousRun.getDistance() >= newRun.getDistance()) {
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

            // A lower pace number is faster, so same or lower means the new run is not a PR.
            if (previousRun.getPace() <= newRun.getPace()) {
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

            // If this run is longer than the current longest run, update longestRun.
            if (run.getDistance() > longestRun.getDistance()) {
                longestRun = run;
            }

            // If this run has a lower pace than the current fastest pace run, update fastestPaceRun.
            if (run.getPace() < fastestPaceRun.getPace()) {
                fastestPaceRun = run;
            }
        }

        // Display the longest distance record.
        System.out.println("Current Personal Records for " + username + ":");
        System.out.println("Longest Distance: " + longestRun.getDistance() + " " + longestRun.getDistanceUnit());
        // Display the fastest average pace record.
        System.out.println("Fastest Average Pace: " + fastestPaceRun.getPace() + " min/" + fastestPaceRun.getPaceUnit());
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
