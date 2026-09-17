---
name: research-music-reflection-generation
description: "Working research notebook for a grounded, music-aware reflection system — source ledger, failure evidence, and provisional system direction"
metadata:
  type: research
  status: active-working-notebook
  updated: 2026-09-17
---

# Music-aware reflection generation research

## Status and purpose

This is a **working research notebook**, not an approved prompt, product contract, or implementation
plan. It keeps the most relevant sources and emerging lessons together while the reflection work is
slowed down for analysis.

Manley's direction as of September 17, 2026:

- Build a repeatable **system** for producing and evaluating reflections instead of hand-writing a
  separate answer for every combination of run, feeling, and song.
- Keep Manley directly involved in music and reflection choices. Research may inform those choices;
  it does not make them automatically.
- Research replies should be organized and relevance-first. Lead with the few findings that matter
  now, distinguish evidence from inference, and leave full source detail behind links unless Manley
  asks to go deeper.

## Reading priority

The labels below matter. Peer-reviewed research, company engineering reports, product reviews, and
craft articles can all help, but they do not carry the same evidentiary weight.

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
   - **RunState use:** Energy, Effort, run evidence, and a possible optional runner note can play
     different roles. Structured inputs make reliable interpretation possible; free expression
     preserves the runner's own meaning.
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
   - **RunState use:** use **verified stat + verified context + earned significance**.
   - **Limit:** some of the article's examples invent backstory such as practice, struggle, or
     motivation. RunState must never borrow that move without evidence.

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

This is the research direction to test, not a locked architecture:

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

## Next research questions

Work through these one at a time rather than opening all of them at once:

1. What minimum evidence packet gives the reflection enough context without burdening the runner?
2. What exact dimensions should Manley score when labeling a response Hit, Close, or Miss?
3. How should artist/song knowledge be sourced, bounded, and attributed internally so voice can be
   informed without fabricated facts or costume-like imitation?
4. How will the evaluation detect structural repetition and lost range across many responses?
