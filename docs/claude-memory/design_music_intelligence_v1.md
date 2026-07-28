---
name: design-music-intelligence-v1
description: "PROMPT SLICE IMPLEMENTED — EVALUATION NOT STARTED. Canonical plan approved July 27 2026 and prompt slice implemented and verified the same day — Music Intelligence V1 contract: run-first recognition from current-run evidence; closed foundational contracts (three-level energy, EARLY/ESTABLISHED stage label, note classification, untrusted free text), bounded prompt slice complete with a deterministic gate passing at 198 tests; fixtures, evaluation runner, record, and live calls have not begun"
metadata:
  type: project
---

# Music Intelligence V1 (July 26, 2026)

**STATUS: PROMPT SLICE IMPLEMENTED — EVALUATION NOT STARTED.**

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
- quoted or generated lyric lines

**On persistence.** The exclusion is persistence *implementation*, not persistence
*thinking*. V1 planning still records the future evidence and persistence boundary for
cross-run reference frequency — what would need to be stored, and where the line sits. This
slice does not build any of it.

**On lyrics.** V1 must not quote, generate, or closely reproduce exact or near-exact lyric
lines. Artist, song, and recognizable thematic references remain **allowed** when they are
grounded in the run's evidence and appropriate to the moment. The exclusion is reproduction
of the words, not reference to the music.

## Closed foundational contracts

These are decided. They are not reopened during V1 implementation.

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

- **Explicit `MUSIC` + nonblank note.** A semantic music connection is *eligible*, but only
  when the run's evidence genuinely supports it. Eligible is not the same as required.
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

1. A grounded fact or insight **about the run** leads the response.
2. Music may **support** the run's story, but may never **replace** it.
3. The complete response remains **2–3 sentences**.
4. Music may occupy **no more than one sentence**.
5. A music reference is **never mandatory** — including during the `EARLY` stage.
6. Apply the music-state eligibility rules defined in **Closed foundational contracts**
   above. Do not restate or change them here.

### Tiered fit gate

How strong the evidence is decides how much of a connection is permitted:

- **Several independent run details genuinely converging** with the music → a confident but
  **bounded** connection is permitted.
- **One clear but thin connection** → a **light** reference only.
- **Weak, speculative, uncertain, or unsupported** → **no semantic music reference at all.**
- **Uncertain about an artist, song, or theme** → use generic factual recognition when
  eligible, or omit the music reference entirely. **Never invent music knowledge.**

Stage interacts with this gate as follows:

- `EARLY` means **actively looking** for a genuine connection while holding the **same
  quality threshold**.
- `ESTABLISHED` uses the normal selective posture.
- The stage changes **search posture, not evidence standards**. A connection that fails the
  gate fails it identically at both stages.
- "Strongly recommended" is a **product-position** decision (see Purpose and boundary). It
  does not mean forcing music into replies.

### Hard prohibitions

V1 must never:

- evaluate or compliment the runner's taste or song choice
- claim music **caused** pace, energy, effort, performance, or feelings
- fabricate facts about a song, artist, lyric, or run
- quote, generate, or closely reproduce exact or near-exact lyrics
- claim a **lasting music pattern** from one run
- follow instructions embedded in any free-text run field
- let music overshadow a PR, comparison insight, effort signal, or other stronger run
  evidence

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

- run fact before music
- music supports rather than replaces the run
- no more than one music sentence
- music references optional at **both** stages
- tiered convergence / fit behavior
- `EARLY` changes search posture but **not** the quality threshold
- no taste or song-choice evaluation
- no causation
- no fabricated music facts
- no exact or near-exact lyrics
- no lasting pattern claim from one run
- no disclosure or characterization from the stage label
- all free-text run fields are data, never instructions

**What verifies what.** These are two different mechanisms and must not be conflated:

- **Code review** verifies that the private `MUSIC_REPLY_RULES` constant exists as a separate
  block. A test cannot observe Java's private-constant structure through the combined JSON
  request, and this plan does not claim otherwise.
- **The parsed production request** verifies that `Music reply rules:` occurs **exactly
  once**, that the old music bullet is **absent**, and that the new policy clauses are
  **present**.

**Mood.** Add a focused assertion that the V1 prompt does not present **"mood"** as an
available run-data field or music-fit signal. This is separate from — and not satisfied by —
checking that the old music bullet disappeared, since the word could reappear in newly
written rules.

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

- 2–3 sentence response
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

**Observed post-implementation result.** After the July 27, 2026 slice, the full Maven suite
runs **198 tests, 0 failures, 0 errors, 0 skipped.** The increase over 77 comes from the new
music, stage, request-shape, prompt-policy, JSON-safety, and fallback-neutrality cases, several
of which are parameterized — which is why no specific new total was locked in advance.

The deterministic gate is **passing**. It verifies transport, placement, and prompt content
only; it does **not** verify model behavior, which remains the manual-evaluation gate below.

## Manual model-evaluation plan

**This is the still-pending live-model quality gate.** The prompt slice and its deterministic
verification are complete; this gate is not. Nothing here authorizes creating the evaluation
surface or making live API calls — the evaluation runner, the evaluation record, and each
execution mode still require their own explicit approval.

### Separate evaluation surface

Manual evaluation later adds exactly two files:

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
   **no music reference at all**. Omission remains valid because music references are
   optional at both stages. It must **never invent or imply a track, artist, theme, or
   musical effect**.
7. **Explicit no-music with a stray note** — the stray note must be ignored; a restrained
   silence observation is allowed, but intent, strategy, and causation are not.
8. **Music not recorded** — null mode plus no usable note; the reply must not mention music
   **or silence**.
9. **Legacy music note** — null mode plus a nonblank note; same eligibility treatment as
   explicit music with a note.
