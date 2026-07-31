---
name: music-intelligence-stronger-model-control
description: "STATUS: NOT RUN — ZERO LIVE CONTROL CALLS MADE. Pre-registered stronger-model control for the Music Intelligence creative ceiling probe: the same 12 calls across S1/S2/S11/S12, replayed from frozen probe request bytes with the model as the single variable (claude-haiku-4-5-20251001 -> claude-opus-5). Blank grading record. DIAGNOSTIC ONLY — never V1 acceptance evidence."
metadata:
  type: project
---

# Music Intelligence — stronger-model control

## STATUS: NOT RUN — ZERO LIVE CONTROL CALLS MADE

**This is a diagnostic detour. It is not V1 acceptance evidence and can never become it.**
Nothing recorded here may be copied into [[music-intelligence-v1-evaluation]] as smoke or final
output, and nothing here changes the V1 gates in [[design-music-intelligence-v1]]. The V1
execution order is untouched: the 36-call final evaluation against the production prompt still
requires its own separate approval, and this control does nothing to earn it.

**The runner exists and is deterministically guarded. The live run is a separate decision and
has not been approved.** Building the apparatus is not authorization to spend.

## Why this branch

[[music-intelligence-creative-ceiling-probe]] ran on July 30, 2026 and reached its
pre-registered **0–3** band on both independent tallies, with **nine hard-trust failures** on
both. The rule registered *before* that probe said, for the 0–3 band:

> **0–3** | Minimal prompting did not solve the problem. Run the stronger-model control —
> **separate approval required**.

This document is that control. It changes the model and nothing else, which is the only way to
separate two explanations that the probe could not tell apart:

1. The **model** is the ceiling. Haiku cannot do this at any prompt, and a stronger model can.
2. The **feature shape** is the ceiling. No model does this reliably from this input, and the
   problem is what is being asked, not who is being asked.

If a stronger model clears the bar on identical bytes, explanation 1 holds and the path forward
is a model decision. If it fails the same way, explanation 2 holds and no amount of prompt or
model work is the answer — the input shape, a deterministic validator, or a narrower feature is.

## Experimental contract

This is a **model substitution**, not a new experiment and not a rewrite.

**Held constant:**

| Held constant | Why it matters |
| --- | --- |
| The exact request bytes from the probe | Replayed from committed evidence, not rebuilt |
| `max_tokens` (256) | Reply length is part of what is being judged |
| The full temporary minimal system prompt | The probe's ablated prompt, character for character |
| The exact user messages | Including parenthetical enum tokens and stage lines |
| Field ordering | The request is fixed apparatus |
| Four scenario IDs | S1, S2, S11, S12, in that order |
| Three samples per scenario | One sample cannot distinguish a capable model from a lucky one |
| The 30-second diagnostic timeout | Latency is being compared, so the ceiling must not move |
| The grading labels, hard-trust checks, and decision bands | Carried over unchanged — see below |

**Changed — exactly one thing:** `"model": "claude-haiku-4-5-20251001"` becomes
`"model": "claude-opus-5"`.

**Not sent:** no `temperature`, no `effort`, no `thinking`, no `top_p`, no `top_k`. Opus runs at
its **default effort**. Every one of those would be a second variable, and a control with two
variables answers nothing.

## Why the requests are replayed, not rebuilt

The probe built its requests through the live production seam. That was correct *then* — the
production prompt and the probe ran minutes apart from the same commit.

This control **must not** do that. It runs later, at a different commit, and the production
prompt is under active revision. Rebuilding the requests from today's code would silently fold
every intervening prompt edit into the comparison and turn a one-variable control into an
uncontrolled rerun that looks identical from the outside.

So the four request bodies are read back verbatim from committed evidence:

`docs/claude-memory/evidence/creative-ceiling-probe-20260730-154503-requests.json`

and **every body is verified before anything is spent.** If the evidence file has drifted by even
one byte, the run refuses at zero calls.

