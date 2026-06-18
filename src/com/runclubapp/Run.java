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
    private DistanceUnit distanceUnit;
    private double duration;
    private String routeName;
    private String routeLocation;

    // Describes the runner's optional energy level before the run.
    private EnergyLevel preRunEnergy;

    // Describes the runner's optional energy level after the run.
    private EnergyLevel postRunEnergy;

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
               double distance, DistanceUnit distanceUnit, double duration,
               String routeName, String routeLocation, EnergyLevel preRunEnergy,
               EnergyLevel postRunEnergy,
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
        this.preRunEnergy = preRunEnergy;
        this.postRunEnergy = postRunEnergy;
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

    // This method returns the unit used for pace, such as mile or kilometer.
    public String getPaceUnit() {
        return distanceUnit.getPaceName();
    }

    // This method lets other classes read the run date.
    public String getDate() {
        return date;
    }

    // This method lets other classes read the run distance.
    public double getDistance() {
        return distance;
    }

    // This method lets other classes read the distance unit display text.
    public String getDistanceUnit() {
        return distanceUnit.getDisplayName();
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

    // Returns the runner's energy level before the run, or null if it was skipped.
    public EnergyLevel getPreRunEnergy() {
        return preRunEnergy;
    }

    // Returns the runner's energy level after the run, or null if it was skipped.
    public EnergyLevel getPostRunEnergy() {
        return postRunEnergy;
    }

    // This method returns a short summary of the run.
    public String getRunSummary() {

        // This method returns a readable summary of the run.
        String summary = "\nDate: " + date +
                "\nDistance: " + distance + " " + distanceUnit.getDisplayName() +
                "\nPace: " + getPace() + " min/" + getPaceUnit()  +
                "\nDuration: " + duration + " minutes";

        if (preRunEnergy != null) {
            summary = summary + "\nPre-run energy: " + preRunEnergy.getPreRunLabel();
        }

        if (postRunEnergy != null) {
            summary = summary + "\nPost-run energy: " + postRunEnergy.getPostRunLabel();
        }

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
        System.out.println("Distance: " + distance + " " + distanceUnit.getDisplayName());
        System.out.println("Duration: " + duration + " minutes");
        System.out.println("Pace: " + getPace() + " minutes per " + getPaceUnit());

        if (preRunEnergy != null) {
            System.out.println("Pre-run energy: " + preRunEnergy.getPreRunLabel());
        }

        if (postRunEnergy != null) {
            System.out.println("Post-run energy: " + postRunEnergy.getPostRunLabel());
        }

        System.out.println("Route Name: " + routeName);
        System.out.println("Route Location: " + routeLocation);
        System.out.println("Club Run: " + clubRun);
        System.out.println("Associated Club: " + associatedClub);
    }
}
