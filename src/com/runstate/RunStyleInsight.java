package com.runstate;

import java.util.List;

/*
 * Immutable result of RunStyleService.analyze. It carries the current RunStyle state
 * (stage, primary pattern, the counts, any qualified facets, an optional habit line)
 * AND the one thing the caller acts on: whether THIS run should announce anything.
 *
 * Announcements fire only when the current run first creates an EARLY pattern, advances
 * one to FORMING/ESTABLISHED, or first qualifies a new facet — never repeats, never
 * downgrades. So shouldAnnounce() can be false even when a strong pattern exists (it was
 * already announced on an earlier run). getMessage() is the fully assembled text when
 * shouldAnnounce() is true, and null otherwise.
 *
 * The structured getters are package-private: they exist mainly so tests can assert on
 * the decision without parsing the message string. Only shouldAnnounce()/getMessage() are
 * public, because those are all the console needs.
 */
public final class RunStyleInsight {

    // Shared "nothing to say" result — no pattern, no announcement.
    public static final RunStyleInsight NONE =
            new RunStyleInsight(false, RunStyleStage.NONE, null, 0, 0, List.of(), null, null);

    private final boolean announce;
    private final RunStyleStage stage;
    private final RunStyleFamily primary;
    private final int supportCount;
    private final int opportunityCount;
    private final List<String> facetLines;
    private final String habitLine;
    private final String message;

    RunStyleInsight(boolean announce, RunStyleStage stage, RunStyleFamily primary,
                    int supportCount, int opportunityCount, List<String> facetLines,
                    String habitLine, String message) {
        this.announce = announce;
        this.stage = stage;
        this.primary = primary;
        this.supportCount = supportCount;
        this.opportunityCount = opportunityCount;
        this.facetLines = List.copyOf(facetLines);
        this.habitLine = habitLine;
        this.message = message;
    }

    // Whether the current run should print an announcement.
    public boolean shouldAnnounce() {
        return announce;
    }

    // The assembled announcement text when shouldAnnounce() is true, else null.
    public String getMessage() {
        return message;
    }

    RunStyleStage getStage() {
        return stage;
    }

    RunStyleFamily getPrimary() {
        return primary;
    }

    int getSupportCount() {
        return supportCount;
    }

    int getOpportunityCount() {
        return opportunityCount;
    }

    List<String> getFacetLines() {
        return facetLines;
    }

    String getHabitLine() {
        return habitLine;
    }
}
