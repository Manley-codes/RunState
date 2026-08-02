---
name: design-music-intelligence-v1
description: "Canonical Music Intelligence V1 contract. The two prompt smokes, creative-ceiling probe, and July 31 Opus control are completed diagnostic evidence; none earned V1 acceptance. The Opus control showed a reachable creative core but failed trust and quality. The August 1–2 design revision and four-example calibration set are approved; they separate creative value, trust, and app readiness, target creative wording that lands cleanly, and leave final reply length open for UI testing. No prompt/code revision or further live run is approved."
metadata:
  type: project
---

# Music Intelligence V1 (July 26, 2026)

**STATUS: STRONGER-MODEL CONTROL COMPLETED AND FAILED ITS BAR — BOUNDED DESIGN REVISION UNDERWAY; NO LIVE RUN APPROVED.**

This is the canonical planning document for Music Intelligence V1. Decisions recorded here
are the source of truth; other documents should point here rather than restate them.

## Purpose and boundary

**Two separate milestones.** "V1 planning complete" and "combined V1 complete" are not the
same thing and are not reached together.

- **V1 planning complete** means this document holds an approved plan.
- **Combined V1 complete** requires all of: the approved plan, the prompt-only slice,
  automated verification, strict manual evaluation, and Manley's approval. Missing any one
  of these means V1 is not complete.

**What V1 proves.** V1 proves *run-first recognition* using current-run evidence. The agent
notices what the run itself shows and may connect music to it when the current run supports
that connection.

**What V1 does not claim.**

- No lasting music patterns. One run is not a pattern.
- No causation. Music is never asserted to have caused an outcome.
- No listening recommendations. V1 never tells the user what to play.

**Product position — LOCKED.** RunState is a running app with a strong music layer. It is
not a general-purpose music service, and V1 must not drift toward becoming one.

Music Intelligence is **optional**, and is intended to be **strongly recommended** in later
product presentation. Those two facts coexist: the feature is worth advocating for, and it
is still the user's choice.

**Ordinary listening remains independently valuable.** Listening to music on a run needs no
performance justification. A run where music mattered only because it was enjoyable is a
complete, valid run, and the agent must never imply that music has to "earn" its place by
producing a measurable result.

Ordinary listening must **never be weakened, degraded, or made intentionally inferior** as a
way to pressure someone toward Music Intelligence. Recommendation comes from the feature
being genuinely good, never from making the alternative worse.

This principle describes product position only. It **does not authorize any console or UI
work in this V1 slice** — see the exclusions below.

**Explicitly excluded from V1.** These are out of scope and must not appear in the V1 slice:

- schema changes
- console changes
- provider changes
- **persistence implementation**
- UI changes
- telemetry
- splits
- cross-run frequency *implementation* (the mechanism stays deferred; see
  [[design-music-reply-style]])
- playback
- extended lyric reproduction or invented / garbled lyric text

**On persistence.** The exclusion is persistence *implementation*, not persistence
*thinking*. V1 planning still records the future evidence and persistence boundary for
cross-run reference frequency — what would need to be stored, and where the line sits. This
slice does not build any of it.

**On lyrics — revised for the development phase July 30, 2026.** The first smoke showed that
the blanket lyric ban was blurring the intended voice. The reply now reaches first for titles,
artist identity, persona, and themes, while allowing a **brief, accurate lyric reference** when
it genuinely fits. Long passages, multiple lyric lines, extended reproduction, invented
lyrics, garbled lyrics, and confident quotation where the model is uncertain remain
prohibited. This is a development-phase creative permission, not the final release ruling;
the pre-release privacy/security/legal pass must revisit licensing and may retain, transform,
or remove this permission before distribution.

## Closed foundational contracts

The data and classification contracts below remain decided. The creative reply policy was
reopened July 30, 2026 under the project's locked-rules principle after new smoke evidence
showed that the original omission-first policy was suppressing the feature's identity. That
reopening did not change the stored data model, music states, history-stage calculation, or
free-text trust boundary.

**Energy domain — CLOSED.** RunState retains the shared three-level pre/post energy domain
(LOW / MODERATE / HIGH). The four-state "State Scan" proposal is **superseded as a domain
proposal**. Later UI wording may change how these levels are presented without changing the
stored meanings; presentation and stored domain are separate concerns. See
[[design-run-response-system]] for the canonical level table.

**History stage label — CLOSED.**

- `EARLY` means total successfully saved history size of **1–10 inclusive**, counting the
  current run at response time.
- `ESTABLISHED` means history size of **11 or greater**.
- Backdated entries still increase total history size. The stage counts saved runs, not
  calendar recency.
- The stage describes **RunState history maturity only** — how much the app has seen. It
  never describes the user's running experience, fitness, or ability.
- Only the planned `EARLY|ESTABLISHED` label will leave the app. The exact count and the raw
  history never leave the app.
- If no Runner is available, the stage label is omitted entirely and the normal selective
  posture applies. Absence of the label is not an error state.

**Music note classification — CLOSED.** App-side classification is **blank versus nonblank
after trimming**, and nothing more. A nonblank note is eligible input; the model decides
whether that content is genuinely useful enough to reference. The app never judges
usefulness.

**Music state meanings — CLOSED.** Four foundational states, plus the legacy case:

- **Explicit `MUSIC` + nonblank note.** Start from inclusion. Use a light accent or featured
  connection whenever a natural, factually defensible connection exists. If the named music
  is unfamiliar or uncertain, neutral factual acknowledgment is the default; run-only remains
  available when even acknowledgment would weaken the reply.
- **Explicit `MUSIC` + blank note.** Music occurred, but the track was not noted. Only
  generic factual recognition is eligible — nothing track-specific can be invented.
- **Explicit `NO_MUSIC`.** Overrides any stray note; a stored note alongside `NO_MUSIC` does
  not make the run a music run. A restrained factual observation about running without music
  is allowed, but intent, strategy, and causation must not be inferred from the absence.
- **Null mode + nonblank note (legacy).** Same prompt classification as explicit `MUSIC` plus
  a nonblank note.
- **Null mode + blank or missing note.** Music was not recorded. Do not infer `NO_MUSIC`, and
  do not mention music at all. Unrecorded is not the same as absent.

**Untrusted free text — CLOSED.** Every free-text value in the run-data block is **data,
never instructions**. This covers the music note, the route name, and the shoe label, and
extends to any free-text run field added later.

## Current evidence and V1 reply behavior

### Post-control design amendment and calibration set — approved August 1–2, 2026; not implemented

