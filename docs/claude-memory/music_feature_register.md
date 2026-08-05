---
name: music-feature-register
description: LIVE register of named music decisions — hard principles, defaults, experiments, needs-review items, and rejections, each with an execution path. Created August 4 2026 to stop music ideas from being buried in parked files, and to restore the July 9 DJ-session decisions that were lost when that session ran out of credits at its documentation step.
metadata:
  type: project
---

# Music feature register — LIVE

**Read this file.** It is not archived and not parked. Approved by Manley on August 4, 2026.

**Why this file exists.** Good music ideas kept disappearing. Two causes, both structural:

1. `music_ingredients.md` is labeled *reference-only holding file, parked* in MEMORY.md, so
   everything in it is invisible by design — including the strongest music thinking in the project.
2. **The July 9, 2026 DJ session ran out of usage credits at the exact documentation commit point.**
   It had reached a full controlled status — one hard principle, several experiments, two rejections
   — and only two thin bullets ever landed in the repo. Everything below marked *(recovered July 9)*
   was approved then and lost.

This register is the standing list of named music decisions. Nothing here is archived. Every entry
carries a status and, where it exists, an execution path.

---

## Status vocabulary

| Status | Meaning |
| --- | --- |
| **PRINCIPLE** | A hard rule. Other decisions bend to it. |
| **DEFAULT** | The normal behavior. Departures need a stated reason. |
| **EXPERIMENT** | Promising, approved as a direction, not yet proven. Needs runner evidence. |
| **NEEDS REVIEW** | Manley is deciding whether and how to use it. Do not build against it. |
| **REJECTED** | Decided against, with the reason recorded so it isn't re-proposed. |

---

## 1. The data reality

**Spotify Audio Features, Recommendations, and Related Artists are dead for new apps** (deprecated
November 2024). Skip events are not exposed. **Playback control, Add to Queue, and Get the User's
Queue are available**; queue writes need Premium.

**The resolution already made — meaning-sync, not beat-sync.** RunState syncs music to the runner's
state, intent, and history rather than to steps per minute. Tempo/BPM matching is **out of current
scope by stage, not by verdict.** Nothing may depend on it; the design must not foreclose it.

**A song's character can be learned from the runner's own history instead of bought from an API.**
That's what the trust ledger does, and it's more distinctive than the capability it replaces.

**Provider stance:** treat Spotify and others as **playback and history pipes, not the music brain.**

**Policy flag:** Spotify's terms restrict feeding their data into AI and building profiles from their
content. Formal review before any deep integration.

---

## 2. Hard principles

### Protect Momentum — PRINCIPLE *(recovered July 9)*

> **Never replace a stimulus that appears to be carrying the runner with a speculative
> intervention. When uncertain, preserve momentum.**

The underlying reasoning, in Manley's framing: the rule was never *don't cut songs* for its own
sake — it is **don't destroy momentum while trying to improve it.** A runner at their limit may be
holding on partly because the current song is carrying them. Swapping a known-working song for a
supposedly better one is a bad gamble at exactly the wrong moment.

**Intervention order:**

1. **Protect** — leave the current song alone.
2. **Reinforce** — optionally add a brief mental cue without replacing the song.
3. **Prepare** — queue the best next track for the natural transition.
4. **Replace** — only on the runner's own action.

**The cutting rule, confirmed August 2026: RunState never cuts a song.** The July 9 session proposed
softening this to a strong-but-breakable default with pre-authorized exceptions. **That softening is
declined.** The runner may skip whenever they like — that is the runner acting, not RunState
interrupting. RunState's automatic actions are **LEAVE** and **QUEUE_NEXT**, and nothing else.

**Corollary:** RunState should not infer *this song stopped working* from a pace drop. The runner
could be climbing, easing off deliberately, or struggling while loving the track.

### Record now, interpret later — PRINCIPLE

Live behavior may make cautious choices; deeper meaning is validated after the run. Applies to skip
patterns, push requests, and every association the system might notice.

### Sense broadly, act cautiously — PRINCIPLE

Use many signals. Don't overreact and don't pretend certainty.

---

## 3. Named features

### I Need a Push — EXPERIMENT, leading *(recovered July 9)*

