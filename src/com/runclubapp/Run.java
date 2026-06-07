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

    // Indicates whether this run was part of a club event
    private boolean clubRun;

    // Name of the club associated with the run
    private String associatedClub;

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
               String routeName, String routeLocation,
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
        this.clubRun = clubRun;
        this.associatedClub = associatedClub;
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
        System.out.println("Route Name: " + routeName);
        System.out.println("Route Location: " + routeLocation);
        System.out.println("Club Run: " + clubRun);
        System.out.println("Associated Club: " + associatedClub);
    }
}
