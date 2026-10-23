---
name: research-app-landscape
description: "Competitive landscape + research-backed product considerations for RunState (June 2026): differentiation, RPE science, privacy, and scope-creep watch."
metadata:
  type: reference
---

# Running-app landscape & considerations

## October 22, 2026 — Energy foundation review

**Status: research and proposed validation plan; no new Energy implementation.** Manley wants a
credible foundation that can grow into demonstrably useful runner learning. AfroTech requires an
honest working foundation and a clear direction, not proof of a finished coaching system. He is
open to changing earlier decisions if their measurement or inference does not hold up. This review
reopens those decisions; proposed questions, thresholds and experiments below are not approved code.

### What is actually built

Review baseline: clean `claude/run-voice` at `8f5e28c`, after the Start-panel checkpoint `34b6b14`
and Claude's opening-wordmark changes `c9bb68a` / `8f5e28c`. The latter add purple R, orange n and
olive a; existing photos, motion and entry timing stay intact. No newer Energy engine or concrete
trial protocol was found in the reviewed tracked code/docs.

- Android saves optional pre-run Low / Moderate / High, post-run Spent / Feeling Good / Powered Up,
  explicit Not sure, separate Smooth / Working / Heavy / Empty Tank Effort, and a local note.
- Post Energy selects the immediate reflection branch. Subsequent feedback edits do not rewrite
  the frozen reflection. Effort currently affects neither the native insight nor the AI reflection.
- `RunnerStateInsight.kt` computes one local History count card: latest 20 eligible genuine GPS
  records, including unanswered ones in that window; at least three paired answers in one starting
  category; newest represented qualifying category wins. It reports exact finish counts and answer
  coverage, including mixed outcomes. Samples, confirmed test provenance and ambiguous duplicate
  identities are excluded. No AI request is involved.
- Three is a display threshold, not a statistical confidence threshold. The card has no calendar
  age limit, comparable-run matching, effect estimate, causal analysis or fitness-improvement test.
  It correctly does not subtract the differently worded pre/post scales.
- The older console has ComparisonService and RunStyleService with route/distance matching,
  medians, effort-related signals and count-based stages. These are implemented heuristics, not
  scientifically established confidence levels, and are not the phone's Energy Insights engine.
  Console numeric post-minus-pre Energy and hidden RPE ranges must be reviewed before any port.
- Existing 18 insight unit cases and four relevant UI cases cover arithmetic, eligibility,
  missing data, grouping and edits. Their existence/historical results are software evidence;
  this review did not rerun them or demonstrate runner benefit.

### The measurement problem to resolve first

Energy, pleasantness and exertion are different constructs. A runner can finish physically tired,
proud and happy, after a manageable run. Low could also be interpreted as sleepy or unmotivated.
Feeling Good may describe mood rather than energy, and Empty Tank overlaps the energy question.
The current categories can describe what was selected, but they do not establish an interchangeable
numeric pre/post energy scale. More AI analysis cannot recover a meaning the question never captured.

Proposed first wording study, retaining the simple interaction:

1. Test the same anchored question before and after: **How much physical energy do you feel you
   have right now?** Low / Moderate / High is a candidate, not a validated scale or final wording.
2. Test Effort as **Thinking about the whole run, how hard did it feel?** Compare current labels
   with Easy / Moderate / Hard / Very hard. Do not silently map either custom set to validated RPE.
3. Use an enjoyment question only in the small pilot to discover whether the energy choices miss
   something important. Do not add a permanent third post-run survey by default.
4. Preserve every old answer under its original wording/version if questions change. Never
   reinterpret historical Feeling Good as a measured Moderate energy answer.

This may justify clearer labels, rather than more choices. Validate meaning before deciding.
Finishing depleted is not automatically a bad result: the intended session matters. Optimizing every
run for Powered Up would favor easy/short sessions and could misrepresent a useful demanding run.

### Research and comparable products

These sources support investigation; none validates RunState's current buttons or proves that its
insights improve running. Product pages demonstrate precedent, not independent efficacy.