**Approved boundary:** *"I Need a Push" is a runner support request, not a playlist mode.* It
protects current momentum, gives one brief cue, and prepares a trusted next track only when
appropriate.

**The insight that unifies it:** the need is *push*; the channel is voice, music, both, or neither.
That's better than building a DJ control and a mantra feature separately.

| Situation | Response |
| --- | --- |
| Current song appears to be carrying the runner | Voice cue only; protect the song |
| Runner has skipped repeatedly | Voice cue plus a recommended next track |
| No music playing | Personal anchor cue only |
| Runner explicitly asks for something different | An unfamiliar candidate becomes eligible |

Example line: *"Keep your feet moving. I've got the next one."* Acknowledges the struggle without
fake praise, and the runner stops searching manually.

**Never turn the moment into a conversation or a settings exercise.**

**Trigger contract:**

| Type | Trigger | Allowed response |
| --- | --- | --- |
| **Action** | Runner presses "I Need a Push" | One anchor cue, then protect or queue |
| **Action, later** | Runner pre-authorized a planned moment (e.g. final mile) | Deliver without asking again |
| **Offer** | Repeated manual skipping | Prepare a recommendation, surface "Push track ready" |
| **Context only** | Pace decline, cadence, heart rate, hill, run phase | Adjust ranking; **never** trigger alone |

**V1 trigger: the runner presses the button. That is all.** No guessing whether they're tired,
bored, climbing, or in trouble.

**Skip-spiral threshold (hypothesis, not truth):** three runner-initiated skips within two minutes
during an active run. Excludes playback failures, provider transitions, natural track endings, and
pre-run browsing. On threshold, RunState prepares one recommendation and surfaces *"Push track
ready"* — it does not take over. If ignored, silent for at least ten minutes. No nagging.

**Rescue-track confidence:** first rescue should be **Proven for you** — a familiar track with
repeated fit in similar moments. A **Strong candidate** (unfamiliar, shares tone or language with
proven tracks) becomes appropriate only when the runner asks for something different, their usual
push tracks have already failed, they opted into discovery during push moments, or the candidate was
auditioned earlier in a lower-stakes stretch. Finding a new rescue track could be magical; it just
can't be presented as proven before the evidence exists.

**Data worth recording on each use:** when the push was requested, run phase, whether music was
playing, recent manual skips, cue delivered, track retained or queued, whether a suggestion was
accepted or skipped, and the post-run energy and effort already collected.

Later, RunState may say *"your push requests have been clustering in the final third of longer
runs."* It must never say *"you always lose motivation near the end."* The first describes evidence;
the second judges the runner.

### Personal Anchor Language — EXPERIMENT *(recovered July 9)*

Manley's own self-talk, turned into a system. *"Just keep moving"* is an immediate action anchor;
*"it won't last forever"* shrinks the painful moment and restores perspective.

**Two sources:**

- **Song language** — themes in trusted songs that resonate during difficult moments.
- **Runner language** — the words this runner actually uses to stay in motion.

This is more personal than a generic AI coach shouting encouragement, and it expands the existing
push-vocabulary concept from lyric themes alone.

