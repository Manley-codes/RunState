package com.runstate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
 * ComparisonService turns a run plus the runner's history into a ComparisonInsight.
 * It is the isolated home for ALL candidate selection and aggregation (SRP, like
 * WeatherService) — RunAgent never does this work itself. Everything here is static:
 * the service holds no state, it just computes an answer from its inputs.
 *
 * Current status: candidate selection, signal-specific evidence pools, median aggregation,
 * confidence tiers, and the negative pre-filter are built. The strict RunStyle evidence path
 * remains separate near the bottom of this class.
 */
public class ComparisonService {

    // Only compare against runs from the last 180 days (Manley, July 7 2026).
    private static final int RECENCY_DAYS = 180;

    // At most the 10 most recent matches feed a comparison — keeps it "recent you".
    private static final int MAX_CANDIDATES = 10;

    // Similar-distance band: whichever is larger of half a mile or 20% of distance.
    private static final double MIN_DISTANCE_BAND = 0.5;
    private static final double DISTANCE_BAND_FRACTION = 0.20;

    // Entry point: summarize how THIS run compares to comparable past runs.
    public static ComparisonInsight analyze(Run current, List<Run> history) {
        if (current == null || history == null) {
            return ComparisonInsight.NONE;
        }
        List<Run> candidates = selectCandidates(current, history);
        if (candidates.isEmpty()) {
            return ComparisonInsight.NONE;
        }

        // Energy pool: candidates with both energy readings — backs State Lift only.
        List<Run> energyPool = new ArrayList<>();
        for (Run r : candidates) {
            if (r.getPreRunEnergy() != null && r.getPostRunEnergy() != null) {
                energyPool.add(r);
            }
        }

        // Effort pool: candidates with effort recorded — backs all three effort signals.
        // Pace, distance, and effort medians for those signals all come from this pool.
        List<Run> effortPool = new ArrayList<>();
        for (Run r : candidates) {
            if (r.getEffortLevel() != null) {
                effortPool.add(r);
            }
        }

        double medLift   = medianEnergyLift(energyPool);
        double medPace   = medianPace(effortPool);
        double medEffort = medianEffort(effortPool);

        // Derive signals in priority order. Each returns null unless its delta is
        // positive or explanatory — THAT is the negative pre-filter: nothing negative
        // (slower, weaker, unexplained higher effort) is ever turned into an outcome.
        List<ComparisonOutcome> outcomes = new ArrayList<>();
        addIfPresent(outcomes, stateLiftOutcome(current, medLift, energyPool.size()));
        addIfPresent(outcomes, quietGainOutcome(current, medEffort, medPace, effortPool.size()));
        addIfPresent(outcomes, sameCostBetterOutcome(current, medEffort, medPace, effortPool.size()));
        addIfPresent(outcomes, demandExplainedOutcome(current, effortPool, medEffort, effortPool.size()));

        // If nothing productive survived the filter, there is no comparison to make.
        if (outcomes.isEmpty()) {
            return ComparisonInsight.NONE;
        }

        String basis = describeBasis(matchedByRoute(current, candidates));
        // A weather hedge only rides along when effort was actually recorded on this run.
        String contextNote =
                Double.isNaN(effortValue(current)) ? null : weatherContextNote(current);

        return new ComparisonInsight(basis, outcomes, contextNote);
    }

    // Picks the previous runs worth comparing against: recent, and matched by route
    // first (route implicitly controls terrain, which we have no elevation data for),
    // falling back to similar distance when the current run has no shared route.
    private static List<Run> selectCandidates(Run current, List<Run> history) {
        // First narrow to previous runs inside the recency window.
        List<Run> recentPrevious = new ArrayList<>();
        for (Run past : history) {
            if (past != current && withinRecency(current, past)) {
                recentPrevious.add(past);
            }
        }

        List<Run> matches = new ArrayList<>();

        // Preferred: same route as the current run.
        String currentRoute = normalizeRoute(current.getRouteName());
        if (currentRoute != null) {
            for (Run past : recentPrevious) {
                if (currentRoute.equals(normalizeRoute(past.getRouteName()))) {
                    matches.add(past);
                }
            }
        }

        // Fallback: similar distance, used when there's no route or no route match.
        if (matches.isEmpty()) {
            for (Run past : recentPrevious) {
                if (similarDistance(current, past)) {
                    matches.add(past);
                }
            }
        }

        // Most recent first, then cap at the 10 nearest in time.
        matches.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        if (matches.size() > MAX_CANDIDATES) {
            return new ArrayList<>(matches.subList(0, MAX_CANDIDATES));
        }
        return matches;
    }

