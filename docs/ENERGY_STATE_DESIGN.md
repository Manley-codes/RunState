# Pre-Run And Post-Run Energy Design

This document preserves the completed foundation, agreed direction, open problems, and implementation concerns for the app's pre-run and post-run energy feature.

The capture-and-display foundation is implemented. Interpretation and analysis should not begin until the remaining design questions are reviewed and approved.

## Implemented Foundation

The old single `RunFeeling` model has been removed.

The Java code now stores two optional `EnergyLevel` values:

- Pre-run energy
- Post-run energy

`EnergyLevel` uses one shared internal scale with explicit numeric values and different pre-run and post-run labels. Skipped answers are stored as `null` and omitted from run output.

The current implementation captures and displays observations only. It does not calculate energy change, averages, scores, conclusions, or run-quality judgments.

When both values are recorded, the compact run-history card displays them together:

```text
Energy: Let's Go! -> Powered Up
```

When only one answer is recorded, the card shows only that stage. No conclusion is
attached to the arrow; it represents the two recorded observations.

Run dates now use Java `LocalDate`, providing a stronger foundation for friendly date
formatting and future date-based pattern comparisons.

The planned Average Distance by Feeling feature remains paused because the new interpretation rules are not settled.

## Agreed Direction

The feature should record the runner's state before and after a run:

```text
Pre-run energy -> run details -> post-run energy
```

The feature should help the runner notice personal patterns over time. It should not treat one energy choice as a rating of whether the run was good or bad.

Core principles:

- Feelings and energy states are observations, not performance grades.
- Run metrics and history give an energy response meaning.
- The app should collect data before making strong interpretations.
- Insights should compare the runner with their own relevant history.
- The experience should remain quick, playful, and easy to answer.
- Complex analysis is acceptable when the user experience stays simple and useful.

## Working Questions And Labels

The current working question before a run is:

```text
How's your energy going into this run?
```

Working pre-run labels:

- I'm Here
- Ready-ish
- Let's Go!

The current working question after a run is:

```text
How's your energy now?
```

Working post-run labels:

- Spent
- Feeling Good
- Powered Up

These are working labels, not permanently finalized wording.

## Shared Internal Scale

The pre-run and post-run labels should map to the same internal ordered scale:

| Level | Pre-run label | Post-run label |
| --- | --- | --- |
| Low | I'm Here | Spent |
| Moderate | Ready-ish | Feeling Good |
| High | Let's Go! | Powered Up |

This allows the app to compare before and after values without forcing identical user-facing words at different moments.

The visible wording can stay fun while the internal values remain consistent for analysis.

## Energy Is Not Run Quality

A lower post-run energy level does not automatically mean the run went badly.

Examples:

- Feeling Spent after a marathon may reflect a strong and demanding achievement.
- Feeling Powered Up after a short easy run may be positive without making it the runner's best performance.
- Running faster or farther may explain a drop in energy without turning that drop into failure.

The app should not show a simplistic conclusion such as:

```text
Your energy decreased, so this was a bad run.
```

Instead, a future contextual recap could communicate separate facts:

```text
Energy: Let's Go! -> Spent
Distance: Longest run so far
Pace: Faster than your recent long runs

You finished with less energy after a strong, demanding effort.
```

This example illustrates the intended meaning. The exact recap design, colors, arrows, and wording are not decided.

## Run Type Context

Possible run types:

- Easy
- Steady
- Speed
- Long Run
- Race

Run type should be optional and should not interrupt a Quick Run flow.

Agreed behavior:

- The runner may select a type from a secondary details area.
- If the runner skips it, the app should omit it.
- The app should not ask for the run type after completion.
- The app should not silently infer or assign a run type.

Run type can improve future analysis, but the feature must still work when this context is absent.

## When Insights Become Meaningful

The app should initially collect pre-run energy, run details, and post-run energy without overinterpreting individual runs.

Insights should become more visible when enough comparable history exists.

Example pattern:

- A runner repeatedly completes two-mile runs and usually finishes with low energy.
- The runner later completes three miles and finishes Feeling Good or Powered Up.
- That result may be worth highlighting because the runner went farther and finished with more energy than their own recent pattern.

A future insight might say:

```text
You ran farther than usual and finished with more energy than on your recent runs.
```

The app should avoid claiming that one factor caused the result. It should report observed associations and changes.

## Main Problems To Solve

### 1. Connect Pre-Run State, Post-Run State, And Run Data

The main challenge is deciding how the complete run story should be interpreted:

```text
Pre-run state -> run details -> post-run state -> personal history
```

The app needs rules for combining the before-and-after change with distance, duration, pace, optional run type, personal records, and eventually other context.

The system must recognize that the same post-run state can mean different things depending on the effort. Feeling Spent after a marathon is different from feeling Spent after a short routine run.

### 2. Present The Result Without Misjudging The Run

The runner should see a clear, useful recap that preserves the full context instead of reducing the run to one positive or negative result.

The presentation needs to decide:

- Which facts should appear together
- When energy change deserves emphasis
- How to acknowledge demanding effort
- How to celebrate improvement without ignoring lower post-run energy
- How to report a decline without labeling the entire run as bad
- When to show only observations instead of an interpretation

Visual indicators such as arrows, colors, cards, or short messages remain examples rather than decided solutions.

### 3. Decide When There Is Enough History

No minimum has been finalized.

The system needs a rule for when it has enough relevant runs to show an insight. A fixed number such as ten runs may be simple, but comparable history may matter more than total history.

### 4. Decide What Counts As A Fair Comparison

Runs may differ by:

- Distance
- Duration
- Pace
- Optional run type
- Route
- Weather
- Shoes
- Music
- The runner's starting energy

The first analysis should use only the context that is actually available and avoid pretending unlike runs are directly equivalent.

### 5. Separate Reporting From Judgment

The app needs language that can report lower energy, slower pace, or shorter distance without automatically coloring those results as bad.

Visual indicators such as arrows or colors were discussed only as examples. The final recap language and visual system remain unresolved.

### 6. Handle Missing Optional Context

Run type and future details may be absent. Insights must degrade gracefully instead of guessing missing information.

### 7. Define The Exact State Being Measured

Energy is easy to answer and compare, but it does not fully capture accomplishment, confidence, stress relief, disappointment, or overall satisfaction.

The app still needs to decide whether the choices represent:

- Energy specifically
- A broader physical and mental state

This matters, but it is secondary to designing how before-and-after states connect with run data and how the result is presented.

### 8. Build On The New Data Model Safely

The capture foundation now uses one shared `EnergyLevel` enum throughout `Run` and `App`.

Completed:

- A shared internal energy level
- A pre-run energy value
- A post-run energy value
- Updated constructors and getters
- Updated run summaries
- Removal of the old feeling counts and pace summaries
- Revised demo data in `App`

Next work must decide how to interpret and present the stored observations before adding calculations.

## Recommended Return Point

When work resumes:

1. Design how pre-run state, run details, post-run state, and personal history connect.
2. Decide what the runner should see when the app has little data versus enough comparable history.
3. Define presentation rules that report energy change without rating the whole run as good or bad.
4. Confirm whether the choices measure energy specifically or a broader state.
5. Revisit the working questions and labels only if testing suggests they need improvement.
6. Add interpreted insights only after the comparison and presentation rules are stable.

## Current Non-Goals

Do not include these in the first implementation of the energy redesign:

- A single overall run score
- Automatic run-type classification
- Mandatory run-type selection
- Medical or coaching conclusions
- Claims that one condition caused an energy change
- Full weather, shoe, route, music, or GPS analysis
- Final dashboard arrows, colors, or visualization