**Cue families to learn between:** direct (*"keep your feet moving"*), temporal (*"this part
passes"*), evidence-based (*"you have held this stretch before"*), calm (*"settle your breathing,
keep the rhythm"*).

**Research support, stated honestly:** a small randomized study found rehearsed motivational
self-talk improved cycling time-to-exhaustion from 637 to 750 seconds and reduced perceived effort
at the same elapsed point. A second found personalized second-person phrasing slightly outperformed
first-person. **Both studied phrases the participant learned and used themselves — not an
unexpected AI voice.** That makes runner-authored phrases the strongest starting point.

**Safety limit:** encouragement can help someone override discomfort while physiological strain
keeps rising. Runner-invoked or explicitly authorized only — never triggered by a pace drop. No
hostile or shame-based coaching, ever. Motivation must never become pressure to ignore pain or
warning symptoms.

**Also covers the no-music runner.** Need and channel are separate. Someone running without music
can receive an anchor before the run — *"today's anchor: keep moving, this part passes"* — or invoke
one during it. Call it *a cue to try*, never a claim that it works for them, until repeated feedback
says so.

**Distinct from the scrapped friend-voice feature.** That was social messages from supporters. This
is a brief, runner-controlled performance-support channel.

### Settle / Hold / Build — NEEDS REVIEW

> **The only music question a runner should ever face: what should the music do for you today?**
>
> - **Settle** — calm, escape, recovery
> - **Hold** — smooth rhythm, flow, focus
> - **Build** — controlled lift toward harder moments

The agent proposes one from pre-run energy and history — *"sounds like a settle morning?"* — and the
runner confirms with one tap.

**Where it came from.** The DJ document listed six run modes: Escape, Smooth Pace, Focus, Push,
Finish Strong, Recovery. Those aren't six equivalent modes — they mix four separate axes: **run
type** (recovery, easy, long, tempo), **music intention** (escape, flow, focus, push), **finish
strategy** (hold steady or build), and **intervention preference** (gentle, balanced, active).
Settle/Hold/Build is one question standing in for all four, broad enough to learn from without
becoming a dashboard.

**Status is NEEDS REVIEW, at Manley's direction** — he is deciding how, or whether, to use it. The
July 9 session independently filed it as *promising, not settled*, with open questions that remain
unanswered: does Settle mean recovery, emotional calm, or warming up? Does Hold mean cadence, mood,
pace, or focus? Is Build a whole-run intention or only the closing arc? It also may not survive
contact with Deep Run Crate, which is by definition a longer conversation.

**Recommended shape (August 2026): the axis is necessary, the prompt is optional.**

The playlist agent has to know what kind of run this is — that's the difference between *clearing*
music and shuffling it. Whether the runner ever sees the words Settle, Hold, and Build is a separate
question, and three labeled buttons would sit closer to the dashboard this project avoids than to
the restraint it wants. This is consistent with the locked *internal gates, not user-facing bins*
rule.

**Manley's own described behavior is the strongest evidence for keeping it internal.** Asked whether
runners think this way before a run:

> *"I rarely ever have my music prepared and it's never really an actual question to myself. It's a
> vibe I hope to quickly fall into — one that matches my mood, or a freshness for my current state,
> and a lot of times something I can make my own during my run."*

That is not a decision being made. It is a **search being conducted**, in the moment, under time
pressure, hoping to land fast. Asking a runner to name the vibe converts a feeling into homework.

**Which reframes the job.** The point of this axis is not to capture intent up front — it is to
**shorten the search.** The app inferring *he's in a settle mood* is precisely what stops the
hunting. That makes Settle/Hold/Build infrastructure for the skip-spiral problem rather than a
front-door question.

**Proposed placement if it survives review:**

- **At home** — may be visible. There is time, and choosing what the music should do reads as
  planning rather than paperwork.
- **At the trail** — inferred silently from pre-run energy and history. Never asked.
- **Either way** — a correction path must exist. If the app infers, it will sometimes be wrong, and
  the runner needs a way to say so. A correction path is not the same as a prompt.

**Do not build against this until Manley decides.**

### Music pacing and rest — DEFAULT, consolidated August 2026

**Preserve the emotional lifespan of good music.** Pace discovery, save some tracks for the right
run, and create something to look forward to. This is why the app can decline to play a great song.

Until now this existed as three disconnected fragments across two files: *music pacing / anticipation
pacing* as a one-line ingredient, *overuse* as a tracked signal and a relationship-web connection,
and *deep cuts* as one of five internal selection gates. They are one idea.

**Fresh and unfamiliar are different axes.** This distinction matters because conflating them
creates a conflict that does not exist.

| | Meaning | Placement |
| --- | --- | --- |
| **Unfamiliar** | Unproven for this runner | Mid-run only, per risk-managed placement |
| **Rested** | Proven, but not played recently | Safe anywhere, including the opener |

Manley's example: *"it could be an old Michael Jackson song, but I haven't listened to it in months,
so currently it feels fresh — and I'm in a happy relaxed mood like his music, so it lands
strongly."*

A rested track is **maximally proven and feels new at the same time.** That makes it the ideal
opener under both rules rather than an exception to either. Risk-managed placement is untouched; it
was never about rested songs.

**Two selection inputs follow:** how long since this track was played, and whether its character
fits the runner's current state. A deep cut that fits the mood is the strongest opener the system
can offer.

**Needs:** structured song history with timestamps. Same dependency as the trust ledger.

### Songs a runner makes their own — NEW, unrecorded until now

From the same description: *"a lot of times something I can make my own during my run."*

This is not in any existing document and it inverts the reply system. The AI replies use a song to
describe a run. This describes the opposite — **the run gives the song meaning.** A track becomes
*yours* because of where you were and what you did while it played.

Two connections worth noting:

- **The trust ledger gains a second dimension.** Not only *this song works for me in this context*,
  but *this song became mine on that run.* Those are different kinds of value and the second is more
  emotional.
- **The Soundtrack Log may already be the surface for it.** A newest-first record of runs and the
  music that played is, read this way, a record of which songs became yours and when. That is a
  stronger emotional premise than *here is what was playing*, and it costs nothing extra to frame it
  that way.

Unresolved as a feature. Recorded so it isn't lost.

### RunStyle Sound — EXPERIMENT *(named August 4, 2026)*

**A subset of RunStyle, not a sibling feature.** "Which artists show up around your strongest runs"
is a RunStyle output — the music facet of the same pattern engine. Naming it as a subset states what
is architecturally true and avoids adding another top-level feature name to a product that already
has several.

It also fills a slot Manley had already sketched. When thinking about how to present a RunStyle
summary without cards, he floated badges organized around questions — mood, marathon, best
performance — and separately asked *what music helps run performance.* This is that section.

**Two forms, doing different jobs, both approved:**

| Form | Job | Nature |
| --- | --- | --- |
| **Personal view** | *What music works for me?* | A utility, inside the app |
| **Shareable card** | *Who am I as a music runner?* | An artifact, exported |

**Personal view.** Artists and songs that recur around the runner's strongest runs, plus the
inverse — what appears around mood improvement, easy days, or hard finishes. This is a presentation
of relationship-web output, not new intelligence.

**Shareable card.** The runner's picture with their top runs and the songs beside them. Manley's
position, stated August 4: he accepts that some comparison will happen and does not want it to
become the app's core identity — an optional artifact is not a core mechanic. **See the social
position refinement in `creative_direction_ui.md`.**

**Card, not page.** Cards are exported; pages are browsed. The locked exclusion is on browsable
feeds and social-graph mechanics, and an artifact sidesteps that entirely. RunState already has a
share card in its design system — this belongs to that family and reuses its export behavior rather
than introducing a new browsing surface.

**Distribution, not vanity.** People share things that say something about them. A card showing the
three songs that carried a runner's best runs is the kind of thing that gets posted, which makes it
a distribution channel rather than a decoration.

**Wording constraint.** *"Artists who promote your best performances"* is a causal claim and
collides with the overclaim guard. The associative form carries the same information safely:
**artists that keep showing up around your strongest runs.**

**Confidence constraint.** Three runs is a small sample — three coincidences can look like a
pattern. Reuse the thresholds already calibrated in `RunStyleService`: a descriptive minimum of
three supporting runs, comparative minimums of five with and five without, an 80% support rate, and
a 30% lead over runs lacking the context. Without them this becomes astrology.

**Naming notes.** Singular — **RunStyle Sound** — reads better than the plural, which drifts toward
sounding like a tagline. **The feature name should not appear on the exported card.** Inside the app
it is coherent; on a card seen by someone who does not use RunState it is jargon. The artifact needs
the face, the runs, the songs, and the RunState mark, and nothing else.

**Relationship to cross-user suggestions.** The aggregate side — *runners who respond to the artists
you respond to also run well to this* — is already recorded as **on-vision, waiting only on the
platform**, alongside browsable community playlists with run context. The two forms coexist: the
aggregate is a recommendation engine, the card is identity. Neither requires the other.

**Dependency worth knowing:** this lives inside whatever RunStyle summary eventually exists, and
that summary's shape is still open (see `adr_001_runstyle_surfacing.md` — recompute versus persisted
insight log). RunStyle Sound inherits that open question.

