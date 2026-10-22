---
name: design-start-run-v7
description: "Current native Start/Active decisions followed by the August 16-19 prototype record: map foundation, energy absorption into the ring and transition into the timer. Visual design remains open."
metadata:
  type: project
status: CURRENT NATIVE DECISIONS plus dated prototype history; verification lives in project_current_state.md. Visual design is not locked.
---

# Start Run — where this screen actually is

**October 22 shoe follow-up — Ready to test, installed on Moto:** the former `SHOES / LATER` object is connected to
the native saved-pair picker. Original sneaker illustration on an organic sage/lime backing replaces
the shaded pebble, with selected model/nickname and mileage below it. Start/music sizes and placement,
the accepted Agent-panel utilities, Energy and opening design remain unchanged. Canonical behavior
and installation evidence are in `design_shoe_selection.md` and `project_current_state.md`.

**October 22 current Start direction — Complete for agreed scope, installed and accepted:** the compact GPS
status, Map, History and gear sit inside the white Agent panel, replacing the external utility/
status stack. There is **no Energy beam or decorative Energy outline**, superseding the earlier
individual-square/shared-rectangle requests. Original map artwork, orbs, main footer, lower
objects, anchored Energy, intake/Undo and Agent motion remain.

Routine music on/off lives in Settings and accessibility/status; only actionable access/setup/
errors remain inline. No additional navigation screen was added. All three APK builds and 14
focused silent Moto cases passed, including callback/touch-target, responsive-layout and intake
checks. The isolated/normal phone screenshots and Energy crop were inspected; **Manley accepted
the result: “Okay, this looks good.”** Saved data/preferences and accepted October 21
completion audio are unchanged. This UI check does not newly establish outdoor GPS or audio
acceptance; exact receipts and remaining checks live in `project_current_state.md`.

**October 18 corrected GPS Start direction — installed and checked:** Manley rejected
the large Finding GPS interruption. The GPS Start screen quietly prepares location while visible,
using existing precise permission. Start immediately enters the normal 3-second countdown; a weak
or still-acquiring signal does not block it. Compact signal status replaces the readiness modal,
20-second wait and Start anyway choice. Missing permission or disabled location still uses normal
setup. GPS preparation stays active through map toggles, countdown and cancellation, and stops
away from Start or before the recorder takes over. Nothing records or times a run before the
official start. Pre-start points are never imported into the saved route.

The static artwork remains default. Preparation does not open the live map or fetch its tiles.
It uses battery while Start is visible and can improve readiness, but cannot guarantee a quick
fix under poor satellite reception. Background/recreation cancels an unsent countdown; a
same-session marker follows only an already-sent service start. Both replacement 632-test suites
and all three APKs passed; all 31 unique phone checks passed across runs after a test-scheduling
correction, preserving all 32 completed records/11-table fields/Room 14. The actual screen showed
static artwork, available Start and small "GPS connecting…" status, with no readiness modal.
Outdoor accuracy/pace/splits remain unverified. Earlier readiness-gate receipts are historical;
exact current receipts live in `project_current_state.md`.

**October 16 Spotify follow-up, installed after the returned run review:** the original music orb now has a deliberate
`MUSIC / SPOTIFY` action that opens the installed Spotify app. It sends no playback command,
starts no run and changes no observation/sharing preference. If Spotify cannot open, RunState
shows an explanatory message. Its artwork, sizing and footer position remain unchanged; Shoes
still says `LATER`. Builds and the callback UI test on the Moto passed; the installed Spotify
launcher activity resolves. Actual Spotify playback was not controlled during verification.
Manley clarified that this object originally had other music purposes. Its current launcher is
a useful interim action, not a decision to dedicate the music entry permanently to Spotify.
This supersedes the music-placeholder status below.

