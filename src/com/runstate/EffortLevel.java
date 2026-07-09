package com.runstate;

// Represents how much a run COST — the effort axis, kept separate from energy
// (which is how you FINISHED). Runner-native labels on the surface; the internal
// RPE ranges stay private, for future effort analysis, never shown as "RPE".
public enum EffortLevel {
    LOW_COST("Smooth", 1, 3),
    MODERATE_COST("Working", 4, 5),
    HIGH_COST("Heavy", 6, 7),
    MAX_COST("Empty tank", 8, 10);

    private final String label;
    private final int minRpe;
    private final int maxRpe;

    EffortLevel(String label, int minRpe, int maxRpe) {
        this.label = label;
        this.minRpe = minRpe;
        this.maxRpe = maxRpe;
    }

    // Returns the runner-facing label (never "RPE" — runner-native words only).
    public String getLabel() {
        return label;
    }

    // Low end of the internal RPE range this effort maps to (for the coming comparison fix).
    public int getMinRpe() {
        return minRpe;
    }

    // High end of the internal RPE range this effort maps to.
    public int getMaxRpe() {
        return maxRpe;
    }
}