| Source | Finding and relevant limit | Consequence for RunState |
| --- | --- | --- |
| [Saw, Main & Gastin, systematic review, 2016](https://bjsm.bmj.com/content/50/5/281) | 56 studies; subjective wellbeing measures responded to training changes more consistently than common objective measures. Many used established multi-item questionnaires. | A runner's report can add information beyond pace/sensors. This does not validate a custom three-choice scale. |
| [Duignan et al., systematic review, 2020](https://pmc.ncbi.nlm.nih.gov/articles/PMC7534939/) | 21 field/court-sport studies; single-item wellbeing associations with load varied widely. The larger-observation studies mostly found trivial-to-moderate relationships. Combining or altering measures does not retain established psychometric properties. | Keep custom questions explicitly experimental; do not convert Energy into a recovery/readiness or training-load score. Team-sport findings do not directly establish recreational-running utility. |
| [Thorenz et al., jogging validation, 2024](https://pubmed.ncbi.nlm.nih.gov/38667113/) | 194 participants, 45-minute moderate jogging; pleasantness and arousal examined separately. Comparator agreement differed substantially (r=.50 versus .16). German scales in a particular setting. | Feeling pleasant, feeling activated and finding a session demanding must remain distinguishable. |
| [Williams et al., prospective study, 2012](https://pubmed.ncbi.nlm.nih.gov/22532005/) | 146 low-active adults in a 12-month activity trial. Affect during a treadmill walk predicted later activity; seated post-cooldown affect was not associated. | Record when an answer was given and what period it describes. Post-run Energy is not a substitute for whole-run enjoyment or a proven predictor of adherence. |
| [Teixeira et al., randomized trial, 2024](https://link.springer.com/article/10.1186/s12966-024-01636-0) | 47 randomized, 46 analyzed; three individualized gym sessions, then eight weeks. Pleasure-oriented intensity guidance yielded 14.35 versus 8.13 gym visits. Small, non-regular-exerciser sample; post-exercise enjoyment did not significantly differ. | Encouraging evidence for adapting exercise around experience, not for logging alone. A useful system eventually needs an action and follow-up loop. |
| [Using Behavioral Analytics to Increase Exercise, 2018](https://pubmed.ncbi.nlm.nih.gov/29429607/) | 79 enrolled, 73 randomized; personal feedback derived from six months of observations. Exercise-day frequency declined less with feedback than in controls (4.0 versus 10.5 percentage points). The intervention did not establish an absolute increase. | Personalized observations can be worth testing, but favorable comparative results must not be advertised as an increase that did not occur. A few runs cannot reproduce this study. |
| [Strava Perceived Exertion](https://support.strava.com/en-us/articles/15401767-perceived-exertion) | Records whole-activity intensity on a 1–10 scale; can inform Relative Effort. | Effort has a distinct job. Friendly Energy labels are not a replacement for this measurement. |
| [TrainingPeaks Training Insights](https://help.trainingpeaks.com/hc/en-us/articles/41077784209165-Athlete-Home-Performance-Insights) | Separately charts RPE and Feeling, with up to 90 days and objective intensity context. | Feeling-plus-effort tracking is not unique by itself. Differentiate through understandable personal evidence and useful follow-through. |
| [Garmin Training Readiness](https://www8.garmin.com/manuals/webhelp/GUID-025D75CF-3445-49E1-8D81-1AA74AB4E00F/EN-GB/GUID-C21BE0C8-A08E-4DA1-B6C6-2E0E2DDDB372.html) | Uses sleep, recovery time, HRV, acute load and recent sleep/stress history. | Do not imply that RunState's check-ins already provide the same information or readiness capability. |

### A foundation with specific, testable jobs

**First job — describe personal experience.** Preserve the existing card as an observation. A
fictional example it can already express: Feeling Good after four of six Low-start runs. A future
bounded slice should also expose the other two finishes, contributing runs and date span, alongside
the existing answer-coverage count. The current majority sentence does not list minority outcomes.
Do not call this an energy boost, advice to run while tired, or evidence of what caused it.

**Next job — one fair comparison.** Candidate question: **Are my similar easy runs feeling less
demanding over time?** Use the same runner, known easy-run purpose, same route or verified comparable
terrain, similar distance/duration/pace, and credible GPS metrics. Show heat/weather differences and
unknown context. Establish comparison rules before looking for a pleasing result. Do not combine
speedwork, long runs and easy runs or silently relax matching until a positive message appears.
There is no universal validated distance/time tolerance; thresholds need sensitivity checks.

A possible report is: **On these comparable loop runs, your recent Effort reports were lower, while
pace was similar.** That is a reported-experience trend, not proof of fitness gain. A cooler day,
different pacing, recovery or a changed interpretation of the labels could explain it. Purpose,
route matching and this analytical comparison are not currently implemented in native Insights.

**Later job — test a practical personal choice.** For example, a runner compares two familiar,
similarly flat routes during their existing easy sessions. Choose the outcome in advance, balance
or randomize route order where practical, record weather/time/effort, and review contradictory runs
too. Six matched pairs could be an exploratory budget, not a universal proof threshold. Changes
such as training volume, fatigue or weather trends can still confound the comparison. Do not add
runs just to fill the study. [CENT guidance](https://www.bmj.com/content/350/bmj.h1793) describes the
methodological importance of planned outcomes, repeated crossover, time effects and carryover;
it is not evidence that this route experiment will succeed.

For all three jobs, calculate evidence in inspectable code. AI may explain a supported observation;
it should not decide that a relationship exists merely because it can tell a convincing story.
The creative music reflection keeps its own purpose and accepted expressive range. This proposal
does not add restrictions to its writing. Praise and analytical truth have different jobs.

### Small technical gaps that matter before deeper interpretation

- Make Effort equally accessible after Not sure. Currently only a real Energy tile schedules the
  inline follow-up; History editing is the alternative. This selectively misses reports from
  runners whose feelings fit the Energy choices poorly.
- Save assessment time, question/version and original-versus-later-edit provenance. Current storage
  lacks a dedicated answer-time/version trail; reflection timestamps are not assessment timestamps.
  Preserve unknowns and old answers, with an explicit migration plan before implementing.
- Keep the current post-choice-before-reflection ordering. For research, distinguish immediate
  post-run answers from later edits after encouragement/music; those stimuli might change feelings.
  This is a measurement-design inference, not a demonstrated RunState effect.
- Separate run/test provenance from whether an AI reflection is marked test data. That coupling
  is currently a conservative exclusion shortcut, not an independent research-quality run flag.
- Current genuine GPS entries have no insight minimum duration. Keep their subjective records,
  but classify accidental/very short sessions separately before comparing training experiences.
  Define this by the question being studied; no arbitrary minimum is scientific validation.
- Show the time span, denominator and unanswered data. Twenty latest runs can span months or years;
  enthusiastic-only logging can distort a pattern even when the arithmetic is correct.
- Never present three/five/eight observations as statistical certainty. Keep exceptions and null
  results in the analytical record, including when the spoken reflection stays supportive.

### Proposed Energy test sequence

The earlier 3–5-runner formative proposal remains useful, but it answers usability rather than
effectiveness. Use three separate tests, with conclusions labeled accordingly:

1. **Meaning and mechanics, first session.** Manley, then 3–5 willing runners, interpret three cases
   without coaching: tired but happy; energetic but frustrated; comfortable easy run versus hard
   planned workout. Ask what each choice means and which they would select. Check Not sure/skip,
   effort entry, later edits and the count card against known labeled fixtures. Success means
   understandable questions and faithful records, not a positive-looking insight. Revise wording
   when people are answering different questions. Do not silently seed personal history.
2. **Repeated-use pilot.** A proposed practical budget is 3–5 runners over 3–4 weeks of their normal
   running, aiming for roughly 8–12 paired records each when their routines permit. These are
   feasibility targets, not powered efficacy thresholds. Track completion/missingness, answer
   timing, interpretation drift, exceptions, and whether a runner can explain a displayed count.
   Record whether it changed a specific decision and what happened on subsequent comparable runs.
   If almost everything is Feeling Good, investigate loss of useful distinction; do not assume a
   successful intervention. If no reliable pattern emerges, report that instead of mining more.
3. **Prospective benefit test, later.** Preselect one primary outcome relevant to the runner, such
   as decision usefulness followed by enjoyment or consistency on future planned sessions. Compare
   logging alone with logging plus evidence-based insights, with equivalent measurement in both.
   Keep music/reflection experiences comparable when attributing benefit to Energy Insights. For
   individual actionable factors, use a planned repeated within-person comparison as above.
   Derive a larger study's sample size from pilot variability and the smallest worthwhile benefit;
   repeated runs are not independent people. Involve a sports-science/research collaborator before
   making efficacy claims. General fitness gains require separate performance evidence.

An insight being liked, a reflection sounding convincing, or a runner going faster once does not
establish effectiveness. The useful evidence chain is: understood report → fair observation →
specific decision → actual follow-through → prospective outcome, including cases where it fails.

### Follow-up — reusable blueprint and the purpose of each input

**Proposal, not implementation approval.** Manley asks for the fastest defensible foundation and
the purpose of every input, including time, weather, trail, hydration and shoes. Recommend adapting
product patterns rather than buying an integration or reproducing a full coaching platform:

- TrainingPeaks: distinguish planned demand, actual reported effort and overall experience.
  RunState can keep its state question specifically about physical Energy; copying broad Feeling
  wording is not required. An anchored perceived-exertion scale is a candidate shortcut for Effort,
  with deliberate instructions/timing; do not invent exact RPE equivalents for the current labels.
- [Strava Matched Activities](https://support.strava.com/en-us/articles/15401955-how-do-i-view-my-matched-activities):
  compare repeated routes. RunState must additionally consider intended demand and actual amount
  of running; a route match alone does not make an easy outing and speed session equivalent.
- [Oura Discovery Hub](https://support.ouraring.com/hc/en-us/articles/30697762898323-Discovery-Hub):
  show associations between recorded context and personal outcomes. Its sensor baseline and
  eligibility rules are specific to Oura, not transferable validation thresholds for RunState.

Purpose of the proposed inputs:

| Input | Useful job | Limit |
| --- | --- | --- |
| Pre Energy | Record the state the runner brought in; distinguish an already-low starting state from a demanding session's reported finish. | Does not itself explain why Energy was low or prescribe whether to run. Test whether it adds information beyond post Energy/Effort. |
| Post Energy | Record the state the runner reports after running; with consistent before/after meanings, describe changes in reported state. | A depleted finish can be appropriate for the intended session. Current mixed labels are not an interchangeable numeric scale. |
| Whole-run Effort | Capture demand that pace/distance alone may miss; compare the same person's similar sessions. | A higher report is not automatically worse. The intended session and circumstances matter. |
| Intended demand | Define what a suitable result means before judging the run. | Optional, never inferred from how fast the runner happened to go. |
| Context | Make comparisons fairer and generate a specific hypothesis worth checking again. | Association does not identify which factor caused the result. |

The first analytical question could be: **Among my intended easy runs on a familiar route, which
felt manageable and what Energy did I report afterward?** Preserve all outcomes. Pre Energy adds
starting context; time/weather/shoes initially annotate the comparison rather than generating
separate automatic claims. This is a proposal beyond the current native count card.

One optional intended-demand choice (for example Keep it easy / Steady effort / Hard effort /
No target) would be a smaller first step than a complete training-plan system. These are wording
candidates, not settled choices. A long-run distance goal is a different dimension; do not claim
this small field measures whether every kind of goal was achieved.

Existing native reuse, verified October22 without reading personal run records:

- Start timestamp and original time zone already support local time-of-day grouping.
- Optional weather already stores temperature, feels-like, humidity, wind, weather code and source
  timing. It is a modeled hourly snapshot near the start, not continuously observed route weather.
- Selected physical shoe pair and mileage association are built. No shoe-versus-outcome analysis.
- GPS paths are stored, but stable named trail identity and same-route matching are not built.
  The Hermann Park artwork is not evidence that a run occurred there.
- Structured hydration and intended-demand inputs are not built.

Prioritize automatic time/weather, reliable route grouping and optional intended demand. Keep shoe
association available at low friction, but prioritize mileage/recordkeeping before claims about
which shoe improves performance. Racing shoes may appear faster because they are selected for fast
sessions. Hydration can wait: a question such as whether someone drank before/during the session
records a behavior, not measured hydration status, and must allow unknown. It needs a defined
question and a useful comparison before adding routine taps.

More fields do not automatically create better evidence: mornings may also be cooler and shorter,
and route/shoe choices may track workout type. Small histories split across many factors become
sparse quickly. Use one predefined question, visible examples/exceptions and prospective follow-up.
The first foundation should establish this repeatable workflow, not rank every possible influence.

### AfroTech boundary and next bounded step

Before the event, aim to demonstrate reliable optional check-ins, the truthful existing count card,
a clear explanation of its evidence, and the proposed direction. Demonstration fixtures must be
clearly labeled and kept separate from personal learning. No fabricated completed pilot, validated
readiness score, causal music/weather claim or finished fitness detector.

Recommended immediate next step: the short meaning/mechanics walkthrough, then agree on one small
implementation slice (clear question anchors, independent Effort access, and assessment provenance
as appropriate). Do not automatically port the console RunStyle rules or implement this entire plan.
The longer repeated-use evaluation can extend beyond AfroTech.

---

## Historical June 26, 2026 landscape

Web research done to surface things to consider before expanding RunState's feature set
(specifically before Phase 5 Step 2, weather). See [[design-weather-context]] for the build,
and [[user-goals]] for the scope-discipline commitment this reinforces.

## Strategic read — differentiate on identity, not features

There is no single "best" running app; runners typically use 2-3 together. The majors own clear lanes:
- Strava = social (segments, kudos, clubs)
- Garmin / Coros = device data depth (VO2max, training load, HRV, Body Battery)
- Runna = adaptive training plans (acquired by Strava in 2025)
- Nike Run Club = free, friendly, guided/voice runs

Implication: **RunState cannot and should not compete on feature count.** Its white space is the
combination none of them center: a tracker built around *how the run felt* + an AI agent with a real
*voice* that makes a run feel meaningful. Judge every new idea by: does it strengthen the
feeling-and-voice core, or just chase parity?

## Correction to the June RPE interpretation — October 22, 2026

The original section incorrectly equated pre/post Energy with rating of perceived exertion and
suggested `energy(1–3) × duration` as a science-backed training load. Those claims are withdrawn.
Energy is a present state; session exertion concerns how demanding the whole activity was. Session
RPE uses a specified exertion scale and collection protocol, not RunState's Energy categories.
The custom four-choice Effort scale is also not automatically a validated RPE instrument.
[Foster et al.](https://pubmed.ncbi.nlm.nih.gov/11708692/) studied session exertion in cycling and
basketball; that does not validate these app labels.

Assessment timing matters, but the earlier blanket claim that ratings always decrease with delay
is not an established universal rule. Specify and record timing rather than assuming all same-day
or immediate answers are equivalent. See the current review above for sources and proposed tests.

## Privacy — the one genuinely new cross-cutting consideration

Across AI-coaching coverage, privacy is the top concern: sending user health/location data to
third-party AI servers without clear consent. For RunState this is concrete, not abstract:
- The agent already sends run data (distance, pace, energy, route) to **Anthropic's API** (third party).
- Weather adds **location**: city/state to **Open-Meteo**, and city may enter the agent prompt.
City-level data is coarse/low-risk, but two external services now receive run data.
Recommended action: a short "Data & Privacy" note in README/docs (what leaves the app, to whom, why).
Cheap, intentional, and a maturity signal employers notice. Tracked in [[design-weather-context]].

> **Correction to the June statement above (July 28, 2026).** The June research note predates the
> as-built behavior and is preserved as a research record, but its privacy claim is **imprecise**
> in two ways. As built:
> - **Open-Meteo, request 1:** the **city name** may be sent to the geocoding endpoint. A missing
>   or blank city produces **no request at all**.
> - **Open-Meteo, request 2:** the **selected candidate's returned coordinates plus the run date**
>   may be sent to the forecast endpoint, and **only if geocoding succeeded**.
> - **The stored state is used locally** to pick which returned candidate matches. It is
>   **never placed in either request**.
> - **Anthropic receives no runner-profile city or state**, and **no Open-Meteo geocoding or
>   forecast coordinates.** So "city may enter the agent prompt" is **not** true of the current
>   build.
> - **The weather path contributes only derived values** to the run message — temperature,
>   apparent temperature, and condition.
> - **Separately, the runner-entered route name is an outbound run field, and a route name may
>   itself identify a location.** That is user-typed free text, not weather-path data, and it is
>   sent as the runner wrote it. This is why "Anthropic receives no location information" would
>   be too strong a claim.
>
> The authoritative as-built disclosure is **`docs/DATA_PRIVACY.md`**, not this file.

## Scope-creep watch — consciously RESIST (mostly already parked)

These are table stakes for the big apps and explicitly NOT RunState's lane as a console learning
project. The roadmap already parks most; naming them keeps them parked:
- GPS / automatic run tracking
- Social feed, segments, kudos, leaderboards
- Device/file integrations (FIT/GPX, Garmin/Apple Health sync)
- Recovery metrics (HRV, sleep, Body Battery)
- Proactive/adaptive coaching nudges ("great day for a PR")

## Sources

- Best running apps 2026 (MAVR): https://www.mavr.app/blog/best-running-apps-2026-complete-guide
- Best AI running coach apps 2026 (Running Genie): https://therunninggenie.com/blog/best-ai-running-coach-apps
- How AI is revolutionizing fitness coaching 2026 (Vora): https://askvora.com/blog/ai-fitness-coaching-2026
- RPE scale for running (Marathon Handbook): https://marathonhandbook.com/rate-of-perceived-exertion/
- RPE & subjective feedback (TrainingPeaks): https://www.trainingpeaks.com/learn/articles/what-are-rpe-and-subjective-feedback/
- Weather impact on running (The Running Week): https://www.therunningweek.com/post/weather-impact-running-performance-complete-guide
- Berlin Marathon environmental factors study (NCBI): https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11482731/
- Open-Meteo Geocoding API: https://open-meteo.com/en/docs/geocoding-api
- Open-Meteo Historical Weather API: https://open-meteo.com/en/docs/historical-weather-api