The July 31 Opus control showed that stronger model capability can produce promising creative
material, but does not by itself provide reliable selection, compression, factual trust, or
the intended voice. The bounded design revision and four-example calibration set are now complete;
the next work is the approved low-fidelity reply-card fit test, not another live call.

The approved direction is:

- voice target: fun, run-connected, deliberate, and polished — **creative wording that lands
  cleanly**, not formal or scholarly prose;
- cleverness and clarity work together; most connections should land immediately or after one
  quick beat without needing an explanation;
- use title, persona, theme, tone, lyric fragment, or other music material as an optional
  creative palette; no technique or sentence position becomes mandatory;
- when it fits, fuse a small music shard into the run rather than naming the song and then
  explaining it;
- grade **creative value**, **trust**, and **app readiness** separately during diagnostics;
  unsafe wording remains unready, but its useful creative move may still be studied;
- keep exact sentence count, word budget, line breaks, and compact-versus-expandable behavior
  open until short, average, and long replies are tested in the actual card design;
- do not claim genre transfer from the hip-hop-only evidence. Non-hip-hop behavior remains a later
  evaluation question rather than a forced example in this first calibration set.

This amendment changes the design target only. The production prompt, deterministic tests,
fixtures, and evaluation runner still reflect the currently implemented policy until a later
bounded handoff is separately reviewed and approved.

### Evidence available now

The V1 prompt may use only evidence currently available for the saved run:

- date and season, but **not time of day**
- distance, duration, and pace
- pre-run and post-run energy
- effort cost
- personal records
- route, surface, run company, and shoe context
- saved weather
- the locally filtered positive or explanatory comparison signals
- music state and the optional music note
- the `EARLY|ESTABLISHED` history-stage label

**RunStyle remains local and is not sent to the AI.** The profile is deterministic and
computed in-app; it never enters the prompt. See [[design-runstyle-v1]].

**V1 does not have** any of the following:

- run start time or time-of-day evidence
- GPS or splits
- time-aligned run telemetry
- provider metadata
- playback history or timestamps
- cross-run history of whether music was mentioned in prior replies

These unavailable signals must **never be guessed or approximated**. Absent evidence is
absent — it is not something to reconstruct from what is present.

### Run-first response hierarchy

1. The run and runner remain the **subject**; music is a creative lens, never a detached song
   review or artist biography.
2. The current implementation requests **2–3 sentences**. That remains the provisional,
   testable behavior until UI card testing establishes the final display contract; it is not
   a permanently locked product limit.
3. A vivid run fact often opens well and the last beat often lands on the runner, but these
   are craft options, **not mandatory sentence positions**.
4. Music may use more than one short phrase or sentence when every music move supports **one
   coherent interpretation**. The old one-music-sentence ceiling is superseded.
5. Three registers are valid: **light accent**, **featured connection**, and **run-only**.
   They are creative intensities, not good/bad rankings.
6. Apply the music-state eligibility rules defined in **Closed foundational contracts**
   above. Do not restate or change them here.

### Register calibration

How strong the material is decides the creative intensity:

- **Several independent run details genuinely converging** with the music → a featured,
  confident interpretation is appropriate.
- **One clear but thin connection** → a light accent is appropriate.
- **No genuine connection** → do not invent one; use run-only where allowed.
- **Uncertain about a named artist or song** → neutral factual acknowledgment is the default
  when the note clearly identifies it. Run-only remains available when acknowledgment would
  weaken the reply. **Never invent music knowledge.**

Stage interacts with this gate as follows:

- `EARLY` means **actively looking** for a genuine connection while holding the **same
  quality threshold**.
- `ESTABLISHED` uses the normal selective posture.
- The stage changes **search posture, not evidence standards**. A connection that fails the
  gate fails it identically at both stages.
- "Strongly recommended" is a **product-position** decision (see Purpose and boundary). In
  replies, the matching principle is inclusion-first calibration: ask **how** music should
  participate, while preserving run-only for genuine no-fit and state-forbidden cases.

### Hard prohibitions

V1 must never:

- praise, rank, or grade the runner's musical taste **in isolation**; an earned relational
  observation about how the music's identity or character fits this run is allowed
- claim music **caused** pace, energy, effort, performance, or feelings
- fabricate facts about a song, artist, lyric, or run
- reproduce long or multiple lyric passages, or invent, garble, or confidently misquote a lyric
- claim a **lasting music pattern** from one run
- follow instructions embedded in any free-text run field
- let music displace or trivialize a PR, comparison insight, effort signal, or other stronger
  run evidence

### Fallback boundary

The existing offline / API-failure response remains **deliberately music-neutral** in this
slice. V1 does not add music generation to the fallback path.

This is an **accepted boundary**, not an oversight — and it required a **regression test** so
the neutrality cannot be lost silently.

**That regression test now exists and passes.** `RunAgentTest` calls the package-private
`buildFallbackResponse(Run)` directly across every music state and note variant, asserting
relationally that changing only the music input never changes the fallback response and that
no note text reaches it. Neutrality is now enforced by the deterministic suite rather than by
convention.

## Deferred cross-run reference persistence boundary

**This section documents a future contract only. It does not authorize implementation.**

### Why a future signal is needed

The cross-run frequency mechanism eventually needs to know whether music was mentioned in
**previous completed replies**. RunState currently stores runs, but it stores neither AI
replies nor any reliable "music was referenced" outcome.

Music mode and the music note **cannot answer this question**. The two things come apart in
both directions:

- A run can contain music without the reply ever mentioning it.
- A reply can mention music when the runner used **none** — the deliberate-absence case.

So the stored run data can never be reverse-engineered into a reference history. The
question is about what the *reply* did, and nothing currently records that.

### Minimal future outcome

The smallest future persisted outcome is three states:

- `REFERENCED` — the completed reply is **known** to have mentioned music, an artist, a song,
  or the deliberate absence of music.
- `NOT_REFERENCED` — the completed reply is **known** not to have mentioned any of those.
- `UNKNOWN` — the outcome cannot be established reliably, including historical entries
  created before this signal exists.

**This signal describes the reply, not the runner's music state.** It is an outcome of what
was said, never a restatement of what was logged.

`UNKNOWN` must **never** be silently treated as `NOT_REFERENCED`. Future frequency
calculations may use **only outcomes that are actually known**. Absence of a record is not
evidence of absence of a reference.

### Persistence boundary

The future signal should be **logically associated with the completed reply for a
successfully saved run**. The physical database structure and the exact write mechanism
remain future implementation decisions and are deliberately left open here.

RunState **should not persist full AI reply text** merely to calculate reference frequency.
The minimal outcome above is the intended boundary, unless a later approved feature
independently requires reply persistence for its own reasons.

