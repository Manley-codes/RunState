---
name: run-initiation-register
description: LIVE register for the run-initiation system — Prepare to Detect (armed mode), provisional detection, warm-up discrimination, session orchestration, and the at-home/at-trail preparation flows. Same status vocabulary as the music feature register. Created August 4 2026 so this material has a live home rather than sitting in a parked file.
metadata:
  type: project
---

# Run initiation register — LIVE

**Read this file.** Not archived, not parked.

**What this system is.** Everything that removes friction from *starting* a run — arming the app,
recognizing when the run actually begins, and handing the runner into a prepared session.

**Where it came from.** Manley's own observation from five years of run club: the group takes off
fast, people are wearing gloves or armbands, and nobody wants to be the one standing there operating
a phone while everyone else is already running. The friction is not only pressing start — it is also
finding and starting the music.

**Its dependency, stated once.** Every entry here needs a mobile client with motion sensors,
location, and background execution. **None of it is buildable in the console era.** That is a
sequencing fact, not a judgment on the ideas — provisional detection is the best-grounded problem in
the project and also the hardest thing in the app.

---

## Status vocabulary

Same as `music_feature_register.md`: **PRINCIPLE**, **DEFAULT**, **EXPERIMENT**, **NEEDS REVIEW**,
**REJECTED**.

---

## Prepare to Detect (armed mode) — EXPERIMENT, leading

An intentional, temporary armed state. The runner signals *"I plan to run soon; be ready."* That
intent is what makes automatic detection realistic — it improves privacy, constrains battery use,
and creates a clear moment to load the expected session.

**The runner experience**

1. **Prepare once** — at home, in a parking lot, or before a run-club warm-up.
2. **Confirm only what matters** — RunState fills in known context, offers easy corrections, and
   asks at most one human-only question.
3. **Put the phone away** — visible but quiet armed state, with the session resources ready.
4. **Warm up normally** — running-like motion may become a candidate, but nothing is saved or
   announced.
5. **Begin the real run** — sustained forward running raises confidence; RunState restores the
   buffered beginning, starts the session, and gives a brief audio cue.
6. **Correct when necessary** — manual start, cancel, edit, and delete stay simple and available.

**MVP behavior**

- Explicit opt-in each time. No constant background surveillance by default.
- **A time limit.** The armed window expires automatically — 30, 60, or 90 minutes as candidate
  defaults, runner-configurable. Without it, someone arms at home, changes plans, and triggers a run
  hours later.
- Clear armed indicator on the lock screen or a system surface where the platform allows it.
- Outdoor forward-running scope first. Indoor and treadmill are unsupported or manual-only until
  proven.
- Quiet candidate detection with a short rolling buffer.
- Retroactive start from the estimated takeoff point once confidence is high.
- Audio or haptic confirmation **only after confirmation**, never for every candidate.
- Manual start remains the guaranteed fallback.

**What the mode may prepare** *(options behind it, not promises)*: location permissions and tracking
configuration, expected route or recognized start area, likely shoes and run type, audio cue
settings and headphone state, a chosen or generated music session, cached weather and offline-safe
session metadata, and the smallest useful pre-run message.

### Three ways to start — EXPERIMENT *(added August 4, 2026)*

The runner chooses how the run begins:

1. **Manual** — start the armed system by hand.
2. **Voice phrase** — a set phrase through the headphones starts it.
3. **Detection** — let the system recognize the run's start.

This matters because it keeps control with the runner without abandoning the vision. On a day when
detection misbehaves, the feature still works. It also serves runners who simply do not want the app
watching for them.

### Preparation assisted by the runner model — EXPERIMENT *(added August 4, 2026)*

Once enough evidence exists, choosing a trail at home lets the app prepare against what it already
knows about the runner there.

Manley's example: *he usually struggles at this trail, so surface a note to stretch well and hydrate
— especially if the app also detects it is hot.* It could show or speak his last performance on that
route.