*Needs:* structured song history, enough run history to clear the confidence thresholds, and a
decided RunStyle summary surface.

### Playlist grading — taste versus run impact — EXPERIMENT

**The reframe that makes it work:** do not ask the runner to grade *how well the AI understood their
taste.* Ask **how the playlist affected the run.** Those sound similar and are completely different
questions. Grading the AI's understanding makes the AI the subject; grading the run keeps the runner
the subject — the same principle behind rejecting audible restraint.

**Two dimensions, kept separate:**

| Dimension | Question | Example answers |
| --- | --- | --- |
| **Taste** | Did you like the songs? | Loved them / they were okay / didn't like them. Plus passive signals: saves, replays, skips, artist and genre preference |
| **Run impact** | Did it fit or affect this run? | Energized me / matched my pace / steadied me / calmed me / distracted me / no effect / wrong timing |

The separation exists because a runner can love a song and admit *"it's terrible for my tempo
runs"* — or the opposite, *"I don't normally listen to this, but somehow it pushed me during the
last two miles."* A single rating destroys both cases.

What it teaches the system is deeper than favorite artists: the difference between music you enjoy
in everyday life, music you enjoy while running, music that supports performance, and music that
helps you relax or recover.

**Ask selectively, never after every run.** Grading every playlist becomes homework fast. Ask when
the playlist was experimental, when the run was unusually good or bad, when the agent tried a new
genre, when many songs were skipped, or when the runner asked for discovery.

