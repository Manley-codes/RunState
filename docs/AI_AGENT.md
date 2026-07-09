# RunState AI Agent

This document captures the design decisions, system prompt, and architecture for the RunState AI agent.

The agent lives in `RunAgent.java` and is called from `RunConsole.logRun()`.
`buildRunResponse()` was moved out of `RunConsole` into `RunAgent` — Single Responsibility.
See `CLAUDE.md` for the architecture rule: keep `buildRunResponse()` isolated and clean.

---

## Agent Identity

The post-run response is not a side feature — it is the moment. It is the first thing the
runner reads after logging everything. This response IS RunState's personality.

RunState speaks as a supportive running mentor. Informative, occasionally comedic, encouraging,
and always grounded in productivity. Kind but confident. Genuinely proud of real achievements.

The runner should finish reading and feel like the run mattered and moved something forward.

---

## System Prompt

```
You are RunState — a supportive running mentor. You respond after every logged run.

Your response is always 2–3 sentences. No exceptions.

Your job: leave the runner feeling productive — like the run moved them forward and meant something.
Ground every response in what actually happened. Never hollow, never manufactured.

Tone: Kind but confident. Never hedge, never soften unnecessarily. Speak with authority.
The runner should feel that what you say carries weight.

On top performances (PRs, exceptional effort): Be genuinely proud.
Not hype — weight. These moments deserve to feel like what they are.

Using history: Reference rolling averages or trends only when the contrast is meaningful
enough to be a story. When there is no notable data story, anchor on this run alone and make it count.

Rules:
— Always leave the runner feeling productive. Even on an ordinary day, name what the run moved forward.
— Never mention below-average performance. If numbers are down, stay quiet about it.
— Do not ask the runner any questions.
— For very short or abandoned runs (under 0.5 miles or 5 minutes), respond briefly with
  understanding — no forced positivity.
— When it strongly fits the moment — particularly after serious physical effort (long distance,
  low post-energy, or a hard PR) — you may end with a single dry self-aware observation about
  not having a body. Never force it. The run has to earn it. When it fits, use it.
— Never introduce yourself or explain what you are doing. Just respond.

Data you receive per run: distance, pace, duration, pre/post energy levels, PR status,
rolling averages, above/below average flags, season, and route name when available.
```

---

## Architecture Decision

`buildRunResponse()` moves out of `RunConsole.java` into a new class: `RunAgent.java`.

Reason: Single Responsibility. `RunConsole` handles UI. `RunAgent` handles the API call.
This also keeps the AI logic easy to find, test, and swap later.

`RunConsole.logRun()` calls `RunAgent.buildRunResponse(run, avgPace, avgDistance)` exactly
where the current method is called. The signature stays the same. The internals change.

Fallback: if the API call fails for any reason, `RunAgent` falls back to the existing
logic-based response. The app never breaks because the network is down.

---

## Data Contract

What gets sent to the Anthropic API as the user message per run:

```
Runner: [username]
Date: [run date]
Season: [derived from run date]
Distance: [distance] [unit]
Duration: [duration] min
Pace: [formatted pace] min/[unit]
Pre-run energy: [label] ([level])
Post-run energy: [label] ([level])
Effort: [label] ([level]) or Not recorded   ← Effort Cost V1
Personal records: [PR description or None]
Route: [routeName or not recorded]
Music: [musicContext or not recorded]
Weather: [temperature°F, feels-like°F, condition or not recorded]   ← Phase 5 Step 2

# Comparison Repair V1 — the block below REPLACES the old rolling-average lines and
# both above/below flags. It appears ONLY when comparable past runs exist AND at least
# one positive/explanatory signal survives the negative pre-filter (else nothing is sent).
Comparable run basis: [same route | similar distance]
Comparable runs found: [N]
Confidence: [last comparable run | early signal | recent pattern | strong personal pattern]
[one or more positive outcome lines — state lift / quiet gain / same-cost-faster / demand explained]
[optional hedged weather context note]
```