**Escalating alertness.** Armed at home is a low-attention state. Arriving at the recognized trail
can move the app into a more alert mode. On detecting the run: *activity started, playlist
initiated.*

**Guardrails that already apply:** calibrated language rather than performance prediction — *"heat
and hills have made this route more demanding for you"*, never *"this will be a difficult run."* And
the safety rule: never encourage a runner to push through pain, illness, or heat risk.

**Depends on:** trail choice as a feature (future), the runner model having route-level evidence, and
weather at the start location.

---

## Provisional detection — EXPERIMENT

The system should not answer *run or not run* in a single instant. It holds a hypothesis.

| State | System behavior | Runner experience |
| --- | --- | --- |
| **Armed** | Monitor a limited signal set, keep resources ready | Phone can stay away; cancellable |
| **Watching** | Buffer a candidate and collect evidence | Usually silent; no official run yet |
| **Confirmed** | Restore estimated start, begin session services | Brief "run started" cue; tracking active |
| **Dismissed** | Discard the candidate, return to armed | Warm-up or brief activity disappears |
| **Timed out** | Stop monitoring at the end of the armed window | Clear notice; no session created |

**Why buffering matters.** Waiting for confidence would normally lose the first part of the run. A
rolling buffer preserves recent location and motion samples long enough to estimate when takeoff
began. Buffer length is a platform-testing question; the strategy depends on the behavior, not a
fixed number of seconds.

**Confirmation comes from a cluster of signals, not one threshold:** sustained forward displacement
from the start area, speed and cadence consistent with running for that person, a path that expands
rather than staying a tight cluster, continuity long enough to exclude a drill, plausible direction
and distance patterns, and the absence of an immediate stop or return.

**Dismissal when evidence breaks:** movement stops quickly, displacement stays small, the path
oscillates in a tight radius, or the pattern repeatedly resembles a drill.

---

## Warm-up versus actual run — EXPERIMENT

GPS alone is not enough. Warm-up movements can resemble running in cadence and acceleration, while
trees, buildings, or group movement make location noisy.

| Signal | Warm-up tendency | Actual-run tendency | Caution |
| --- | --- | --- | --- |
| Forward displacement | Low or returns to origin | Sustained and expanding | GPS drift fakes small movement |
| Path shape | Clustered or reversing | Continuous progression | Tracks and switchbacks loop |
| Cadence | High but brief | Stable across time | Varies by runner |
| Speed | Bursting or near zero average | Sustained above baseline | Hills and crowds lower speed |
| Duration | Short drill sets | Longer continuous motion | Short runs and intervals are edge cases |
| Start-area radius | Remains nearby | Leaves the area | Small loops stay nearby |
| Stop pattern | Frequent stops | Continues after takeoff | Traffic lights interrupt |

**Rule of thumb:** enter Watching when motion resembles running. Confirm only when the runner also
travels forward in a sustained, plausible path. Dismiss when motion is brief, local, inconsistent,
or followed by a return to warm-up behavior.

**Edge cases that must be handled:** run-club takeoff with crowding and a slow first minute; a
traffic light shortly after starting *(Manley flagged this one as needing real review)*; intervals
and strides near one area; track running where loops stay geographically close; treadmill with no
forward displacement (manual-only until a separate sensor strategy exists); urban canyon or dense
trees lowering confidence; and a short accidental jog that should vanish rather than become a saved
run.

**The evaluation dataset is the whole game.** Not a large generic running archive — a deliberately
labeled collection of the confusing moments: dynamic warm-ups, high knees, jumping jacks, walking,
short jogs, run-club takeoffs, slow starts, traffic-light stops, loops, hills, and noisy GPS. Each
trial records the true takeoff time and whether the system should confirm, dismiss, or stay
uncertain.

---

## At-home versus at-trail preparation — EXPERIMENT

Different moments, different jobs.

**At home — decision support.** There is time to consider route, timing, shoes, hydration, weather,
or intent. The app supports a decision without becoming a setup form: confirm the plan, assemble
known context, ask one meaningful question, offer at most one or two useful actions, then arm.