**October 16 footer polish — approved direction:** keep the original music artwork with more
clearance below the footer's curved top. Give Shoes a softly uneven, shaded backing without the
enclosing circular border. Keep Start's round dial, tick marks and play symbol, but replace its
flat fill and continuous idle outline with restrained depth and a partial upper highlight.
The existing Energy-triggered glow remains. The curved side shoulders are raised within the same
footer bounds, and the crest above Start is one smooth rounded hill without the small central dip;
control sizes/positions and the stationary Energy gap are unchanged. Shoes and
Music still show `LATER`; this pass does not add their mechanics. Verification belongs in
`project_current_state.md`.

**Latest October 16 direction — original Start artwork, optional styled live map:** Manley
clarified that he wants the actual landscape features recolored, not a blue/green tint. The
live map now uses OpenFreeMap vector data with an original RunState style: light sage ground,
stronger green park/wood areas, darker cream building shapes, restrained blue road edges,
blue waterways and dashed mapped paths. Feature geometry remains real; a mapped path is not a
claim of public access or a recommended running route. No colored wash sits over the live map.

The original static Start image is restored by default, including its corner atmosphere.
**Live map** opens a separate optional location preview; **Close map** returns to Start. The
normal Active layout is restored too, with **View live map** / **View recorded map** as its
explicit options. This replaces the earlier always-visible background/Expand decision below.
The fixed portrait Energy/footer layout, white card definition and drag cancellation remain.

The October 18 correction supersedes map-only acquisition: visible GPS Start and its optional
map share one foreground, memory-only listener, retained through map toggles and countdown.
History, Settings, backgrounding and Sample mode stop preparation. The existing recorder owns
the official run; the foreground preview contributes no recorded points.
During a run, hiding the map does not stop recording. No map tiles or native map renderer are
requested by the static Start/Active views. Expanded maps show provider disclosure and linked
OpenFreeMap/OpenMapTiles/OpenStreetMap attribution. Provider details live in `../DATA_PRIVACY.md`.

**October 16 Start refinement:** Manley found that the scrollable Energy/content stack could
move into the separately fixed Start crest, and the real map's beige/white treatment lost the
earlier screen's character. On normal portrait screens, upper content, Energy and the footer now
hold separate measured space; Energy clears the footer by at least 12 dp. Dragging inside an
Energy tile does not answer it. Short screens or larger text retain access to setup/history
without allowing Energy to overlap Start. Very short landscape windows use one scrollable stack.

The RunState card stays opaque white, with a quiet green-grey edge and shadow. A blue/sage
atmosphere and the existing tree shadows sit above the real streets; these are screen decorations,
not invented parks or terrain. Map gestures are available only in Expand. The normal backdrop
follows GPS, and closing Expand recenters it. No run-recording or Energy meaning changed.
This visual pass is open to review; device results live in `project_current_state.md`.

**Earlier October 16 native map continuation — superseded by optional maps above:** Manley wanted the real map visible
by default on both GPS Start and Active, while preserving the corner tree shadows. The earlier
separate **View live map / View recorded map** entry is superseded. The same native map remains
through countdown, running and **Expand**; expansion enlarges it instead of initializing a
second map. The decorative shadows do not imply actual tree locations. History retains its
separate offline recorded-path preview.

Start's foreground preview is memory-only. An accurate cached GPS fix up to 30 minutes old can
establish a clearly labeled last-known position; only a usable source fix within 15 seconds is
current. The preview stops in History, the background and Sample mode. It creates no saved run,
route or distance before Start. The official start hands location ownership to the existing run
recorder; Active's marker and separated route sections use that recorded evidence. The map does
not request another recording stream. Missing permission or location stays an honest unavailable
or waiting state, never a fabricated live position. Paused and stale markers remain identified.

OpenStreetMap tile requests can occur automatically when GPS Start is visible and an actual
anchor exists; they are no longer limited to a separately opened map view. Requests reveal the
viewed area and IP address, as described in `../DATA_PRIVACY.md`. No new map account or hosted
service is introduced. Map loading is separate from GPS recording and run controls.

Manley reported that real street tiles worked but the first opening was slow. Keeping one map
removes the redundant expansion initialization by design; no measured speed improvement or
outdoor accuracy is claimed yet. This continuation's build/device results belong in
`project_current_state.md`, not in this decision record. New-run pace can meet the conservative
coverage policy; older/partial runs remain unchanged.

