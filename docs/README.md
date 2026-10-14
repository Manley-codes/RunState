# RunState Documentation

A guided path through this project's design record, written for someone reviewing the repository
rather than working in it.

The short version: RunState's documentation is more detailed than a console app strictly requires,
on purpose. Decisions are written down with their reasoning and an explicit status, so that a
choice made in June can be re-examined in August without anyone reconstructing why it was made.

## Platforms and tools used

**Current record: October 13, 2026.** Development tools help us build the app; runtime services
are what the app actually uses. The browser designs, native phone app and reflection backend
are separate parts of this project.

| Role | Platform or tool | How it has helped RunState and its current status |
|---|---|---|
| Development | **Android Studio** | The IDE for the native Android project in `android/`. Its Android SDK and emulator tools build/install the app and test screens, permissions and simulated GPS. The Moto g play (2026) provides actual-device checks; outdoor accuracy and continuous screen-off tracking still need validation. |
| Development | **IntelliJ IDEA** | The IDE for the original Java console app and Java reflection backend. IntelliJ's bundled Maven and Java runtime (JBR) were also used during build/test work; direct Java 17 checks now verify the backend launch boundary. Opening either IDE does not create a separate copy of the repository. |
| Development | **Codex Desktop and Codex CLI** | Assisted implementation, code review, builds, tests and device checks. Earlier work used CLI handoffs; Manley later authorized direct implementation. Manley still handles commits and pushes. These are development assistants, not the app's reflection service. |
| Development | **Earlier Claude conversations, Cowork and Claude Design** | Helped planning, design review and screen prototypes recorded in the design documents. Their design/planning role is separate from the Anthropic API that generates actual app reflections. |
| Development | **PowerShell** | The Windows terminal used to run builds, tests, local backend processes and Android device tools. It works on the same project files as the IDEs. |
| Development | **Git and GitHub** | Preserve version history, publish the public source and run automated checks through GitHub Actions. Git records changes; GitHub hosts them. A source push does not itself prove the Render app has deployed. |
| App implementation | **Kotlin and Jetpack Compose; Java 17** | Kotlin/Compose build the native phone screens and behavior. Java 17 supports the console and the separate reflection backend, which currently uses the JDK HTTP server rather than Spring Boot. |
| Build and verification | **Gradle, Maven and JUnit** | Gradle builds Android; Maven builds the Java projects. Unit and Android device tests check storage, run controls, failure handling and exact saved reflections. Automated tests are separate from physical outdoor/audio acceptance. |
| Local storage | **Room/SQLite and MySQL** | Room manages the Android phone's SQLite database: runs, feedback, observations and exact selected replies stay local. MySQL belongs to the original console app. The reflection backend currently has no hosted run database; its request cache is temporary memory. |
| Reflection runtime | **Anthropic Claude API** | The hosted backend now generated two new Moto Sample reflections; the latest exact text survived cold History reopen without a FAKE label. Manley entered the key privately on Render; it stays backend-only. A no-selection wording issue remains, and small checks do not prove broad quality. |
| Speech runtime | **Android TextToSpeech** | Speaks the factual save receipt and exact stored reflection, with audio-focus/failure handling. Manley confirmed hearing saved facts followed by the latest hosted live reply. Music duck/resume/snippets remain unverified. |
| Music evidence | **Spotify through Android media sessions** | Supplies partial local title/artist/playback observations while an opted-in run is Running. A separate per-run choice permits one eligible title/artist in reflection context. No Spotify SDK, preview playback or music snippet is implemented; metadata access is not playback permission. |
| Hosted backend | **Render** | The `runstate-reflections` Starter service in Oregon ($7/month, free Hobby workspace) is Live with Anthropic and `/health` monitoring. Hosted FAKE staging preceded private key entry/live-mode deployment. Two paid phone generations, exact cold reopening and the latest audible sequence now passed. |
| Deployment packaging | **Docker** | Render built/deployed Java 17 after all 45 Linux backend tests passed. The test-reader repair kept security assertions. The FAKE command override is now cleared; the committed `--remote` CMD runs live mode with the server key. App code stayed at `85cbfab` through configuration deployments. |
| Design preview | **Local HTML and GitHub Pages** | `start.html`/`completion.html` and the public preview helped review appearance and interactions. They are browser prototypes, not the installed Kotlin/Compose phone app, and do not prove native tracking or backend behavior. |

Render hosting has an ongoing cost while the paid service remains provisioned, even without a
reflection request. Live Anthropic usage costs extra when requests reach it. Hosting can be
changed or cancelled; local tracking, feedback and saved History remain usable offline, while
new live reflections need a working connection.