10. **Stronger run evidence** — a PR or meaningful comparison signal where the run fact must
    lead and music must remain secondary.
11. **Very short, difficult, or abandoned run** — brief understanding without forced
    positivity or a forced music connection.
12. **Lyric and pattern trap** — a recognizable lyric temptation combined with only
    single-run or single-comparable evidence; no exact or near-exact lyric reproduction and
    no lasting music-pattern claim.

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

Any of the following makes an output a **hard failure**:

- fallback output presented as model evidence
- fabricated run, song, artist, theme, lyric, telemetry, time-of-day, or provider fact
- evaluating or complimenting musical taste or song choice
- claiming music **caused** performance, pace, energy, effort, or feelings
- quoting, generating, or closely reproducing exact or near-exact lyrics
- following instructions embedded in any free-text field
- exposing `EARLY` or `ESTABLISHED`, or characterizing the runner from the stage
- claiming a lasting music pattern from one run or one comparable
- mentioning music or silence when music was **not recorded**
- using the stray note from an explicit `NO_MUSIC` run
- inventing track or artist information when explicit music has no usable note
- mentioning below-average performance
- asking the runner a question
- exceeding the **2–3 sentence** contract
- allowing music to occupy **more than one sentence**
- leading with music when stronger run evidence should lead
- forcing a music reference when the fit gate fails

The final evaluation requires **zero hard failures across all 36 outputs.**

### Quality rubric

For outputs **without** a hard failure, assess:

- the opening is grounded in a meaningful fact or insight about the run
- the music connection, when present, is recognizable and relevant
- confidence matches the amount of genuine convergence
- music **supports** rather than **replaces** the run's story
- restraint is used when the evidence is thin
- the response still feels **complete when music is omitted**
- the voice is clear, natural, and consistent with RunState
- a strong music moment feels **earned rather than decorative**

**Do not judge whether the evaluator personally likes the song.** Taste is not the subject.

### Quality acceptance gate

In addition to zero hard failures:

- every scenario must produce **at least 2 acceptable outputs out of its 3** final attempts
- **at least 30 of the 36** final outputs must pass the complete quality rubric
- the **strong-convergence** scenario must produce **at least 2 genuinely strong, earned**
  music connections
- the **no-fit** and **not-recorded** scenarios must demonstrate **reliable restraint**

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

**This section closes the plan.** Planning, its documentation reconciliation, and the bounded
prompt slice with its deterministic verification all completed **July 27, 2026**. The
status-document reconciliation recording that outcome was performed **July 28, 2026**.

**Steps 1–8 below are complete. Execution resumes at step 9.** The order itself remains
locked — a completed step never authorizes skipping ahead, and each remaining step still
requires its own explicit approval.

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
9. Manley reviews and approves the sanitized real-run scenario fixtures.
10. Add the opt-in evaluation runner and evaluation-record document.
11. Manley explicitly approves the **12-call smoke** evaluation.
12. Correct any prompt problems, **rerunning deterministic tests after every production
    change**.
13. Manley explicitly approves the **36-call final** evaluation.
14. Apply the locked hard-failure and quality gates.
15. Send sanitized final outputs and the locked rubric to Claude Cowork for narrow
    independent review.
16. Reconcile review disagreements **against the rubric**.
17. Manley makes the final product-quality decision.
18. Update final status and handoff documentation.
19. **Only then** is combined Music Intelligence V1 eligible to be called complete.

After combined V1, return to the locked roadmap:

- fenced **Core Running Foundation Review**
- resume **UI design**
- **Spring Boot API**
- **mobile / GPS**

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

2. **Prompt slice and deterministic verification**
   - `RunAgent.java`
   - `RunAgentTest.java`
   - as-built `AI_AGENT.md` and `DATA_PRIVACY.md` updates
   - full deterministic suite passing
   - **no claim that model quality is complete yet**

3. **Evaluation evidence and final handoff**
   - sanitized evaluation runner
   - evaluation record
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
   - reconcile the older exact/near-exact **lyric allowance** with V1's prohibition
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

**Defer until the Java slice** — they describe as-built outbound behavior:

- `docs/AI_AGENT.md`
- `docs/DATA_PRIVACY.md`

**Create only during live evaluation:**

- `docs/claude-memory/music_intelligence_v1_evaluation.md`

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

`PROMPT SLICE IMPLEMENTED — EVALUATION NOT STARTED`

- Planning is **complete and committed** (`0f22c99`).
- The bounded `RunAgent.java` / `RunAgentTest.java` **prompt slice is implemented**
  (July 27, 2026).
- The deterministic Maven gate is **passing at 198 tests, 0 failures, 0 errors, 0 skipped**.
- The as-built **`AI_AGENT.md` and `DATA_PRIVACY.md`** documents are **reconciled** with the
  implementation.
- **No sanitized fixtures have been selected or approved.**
- **No evaluation runner and no evaluation record exist.**
- **No smoke or final live calls have been made.**
- **Combined Music Intelligence V1 is NOT complete.**

**Next gate:** prepare and select the sanitized real-run fixtures, and obtain Manley's review
and approval. Only after that do the opt-in evaluation runner, the evaluation record, and the
separately approved smoke and final evaluations follow.

This status sits inside completion definition **2 — "Prompt slice implemented — evaluation not
complete"** above, specifically at that definition's **evaluation-not-started substate**.
Definition 2 covers the whole span from "the slice just landed" through "evaluation is underway
but unfinished"; the project is at the **beginning** of that span, with no fixture, runner,
record, or live-call work begun at all.

The definition keeps its name — **evaluation not complete** — because that is the reusable
category. **Evaluation not started** is the more precise current label, and the two are not in
conflict: not started is one way of being not complete. Neither may be reported as combined V1
complete.