**Earlier native GPS status correction, October 15:** Active distinguishes GPS waiting,
recording, weak signal and signal unavailable from fresh usable recorded observations. Earlier distance
does not keep the recording label alive; accurate stationary readings can be healthy before any
distance exists. A usable fix older than 15 seconds loses freshness. Paused/stopped/Sample stay
distinct. That checkpoint changed status truth while the map was still illustrative; the later
map and coverage/pace work above supersedes that limitation. See `project_current_state.md`.

**Earlier native setup correction, October 15, 2026:** regular Start uses precise GPS by
default. At that checkpoint the map was illustrative and quiet status described readiness;
the October 16 foreground preview now supplies the distinct current/last-known position.
The repeated GPS/music/connection panels have moved into **Run settings**.
The Start surface keeps quiet setup/status messages and a Settings entry so the everyday path is
optional pre-run Energy, then Start.

Music has a one-time settings review. **Observe music** and **Music in reflection** start off,
including on a phone that already granted Android notification access. Done records that the review
is complete; choosing either option is optional. The app remembers the two choices separately
across restarts, and completing, discarding or canceling a run does not reset them. Sharing can
only be enabled with observation. Local observation and sending one eligible observed Spotify
title/artist to the reflection service and Anthropic remain different choices; no audio is recorded
or sent. The external-access explanation remains in Settings.

These preferences are not permission grants or claims that music is playing. At the official start,
the app snapshots observation only when the saved choice and current Android access allow it;
sharing additionally needs its separate saved choice and an available configured backend. Revoked
access leaves the remembered preference intact but prevents effective observation/sharing and shows
the needed setup. A quiet status such as enabled or access needed must not claim **Music detected**
without actual evidence. Previously saved per-run consent and observations are not rewritten.

When GPS permission is missing, Start requests precise location before countdown. When phone
Location is disabled, the setup path opens Android's Location settings. After returning or granting
permission, the runner taps Start again; setup never starts a run by itself and permission denial
never falls back to Sample distance.

**Sample demo** now lives under Settings' Demo & testing section. It is one explicitly chosen
temporary draft, not the normal default or a persistent preference. Rotation preserves the draft,
but a new-process nonce prevents Android state restoration from reviving Sample as the next-run
default. Canceling the countdown, completing/returning for another run, or discarding restores GPS.
The active Sample banner still explains that distance increases while standing still.

Reflection address/access-code setup also lives in Settings. A configured service label is not a
connectivity test. Changing or clearing that service first durably turns remembered music sharing
off; consent for an earlier destination is not carried into a new one. Preference read/save errors
are explicit and block Start until a confirmed state is available; an accepted save survives screen
rotation. The following panel-placement descriptions are retained as history of the earlier native
screen, not instructions to restore those controls below the agent.

**Native timing and accidental-start correction, October 15, 2026:** Active now makes **Active
time** the main timer; it freezes while Paused. The smaller **Total incl. breaks** value keeps
counting because elapsed time still means official start to finish, including pauses. This changes
the display hierarchy, not saved timestamps, the elapsed definition or pace calculation. Sample
mode now has an explicit banner explaining its simulated distance: it deliberately adds 3 meters
per second while Running, even at a desk, and freezes distance when Paused. GPS mode uses measured
location evidence instead; it never substitutes sample distance.

After Pause, holding to end a run with reliably saved active time below 30 seconds opens **Save
run / Discard run / Keep paused** before completion. No short run is silently removed. Save follows
the normal durable completion, receipt and reflection path; Discard explicitly removes only that
unfinished paused run. Dismissing the choice, keeping it paused or rotating preserves the same
paused run; a failed save or discard leaves a retryable error. The exact eligibility and storage
boundary are recorded in `run_initiation_register.md`.