For the latest implemented behavior and remaining checks, read
[the Android checkpoint](design/project_current_state.md). Backend setup is described in
[backend/README.md](../backend/README.md); do not put private API keys or access codes in these docs.

## Deployment: how the parts connect

**Verified October 13:** Manley pushed the repair and documentation to GitHub `master`. A manual
Render deployment of `85cbfab` built the Docker image, passed all 45 Maven tests on Linux and
started the Java 17 FAKE server. Manley then rotated the demo access code and redeployed the same
version with its replacement configuration. Public HTTPS health/auth/candidate checks and a
labelled Moto FAKE Sample reply/cold reopen passed without paid AI. He then privately entered
the hosted Anthropic key and cleared the FAKE launch override. Another configuration deployment
of the same `85cbfab` started live mode. Two new Sample runs received real reflections; he
confirmed saved facts followed by the second reply, and its exact text/metrics/online settings
survived cold restart with no FAKE label. Local USB forwarding is removed and local servers are
stopped. This meets the hosted live phone delivery/reopen gate, with two paid generations.

The sequence is **GitHub code → Docker/Maven build → running Render backend → HTTPS phone
request → Anthropic when live mode and its key are configured**. Each part has a separate job:

- **Build** turns source into runnable software and checks it. Maven/JUnit build and test the Java
  backend inside Docker; a container packages its environment consistently. The first Linux test
  read beyond a complete HTTP response and hit a reset. Reading the declared response length
  repaired that test without weakening 403/413/security checks or skipping tests.
- **Deploy** starts a built version on Render, where a restricted, non-root Java process runs the
  backend. Render provides the public HTTPS entry point. This service uses manual deployments:
  pushing another commit alone does not change the running version. Redeploy/rollback controls
  select the version to run. Saving new credential values changes configuration; a configuration
  deployment starts a process that reads them. Merely editing a saved setting is not proof that
  the running process has the replacement.
- **Install** puts the Android APK on the phone. Gradle builds that separate Kotlin/Compose app;
  its screens and Room database do not live inside the backend container.

There are two different credentials. The **demo access code** lets the phone call RunState's
backend and is encrypted in phone settings after setup. The **Anthropic API key** lets the backend
call the writing provider and stays server-only. Replacing one does not replace the other. The
HTTPS checks prove server liveness/authentication; the additional phone checks now prove live
delivery/saved-text reopening and one heard receipt-to-reflection sequence. Render's `/health`
monitoring checks server liveness; separate tests establish provider/phone behavior, and hearing
does not prove GPS accuracy or music handling.

Room saves the run locally before asking for a reflection. A network failure leaves the saved
run intact and offers reflection retry. Once selected, the exact reply and frozen evidence remain
attached to that run, so History can reopen them offline without generating new text. Paid Render
hosting continues while provisioned; live Anthropic requests add separate usage costs. Hosting
can be changed/cancelled while the phone's local records remain.

The latest no-selection reply mentioned that the after-run feeling "remains unrecorded", contrary
to the existing rule to stay quiet about unanswered Energy. Structure, transport and persistence
worked; writing quality still needs a bounded follow-up. No prompt fix or saved-reply rewrite
has been made. History's older scroll position also sometimes hides the new row above until
scrolling up; that is navigation polish, not lost data. Outdoor GPS continuity/accuracy, music
duck/resume/snippets and broad writing quality remain unverified.

---

## If you only read four files

**1. [CURRENT_RUN_FLOW.md](CURRENT_RUN_FLOW.md)** — *what the console application actually does*

The verified as-is behavior of one complete run, with a flowchart covering failure paths. It also
carries a resolved-audit table: four gaps that were found in a self-audit, what each one really
risked, and how each was closed. Start here to understand the system.

**2. [AI_AGENT.md](AI_AGENT.md)** — *the most interesting design problem in the project*

How the post-run response is constructed: the agent's intended voice, the two-constant prompt
architecture, and the constraints — including why below-average performance is filtered out before
the prompt is built rather than being handled by instructing the model to be tactful.

**3. [design/adr_001_runstyle_surfacing.md](design/adr_001_runstyle_surfacing.md)** — *a decision record, including a rejected design*

An architecture decision record covering how RunStyle should surface to the runner. The
multi-card destination was rejected; RunStyle became a background engine instead. Worth reading
for the reasoning that produced a "no."

**4. [EVALUATION_RECORD.md](EVALUATION_RECORD.md)** — *how quality gets judged here*

A feature was built, evaluated against a rubric written before testing began, blind-graded by two
independent graders, and did not pass. That result is recorded rather than worked around.

