package com.runstate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/*
 * The Run class represents a single running activity.
 *
 * Every time a runner completes a run,
 * we can create a Run object to store information about it.
 */

public class Run {

    private static final DateTimeFormatter SUMMARY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);

    // Unique identifier for this run
    private int runId;

    // This stores the actual Runner object who completed the run.
// This is more object-oriented than storing only the runner's ID number.
    private Runner runner;

    private LocalDate date;
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

    // What the runner listened to during the run, or null if skipped.
    private String musicContext;

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
    public Run(int runId, Runner runner, LocalDate date, String startTime, String endTime,
               double distance, DistanceUnit distanceUnit, double duration,
               String routeName, String routeLocation, EnergyLevel preRunEnergy,
               EnergyLevel postRunEnergy, String musicContext) {

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
        this.musicContext = musicContext;
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

    // Returns what the runner listened to, or null if it was skipped.
    public String getMusicContext() {
        return musicContext;
    }

    // This method lets other classes read the run date.
    public LocalDate getDate() {
        return date;
    }

    // This method lets other classes read the route name.
    public String getRouteName() {
        return routeName;
    }

    // This method lets other classes read the run distance.
    public double getDistance() {
        return distance;
    }

    // Sends this run's distance to its DistanceUnit and returns the value in miles.
    public double getDistanceInMiles() {
        return distanceUnit.convertToMiles(distance);
    }

    // This method lets other classes read the distance unit display text.
    public String getDistanceUnit() {
        return distanceUnit.getDisplayName();
    }

    // Returns the raw distance unit enum so it can be stored and reconstructed later.
    public DistanceUnit getDistanceUnitEnum() {
        return distanceUnit;
    }

    // This method lets other classes read the run duration.
    public double getDuration() {
        return duration;
    }

    // Returns minutes per mile so pace PRs can compare mile and kilometer runs fairly.
    public double getPaceInMinutesPerMile() {
        // Calls the conversion helper and stores this run's distance in miles.
        double distanceInMiles = getDistanceInMiles();
        // Protects the division below if a zero-distance Run ever reaches this method.
        if (distanceInMiles == 0) {
            return 0;
        }
        // Divides this run's duration by its normalized mile distance.
        return duration / distanceInMiles;
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

    // Allows post-run energy to be recorded after the run summary is displayed.
    public void setPostRunEnergy(EnergyLevel energy) {
        this.postRunEnergy = energy;
    }

    // Returns the runner who completed this run, so other classes can read their profile data.
    public Runner getRunner() { return runner; }

    // This method returns a short summary of the run.
    public String getRunSummary() {

        String summary = "\n" + getSummaryHeader() +
                "\n" + formatPace() + " min/" + getPaceUnit() +
                " | " + formatNumber(duration) + " min";

        if (preRunEnergy != null && postRunEnergy != null) {
            summary = summary + "\nEnergy: " + preRunEnergy.getPreRunLabel() +
                    " -> " + postRunEnergy.getPostRunLabel();
        } else if (preRunEnergy != null) {
            summary = summary + "\nPre-run energy: " + preRunEnergy.getPreRunLabel();
        } else if (postRunEnergy != null) {
            summary = summary + "\nPost-run energy: " + postRunEnergy.getPostRunLabel();
        }

        String compactPersonalRecords = getCompactPersonalRecordSummary();
        if (!compactPersonalRecords.equals("")) {
            summary = summary + "\nPR: " + compactPersonalRecords;
        }

        return summary;
    }

    // Builds the first summary line and omits the route when it is missing.
    private String getSummaryHeader() {
        String header = date.format(SUMMARY_DATE_FORMAT);

        if (routeName != null && !routeName.isBlank()) {
            header = header + " | " + routeName;
        }

        return header + " | " + formatNumber(distance) + " " +
                distanceUnit.getDisplayName();
    }

    // Converts decimal pace into the minutes:seconds format runners commonly use.
    private String formatPace() {
        long totalSeconds = Math.round(getPace() * 60);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format(Locale.ENGLISH, "%d:%02d", minutes, seconds);
    }

    // Removes unnecessary .0 endings while preserving meaningful decimal values.
    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.format(Locale.ENGLISH, "%.0f", value);
        }

        return String.format(Locale.ENGLISH, "%s", value);
    }

    // Returns shorter PR labels for the compact run-history card.
    private String getCompactPersonalRecordSummary() {
        String summary = "";

        if (longestDistanceRecord) {
            summary = "Longest distance";
        }

        if (fastestAveragePaceRecord) {
            if (!summary.equals("")) {
                summary = summary + " | ";
            }

            summary = summary + "Fastest pace";
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
    }
}