The prepared-reflection family is already built: after durable completion, one backend request
prepares all four Energy branches before selection. A tap chooses locally and does not start
another generation request. A quick tap can still precede the network response; receipt and
initial visualizer intake also govern presentation. `design_run_response_system.md` remains the
canonical detailed contract.

**Earlier native music artwork and panel clarification, October 15, 2026 — panel placement historical:** the original profile,
lime-headphone and waveform orb is copied unmodified from the Production `start.html` manifest
(`./assets/opt/music-orb.jpg`, UUID `82c43e6a-55ff-4ef3-a580-4747bc5b5d81`) into
`android/app/src/main/res/drawable/start_music_orb.jpg`. It replaces the generic headphones,
displayed at 64 dp without a tint. The footer music object still says `LATER`; it does not open a
playlist or start playback. The shoe object also remains `LATER`, with no chooser, owned-pair
inventory or mileage. Existing optional music observation and reflection sharing are separate
working controls, explained below. The follow-up is installed on the Moto: all 85 selected silent
screen cases passed, both 291-test JVM suites and all three APK builds passed, and five affected
checks passed again after final wording polish. See `project_current_state.md` for exact evidence.

The earlier panels below the agent were functional controls or truthful status, not fake sample cards.
Their provisional placement is now superseded by the Run settings correction above:

| Visible item | What it does now | Design status |
| --- | --- | --- |
| Illustrative map / GPS label and Log History | Clarifies that the background map is artwork; the GPS choice is separate. Log History opens saved runs. | Keep provenance truthful; the map is not a live location preview. |
| Reflection connection and Reflection setup | Selects the backend address and RunState access code used for new reflection requests and chosen History retries. “Online service selected” means configured, not a successful connection test. | Working setup, currently prominent for the demo; a later Settings or onboarding home is recommended. |
| GPS / Sample run | Chooses precise GPS recording or sample distance for the next run, with real permission handling. | Working mode choice; later consolidate sample/demo controls while keeping the chosen mode clear. |
| Observe music | Opts into local media-session title, artist and app observations while Running. Android's broad notification-access grant is explained. | Working optional observation, not song selection or playback. |
| Music in reflection | Appears when observation, permission and a backend are available. Separate per-run consent allows one eligible observed Spotify title and artist to reach the backend and Anthropic; no audio is sent. | Working optional privacy choice that must remain accessible. |

No panel-layout changes were made in that artwork follow-up. Its proposed consolidation of backend
setup and sample/demo controls is now implemented by the latest correction, preserving GPS clarity
and separate music consent.

**Native journey additions, October 15, 2026:** the Start footer now includes shoe and headphone
objects marked `LATER`; neither claims a saved collection, mileage or playlist mechanics. Optional
Energy makes Start glow softly, Undo restores its idle treatment, and starting without Energy
remains available. The play triangle's inner polygon now matches the dark dial. These additions
retain the compact agent, irregular traveling wave and intake described below. The original
prototype Barlow Condensed font is also bundled for native Completion/Active metric numerals.
The full supplied Start and Completion/History recordings and all six Completion alternatives were
reviewed before these additions. Final native builds and all 82 selected isolated Moto screen checks
passed; final review/data-retention details live in `project_current_state.md`. Shoe catalog research
is in `design_shoe_selection.md`; durable owned pairs and completed-run mileage remain separate work.

**Native Start legibility follow-up, October 15, 2026:** Manley found the first moving ring too
small and faint. Its native box is now 100 dp on the Moto (previously 69 dp), or 84 dp on screens
under 400 dp wide. Bars are roughly twice as thick on the Moto, with darker sage/purple/olive
tones and a stronger minimum opacity. The wave, intake timing and optional Energy behavior stay
the same. The card grows to fit; text columns can wrap without compressing the reserved ring,
and rounded stroke caps remain inside its receiving-swell bounds. All three APK builds and five
focused physical-Moto motion/Energy checks passed. The existing preview video was refreshed;
no additional retained file was needed. Manley's visual acceptance remains pending.