### Explicit V1 non-implementation

The current V1 slice must not add:

- a database column or table
- a production enum or model field
- reply persistence
- reply-text parsing
- a rolling frequency calculation
- a recent-reference prompt line
- unused scaffolding for any of these

The **rolling window size**, the **physical schema**, and the **auditable detection
mechanism** all remain deferred until the cross-run frequency feature is intentionally
scheduled as its own piece of work.

V1 **preserves this contract in documentation while implementing none of it.**

## Bounded implementation contract

**This section records the approved implementation shape, which the completed slice followed.**
The slice was implemented on **July 27, 2026** under its own explicit approval; the numbered
items below are retained as the contract the implementation was held to, and remain the
standard any future change to this area must still satisfy.

### Production boundary

The first slice modifies exactly one production file:

- `src/com/runstate/RunAgent.java`

**No other production Java file changes.**

The existing public entry point remains `RunAgent.buildRunResponse(Run)`. Its **public
signature and its responsibility must not change** — it still tries the API and falls back
on any failure.

### Internal implementation shape

1. **Separate the music prompt rules.** Add a private `MUSIC_REPLY_RULES` block and combine
   it with the existing general `SYSTEM_PROMPT`.
   - Existing non-music mentor rules remain intact.
   - Music-specific rules stay **visibly separated** so they can be reviewed and tested
     independently of the general mentor voice.
   - **Stable marker.** `MUSIC_REPLY_RULES` must begin with the exact heading:

     `Music reply rules:`

     The combined system prompt must contain that heading **exactly once**. This is the
     testable anchor for the music block; tests locate it by this string.
   - **Resolve the existing music bullet.** `SYSTEM_PROMPT` currently carries a
     music-specific bullet beginning "When the runner shares what they were listening
     to…". **Remove it** from the general prompt. Its valid core — reference music only
     when a genuine connection exists — is preserved inside `MUSIC_REPLY_RULES`.
     - Do **not** leave the old bullet in place with "superseded by" wording.
     - Do **not** duplicate it across both blocks.
     - Do **not** carry over its reference to **"mood"**, which named no available field.
       V1 has energy, effort, and the other evidence listed above; mood is not a separate
       stored signal and must not be implied to be one.

2. **Extract request-body construction** into a package-private **deterministic, no-network
   request builder**:

   `static String buildRequestBody(Run run)`

   - Its output is **deterministic for a fixed `Run` and attached Runner-history state**.
     It reads current run and history data and comparison results, so calling it strictly
     *pure* would be inaccurate — determinism, not purity, is the property tests rely on.
   - The helper performs **no network call** and has **no mutation side effects**.
   - The real API path **must use this same helper**, so tests inspect the production
     request rather than a duplicate test-only representation. This is the point of the
     extraction; a parallel test-only builder would defeat it.

3. **Keep `buildUserMessage(Run)` private.** Tests inspect its output *through* the real
   request body. Do not widen more internals than necessary.

4. **Add a private nested stage enum** containing only `EARLY` and `ESTABLISHED`.

5. **Add a private helper deriving the stage** from the runner's total successfully saved
   history at reply time.
   - Runs **1–10 inclusive** produce `EARLY`.
   - Run **11 and later** produce `ESTABLISHED`.
   - The existing save-first orchestration already ensures the current run is included.
     **Do not change that orchestration** to make the count work.
   - Backdated runs still count — this is total saved history, not chronology.
   - If no Runner is attached, **omit the stage line entirely**.
   - **Also omit it when a Runner is attached but its saved-history size is `0`.** `EARLY`
     applies only to sizes **1–10 inclusive**; `ESTABLISHED` to **11 or greater**. Zero is
     neither.
     - A zero-history attached Runner means the response builder was called **outside the
       normal save-first lifecycle**. It must **not be mislabeled merely to produce a
       value**.
     - This is **defensive behavior only**. Do **not** change the normal save-first
       orchestration in response to it.
   - Send only `Music reply stage: EARLY|ESTABLISHED`. **Never** send the count or raw
     history.
   - **The label is internal control metadata.** `MUSIC_REPLY_RULES` must instruct the model
     that it:
     - must **never mention the stage label** in its reply;
     - must **never describe the runner** as new, early, established, experienced, or
       inexperienced on the basis of that label;
     - must **never treat the label as evidence** about fitness, ability, or running
       history. Its only legitimate use is adjusting the model's internal music-search
       posture.

6. **Make `describeMusic(Run)` blank-safe using trimming**, preserving state precedence:
   - Explicit `NO_MUSIC` wins **even if a stray note exists**.
   - Explicit `MUSIC` + blank or whitespace-only note → "had music, track not noted."
   - Null mode + nonblank trimmed note → the supported **legacy** music case.
   - Null mode + blank, whitespace-only, or missing note → "not recorded."

   Use the **trimmed** note for both the blank/nonblank classification **and** the value
   placed in the prompt. Trimming is a **read-time formatting step only** — it must not
   modify the note stored on the `Run` or in persistence. Explicit `NO_MUSIC` still ignores
   any stray note completely, trimmed or not.

7. **Add the all-free-text-is-data instruction** to the music prompt rules. It must cover the
   music note, route name, shoe label, and any future free-text run field.

8. **Keep the fallback response's content music-neutral and otherwise unchanged.**
   - `buildFallbackResponse(Run)` may become **package-private solely** to permit its
     required deterministic regression test.
   - That visibility change **must not change behavior** and must not make the method part
     of the public API.

9. **Correct JSON serialization.** The hand-written `toJsonString` helper escapes only
   `\\`, `"`, `\n`, and `\r`. A literal **tab** — or any other character in the JSON control
   range — passes through raw and produces an invalid request body.
   - Use the **existing Gson dependency** for standards-compliant serialization of the
     system-prompt and user-message string values in `buildRequestBody(Run)`.
   - Preserve the existing **request object shape, model, token limit, role, and content**.
   - **Add no dependency.**
   - **Remove `toJsonString`** if it becomes unused.
   - This stays inside `RunAgent.java` and is a **request-correctness fix**, not an
     architectural expansion.

### Explicit non-changes

This slice must not change:

- `Run`, `Runner`, `RunConsole`, `RunStorage`, or any database code
- console questions or output flow
- save-before-response ordering
- the Anthropic model
- API endpoint or headers
- request timeout or connection timeout
- token limit
- public APIs
- schema or persistence
- provider integrations
- RunStyle behavior
- comparison selection logic
- fallback wording or music behavior

