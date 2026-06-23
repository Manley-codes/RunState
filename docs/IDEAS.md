# RunState Ideas

This file is for the strongest ideas worth remembering. The goal is to keep RunState simple right now while saving the ideas that could make the app stand out later.

RunState is the selected working name for the app. See `docs/BRANDING.md` for the naming notes.

## Core Direction

RunState is currently a simple run tracker that records completed runs, shows personal progress, and leaves room for helpful context around each run.

The early app should feel fun, attractive, unique, and easy to use. It can include a few standout features with more complex logic, but the overall experience should not feel complicated.

The app should stay easy to understand:

- Log a run.
- View run history.
- See pace and personal records.
- Capture how the run felt.
- Add more context over time without overwhelming the runner.

Product guardrails:

- Keep common actions quick and obvious.
- Use feeling-based insights and a small number of playful highlights to create personality.
- Judge complex ideas by whether they make the app fun, easy to experience, and meaningfully different.
- Allow a small number of technically complex signature features when their user experience remains simple.
- Avoid filling the first version with every possible context field or analysis.
- Let real use and feedback guide which advanced features are built later.

An idea should not be postponed only because it is technically complex. Music integration, for example, may deserve earlier consideration because music is naturally connected to running and could give the app a fun identity. Trail analysis may be useful, but it can wait if it adds complexity without creating the same immediate enjoyment.

## Pre-Run And Post-Run Energy Context `[Unique]`

The app can ask one quick energy question before a run and another after it:

```text
How's your energy going into this run?
[I'm Here] [Ready-ish] [Let's Go!]

How's your energy now?
[Spent] [Feeling Good] [Powered Up]
```

The goal is to collect useful observations without making the runner fill out a long survey. Either question can be skipped.

Key characteristics:

- Simple input before and after each run.
- Connects performance numbers with how the runner actually felt.
- Gives RunState a more personal angle than a basic run log.
- Uses one shared low-to-high scale behind different pre-run and post-run labels.
- Stores and displays observations without judging the run.

Over time, RunState could compare the feeling choice with run details such as:

- Distance
- Duration
- Average pace
- Route or trail
- Weather and temperature
- Solo, partner, or group run
- Shoes used
- Time of day

Future insight example:

```text
Your best-feeling runs often happen on cooler days and familiar routes.
```

Pitch:

```text
RunState is a run tracker that records performance and how each run felt, so runners can eventually learn what conditions help them perform their best.
```

## Context-Aware Post-Run Prompts `[Unique]`

RunState should bring useful next actions forward at the right moment instead of making the runner search through menus.

Key characteristics:

- Shows helpful actions after a run, when they are most relevant.
- Keeps the post-run experience simple and focused.
- Can support personal records, feelings, notes, photos, or sharing later.
- Should avoid becoming a cluttered menu.

Example after a run:

```text
Great job, Jay.

You completed 5.0 miles in 50 minutes.
Average pace: 10:00 per mile.

How's your energy now?
[Spent] [Feeling Good] [Powered Up]
```

Possible later prompts:

- Add a photo to this run.
- Save a note about how the run felt.
- View a new personal record.

## Future Run Suggestions `[Unique]`

RunState could eventually suggest a general type of run before the runner starts, based on patterns from past runs.

Key characteristics:

- Uses past run history to suggest a general direction, such as easy, moderate, or faster.
- Could include a loose distance range instead of a strict command.
- Works best after the app has enough completed runs to notice real patterns.
- Should sound like a helpful option, not professional coaching or medical advice.

Possible suggestion examples:

```text
Your best-feeling runs are usually 3-5 miles at an easy pace.
```

```text
Consider a slower recovery run today.
```

This should stay parked for later because it needs enough run history to be useful. The app should avoid sounding like medical or professional coaching advice. A safer version would describe patterns and gentle options instead of telling the runner exactly what they should do.

## Personal Records

The app should recognize meaningful progress without over-celebrating everything.

Current and future personal record ideas:

- Longest distance completed
- Fastest average pace overall
- Fastest average pace for a specific distance
- Fastest time for a specific distance

Pace note:

- A lower pace number is better.
- Example: 8.0 minutes per mile is faster than 10.0 minutes per mile.

Motivation principle:

- Celebrate real progress, not noise.
- The first logged run should create a baseline, but PR messages should appear when a runner beats a previous result.

## Top Run Highlights `[Unique]`

RunState could eventually show a small set of highlighted runs that feel more like badges or rewards than normal log entries.

Key characteristics:

- Shows only a few standout runs, such as the top three.
- Feels more fun and rewarding than a normal run-history list.
- Highlights all-around quality, not just one stat.
- Could eventually use a larger card or badge-style design in a future UI.
- Should wait until the app has enough context to make the highlights meaningful.