**At the trail — get out of the way.** Phone friction matters more. Recognize the start context,
confirm only what is uncertain, ask briefly if at all, prepare silently, arm and wait. The runner
can put the phone away before drills, conversation, or the group's takeoff.

### Group meetup beacons / specific start locations — EXPERIMENT *(added August 9, 2026)*

RunState may eventually place photo-backed location beacons on a dedicated route, map, or meetup
surface so a runner can find the **specific place** where a friend, running group, race team, or
event is gathering. The problem is smaller and more concrete than navigation: the runner may have
reached the correct park or race but still cannot find the group's tent, trail entrance, parking
area, landmark, or actual setup.

**Initial shape:** a static meetup point shared by an organizer, friend, event invite, or run link,
optionally paired with a recognizable photo and a short location detail. Circular map beacons came
from an earlier map-based Start-screen exploration and remain a compatible future visual seed, not
part of the current Start/Active Run screen. They identify practical destinations, not profile
activity, social status, or automatic people tracking.

**Later extension:** an explicitly enabled live friend or group location can use the same beacon
language, but it is a separate, heavier capability. It requires accounts or invite identity,
real-time infrastructure, battery testing, clear visibility and expiration controls, and the
standing privacy/security/legal pass.

**Guardrails:** never broadcast a runner's location automatically; share only with invited people
or a defined group; make who can see the location and for how long explicit; no feed, follower
mechanics, leaderboard, or friend comparison. The value is arriving and starting with less
friction, not turning RunState into a social network or run-club platform.

**Adaptive question policy** — the progressive-input ladder in practice:

| Information | Default | When to ask |
| --- | --- | --- |
| Trail or route | Detect or suggest from location and history | Low confidence, or the runner plans elsewhere |
| Weather | Fetch automatically | Only if unavailable or conditions changed unexpectedly |
| Shoes | Suggest from recent pattern | Multiple plausible pairs, or frequent corrections |
| Feeling | Ask optionally | Useful before advice; never inferred as fact |
| Run intent | Recall or suggest | When intent changes the session or the music |
| Music | Recall last successful mode | Discovery, experiment, or explicit change |

---

## Session orchestration — DEFAULT

On confirmation: restore the estimated start time and buffered segment, begin the timer and route
recording, persist a local session record immediately so a crash cannot erase the start, play a
restrained audio or haptic confirmation, begin the selected music if ready and permitted, attach
prepared context, and switch from detection sampling to run-tracking sampling.

**Critical-path rule — PRINCIPLE.** Timer, route recording, session persistence, and manual control
are the core. Music, weather, AI messages, and visual flourishes are enhancements that **must fail
independently.**

| Failure | Required behavior |
| --- | --- |
| Music unavailable | Start tracking anyway; explain afterward without blocking the run |
| Network lost | Use cached context, save locally, sync later |
| Weather fetch fails | Omit the observation; never invent or delay |
| Audio output unavailable | Use haptic or system confirmation; keep tracking |
| Location confidence poor | Keep observing or fall back to manual; never claim a precise restored path without evidence |
| App suspended | Respect platform limits; state clearly what can be relied on |

---

## Open questions

1. The simplest name for the armed mode — Prepare to Detect, Ready for Run, Prepare to Run, or
   something else.
2. Whether to target one mobile platform first to reduce background-behavior uncertainty.
3. Which outdoor starts define initial scope: solo, run-club, trail, track, or a subset.
4. How long the armed window stays active, and whether the runner chooses or accepts a default.
5. Traffic-light stops shortly after takeoff — flagged by Manley as needing real review.
6. Whether Prepare to Detect is a RunState feature or the product's new center. Manley's position,
   August 2026: **let it play out naturally rather than deciding now.** Noted because the source
   strategy's final recommendation reads as the second, which would displace *log a run → track how
   you felt → learn something meaningful* as the core.
