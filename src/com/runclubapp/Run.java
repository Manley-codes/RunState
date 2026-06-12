package com.runclubapp;


/*
 * The Run class represents a single running activity.
 *
 * Every time a runner completes a run,
 * we can create a Run object to store information about it.
 */

public class Run {


    // Unique identifier for this run
    private int runId;

    // This stores the actual Runner object who completed the run.
// This is more object-oriented than storing only the runner's ID number.
    private Runner runner;

    private String date;
    private String startTime;
    private String endTime;
    private double distance;

    // Unit of distance: "miles" or "kilometers"
    private String distanceUnit;
    private double duration;
    private String routeName;
    private String routeLocation;

    // Describes how the runner felt about this run using a fixed set of choices.
    private RunFeeling runFeeling;

    // Indicates whether this run was part of a club event
    private boolean clubRun;

    // Name of the club associated with the run
    private String associatedClub;

    // Tracks whether this run was the runner's longest distance personal record.
    private boolean longestDistanceRecord;

    // Tracks whether this run was the runner's fastest average pace personal record.
    private boolean fastestAveragePaceRecord;

    /*
     * Constructor
     *
     * A constructor is used when creating a new object.
     *
     * Example:
     *
     * Run run1 = new Run(...);
     *
     * Everything inside the parentheses below must be provided
     * when creating the object.
     */
    public Run(int runId, Runner runner, String date, String startTime, String endTime,
               double distance, String distanceUnit, double duration,
               String routeName, String routeLocation, RunFeeling runFeeling,
               boolean clubRun, String associatedClub) {

        // "this" refers to the current object's variables
        this.runId = runId;
        this.runner = runner;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.distance = distance;
        this.distanceUnit = distanceUnit;
        this.duration = duration;
        this.routeName = routeName;
        this.routeLocation = routeLocation;
        // Store how the runner felt about this run.
        this.runFeeling = runFeeling;
        this.clubRun = clubRun;
        this.associatedClub = associatedClub;
        // A run starts with no PR label until the Runner checks the run history.
        this.longestDistanceRecord = false;
        // A run starts with no fastest pace PR label until the Runner checks the run history.
        this.fastestAveragePaceRecord = false;
    }

    /*
     * Calculates and returns the runner's pace.
     *
     * We do not store pace as a variable because pace
     * can always be calculated from duration and distance.
     */
    public double getPace() {

        // Prevent division by zero
        if (distance == 0) {
            return 0;
        }

        return duration / distance;
    }

    // This method returns the singular version of the distance unit.
   // Pace is usually written as "minutes per mile" or "minutes per kilometer".
    public String getPaceUnit() {
        if (distanceUnit.equals("miles")) {
            return "mile";
        }

        if (distanceUnit.equals("kilometers")) {
            return "kilometer";
        }

        return distanceUnit;
    }

    // This method lets other classes read the run date.
    public String getDate() {
        return date;
    }

    // This method lets other classes read the run distance.
    public double getDistance() {
        return distance;
    }

    // This method lets other classes read the distance unit.
    public String getDistanceUnit() {
        return distanceUnit;
    }

    // This method lets other classes read the run duration.
    public double getDuration() {
        return duration;
    }

    // Marks this run as the runner's longest distance personal record.
    public void markLongestDistanceRecord() {
        longestDistanceRecord = true;
    }

    // This method tells other classes whether this run was a longest distance PR.
    public boolean isLongestDistanceRecord() {
        return longestDistanceRecord;
    }

    // Marks this run as the runner's fastest average pace personal record.
    public void markFastestAveragePaceRecord() {
        fastestAveragePaceRecord = true;
    }

    // This method tells other classes whether this run was a fastest average pace PR.
    public boolean isFastestAveragePaceRecord() {
        return fastestAveragePaceRecord;
    }

    // This method returns text for any personal records earned by this run.
    public String getPersonalRecordSummary() {

        // Start with no PR message.
        String personalRecordSummary = "";

        // Add longest distance PR text if this run earned it.
        if (longestDistanceRecord) {
            personalRecordSummary = "New longest distance PR";
        }

        // Add fastest average pace PR text if this run earned it.
        if (fastestAveragePaceRecord) {

            // If another PR message already exists, separate the messages with a comma.
            if (!personalRecordSummary.equals("")) {
                personalRecordSummary = personalRecordSummary + ", ";
            }

            personalRecordSummary = personalRecordSummary + "New fastest average pace PR";
        }

        return personalRecordSummary;
    }

    // This method lets other classes read how the run felt.
    public RunFeeling getRunFeeling() {
        return runFeeling;
    }

    // This method returns a short summary of the run.
    public String getRunSummary() {

        // This method returns a readable summary of the run.
        String summary = "\nDate: " + date +
                "\nDistance: " + distance + " " + distanceUnit +
                "\nPace: " + getPace() + " min/" + getPaceUnit()  +
                "\nDuration: " + duration + " minutes"  +
                "\nFeeling: " + runFeeling;


        // If this run has a PR, add the PR text to the end of the summary.
        if (!getPersonalRecordSummary().equals("")) {
            summary = summary + "\nPR: " + getPersonalRecordSummary();
        }

        return summary;
    }



    /*
     * Displays all information about the run.
     *
     * This is useful while testing the program.
     */
    public void displayRunInfo() {
        System.out.println("Run ID: " + runId);
        // Ask the Runner object for its ID and username using getter methods.
        System.out.println("Runner ID: " + runner.getRunnerId());
        System.out.println("Runner Username: " + runner.getUsername());
        System.out.println("Date: " + date);
        System.out.println("Start Time: " + startTime);
        System.out.println("End Time: " + endTime);
        System.out.println("Distance: " + distance + " " + distanceUnit);
        System.out.println("Duration: " + duration + " minutes");
        System.out.println("Pace: " + getPace() + " minutes per " + getPaceUnit());
        // Display how the runner felt about this run.
        System.out.println("Run Feeling: " + getRunFeeling());
        System.out.println("Route Name: " + routeName);
        System.out.println("Route Location: " + routeLocation);
        System.out.println("Club Run: " + clubRun);
        System.out.println("Associated Club: " + associatedClub);
    }
}
