package com.runstate;

// The kind of ground a run was on. Secondary CONTEXT only — surface can color a
// RunStyle facet or habit line, but it can never create a RunStyle or raise the
// confidence of a primary pattern (see design_runstyle_v1). null = not recorded.
public enum SurfaceType {
    ROAD("Road"),
    TRAIL("Trail"),
    TRACK("Track"),
    TREADMILL("Treadmill"),
    MIXED("Mixed");

    private final String label;

    SurfaceType(String label) {
        this.label = label;
    }

    // Returns the runner-facing label shown in prompts, history, and the AI message.
    public String getLabel() {
        return label;
    }
}