    // A candidate counts as recent if it falls within RECENCY_DAYS before the current
    // run and not after it (we compare against earlier runs, never later ones).
    private static boolean withinRecency(Run current, Run candidate) {
        LocalDate currentDate = current.getDate();
        LocalDate cutoff = currentDate.minusDays(RECENCY_DAYS);
        LocalDate candidateDate = candidate.getDate();
        return !candidateDate.isBefore(cutoff) && !candidateDate.isAfter(currentDate);
    }

    // Canonical route key: trimmed and lower-cased so "Cedar Trail " and "cedar trail"
    // match. Returns null for a missing or blank route (which then can't route-match).
    private static String normalizeRoute(String route) {
        if (route == null) {
            return null;
        }
        String trimmed = route.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    // True when the candidate's distance is within max(0.5 mi, 20%) of the current run.
    // Both distances are normalized to miles so mile and kilometer runs compare fairly.
    private static boolean similarDistance(Run current, Run candidate) {
        double currentMiles = current.getDistanceInMiles();
        double band = Math.max(MIN_DISTANCE_BAND, currentMiles * DISTANCE_BAND_FRACTION);
        return Math.abs(candidate.getDistanceInMiles() - currentMiles) <= band;
    }

    // Turns the number of comparable runs into the runner-facing confidence phrase.
    // Tiers (handoff): 1 single · 2–4 early · 5–7 recent pattern · 8+ strong.
    private static String confidencePhrase(int count) {
        if (count <= 0) return null;
        if (count == 1) return "last comparable run";
        if (count <= 4) return "early signal";
        if (count <= 7) return "recent pattern";
        return "strong personal pattern";
    }

    // Describes how candidates were matched, for the "Comparable run basis" line.
    private static String describeBasis(boolean matchedByRoute) {
        return matchedByRoute ? "same route" : "similar distance";
    }

    // --- Aggregation helpers (Chunk B) — the toolkit Chunk C uses to compare the
    // current run against the median of its comparables. Median (not mean) is the
    // handoff's choice: robust to one weird run when the sample is small. Each
    // "median..." helper returns NaN when no candidate carries that data, so the
    // caller can cleanly skip a signal it can't support.

    // Median of a list of values. Sorts a COPY so the caller's list order is left
    // intact. Assumes a non-empty list. Even count → average of the two middle values.
    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int size = sorted.size();
        int mid = size / 2;
        if (size % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    // The single representative RPE number for an effort level — the midpoint of its
    // range (HIGH_COST 6–7 → 6.5). Turns an enum into one comparable number.
    private static double effortMidpoint(EffortLevel effort) {
        return (effort.getMinRpe() + effort.getMaxRpe()) / 2.0;
    }

    // A run's representative effort, or NaN when effort wasn't recorded. The NaN is
    // how legacy NULL-effort rows stay out of effort signals while still counting
    // for route/distance/state ones.
    private static double effortValue(Run run) {
        EffortLevel effort = run.getEffortLevel();
        return effort == null ? Double.NaN : effortMidpoint(effort);
    }

    // Median pace (min/mile) across the candidates. Every run has a pace, so this is
    // defined whenever there is at least one candidate.
    private static double medianPace(List<Run> runs) {
        List<Double> paces = new ArrayList<>();
        for (Run run : runs) {
            paces.add(run.getPaceInMinutesPerMile());
        }
        return paces.isEmpty() ? Double.NaN : median(paces);
    }

    // Median start-to-finish energy change (post - pre) across candidates that recorded
    // both, or NaN when none did. Controls for starting point without shrinking the pool.
    private static double medianEnergyLift(List<Run> runs) {
        List<Double> lifts = new ArrayList<>();
        for (Run run : runs) {
            EnergyLevel pre = run.getPreRunEnergy();
            EnergyLevel post = run.getPostRunEnergy();
            if (pre != null && post != null) {
                lifts.add((double) (post.getValue() - pre.getValue()));
            }
        }
        return lifts.isEmpty() ? Double.NaN : median(lifts);
    }

    // Median effort cost (RPE midpoint) across candidates that recorded effort, or
    // NaN when none did (so effort signals simply don't fire on legacy-only data).
    private static double medianEffort(List<Run> runs) {
        List<Double> values = new ArrayList<>();
        for (Run run : runs) {
            double value = effortValue(run);
            if (!Double.isNaN(value)) {
                values.add(value);
            }
        }
        return values.isEmpty() ? Double.NaN : median(values);
    }

    // --- Signal derivation + negative pre-filter (Chunk C) ---

    // Half an RPE point — the "close enough" band that splits effort into clearly
    // lower / about the same / clearly higher, so at most one effort signal fires.
    private static final double EFFORT_EPSILON = 0.5;

    // Adds an outcome only when its signal fired (non-null).
    private static void addIfPresent(List<ComparisonOutcome> outcomes, ComparisonOutcome outcome) {
        if (outcome != null) {
            outcomes.add(outcome);
        }
    }

    // Was selection route-based? True only when the current run has a route and the
    // chosen candidates share it (distance-fallback candidates do not).
    private static boolean matchedByRoute(Run current, List<Run> candidates) {
        String route = normalizeRoute(current.getRouteName());
        if (route == null || candidates.isEmpty()) {
            return false;
        }
        return route.equals(normalizeRoute(candidates.get(0).getRouteName()));
    }

    // Median distance (miles) across candidates — used to judge "went farther today".
    private static double medianDistance(List<Run> runs) {
        List<Double> distances = new ArrayList<>();
        for (Run run : runs) {
            distances.add(run.getDistanceInMiles());
        }
        return distances.isEmpty() ? Double.NaN : median(distances);
    }

    // Single-run language at tier 1, per the confidence tiers.
    private static String comparableNoun(int count) {
        return count == 1 ? "last comparable run" : "comparable runs";
    }

    // Signal 1 — State Lift: bigger start-to-finish energy climb than usual.
    // Uses energyPool so its count only reflects runs where lift was measurable.
    private static ComparisonOutcome stateLiftOutcome(Run current, double medLift, int energyCount) {
        if (energyCount == 0 || Double.isNaN(medLift)) return null;
        EnergyLevel pre = current.getPreRunEnergy();
        EnergyLevel post = current.getPostRunEnergy();
        if (pre == null || post == null) return null;
        int lift = post.getValue() - pre.getValue();
        if (lift > medLift) {
            String line = "Bigger start-to-finish energy lift than your "
                    + comparableNoun(energyCount) + ".";
            return new ComparisonOutcome(line, energyCount, confidencePhrase(energyCount));
        }
        return null;
    }

    // Signal 2 — Quiet Gain: same output, clearly lower effort than usual.
    // Uses effortPool so its count only reflects runs where effort was recorded.
    private static ComparisonOutcome quietGainOutcome(Run current, double medEffort,
                                                      double medPace, int effortCount) {
        if (effortCount == 0) return null;
        double effort = effortValue(current);
        if (Double.isNaN(effort) || Double.isNaN(medEffort)) return null;
        double pace = current.getPaceInMinutesPerMile();
        boolean lowerEffort = effort < medEffort - EFFORT_EPSILON;
        boolean paceHeld = Double.isNaN(medPace) || pace <= medPace * 1.05;
        if (lowerEffort && paceHeld) {
            String line = "Same output, lower effort than your " + comparableNoun(effortCount)
                    + " — that's progress your pace won't show yet.";
            return new ComparisonOutcome(line, effortCount, confidencePhrase(effortCount));
        }
        return null;
    }

    // Signal 3 — Same cost, better output: about the same effort, but faster.
    private static ComparisonOutcome sameCostBetterOutcome(Run current, double medEffort,
                                                           double medPace, int effortCount) {
        if (effortCount == 0) return null;
        double effort = effortValue(current);
        if (Double.isNaN(effort) || Double.isNaN(medEffort) || Double.isNaN(medPace)) return null;
        double pace = current.getPaceInMinutesPerMile();
        boolean sameCost = Math.abs(effort - medEffort) <= EFFORT_EPSILON;
        boolean faster = pace < medPace;
        if (sameCost && faster) {
            String line = "Same effort as your " + comparableNoun(effortCount) + ", but faster.";
            return new ComparisonOutcome(line, effortCount, confidencePhrase(effortCount));
        }
        return null;
    }

    // Signal 4 — Demand explained: today cost clearly MORE, but for a good reason.
    // Distance median also comes from effortPool so all benchmarks share one source.
    private static ComparisonOutcome demandExplainedOutcome(Run current, List<Run> effortPool,
                                                            double medEffort, int effortCount) {
        if (effortCount == 0) return null;
        double effort = effortValue(current);
        if (Double.isNaN(effort) || Double.isNaN(medEffort)
                || effort <= medEffort + EFFORT_EPSILON) return null;
        if (current.isLongestDistanceRecord() || current.isFastestAveragePaceRecord()) {
            String line = "Higher effort today — but you set a PR, so that's what it cost.";
            return new ComparisonOutcome(line, effortCount, confidencePhrase(effortCount));
        }
        double medDistance = medianDistance(effortPool);
        if (!Double.isNaN(medDistance) && current.getDistanceInMiles() > medDistance) {
            String line = "Higher effort today — but you went farther than your "
                    + comparableNoun(effortCount) + ".";
            return new ComparisonOutcome(line, effortCount, confidencePhrase(effortCount));
        }
        return null;
    }

    // Optional hedged weather note to accompany an effort line — explanation, never
    // causation. Only fires in genuinely hot or freezing conditions.
    private static String weatherContextNote(Run current) {
        Double apparent = current.getApparentTemperature();
        if (apparent != null && apparent >= 80) {
            return "Warm conditions may explain some of the effort.";
        }
        Double temp = current.getTemperature();
        if (temp != null && temp <= 32) {
            return "Cold conditions may explain some of the effort.";
        }
        return null;
    }

    // =====================================================================
    // Strict RunStyle evidence path (RunStyle V1, Step 2).
    //
    // Neither entry point calls the other, and their candidate/evidence orchestration stays
    // separate. They still share low-level helpers and thresholds below, so changes to shared
    // math must keep both the general-comparison and strict-RunStyle tests green.
    // RunStyleService (Step 3) consumes the typed evidence produced here.
    //
    // How "strict" differs from analyze()'s selection:
    //   - similar distance is ALWAYS required (analyze() lets a pure route match
    //     through at any distance);
    //   - there is an extra same-surface tier between route and bare distance.
    // The private helpers above (withinRecency, normalizeRoute, similarDistance,
    // the median* family, effortValue, EFFORT_EPSILON) are reused as-is.
    // =====================================================================

    // Bundles the chosen strict candidates with how they were matched, so the
    // evaluator can report the basis without recomputing it.
    private static final class StrictSelection {
        final List<Run> candidates;
        final String basis;

        StrictSelection(List<Run> candidates, String basis) {
            this.candidates = candidates;
            this.basis = basis;
        }
    }

    // Picks strict candidates in preference order, distance required at every tier:
    //   1. same route + similar distance
    //   2. same surface + similar distance
    //   3. similar distance alone
    // The first tier that yields any match wins (no blending across tiers).
    private static StrictSelection selectStrict(Run current, List<Run> history) {
        List<Run> recentPrevious = new ArrayList<>();
        for (Run past : history) {
            if (past != current && withinRecency(current, past)) {
                recentPrevious.add(past);
            }
        }

        // Tier 1 — same route AND similar distance.
        String route = normalizeRoute(current.getRouteName());
        if (route != null) {
            List<Run> tier = new ArrayList<>();
            for (Run past : recentPrevious) {
                if (route.equals(normalizeRoute(past.getRouteName()))
                        && similarDistance(current, past)) {
                    tier.add(past);
                }
            }
            if (!tier.isEmpty()) {
                return new StrictSelection(cap(tier), "same route");
            }
        }

        // Tier 2 — same surface AND similar distance.
        SurfaceType surface = current.getSurface();
        if (surface != null) {
            List<Run> tier = new ArrayList<>();
            for (Run past : recentPrevious) {
                if (surface == past.getSurface() && similarDistance(current, past)) {
                    tier.add(past);
                }
            }
            if (!tier.isEmpty()) {
                return new StrictSelection(cap(tier), "same surface");
            }
        }

        // Tier 3 — similar distance only.
        List<Run> tier = new ArrayList<>();
        for (Run past : recentPrevious) {
            if (similarDistance(current, past)) {
                tier.add(past);
            }
        }
        return new StrictSelection(cap(tier), "similar distance");
    }

    // Most-recent-first, then capped at MAX_CANDIDATES — same rule analyze() uses.
    private static List<Run> cap(List<Run> runs) {
        runs.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        if (runs.size() > MAX_CANDIDATES) {
            return new ArrayList<>(runs.subList(0, MAX_CANDIDATES));
        }
        return runs;
    }

    // Evaluates the four typed signals for the current run against its strict candidates,
    // recording which were MEASURABLE (enough data to judge) and which were SUPPORTED
    // (fired positive). Signal meanings stay aligned with the general path, while strict
    // candidate selection and evidence handling remain intentionally independent.
    static StrictEvidence evaluateStrict(Run current, List<Run> history) {
        if (current == null || history == null) {
            return StrictEvidence.NONE;
        }
        StrictSelection selection = selectStrict(current, history);
        List<Run> candidates = selection.candidates;
        if (candidates.isEmpty()) {
            return StrictEvidence.NONE;
        }

        Set<RunStyleSignal> measurable = EnumSet.noneOf(RunStyleSignal.class);
        Set<RunStyleSignal> supported = EnumSet.noneOf(RunStyleSignal.class);

        double medPace = medianPace(candidates);
        double medLift = medianEnergyLift(candidates);
        double medEffort = medianEffort(candidates);
        double medDistance = medianDistance(candidates);

        // STATE_LIFT — bigger start-to-finish lift than the median candidate. Needs
        // both energy readings here and lift data among the candidates.
        EnergyLevel pre = current.getPreRunEnergy();
        EnergyLevel post = current.getPostRunEnergy();
        if (pre != null && post != null && !Double.isNaN(medLift)) {
            measurable.add(RunStyleSignal.STATE_LIFT);
            if (post.getValue() - pre.getValue() > medLift) {
                supported.add(RunStyleSignal.STATE_LIFT);
            }
        }

        // The three effort signals share one precondition: effort recorded on this run
        // AND on at least one candidate. Legacy null-effort data simply stays unjudged.
        double effort = effortValue(current);
        boolean effortComparable = !Double.isNaN(effort) && !Double.isNaN(medEffort);
        double pace = current.getPaceInMinutesPerMile();

        // QUIET_GAIN — same output, clearly lower effort.
        if (effortComparable) {
            measurable.add(RunStyleSignal.QUIET_GAIN);
            boolean lowerEffort = effort < medEffort - EFFORT_EPSILON;
            boolean paceHeld = Double.isNaN(medPace) || pace <= medPace * 1.05;
            if (lowerEffort && paceHeld) {
                supported.add(RunStyleSignal.QUIET_GAIN);
            }
        }

        // SAME_COST_BETTER — about the same effort, but faster.
        if (effortComparable && !Double.isNaN(medPace)) {
            measurable.add(RunStyleSignal.SAME_COST_BETTER);
            boolean sameCost = Math.abs(effort - medEffort) <= EFFORT_EPSILON;
            if (sameCost && pace < medPace) {
                supported.add(RunStyleSignal.SAME_COST_BETTER);
            }
        }

        // DEMAND_EXPLAINED — clearly higher effort, justified by a PR or more distance.
        if (effortComparable) {
            measurable.add(RunStyleSignal.DEMAND_EXPLAINED);
            boolean higher = effort > medEffort + EFFORT_EPSILON;
            boolean explained = current.isLongestDistanceRecord()
                    || current.isFastestAveragePaceRecord()
                    || (!Double.isNaN(medDistance) && current.getDistanceInMiles() > medDistance);
            if (higher && explained) {
                supported.add(RunStyleSignal.DEMAND_EXPLAINED);
            }
        }

        return new StrictEvidence(candidates.size(), selection.basis, measurable, supported);
    }
}
