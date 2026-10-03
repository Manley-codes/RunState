---
name: research-music-reflection-generation
description: "Working research notebook for a grounded, music-aware reflection system — source ledger, failure evidence, and provisional system direction"
metadata:
  type: research
  status: active-working-notebook
  updated: 2026-10-03
---

# Music-aware reflection generation research

## Status and purpose

This is a **working research notebook**, not a final production prompt or implementation plan. It
keeps sources, user feedback and limitations together. The October 3 checkpoint contains the approved
first-version creative direction; the delivery contract lives in `design_run_response_system.md`.
The earlier research-first pace below is historical, not the current implementation queue.

Manley's direction as of September 17, 2026:

- Build a repeatable **system** for producing and evaluating reflections instead of hand-writing a
  separate answer for every combination of run, feeling, and song.
- Keep Manley directly involved in music and reflection choices. Research may inform those choices;
  it does not make them automatically.
- Research replies should be organized and relevance-first. Lead with the few findings that matter
  now, distinguish evidence from inference, and leave full source detail behind links unless Manley
  asks to go deeper.

## Resume point — October 3, 2026

**Creative baseline accepted for first implementation; improve it later.** Manley clarified that
wrapping up means moving to a functional generated-reflection feature in Android, not parking the
writer and implementing only stored samples. Continue with Anthropic, updating the older prompt
and integration where needed. Start with controlled run/music inputs; live text generation moves
ahead of GPS and automatic music capture. Do not require another writing batch, research round,
or the historical 36-output evaluation before beginning implementation. This is a product decision
to build a first version, not evidence that general generation quality is already reliable.

- **Ready to retain:** the accepted examples below, factual boundaries, and read-first/listen-second
  ElevenLabs setup. These are a provisional creative reference and possible controlled demo material,
  not a proven automatic writer or a production voice choice.
- **Latest accepted reuse:** Manley said both examples below work. No separate reading/listening
  grades or repeated-use judgments were supplied. Both use fictional facts and assume
  "Run It Up" by Offset & Key Glock (2025) played; no actual playback is established.
  - Morning: 2.7 miles / 29 minutes, finished 6:45 a.m., Low → Spent, no PR or effort rating.
    "2.7 miles before seven, with low energy at the start. You ran it up early. Now you’re feeling
    spent—sometimes this is what putting in the work looks like."
  - Evening: 3.2 miles / 35 minutes, finished 6:50 p.m., Moderate → Feeling Good, no PR.
    "Run it up then! 3.2 miles in 35 minutes, and you finished feeling good—that’s a real quality run."
- **Still unproven:** dependable fresh generation, range across repeated runs, and automatic creative
  screening. Two text-only AI reviewers matched 2/8 and 4/8 of a small set of existing user ratings;
  neither result establishes a reliable replacement for Manley's judgment. Near-verbatim reuse is
  not evidence of creative variety. The examples also do not form all four Energy branches for one
  shared run, so they must not be combined as if their different metrics described the same run.
- **Actual implementation baseline:** Android still uses Room v3 and ends at Saved with fixture
  metrics and Start another run. The Java console already calls Anthropic. Neither the new mobile
  backend nor the generated reflection/History journey exists. The first-version delivery brief
  now lives in `design_run_response_system.md`; storage is one part, not the whole milestone.
- **Scope boundaries:** inputs must remain clearly identified as fixtures and match the saved run.
  Never attach fixed-distance, early-morning or music claims from these examples to an unrelated
  emulator run. GPS, automatic music capture, snippets, ElevenLabs app integration and visual polish
  remain later. No new runner questions. Keep the current read-first/listen-second setup for optional
  manual review; do not add automatic audio generation to the implementation task.

Exact code slices still need bounded approval. No app code, API calls or deployed service resulted
from this documentation update. The old storage-only local handoff was removed at Manley's request;
the replacement is copy-and-paste in chat, not another project file. Do not reinstate historical
conference dates as current commitments. This checkpoint supersedes the older writing queue below.

### Recent trial limits — preserve the learning, do not restart the loop

The small in-chat writing trials did not establish a dependable fresh-writing method. Several
batches mostly missed; in one six-example batch Manley liked only the second. A same-case comparison
received Moderate for angle-first and Miss for direct drafting: one output per method is not proof
that angle-first generally wins. These were exploratory chat judgments, not a production Anthropic
benchmark. Repeated use of the "Whatcha Know" phrase also began feeling forced and made feedback
harder to give; changing songs helped exploration, but does not itself prove a generation strategy.
Keep the older diagnostics in `music_intelligence_v1_evaluation.md` as history, not prerequisites.