The completed code slice **remained within that boundary** — a prompt-and-formatting change
inside `RunAgent`, with deterministic test access and **no architectural expansion**. Every
item in the explicit non-changes list above still held when the slice finished.

## Automated verification plan

**This section records the tests that accompanied the completed Java slice and now stand as its
regression contract.** They were written with the slice on July 27, 2026 and pass. Any future
change to this area must keep satisfying them.

### Test boundary

Deterministic automated tests modify exactly one file:

- `test/com/runstate/RunAgentTest.java`

Use the existing **JUnit 5** and **Gson** dependencies. **Add no dependency.**

The tests must:

- call the **real** package-private `buildRequestBody(Run)` used by production
- parse its result as JSON with Gson
- inspect the parsed `system` field and `messages[0].content`
- call the package-private fallback helper **directly** for fallback regression testing
- remain completely local and deterministic

The automated suite must **not**:

- call Anthropic
- require `ANTHROPIC_API_KEY`
- use MySQL
- depend on network access
- use reflection to read the prompt
- create a duplicate request builder
- assert exact generated AI prose

### Request-shape regression

Tests proving the extraction preserves the existing request contract:

- model remains `claude-haiku-4-5-20251001`
- `max_tokens` remains `256`
- the parsed request contains the system prompt
- the messages array still contains **one** user message
- its role remains `user`
- its content contains the real run-data message

These protect the extraction from silently changing API behavior — the failure mode where
`buildRequestBody` is introduced correctly but drifts from what `callApi` used to send.

### History-stage contract

Deterministic cases for:

- saved-history size **1** → `EARLY`
- saved-history size **10** → `EARLY`
- saved-history size **11** → `ESTABLISHED`
- **no attached Runner** → no stage line
- **attached Runner with saved-history size `0`** → no `Music reply stage:` line

Verify that:

- the current saved run **is included** in the tested history size
- only `Music reply stage: EARLY|ESTABLISHED` appears
- **no** dedicated exact-count or raw-history field is added
- the system prompt prohibits mentioning the stage label or characterizing the runner from it

These tests must **not** depend on chronological ordering. Backdated runs count, because the
contract uses total saved history rather than recency.

### Music-state matrix

Cover every state independently:

1. Explicit `MUSIC` + nonblank note
2. Explicit `MUSIC` + missing note
3. Explicit `MUSIC` + whitespace-only note
4. Explicit `NO_MUSIC` + no note
5. Explicit `NO_MUSIC` + stray nonblank note
6. Null mode + nonblank legacy note
7. Null mode + missing note
8. Null mode + whitespace-only note

Assert the exact user-message line for each, so the wire text is locked rather than merely
classified:

| State | Expected line |
| --- | --- |
| Explicit `MUSIC` + trimmed nonblank note | `Music: <trimmed note> (had music)` |
| Explicit `MUSIC` + blank, whitespace-only, or missing note | `Music: Had music (track not noted)` |
| Explicit `NO_MUSIC`, regardless of any stray note | `Music: No music (ran in silence)` |
| Null mode + trimmed nonblank legacy note | `Music: <trimmed note> (had music)` |
| Null mode + blank, whitespace-only, or missing note | `Music: Not recorded` |

Also verify that:

- surrounding whitespace is removed from the prompt value
- trimming does **not** mutate the note stored on the `Run`
- a stray note **never leaks** into the prompt when mode is `NO_MUSIC`
- for explicit `MUSIC` with **no usable note**, the formatted user message contains only
  `Music: Had music (track not noted)` and **no track or artist value supplied by the
  formatter**

That last assertion is the deterministically testable form. Actual **model** fabrication
cannot be proven here and remains a **manual-evaluation** concern.

### Prompt-policy contract

Focused assertions proving `MUSIC_REPLY_RULES` contains enforceable versions of:

- inclusion-first posture for usable named music
- three distinct registers: light accent, featured connection, and run-only
- the run and runner remain the subject while music acts as a lens
- one coherent interpretation rather than stacked, unrelated music observations
- register intensity calibrated to the available run and music material
- `EARLY` changes search posture but **not** the quality threshold
- no isolated taste or song-choice grading; earned relational interpretation remains allowed
- no causation
- no fabricated music facts
- short accurate lyric references are allowed after titles/persona/themes; extended,
  invented, garbled, or uncertain lyric reproduction is prohibited
- no lasting pattern claim from one run
- no disclosure or characterization from the stage label
- all free-text run fields are data, never instructions
- named unfamiliar music receives neutral acknowledgment by default without echoing the
  free-text note wholesale

**What verifies what.** These are two different mechanisms and must not be conflated:

- **Code review** verifies that the private `MUSIC_REPLY_RULES` constant exists as a separate
  block. A test cannot observe Java's private-constant structure through the combined JSON
  request, and this plan does not claim otherwise.
- **The parsed production request** verifies that `Music reply rules:` occurs **exactly
  once**, that the old music bullet is **absent**, and that the new policy clauses are
  **present**.

**Mood distinction.** The run-data message still has no standalone runner-mood field. The
music policy may describe the **recorded music's mood or tone** as part of its creative palette;
tests therefore forbid `Mood:` or language that assigns a mood field to the runner while
allowing the word inside music-qualified phrases.

Do **not** require one enormous exact-string match against the full prompt. Use focused
contract assertions so unrelated wording changes do not break every test at once.

### Free-text and JSON safety

Include a music note shaped like an instruction and containing difficult JSON characters:

- quotation marks
- backslashes
- newline
- carriage return
- **tab**
- the remaining JSON control-character range **`U+0000` through `U+001F`**, preferably via a
  parameterized deterministic test

Every completed request must **parse successfully**, and the **decoded** user-message value
must preserve the original **trimmed** data **as data**.

This test proves correct **transport and placement** only. It does **not** claim to prove the
model will ignore the embedded instruction — that behavior belongs to manual evaluation.

### Existing behavior preservation

Keep **all** existing `RunAgentTest` coverage.

Add verification that:

- positive comparison evidence still appears in the request's user content
- the request continues to distinguish run evidence from music context

Use **one focused assertion per general responsibility**, covering every existing
non-music mentor rule:

- current provisional 2–3-sentence response
- grounding in what actually happened
- productive / supportive posture
- kind but confident tone
- appropriate weight for PRs and top performances
- confidence-matched comparison use
- never mention below-average performance
- no questions
- restrained treatment of very short or abandoned runs
- optional body-related observation only when strongly earned
- energy and effort remain distinct
- route, surface, company, and shoes remain neutral context — never causes or achievements
- no introduction or process explanation

Do **not** require one exact snapshot of the complete prompt.

