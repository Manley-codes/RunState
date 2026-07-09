package com.runstate;

import java.util.List;

/*
 * ComparisonInsight is an immutable summary of how the current run compares to
 * past comparable runs. ComparisonService builds it — ALL candidate selection and
 * aggregation lives there, not here and not in RunAgent. By the time this object
 * exists, negative deltas have already been filtered out: every outcome line is
 * positive or explanatory, so RunAgent can print what it holds without re-checking.
 */
public class ComparisonInsight {

    // A ready-to-use "no comparison" result, returned when there are no comparable
    // runs or nothing survived the negative pre-filter. RunAgent adds nothing for it.
    public static final ComparisonInsight NONE =
            new ComparisonInsight(null, 0, null, List.of(), null);

    // How the comparable runs were matched, e.g. "same route, similar distance,
    // same pre-run energy". Null when there is no comparison.
    private final String basis;

    // How many previous comparable runs back this insight (this drives confidence).
    private final int comparableCount;

    // Runner-facing confidence phrase, set by the service from comparableCount,
    // e.g. "last comparable run" or "strong personal pattern". Null when none.
    private final String confidence;

    // Positive / explanatory outcome lines (median-based), already filtered and
    // ready for the prompt. Empty when there is no insight.
    private final List<String> outcomeLines;

    // Optional hedged context ("warm weather may explain higher effort"), or null.
    private final String contextNote;

    public ComparisonInsight(String basis, int comparableCount, String confidence,
                             List<String> outcomeLines, String contextNote) {
        this.basis = basis;
        this.comparableCount = comparableCount;
        this.confidence = confidence;
        // Defensive copy: nobody can mutate our list after construction, and the
        // copy is itself unmodifiable. That keeps this a truly immutable value object.
        this.outcomeLines = List.copyOf(outcomeLines);
        this.contextNote = contextNote;
    }

    // True when there is a real comparison worth surfacing. RunAgent checks this
    // before adding ANY comparison lines to the prompt or the fallback.
    public boolean hasInsight() {
        return !outcomeLines.isEmpty();
    }

    public String getBasis() {
        return basis;
    }

    public int getComparableCount() {
        return comparableCount;
    }

    public String getConfidence() {
        return confidence;
    }

    public List<String> getOutcomeLines() {
        return outcomeLines;
    }

    public String getContextNote() {
        return contextNote;
    }
}
