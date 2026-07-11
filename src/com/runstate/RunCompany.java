package com.runstate;

// Whether the runner was alone or with others. Secondary CONTEXT only, like
// SurfaceType — it can express a companion facet/habit but never creates a
// RunStyle or raises primary confidence. null = not recorded.
public enum RunCompany {
    SOLO("Solo"),
    WITH_OTHERS("With others");

    private final String label;

    RunCompany(String label) {
        this.label = label;
    }

    // Returns the runner-facing label shown in prompts, history, and the AI message.
    public String getLabel() {
        return label;
    }
}
