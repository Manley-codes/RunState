# RunState

[![Console and Android checks](https://github.com/Manley-codes/RunState/actions/workflows/ci.yml/badge.svg)](https://github.com/Manley-codes/RunState/actions/workflows/ci.yml)

**A native Android running app that connects the numbers of a run with how it felt.**

RunState brings together distance, active time, pace, optional energy and effort check-ins, and
music-aware reflections. The goal is to help a runner take something meaningful from the run,
beyond a set of statistics.

Built by **Manley Johnson**, a CS student at SNHU, as a learning project and portfolio piece.
It began as a Java console application and now includes a Kotlin/Compose Android app and a
separate Java reflection backend.

**[Explore the interactive browser demo →](https://manley-codes.github.io/runstate-preview/)**

Explore the current opening, Start, Countdown, Active Run, music, Completion and History
screens in one connected preview. Desktop and mobile both show a framed phone; mobile uses a
screen dropdown. The [preview repository](https://github.com/Manley-codes/runstate-preview)
uses example runs, silent music controls and scripted reflections rather than live tracking or services.

## The Android app

**Updated October 29, 2026.** The Android app is under active development. Implementation and
verification details are in the [current project state](docs/design/project_current_state.md).

| Part of the run | Current implementation |
|---|---|
| **Opening and Start** | Moving photo collage; optional pre-run energy, run intention, familiar route label and shoe selection |
| **Countdown** | Integrated animated countdown, spoken cues and a Start now control |
| **Active Run** | Distance inside a circular visualizer, active time, average pace and current-mile average pace; pause/resume and a protected Stop control |
| **Appearance** | Dark is the default for Countdown and Active Run; Light and Auto remain settings choices |
| **Music controls** | Spotify controls can appear inside the visualizer. Temporary Headphones and Album cover treatments remain available for comparison |
| **Completion** | Saved run facts, optional post-run energy and effort, spoken receipt/reflection, and eligible opted-in music endings |
| **Log History** | Locally saved runs and exact selected reflections, with All runs, Longest and Fastest pace views |

The visualizer is a designed animation, not an analysis of the playing audio. Music and speech
features have separate connection, consent and provider requirements. The circular album-art
treatment is an experiment; production use still needs its provider-guidance review.

The preview's compact **Your Energy** shortcut in History is a browser refinement; its Android
update and a review of the check-in content are still planned.

## What matters in the implementation

- **Local-first records.** Room/SQLite stores runs, feedback and selected reflection text on the
  phone. A failed online request does not erase a saved run; History can reopen the saved text
  without generating it again.
- **Separate responsibilities.** Kotlin/Compose handles the phone experience, recording and local
  storage. A Java 17 backend handles online reflection and voice requests. Provider credentials
  remain on the backend.
- **Optional context.** Energy is not required to start a run. Music observation, sharing and
  song lookup have explicit settings and run-scoped choices; connecting Spotify is not blanket
  permission to share listening context.
- **Bounded music integration.** Spotify App Remote is integrated for authorized playback
  control. Run controls and music controls remain independent, with safeguards around voice and
  snippet playback. RunState does not extract Spotify audio.

Current limits include wider outdoor GPS and audio reliability, complete musical endings across
different recordings, the final music-player treatment, and preparation for external testers.
The [music development story](docs/design/music_reflection_development_story.md) records
tested reflection/song pairings and remaining gaps.

## Repository map

| Location | Purpose |
|---|---|
| [`android/`](android/) | Native Android app: Kotlin, Jetpack Compose, Room, Gradle |
| [`backend/`](backend/) | Java 17 reflection/voice service, JDK HTTP server, Maven |
| [`src/`](src/) | Original Java console application and its tests |
| [`docs/`](docs/README.md) | Guided documentation, architecture, privacy, design decisions and dated checkpoints |
| [Separate browser-demo repository](https://github.com/Manley-codes/runstate-preview) | Interactive, simulated presentation hosted on GitHub Pages |

## Build and check

Open `android/` in Android Studio, install the SDK components requested by Gradle, and use JDK 17
for the build. From the repository root in PowerShell:

```powershell
Push-Location android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
Pop-Location
```

Online reflection and Spotify setup are separate from building the UI. See the
[backend setup](backend/README.md) and [platform guide](docs/README.md#platforms-and-tools-used).
Private provider keys do not belong in the APK or this repository.

To run the backend's unit tests:

```powershell
mvn -f backend/pom.xml test
```

[GitHub Actions](https://github.com/Manley-codes/RunState/actions/workflows/ci.yml) runs the
console tests and Android unit-test/build checks. Backend tests and physical-device checks have
their own recorded results; the badge does not certify them or a production release.

## Design and engineering record

**[Start with the documentation guide →](docs/README.md)**

- [Current Android/backend state](docs/design/project_current_state.md) — implementation and dated verification limits
- [Creative direction](docs/design/creative_direction_ui.md) — accepted visual direction and superseded experiments
- [Data and privacy](docs/DATA_PRIVACY.md) — local records, optional context and external services
- [Music reflection development](docs/design/music_reflection_development_story.md) — iteration from early experiments to the current native flow

<a id="console-history"></a>
<details>
<summary><strong>Original Java console application — engineering, setup and historical evaluation</strong></summary>

The console app remains in `src/`. Its MySQL-backed analysis is a separate implementation;
these capabilities should not be read as a list of everything available in the Android app.

The console logs completed runs, checks longest-distance and fastest-pace records, attaches
Open-Meteo weather, compares similar past runs, derives a deterministic RunStyle profile, and
generates a short contextual response with a local fallback.

### Engineering decisions

- **Save first.** Personal records, response generation and RunStyle updates run only after a
  successful MySQL save. Save failures preserve the input for recovery.
- **Protect history.** Incomplete or malformed stored history is identified before it can affect
  comparisons or records.
- **Test without live services.** Storage seams and test fixtures allow ordinary unit tests to
  run without a database or API key.
- **Association, not causation.** Context can support a comparison without proving why performance
  changed. RunStyle is computed locally rather than invented by a language model.
- **Represent missing context honestly.** Known music, unidentified music, intentional silence
  and unknown state are distinct. Offline responses remain music-neutral.

```text
App → RunConsole → RunStorage → MySQL
          ├─────→ WeatherService → Open-Meteo
          ├─────→ ComparisonService
          ├─────→ RunStyleService
          └─────→ RunAgent → Anthropic API
```

The [console run-flow diagram](docs/CURRENT_RUN_FLOW.md) and
[original AI-agent design](docs/AI_AGENT.md) explain these boundaries and failure paths.

### Console setup

Use Java 17, Maven and MySQL with a dedicated RunState database user/schema. Configure
`RUNSTATE_DB_PASSWORD`; `ANTHROPIC_API_KEY` is optional because the console has a local fallback.
See the storage configuration in `src/` for the expected connection settings.

```powershell
mvn compile exec:java "-Dexec.mainClass=com.runstate.App"
mvn test
```

### Historical Music Intelligence V1

The **August 18, 2026** evaluation of the original console Music Intelligence V1 implementation
did not meet its predeclared manual quality bar. That result is preserved in the
[evaluation record](docs/EVALUATION_RECORD.md). It is a dated evaluation of that implementation,
not the status of all later Android music/reflection work.

</details>