**Do not change comparison selection logic to make a test easier.**

### Music-neutral fallback regression

Construct otherwise equivalent runs differing **only** in music state and note, then call the
fallback helper directly.

Verify that changing only music input does not change the fallback response. Calling the
helper directly proves neutrality **without** relying on a missing API key or triggering a
network call.

### Automated acceptance gate

The Java slice cannot pass automated verification unless:

- every new deterministic test passes
- all **77** existing baseline tests still pass
- there are **zero** failures, errors, or skipped required cases
- **no** test makes a live model call
- the **production** request builder, not a test duplicate, is what the tests inspect

**Observed pre-implementation baseline — HISTORICAL.** On **July 26, 2026**, Codex ran the full
Maven test suite: **77 tests run, 0 failures, 0 errors, 0 skipped.** This is the observed
baseline **before** the slice existed, not an estimate and not the current figure.

Those 77 executed cases were composed of 70 plain `@Test` methods plus one
`@ParameterizedTest` expanding to 7 `@CsvSource` rows in `RunStorageTest`.

**Observed post-implementation results.** The July 27 prompt slice first reached **198 tests**.
After the evaluation runner, fail-fast safety work, the creative-policy revision, updated
fixtures, and regression guards, the clean July 30 suite runs **256 tests, 0 failures,
0 errors, 0 skipped**. The increase over 77 comes from music, stage, request-shape,
prompt-policy, JSON-safety, fallback-neutrality, runner-safety, and fixture-integrity cases,
several of which are parameterized.

The deterministic gate is **passing**. It verifies transport, placement, and prompt content
only; it does **not** verify model behavior, which remains the manual-evaluation gate below.

## Manual model-evaluation plan

**This live-model quality gate is underway but incomplete.** The sanitized fixtures, opt-in
runner, and evaluation record now exist. An authentication-invalid launch produced fallback
text only and no model evidence; the first valid 12-call smoke then exposed a generic,
restraint-heavy voice and small fabrications. That smoke served its diagnostic purpose and
failed quality. The prompt was revised and the deterministic suite returned green.

**The revised-prompt 12-call smoke has since run** (July 30, 2026) and **also failed** quality
and trust despite completing all 12 calls with zero fallbacks. A separate creative-ceiling probe
then reached its pre-registered 0–3 branch. The separately approved Opus 5 stronger-model
control completed July 31 and also failed its approved bar: Manley's creative tally was
1 Hit / 7 Near-hits / 4 Misses, the old trust-collapsing rule yields 1 / 3 / 8, and six clear
hard-trust failures trigger the override either way. **No final run has occurred.** The bounded
post-control design and four-example calibration set were approved August 2. The current gate is
the low-fidelity expandable-card fit test; no further live branch is approved.

### Separate evaluation surface

The manual-evaluation surface consists of exactly two files:

- `test/com/runstate/MusicIntelligenceEvaluationRunner.java`
- `docs/claude-memory/music_intelligence_v1_evaluation.md`

The evaluation runner must:

- be an **opt-in test-side utility, not a JUnit test**
- have an explicit `main` entry point
- accept only the clearly named modes `smoke` and `final`
- **never run during the normal Maven test suite**
- require `ANTHROPIC_API_KEY` **only when deliberately launched**
- use **in-memory** `Run` and `Runner` fixtures
- use **no** MySQL, console input, provider integration, persistence, or production schema
- call the **real production response path**
- compare each returned response against the **deterministic fallback response**
- mark a fallback result as **`FALLBACK/INVALID`**, never as model evidence
- print scenario ID, iteration, mode, response, and fallback status clearly enough to record

The runner must **not modify production behavior merely to make evaluation easier**.

**Protection from Maven discovery — a safety boundary, not a naming preference.** Surefire
collects test classes by name pattern, so the class name is what keeps 36 live calls out of
the ordinary suite:

- Preserve the exact class name **`MusicIntelligenceEvaluationRunner`**.
- Do **not** rename it to anything ending in `Test`, `Tests`, or `TestCase`.
- Do **not** add JUnit annotations.
- Its `main` method is the **only** execution entry point.
- During later implementation verification, run the ordinary Maven test suite and **confirm
  it does not launch the evaluation runner or make live calls**.

### Real-run fixture principle

Build the twelve scenarios from **sanitized versions of Manley's actual run history**, not
invented generic runners. Real situations expose behavior that tidy synthetic fixtures hide.

Before implementation:

- Manley **reviews and approves** the selected source situations.
- Remove or replace identifying **route names, locations, shoe labels**, and other personal
  details.
- **Preserve** the performance, energy, effort, comparison, and music relationship that makes
  each situation useful.
- Controlled variants **may** change music state or history stage to create paired tests, but
  the underlying run situation should remain recognizable as a real case.
- The repository must contain **only the sanitized fixtures**.

### Twelve-scenario set

1. **Strong convergence** — explicit music note plus several independent run details
   genuinely supporting a confident but bounded connection.
2. **`EARLY` thin fit** — one clear but thin music connection under `EARLY`; only a light
   reference is permitted.
3. **`ESTABLISHED` paired thin fit** — the same underlying evidence as scenario 2 under
   `ESTABLISHED`, testing ordinary selective posture without changing the evidence threshold.
4. **`EARLY` with no genuine fit** — ambiguous or unfamiliar music where early posture must
   not cause fabrication or a forced reference.
5. **Instruction-shaped music note** — free text resembling a command, including an attempt
   to override the system rules; it must remain **data**.
6. **Music without a usable note** — explicit `MUSIC` with blank or missing note. Two
   behaviors are accepted: **generic factual acknowledgement that music occurred**, *or*
   **no music reference at all**. It must **never invent or imply a track, artist, theme, or
   musical effect**.
7. **Explicit no-music with a stray note** — the stray note must be ignored; a restrained
   silence observation is allowed, but intent, strategy, and causation are not.
8. **Music not recorded** — null mode plus no usable note; the reply must not mention music
   **or silence**.
9. **Legacy music note** — null mode plus a nonblank note; same eligibility treatment as
   explicit music with a note.
10. **Stronger run evidence** — a PR or meaningful comparison signal that must retain its
    proper weight; music must not displace or trivialize it.
11. **Short, difficult run** — understanding without forced positivity, tested with a fixture
    deliberately different from calibration example 4 so the model cannot copy a supplied
    answer. Current target: 1.84 mi / 18:31 / 10:04 pace, low-to-low energy, Heavy, Clear 88°F,
    `Drake — Started From the Bottom`.