The strongest version is not just:

- Fastest run
- Longest run
- Best-feeling run

The stronger idea is an all-around best run that balances distance, pace, post-run feeling, and useful context.

Possible context for highlighted runs:

- Route or trail
- Weather or temperature
- Shoes worn
- Solo, partner, or group run
- Post-run feeling

This idea fits RunState well because it supports the main theme: progress is more than one number. It should wait until the app has more context data, but it is worth saving as a future feature.

## Weather, Route, And Shoe Context `[Unique]`

RunState could eventually connect run performance and run feeling with context around the run.

Key characteristics:

- Helps explain why some runs feel or perform better than others.
- Connects run feeling with real-world details like route, weather, and shoes.
- Can feed future insights without making the current app too complex.
- Should be added gradually so the runner is not overloaded with questions.

Useful context ideas:

- Weather and temperature
- Route or trail
- Whether the run was solo, with a partner, or with a group
- Shoes used

The app should collect as much context as possible before the run or automatically later. The post-run experience should stay simple.

Example before-run choices:

```text
Route: Buffalo Bayou Trail
Shoes: Daily Trainers
Run Type: Solo
```

### Simple Trail Gallery Concept

A lightweight early visual feature could show an attractive gallery of selected trail or route photos without trying to provide full nearby-trail discovery or detailed trail analysis.

Key characteristics:

- Adds color and personality to the app.
- Is easier to understand than a dense trail-information tool.
- Could inspire runners without becoming a major navigation feature.
- Remains an idea, not a committed first-version requirement.

## Your Run Style `[Unique]`

`Your Run Style` is a future curated discovery section for interesting habits and
patterns from the runner's completed runs.

Subtitle:

```text
Interesting habits and patterns from your runs.
```

This should not become another general statistics dashboard. Normal run details belong
in run history and personal-record views. `Your Run Style` should surface only a small
number of patterns that are supported by enough relevant evidence and may help the
runner notice something they would otherwise miss.

Possible categories:

- Performance patterns
- Energy patterns
- Route and environment patterns
- Shoe and gear context
- Music-linked patterns
- Solo, partner, or group context

Example future card:

```text
Longer at Memorial Park

Your last 4 Memorial Park runs averaged 5.2 miles,
compared with 3.8 miles on other routes.

Based on 9 recent runs
```

The first prototype should use route and distance because both values already exist and
the evidence is easy to explain. Energy-based pattern cards should wait until the
comparison and interpretation rules are more mature.

The section should feel earned. When evidence is weak, the app should stay quiet or say
it is still learning rather than manufacture an insight.

Confidence language may progress from:

- "I'm starting to notice..."
- "Your recent runs suggest..."
- "Over your last 12 runs..."

Confidence must be based on the number of relevant observations for that specific
pattern, not merely the runner's total number of runs.

## Support Messages And Future Voice Encouragement `[Unique]`

Support messages are a strong future idea because they connect running progress with encouragement from selected people.

Key characteristics:

- Adds an emotional support layer without becoming a social network.
- Can be tied to specific runs or races.
- Text support messages are a simpler early version.
- Voice encouragement is a far-future version and should wait until the core app is stronger.

Early version:

- Selected friends or supporters can send supportive text messages after a run or race.
- Support messages can be connected to a completed run.

Far future version:

- Voice support messages can play after a run or during a training moment.
- Example: a runner finishes a race and hears a short encouraging message from a selected supporter.

This should be saved for much later, but it could become one of RunState's more unique emotional features.

## Future Body Feedback And Effort Patterns `[Unique]`

RunState could eventually help runners notice patterns around effort, discomfort, and performance.

Key characteristics:

- Connects run feeling with effort, discomfort, pace, distance, and context.
- Could help runners notice patterns over time.
- Must avoid medical claims or pretending to diagnose injury causes.
- Should use careful wording and stay focused on pattern awareness.

This should be handled carefully. The app should notice patterns, not make medical claims.

Possible data:

- Run feeling
- Pace
- Distance
- Route
- Weather
- Step count or cadence
- Knee, hip, foot, or other discomfort

Example safe insight:

```text
You often report knee discomfort after faster runs over 4 miles.
```

The app should avoid saying that one factor caused an injury. Persistent pain should be handled by a qualified professional.

## Later Effort Analysis

Later versions could use split data to understand how a runner performed inside a run.

Possible effort indicators:

- Fastest split
- Strong finish
- Negative split
- Pace surge
- Inconsistent pacing
- Estimated hard effort

This depends on adding more detailed run data, such as mile or kilometer splits.