**Latest card/color refinement, October 15:** Manley wanted less empty space and found the darker
ring too vibrant. Keep the larger ring and stroke; reduce card vertical padding from 15 to 6 dp
(130 to 112 dp tall on the Moto) and corners from 28 to 24 dp. Muted sage `#849B80`, purple
`#A390B3` and olive `#ACA473`, with 0.84–0.96 idle opacity, sit between the faint first pass and
the darker follow-up. Text, motion and Energy behavior remain. All three APK builds and five
existing Moto checks passed; the same preview video was refreshed without another retained file.
This is the current native card/color treatment; visual acceptance remains with Manley.

**Initial native Start motion, October 15, 2026:** Manley requested only the original agent's irregular,
traveling wave and the Energy intake motion, keeping the current native screen's other design.
The entire supplied 17.17-second recording was reviewed through sequential frames before edits.
The native ring now varies its 76 fixed-angle bars to create the spinning illusion. Energy tiles
converge, the chosen tile shrinks toward the measured ring center, and the ring briefly receives
it before returning to its idle wave. It remains an agent taking in information, not an Energy
gauge. The real optional draft updates on tap; Start stays available and Undo restores the tiles.
Backgrounding or moving the layout cancels only the visual flight; restored choices do not replay.
System-disabled animations are respected. Installed and recorded on the Moto; visual acceptance
remains with Manley. No other native layout, labels, controls or data behavior changed.

**Native Active status, October 14, 2026:** a bounded Compose pass now puts exact distance above
the smaller circular map/timer, with compact Average pace/Active below and full Elapsed fitted.
The smaller mi/km suffix belongs to the same complete semantic value. Unknown metrics, truthful
fixture/GPS labels, pause explanation and action/footer behavior remain; source/provenance may
require scrolling on Paused, short-height or large-font views. Fresh Running/settled Paused
renders were inspected, then final 287 JVM tests per variant, APK builds and 14 focused emulator
checks passed. Moto update retained 22 completed runs without a new run or reflection request.
The HTML reference file URL was blocked; no workaround or rendered-reference fidelity claim is
made. This native pass is not a locked design or the larger Active redesign; that review remains open.
See `project_current_state.md` for the review screenshots, exact verification and remaining limits.

**Native foundation, October 12, 2026 — historical context:** a first Compose visual pass followed this
prototype's map, agent card, Energy tiles and Start control. The map stays illustrative; an
explicit Use GPS choice now requests precise location and records cautious GPS evidence through
a foreground service, while fixture mode remains the default. The hardcoded GPS status,
last-at-place RunStyle card, shoe and music controls are not represented as working features.
Pre-run Energy is optional, undoable, stored
per run in Room version 5 and sent as saved reflection evidence. The functional Active controls
now have a first native circular-map presentation, with explicit fixture/GPS provenance labels; the
larger Active visual redesign Manley requested remains open. Countdown still uses the earlier
foundation screen.
See `project_current_state.md` for verification and remaining limits.

**Read this instead of `design_start_active_run.md`.** That file describes the August 13 version of
this screen, which no longer exists. It is kept as history, not as instructions.

**What follows:** a historical description of the Start screen prototype as of **August 19, 2026** —
what it does, why each piece is there, and what still isn't decided. Written so it can be picked up
cold months later without rebuilding the context from memory.

**What this file is not:** a spec. Nothing below is locked. Where something is a leaning rather than
a decision, it says so in that sentence.

**Version naming:** the current file is **Start Run V7**. V7 is V6 plus the RunStyle card. The
energy-absorption pass landed after that and its handoff still says V6 in the title — that's just a
stale filename, not a different screen. There is one live file.

---

## The short version

One screen carries the runner from standing still to finishing the run. It never navigates anywhere
else. A map fills the top; a white panel floats over the bottom.

You choose an energy level, press Start, and **the map itself shrinks down into the circle that
becomes the timer.** That single move is the strongest idea on the screen — the map isn't replaced
by a timer, it *becomes* one.

---

## Before the run

### The map

Fills the screen from the status bar down to the white panel, edge to edge. Today it shows where the
runner is standing.

