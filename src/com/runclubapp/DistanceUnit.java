package com.runclubapp;

// Represents the allowed distance units for a run.
public enum DistanceUnit {
    MILES("miles", "mile"),
    KILOMETERS("kilometers", "kilometer");

    // Text used when showing distance, such as "5.0 miles".
    private String displayName;

    // Text used when showing pace, such as "minutes per mile".
    private String paceName;

    // Creates a distance unit with display text and pace text.
    DistanceUnit(String displayName, String paceName) {
        this.displayName = displayName;
        this.paceName = paceName;
    }

    // Returns the text used when showing distance.
    public String getDisplayName() {
        return displayName;
    }

    // Returns the text used when showing pace.
    public String getPaceName() {
        return paceName;
    }
}
