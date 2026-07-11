package com.runstate;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/*
 * Immutable, typed result of the strict RunStyle evidence path
 * (ComparisonService.evaluateStrict). For ONE run judged against its strict
 * candidates it records, per signal, two separate facts:
 *
 *   MEASURABLE — there was enough data to judge the signal at all
 *   SUPPORTED  — the signal actually fired (a positive outcome)
 *
 * Keeping the two apart is the whole point of this class. RunStyleService needs to
 * know a run was an OPPORTUNITY for a pattern (measurable) even on the runs where the
 * pattern did not fire — that is how "supported X of Y opportunities" gets counted.
 *
 * The signal sets use EnumSet: a Set implementation specialised for enums that is
 * backed by a bit field, so membership checks are effectively free. Both sets are
 * wrapped unmodifiable, and this class has only getters, so once built a StrictEvidence
 * can never change (immutability — the same discipline as RunContext and WeatherData).
 */
final class StrictEvidence {

    // Shared empty result: no comparable candidates, so nothing was measurable. Safe to
    // share because the object is immutable.
    static final StrictEvidence NONE =
            new StrictEvidence(0, null,
                    EnumSet.noneOf(RunStyleSignal.class),
                    EnumSet.noneOf(RunStyleSignal.class));

    // How many strict candidates backed this evaluation.
    private final int candidateCount;

    // How the candidates were matched: "same route" / "same surface" / "similar distance",
    // or null when there were none.
    private final String basis;

    // Signals that had enough data to judge, and the subset of those that fired.
    private final Set<RunStyleSignal> measurable;
    private final Set<RunStyleSignal> supported;

    StrictEvidence(int candidateCount, String basis,
                   Set<RunStyleSignal> measurable, Set<RunStyleSignal> supported) {
        this.candidateCount = candidateCount;
        this.basis = basis;
        // Defensive, unmodifiable copies so the caller's sets can't mutate our state.
        this.measurable = unmodifiableCopy(measurable);
        this.supported = unmodifiableCopy(supported);
    }

    // Copies into an EnumSet and wraps it unmodifiable. EnumSet.copyOf rejects an empty
    // source, so an empty set is built the long way instead.
    private static Set<RunStyleSignal> unmodifiableCopy(Set<RunStyleSignal> source) {
        Set<RunStyleSignal> copy = source.isEmpty()
                ? EnumSet.noneOf(RunStyleSignal.class)
                : EnumSet.copyOf(source);
        return Collections.unmodifiableSet(copy);
    }

    // Number of strict candidates this evaluation was based on.
    int getCandidateCount() {
        return candidateCount;
    }

    // How those candidates were matched, or null when there were none.
    String getBasis() {
        return basis;
    }

    // True when the signal had enough data to be judged this run (an opportunity).
    boolean isMeasurable(RunStyleSignal signal) {
        return measurable.contains(signal);
    }

    // True when the signal fired this run (a positive outcome).
    boolean isSupported(RunStyleSignal signal) {
        return supported.contains(signal);
    }

    // True when at least one signal could be judged at all.
    boolean hasAnyMeasurable() {
        return !measurable.isEmpty();
    }
}
