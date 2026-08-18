---
name: idea-organization-analysis
description: August 4 2026 organization pass over every loose idea across the three ChatGPT strategy docs, the Claude Design work, and the repo's parked files. Collapses ~35 ideas into four systems plus a shared music-context service; finds that the runner model is the substrate three systems consume and the only one buildable today. Grades and places every idea (build now / next / later / hold / reject).
metadata:
  type: project
---

# RunState — idea organization and assessment

**Date:** August 4, 2026
**Reviewed:** the three ChatGPT strategy documents and both diagrams, the Claude Design Soundtrack
Log and Share Card work, the full brainstorm conversation, and the parked-idea records already in
the repo (`parked_feature_ideas.md`, `music_ingredients.md`, `parked_music_recommendation.md`,
`creative_direction_ui.md`).
**Purpose:** find where roughly thirty-five loose ideas belong. Not a RunStyle review.

---

## 1. The reframe

These are not thirty-five features. They are **four systems, one shared service, and a small number
of genuine orphans.** Grouping them by what they actually are — rather than by which document they
arrived in — collapses most of the confusion.

| System | What it does | Ideas inside it |
| --- | --- | --- |
| **Runner model** | Learns the runner and expresses that learning | Environmental intelligence, RunStyle engine, progressive-input ladder, readiness/difficulty language, learning ledger, at-home and at-trail preparation, adaptive question policy |
| **Run initiation** | Removes the friction of starting | Prepare to Detect, playlist generation begins on activation, provisional detection, warm-up discrimination, retroactive start, session orchestration, "run detected" cue |
| **Music intelligence** | Connects music to running | Reply generation (built), split music context, playlist generation, taste-vs-run-impact feedback, cross-run frequency, song snippet |
| **Expression** | Gives the learning a face | Soundtrack Log, post-run reply card, READ FROM chips, adaptive voice, Top Run Highlights, post-run artifact |
| **Shared music-context service** | Infrastructure under three of the four | Track identity, playback timeline, audio features, permission and failure states |

**Orphans** — real ideas that don't belong to any system: edit/delete a run, body feedback, small
context fields, community layer, text support messages.

---

## 2. The finding that matters most

**Three of the four systems consume the runner model. The runner model is the only one you can
build today.**

| System | Needs | Available now? |
| --- | --- | --- |
| Runner model | Run history with context | **Yes — you collect all of it** |
| Run initiation | GPS, motion sensors, background execution | No — mobile phase |
| Music intelligence (beyond replies) | Playback observation, session clock | No — mobile + provider |
| Expression | Something learned worth expressing | Partly — depends on the model |

This isn't four parallel tracks. It's **one foundation with three consumers.**

Adaptive voice needs to know the runner to decide when silence is better. Playlist generation needs
to know the runner. The preparation flows are literally a view onto the runner model. Post-run
expression has nothing to express until something has been learned.

Every input environmental intelligence needs is already in your `runs` table: surface, shoe, company,
route, pre- and post-energy, effort, temperature, apparent temperature, condition, distance,
duration, date. Weather shipped in July. And your candidate-based comparison logic — the 180-day
window, cap of ten, signal-specific evidence pools with confidence — is already a working primitive
of exactly this.

**You are further into the foundation than any of these documents realize, and it is the piece
everything else waits on.**

---

## 3. The highest-leverage infrastructure decision is buried in the lowest-priority document

The Split Music Context doc names it in a single callout: *build one trustworthy music-context
capability that can serve multiple experiences.* That is understated. Track identity plus a
timestamped playback timeline plus permitted audio features serves:

1. Split music context
2. Adaptive voice delivery profiles
3. Song snippet after a spoken reply
4. Playlist generation and its feedback loop
5. Cross-run reference frequency (already deferred in your music plan)
6. The Soundtrack Log's own accuracy
7. AI replies that can reference **when** a track played, not just that it played

Seven features on one capability. It's also the thing that decides whether the Soundtrack Log's
mile stamps — *"Lose Yourself came on with one climb left"* — are real or decorative.

**This deserves to be a named system, not a footnote.** It doesn't get built now, but it should be
designed once, deliberately, rather than assembled piecemeal by whichever feature needs it first.

---

