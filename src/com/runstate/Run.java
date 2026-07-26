package com.runstate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    // Optional secondary context (surface, company, shoes, music) bundled into one
    // immutable object — composition, the same design as `weather` below. Never null:
    // a run with nothing recorded holds RunContext.EMPTY, so the delegating getters
    // never have to guard a null bundle (this is where it diverges from weather, which
    // stays nullable and is guarded field-by-field).
    private final RunContext context;

    // Weather at the time of the run, bundled into one immutable object (composition).
    // The bundle may be null, and its fields may be null, when weather wasn't recorded.
    private final WeatherData weather;

    // How much the run COST — the effort axis, separate from energy (how you finished).
    // Null when the runner skipped, or until it's recorded just after post-run energy.
    // Non-final with a setter, mirroring postRunEnergy: the run is constructed before the
    // runner answers, so this is set a moment later (see the construct-early refactor note).
    private EffortLevel effortLevel;

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
               EnergyLevel postRunEnergy, RunContext context, WeatherData weather,
               EffortLevel effortLevel) {

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
        // Coerce a null bundle to the shared EMPTY instance so the getters below stay
        // null-free and getRunContext() never returns null.
        this.context = context != null ? context : RunContext.EMPTY;
        // A run starts with no PR label until the Runner checks the run history.
        this.longestDistanceRecord = false;
        // A run starts with no fastest pace PR label until the Runner checks the run history.
        this.fastestAveragePaceRecord = false;
        this.weather = weather;
        this.effortLevel = effortLevel;
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

    // Returns the full context bundle (surface/company/shoes/music). Never null —
    // an all-null RunContext.EMPTY stands in when nothing was recorded.
    public RunContext getRunContext() {
        return context;
    }

    // Returns what the runner listened to (free text), or null if it was skipped.
    // Delegates into the context bundle — the note now lives there, not on Run.
    public String getMusicContext() {
        return context.getMusicNote();
    }

    // Returns the surface the run was on, or null if not recorded.
    public SurfaceType getSurface() {
        return context.getSurface();
    }

    // Returns whether the runner was solo or with others, or null if not recorded.
    public RunCompany getRunCompany() {
        return context.getCompany();
    }

    // Returns the reusable shoe label, or null if not recorded.
    public String getShoeLabel() {
        return context.getShoeLabel();
    }

    // Returns whether the runner had music (MUSIC / NO_MUSIC), or null if not recorded.
    public MusicMode getMusicMode() {
        return context.getMusicMode();
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

    // Returns the air temperature in Fahrenheit, or null if weather wasn't recorded.
    // Delegates to the WeatherData bundle, guarding against a null bundle first.
    public Double getTemperature() {
        return weather != null ? weather.getTemperature() : null;
    }

    // Returns the feels-like temperature in Fahrenheit, or null if not recorded.
    public Double getApparentTemperature() {
        return weather != null ? weather.getApparentTemperature() : null;
    }

    // Returns the weather condition description, or null if not recorded.
    public String getWeatherCondition() {
        return weather != null ? weather.getWeatherCondition() : null;
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

    // Returns how much the run cost (the effort level), or null if the runner skipped it.
    public EffortLevel getEffortLevel() {
        return effortLevel;
    }

    // Allows post-run energy to be recorded after the run summary is displayed.
    public void setPostRunEnergy(EnergyLevel energy) {
        this.postRunEnergy = energy;
    }

    // Allows effort to be recorded just after post-run energy, once the run exists.
    // Mirrors setPostRunEnergy — same construct-early reason (Spring-Boot-era refactor note).
    public void setEffortLevel(EffortLevel effortLevel) {
        this.effortLevel = effortLevel;
    }


    // Returns the runner who completed this run, so other classes can read their profile data.
    public Runner getRunner() { return runner; }

    // This method returns a short summary of the run.
    public String getRunSummary() {

        String summary = "\n" + getSummaryHeader() +
                "\n" + formatPace() + " min/" + getPaceUnit() +
                " | " + formatNumber(duration) + " min";

        String contextSummary = getContextSummary();
        if (!contextSummary.isEmpty()) {
            summary = summary + "\n" + contextSummary;
        }

        if (preRunEnergy != null && postRunEnergy != null) {
            summary = summary + "\nEnergy: " + preRunEnergy.getPreRunLabel() +
                    " -> " + postRunEnergy.getPostRunLabel();
        } else if (preRunEnergy != null) {
            summary = summary + "\nPre-run energy: " + preRunEnergy.getPreRunLabel();
        } else if (postRunEnergy != null) {
            summary = summary + "\nPost-run energy: " + postRunEnergy.getPostRunLabel();
        }

        // Effort appears only when recorded, mirroring the Energy line above.
        if (effortLevel != null) {
            summary = summary + "\nEffort: " + effortLevel.getLabel();
        }

        String compactPersonalRecords = getCompactPersonalRecordSummary();
        if (!compactPersonalRecords.equals("")) {
            summary = summary + "\nPR: " + compactPersonalRecords;
        }

        return summary;
    }

    // Builds an optional one-line context summary in a fixed field order:
    // Surface | Company | Shoes: <label> | Music: <note> (or Music, No music).
    // Returns an empty string when every context field is absent so the caller
    // can decide whether to append a newline prefix.
    private String getContextSummary() {
        ArrayList<String> pieces = new ArrayList<>();

        if (getSurface() != null) {
            pieces.add(getSurface().getLabel());
        }
        if (getRunCompany() != null) {
            pieces.add(getRunCompany().getLabel());
        }
        String shoe = getShoeLabel();
        if (shoe != null && !shoe.isBlank()) {
            pieces.add("Shoes: " + shoe);
        }

        MusicMode mode = getMusicMode();
        String note = getMusicContext();
        boolean hasNote = note != null && !note.isBlank();
        if (mode == MusicMode.NO_MUSIC) {
            // Explicit NO_MUSIC wins over any stray note.
            pieces.add("No music");
        } else if (mode == MusicMode.MUSIC) {
            pieces.add(hasNote ? "Music: " + note : "Music");
        } else if (hasNote) {
            // Legacy row: note present but mode not stored — treat as music.
            pieces.add("Music: " + note);
        }

        if (pieces.isEmpty()) {
            return "";
        }
        return "Context: " + String.join(" | ", pieces);
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

    // Public display accessors used by the personal-records view (and anywhere else
    // that shows a run's pace or distance to the user). They delegate to the private
    // formatters below, so pace/distance formatting has a single home in Run.

    // Returns this run's pace in the runner-friendly minutes:seconds form (e.g. 7:26),
    // rather than the raw decimal getPace() returns.
    public String getFormattedPace() {
        return formatPace();
    }

    // Returns this run's distance with any trailing ".0" trimmed (5.0 -> "5").
    public String getFormattedDistance() {
        return formatNumber(distance);
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