**Show that feedback changes future sessions**, or the request feels like a chore.

**One correction to the source phrasing.** *"Did this playlist help, hurt, or have no effect on your
performance?"* invites the runner to make a causal claim the app then holds as a stored assertion —
which collides with the overclaim guard. Safer: ask what the run **felt** like, and let the system
correlate that with actual pace itself. Feelings are the runner's to report; causation is not.

**Why this matters more than its size suggests.** Everything else in the music layer is the app
talking. This is the app **listening**. The trust ledger cannot earn roles without it, and role and
trust are what make any of this personal rather than generic. It is the first feature that lets the
system learn whether its own recommendations are working.

*Needs:* playlist generation to exist, plus structured song history to attach feedback to.

### Trust ledger + track roles — EXPERIMENT

Songs earn roles over runs. A track that carried strong finishes gets quietly cleared for the final
mile; new tracks audition mid-run first. **History tags the library, not the user.**

**Two dimensions, not one** *(recovered July 9)*:

- **Role** — what this song might do: steady, settle, lift, close.
- **Trust** — how confident RunState is that it works **for this runner in this context**.

A trusted mellow song does not become a push song. Trust is contextual: a proven closer for Build
runs is not automatically proven for recovery or escape. Manual tagging may be necessary early,
because available streaming metadata is thin.

**Needs:** structured per-run song history. The console stores a free-text music note, not
structured tracks. **This is the narrowest gap between here and a real music system.**

### Music confidence / risk-managed placement — DEFAULT

New or unfamiliar songs go **mid-run only**. Trusted, proven tracks open and close. Never make the
first song or the final-push song experimental.

Root insight: new music before a run feels risky, and a bad song at mile three can kill the rhythm.
The app doesn't reduce risk by avoiding new music — it reduces risk by controlling *where* it lands.

### The picky gatekeeper — PRINCIPLE (personality)

RunState does not recommend music. It **clears music for the run.** It filters hard, and restraint
is the premium feel.

**Internal gates, not user-facing bins.** Selection categories — own-history-proven, same-DNA new,
deep cuts, last-mile-intensity, low-risk-new — stay agent-internal. Six labeled bins is a dashboard;
the brand is restraint. The runner sees a restrained result: *"mostly steady, final push
protected."* They never manage Mellow → Steady → Build → Push → Sprint → Cooldown by hand.

### Deep Run Crate — CANDIDATE *(working name)*

A **runner-invoked** deeper playlist mode, on top of the basic agent.

- **Two tiers.** Quick Run Mix — fast, low-friction, mostly trusted. Deep Run Crate — slower,
  intentional analysis for meaningful runs: long runs, comeback runs, race prep, low-energy starts,
  early mornings, music-central days.
- **Runner-chosen timing.** An hour ahead or **the night before.** Anticipation is part of the
  product.