## 4. A tension nobody has named

**The more successful run initiation is, the less surface the app has to speak through.**

Prepare to Detect's whole promise is that the runner puts the phone away. But the preparation flow,
the progressive-input ladder, and environmental intelligence's best moment are all *screens*. If
detection works, nobody looks at them.

Two consequences:

**Audio becomes the primary expression channel of the finished app**, not a small enhancement. That
elevates Adaptive Voice considerably — it stops being "a nice touch on mile splits" and becomes the
main way a phone-in-pocket runner experiences everything the app has learned.

**The pre-run screen has to earn its existence in seconds**, because it's the last moment the runner
is looking. That's a real constraint on the State Scan design and worth knowing before you draw it.

---

## 5. What kind of evidence exists, and what kind doesn't

**The evidence base here is participant observation, and it's stronger than it looks on paper.**
Five years of running, most of it in a run club. That is sustained exposure to how many different
runners actually behave at the start of a run, what they complain about, what they fiddle with, and
what they never touch. Products built by someone embedded in the user community usually start from
better instincts than a survey produces.

Concretely, this means several of these ideas are grounded rather than speculative:

- **The run-club takeoff** in Prepare to Detect is an observed weekly pattern across many runners —
  the group moves fast, people are wearing gloves or armbands, nobody wants to be the one standing
  there operating a phone. Not a hypothetical.
- **"Why did this run feel good?"** is a question runners ask each other out loud, constantly. That
  environmental intelligence has a real audience is an observation, not an inference.
- **The voice-interruption complaint** came from a runner in that context, which gives it
  surrounding knowledge rather than making it an isolated data point.

**What's genuinely missing is different: nobody has used the app.** No runner other than Manley has
logged a run in RunState, read a reply, or reacted to an insight. That gap is real and worth
tracking — knowing that runners want to understand their runs doesn't tell you whether *this*
implementation lands. But it's a much narrower gap than "no evidence," and it closes the moment
there's something usable to put in front of the club.

The practical consequence: **the run club is an available test population**, which most solo
builders don't have. That's an asset the roadmap should use, not a hole to apologize for.

---

## 6. Free evaluation data, if designed in now

Several features generate labeled training and evaluation data at zero marginal cost — but only if
the logging exists. Retrofitting it later is expensive.

| Interaction | What it labels |
| --- | --- |
| "Remove response" | Reply quality — a real failure label from a real run |
| Correcting an inferred shoe or trail | Runner-model accuracy |
| Dismissing an insight | Insight trust |
| Skips during a run | Music fit (noisy but real) |
| Manual start after a missed detection | Detection recall |
| Choosing to replay a spoken reply | Which replies landed |

You have been hand-grading twelve scenarios at a time. This is the same evidence, from real runs, at
volume. **The design decision is only to log the interaction rather than discard it.**

---

## 7. Idea-by-idea assessment

Graded on: **Problem** (how real and evidenced), **Distinct** (differentiation), **Leverage** (what
else it unlocks), **Build** (feasible now). Scale is 1–10; placement is the actual output.

### Build now — runs on data you already have

| Idea | Problem | Distinct | Leverage | Build | Notes |
| --- | --- | --- | --- | --- | --- |
| **Environmental intelligence** | 9 | 7 | **10** | Yes | Answers *why did this run feel good* — the question that survives deleting the words "Run Style." Every input exists. Comparison logic is a working primitive. **This is the foundation.** |
| **RunStyle as background engine** | 8 | 7 | 9 | **Built** | Already works this way. Silence by default, speaks when there's something. Not a new build — a decision not to bury it under a card layer. |
| **Edit / delete a run** | 7 | 1 | 3 | Yes | Zero distinctiveness, but it's the named hole in the core journey and you've already written INSERT and SELECT. Smallest item on the list. |
| **Insight persistence (learning ledger)** | 6 | 6 | **9** | Yes | Currently insights are computed, printed, discarded. Persisting them enables the summary, correction, staleness handling, and evaluation data. Cheap now, awkward later. |

### Next — needs screens, not new capability