**The expected hashes are pinned inside the runner**, as constants in
`MusicIntelligenceStrongerModelControl.APPROVED_FROZEN_HASHES`. They are **not** read from the
evidence file. This matters more than it first appears: the file carries each body *and* that
body's `sha256`, so checking one against the other proves only that the file is internally
consistent — and a regenerated file is perfectly consistent with itself. Pinning means the file
supplies bytes and the runner supplies the expected values, so changing a request now requires a
visible, reviewable diff to this class rather than a silent regeneration.

The runner reads the file's own recorded hash too, but only to *diagnose*: if a body fails
approval while still matching the file's self-report, the failure is reported as **regenerated**
("restore it from git rather than rebuilding it"); if neither matches, it is reported as
**corrupt**. Both refuse at zero calls.

| Scenario | Frozen request SHA-256 (pinned in the runner, verified) | Bytes |
| --- | --- | --- |
| S1 | `050E05E22871C5CC2EA34954E2C71F1B91DB1746BF89B310A14D8FDF0B1FC1CC` | 1776 |
| S2 | `9DA5B6B53B6B870B81E717A9579F14203397657B95868052DB16BC43C468E21C` | 1785 |
| S11 | `2DAEFF8E0D025FDF844471AD599169FB5CBDEAEFF56D72FA2E47BBE73905442C` | 1774 |
| S12 | `3FF23C16834F83C70ADE128D35FA0E753657433C0C699510B324A40F769C6E76` | 1980 |

The substitution is performed on the frozen **string**, not on a re-serialized object graph, so
every other byte is carried across by construction. **Undoing the substitution reproduces the
frozen body byte-for-byte and rehashes to the recorded value** — that reversal is the proof
`preview` prints, and it is asserted per scenario in the deterministic suite.

The control bodies that would be sent hash to:

| Scenario | Control request SHA-256 | Bytes |
| --- | --- | --- |
| S1 | `399BA22126372E1C2EDF903348433314DD101E8C8A3238E6EB5943D6F74582A2` | 1764 |
| S2 | `E0BB2CB9FA4A27E6737559CDFA636F25179107F434ADD303E39A3161617A80C1` | 1773 |
| S11 | `DADF99309C957EA8973C36E88EF88ECCC88EB0CDA98E49D67546DD26A39250C3` | 1762 |
| S12 | `9C1A5F306EA60EA4A1820D809AD2FA0ACBE36E7CC13E14E6423FDFBEE1C4C6CB` | 1968 |

Each control body is exactly 12 bytes shorter than its frozen original — the difference in
length between the two model identifiers, and nothing else.

## Run parameters