- **Earned, not default.** Appears only once adequate data exists. Unlocking it is a milestone, like
  RunStyle at run eleven. Before that: honest progress framing, never faked depth.
- **Output is a summary:** *"Cleared for today's run: 42 minutes. Mostly trusted. 4 new tracks.
  Final push protected."*

**Not the same as armed mode.** Armed mode removes friction from starting and carries an obligation
to be ready. Deep Run Crate is optional depth the runner chooses. They can meet — an armed session
can use a crate prepared the night before — but neither requires the other.

Naming collides with a possible community "running crates" concept.

### Phase-aware playlist structure — DEFAULT

Opener to start strong, steady-rhythm middle, and a boost track **held in reserve** for a mid-run
slump. Full phase detection needs time-aligned telemetry.

**Sports-science backing:** music matched to cadence and motivational music measurably lower
perceived exertion. Phases responding to different music is evidence-based.

**Two slices:** the pre-run brain (assembles before the run, no GPS needed) and live adaptation
(mobile only — detecting a mid-run slowdown needs live GPS pace). Same feature, two phases.

### Lyric-trigger push vocabulary — EXPERIMENT, gated

Certain words and lyrical themes trigger a runner to push harder. The agent learns a personal push
vocabulary — defiance, winning, proving-people-wrong, calm focus — from themes recurring around
strong efforts.

**The delayed-observation form** *(recovered July 9)*: never claim causation in the moment. Not
*"the language of this song sparked something in you"* but *"defiant language showed up around
yesterday's hardest stretch — want me to find a few tracks with that tone?"* That states an
association, delays analysis until the runner isn't exhausted, asks permission before researching,
and turns yesterday's run into preparation for the next one. Surface it as an in-app **Next Run
Prep** card; notifications opt-in only.

**Legal gate:** lyrics are licensed content. Musixmatch is paid; scraping Genius violates their
terms. Belongs to the privacy/security/legal milestone.

### Skip detection as signal — DEFAULT

One skip wakes the system up; repeated skips plus run context create stronger confidence. Feeds the
I Need a Push offer trigger. **Spotify does not expose skip events** — this needs in-app playback or
another provider.

### Relationship web — ARCHITECTURE

Song-to-run relationships stay **internal** rather than becoming visible bins like Push Songs,
Rescue Songs, or Power Songs. Internally the web may connect music to run phase, pace, cadence,
skips, weather, effort cost, pre- and post-run energy, route, familiarity, overuse, comparable runs,
strong finishes, hard miles, and lyric patterns. Always pattern language, never causal certainty.

### Music Agent Workspace — CANDIDATE

An optional deeper place where the runner works with the agent to shape run music. Not a default
fitness playlist screen.

---

## 4. The DJ layer

**North star:** *the runner should not only experience the music — the music system should
experience the runner back.*

The spark: music is currently one-sided. The person experiences the music; the music does not
experience the person.

**The intelligence loop:** runner state + music state + run intent + personal response profile = the
next music decision.

**Two forms of intelligence:** general music intelligence (song traits) and personal runner
intelligence (how *this* runner responds). The same mellow song may relax one runner, slow another
too much, and help a third lock into pace.

**Not one-size-fits-all.** Running music does not always mean louder, faster, harder. If the app
sees a slower pace and answers with aggressive music, it can ruin the run. Intent comes before
intensity.

**Response levels:** leave the song alone; queue the next song differently; shift the upcoming
energy arc. *(The DJ document's "offer a gentle prompt" and "switch immediately" are superseded —
mid-run prompts are on the avoid list and RunState never cuts a song.)*

**Culture anchor:** at big races, organizers place DJs along the course, and **mile 20 is where
marathoners hit the wall.** A DJ stationed there isn't entertainment — that's where people break,
and the DJ reads the crowd and plays what lifts them.

> **A DJ who reads a crowd of one.** A race DJ reads a thousand strangers in real time. RunState's
> has only ever played for you.

Candidate north-star line for the live feature.

---

## 5. Competitive reality — the category is not empty

Researched July 9, 2026. Recorded because the earlier assumption of an untouched category was wrong.

