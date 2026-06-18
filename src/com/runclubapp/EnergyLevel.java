package com.runclubapp;

// Represents the shared low-to-high scale used before and after a run.
public enum EnergyLevel {
    LOW(1, "I'm Here", "Spent"),
    MODERATE(2, "Ready-ish", "Feeling Good"),
    HIGH(3, "Let's Go!", "Powered Up");

    private final int value;
    private final String preRunLabel;
    private final String postRunLabel;

    EnergyLevel(int value, String preRunLabel, String postRunLabel) {
        this.value = value;
        this.preRunLabel = preRunLabel;
        this.postRunLabel = postRunLabel;
    }

    // Returns the stable numeric level used for future comparisons.
    public int getValue() {
        return value;
    }

    // Returns the label shown before a run.
    public String getPreRunLabel() {
        return preRunLabel;
    }

    // Returns the label shown after a run.
    public String getPostRunLabel() {
        return postRunLabel;
    }
}