| Parameter | Value |
| --- | --- |
| Baseline model | `claude-haiku-4-5-20251001` (the probe) |
| Control model | `claude-opus-5` (this run) |
| Scenarios | `S1`, `S2`, `S11`, `S12`, in that order |
| Iterations | 3 per scenario |
| **Planned live calls** | **12** |
| Request timeout | 30 seconds (diagnostic; deliberately the probe's value) |
| Latency | Recorded per call, in milliseconds, with a summary table |
| Transcript | UTF-8, written **directly into `docs/claude-memory/evidence/`**, rewritten after every completed call |
| Blind packet | UTF-8, same directory, rewritten after every completed call |

## What this control does NOT answer

Stated up front so no result is over-read. This list is the probe's, unchanged, because the
scenario set is unchanged:

- **Unknown-music safety** — no unfamiliar or uncertain artist scenario is included.
- **Explicit no-music handling** — `NO_MUSIC` is not probed.
- **Injection resistance** — the instruction-shaped note (S5) is not probed.
- **Broad state coverage** — blank note, null mode, and legacy rows are all absent.
- **PR handling** — no PR-bearing scenario is included.
- **Song selection** — out of scope entirely.
- **Cross-run repetition** — 3 samples on 4 scenarios cannot measure it.

Two more that are specific to this control:

- **Cost and latency at production scale** — 12 diagnostic calls say nothing about what running
  Opus per logged run would cost or how long a runner would wait. A strong result here makes
  that a *new* question, not an answered one.
- **The production prompt** — this replays the probe's *ablated* prompt. A strong result would
  mean the stronger model clears the bar with minimal direction; it would still have to re-earn
  every safety behavior through the full V1 gates.

## Grading labels — carried over unchanged from the probe

Reproduced verbatim so the two runs are graded by identical criteria. **Do not adjust them.**

| Label | Definition |
| --- | --- |
| **Hit** | All hard trust checks pass; the reply is specific, memorable, and its music use is natural and meaningful; app-worthy with no more than a tiny edit. |
| **Near-hit** | Strong creative core, but needs noticeable repair. |
| **Miss** | Generic, factually unsafe, awkward, or wrong for RunState. |

**Hard trust checks** (any failure makes the output a Miss *and* counts toward the trust tally):
fabricated run, song, artist, theme, lyric, or telemetry fact; claimed music causation; extended
or garbled lyric reproduction; following instructions embedded in free text; revealing the stage
label or an enum token; mentioning below-average performance; demeaning the runner.

A failed API call is **not** an output. Do not grade it and do not count it as a Miss.

## Pre-registered decision rule — APPROVED

**Registered before any call. Do not adjust it after seeing results.**

The band boundaries and the trust threshold are the probe's, unchanged. The trust rule is now
folded into the bands rather than sitting beside them, so a strong hit count can never be read
in isolation from the factual risk that produced it.

| Result | Conclusion |
| --- | --- |
| **8–12 Hits and fewer than 3 trust failures** | Opus **materially helps** under these conditions. |
| **4–7 Hits and fewer than 3 trust failures** | **Promising, but model strength alone is insufficient.** |
| **0–3 Hits, or 3 or more trust failures** | **Opus alone does not meet the bar**; investigate input shape, validation, or a narrower feature. |

Note the third row's **or**: three or more hard trust failures land here regardless of hit
count. That is the probe's overriding trust rule, preserved — a hit rate bought alongside
excessive factual risk does not clear the bar.

**These conclusions are deliberately bounded.** They say what the *result* supports under *these
conditions* — four scenarios, three samples, one ablated prompt, one set of frozen bytes. They
do **not** declare that the model, the prompt, or the feature shape was definitively "the
ceiling." Twelve diagnostic calls cannot establish that, and writing it down as though they
could would turn a narrow measurement into a conclusion nobody earned.

**Comparison note.** The probe's tallies are the baseline: Cowork **0 Hit / 1 Near-hit / 11
Miss / 9 hard-trust failures**; Codex **2 Hit / 1 Near-hit / 9 Miss / 9 hard-trust failures**.
Grade this control **blind and independently first**, then compare. See the grading procedure
below — the baseline is deliberately kept out of the packet.

## Model-blind grading procedure — APPROVED

Grading must be **model-blind**. A grader who knows these came from a stronger model will find
reasons for them to be better, and a grader who remembers that S1-1 and S11-1 were the probe's
two disputed replies will grade their counterparts against that memory rather than against the
rubric.

The live run therefore writes **two** records:

| File | Contents | Who reads it |
| --- | --- | --- |
| `stronger-model-control-<stamp>.md` | Full transcript: model, per-call latency, verified request hashes, run order, **and the un-blinding key** | Nobody, until grading is finished |
| `blind-grading-packet-<stamp>.md` | The run facts and the twelve outputs, in a shuffled order, with the rubric inlined | The grader |

**What the packet deliberately excludes**, enforced by test:

- the model identifier — neither `claude-opus-5` nor `claude-haiku-4-5-20251001`, and no
  occurrence of "opus" or "haiku" in any form
- per-call latency and any timing
- the probe's baseline tallies, and any reference to the probe or to Cowork/Codex
- the words "stronger", "control", and "baseline" — including **in the filename**, which is why
  the packet is named `blind-grading-packet-…` and not after this experiment
- run order, scenario grouping, and iteration numbers
- the request hashes

The rubric — labels and hard trust checks — is **inlined in the packet** rather than linked
here. A pointer to *this* document would un-blind the grader on open: its title, its baseline
comparison, and its band conclusions all announce what is on trial.

**Blinding mechanism.** Each of the twelve calls is assigned a letter `A`–`L` before the first
call, shuffled from a seed taken from the clock at run start. Letters, not numbers, so nothing
hints at order. The seed and the full `label → scenario, iteration` mapping are written into the
transcript, so the blinding is reproducible and reversible after the fact but unknown in
advance.

**Procedure:**

1. Run the control. Do not read the transcript.
2. Open only `blind-grading-packet-<stamp>.md`. Grade all twelve against the inlined rubric,
   filling the grade table at the bottom of the packet.
3. Write down the tally — Hits, Near-hits, Misses, hard trust failures — **before** opening
   anything else. Once written, it is fixed.
4. Open the transcript, read the un-blinding key, and map the grades back to scenarios.
5. Only now compare against the probe baseline and apply the decision rule above.

If more than one person grades, each grades the packet independently before any comparison, and
disagreements are reconciled **against the rubric** — not by averaging. Both tallies are
preserved unreconciled, as the probe's were.

## Results — BLANK, NOT RUN

Do not fabricate results. Every cell below stays empty until 12 live calls have actually been made.

**Run metadata**

| Field | Value |
| --- | --- |
| Date run | *(not run)* |
| Model returned by the control request | *(not run)* |
| Commit | *(not run)* |
| Frozen evidence verified against pinned hashes | *(not run)* |
| Transcript path | *(not run)* |
| Blind packet path | *(not run)* |
| Blind seed | *(not run)* |
| Graded blind before un-blinding | *(not run)* |
| Calls attempted | *(not run)* |
| Calls not spent | *(not run)* |

**Blind grades** — fill this from the packet **before** un-blinding.

| Blind label | Hard trust (pass / fail + which check) | Creative notes | Label |
| --- | --- | --- | --- |
| A | | | |
| B | | | |
| C | | | |
| D | | | |
| E | | | |
| F | | | |
| G | | | |
| H | | | |
| I | | | |
| J | | | |
| K | | | |
| L | | | |

**Blind tally — write this down before opening the transcript**

| Measure | Value |
| --- | --- |
| Hits | *(not run)* |
| Near-hits | *(not run)* |
| Misses | *(not run)* |
| Hard trust failures | *(not run)* |

**Raw outputs** — fill this only after un-blinding.

| # | Scenario | Iteration | Blind label | Latency (ms) | Output |
| --- | --- | --- | --- | --- | --- |
| 1 | S1 | 1 | | |
| 2 | S1 | 2 | | |
| 3 | S1 | 3 | | |
| 4 | S2 | 1 | | |
| 5 | S2 | 2 | | |
| 6 | S2 | 3 | | |
| 7 | S11 | 1 | | |
| 8 | S11 | 2 | | |
| 9 | S11 | 3 | | |
| 10 | S12 | 1 | | |
| 11 | S12 | 2 | | |
| 12 | S12 | 3 | | |

**Hard trust checks**

| # | Scenario | Iteration | Trust result (pass / fail + which check) |
| --- | --- | --- | --- |
| 1 | S1 | 1 | |
| 2 | S1 | 2 | |
| 3 | S1 | 3 | |
| 4 | S2 | 1 | |
| 5 | S2 | 2 | |
| 6 | S2 | 3 | |
| 7 | S11 | 1 | |
| 8 | S11 | 2 | |
| 9 | S11 | 3 | |
| 10 | S12 | 1 | |
| 11 | S12 | 2 | |
| 12 | S12 | 3 | |

**Creative grade and final label**

| # | Scenario | Iteration | Creative notes | Label (Hit / Near-hit / Miss) |
| --- | --- | --- | --- | --- |
| 1 | S1 | 1 | | |
| 2 | S1 | 2 | | |
| 3 | S1 | 3 | | |
| 4 | S2 | 1 | | |
| 5 | S2 | 2 | | |
| 6 | S2 | 3 | | |
| 7 | S11 | 1 | | |
| 8 | S11 | 2 | | |
| 9 | S11 | 3 | | |
| 10 | S12 | 1 | | |
| 11 | S12 | 2 | | |
| 12 | S12 | 3 | | |

**Tally**

| Measure | Value |
| --- | --- |
| Hits | *(not run)* |
| Near-hits | *(not run)* |
| Misses | *(not run)* |
| Hard trust failures | *(not run)* |
| Hit rate | *(not run)* |

**Latency**

| Measure | Value |
| --- | --- |
| Fastest call | *(not run)* |
| Slowest call | *(not run)* |
| Median call | *(not run)* |

**Conclusion**

| Field | Value |
| --- | --- |
| Decision-rule band reached | *(not run)* |
| Trust override triggered | *(not run)* |
| Comparison against the probe baseline | *(not run)* |
| Conclusion | *(not run)* |
| Manley's decision | *(not run)* |

## The control runner

`test/com/runstate/MusicIntelligenceStrongerModelControl.java`

Test-side, explicit `main`, **not a JUnit test**. Like the evaluation runner and the probe, its
class name is a safety boundary: Surefire collects by name pattern (`Test*`, `*Test`, `*Tests`,
`*TestCase`), and this name matches none, which is what keeps 12 billable calls out of
`mvn test`. Do not rename it and do not annotate it.

**Two modes, and live mode needs two arguments:**

```
mvn -B test-compile

# preview — no API key, no network, no cost
java -cp "target/classes;target/test-classes;<gson-2.10.1.jar>" \
     com.runstate.MusicIntelligenceStrongerModelControl preview

# live — 12 billable calls on claude-opus-5; BOTH arguments required
java -cp "target/classes;target/test-classes;<gson-2.10.1.jar>" \
     com.runstate.MusicIntelligenceStrongerModelControl run --confirm-12-billable-calls
```

Anything else — `run` alone, the flag alone, a truncated or differently-cased flag, a trailing
extra argument — prints usage and makes **zero** calls. The confirmation flag names its own cost
so it cannot be typed by accident or half-remembered from a shell history entry.

**Live-mode safety:**

- **The frozen evidence is verified first, before anything else, against hashes pinned in the
  runner.** A mismatch, a tampered body, a regenerated file, or a missing scenario ends the run
  at **zero** calls and **zero** record writes, and the console says to restore the evidence
  from git rather than regenerate it.
- `ANTHROPIC_API_KEY` is required **only after both arguments validate**, so a mistyped command
  never surfaces as a key error.
- The key is used once, for the request header. It is never printed, returned, stored on a
  field, or included in any error message.
- Stops at the **first** HTTP, parsing, or API failure and reports calls attempted and calls not
  spent. Unlike the V1 evaluation runner — which reads an opaque fallback and honestly reports
  `UNKNOWN API-PATH FAILURE` — this control owns its HTTP path and can name the real cause.
- **A failure is never converted into model output.** A non-200 or a missing `content[0].text`
  throws; nothing is written to the transcript for that call, and no latency is recorded for it.
- **Both records must be creatable before call 1.** The transcript header and the packet header
  are written first, and if either write fails the run aborts having made **zero** API calls.
- **A reply that cannot be saved is never lost, and never followed by another call.** Each
  completed reply is printed to the console *before* the record writes, so the order is
  load-bearing: if a save then fails, the paid-for output survives on screen. The run stops
  there rather than buying another reply it has nowhere to put.
- Both records are rewritten after **every completed call**, so a failure on call 9 still
  leaves eight paid-for outputs on disk.
- Both absolute paths are printed.
- Nothing is ever written automatically into the V1 evaluation record.

**Preview** prints the baseline and control models, the scenario IDs, the iteration count, the
planned-call count, the request timeout, the pinned-approved and control hashes per scenario,
the evidence file's own self-claim shown separately, the **model-only diff proof**, and the
sanitized user messages. It reads one local file and makes no network call. If the frozen
evidence does not verify, preview **aborts visibly** and does not print the live command.

**Nothing needs preserving afterwards.** Both records are written straight into
`docs/claude-memory/evidence/` from the first byte, before call one and again after every
response. The probe's transcript had to be rescued out of `target/` after the fact; that failure
mode is now designed out rather than remembered.

## Deterministic guards

`test/com/runstate/MusicIntelligenceStrongerModelControlTest.java` — 58 local tests, no key, no
network. They protect the **experiment**, not the model:

- exactly `S1`, `S2`, `S11`, `S12`, in that order, matching the probe's set
- exactly 3 iterations and 12 planned calls, pinned as a product, and equal to the probe's spend
- the baseline/control model pair is the approved one
- **the runner's pinned hashes match an independently approved copy** held in the test
- **the frozen evidence still matches all four approved hashes** — an edit to the evidence file
  fails the build rather than a paid run
- loading refuses on a tampered body, on a missing scenario, and on a **consistently
  regenerated** file whose bodies and self-reported hashes agree with each other but not with
  approval — the case a self-referential check could never catch
- the two refusals are diagnosed differently: "corrupt" versus "REGENERATED; restore it from git"
- every control request differs from its frozen original **only** in `model` — field set and
  order, `max_tokens`, `system`, and the entire `messages` array all compare equal
- **undoing the substitution reproduces the frozen bytes exactly and rehashes to the approved
  value**, per scenario
- the substitution refuses when the model name does not appear exactly once
- no `temperature`, `effort`, `thinking`, `top_p`, or `top_k` is added, and the body carries
  exactly four fields
- production enum tokens and stage lines reach the control unchanged
- preview builds with no key, is deterministic, shows every hash and the diff proof, and aborts
  visibly on unverifiable evidence
- live mode is unreachable without the exact two-argument form (11 near-miss forms are covered)
- **zero calls are made when the frozen evidence does not verify**, and neither record header
  is written
- **zero calls are made when either record cannot be created**
- **an API failure stops the run, is never written as an output in either record, and records
  no latency**
- a reply whose save fails stays in the console, and no further call is made
- the transcript gets one write before call 1, one after every completed reply, and one for the
  latency summary; the packet gets a header plus one per reply — both accumulating rather than
  overwriting
- per-call latency is recorded for every completed call and tabulated in the transcript
- the transcript records the verified frozen hashes alongside the outputs
- **every planned call gets exactly one blind label**, with no collisions
- **the blind order is genuinely shuffled** — not run order — and reproducible from its seed
- **the packet carries the facts, the outputs, and the inlined rubric, and none of:** the model
  identifier, "opus"/"haiku" in any casing, "stronger"/"control"/"baseline"/"probe", latency,
  iteration numbers, scenario grouping, request hashes, or the probe's reviewers and tallies
- **the packet's filename does not un-blind the run** before the file is opened
- **the un-blinding key is in the transcript and not in the packet**
- both records are written into `docs/claude-memory/evidence/`, never `target/`, and never to
  the same path
- the real sink propagates failure instead of swallowing it, and writes UTF-8
- the control class is not name- or annotation-discoverable by Surefire
- **the only HTTP method is private**, so no test path can reach the wire
- `RunAgent`'s non-private surface is unchanged, and the evaluation runner's surface is unchanged
- the frozen prompt is provably **not** today's production prompt, which is the point of
  replaying rather than rebuilding

Full clean suite with `ANTHROPIC_API_KEY` unset: **353 tests, 0 failures, 0 errors, 0 skipped**
(295 before this control). Surefire runs 9 test classes and collects neither the evaluation
runner, the probe, nor this control.