**Open question, unchanged since August 16:** a map of where you're standing tells a runner nothing
they can't already see by looking up. Its only current job is to be the thing that collapses — a
transition job, not an information job. The leaning is that it should carry history instead, but
that hasn't been designed.

Two ideas attached to the map, both still open: **circular markers** for things the runner connected
to themselves (a friend request, a group, a joined event, a location they asked for — nothing
broadcasts), and **popular trails**, which reads as a cold-start feature rather than a claim that
needs a dataset behind it. Neither is designed, and it isn't settled that they belong on this screen.

### The RunStyle card

Added August 18. It slides in from the side when the screen opens — the app offering something rather
than waiting to be asked. It leaves inside the same movement as the Start collapse.

Content is the runner's last run at this place:

> **RUNSTYLE**
> LAST AT HERMAN PARK
> **4.2 MI · 38:17**
> PEGASUS 40
> LOW → HIGH

`LOW → HIGH` — where the energy started and where it finished — is the most distinctive line on it.

**Why it exists:** it is the one element on the pre-run screen saying something only RunState can
say. Everything else there is either a control or a map the runner can already see.

**It reads as pressable.** Later it opens a fuller RunStyle view. That view has not been designed and
should not be drawn from this card.

**The eyebrow wording is a placeholder.** The card's content is *last run at this place*, while
RUNSTYLE is the name of the app's pattern engine. Related, not the same thing. Naming is unresolved.

**It has to be able to not appear** — a runner at a new place, or a new user with no history, gives
it nothing to say, and inventing something there is the one move the app shouldn't make. The empty
state itself hasn't been designed; the layout just doesn't break without the card.

Static content in the prototype. No data wiring.

### The white panel

One draggable panel with **two rest positions**, about 340px apart. Route, location sharing and run
context are rows inside it. Everything on this screen has to work in both positions.

At the top of the panel sits a **ring of bars** next to `AGENT · READY` and a pulsing dot.

### Pre-run energy

Three tiles: `LOW / MODERATE / HIGH`. All three start unselected. Choosing one is optional — pressing
Start without touching them stores the value as unknown, and it never quietly becomes Moderate.

**Choosing a level sends the tiles up into the ring, where they disappear.** The circle row below
then rises into the space they left. The screen gets simpler as the runner gets closer to running.

**What the motion means:** the runner handing the agent their state. Energy is the thing the agent
consumes — it feeds the reflection and the pattern engine — so the tiles travel to where that data
actually goes.

The ring **receives** the energy. It does not turn into an energy display or a permanent gauge. A
moment, not a mode. That was a deliberate constraint on the pass so nothing has to be undone if the
ring's role changes later.

**Manley added an Undo** after the pass came back, so a mistap is recoverable. That closes the one
thing the handoff deliberately left open.

A runner who never touches a tile is a normal path — the tiles just stay put and nothing rises.

### The primary row

Shoe · **Start** · Run Mix.

Start is the largest and darkest element and the only primary action. The route control is not on
this row — it moved into the panel.

Shoe selection has its own accepted design; `design_shoe_selection.md` is canonical for it.

---

## Starting

Pressing Start does all of this on the same screen, on one ease curve:

1. A countdown runs inside the circle.
2. The panels lift and fade — about a second.
3. **The full-bleed map collapses into a 440px circle**, corner radius travelling 80px → 220px.
4. The timer starts.

No crossfade. No second screen.

**Known limitation in the prototype:** the countdown can't be interrupted.

---

## During the run

Inside the circle: **pace, duration, and calories.**

**Calories is being replaced.** Raised August 16 as the most generic metric a fitness app can show
and the one least connected to what a run meant. **Cadence** is the candidate for that slot — it's
the only number in that group a phone can produce without extra hardware. Not yet done.

**BPM is not on this screen.** It would need a sensor or a health-platform integration that doesn't
exist yet.

Pause is the central control while running. Pressing it reveals **Stop** and **Play**. Play means
resume the run — it is not a music command.