| Idea | Problem | Distinct | Leverage | Build | Notes |
| --- | --- | --- | --- | --- | --- |
| **Progressive-input ladder** | 8 | 8 | 7 | Partial | *Ask less over time* is the clearest value proposition in any of these documents. It's a State Scan design principle before it's a feature. |
| **Soundtrack Log** | 6 | **9** | 8 | Partial | Strongest product *frame* in the whole set — turns disposable replies into an accumulating record and gives a reason to open the app on a rest day. A thin version could run on today's data (one track per run, no mile stamps). |
| **At-home vs at-trail flows** | 7 | 6 | 6 | Partial | Correct observation that these are different jobs. Becomes concrete once State Scan exists. |
| **READ FROM chips** | 7 | **9** | 7 | Partial | Underrated. Exposes what the reply drew on, which makes fabrication visible to the user. Given that fabricated telemetry is your most persistent failure, this is trust architecture, not decoration. |

### Later — genuinely blocked on mobile

| Idea | Problem | Distinct | Leverage | Build | Notes |
| --- | --- | --- | --- | --- | --- |
| **Prepare to Detect** | **10** | **8** | 8 | No | Observed weekly across a whole run club for years, not just personally felt — the best-grounded problem in the set. Auto-detection isn't novel; *armed intent + provisional buffering + retroactive start* is. |
| **Provisional detection** | 9 | 8 | 7 | No | The technical heart. Also the hardest thing in the entire app — harder than the replies, harder than Spring Boot. Strava and Garmin still get this wrong. |
| **Shared music-context service** | 5 | 4 | **10** | No | Low intrinsic interest, enormous leverage. Design once, deliberately. |
| **Adaptive voice announcements** | **9** | **10** | 8 | No | The only externally evidenced problem. Nobody else is doing music-matched delivery. Section 4 argues this is bigger than "a small feature." |
| **Split music context** | 5 | 8 | 6 | No | Alignment diagram is right that a split is a time window, not a song. Needs the shared service first. |
| **Taste vs run-impact feedback** | 7 | 8 | 7 | No | The insight that a favorite song can be wrong for a tempo run is genuinely sharp and separates this from generic recommendation. |
| **Playlist generation** | 6 | 6 | 5 | No | Crowded space. Your differentiation is the *feedback loop*, not the assembly. |
| **Session orchestration** | 6 | 5 | 5 | No | Follows detection; the critical-path rule (tracking never depends on music) is already correct. |
| **Song snippet after reply** | 5 | 7 | 3 | No | Charming, and licensing is the real wall rather than engineering. Verify before designing around it. |

### Hold — good idea, wrong question still open

| Idea | Why held |
| --- | --- |
| **Performance bar** | The doc is right: you can't design the visual until you decide what it compares against. Six candidate baselines, all with real problems. Leave it as a neutral pace visualization. |
| **Post-run artifact / gallery** | Your own words — you haven't put much thought into it. Also downstream of three systems. The suggested test (insight-led vs photo-led vs music-led) is a good way to find its center later. |
| **RunStyle summary view** | Blocked on one decision: recompute on open, or a view over a persisted insight log. See ADR-001. |
| **Top Run Highlights** | Already parked in the repo. Overlaps heavily with the Soundtrack Log — check whether the Log absorbs it before building both. |

### Reject, or reject as stated

| Idea | Verdict |
| --- | --- |
| **Confident performance prediction** | Reject. *"I predict you'll do well"* is unsupportable and fails the moment it's wrong. The calibrated version — *"these conditions resemble several of your more comfortable runs"* — survives and is better. Both documents already reached this. |
| **Shoe recommendation as expertise** | Reject. *"Wear these on concrete"* requires knowledge you don't have. *"You've rated this route more comfortably in this pair"* is a personal observation and is fine. |
| **Beat-synchronized announcement timing** | Reject for now. Named as a later possibility in the voice doc; it's exactly the kind of pursuit that consumes months for marginal gain. |
| **Multi-card RunStyle destination** | Rejected as the primary expression (ADR-001). The aesthetic direction survives for whatever summary eventually exists. |
| **Re-deriving decided rules** | The strategy docs re-specify confidence ladders, evidence rules, and asking policy that you've already built and shipped. Your annotations caught this. Take the new ideas from those documents; ignore their re-derivations. |

---

## 8. Combinations worth making