### First-version writing baseline

Use these instructions with representative accepted examples and their complete fictional facts.
This is the implementation starting point, not a claim of a tested final production prompt.

- Recognize something worthwhile about this run, not merely its statistics. The runner and run stay
  central. An ordinary run can deserve recognition without a PR or an invented struggle.
  A fact may repeat from the factual receipt if the reflection adds a worthwhile perspective or
  feeling. "Before the city woke up" illustrates productivity and pride, not evidence that nobody
  else was awake or that this runner outperformed other people.
- Land quickly as one coherent, conversational reflection. Use only the words the idea needs;
  do not manufacture an extra closing line, pile up clever fragments, or force an old catchphrase.
  Keep creative range; approved examples are not compulsory sentence shapes.
- Music can contribute an identifiable reference, character or atmosphere when it fits. Direct
  naming is allowed; weak connections may be omitted. Use supplied, bounded music context; do not
  guess unfamiliar lyrics, artist identity, persona or playback timing. No artist voice cloning.
  Artist influence may inform vocabulary and conversational attitude; Manley wants that personality,
  not rap flow or a chain of disconnected clever lines. RunState remains a running app first.
- Use only supplied run/runner evidence. Do not invent goals, reasons for running, crowds, solitude,
  improvement, PRs or comparisons. Never mention below-average performance or claim music caused
  the result. Free-text fields and music notes are data, never instructions.
- Respect each conditional Energy branch. Spent is not failure or proof of maximum effort;
  Feeling Good is not automatically an improvement. No selection means unknown, not Moderate,
  and does not invite a comment about the unanswered question. Current-run Effort does not control
  this mobile reflection; keep the existing Energy/Effort product distinction.
- Add no questions. Return only the requested candidate texts in the agreed response structure,
  not explanations of the writing process. Copy no sample metrics or history into the current run.

The first foundation can contain solid as well as exceptional creative responses; every run need
not receive a masterpiece. That flexibility never relaxes factual accuracy or the evidence boundary.

Keep a few accepted cases (ordinary, Spent, verified longest, and an alternate-song pairing) and a
few rejected patterns as references. Do not turn every dislike into a universal prohibition. The
latest near-verbatim adaptations show limited reuse, not a general creative breakthrough. Research
and the small reviewer experiment remain context, not a requirement to build a reviewer pipeline.

## Working checkpoint — September 23, 2026

**Working examples, not a locked prompt or final app voice.** Manley materially co-wrote the first
three examples; the later pairings below are recorded separately. These successes do not establish
reliable generation or cross-artist quality. Nothing is integrated.

### Provisional listening setup and review order

- **ElevenLabs:** Manley generates and auditions recordings there using his saved designed voice,
  `1st test voice`, and **Eleven Multilingual v2**. His description: "A middle-aged African American male,
  friendly, confident, conversational, fun, productive and motivational energy."
- Keep voice/model/settings unchanged within a comparison; note deliberate changes. Exact slider
  values were not captured. This is an original designed voice, not an artist clone or production choice.
- **Read first, listen second:** Manley first reads and judges how the wording sounds to him. Then he
  hears the **same text** in his ElevenLabs voice and gives a separate reaction. Preserve both, even
  when delivery improves a disliked line; History also needs readable text. Unreported playback
  feedback stays unconfirmed. Listening judgments below are Manley's reports, not assistant auditions.

### Three working examples

All three are **fictional**, with **"Run It Up" — Offset & Key Glock (2025)** assumed observed.
Unreported goals, effort and music causation remain unknown. Moderate → Feeling Good and Low → Spent
each stay at the same internal Energy level; neither establishes an energy change or effort intensity.

**Ordinary afternoon run:** 2.8 miles in 30 minutes, 4:10–4:40 p.m.; Moderate → Feeling Good;
no personal best.

> Run it up then! 2.8 miles in 30 minutes, and you finished feeling good—that’s a real quality run.

Manley supplied the quality-run interpretation and accepted this tightened wording. **Listening:
unconfirmed.** Generic swagger and time-saving endings missed; he wanted meaningful recognition of
the work and finish together. "Quality" is affirmation, not a training grade or a judgment against Spent.

