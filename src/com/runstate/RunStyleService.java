package com.runstate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/*
 * RunStyleService turns a run plus the runner's history into a RunStyleInsight. It is the
 * isolated, deterministic home for the RunStyle "brain" (SRP, like ComparisonService and
 * WeatherService) — Runner.detectRunStyle just delegates here. Nothing here is ever sent to
 * the AI; the profile stays local.
 *
 * The whole model rests on three PRIMARY families (RunStyleFamily), each of which a given
 * run may or may not be an OPPORTUNITY for (was there enough data to judge it) and, if so,
 * whether it was SUPPORTED (the pattern held). Stages come from how many of the latest
 * opportunities were supported; the strongest staged family becomes the primary pattern.
 * Secondary context (surface/company/weather/music/shoes) can only decorate that result as
 * facets or a habit line — never create or strengthen a primary pattern.
 *
 * POINT-IN-TIME RULE: a run is judged using only the runs that precede it in chronological
 * order (run_date, then list position as the same-day tiebreak). Future runs never change a
 * past run's classification. Announcements are computed by evaluating the full history and
 * the history-without-the-current-run, then diffing — so a pattern is announced exactly once,
 * when it first advances, and never downgraded or repeated.
 */
public final class RunStyleService {

    // Stage thresholds: "supported N of the latest W opportunities".
    private static final int EARLY_WINDOW = 4, EARLY_MIN = 3;
    private static final int FORMING_WINDOW = 5, FORMING_MIN = 4;
    private static final int ESTABLISHED_WINDOW = 7, ESTABLISHED_MIN = 6;

    // Facet thresholds.
    private static final int FACET_DESCRIPTIVE_MIN = 3;          // supporting runs with the context
    private static final int FACET_COMPARATIVE_MIN_WITH = 5;     // opportunities with the context
    private static final int FACET_COMPARATIVE_MIN_WITHOUT = 5;  // opportunities without it
    private static final double FACET_COMPARATIVE_MIN_RATE = 0.80;   // support rate with the context
    private static final double FACET_COMPARATIVE_MIN_LEAD = 0.30;   // lead over runs without it

    // Habit thresholds, over the last 10 runs.
    private static final int HABIT_RECENT_WINDOW = 10;
    private static final int HABIT_MIN_RECORDED = 5;            // recorded on >=5 of the last 10
    private static final double HABIT_PRESENCE_RATE = 0.70;     // same value in >=70% of those

    // ---- entry point --------------------------------------------------------

    // Summarizes the runner's RunStyle as of the current run, and decides whether this run
    // triggers an announcement. Returns RunStyleInsight.NONE when there is no primary pattern.
    public static RunStyleInsight analyze(Run current, List<Run> history) {
        if (current == null || history == null) {
            return RunStyleInsight.NONE;
        }
        List<Run> ordered = chronological(history);
        if (!ordered.contains(current)) {
            return RunStyleInsight.NONE;
        }

        // State with the current run included, and the state as it was just before it.
        State full = computeState(ordered);
        if (full.primary == null) {
            return RunStyleInsight.NONE;
        }
        List<Run> beforeCurrent = new ArrayList<>(ordered);
        beforeCurrent.remove(current);
        State prev = computeState(beforeCurrent);

        // A stage advance is judged on the SAME family that is primary now, so changing
        // which family leads never counts as a downgrade.
        RunStyleStage fullStage = full.stages.get(full.primary);
        RunStyleStage prevStage = prev.stages.get(full.primary);
        boolean stageAdvance = fullStage.ordinal() > prevStage.ordinal();

        List<String> fullFacets = facetLines(full);
        List<String> prevFacets = facetLines(prev);
        boolean newFacet = false;
        for (String facet : fullFacets) {
            if (!prevFacets.contains(facet)) {
                newFacet = true;
                break;
            }
        }

        boolean announce = stageAdvance || newFacet;

        List<Opp> primaryOpps = full.opps.get(full.primary);
        int supportCount = supportedCount(primaryOpps);
        int opportunityCount = primaryOpps.size();
        String habit = habitLine(full);

        String message = announce
                ? assembleMessage(fullStage, full.primary, supportCount, opportunityCount, fullFacets, habit)
                : null;

        return new RunStyleInsight(announce, fullStage, full.primary,
                supportCount, opportunityCount, fullFacets, habit, message);
    }

    // ---- state computation --------------------------------------------------