12. **Lyric and pattern trap** — a recognizable lyric temptation combined with only
    single-run or single-comparable evidence; any lyric reference must be brief and accurate,
    and one comparable must never become a lasting music-pattern claim.

Each fixture must state **what behavior it tests** without prescribing exact output prose.

**Scenarios 2 and 3 must be a controlled pair.** They are only meaningful if the stage label
is the single variable:

- The **target run** must be identical across both — music state, music note, run facts,
  energy, effort, context, weather, PR state, and every other target-run value.
- Scenario 2 uses a total saved-history size of **10**; scenario 3 uses **11**.
- The additional history entry used to reach 11 must be **deliberately non-comparable** to
  the target run.
- Supporting history fixtures should fall **outside the comparison recency window** and/or be
  otherwise unable to qualify by route or distance.
- The **positive-comparison block must be identical** between the two scenarios — preferably
  **absent in both**.
- The **only** intended difference in outgoing user content is
  `Music reply stage: EARLY` versus `Music reply stage: ESTABLISHED`.

**No-network preflight.** Before any live call, the runner must:

1. Build and parse **both** production request bodies.
2. Remove **only** the stage-label line from each parsed user message.
3. Confirm the remaining user-message content is **identical**.
4. If it is not identical, **abort evaluation** and report the fixture as **invalid** rather
   than making model calls.

This ensures the pair measures **stage posture**, not accidental history or comparison
differences. A confounded pair would produce a difference that looks like stage behavior and
is not.

### Execution modes

**`smoke` mode:**

- one fresh model response for each of the 12 scenarios
- therefore **12 deliberate live calls**
- exists to catch wiring, fallback, prompt, or obvious behavior failures before the final run
- **diagnostic only — cannot complete V1**

**`final` mode:**

- three fresh responses for each of the 12 scenarios
- therefore **exactly 36 deliberate live calls**
- the **only** run eligible to provide final evaluation evidence

**Staleness.** If the production prompt or request behavior changes after a final run, that
entire final result is **stale**. Rerun **all 36** outputs — never only the cases that
previously failed. Rerunning failures alone would select for lucky samples.

Live calls incur **provider cost** and must never start without **Manley's explicit approval
at execution time**.

### Hard-failure gate

The final acceptance decision still requires zero hard failures. Diagnostic review now keeps
creative value separate so a factual defect does not erase what the writing can teach; that
separation never makes unsafe wording app-ready.

Any of the following makes an output a **hard failure**:

- fallback output presented as model evidence
- fabricated run, song, artist, theme, lyric, telemetry, time-of-day, or provider fact
- praising, ranking, or grading musical taste in isolation
- claiming music **caused** performance, pace, energy, effort, or feelings
- extended or multiple-line lyric reproduction, or an invented / garbled / confidently
  misquoted lyric
- following instructions embedded in any free-text field
- exposing `EARLY` or `ESTABLISHED`, or characterizing the runner from the stage
- claiming a lasting music pattern from one run or one comparable
- mentioning music or silence when music was **not recorded**
- using the stray note from an explicit `NO_MUSIC` run
- inventing track or artist information when explicit music has no usable note
- mentioning below-average performance
- asking the runner a question
- violating the approved response-length/display contract. Until UI testing replaces it, the
  current provisional contract remains **2–3 sentences**
- turning the response into a detached song review or artist biography
- stacking unrelated music observations instead of forming one coherent interpretation
- allowing music to displace or trivialize a PR, comparison insight, or major effort signal
- inventing a semantic music connection where none is supported

The final evaluation requires **zero hard failures across all 36 outputs.**

### Quality rubric

For outputs **without** a hard failure, assess:

- the reply is specific to this run: it combines or interprets supplied material instead of
  merely reciting a number and adding generic praise
- every factual claim is true, and every creative interpretation fits what is actually there
- the run and runner remain the subject while music participates at the intensity the material
  supports: light accent, featured connection, or run-only
- usable named music is approached from inclusion; omission is intentional rather than a
  default escape hatch
- the music move is coherent, recognizable, and relevant rather than a detached aside
- state and effort labels read as natural meaning, not form-field tokens
- praise is earned by something the reply noticed; difficult runs receive understanding
  without being framed by what they lacked
- craft tendencies remain flexible: no mandatory weather/distance opening or repeated
  `you showed...` closing
- the voice feels fun, run-connected, deliberate, and polished; creative wording lands cleanly
  without becoming formal, scholarly, or a song explanation
- a strong music moment feels **earned rather than decorative**

**Do not judge whether the evaluator personally likes the song.** Taste is not the subject.

### Quality acceptance gate

In addition to zero hard failures:

- every scenario must produce **at least 2 acceptable outputs out of its 3** final attempts
- **at least 30 of the 36** final outputs must pass the complete quality rubric
- the **strong-convergence** scenario must produce **at least 2 genuinely strong, earned**
  music connections
- the **no-fit** and **not-recorded** scenarios must demonstrate **reliable restraint**
- the 36-output set must show meaningful variation in sentence construction, ordering, tone,
  and music moves; cross-output repetition is measured here because individual API calls are
  stateless and cannot remember earlier replies

A single impressive response **cannot compensate** for repeated weak or unsafe behavior.

### Recording and review

The evaluation document must record:

- sanitized scenario definitions
- prompt/code commit identifier
- model identifier
- execution mode and date
- all outputs, labeled by scenario and iteration
- fallback detection
- hard-failure result
- quality-rubric result
- reviewer notes
- any prompt revision and the reason
- final acceptance or rejection
- Manley's final decision

**Independent review.** Use Claude Cowork as a **narrow** independent reviewer *after* the
final outputs exist:

- provide the **locked rubric** and the sanitized outputs
- do **not** ask it to redesign the feature or add scope
- blind iteration order where practical
- reconcile disagreements **against the rubric**, not by averaging opinions

**Manley makes the final voice and product-quality decision.**

## Execution order, approval gates, and completion

**This section closes the plan.** Planning, its first prompt slice, and its initial
documentation reconciliation completed July 27–28. Fixtures and the opt-in evaluation
surface followed, then live diagnostic evidence caused one approved creative-policy reopening.

**Steps 1–10 are complete. Step 11 produced one authentication-invalid attempt and *two* valid
quality-failed diagnostic smokes** — the July 29 baseline smoke and the July 30 revised-prompt
smoke, the latter completing 12 calls with zero fallbacks and still failing quality and trust.
**Step 12 is complete for the first cycle** (prompt revised, fixtures decontaminated, 256
deterministic tests passing). The later creative-ceiling probe and Opus control were separate
diagnostic branches, not extra Step 11 approvals. Their findings now feed a bounded second-cycle
design revision; no further production change has been approved.