---

## Ending

**Stop is a 1.5-second hold** with a ring that fills as you hold, labelled `HOLD TO END`. This is
what settled the older open question of whether Stop needs a confirmation step — a hold *is* the
confirmation.

### How an accidental start gets undone — bounded native choice built October 15

Pause, then hold to end. If the saved transition history is complete and confirms **less than
30 seconds of active time**, the native app asks whether to **Save run**, **Discard run**, or
**Keep paused**. The threshold uses active time, so sitting paused does not make an accidental
start ineligible. Exactly 30 seconds, longer runs and uncertain histories follow ordinary
completion rather than guessing that the run was accidental.

Save completes the same durable run and then starts its normal receipt/reflection path. Discard
requires the runner's explicit choice and removes only that exact unfinished, paused run and its
owned child observations. It cannot delete a completed History entry. There is no automatic
short-run deletion, no provider request or completion receipt before Save, and no new run created
by retry. Keep paused, dismissal and rotation preserve the paused session. Storage failure leaves
the run paused with a visible retry path.

The earlier August prototype considered saving a completion first and offering removal afterward.
The native correction instead confirms the decision while the unfinished session is already
durably paused. Automatic accidental-start detection remains separate, unbuilt work.

(In the console code, `cancelCount()` exists and is wired to nothing.)

---

## What the ring is

**Manley's current leaning: the ring is a symbol for the run agent** — something that works across
multiple run contexts, not a music instrument.

It started as decoration. It reacts to music, it sits beside `AGENT · READY`, and it now receives
the energy tiles. Those are three different jobs it's already doing, which is why the question came
up at all.

**This is a leaning, not a decision.** It's recorded here because it changes how future work on the
ring should be judged, and because treating it as settled once already produced the wrong advice.
What it does during a run, and what it means long-term, are open.

---

## What's fake, and known to be

Recorded so nobody "fixes" them by accident or mistakes them for bugs:

- The map ignores pinch
- The route line during the run is a timer effect, not real tracking
- The countdown can't be interrupted
- The RunStyle card's content is static
- All data is example data

None of these break anything in a preview, and the guidance line on the hosted version steers people
toward what does work.

---

## Requirements exposed by the historical prototype

The prototype exposes requirements. It does not establish native implementation; current native
map behavior is recorded at the top, and broader implementation status lives in
`project_current_state.md`. Rows not explicitly updated below retain their historical context.

| Needed | Status |
| --- | --- |
| Live elapsed time, distance, pace | Mobile tracking / GPS / session work |
| Real map and route tracking | Native GPS Start/Active map continuation is recorded above; recorded evidence feeds the route, while field accuracy and performance checks remain separate. |
| Cadence, if it takes the calories slot | Phone sensors — reachable without extra hardware, not built |
| BPM | Sensor or health-platform integration; not collected |
| Music-reactive ring | Real playback signal and a permission strategy |
| Live song, artist, album | Music-provider integration |
| Pause / resume / stop states | Android state machine, Room lifecycle, UUID-bound owner, active-row discovery, recovery core, production database and recovery-before-start coordinator are built; the Android initialization call site, foreground-service execution and UI wiring remain |
| Shoe mileage | Design accepted; exactly-once completed-run mileage not implemented |
| RunStyle card content | Needs a query for *last run at this route/place*; the data exists |
| Accidental short start | Native explicit Save/Discard/Keep paused choice below 30 reliable active seconds is built; automatic detection remains later |

Later refinement still has to test: contrast over the map, dynamic type, screen-reader labels and
state announcements, tap targets, reduced motion, battery, outdoor visibility, the music-free state,
and what happens if the app is interrupted mid-run.

---

## Related files

- `design_start_active_run.md` — the August 13 version. History.
- `design_shoe_selection.md` — canonical for the shoe control.
- `design_state_scan.md` — canonical for the energy tiles' stored meanings.
- `design_preview_build.md` — the public hosted version of this screen.
- `creative_direction_ui.md` — read the open-mind section before advising on anything visual.