**New longest run:** 4.6 miles in 47 minutes, finishing at 6:55 p.m.; Moderate → Powered Up;
the fictional history establishes a previous longest distance of 4.2 miles.

> 4.6 miles—your longest run yet. You ran it up, and you finished powered up.

Manley's shortened opening, retained as a complete candidate. **Listening for this edit: unconfirmed.**
He called the longer draft close, but rejected "talk your talk tonight" as worn/corny. The opening
already connected achievement, music and Energy; its extra closing weakened the impact.

**Spent morning run:** 3.1 miles in 31 minutes on a Houston park loop, 6:12–6:43 a.m.; Low → Spent;
no effort report or performance comparison.

> Three miles before seven, on a morning you started low on energy. You put up the numbers. Now
> you’re feeling spent—sometimes this what hard work looks like.

Manley's preferred wording, conversational phrasing preserved. **He explicitly liked it in his
ElevenLabs voice:** productivity with encouragement. Earlier reassurance implied disappointment in
reading, though voice helped; "let those miles speak for themselves" missed in both. "Hard work" here
recognizes the whole run, not a rule that Spent proves hard or maximum effort.

### Later checkpoint — two successful pairings and the snippet experiment

**Shared fictional case:** Wednesday, 6:48–7:20 p.m.; 3.0 miles in 32 minutes;
Moderate → Feeling Good. Monday's 2.4 miles and Tuesday's 2.6 make eight miles across three runs
this week. No PR, goal, effort report or long-term habit is established. Each song is assumed heard
in a separate version of this case, not evidence of actual playback or a music-caused change.

**Nappy Roots — "Good Day":**

> Three runs into the week, and you finished tonight feeling good. You don’t have to save all the
> celebration for Friday—there’s a good piece of Wednesday right here.

**Surfaces — "Sunday Best":**

> Eight miles by Wednesday, and you’re finishing tonight feeling good. That’s a pretty good way
> to leave a run—with the work done and something to smile about.

Manley said he would personally enjoy receiving either after a run, preferring the second. These
were liked before adding snippets; they were not repaired through the earlier co-writing process.
This is feedback on these examples, not a claim that the same words suit every run.

**Listening experiment:** Manley played the ElevenLabs reflection, then manually started a chosen
song passage in Spotify's Windows browser player. He reported that both pairings sounded good.
For "Sunday Best," he deliberately cued just before the singing about feeling good, rather than
starting at the beginning, and said the connection was clear. The "Good Day" passage and exact
start times, gaps and durations were not captured. These are his listening reports, not assistant
audio judgments or a controlled comparison.

**Useful finding:** passage choice and entrance timing deserve separate attention from the wording.
Manual switching made the handoff rough. Manley is now confident the idea can work well once the
connection is smooth; transfer to other pairings, repeat use and automatic selection remain untested.

