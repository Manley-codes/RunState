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
            new ComparisonInsight(null, List.of(), null);

    // How the comparable runs were matched, e.g. "same route" or "similar distance".
    // Null when there is no comparison.
    private final String basis;

    // Positive / explanatory outcomes, each carrying its own evidence count and
    // confidence phrase. Empty when there is no insight.
    private final List<ComparisonOutcome> outcomes;

    // Optional hedged context ("warm weather may explain higher effort"), or null.
    private final String contextNote;

    public ComparisonInsight(String basis, List<ComparisonOutcome> outcomes, String contextNote) {
        this.basis = basis;
        // Defensive copy keeps this a truly immutable value object.
        this.outcomes = List.copyOf(outcomes);
        this.contextNote = contextNote;
    }

    // True when there is a real comparison worth surfacing.
    public boolean hasInsight() {
        return !outcomes.isEmpty();
    }

    public String getBasis() {
        return basis;
    }

    public List<ComparisonOutcome> getOutcomes() {
        return outcomes;
    }

    public String getContextNote() {
        return contextNote;
    }
}