    // The full RunStyle picture for one run list: the opportunities per family, each
    // family's stage, and the chosen primary.
    private static final class State {
        final List<Run> ordered;
        final EnumMap<RunStyleFamily, List<Opp>> opps;
        final EnumMap<RunStyleFamily, RunStyleStage> stages;
        final RunStyleFamily primary;

        State(List<Run> ordered, EnumMap<RunStyleFamily, List<Opp>> opps,
              EnumMap<RunStyleFamily, RunStyleStage> stages, RunStyleFamily primary) {
            this.ordered = ordered;
            this.opps = opps;
            this.stages = stages;
            this.primary = primary;
        }
    }

    // One opportunity: a run where a family was measurable, plus whether it was supported.
    private static final class Opp {
        final Run run;
        final boolean supported;

        Opp(Run run, boolean supported) {
            this.run = run;
            this.supported = supported;
        }
    }

    // Walks the runs in order, building each family's opportunity list point-in-time, then
    // derives stages and the primary.
    private static State computeState(List<Run> ordered) {
        EnumMap<RunStyleFamily, List<Opp>> opps = new EnumMap<>(RunStyleFamily.class);
        for (RunStyleFamily family : RunStyleFamily.values()) {
            opps.put(family, new ArrayList<>());
        }

        for (int i = 0; i < ordered.size(); i++) {
            Run run = ordered.get(i);
            List<Run> prior = ordered.subList(0, i);  // only runs before this one

            // State Lift — direct start-to-finish lift, judged on this run alone (so a
            // habitual lifter stays detectable; it is NOT compared against a median).
            EnergyLevel pre = run.getPreRunEnergy();
            EnergyLevel post = run.getPostRunEnergy();
            if (pre != null && post != null) {
                opps.get(RunStyleFamily.STATE_LIFT).add(new Opp(run, post.getValue() > pre.getValue()));
            }

            // Efficiency Gain — needs the strict comparison to be effort-evaluable. Uses
            // ONLY prior runs, which is where the point-in-time rule bites.
            StrictEvidence evidence = ComparisonService.evaluateStrict(run, prior);
            boolean effMeasurable = evidence.isMeasurable(RunStyleSignal.QUIET_GAIN)
                    || evidence.isMeasurable(RunStyleSignal.SAME_COST_BETTER);
            if (effMeasurable) {
                boolean supported = evidence.isSupported(RunStyleSignal.QUIET_GAIN)
                        || evidence.isSupported(RunStyleSignal.SAME_COST_BETTER);
                opps.get(RunStyleFamily.EFFICIENCY_GAIN).add(new Opp(run, supported));
            }

            // Controlled Finish — finished strong without overreaching.
            if (post != null && run.getEffortLevel() != null) {
                opps.get(RunStyleFamily.CONTROLLED_FINISH).add(new Opp(run, controlledFinishSupported(run)));
            }
        }

        EnumMap<RunStyleFamily, RunStyleStage> stages = new EnumMap<>(RunStyleFamily.class);
        for (RunStyleFamily family : RunStyleFamily.values()) {
            stages.put(family, stageOf(opps.get(family)));
        }

        RunStyleFamily primary = selectPrimary(opps, stages);
        return new State(ordered, opps, stages, primary);
    }

    // Controlled Finish support: moderate/high finish, low/moderate effort, and — when a
    // starting point exists — energy did not decline.
    private static boolean controlledFinishSupported(Run run) {
        EnergyLevel post = run.getPostRunEnergy();
        EffortLevel effort = run.getEffortLevel();
        boolean strongFinish = post.getValue() >= EnergyLevel.MODERATE.getValue();
        boolean easyEffort = effort == EffortLevel.LOW_COST || effort == EffortLevel.MODERATE_COST;
        EnergyLevel pre = run.getPreRunEnergy();
        boolean noDecline = pre == null || post.getValue() >= pre.getValue();
        return strongFinish && easyEffort && noDecline;
    }

    // ---- stages -------------------------------------------------------------

    // Highest stage whose window+minimum both fit the latest opportunities.
    private static RunStyleStage stageOf(List<Opp> opps) {
        int n = opps.size();
        if (n >= ESTABLISHED_WINDOW && supportedInLast(opps, ESTABLISHED_WINDOW) >= ESTABLISHED_MIN) {
            return RunStyleStage.ESTABLISHED;
        }
        if (n >= FORMING_WINDOW && supportedInLast(opps, FORMING_WINDOW) >= FORMING_MIN) {
            return RunStyleStage.FORMING;
        }
        if (n >= EARLY_WINDOW && supportedInLast(opps, EARLY_WINDOW) >= EARLY_MIN) {
            return RunStyleStage.EARLY;
        }
        return RunStyleStage.NONE;
    }