**Parked, not rejected:** smooth playback, audio sourcing and provider integration. No combined
recording or automated handoff was built. A feasibility check found that Spotify's
[Developer Policy, III.7](https://developer.spotify.com/policy) restricts developer-system segues
between Spotify content and other audio; production permission is unresolved. This is separate from
the user's manual listening experiment and does not establish that every possible delivery route
is unavailable. No purchases, downloads of songs or Android changes were made.

**Historical September direction (superseded by October 3):** return to developing the reflections. Retain these examples and the positive
snippet feedback without making playback polish a prerequisite for the next writing test.

### Additional Log History references — later user feedback

Exact sample text already lives in the [Run Complete / Log History prototype](../../prototypes/run-complete-log-history/run-complete-log-history.dc.html)
(`LOG` records). Link to it rather than duplicating all five replies. These are mockup references,
not verified records of actual runs. Feedback below is Manley's report; no assistant audio judgment
or separate numerical grade is implied.

| Song | Feedback worth carrying forward |
| --- | --- |
| Larry June — "6am in Sausalito" | Strongest reference: liked completely, wanted no wording changes, and the song pairing connected very well. Preserve the creative wording as a reference, not reusable factual claims about other runs. |
| Nas — "The World Is Yours" | Mostly keep; remove "nobody else on the bridge." Especially liked the final line and its connection into the chosen song passage. "Empty city" is also not established as literal fact by this preference. |
| Kanye West — "Runaway" | Good, below the Larry June example for him; the snippet brought it to life through atmosphere and motivational delivery more than exact semantic matching. |
| J. Cole — "Power Trip" | Liked reading and ElevenLabs delivery, including natural direct song naming. The weaker snippet connection did not invalidate the reflection itself. |
| GloRilla — "TGIF" | Liked the vibe and snippet pairing while noting technical adjustments were needed. This does not approve inferring run intent from cadence or from the song. |

### Lessons to carry forward, not mandatory templates

- **Recognition over recap:** ordinary runs deserve meaning, not facts followed by generic swagger.
- **Land quickly, as a whole:** tighten for impact and rhythm, not a fixed length or cleverness in every clause.
- **Endings must contribute:** avoid worn catchphrases and disconnected slogans. Artist character serves the run.
- **Respect Energy:** Spent is not disappointment; Feeling Good does not prove improvement or music causation.

### Historical writing-session workflow — not the current implementation queue

Retain this as an optional method when creative exploration resumes, not a required sequence before
the first build. Related examples may be batched; explain the question and stopping point first.

1. **Choose one question and scenario.** Establish facts and unknowns; label fiction. No new required runner questions.
2. **Draft one or two responses.** Get first reactions before explaining the intended effect or revising.
3. **Read, then listen in ElevenLabs.** Keep reactions separate. Hit / Close / Miss is optional; never invent a rating.
4. **Revise the specific weakness.** Preserve facts; distinguish wording from delivery changes. Case preferences are not universal rules.
5. **Checkpoint, then try another run.** Keep representative examples and reasons, distinguishing first attempts from co-edits. Do not require Manley to write every outcome.

**Historical proposed step (superseded by the October 3 resume point):** try one fresh run case with a researched song, judging the reflection first
on the page and then in the same provisional ElevenLabs voice. Test whether recognition carries to
a different run without requiring another long co-editing cycle. Snippet timing remains parked.
This is not authorization for code, training or paid API integration.

**Keep this notebook lean:** explain scope before saving; update this checkpoint only for meaningful
learning, not every edit. No transcript, duplicate plans or automatic promotion of preferences to rules.

## Reading priority

The labels below matter. Peer-reviewed research, company engineering reports, product reviews, and
craft articles can all help, but they do not carry the same evidentiary weight.

### Quick route through the research

These are **uses of the research**, not extra tasks or an approved multi-stage AI architecture:

- **Meaning, not just creative description:** [sports storytelling](https://www.sportscasterlife.com/tell-better-stories-stats/)
  and [HPI's computer-tailored reflections study](https://doi.org/10.1093/heapro/dat069). Authentic
  context can make a number matter; support choice, acknowledge real accomplishment, and convey
  understanding. Do not invent personal backstory or add questions to reproduce a study protocol.
- **Human taste guiding generation:** [Spotify's narrative-generation research](https://research.atspotify.com/2024/12/contextualized-recommendations-through-personalized-narratives-using-llms)
  and [persona/style evaluation](https://futureagi.com/blog/evaluating-llm-personas-style-2026/).
  Preserve representative examples and reasons; judge truth separately from voice. Stable values
  should coexist with expressive variety. Embedding similarity is not proof of meaning or quality;
  vendor scores, tools and fine-tuning are not requirements for this first build.
- **Trust checks, not the whole creative ambition:** the [hallucination survey](https://doi.org/10.1145/3571730)
  and the [Cycling Weekly](https://www.cyclingweekly.com/news/strava-says-its-new-ai-feature-is-not-a-novelty-but-i-think-its-pointless)
  / [T3](https://www.t3.com/active/strava-athlete-intelligence-i-tried) reviews below. Avoid unsupported
  comparisons, invented significance and empty praise. Historical reviews are not a current audit
  of Strava or a definition of RunState's identity.
- **Delivery as a separate contributor:** [Popular Science's Spotify DJ launch report](https://www.popsci.com/technology/spotify-ai-dj/)
  helps distinguish material selection, writing and speech. Our read/listen/snippet feedback also
  keeps those effects separate; production audio and smooth snippet playback remain later.

Manley's selected excerpts and highlights live locally in
`C:\Users\Owner\OneDrive\Documents\MusicReflectionResearch`. He asked that all selected material be
considered, with extra attention to highlights. `Evaluating LLM Personas, Style.docx` and `HPI.docx`
mainly point to the full articles rather than exhaustive extracts. This is a local research pointer,
not a repository dependency or instruction to reread everything before implementation.

### Priority 1 — foundations for the system

1. **[Contextualized Recommendations Through Personalized Narratives using
   LLMs](https://research.atspotify.com/2024/12/contextualized-recommendations-through-personalized-narratives-using-llms)**
   — Spotify research/engineering article, December 2024.
   - **Why it matters:** the closest operational model in this set. Spotify reports that ordinary
     zero-shot and few-shot prompting was not enough. Music editors supplied golden examples and
     continuing feedback about attribution, tone, and factual errors; the technical teams added
     prompt work, instruction tuning, adversarial tests, curated data, and formal evaluation.
   - **RunState use:** start with Manley-approved and rejected examples plus separate truth, voice,
     and cultural-fit checks. Fine-tuning is a later possibility, not the starting requirement.
   - **Limit:** this is a company-authored report, not a peer-reviewed controlled study, and clicks
     or listening behavior do not prove that a runner felt seen.

2. **[Motivational interviewing in a web-based physical activity intervention: questions and
   reflections](https://doi.org/10.1093/heapro/dat069)** — peer-reviewed study in *Health
   Promotion International*; [Oxford publisher
   page](https://academic.oup.com/heapro/article/30/3/803/620985).
   - **Why it matters:** it directly studies computer-tailored physical-activity reflections. The
     randomized comparison found value in combining open-ended expression with structured answers;
     its definition of a skillful reflection is especially relevant because the reflection adds
     grounded meaning or emphasis rather than merely repeating the person's words.
   - **RunState use:** treat autonomy, competence and relatedness as design lenses: respect the
     runner's choice, recognize something real, and show understanding rather than generic warmth.
     Use the existing evidence and optional Energy inputs; this is not approval for more questions,
     a runner-note feature, or current-run Effort to control the immediate mobile reflection.
   - **Limit:** this is pre-LLM research, the motivation findings were mixed, and it does not prove
     that RunState-style creative reflections improve long-term running behavior.

3. **[Survey of Hallucination in Natural Language
   Generation](https://doi.org/10.1145/3571730)** — peer-reviewed survey in *ACM Computing
   Surveys*, 2023.
   - **Why it matters:** it supplies the strongest truth vocabulary in this list. An intrinsic
     hallucination contradicts the supplied evidence; an extrinsic hallucination adds content that
     the evidence does not support. The survey includes data-to-text generation, RunState's closest
     technical category.
   - **RunState use:** truth should be a separate pass/fail gate, not blended into a single
     creativity score. Each factual or causal clause should trace to approved run, runner, history,
     or music evidence.
   - **Limit:** it is a broad survey rather than a ready-made RunState evaluation method, and much
     of the reviewed work predates today's production LLMs.

### Priority 2 — evaluation method and shipped failure evidence

4. **[Evaluating LLM Personas, Style, and Persona Drift Across
   Turns](https://futureagi.com/blog/evaluating-llm-personas-style-2026/)** — Future AGI vendor
   engineering guide, 2026.
   - **Why it matters:** the most immediately actionable evaluation article here. It recommends a
     bounded voice description, 10–20 matched in-voice/out-of-voice examples, a small hard-rule set,
     separate per-response scoring, and calibration against human raters.
   - **RunState use:** Manley's ratings remain the authority. The useful adaptation is not mainly
     multi-turn chat drift; it is drift across a collection of reflections into generic AI wording,
     repeated openings, fake artist color, or one formula.
     Protect truthful, respectful values without treating playful versus understated expression as
     unwanted drift. Its judge/embedding mechanisms are possible later diagnostics, not proven gates.
   - **Limit:** it promotes the vendor's own tooling and is not peer-reviewed. Its numeric targets
     are examples, not standards RunState should inherit.

5. **[Strava says its new AI feature is 'not a novelty' — but I think it's
   pointless](https://www.cyclingweekly.com/news/strava-says-its-new-ai-feature-is-not-a-novelty-but-i-think-its-pointless)**
   — first-person product review in *Cycling Weekly*, November 2024.
   - **Why it matters:** a compact failure-case inventory from a shipped product: repeating visible
     data, comparing without knowing the activity's purpose, over-reading titles, reversing what a
     user wrote, making unsupported performance claims, and producing impressive-sounding filler.
   - **RunState use:** turn these examples into adversarial fixtures and rejection checks.
   - **Limit:** this is one journalist's review with colleague anecdotes, not evidence of failure
     rates across all Strava users.

6. **[I've spent four months with Strava's AI-powered Athlete Intelligence
   feature](https://www.t3.com/active/strava-athlete-intelligence-i-tried)** — long-term product
   review in *T3*, September 2024.
   - **Why it matters:** it identifies the positive side of the same problem. The reviewer found the
     response more useful when it reacted to the runner's own description, while the lack of goals
     and training-plan context kept other summaries upbeat but vague.
   - **RunState use:** more verified context creates more possible angles, but a user's note must be
     treated as first-person context rather than mined recklessly for claims.
   - **Limit:** this is still one review, not a controlled study.

### Priority 3 — architecture and writing craft

7. **[How Spotify's AI DJ works](https://www.popsci.com/technology/spotify-ai-dj/)** — *Popular
   Science* launch reporting, February 2023.
   - **Why it matters:** it describes three separable layers: Spotify personalization chooses the
     material, generative AI scripts commentary alongside human writers, and Sonantic supplies the
     voice. A writers' room and cultural experts set the subject matter and phrasing before AI
     scales and tailors it.
   - **RunState use:** keep evidence/angle selection, reflection writing, and eventual spoken
     delivery as distinct responsibilities. AI scales editorial judgment; it does not replace it.
   - **Limit:** it is secondary reporting about a launch-era beta, not a current technical spec.

8. **[How to Tell Better Stories with
   Stats](https://www.sportscasterlife.com/tell-better-stories-stats/)** — practitioner craft article
   from Sportscaster Life.
   - **Why it matters:** numbers are the foundation, while timing, context, and significance create
     the story. That closely matches the difference between restating 1.52 miles and finding what
     that run meant.
   - **RunState use:** authentic personal context can make a statistic meaningful, including a
     runner-reported musical discovery or association. More measurements alone are not the answer.
   - **Limit:** its illustrative stories include aspirations, family and practice history. Do not
     infer such backstory from statistics; this is craft guidance, not evidence of an app benefit.

9. **[Praise vs Recognition: Why Employees Need More Than "Great
   Job"](https://www.fringe.us/news/praise-vs-recognition)** — workplace-recognition vendor article.
   - **Why it matters:** its useful distinction is between generic praise and specific recognition:
     naming what was observed and why it mattered makes someone feel genuinely seen.
   - **RunState use:** this is a drafting lens for moving beyond "Good job." Identity statements
     should come from repeated history, not be declared from one run.
   - **Limit:** the article provides little research support for its broader psychology claims.

10. **[Data Storytelling: How Spotify Wrapped Hooks the
    World](https://storysoft.io/data-storytelling-spotify-wrapped/)** — Storysoft marketing analysis.
    - **Why it matters:** brevity, creative abstraction, identity framing, and visual presentation
      can make personal data emotionally legible and shareable.
    - **RunState use:** learn from the compression and presentation, not just the copy.
    - **Limit:** the article praises language that is both specific and vague enough to fit many
      people. That Barnum-like shortcut is precisely what RunState should avoid if the goal is to
      make one runner feel accurately seen.

### Additional relevant research — retained for later, not new feature commitments

These preserve promising directions from the broader discussion without reopening discovery or
changing the text-first implementation. Sources checked October 3; the creativity paper's original
PDF was inaccessible in this check, so its official repository abstract is linked instead.

11. **[Music-evoked autobiographical memories in everyday life](https://journals.sagepub.com/doi/10.1177/0305735619888803)**
    — Jakubowski and Ghosh, peer-reviewed diary study. Across 31 people and seven days, music-cued
    memories were often spontaneous, vivid and positive or mixed. **Possible use:** a song's
    runner-endorsed personal association, not only its title or public meaning, may make a run
    memorable. **Limit:** observational evidence, not proof of a RunState motivational benefit.

12. **[Designing Documentary Informatics](https://elsden.me/wp-content/uploads/2015/03/designing-documentary-informatics.pdf)**
    — Elsden and colleagues, design research. Tracking can support remembering and self-expression,
    not only monitoring and improvement. **Possible use:** a meaningful run record without turning
    RunState into a training coach. **Limit:** exploratory work around a speculative wedding service,
    not a tested running product.

13. **[A Quantified Past: Towards Design for Remembering with Personal Informatics](https://elsden.me/wp-content/uploads/2015/03/hci-journal-q-past-camera-ready-shared.pdf)**
    — Elsden, Kirk and Durrant, qualitative study of 15 long-term self-trackers. **Possible use:**
    people help make records meaningful; collecting more data alone does not create that meaning.
    **Limit:** design opportunities from a small interview study, not established feature effects.

14. **[Generative AI enhances individual creativity but reduces the collective diversity of novel content](https://discovery.ucl.ac.uk/id/eprint/10195027/)**
    — Doshi and Hauser, 2024 controlled writing experiment. AI ideas improved individual story
    evaluations while making stories more similar. **Possible use:** preserve Manley's original
    associations and review variety across a set, not just polish per response. **Limit:** short
    fiction, not running reflections; it does not establish inevitable sameness for our workflow.

**Parked opportunity:** immediate recognition and longer-term musical memory may be distinct kinds
of value. Personal associations must be supplied or endorsed, not guessed; unwanted reminders and
repetitive nostalgia are risks. No memory feature, extra runner questions, automatic snippet
selection, model training or evaluation service is approved by saving these references.

## What the negative feedback contributes

The negative sources do not argue that the RunState idea should be abandoned. They show the gap the
system has to close:

1. **Do not narrate the dashboard.** A reflection must add an earned interpretation, connection, or
   feeling; otherwise the visible metrics already did the job.
2. **Do not compare without purpose.** Pace, distance, and effort mean different things on a recovery
   run, first run back, speed session, or casual run. Silence is better than a false achievement.
3. **Do not mistake user text for literal fact.** A title or note is context, not automatic proof of
   terrain, intention, emotion, or outcome.
4. **Do not let positivity replace recognition.** Warmth without specificity becomes generic
   encouragement; specificity without truth becomes fake intimacy.
5. **Do not sound analytical merely to sound valuable.** Every clause should either be grounded,
   create a real connection, or earn its place through voice.
6. **Do not succeed only one response at a time.** A set can contain individually acceptable lines
   and still fail through repeated structures, openings, ideas, or emotional range.

## Provisional system hypothesis

**Historical exploration, not the October 3 implementation architecture.** The small later
angle-first comparison and reviewer results above did not validate this pipeline. Keep the useful
evidence and judgment distinctions; do not require these six stages or another evaluation round
before implementation:

1. **Assemble an evidence packet.** Keep measured run facts, comparisons, Energy/Effort, runner
   words, song-play evidence, and researched music context separate, with provenance and confidence.
2. **Select one angle.** Decide what is most worth reflecting before trying to write. The response
   does not need to mention every available fact.
3. **Generate genuinely different candidates.** Vary the idea and construction, not only synonyms.
   Artist/song knowledge is material to write with, not a paragraph to explain.
4. **Apply separate gates.** At minimum: factual truth, recognition/meaning, voice/creative value,
   product readiness, and variation across the set. One high average must not hide a truth failure.
5. **Use Manley's Hit / Close / Miss judgments with reasons.** Preserve approved lines, near misses,
   and rejected lines as calibration pairs. The reason is more reusable than the score alone.
6. **Improve retrieval and selection before considering model training.** A small, well-labeled
   example library and a reliable evaluation loop should exist before fine-tuning or a large prompt
   framework is justified.

This changes the unit of work. Manley does **not** need to author every possible reflection. His
highest-value role is defining taste through examples, explaining why a line lands or misses, and
settling the few product choices that govern the system.

## Research-response protocol

For future analysis in this area:

- Lead with the one to three findings most relevant to the current decision.
- Label claims as **source finding**, **RunState inference**, or **recommendation** when the
  distinction is not obvious.
- State source strength briefly: peer-reviewed research, company report, product review,
  practitioner guidance, or vendor/marketing analysis.
- Keep deeper summaries and caveats in this notebook or behind direct links. Do not turn every
  conversational response into a literature review.
- Research broadly enough to find contradictions and failure cases, then narrow back to the next
  product decision. No source becomes a RunState requirement without Manley's approval.

## Parked research questions — not implementation prerequisites

Work through these one at a time rather than opening all of them at once:

1. What minimum evidence packet gives the reflection enough context without burdening the runner?
2. What exact dimensions should Manley score when labeling a response Hit, Close, or Miss?
3. How should artist/song knowledge be sourced, bounded, and attributed internally so voice can be
   informed without fabricated facts or costume-like imitation?
4. How will the evaluation detect structural repetition and lost range across many responses?
