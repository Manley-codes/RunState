# RunState Documentation

A guided path through this project's design record, written for someone reviewing the repository
rather than working in it.

The short version: RunState's documentation is more detailed than a console app strictly requires,
on purpose. Decisions are written down with their reasoning and an explicit status, so that a
choice made in June can be re-examined in August without anyone reconstructing why it was made.

## Platforms and tools used

**Platform list updated October 16, 2026; individual checkpoints retain their dates.**
Development tools help us build the app; runtime services
are what the app actually uses. The browser designs, native phone app and reflection backend
are separate parts of this project.

| Role | Platform or tool | How it has helped RunState and its current status |
|---|---|---|
| Development | **Android Studio** | The IDE for the native Android project in `android/`. Its SDK, emulator and Java runtime support Gradle builds, visual review and APK installation through PowerShell. The October 14 bounded Active pass passed JVM/APK and 14 focused emulator checks; Moto update retained all 22 runs. Native installation is separate from Render deployment. One outdoor screen-off capture check passed; distance accuracy and wider reliability remain unverified. |
| Development | **IntelliJ IDEA** | The IDE for the original Java console app and Java reflection backend. IntelliJ's bundled Maven and Java runtime (JBR) were also used during build/test work; direct Java 17 checks now verify the backend launch boundary. Opening either IDE does not create a separate copy of the repository. |
| Development | **Codex Desktop and Codex CLI** | Assisted implementation, code review, builds, tests and device checks. Earlier work used CLI handoffs; Manley later authorized direct implementation. Manley still handles commits and pushes. These are development assistants, not the app's reflection service. |
| Development | **Earlier Claude conversations, Cowork and Claude Design** | Helped planning, design review and screen prototypes recorded in the design documents. Their design/planning role is separate from the Anthropic API that generates actual app reflections. |
| Development | **PowerShell** | The Windows terminal used to run builds, tests, local backend processes and Android device tools. It works on the same project files as the IDEs. |
| Development | **Git and GitHub** | Preserve version history, publish the public source and run automated checks through GitHub Actions. Git records changes; GitHub hosts them. A source push does not itself prove the Render app has deployed. |
| App implementation | **Kotlin and Jetpack Compose; Java 17** | Kotlin/Compose build the native phone screens and behavior. Java 17 supports the console and the separate reflection backend, which currently uses the JDK HTTP server rather than Spring Boot. |
| Build and verification | **Gradle, Maven and JUnit** | Gradle builds Android; Maven builds the Java projects. Unit and Android device tests check storage, run controls, failure handling and exact saved reflections. Automated tests are separate from physical outdoor/audio acceptance. |
| Local storage | **Room/SQLite and MySQL** | Room manages the Android phone's SQLite database: runs, feedback, observations and exact selected replies stay local. MySQL belongs to the original console app. The reflection backend currently has no hosted run database; its request cache is temporary memory. |
| Android map rendering | **MapLibre Native 13.7.1 (OpenGL)** | Draws the optional GPS Start/Active Live map. The normal Start view restores its static illustration; opening Live map enables a temporary foreground position preview without creating a run. Active uses the existing recorder's route and current/last-known position. The renderer measures no distance and starts no second recorder. Corner tree shadows remain decorative. [Official release](https://github.com/maplibre/maplibre-native/releases/tag/android-v13.7.1). |
| Street-map data and styling | **OpenFreeMap; OpenMapTiles schema; OpenStreetMap data** | Supplies vector features when Live map is opened with an actual location anchor. RunState's original inline style independently colors sage landscape, cream buildings, blue water and dashed paths; it is not a screen tint or a derivative of the hosted Liberty style. The stable TileJSON points to current weekly data. No account/key, external style, sprite or terrain source is used. Viewed-area requests disclose network location and map areas; linked attribution remains visible. Public service has no availability guarantee. [Official integration guide](https://openfreemap.org/quick_start/); [map privacy](DATA_PRIVACY.md#android-street-map--october-16-2026). |
| Map labels | **Noto Sans Regular** | MapLibre downloads and caches only the required glyph ranges from OpenFreeMap for street and place names. No new font binary is bundled. Noto Sans is licensed under SIL OFL 1.1; the app's existing notices identify the source and license. [Provider font sources](https://github.com/hyperknot/openfreemap-styles/tree/main/fonts); [license record](https://github.com/hyperknot/openfreemap-styles/blob/main/LICENSE.md). |
| Map networking | **OkHttp 4.12.0** | The dedicated map client permits only the declared HTTPS OpenFreeMap TileJSON, bounded vector-tile coordinates and Noto Sans Regular glyph ranges. It identifies RunState and preserves cache validators; no map credentials or reflection context are sent. Normal Start/Active views make no new tile requests, and no offline-area download or prefetch feature is enabled. [Official source and license](https://github.com/square/okhttp/tree/parent-4.12.0). |
| Run-start weather | **Open-Meteo; OkHttp** | An off-by-default remembered choice permits one direct HTTPS lookup from a rounded approximate GPS start location. The saved modeled hourly estimate powers Completion/expanded History and can enter the first reflection without coordinates. Three-second total budget; no polling, retry or old-run backfill. No new account/key/subscription; public noncommercial endpoint terms must be revisited before commercial release. [API](https://open-meteo.com/en/docs); [Android privacy](DATA_PRIVACY.md#android-weather-near-the-run-start--october-16-2026). |
| Reflection runtime | **Anthropic Claude API** | The hosted backend generates four conditional reflection choices from one approved evidence snapshot. Prompt v1.6 is Live at `bfc64ad`, retaining saved hourly-weather evidence and accepting independently sourced song labels. A witnessed local live call proved actual Musixmatch labels reached Claude; a separate silent Moto hosted call returned four real candidates. Keys stay backend-only. |
| Song understanding | **Musixmatch Pro API, Basic** | Manley approved/purchased the $49/month plan. One supplied title/artist is matched, then only theme/mood labels enrich unprepared-song reflections; no lyrics, audio, run facts or full history go to Musixmatch. Local real-provider and Render's own-key checks passed for Lovely Day/Bill Withers. This does not find vocal-hook timestamps or rank a whole run's songs. |
| Speech runtime | **Android TextToSpeech; ElevenLabs `1st test voice`** | Saved-run facts stay local; the exact selected reflection uses Manley's designed male voice with Eleven Multilingual v2, then the existing music handoff. `cb23da6` is Live. Actual hosted facts → cloud reflection MP3 → instrumental passed in 40.732 s; Manley confirmed all three sounded right. Audio stays in memory, and all 32 saved runs/fields were preserved. History remains voice-only; unavailable service retains the pre-audio local fallback. |
| Asset creation and local playback | **ElevenLabs Music v2; Android MediaPlayer** | Generated one temporary original 10-second instrumental on October 14, bundled in the APK with an Eleven Music credit. MediaPlayer plays it after Completion speech when no eligible actual-song plan exists. It remains separate from the requested custom spoken reflection voice and creates no per-run music-generation request. |
| Music evidence and current-song passage | **Spotify through Android media sessions** | Supplies partial local title/artist/playback reports during opted-in runs; separate sharing approval permits one eligible identity in a reflection. For the curated original Sunday Best recording, checked controls can automatically play its accepted 15.5–25.5-second vocal entrance after reflection speech and restore prior position/state. No SDK, audio extraction, other-song selection or queue editing. Manley confirmed the automatic pairing; wider recordings/controls remain unverified. |
| Hosted backend | **Render** | The existing `runstate-reflections` Starter service in Oregon ($7/month, free Hobby workspace) uses manual deployment and `/health` monitoring. Latest `cb23da6` deployed successfully in 55.2s; health and the actual hosted custom-voice sequence passed. Earlier `bfc64ad` separately passed Musixmatch-key and silent authenticated reflection checks. Hosting runs the GitHub backend copy independently of Android installation. |
| Deployment packaging | **Docker** | Packages Java 17 and backend resources; the Dockerfile copies full `src/` and runs Maven test/package. Latest `bfc64ad` built and started the Anthropic backend on internal port 10000. The `--remote` CMD remains live mode; prompt v1.6 source is deployed. |
| Design preview | **Local HTML and GitHub Pages** | `start.html`/`completion.html` and the public preview helped review appearance and interactions. They are browser prototypes, not the installed Kotlin/Compose phone app, and do not prove native tracking or backend behavior. |

Render hosting has an ongoing cost while the paid service remains provisioned, even without a
reflection request. Live Anthropic usage costs extra when requests reach it. Hosting can be
changed or cancelled; local tracking, feedback and saved History remain usable offline, while
new live reflections need a working connection.

For the latest implemented behavior and remaining checks, read
[the Android checkpoint](design/project_current_state.md). Backend setup is described in
[backend/README.md](../backend/README.md); do not put private API keys or access codes in these docs.

## Deployment: how the parts connect

**October 17 custom reflection voice Live:** Manley pushed backend `25f2637`, Android `eb793cd`
and documentation `cb23da6`. Manual deployment of latest `cb23da6` succeeded in **55.2 seconds**,
deployment `dep-db9g9961egvs73f5sg10`. Public health returned 200/ok with both voice and song-cue
capability. The real hosted Moto voice check then passed in **40.732 seconds**: generic local
sample facts → custom ElevenLabs reflection MP3 → instrumental, with natural completion and no
local reflection fallback. Manley confirmed the voices and sequence sounded right. Existing keys
were never revealed, and every saved run/field was preserved. This activates the chosen voice
without adding a phone provider credential, voice agent, audio cache or new subscription.

**Latest October 16 music activation:** Manley pushed `bfc64ad` and privately saved the separate
Musixmatch key on Render. Manual Deploy → Deploy latest commit completed successfully in one minute,
deployment `dep-db9foq7btjjs73bikpmg`. Health returned HTTP 200/ok with song-cue support. A bounded
read-only diagnostic inside the running service retrieved real Lovely Day/Bill Withers themes/moods;
a separate silent Moto request used fictional run facts and its existing encrypted connection to
receive four real reflection candidates. All 32 saved runs/fields stayed unchanged. Windows User
environment settings alone would not have configured this cloud service. These are distinct checks:
public health is not an analysis receipt, and deployment does not install an Android update.

**Latest October 16 continuation:** Manley committed/pushed backend `da808a0`, adding compatible
optional hourly weather evidence and prompt v1.4. The Android weather update is installed, sharing
remains off, and all 32 old run records are preserved. Direct Render review still showed `bcde0c4`
Live; Manley then selected Manual Deploy → Deploy latest commit. Direct review confirmed
`da808a0` **Deploy succeeded / Live**, deployment `dep-db978k6q1p3s73arjd3g`, in 1m36s. Public
`/health` returned HTTP 200/ok, and the visible build/start log tail passed. This updates the hosted
server, while the Android APK was installed separately; pushing alone had not deployed the code.
The older v1.3 deployment and audio evidence below remain
historical, separate checks. See [the current checkpoint](design/project_current_state.md).

**Historical October 14 checkpoint:** Manley chose `Manual Deploy` → `Deploy latest commit`, reported
Succeeded/Live and confirmed `bcde0c4` on Render, replacing `85cbfab`. The pushed local/GitHub
`master` copies match that commit; public health returned 200/ok. This deploy includes prompt v1.3.
Latest build logs were not inspected. A subsequent connected-Moto Sample separately passed one
bounded v1.3 writing check and cold History reopen. Its exact reply was:

> You completed a sample run in just over 9 seconds at 6:57 p.m. That's time on your feet.

It matches the 9.384-second Sample and leaves unknown post-run feelings/questions out, with no
Energy choice, music sharing or assistant Retry. After cold restart, 21 completed runs included
all 20 prior IDs; every stored run/reflection/transition/location field hash was unchanged, along
with Online service and the exact new History text. Temporary read-only inspection copies were
removed. This is one output pass, not broad writing quality or a new listening confirmation.
The subsequent Spotify check below supplies current hearing/compatibility proof. No code change
or test rerun was needed for either QA phase.

**Spotify follow-up:** A second fresh v1.3 Sample (about 0.01 mi FIXTURE, 9.400 s elapsed,
5.379 s active) finalized READY/NO_SELECTION/non-FAKE with no Energy or music observation/sharing:

> You covered 0.01 miles of sample distance in under 10 seconds. A brief run, wrapped up at 7:05 p.m.

Manley heard facts, reflection and instrumental in order. Spotify became quieter or paused and
returned to normal by itself; focus logs support release/return and Spotify was PLAYING afterward.
RunState sent no Spotify play command or song metadata/audio to the model. The final audit found
22 completed/0 active with all prior 21 run/reflection/transition/location field hashes unchanged.
Temporary copies were removed and normal stay-awake 0 restored. This is one Moto/session pass;
wider compatibility, phone calls and Bluetooth remain unverified. Active visual work was next at
that QA point; the later bounded native pass below is complete. Actual-song playback remains later.

**Native Active follow-up:** Distance now leads above a smaller map/timer with full Elapsed and
compact pace/Active below. Fresh Running/settled Paused renders, 287 JVM tests per variant, three
APK builds and 14 focused emulator checks passed; the Moto installation retained all 22 runs and
exact stored data with no new run or provider request. Three necessary before/after screenshots
were saved under `android/artifacts/`; no new app asset or backend change was made. This is a
native layout pass, not a locked design or an HTML-reference clone. Paused/short/large-text views
may need scrolling for source labels. Installing this APK changes phone presentation; it does not
deploy a different backend on Render. The larger Active design review remains open.

A **commit ID** names the same saved source snapshot in local Git and on GitHub after pushing.
**Deployment** builds and runs the chosen snapshot on Render; it is a separate action and can
redeploy the same commit. A healthy service means its health endpoint responds, not that a new
AI-written reflection has met the wording rules.

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

**October 14 follow-up:** A timed CPU wake safeguard passed one Moto outdoor screen-off capture
check, with fresh fixes throughout the 110.786-second locked interval and no stale rejections.
The run saved 0.08 miles as partial GPS and reopened its exact live reply and one-section path.
All 19 records remain. This is a device/session capture result, not an independently measured
distance-accuracy claim. GPS still uses the deliberately conservative PARTIAL label.

The earlier no-selection reply's "remains unrecorded" sentence prompted local v1.3 guidance:
omit post-run feelings and missing-answer commentary, and do not request an Energy answer.
All 45 backend tests passed; a local live check could not obtain a usable private configuration
and made zero AI requests. That was before the later `bcde0c4` v1.3 deployment and successful
bounded live Sample above. Existing saved replies are never rewritten. History now resets to the top
when a new first run appears, preserving position through feedback changes; all eleven focused
History checks passed. The Android build/JVM checks also passed. Manley's final October 14 decision
approved a temporary original instrumental until an actual song can be used. Completion now adds
one 10-second local cue after successful reflection speech, with Stop audio and cancellation on
navigation/new run/app pause; History remains voice-only. Final verification passed 287 JVM tests
per Android variant, all three APK builds and all 76 emulator screen cases, including ten new
audio checks and actual-player playback. The suite found a real footer overlap intercepting Stop
touches; a measured footer/weighted scroll viewport repaired the UI, and the original touch check
and complete rerun passed. Moto installation preserved data without a reset; a new Sample run
received one live v1.2 response family, then Manley confirmed receipt → reflection → cue was
"all in order and landed perfectly." Cue playback adds no network generation. The necessary MP3 is bundled at
`android/app/src/main/res/raw/runstate_reflection_instrumental.mp3`; its origin and checksum are
recorded in [the music research note](design/research_music_reflection_generation.md). It adds no
runtime ElevenLabs request, credential or backend deployment. Actual-song source/playback,
wider audio/tracking/battery reliability and broad writing quality remain open; the later single-Moto
Spotify yield/return proof is recorded above.

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
