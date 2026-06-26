# RunState Roadmap

This roadmap keeps the project focused while leaving room for future ideas.

RunState is the current working name, and MoodPace is a strong alternate name. See `docs/BRANDING.md`.

For the ideas that make RunState feel more distinct, see `docs/UNIQUE_IDEAS.md`.

`[Unique]` marks future directions that help RunState stand out from a basic run log.

For the current pre-run and post-run energy design discussion, see `docs/ENERGY_STATE_DESIGN.md`.

## Phase Status

| Phase | Description | Status |
|---|---|---|
| 1 | Console app and energy system | Done |
| 2 | MySQL persistence | Done |
| 3 | Your Run Style detection | Done |
| 4 | AI agent — replace buildRunResponse() with Anthropic API | Done |
| 5 | AI agent context expansion — music first, then weather (automatic via Open-Meteo) | Next |
| 6 | Mobile UI and GPS | Later |

> **Phase 5 is next — music first, then weather.**
> Music is the signature feature. Go deep on one thing rather than shipping many features halfway.
> Weather (automatic via Open-Meteo, no API key needed) comes second.
> Both feed the AI agent for more personal, specific post-run responses.
> See `docs/AI_AGENT.md` for the full plan and `docs/claude-memory/project_current_state.md` for the build steps.

## Present Focus

- Keep the app as a simple Java console run tracker.
- Build a fun, attractive, and easy-to-use foundation.
- Limit early complexity to a few features that make the app meaningfully different.
- Evaluate music as a possible signature feature rather than postponing it only because it is complex.
- Continue improving `Runner`, `Run`, and `App`.
- Use the interactive menu to log completed runs and review results.
- Keep manual logging as a useful workflow without treating it as a replacement for future automatic tracking.
- Strengthen run history.
- Keep personal record tracking clear and beginner-friendly.
- Add small features slowly and commit after stable steps.

## Near Future

- Update CLAUDE.md main class path after Java package rename.
- Decide how manually logged runs should be saved between sessions.
- Review the input prompts and validation in the new Log Run flow.
- Test the implemented pre-run and post-run energy capture foundation.
- Review the new compact run-history cards with realistic data.
- Return to the carefully paused `Your Run Style` design and prototype when ready.
- Design how pre-run energy, run data, post-run energy, and personal history connect.
- Decide how observations should be presented before adding interpretation.
- Keep Average Distance by Feeling paused until the new energy model is stable.
- Improve the console output layout for run history, personal records, and feeling summaries.
- Collect pre-run and post-run energy as observations before making strong interpretations. `[Unique]`
- Compare energy with relevant run details and personal history only after enough data exists. `[Unique]`
- Improve test runs in `App` so they clearly demonstrate each feature.
- Start thinking about a small demo UI after the console logic feels stable.

## High-Priority Later Ideas

- Music connected to runs, with advanced song-and-pace moment matching added in layers. `[Unique]`
- Weather and temperature context for runs. `[Unique]`
- Route or trail context, such as Buffalo Bayou or Memorial Park. `[Unique]`
- Shoe selection before a run. `[Unique]`

These ideas connect strongly with the post-run feeling concept because they can help explain when a runner performs or feels their best.

## Far Future

- Pattern-based run suggestions after enough run history exists. `[Unique]`
- Top run highlights that show the best all-around runs as special badge-style entries. `[Unique]`
- Support messages from selected friends or supporters. `[Unique]`
- Voice encouragement after runs or races. `[Unique]`
- Split tracking and deeper effort analysis.
- Body feedback and discomfort pattern tracking, handled carefully. `[Unique]`

## Parking Lot

These ideas are not priorities right now:

- Nearby-trail discovery and detailed trail insights
- AI-assisted workout creation
- Auto-stop run goals
- Full home screen customization
- Complex route crowding or local activity intelligence
