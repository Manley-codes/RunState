package com.runclubapp;

// Represents the allowed distance units for a run.
public enum DistanceUnit {
    // One mile already equals one mile, so its conversion multiplier is 1.0.
    MILES("miles", "mile", 1.0),
    // One kilometer equals about 0.621371 miles for internal comparisons.
    KILOMETERS("kilometers", "kilometer", 0.621371);

    // Text used when showing distance, such as "5.0 miles".
    private String displayName;

    // Text used when showing pace, such as "minutes per mile".
    private String paceName;

    // Stores how many miles one of this enum's units represents.
    private double milesPerUnit;

    // Receives and stores the labels and conversion multiplier for each enum value.
    DistanceUnit(String displayName, String paceName, double milesPerUnit) {
        this.displayName = displayName;
        this.paceName = paceName;
        this.milesPerUnit = milesPerUnit;
    }

    // Returns the text used when showing distance.
    public String getDisplayName() {
        return displayName;
    }

    // Returns the text used when showing pace.
    public String getPaceName() {
        return paceName;
    }

    // Receives a distance in this enum's unit and returns its equivalent in miles.
    public double convertToMiles(double distance) {
        return distance * milesPerUnit;
    }
}
