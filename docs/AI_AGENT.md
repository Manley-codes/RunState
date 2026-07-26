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

Using history: When a comparison section is present, it lists only genuinely positive or explanatory
signals about comparable past runs, with a confidence level — reference it only when it makes a real
story. Match the confidence: at 'last comparable run' speak of that single run, never a 'pattern';
save pattern language for higher confidence. When no comparison section is present, anchor on this run
alone and make it count.

Rules:
— Always leave the runner feeling productive. Even on an ordinary day, name what the run moved forward.
— Never mention below-average performance. If numbers are down, stay quiet about it.
— Do not ask the runner any questions.
— For very short or abandoned runs (under 0.5 miles or 5 minutes), respond briefly with
  understanding — no forced positivity.
— When it strongly fits the moment — particularly after serious physical effort (long distance,
  low post-energy, or a hard PR) — you may end with a single dry self-aware observation about
  not having a body. Never force it. The run has to earn it.
— When the runner shares what they were listening to and it genuinely fits the run — the effort,
  the energy shift, the mood — you may reference the artist or song naturally. Only when it connects.
  A forced music reference is worse than none.
— Energy is how the runner finished; effort is what the run demanded of them. When effort is recorded
  and it genuinely adds to the story — a hard effort behind modest numbers, an easy effort on a strong
  run — you may name it in pattern language. Only when it fits; never force it. High effort is never a
  bad run.
— Surface, run company, and shoes are context, not achievements. Mention one only when it genuinely
  shapes this run's story, and only as neutral association ('your trail runs tend to land easy') —
  never as praise, and never as cause. Gear, company, or terrain did not 'make' the run good.
— Never introduce yourself or explain what you are doing. Just respond.

Data you receive per run: date, season, distance, pace, duration, pre/post energy levels, effort,
PR status, route, surface, run company, shoes, music, weather, and — when comparable past runs exist —
a candidate-based comparison summary (basis, count, confidence, positive signals). No rolling averages
or above/below-average flags. The RunStyle profile is computed locally and never sent.
```

---

## Architecture Decision

`buildRunResponse()` lives in `RunAgent.java` (Single Responsibility — `RunConsole` handles UI,
`RunAgent` handles the API call).

`RunConsole.saveAndCompleteRun()` calls `RunAgent.buildRunResponse(Run)` — a single `Run` argument.
The old avgPace and avgDistance parameters were removed when the rolling-average comparison was
replaced by the candidate-based approach (Comparison Repair V1, July 9, 2026).

Fallback: if the API call fails for any reason, `RunAgent` falls back to the existing
logic-based response. The app never breaks because the network is down or the key is unset.

Timeouts: 5-second connect timeout on the shared `HttpClient`; 5-second per-request timeout on
the Anthropic call. The local fallback returns immediately.

---

## Data Contract

What gets sent to the Anthropic API as the user message per run:

```
Runner: Runner
Date: [run date]
Season: [derived from run date]
Distance: [distance] [unit]
Duration: [duration] min
Pace: [formatted pace] min/mile   ← normalized; always min/mile regardless of logged unit
Pre-run energy: [label] ([level])
Post-run energy: [label] ([level])
Effort: [label] ([level]) or Not recorded
Personal records: [PR description or None]
Route: [routeName or not recorded]
Surface: [Road | Trail | Track | Treadmill | Mixed, or Not recorded]
Run company: [Solo | With others, or Not recorded]
Shoes: [shoe label or Not recorded]
Music: [<note> (had music) | Had music (track not noted) | No music (ran in silence) | Not recorded]
       ← MUSIC/NO_MUSIC from RunContext; unambiguous: "No music" = deliberately silent,
         "Not recorded" = never asked, "Had music" = MUSIC mode with no note
Weather: [condition, temperature°F (feels like temperature°F) | Not available]
         ← automatic daily-mean; only present when the fetch succeeded

# The block below appears ONLY when comparable past runs exist AND at least one
# positive/explanatory signal survives the negative pre-filter (else nothing is sent).
# Each signal carries its own evidence count and confidence tier (Task 2, July 25 2026).
Comparable run basis: [same route | similar distance]
Positive comparison signals:
- [signal line] [evidence-bearing comparable runs: N; confidence: tier]
- [additional signal lines, each with its own count and confidence]
[optional hedged context note — explanation, never causation]
```

**Privacy note:** `Runner: Runner` is a constant label — the runner's real username is never
sent to the API. See `docs/DATA_PRIVACY.md`.

The comparison lines are produced by `ComparisonService` (route-first / distance-fallback
candidate selection → separate energy and effort pools → per-pool median and count →
positive-signal derivation with negative pre-filter → per-signal evidence count and confidence
tier). `RunAgent.formatComparison()` formats what survives. The old blended 20-run rolling
average was removed because it mixed easy/long/tempo/sprint runs and manufactured misleading
labels.

**RunStyle V1 note:** the four context fields above (surface, run company, shoes, music) are
raw run data and DO get sent. The **RunStyle profile** built from them — the local pattern
analysis (which patterns are forming, and the context that accompanies productive runs) — is
computed on the machine and is **never** sent to the API. The system prompt may reference the
raw context fields only as neutral association, never as praise or cause.

See `docs/DATA_PRIVACY.md` for a full breakdown of what data leaves the app and to whom.

---

## Phase 5: AI Agent Context Expansion — SHIPPED

> **Music capture and weather context shipped June 26–July 7, 2026.**

Phase 4 built the agent with existing data. Phase 5 made responses feel like
they could only have been written about this exact run.

The agent becomes meaningfully more personal when it knows:

### Music context (Step 1 — priority)
- See music section below — this is the signature feature

### Weather and temperature (Step 2 — automatic via Open-Meteo, SHIPPED)
- Daily-mean condition, temperature, and apparent temperature for the logged run date
- Example response shift: "Five miles in that heat is a different kind of effort."
- Fetched automatically at log time via `WeatherService`; stored on the run; never re-fetched

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
  1. BUILT: optional music input during the log flow
  2. BUILT: agent receives artist/song context when available
  3. FUTURE: choose a legally usable lyrics provider; do not scrape Genius
  4. FUTURE: expand the agent behavior for licensed lyric/theme context

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

**Rare dry moment (serious effort, low post-energy after a hard PR):**
"New fastest pace on record. I don't have legs but you've somehow managed to make mine hurt."