---

## How decision status works

Documents in this project use explicit status words, because vague status is how a paused idea
gets mistaken for a dead one:

| Status | Meaning |
|---|---|
| **Current** | The working answer as of the date recorded. A reference, not an authority against a new idea. This is the default. |
| **Locked** | Settled, and the document names what would reopen it. |
| **Parked** | Deliberately set aside. The entry names what unlocks it. |
| **Out of current scope** | Not the right focus at this stage. Not a judgment on merit. |
| **Superseded** | Replaced by a named successor. Kept as history. |
| **Rejected** | Considered and declined, with reasons. |

Superseded documents keep a banner at the top pointing at whatever replaced them, and are kept
rather than deleted.

---

## Map of the documentation

### Built and verified

| Document | Covers |
|---|---|
| [design/project_current_state.md](design/project_current_state.md) | Current native Android/backend checkpoint and physical/hosting verification limits |
| [CURRENT_RUN_FLOW.md](CURRENT_RUN_FLOW.md) | As-is console behavior, flowchart, resolved audit |
| [AI_AGENT.md](AI_AGENT.md) | Agent identity, prompt architecture, constraints |
| [DATA_PRIVACY.md](DATA_PRIVACY.md) | Exactly what leaves the machine, when, and what a failure does or doesn't guarantee |
| [design/design_runstyle_v1.md](design/design_runstyle_v1.md) | RunStyle V1 — local deterministic profile, never sent to the model |
| [design/design_comparison_logic_fix.md](design/design_comparison_logic_fix.md) | Candidate-based comparison with recency and confidence tiers |
| [design/design_weather_cleanup.md](design/design_weather_cleanup.md) | Weather as shipped |
| [design/design_run_response_system.md](design/design_run_response_system.md) | Post-run response and RunStyle logic |

### Design records and decisions

| Document | Covers |
|---|---|
| [design/adr_001_runstyle_surfacing.md](design/adr_001_runstyle_surfacing.md) | ADR — RunStyle as background engine; multi-card destination rejected |
| [design/creative_direction_ui.md](design/creative_direction_ui.md) | Visual direction for the mobile client |
| [design/design_start_active_run.md](design/design_start_active_run.md) | Start-to-active-run screen foundation |
| [design/design_state_scan.md](design/design_state_scan.md) | Pre-run energy capture |
| [design/design_shoe_selection.md](design/design_shoe_selection.md) | Shoe selection and mileage accrual |
| [design/design_effort_cost.md](design/design_effort_cost.md) | Effort cost capture and the Quiet Gains unlock |
| [design/requirements_nonfunctional.md](design/requirements_nonfunctional.md) | Eleven quality bars, each with a status |
| [design/idea_organization_analysis.md](design/idea_organization_analysis.md) | Every loose idea, collapsed into four systems and graded |

### Music Intelligence

| Document | Covers |
|---|---|
| [EVALUATION_RECORD.md](EVALUATION_RECORD.md) | **Start here** — what was built, how it was judged, why it did not pass |
| [design/design_music_intelligence_v1.md](design/design_music_intelligence_v1.md) | The canonical plan and contract |
| [design/music_intelligence_v1_evaluation.md](design/music_intelligence_v1_evaluation.md) | Fixtures, gates, full diagnostic history |
| [design/design_music_selection_system.md](design/design_music_selection_system.md) | The aim under the whole music layer, in six layers |
| [design/music_feature_register.md](design/music_feature_register.md) | Named music decisions, each with a status and execution path |
| [design/evidence/](design/evidence/) | Raw grading packets, probe outputs, smoke transcripts, screenshots |

### Scope and research

| Document | Covers |
|---|---|
| [design/user_goals.md](design/user_goals.md) | Core goals, phases, product positioning |
| [design/research_app_landscape.md](design/research_app_landscape.md) | Competitive research, RPE science, scope-creep watch |
| [design/parked_feature_ideas.md](design/parked_feature_ideas.md) | Parked ideas — each names what would unlock it |
| [design/run_initiation_register.md](design/run_initiation_register.md) | Run detection and initiation flows |

### History

`ROADMAP.md` is a June 2026 snapshot and `ui-design-brief-v1.md` is superseded — both carry
banners saying so. `Archived/` holds earlier branding, feature, and idea documents that have been
harvested into the current records. All are kept as history and are not live.

---

## A note on `design/MEMORY.md`

That file is a working index maintained during active development. It tracks open items and what
unlocks each one, and it is written for continuity between working sessions rather than for a
first-time reader. It is genuinely useful for understanding what is unresolved — but this file,
not that one, is the intended starting point.