| Product | What it already does |
| --- | --- |
| **Soul Pacer** (2026) | Power Songs, Rescue Me, final-push songs, cross-session variety, pace-based learning, AI coach personalities, post-workout soundtrack cards. Closest overlap. Very early — one App Store rating. |
| **RockMyRun** | Actively matches tempo to steps or heart rate |
| **Qadence** (Feb 2026) | Adaptive and fixed-cadence Apple Music modes |
| **RunVibes** | Markets pace, heart rate, run phase, training intent, "zero interruption," adaptive Spotify sequencing. Coming-soon beta — marketing, not proof. |
| **Weav Run** (shut down 2023) | Licensed stem-based song adaptation to cadence, with coaching that responded to live pace, distance, location, and workout progress. Demonstrates how expensive rights-heavy remixing becomes. |

**Conclusion: a strong but emerging category, not an untouched one.** RunState cannot rely on power
songs, queue-next, or protected finish as the unique idea.

**What remains unclaimed:** combining the runner's subjective starting state, intention, live run
experience, post-run energy and effort, and personal relationship with music into **one continuing
history.** No established product clearly owns that whole relationship.

**But the differentiation comes from execution, voice, restraint, and trust — not from possessing
the idea.**

**Cadence is an input, not the identity.** RunState should not become the BPM app, but discarding a
useful signal because competitors also use it would be an overcorrection.

---

## 6. Execution track

The point of this section is that nothing here quietly becomes "later" without being looked at.

**Buildable in the console today**

- **Structured song history.** Replace or supplement the free-text music note with structured
  track data. Unlocks the trust ledger, split context, the relationship web, and every pattern
  feature. Smallest change with the largest downstream effect.
- **The music decision simulator.** Both source documents independently recommended it: *print the
  music decision instead of changing songs.* Proves whether the decision feels intelligent without
  GPS, OAuth, mobile lifecycle, or playback control. Already in the build-order rule.

**Blocked only on a decision, not on capability**

- **Settle / Hold / Build** — needs no new data. Blocked on Manley's review.

**Blocked on playlist generation existing**

Music confidence, picky gatekeeper, Deep Run Crate, phase-aware structure.

**Blocked on mobile and playback observation**

I Need a Push, personal anchor language, skip detection, live DJ adaptation, lyric-trigger
vocabulary, split music context.

---

## 7. Rejected — with reasons, so they aren't re-proposed

| Rejected | Reason |
| --- | --- |
| **Audible restraint** | *"I left the music alone — you didn't need me"* makes the system the hero and congratulates itself for doing nothing. Restraint should be felt, not announced. Scrapped July 9. |
| **Friend / supporter voice messages** | A social message feature, judged heavy and gimmicky. Scrapped July 7. Distinct from the runner-controlled support voice above. |
| **Automatic mid-song interruption** | Including as an opt-in aggressive mode. RunState never cuts a song. |
| **Mid-run prompts or dialogs** | Revisit only if a watch UI ever exists. |
| **Visible algorithm bins** | Push Songs, Rescue Songs, Power Songs as user-facing categories. Internal only. |
| **Tempo/BPM matching as the identity** | Out of scope by stage, not by verdict. Cadence stays available as a supporting input. |
| **Spotify with pace data / Soul Pacer with a cleaner UI / a playlist generator with a run tracker attached** | The stated anti-goals. |

**Avoid exposing too many names too early.** Deep Run Crate, Push Songs, Rescue, Power Songs, Run
Soundtracks, DJ Mode, Playlist Lab may all survive internally, but too many visible labels make the
app feel crowded.

---

## 8. Open questions

1. **Settle / Hold / Build** — how it's used, or whether it's used at all. Manley reviewing. The
   recommended shape is axis-internal with no prompt; see that entry.
2. **Songs a runner makes their own** — whether this becomes a feature, a trust-ledger dimension, or
   just the framing of the Soundtrack Log.
2. Does the trust ledger need manual tagging at the start, given thin streaming metadata?
3. What counts as structured song history — title and artist only, or more?
4. Deep Run Crate's name collides with the possible community "running crates" concept.
5. How does I Need a Push sit alongside the state-aware reflection and run-comparison systems? Left
   deliberately open on July 9.
