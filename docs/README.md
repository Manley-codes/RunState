# RunState documentation

RunState is now a native Android running app with a separate Java reflection backend. The
original Java console application remains part of the project and its engineering history.

This guide is for someone reviewing the repository. **Updated October 29, 2026.** Detailed
documents retain their own checkpoint dates; an older entry labeled “current” describes its
date, not necessarily today's app.

## Start here

1. **[Project README](../README.md)** — the app, repository layout, current scope and build commands.
2. **[Current project state](design/project_current_state.md)** — what is implemented, installed,
   deployed or still awaiting a real-use check. Read the newest relevant checkpoint first.
3. **[Creative direction](design/creative_direction_ui.md)** — accepted screens, design reasoning
   and which prototypes have been superseded.
4. **[Data and privacy](DATA_PRIVACY.md)** — what stays on the phone, what may leave it and the
   permissions that separate those actions.

For the music-reflection work, read the
[development story](design/music_reflection_development_story.md), followed by the
[accepted baseline](design/music_reflection_accepted_baseline.md). The earlier
[August 2026 console evaluation](EVALUATION_RECORD.md) remains a useful quality record, but does
not describe the status of the later Android system.

## App, interactive demo and historical concepts

| Material | How to read it |
|---|---|
| **Native Android app** | `android/` contains the installed-app implementation. Current development includes the opening collage, Start, integrated Countdown/Active Run, Completion, settings and runner-focused History |
| **Interactive browser demo** | [Seven connected screens](https://manley-codes.github.io/runstate-preview/) in a phone frame on desktop and mobile. Example runs, silent music controls and scripted reflections; [source is in a separate repository](https://github.com/Manley-codes/runstate-preview) |
| **Design prototypes and screenshots** | Evidence of a particular visual experiment. A prototype is not an installed feature unless a later checkpoint explicitly records implementation |
| **Trailer and onboarding studies** | Separate presentation explorations. They are not proof of product behavior or a completed onboarding feature |
| **Console architecture/evaluations** | The earlier Java/MySQL implementation. Preserve its lessons without attributing all of its analysis features to Android |

The October 24–26 Android records supersede older countdown, Active Run and History designs.
Dark is the default run appearance; Light and Auto are alternatives. The two music-artwork
treatments remain a temporary comparison. The ring is decorative motion, not audio analysis.
The circular album-cover experiment does not establish Spotify approval for production use.

The October 29 browser refresh is published and user-confirmed. Its compact **Your Energy**
History shortcut is still a later Android update. See the [preview guide](design/design_preview_build.md)
for links and maintenance instructions.

## Platforms and tools used

Development tools, the installed app and online runtime services have separate responsibilities.

| Role | Platform or tool | Responsibility |
|---|---|---|
| Android development | **Android Studio, Android SDK and emulator** | Build and inspect the native app. An emulator installation is separate from a physical-phone installation |
| Android app | **Kotlin, Jetpack Compose and Gradle** | Screens, run controls, app lifecycle and Android builds |
| Phone storage | **Room / SQLite** | Durable local runs, feedback, listening observations and exact selected reflection text |
| Java development | **IntelliJ IDEA, Java 17 and Maven** | Console and standalone reflection-backend development |
| Reflection backend | **JDK HTTP server and Render** | Authenticated HTTPS service for the app's online reflection/voice features; provider credentials stay server-side |
| Reflection writing | **Anthropic API** | Produces candidate reflections from allowed run context. The phone saves the selected text rather than rewriting it on reopen |
| Speech | **ElevenLabs and Android TextToSpeech** | Configured run/reflection voices with fallback paths. Provider availability and a valid subscription affect fresh synthesis; saved reflection text is separate from audio |
| Music controls | **Spotify App Remote** | Implemented Android connection and authorized playback commands, player metadata and artwork. Account setup, consent and player restrictions still apply |
| Music context | **Android music observations, Musixmatch and LRCLIB** | Optional observed-song evidence, available song analysis and timing lookup. Coverage varies; an identified song is not proof of usable analysis or a complete musical ending |
| Maps | **MapLibre Native, OpenFreeMap and OpenStreetMap data** | Optional live-map rendering. The default Start illustration is separate; the renderer is not a second GPS recorder |
| Weather | **Open-Meteo** | Optional run-start weather lookup from approximate location; the saved estimate is distinct from measured conditions |
| Legacy storage | **MySQL / JDBC** | Persistence for the original console app, not the Android phone's database |
| Verification | **JUnit, Android instrumentation and GitHub Actions** | Repeatable code checks. Physical running, listening, accessibility and provider behavior need their own evidence |
| Version history | **Git and GitHub** | Source history and review. The app repo and browser-demo repo are separate |
| Development assistance | **Codex, Claude conversations, Cowork and Claude Design** | Assisted research, design, implementation and review under Manley's direction; distinct from the API service that writes reflections in the app |
| Command line | **PowerShell** | Runs builds, tests, local services and device tools against the same repository files used by the IDEs |

Spotify App Remote is already integrated. The October 18 note calling it a future candidate and
the October 19 failed-authorization checkpoint are historical; later authorization repairs and
playback checks supersede them. Current native in-circle controls were added October 25. Their
software/fixture checks do not prove compatibility with every device, account or real run.

See [the current checkpoint](design/project_current_state.md) for precise installation/deployment
receipts and [backend/README.md](../backend/README.md) for server setup. Provider subscriptions,
service availability and usage costs are external to the browser demo. Never add private API keys,
access codes or personal route records to public documentation.

## Deployment: how the parts connect

```text
Android app → local Room records
     └──────→ configured HTTPS backend → permitted reflection/voice services

Separate browser-demo repository → GitHub Pages → simulated screen experience
```

- **Commit and push** save and share source. They do not, by themselves, prove that a phone has
  been updated or the hosted backend is running that source.
- **Build** turns Android source into an APK, or packages the Java backend. The projects have
  separate build/test commands.
- **Install** updates the APK on a selected phone or emulator. Its local Room database belongs to
  that device; updating the backend does not replace native screens.
- **Deploy** runs a backend build on Render or publishes the separate demo to GitHub Pages.
  RunState's recorded Render workflow uses manual deployment. A healthy `/health` response proves
  service liveness, not reflection quality or successful Spotify playback.

The phone's backend access code and the backend's provider API keys have different purposes.
The code authorizes a phone request; provider keys stay on the server. A saved run remains local
if an online reflection fails. The exact selected reply can later reopen from History offline.

## Reading the evidence

The current-state record uses these labels with a named scope and remaining check:

| Status | Meaning |
|---|---|
| **Planned** | Agreed direction; no implementation yet |
| **Building** | Implementation or integration is still underway |
| **Ready to test** | The stated implementation passed its software checks; a named user or real-use check remains |
| **Verified in use** | Worked in the recorded real scenario; not a promise about every circumstance |
| **Complete for agreed scope** | The agreed work and checks are finished, with any remaining limits stated |

Design decisions also use **Locked**, **Parked**, **Superseded**, **Rejected** and **Out of current
scope**. Those words describe a decision, not a test result. Keep dates and successors when reading
older records. Test totals are checkpoint evidence; consult the current workflow/test output for
a fresh result rather than treating an old count as today's coverage.

## Documentation map

| Topic | References |
|---|---|
| Current Android/backend work | [Project state](design/project_current_state.md), [creative direction](design/creative_direction_ui.md), [privacy](DATA_PRIVACY.md) |
| Start, energy and equipment | [Start/Active design foundation](design/design_start_active_run.md), [energy capture](design/design_state_scan.md), [shoes](design/design_shoe_selection.md), [effort](design/design_effort_cost.md) — read with later native checkpoints |
| Music decisions and delivery | [Development story](design/music_reflection_development_story.md), [accepted baseline](design/music_reflection_accepted_baseline.md), [selection system](design/design_music_selection_system.md), [feature register](design/music_feature_register.md) |
| Quality and scope | [Nonfunctional requirements](design/requirements_nonfunctional.md), [user goals](design/user_goals.md), [parked ideas](design/parked_feature_ideas.md) |
| Session continuity | [MEMORY](design/MEMORY.md) — working index of open items and pointers, rather than the recommended first read |

<details>
<summary><strong>Original console engineering and historical design records</strong></summary>

These records explain the Java/MySQL foundation. They remain useful examples of architecture,
testing and decision-making; they are not a current Android feature checklist.

| Document | What it demonstrates |
|---|---|
| [CURRENT_RUN_FLOW.md](CURRENT_RUN_FLOW.md) | Console save-first flow, failure paths and resolved audit findings |
| [AI_AGENT.md](AI_AGENT.md) | Original response identity, prompt architecture and constraints |
| [RunStyle ADR](design/adr_001_runstyle_surfacing.md) | Why a multi-card destination was rejected in favor of a background engine |
| [RunStyle V1](design/design_runstyle_v1.md) | Deterministic local strategy profiles |
| [Comparison logic](design/design_comparison_logic_fix.md) | Candidate matching, recency and confidence tiers |
| [Weather cleanup](design/design_weather_cleanup.md) | Console weather handling |
| [Response system](design/design_run_response_system.md) | Original post-run response and RunStyle logic |
| [Music Intelligence V1 evaluation](EVALUATION_RECORD.md) | August 18, 2026 manual evaluation that did not meet its predeclared quality bar |
| [Full V1 evaluation](design/music_intelligence_v1_evaluation.md) | Fixtures, gates and diagnostic history for that earlier implementation |

Console setup and commands are preserved in the
[project README's historical section](../README.md#console-history).
`ROADMAP.md` is a June 2026 snapshot; `ui-design-brief-v1.md` is superseded. `Archived/` retains
earlier ideas. Later Android implementations do not retroactively change an old evaluation's
result, and old results should not replace current implementation evidence.

</details>
