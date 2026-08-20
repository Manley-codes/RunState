---
name: design-start-run-v7
description: "The Start screen as it actually stands after the August 16-19 2026 passes - map foundation, RunStyle card, energy absorption into the ring, and the collapse into the timer. Prototype only. Nothing here is locked."
metadata:
  type: project
status: CURRENT DESCRIPTION - what is built in the prototype right now. Not a specification, not locked.
---

# Start Run — where this screen actually is

**Read this instead of `design_start_active_run.md`.** That file describes the August 13 version of
this screen, which no longer exists. It is kept as history, not as instructions.

**What this file is:** a plain description of the Start screen prototype as of **August 19, 2026** —
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

### How an accidental start gets undone — still open

There is **no cancel button**, and that's deliberate. Manley's direction on August 16: instead of a
cancel control, detect that a run probably wasn't real from very early stoppage, then either discard
it or ask whether to keep it.

The likely shape is save durably first, then offer to discard — so the save-first rule still holds.
**Manley is still thinking about this.** Nothing is built.

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

## What this screen needs that doesn't exist yet

The prototype exposes requirements. It doesn't satisfy them.

| Needed | Status |
| --- | --- |
| Live elapsed time, distance, pace | Mobile tracking / GPS / session work |
| Real map and route tracking | Mobile / GPS phase |
| Cadence, if it takes the calories slot | Phone sensors — reachable without extra hardware, not built |
| BPM | Sensor or health-platform integration; not collected |
| Music-reactive ring | Real playback signal and a permission strategy |
| Live song, artist, album | Music-provider integration |
| Pause / resume / stop states | A durable local run-session state machine |
| Shoe mileage | Design accepted; exactly-once completed-run mileage not implemented |
| RunStyle card content | Needs a query for *last run at this route/place*; the data exists |
| Early-stoppage detection | Nothing built |

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
