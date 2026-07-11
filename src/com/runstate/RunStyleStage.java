package com.runstate;

// How mature a RunStyle pattern is, lowest to highest. The ordinal order is used
// directly: the service compares stages to ask "did this run advance the pattern?"
// (a strictly higher ordinal) and to pick the highest stage. NONE means "not a
// pattern yet." Each real stage carries its deterministic announcement headline.
enum RunStyleStage {
    NONE(null),
    EARLY("A RunStyle pattern is beginning to show."),
    FORMING("Your RunStyle is forming."),
    ESTABLISHED("This is becoming part of your RunStyle.");

    private final String headline;

    RunStyleStage(String headline) {
        this.headline = headline;
    }

    // The fixed opening line for an announcement at this stage, or null for NONE.
    String getHeadline() {
        return headline;
    }
}