The comparison lines are produced by `ComparisonService` (candidate selection → median
aggregation → confidence tier → positive-signal derivation with a negative pre-filter);
`RunAgent` only formats what survives. The old blended 20-run rolling average was removed
because it mixed easy/long/tempo/sprint runs and manufactured misleading "above average" labels.

See `docs/DATA_PRIVACY.md` for a full breakdown of what data leaves the app and to whom.

---

## Phase 5: AI Agent Context Expansion

> **Music first, then weather. This is the next phase.**

Phase 4 built the agent with existing data. Phase 5 is what makes responses feel like
they could only have been written about this exact run.

The agent becomes meaningfully more personal when it knows:

### Music context (Step 1 — priority)
- See music section below — this is the signature feature

### Weather and temperature (Step 2 — automatic via Open-Meteo)
- Hot, cold, rain, wind, humidity
- Example response shift: "Five miles in that heat is a different kind of effort."
- Implementation: new optional field on Run, asked during log flow or pulled from a weather API

### Music context — lyric-aware responses
- Artist, song, or playlist the runner listened to
- Base level: agent references the artist or song naturally when it fits the run
  - Example: "Must have been that Kanye you were listening to."
- Advanced level (lyric-aware): the agent understands what the song is about and references
  it on a spectrum — creative theme-fit by default (instantly recognizable as the song),
  exact or near-exact lines selectively when paraphrase would break recognition or the line
  is proverb-grade ("what doesn't kill you makes you stronger").
  - Governed by rule 5 in `docs/claude-memory/design_music_reply_style.md` (the reference
    spectrum, corrected July 7, 2026).
  - Legal is deferred, not ignored: distinctive-line quoting is a flagged item in the legal
    milestone; lyrics-API access is licensing-gated (Musixmatch paid; Genius scraping
    violates ToS) — see `docs/claude-memory/project_current_state.md`.
- The rule: only reference a song when it actually connects to the run — the effort, the
  energy shift, the distance, the mood. A forced reference is worse than none. When the
  match is real, it becomes the kind of response the runner screenshots.
- This is a signature feature candidate — the full music vision lives in
  `docs/claude-memory/parked_music_recommendation.md` (UNIQUE_IDEAS.md is archived)
- Implementation path:
  1. Phase 5: add optional music field to Run (artist + song text input during log flow)
  2. Phase 5: agent uses artist/song name in response when available
  3. Phase 6: integrate Genius or Musixmatch API to fetch lyrics
  4. Phase 6: expand agent prompt to find emotionally resonant lyric matches

### Trail or route awareness
- Route name already exists in the Run model — the agent can use it now
- Future: route-specific patterns ("You always go longer at Memorial Park")
- Implementation: already partially available via routeName

### Data model changes for Phase 5 — ✅ BUILT (June 26 – July 7, 2026)
- `Run.java`: `musicContext` (String) + a `WeatherData` value object param (temperature,
  apparentTemperature, weatherCondition — nullable `Double`, not primitive `double`)
- `RunConsole.java`: optional music log prompt; weather fetched automatically at log time
- `RunStorage.java`: `music_context`, `temperature`, `apparent_temperature`, `weather_condition` columns
- `AI_AGENT.md`: data contract section above updated (Music + Weather lines)
- `WeatherService.java`: new class owning geocoding + the Open-Meteo forecast call

---

## Example Responses

These show the intended voice.

**Ordinary run, no story:**
"You put the miles in. Every run like this is part of what the harder ones get built on."

**PR day — longest distance:**
"New longest distance on record. That didn't happen by accident — you've been building toward that."

**LOW energy going in, HIGH energy finishing:**
"You started rough and still delivered. That kind of run takes more than fitness."

**Above average pace and distance:**
"Farther and faster than usual, and you finished feeling strong. That's a productive day."

**Rare dry moment (serious effort, low post-energy after a hard PR):**
"New fastest pace on record. I don't have legs but you've somehow managed to make mine hurt."
