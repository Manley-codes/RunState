# RunNet Unique Ideas

This file gathers the ideas that could make RunNet feel different from a basic run tracker.

These are not all current build targets. Some are active or near-future ideas, while others are parked for later so they are not forgotten.

## Unique Idea Marker

In the other docs, `[Unique]` means the feature or idea helps RunNet stand out.

## Pre-Run And Post-Run Energy

Status: capture foundation implemented; interpretation under design

Core idea:

The app records the runner's energy before and after a run, not just distance, pace, and duration.

Key characteristics:

- The runner can skip either energy question.
- Pre-run and post-run labels map to one shared low-to-high scale.
- Connects performance data with the runner's actual experience.
- Keeps feedback lightweight before and after each run.
- The pattern becomes: pre-run energy -> run details -> post-run energy.
- An energy-focused question may feel easier and more natural to answer than a broad mood question.

Possible question direction:

```text
Before: How's your energy going into this run?
After: How's your energy now?
```

The wording shown before and after the run could be slightly different while still mapping to the same underlying low, middle, and high scale. For example, pre-run labels could describe readiness while post-run labels describe the resulting energy. Keeping a shared underlying scale would still allow the app to measure change.

Open design question:

Energy is clear and measurable, but it does not fully capture emotions such as accomplishment, confidence, stress relief, or disappointment. The app should decide whether this feature measures energy specifically or a broader overall state before finalizing the labels.

Why it matters:

This creates the foundation for a stronger pattern system. It can eventually help the runner notice which distances, paces, routes, shoes, weather, or conditions tend to improve or worsen their energy.

Important design note:

Energy should remain an observation until run metrics and enough personal history give it context. A lower post-run energy level should never automatically classify a demanding run as bad.

See `docs/ENERGY_STATE_DESIGN.md` for the completed foundation, unresolved interpretation problems, and return point.

## Energy-Based Insights

Status: paused until interpretation rules are designed

Core idea:

The app can eventually connect energy observations with run details and relevant personal history.

Key characteristics:

- Compare pre-run and post-run energy without treating the change as run quality.
- Use distance, duration, pace, and available context before interpreting a result.
- Wait for enough comparable history before presenting patterns confidently.
- Later comparisons by route, weather, shoes, or run type.

Why it matters:

This can turn the captured energy values into useful personal insights, but only after the app can present them honestly and with enough context.

## Top Run Highlights

Status: future idea

Core idea:

RunNet could show a small set of top runs as special highlights, badges, or large reward-style cards.

Key characteristics:

- Shows only a few standout runs, such as the top three.
- Focuses on all-around quality instead of only fastest pace or longest distance.
- Could combine distance, pace, post-run energy, route, weather, shoes, music, and other available context.
- Should feel more fun than a normal run-history log.

Why it matters:

This supports the idea that the best run is not always the fastest or longest run. It could become one of the app's most memorable features later.

## Future Run Suggestions

Status: parked for later

Core idea:

RunNet could suggest a general type of run before the runner starts, based on patterns from past runs.

Key characteristics:

- Suggests general pace or distance ranges.
- Uses past run history instead of guessing.
- Should describe patterns and options, not strict instructions.
- Needs enough data before it becomes useful.

Why it matters:

This could make RunNet feel helpful and personal, but it should wait. It needs careful wording so it does not sound like professional coaching or medical advice.

## Context-Aware Run Patterns

Status: future idea

Core idea:

RunNet could connect run results and energy with context like route, weather, shoes, time of day, and solo, partner, or group runs.

Key characteristics:

- Helps explain why some runs feel better than others.
- Adds meaning to future top-run highlights.
- Can be added one context field at a time.
- Should not make the runner answer too many questions after every run.

Why it matters:

This gives RunNet a stronger identity: it helps runners understand what conditions support their best runs.

## Your Run Style

Status: approved future identity; first transparent pattern is the likely next prototype

Core idea:

`Your Run Style` is a curated section for interesting habits and patterns from completed
runs. It should help the runner discover what tends to appear around stronger
performance or energy responses without turning into a generic analytics dashboard.

Key characteristics:

- Shows a few high-value cards instead of every possible correlation.
- Explains the evidence behind each observation.
- Uses cautious language when a pattern is still developing.
- Bases confidence on relevant observations for the specific comparison.
- Starts with existing reliable data before adding external context.
- Reports associations and does not claim that shoes, weather, routes, or music caused a result.

Recommended first prototype:

```text
Longer at Memorial Park
Your last 4 Memorial Park runs averaged 5.2 miles,
compared with 3.8 miles on other routes.
Based on 9 recent runs
```

The working subtitle is:

```text
Interesting habits and patterns from your runs.
```

## Music And Run Performance Context

Status: signature feature candidate

Core idea:

With the runner's permission, the app could connect music played during a run with pace, distance, energy change, and other run results.

Key characteristics:

- Could examine playlists, songs, artists, tempo, or music type.
- Could help reveal personal patterns, such as stronger runs with certain playlists or tempos.
- Makes the running experience feel more playful and personal.
- Should report associations instead of claiming that music caused better performance.
- Would require an external music-service integration and clear user permission.
- Listening history and music preferences should be treated as private personal data.

Possible feature layers:

1. Save the playlist or music session connected to a run.
2. Show songs that played during the run.
3. Compare run results with playlists, genres, artists, or tempo.
4. Match timestamps from the run with playback timestamps.
5. Highlight a fastest moment and show the song playing at that time.

Example:

```text
Fastest Moment
Mile 2 - 8:12 pace
Playing: "Stronger" by Kanye West
```

The advanced version would require accurate run splits or GPS timestamps and reliable music playback history. It may not work equally well across every music service.

Why it matters:

Music is naturally connected to running and could become one of the app's most fun and memorable identities. Its technical complexity should be weighed against that value instead of automatically pushing it to the far future. A simpler music layer could arrive before the full synchronized song-and-pace analysis.

## Support Messages And Voice Encouragement

Status: far future

Core idea:

RunNet could connect completed runs with encouragement from selected friends or supporters.

Key characteristics:

- Text support messages could be an early version.
- Voice encouragement could be saved for much later.
- Messages could connect to a specific run or race.
- Should avoid becoming a full social media system.

Why it matters:

This gives RunNet an emotional feature that could feel meaningful without requiring a huge social platform.

## Body Feedback And Effort Patterns

Status: far future

Core idea:

RunNet could help runners notice patterns between effort, discomfort, pace, distance, and context.

Key characteristics:

- Tracks patterns without making medical claims.
- Uses careful wording around discomfort or possible injury.
- Could connect with distance, pace, route, weather, and feeling.
- Should stay focused on awareness, not diagnosis.

Why it matters:

This could be useful, but it needs extra care because it touches health-related information.
