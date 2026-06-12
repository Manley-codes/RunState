package com.runclubapp;

// Represents the allowed feeling choices after a run.
public enum RunFeeling {
    DIFFICULT("Difficult"),
    NORMAL("Normal"),
    GREAT("Great");

    // Stores the user-friendly text for each feeling.
    private String displayName;

    // Creates a RunFeeling with text that looks good in output.
    RunFeeling(String displayName) {
        this.displayName = displayName;
    }

    // Returns the user-friendly version of the feeling.
    public String getDisplayName() {
        return displayName;
    }
}