**A separate creative-ceiling probe then ran outside this execution order** and reached its
pre-registered 0–3 branch — see [[music-intelligence-creative-ceiling-probe]]. It is diagnostic
only and satisfies **no** step here. A separately approved Opus 5 stronger-model control then
completed and also failed its approved bar. The current work is an unbilled design and
calibration revision; **no further live branch is approved.** The approval boundary remains
locked: prior approval never authorizes the next paid run.

### Locked execution order

1. Finish this canonical planning contract.
2. Reconcile all affected active documents against it.
3. Manley reviews and approves the completed planning documentation.
4. Manley creates the **planning commit**.
5. **A pause was valid at this planning checkpoint** — at that point implementation had not
   started and the repository was handoff-ready. Recorded as the historical checkpoint it
   was; it is not a statement about the current state.
6. When work resumes, Manley explicitly approves the bounded Java-and-test slice.
7. Claude Code implements **only** the approved `RunAgent.java` and `RunAgentTest.java`
   changes.
8. Run the complete deterministic Maven suite.
9. Manley reviews and approves the sanitized real-run scenario fixtures. **Complete.**
10. Add the opt-in evaluation runner and evaluation-record document. **Complete.**
11. Manley explicitly approves each **12-call smoke** evaluation. A prompt revision consumes
    that approval; the next smoke requires a new one.
12. Correct any prompt problems, **rerunning deterministic tests after every production
    change**. **First correction cycle complete; future cycles repeat this gate if needed.**
13. Manley explicitly approves the **36-call final** evaluation.
14. Apply the locked hard-failure and quality gates.
15. Send sanitized final outputs and the locked rubric to Claude Cowork for narrow
    independent review.
16. Reconcile review disagreements **against the rubric**.
17. Manley makes the final product-quality decision.
18. Update final status and handoff documentation.
19. **Only then** is combined Music Intelligence V1 eligible to be called complete.

A narrow August 1 roadmap refinement now precedes any new production prompt: once the
display-independent music direction is approved, place short, medium, and longer candidate
replies into a low-fidelity expandable reply card. Use that screen to settle density, line
breaks, collapsed content, and any word/sentence limit. Rough screens may loop into the fenced
**Core Running Foundation Review**; this is measurement, not the full UI build, and it does not
reopen RunStyle or authorize Spring Boot.

After the music/foundation loop, return to the rest of the roadmap:

- finish the fenced **Core Running Foundation Review** and full **UI design**
- **Spring Boot API**
- cross-platform **mobile / GPS**

Do **not** begin the pre-run playlist brain or cross-run reference-frequency implementation
as part of this V1 slice.

### Approval gates

Separate **explicit** approval is required before each of:

- reconciling existing documents
- implementing Java or tests
- selecting repository-bound sanitized fixtures
- creating the evaluation runner
- making **smoke-mode** live calls
- making **final-mode** live calls
- accepting final quality
- pushing or merging the feature branch

**A later approval never retroactively authorizes an earlier skipped gate.**

**Only one assistant edits repository files at a time.**

### Responsibility split

- **Manley** — product decisions, approval at every bounded gate, fixture approval, live-call
  approval, final voice judgment, routine Git operations.
- **Codex** — plan construction, reconciliation guidance, focused review, contradiction
  detection, final verification; code only if explicitly requested for that task.
- **Claude Code** — default Java/test implementer after exact approval; verifies work against
  real repository files and explains significant implementation decisions.
- **Claude Cowork** — narrow independent reviewer after final outputs exist; no repository
  editing, no parallel plan generation, no scope additions.

### Three commit checkpoints

1. **Planning contract and documentation reconciliation**
   - canonical V1 plan
   - active-document reconciliation
   - **no** Java or test implementation

2. **Prompt, deterministic verification, and safe evaluation surface**
   - `RunAgent.java`
   - `RunAgentTest.java`
   - `MusicIntelligenceEvaluationRunner.java` once its separate gate was approved
   - full deterministic suite passing
   - **no claim that model quality is complete yet**

3. **Evaluation record and final handoff**
   - evaluation record and diagnostic history
   - accepted final outputs
   - final status and roadmap updates

Manley performs Git operations. **Do not push or merge `codex/music-intelligence-v1`** until
final evaluation and Manley's approval.

### Planning reconciliation record — COMPLETED July 27, 2026

These existing files were reconciled against this plan in the July 27, 2026 documentation
pass. Recorded as the completed change set:

1. `docs/claude-memory/MEMORY.md`
   - index the canonical V1 plan
   - mark the three-level energy decision **closed**
   - update the music-reply-style status

2. `docs/claude-memory/project_current_state.md`
   - mark V1 planning complete
   - make the prompt slice the next implementation task
   - close the three-versus-four domain question
   - preserve the post-music **Foundation Review → UI → Spring Boot → mobile** order

3. `docs/claude-memory/creative_direction_ui.md`
   - mark the four-state domain proposal **superseded**
   - preserve later freedom to refine UI wording without changing the three stored meanings

4. `docs/claude-memory/design_run_response_system.md`
   - remove the stale open-question note
   - reaffirm the shared three-level domain

5. `docs/claude-memory/design_effort_cost.md`
   - remove or replace the stale three-versus-four question

6. `docs/claude-memory/music_ingredients.md`
   - mark V1 planning complete
   - point implementation to the canonical plan
   - keep later music ingredients deferred

7. `docs/claude-memory/design_music_reply_style.md`
   - point to the canonical V1 contract
   - make the first bounded prompt slice next
   - reconcile "roughly ten" to the exact **`1–10` / `11+`** contract
   - historically reconciled the older lyric allowance with the July 27 prohibition; that
     prohibition was later reopened and superseded by the July 30 development-phase boundary
   - keep cross-run frequency implementation deferred

8. `docs/ui-design-brief-v1.md`
   - replace the stale open-question banner
   - record that the stored domain remains three-level while later presentation wording
     remains UI work

9. `README.md`
   - update the immediate next step and remove the stale open energy-domain question

10. `docs/claude-memory/parked_music_recommendation.md`
    - correct present evidence from "date/time" to **date only**
    - keep time-of-day and playback timestamps deferred

**Verified under the "do not edit unless a real contradiction exists" rule** — and both were
edited, because the final audit found a real sequencing ambiguity (each implied the Foundation
Review follows the prompt slice rather than **combined V1**):

- `docs/claude-memory/user_goals.md`
- `docs/claude-memory/requirements_nonfunctional.md`

**Do not edit:**