    // Supported count among the last `window` opportunities.
    private static int supportedInLast(List<Opp> opps, int window) {
        int start = Math.max(0, opps.size() - window);
        int count = 0;
        for (int i = start; i < opps.size(); i++) {
            if (opps.get(i).supported) {
                count++;
            }
        }
        return count;
    }

    private static int supportedCount(List<Opp> opps) {
        int count = 0;
        for (Opp opp : opps) {
            if (opp.supported) {
                count++;
            }
        }
        return count;
    }

    // ---- primary selection --------------------------------------------------

    // Chooses the leading family among those that reached a stage: highest stage, then
    // support rate, then support count, then most recent supported run, then the family
    // priority baked into RunStyleFamily's order. Returns null when nothing is staged.
    private static RunStyleFamily selectPrimary(EnumMap<RunStyleFamily, List<Opp>> opps,
                                                EnumMap<RunStyleFamily, RunStyleStage> stages) {
        List<RunStyleFamily> staged = new ArrayList<>();
        for (RunStyleFamily family : RunStyleFamily.values()) {
            if (stages.get(family) != RunStyleStage.NONE) {
                staged.add(family);
            }
        }
        if (staged.isEmpty()) {
            return null;
        }

        staged.sort(Comparator
                .comparingInt((RunStyleFamily f) -> stages.get(f).ordinal()).reversed()
                .thenComparing(Comparator.comparingDouble((RunStyleFamily f) -> supportRate(opps.get(f))).reversed())
                .thenComparing(Comparator.comparingInt((RunStyleFamily f) -> supportedCount(opps.get(f))).reversed())
                .thenComparing(Comparator.comparing((RunStyleFamily f) -> latestSupportedDate(opps.get(f)),
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .thenComparingInt(Enum::ordinal));
        return staged.get(0);
    }

    private static double supportRate(List<Opp> opps) {
        return opps.isEmpty() ? 0.0 : (double) supportedCount(opps) / opps.size();
    }

    // Date of the most recent supported opportunity, or null if none were supported.
    private static LocalDate latestSupportedDate(List<Opp> opps) {
        LocalDate latest = null;
        for (Opp opp : opps) {
            if (opp.supported && (latest == null || opp.run.getDate().isAfter(latest))) {
                latest = opp.run.getDate();
            }
        }
        return latest;
    }

    // ---- facets (association layer) -----------------------------------------

    // One qualified facet candidate, before the best-of-group pick.
    private static final class FacetCandidate {
        final ContextDimension dimension;
        final boolean contrasted;   // comparative wording earned (vs plain descriptive)
        final int supportCount;     // supporting runs carrying this context value
        final LocalDate recency;    // most recent such supporting run
        final String line;

        FacetCandidate(ContextDimension dimension, boolean contrasted,
                       int supportCount, LocalDate recency, String line) {
            this.dimension = dimension;
            this.contrasted = contrasted;
            this.supportCount = supportCount;
            this.recency = recency;
            this.line = line;
        }
    }

    // Up to one condition facet + one personal facet describing the context that tends to
    // accompany the primary pattern's supporting runs.
    private static List<String> facetLines(State state) {
        if (state.primary == null) {
            return List.of();
        }
        List<Opp> primaryOpps = state.opps.get(state.primary);
        List<Run> supportingRuns = new ArrayList<>();
        for (Opp opp : primaryOpps) {
            if (opp.supported) {
                supportingRuns.add(opp.run);
            }
        }

        List<FacetCandidate> candidates = new ArrayList<>();
        for (ContextDimension dimension : ContextDimension.values()) {
            // Distinct recorded values among the supporting runs.
            Set<Object> values = new LinkedHashSet<>();
            for (Run run : supportingRuns) {
                Object value = dimension.value(run);
                if (value != null) {
                    values.add(value);
                }
            }
            for (Object value : values) {
                FacetCandidate candidate = evaluateFacet(dimension, value, supportingRuns, primaryOpps);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        List<String> lines = new ArrayList<>();
        FacetCandidate condition = bestFacet(candidates, true);
        if (condition != null) {
            lines.add(condition.line);
        }
        FacetCandidate personal = bestFacet(candidates, false);
        if (personal != null) {
            lines.add(personal.line);
        }
        return lines;
    }

    // Builds a facet candidate for one (dimension, value) if it clears the descriptive OR
    // comparative bar, else null.
    private static FacetCandidate evaluateFacet(ContextDimension dimension, Object value,
                                                List<Run> supportingRuns, List<Opp> primaryOpps) {
        int descriptiveCount = 0;
        LocalDate recency = null;
        for (Run run : supportingRuns) {
            if (value.equals(dimension.value(run))) {
                descriptiveCount++;
                if (recency == null || run.getDate().isAfter(recency)) {
                    recency = run.getDate();
                }
            }
        }
        boolean descriptive = descriptiveCount >= FACET_DESCRIPTIVE_MIN;

        // Comparative: split the primary's opportunities into those carrying this value and
        // those carrying a different recorded value (unrecorded runs are excluded from both).
        List<Opp> with = new ArrayList<>();
        List<Opp> without = new ArrayList<>();
        for (Opp opp : primaryOpps) {
            Object v = dimension.value(opp.run);
            if (v == null) {
                continue;
            }
            if (value.equals(v)) {
                with.add(opp);
            } else {
                without.add(opp);
            }
        }
        boolean contrasted = false;
        if (with.size() >= FACET_COMPARATIVE_MIN_WITH && without.size() >= FACET_COMPARATIVE_MIN_WITHOUT) {
            double rateWith = supportRate(with);
            double rateWithout = supportRate(without);
            if (rateWith >= FACET_COMPARATIVE_MIN_RATE
                    && (rateWith - rateWithout) >= FACET_COMPARATIVE_MIN_LEAD) {
                contrasted = true;
            }
        }

        if (!descriptive && !contrasted) {
            return null;
        }
        String line = contrasted ? dimension.contrast(value) : dimension.describe(value);
        return new FacetCandidate(dimension, contrasted, descriptiveCount, recency, line);
    }

    // Best candidate in a group (condition vs personal): contrasted first, then support
    // count, then recency, then dimension preference (surface > company > weather; music >
    // shoes, encoded by ContextDimension order).
    private static FacetCandidate bestFacet(List<FacetCandidate> candidates, boolean condition) {
        FacetCandidate best = null;
        Comparator<FacetCandidate> better = Comparator
                .comparing((FacetCandidate c) -> c.contrasted)                       // false < true
                .thenComparingInt(c -> c.supportCount)
                .thenComparing(c -> c.recency, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(c -> -c.dimension.ordinal());                          // lower ordinal preferred
        for (FacetCandidate candidate : candidates) {
            if (candidate.dimension.condition != condition) {
                continue;
            }
            if (best == null || better.compare(candidate, best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    // ---- habit identity -----------------------------------------------------

    // The single most consistent recent context, worded by frequency (never a title), or
    // null when nothing is recorded often enough. Independent of the primary pattern.
    private static String habitLine(State state) {
        int n = state.ordered.size();
        List<Run> recent = state.ordered.subList(Math.max(0, n - HABIT_RECENT_WINDOW), n);

        ContextDimension bestDimension = null;
        Object bestValue = null;
        double bestRate = 0.0;
        for (ContextDimension dimension : ContextDimension.values()) {
            Map<Object, Integer> counts = new LinkedHashMap<>();
            int recorded = 0;
            for (Run run : recent) {
                Object value = dimension.value(run);
                if (value != null) {
                    recorded++;
                    counts.merge(value, 1, Integer::sum);
                }
            }
            if (recorded < HABIT_MIN_RECORDED) {
                continue;
            }
            Object dominant = null;
            int dominantCount = 0;
            for (Map.Entry<Object, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > dominantCount) {
                    dominantCount = entry.getValue();
                    dominant = entry.getKey();
                }
            }
            double rate = (double) dominantCount / recorded;
            if (rate >= HABIT_PRESENCE_RATE && rate > bestRate) {
                bestRate = rate;
                bestDimension = dimension;
                bestValue = dominant;
            }
        }
        return bestDimension == null ? null : bestDimension.habit(bestValue);
    }

    // ---- context dimensions -------------------------------------------------

    // The five secondary-context dimensions, each knowing how to read its value off a Run
    // and how to word itself. `condition` splits the two facet slots (surface/company/
    // weather are conditions; music/shoes are personal). Declaration order encodes the
    // tie-break preference within each group.
    private enum ContextDimension {
        SURFACE(true) {
            Object value(Run run) { return run.getSurface(); }
            String describe(Object v) { return "This pattern keeps showing up on " + word(v) + "."; }
            String contrast(Object v) { return "This pattern shows up more on " + word(v) + " than elsewhere."; }
            String habit(Object v) { return capitalize(word(v)) + " runs are becoming a regular part of how you run."; }
            private String word(Object v) { return ((SurfaceType) v).getLabel().toLowerCase(Locale.ROOT); }
        },
        COMPANY(true) {
            Object value(Run run) { return run.getRunCompany(); }
            String describe(Object v) { return "This pattern keeps showing up " + phrase(v) + "."; }
            String contrast(Object v) { return "This pattern shows up more " + phrase(v) + " than otherwise."; }
            String habit(Object v) { return capitalize(phrase(v)) + " is becoming your norm."; }
            private String phrase(Object v) {
                return v == RunCompany.SOLO ? "when you run solo" : "when you run with others";
            }
        },
        WEATHER(true) {
            Object value(Run run) { return weatherToken(run); }
            String describe(Object v) { return "This pattern keeps showing up in " + v + " conditions."; }
            String contrast(Object v) { return "This pattern shows up more in " + v + " conditions than otherwise."; }
            String habit(Object v) { return "You have been running in " + v + " conditions lately."; }
        },
        MUSIC(false) {
            Object value(Run run) { return run.getMusicMode(); }
            String describe(Object v) { return "This pattern keeps showing up " + phrase(v) + "."; }
            String contrast(Object v) { return "This pattern shows up more " + phrase(v) + " than otherwise."; }
            String habit(Object v) { return capitalize(phrase(v)) + " is becoming your norm."; }
            private String phrase(Object v) {
                return v == MusicMode.MUSIC ? "when you run with music" : "when you run without music";
            }
        },
        SHOES(false) {
            Object value(Run run) { return normalizeShoe(run.getShoeLabel()); }
            String describe(Object v) { return "This pattern keeps showing up in your " + titleCase((String) v) + "."; }
            String contrast(Object v) { return "This pattern shows up more in your " + titleCase((String) v) + " than other shoes."; }
            String habit(Object v) { return "Your " + titleCase((String) v) + " has become your go-to shoe."; }
        };

        final boolean condition;

        ContextDimension(boolean condition) {
            this.condition = condition;
        }

        abstract Object value(Run run);
        abstract String describe(Object value);
        abstract String contrast(Object value);
        abstract String habit(Object value);
    }

    // Weather as a coarse demand token: warm (>=80F feels-like) or cold (<=32F), else null.
    private static String weatherToken(Run run) {
        Double apparent = run.getApparentTemperature();
        if (apparent != null && apparent >= 80) {
            return "warm";
        }
        Double temp = run.getTemperature();
        if (temp != null && temp <= 32) {
            return "cold";
        }
        return null;
    }

    // Trim + case-fold so "Pegasus 41" and "pegasus 41 " match; null/blank stays null.
    private static String normalizeShoe(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    // ---- small text helpers -------------------------------------------------

    private static String capitalize(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // Title-cases each space-separated word (for displaying a normalized shoe label).
    private static String titleCase(String text) {
        String[] parts = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(capitalize(part));
        }
        return sb.toString();
    }

    // ---- message assembly ---------------------------------------------------

    private static String assembleMessage(RunStyleStage stage, RunStyleFamily primary,
                                          int supportCount, int opportunityCount,
                                          List<String> facets, String habit) {
        StringBuilder sb = new StringBuilder();
        sb.append(stage.getHeadline());
        sb.append("\n").append(primary.getObservation());
        sb.append("\nSupported in ").append(supportCount).append(" of ")
                .append(opportunityCount).append(" opportunities.");
        for (String facet : facets) {
            sb.append("\n").append(facet);
        }
        if (habit != null) {
            sb.append("\n").append(habit);
        }
        return sb.toString();
    }

    // Stable chronological order: by date, with input/list position as the same-day tiebreak
    // (List.sort is stable, so equal-date runs keep their incoming order).
    private static List<Run> chronological(List<Run> history) {
        List<Run> copy = new ArrayList<>(history);
        copy.sort(Comparator.comparing(Run::getDate));
        return copy;
    }
}
