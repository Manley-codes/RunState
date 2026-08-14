---
name: design-start-active-run
description: "Current Start and Active Run screen foundation as of August 13 2026 — one continuous pre-run-to-tracking screen, its agreed behavior, visual evidence, and unbuilt dependencies"
metadata:
  type: project
status: CURRENT DESIGN FOUNDATION — stable enough to move on; interactive prototype only, not production implementation
---

# Start and Active Run — current design foundation

## Status and scope

This file is the canonical record for the current Start-to-active-run screen. It replaces earlier
visual proposals that used a dedicated State Scan screen, a full-screen GPS foundation, a hold or
swipe action, or one expandable parent panel. Those ideas remain design history; they do not describe
the current prototype.

The current Claude Design prototype is a **stable visual and interaction foundation**, not a final
production specification. It is good enough to leave this pass and continue the UI queue. Colors,
type details, spacing, and motion remain adjustable through the prototype's tweak controls and later
testing. Nothing here means the mobile tracker, GPS, sensor, or music integrations are built.

The governing product path is one continuous screen:

`pre-run setup → countdown → active tracking → paused controls → stop or resume`

The screen transforms in place. Starting a run does not navigate to a visually unrelated tracker.

---

## Pre-run state

### Screen foundation

- A bright, foggy near-white or porcelain field creates the surrounding atmosphere. It is not flat
  empty white: subtle cloudiness, tonal depth, and soft material variation keep it alive.
- A large circular instrument sits near the upper-middle of the screen. A grayscale photograph of a
  group running on a bridge is contained inside it.
- The elapsed-time display begins at `00:00:00` inside the circle.
- Short radial bars surround the circle. At rest they form a quiet, settled ring rather than an
  active music animation.

### Starting energy

- The stored choices remain exactly `LOW / MODERATE / HIGH`.
- The current visual form is three adjacent, independent rounded-square tiles. There is no outer
  selector panel and no separate `Start energy` or `Choose Start Energy` label in this version.
- The neutral square treatment is named **State Glass** in the Claude Design tweaks. Its current
  relationship moves from light gray to medium gray to near-black, but color variants remain
  experiments rather than semantic definitions of the three levels.
- All three choices begin unselected. A dark HIGH tile or any other aesthetic variant must not be
  treated as a stored selection without an explicit user action.
- Selecting a level records that choice and gives the Start button a clear but restrained readiness
  halo. It does not begin the run.
- Starting energy remains optional. Pressing Start with no energy selected records the value as
  unknown; it never silently becomes Moderate.

### Pre-run actions

- A central black circular Start button with a white right-facing triangle is the primary action.
- A smaller shoe control sits to its left; a smaller route/trail control sits to its right.
- These are solid circular controls, not glass energy tiles.
- The shoe and route controls establish quick entry points only. Their selection, suggestion,
  prefilling, correction, and persistence behavior remain part of the progressive-input-ladder task.
- There is no `Not running today` action; ordinary navigation already provides the exit.

---

## Start transition

Pressing the central Start button performs the transition on the same screen:

1. A `3 → 2 → 1` countdown appears inside the circular instrument.
2. The energy tiles and the three pre-run controls rise slightly and fade away.
3. The circular instrument moves higher on the screen to create working space below it.
4. At the end of the countdown, the elapsed timer starts and the active metrics appear.
5. If music is playing, the radial bars respond to its pattern. With no music signal, the ring stays
   in a quieter settled state rather than pretending to visualize audio.

The motion should feel responsive, controlled, and athletic. Exact timing and reduced-motion
behavior remain a later implementation and accessibility check.

---

## Active-run state

### Circular instrument

The circle remains the screen's live instrument rather than disappearing after Start:

- Current pace appears inside the upper-left portion of the circle.
- BPM appears inside the upper-right portion.
- Elapsed time remains centered and visually dominant inside the circle.
- The grayscale running photograph remains visible but subdued enough to preserve metric legibility.
- The radial visualizer continues around the circle.

The accepted color experiment for the neutral State Glass screen is named **Electrolyte**. It uses a
soft fixed progression of blue-gray, eucalyptus/forest, and pale luminous yellow **only on the radial
visualizer bars**. Bar length and opacity may respond to music; the colors remain in fixed positions.
Neutral Gray remains an alternate tweak. Electrolyte is successful evidence, not a locked brand
palette, and it must not automatically recolor the background, energy tiles, metrics, or controls.

### Distance

- Distance sits outside and directly below the circle.
- Its numeric value is the largest active-run metric, using heavy, slightly condensed numerals and
  natural decimal spacing such as `0.03`.
- `MI` is substantially smaller and lighter.
- This hierarchy is intentional: elapsed time, pace, and BPM live inside the instrument; accumulated
  distance owns the open space beneath it.

### Now-playing/current-run strip

A compact strip near the bottom shows the music currently playing during this run. It borrows the
visual DNA of a collapsed Log History record so the live moment can later feel related to the saved
record, but it is **not a historical Log row**.

- Use `NOW PLAYING`, not `Morning run`.
- Keep the animated sound bars, song title, artist and album context.
- Keep the weather glyph and current run date. They identify the live run context and should remain
  secondary to the music information.
- The music-free active-state treatment is not decided yet; do not invent one from this prototype.

### Pause, resume, and stop

- While the run is active, one central Pause button is shown.
- Pressing Pause places the run in a paused state and reveals two actions: **Stop** and **Play**.
- **Play** resumes the run and returns to the active Pause control. In this context Play means resume
  run tracking; it is not a music-playback command.
- **Stop** ends the run.
- A separate permanent Stop button is not shown during active tracking.
- Whether Stop requires a confirmation step has not been decided. Do not record or build a
  confirmation as settled behavior until that decision is made.
- Whether pausing run tracking also pauses music playback has not been decided. Keep run-session
  control and music-provider control conceptually separate.

---

## What this screen demonstrates about the visual direction

This prototype is evidence that the larger RunState vision can work without every screen using the
same composition:

- A luminous near-white field can feel atmospheric when fog, depth, photography, and motion give it
  substance.
- Real running photography can live inside an interface instrument instead of always acting as a
  full-screen background.
- Soft surroundings can hold forceful running data, large athletic numerals, and confident dark
  controls without weakening the sport.
- Selective color can carry life and music through one moving element while the rest of the screen
  stays disciplined.
- Start and active tracking can share one visual identity through a continuous transformation.
- Borrowing Log History material for the live now-playing strip can create continuity without
  confusing live and historical content when the labels and hierarchy are clear.

These are transferable findings, not a rule that every future screen must use a circular timer,
grayscale photography, State Glass tiles, or the Electrolyte blend.

---

## Production dependencies and later checks

The prototype exposes requirements; it does not satisfy them:

| Screen need | Current status |
| --- | --- |
| Live elapsed time, distance, and pace | Mobile tracking/GPS/session work |
| BPM | Sensor or health-platform integration; not currently collected |
| Music-reactive radial visualizer | Real playback/audio signal and permission strategy needed |
| Live song, artist, and album metadata | Music-provider integration needed |
| Pause / Play-resume / Stop session states | Mobile run-session state machine and durable local persistence needed |
| Shoe and route choices | Visual entry points exist; progressive-input behavior is still open |
| Current weather in the live strip | Current console weather is logged-run context, not a live mobile contract |

Later refinement must also test contrast over photography, dynamic type, screen-reader labels and
state announcements, tap targets, reduced motion, battery use, outdoor visibility, music-free state,
and recovery if the app is interrupted while a run is active or paused.