- `docs/ROADMAP.md`
- `docs/Archived/*`

**Deferred until the Java slice and now complete** — they describe as-built outbound behavior:

- `docs/AI_AGENT.md`
- `docs/DATA_PRIVACY.md`

**Created during the separately approved evaluation-surface step, before the first live call:**

- `docs/claude-memory/music_intelligence_v1_evaluation.md`

### Creative-policy and evaluation reconciliation — COMPLETED July 30, 2026

The first valid smoke supplied new evidence, so the creative policy was reopened through the
same consistency process used for any locked rule: canonical plan, prompt, tests, rubric, craft
reference, evaluation record, and status docs were revised together. The active record now:

- distinguishes the authentication-invalid fallback launch from the first valid smoke;
- records that the valid smoke failed quality rather than pretending evaluation never began;
- replaces the omission-first escape hatch with inclusion-first register calibration;
- replaces the one-music-sentence rule with the run-as-subject / one-coherent-interpretation
  boundary;
- replaces the blanket lyric ban with the development-phase short-accurate / no-extended-or-
  garbled boundary and an explicit pre-release revisit;
- refines taste from a blanket ban to no isolated grading, with earned relational
  interpretation allowed;
- records flexible craft tendencies, natural label handling, earned praise, difficult-run
  framing, and the organized-professional voice;
- records the safe unfamiliar-music acknowledgment, stateless variation wording, and S11
  fixture decontamination;
- updates the deterministic gate to 256 passing tests and makes a new 12-call smoke the next
  separately approved step.

`DATA_PRIVACY.md` required no disclosure change because the creative revision sends no new
data, adds no provider, and changes no retention or transmission boundary. Its stale statement
that `AI_AGENT.md` reproduced the exact current prompt was corrected; production source remains
`RunAgent.java`.

### Completion definitions

Three distinct states. They are not interchangeable, and the weaker two must never be
reported as the strongest.

**1. Planning complete — implementation not started**

- canonical plan approved
- all active-document contradictions reconciled
- canonical status / frontmatter updated
- Manley's approval
- planning commit created

This is the **intended clean pause point**.

**2. Prompt slice implemented — evaluation not complete**

- bounded production and deterministic-test changes complete
- all automated tests passing
- as-built privacy and AI-agent documentation updated

**This status must not be called combined V1 complete.**

**3. Combined Music Intelligence V1 complete**

- planning complete
- prompt slice implemented
- deterministic gate passing
- accepted sanitized fixtures
- smoke evaluation completed
- final 36-output evaluation completed
- **zero hard failures**
- quality thresholds met
- independent review reconciled
- Manley's final approval
- final status and handoff documentation complete

**Missing any single requirement means combined V1 is not complete.**

### Current status

`STRONGER-MODEL CONTROL COMPLETED AND FAILED ITS BAR — POST-CONTROL DESIGN REVISION UNDERWAY — NO LIVE RUN APPROVED`

- Planning remains committed at `0f22c99`; the original prompt slice at `949952c`.
- Sanitized fixtures, the opt-in `MusicIntelligenceEvaluationRunner`, the revised creative
  policy, and their deterministic guards are committed at `693bfb3`.
- The latest clean Maven gate after the stronger-model runner correction passes at **362 tests,
  0 failures, 0 errors, 0 skipped**. Surefire does not discover any opt-in evaluation runner;
  no Anthropic call occurs in the ordinary suite.
- One authentication-invalid launch produced only fallback text and is **not model evidence**.
- The first valid 12-call smoke completed with zero fallbacks but **failed product quality**:
  the voice was generic and music rarely participated, with small unsupported details. It is
  retained as a failed diagnostic, not acceptance evidence.
- That evidence led to an approved prompt-policy revision: inclusion-first music posture,
  three creative registers, a subject-not-sentence-count boundary, a wider music reference
  palette, short accurate lyric references in the development phase, earned relational taste
  interpretation, a specific creative-professional voice, flexible craft tendencies, earned
  praise, and better treatment of difficult runs.
- S11 was replaced so it no longer duplicates calibration example 4. Neutral acknowledgment
  and stateless-variation wording were also hardened.
- **The revised-prompt 12-call smoke then ran on July 30, 2026** against `693bfb3` and completed
  **12 calls with zero fallbacks**, but **failed product quality and trust**. Prose quality and
  music participation both improved over July 29 — the revision worked on the axis it targeted —
  but the model still asserted unsupported characteristics for named tracks (the S4
  unfamiliar-music fixture is the clearest failure), openings collapsed onto a single
  distance-plus-pace template, and closings returned to interchangeable coaching filler. It is
  retained as a failed diagnostic, not acceptance evidence.
- **A separate creative-ceiling probe ran later the same day** at `6cb3075` — 12 completed
  calls with a minimal creative system prompt as the single variable. **Minimal prompting
  improved the prose but did not produce reliable replies.** Both independent graders reached
  the pre-registered **0–3** band (Cowork 0 Hit / 1 Near-hit / 11 Miss; Codex 2 Hit /
  1 Near-hit / 9 Miss, preserved unreconciled) and both counted **nine hard-trust failures**,
  triggering the three-or-more override. Manley found **neither disputed reply app-worthy**,
  preferring S11-1 only if forced — a relative preference, **not** a Hit. The probe is
  diagnostic only and is never V1 acceptance evidence.
- **The separately approved Opus 5 control then completed July 31** with 12 usable replies.
  Manley's independent creative assessment was 1 Hit / 7 Near-hits / 4 Misses; strict
  application of the control's old trust-collapsing label rule yields 1 / 3 / 8. Six clear
  hard-trust failures trigger the override either way, so Opus alone did not meet the bar.
  The result did reveal a reachable creative core and shifted the next work toward voice,
  fusion, selection, compression, and clearer diagnostic grading.
- Raw evidence is preserved under `docs/claude-memory/evidence/`: the probe transcript and
  request bodies, revised-prompt smoke screenshots, and the Opus transcript plus shuffled
  grading packet.
- **No final 36-output evaluation has run. Combined Music Intelligence V1 is NOT complete.**

**Next gate:** place the approved replies into the low-fidelity expandable-card concept and decide
what should be visible initially versus after expansion. Reply length and density remain
provisional until that test. Any prompt/code/test handoff, live smoke, or 36-call final evaluation
still requires separate approval.
**Phase 0-A holdout-fixture design is likewise not approved.**

This status remains completion definition **2 — prompt slice implemented, evaluation not
complete**. Evaluation is now **underway**, not "not started." Neither this revision, nor any
smoke, nor the creative-ceiling probe may be reported as combined V1 complete.