**Environmental intelligence + insight persistence + RunStyle summary** are one thing, not three.
The engine detects, the ledger stores, the summary views. Building any one without the others
produces a weaker version of all three.

**Soundtrack Log + Top Run Highlights** — probably the same feature. The Log is a record of runs
with their music; Highlights is a record of notable runs. Check for absorption before building both.

**READ FROM chips + the fabricated-telemetry problem** — the chips are a UI answer to your most
persistent AI failure. If a reply claims something about the middle miles and SPLITS isn't lit, the
claim is visibly unsupported. Worth carrying into the reply work whenever it resumes.

**Adaptive voice + everything the runner model knows** — per section 4, audio is where a
phone-in-pocket runner receives all of it. The voice isn't only for mile splits.

---

## 9. Reusable assets from work already done

Things built for one purpose that serve another:

**Candidate-based comparison logic** — built for run comparison, is already a primitive
environmental-intelligence engine. Recency window, cap, signal-specific evidence pools, confidence
levels. Extend rather than rebuild.

**RunStyleService's confidence thresholds** — the EARLY/FORMING/ESTABLISHED windows and the
descriptive/comparative/habit minimums are a working answer to *how much evidence before we speak.*
The strategy docs propose a confidence ladder from scratch; you already have a calibrated one.

**The music reply prompt's truth boundary** — the three-tier rule (concrete facts exact, creative
interpretation allowed, no invented measurable events) transfers directly to environmental messaging
and voice announcements. Same problem, already solved once.

**The evaluation harness pattern** — fixtures, contamination guards, pre-registered thresholds,
graded transcripts. Heavy for prompt work with no users, but it's exactly the right shape for
detection testing later, where the labeled warm-up-versus-takeoff dataset is the whole game.

**The "assert versus observe" writing rule** — learned for music replies, applies to every message
the app will ever speak, including voice announcements and environmental observations.

**Weather integration** — shipped, and it's a required input to environmental intelligence.

---

## 10. Connections to the upcoming UI work

Not a UI plan, but things that should be known before screens get drawn:

- **State Scan carries the progressive-input ladder.** What's asked versus prefilled is the screen's
  central design problem, not a detail.
- **Run History may already be answered by the Soundtrack Log.** Worth checking before designing a
  separate history screen.
- **The pre-run screen has seconds, not minutes** (section 4). Design it for someone who is about to
  put the phone away.
- **Environmental intelligence needs a surfacing moment on a screen.** Currently the engine only
  speaks after a run is saved. Pre-run is the obvious gap and it's a screen decision.
- **Correction has no home.** The moment any inference is visible, the runner needs a way to say
  it's wrong. No screen currently offers that.
- **Nothing drawn should require data that doesn't exist** without being logged as a surfaced
  requirement — the Run Complete mockup already did this twice.

---

## 11. Recommended shape

Not a roadmap — that comes after the UI work is stable. This is the ordering the dependencies imply:

1. **Environmental intelligence in the console**, extending the comparison logic. It's the substrate
   for three systems and it runs on data you already have.
2. **Insight persistence** alongside it, because the ledger is cheap now and awkward to retrofit.
3. **Edit / delete a run** — an afternoon, closes the named hole in the core journey.
4. **State Scan and Run History screens**, informed by the progressive-input ladder and checked
   against what the backend can actually produce.
5. Everything else follows Spring Boot and mobile, with the **shared music-context service designed
   deliberately** rather than assembled by whichever feature reaches it first.

---

## 12. Open questions for you

1. Does the Soundtrack Log absorb Run History, or are they different screens?
2. Recompute or persist for the RunStyle summary (ADR-001)? It gates the ledger decision.
3. Is Prepare to Detect a **RunState feature** or the **product's new center**? The strategy's final
   recommendation reads as the second, which would replace *log a run → track how you felt → learn
   something meaningful* as the core. That's a big move and it should be deliberate.
4. Adaptive voice is the one problem someone else volunteered unprompted. Does that change where it
   sits, or does it stay behind the systems it depends on?
6. When is the run club worth involving? It's a test population most solo builders don't have, and
   the first thing worth putting in front of it may arrive earlier than the finished app.
5. Should the shared music-context service be promoted to a named system in the project docs now,
   even though nothing builds against it for months?
