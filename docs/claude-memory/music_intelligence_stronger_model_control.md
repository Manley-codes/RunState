---
name: music-intelligence-stronger-model-control
description: "STATUS: COMPLETED AND GRADED JULY 31, 2026. Pre-registered stronger-model control for the Music Intelligence creative ceiling probe: 12 completed Opus 5 calls across S1/S2/S11/S12. Manley's independent creative assessment was 1 Hit / 7 Near-hits / 4 Misses; the control's old rule that converted every trust failure to a Miss yields a strict historical tally of 1 / 3 / 8. Six clear hard-trust failures trigger the override either way, so Opus alone does not meet the bar. DIAGNOSTIC ONLY — never V1 acceptance evidence."
metadata:
  type: project
---

# Music Intelligence — stronger-model control

## STATUS: COMPLETED AND GRADED — 12 USABLE OUTPUTS, JULY 31, 2026

The corrected control completed all **12 planned Opus 5 calls** on July 31, 2026. Manley's
independent creative assessment was **1 Hit / 7 Near-hits / 4 Misses**. Under the written
historical rule that converted every trust failure to a Miss, the strict tally is **1 / 3 / 8**.
Six replies contain clear unsupported details. The result therefore reaches the pre-registered
**0–3 Hit** band and also triggers the **three-or-more trust-failure override**: **Opus alone
does not meet the bar under these conditions.**

The result still changed the diagnosis. Seven Near-hits show that the creative core is often
present. The repeated weaknesses are over-explained music connections, formal or literary
delivery, automatic artist/title naming, and insufficient editing judgment. The next work is to
control and compress that creativity without flattening it. No further live call is approved.

### Earlier failed attempt

One live attempt on 2026-07-31 reached Opus 5, spent **one billable call**, produced **no model
output**, and stopped — saving the other eleven. The cause was a response-parsing defect in the
runner, not a model result. It has been corrected and is covered by tests. **Nothing has been
graded, and there is nothing to grade.** See *Attempt history* below.

**This is a diagnostic detour. It is not V1 acceptance evidence and can never become it.**
Nothing recorded here may be copied into [[music-intelligence-v1-evaluation]] as smoke or final
output, and nothing here changes the V1 gates in [[design-music-intelligence-v1]]. The V1
execution order is untouched: the 36-call final evaluation against the production prompt still
requires its own separate approval, and this control does nothing to earn it.

**The corrected runner later completed the separately approved 12-call control recorded below.**
That approval covered only this completed run; it is not authorization for another live call.

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
| The full temporary minimal system prompt | The probe's ablated prompt, character for character |
| The exact user messages | Including parenthetical enum tokens and stage lines |
| Field ordering | The request is fixed apparatus |
| Four scenario IDs | S1, S2, S11, S12, in that order |
| Three samples per scenario | One sample cannot distinguish a capable model from a lucky one |
| The 30-second diagnostic timeout | Latency is being compared, so the ceiling must not move |
| The grading labels, hard-trust checks, and decision bands | Carried over unchanged — see below |

**Changed — exactly two things, both approved:**

1. `"model": "claude-haiku-4-5-20251001"` becomes `"model": "claude-opus-5"`.
2. `"max_tokens":256` becomes `"max_tokens":4096`.

**Why max_tokens had to move.** Opus 5 has thinking enabled by default, and its thinking and its
visible reply draw on the **same** `max_tokens` budget. The first live attempt discovered this
the expensive way: at 256, the model spent its entire allowance thinking and returned no text
block at all. Holding 256 constant would not have been a cleaner control — it would have been a
control that cannot produce an output to grade.

This is a **compatibility correction, not a tuning change**. The 2–3 sentence contract is
unchanged and still enforced by the prompt, which the model is still held to. `max_tokens` is a
ceiling, not a target, and a reply that sprawls past the contract is still a rubric failure.

**Not sent:** no `temperature`, no `effort`, no explicit `thinking` field, no `output_config`,
no `top_p`, no `top_k`. Opus runs at its **default effort**. Every one of those would be an
unapproved third variable.

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

**The frozen bodies and their pinned hashes are unchanged by the `max_tokens` correction.** The
substitutions are applied to the frozen string at send time; the stored evidence still carries
`"max_tokens":256` and still hashes to the values above. Nothing in `evidence/` was rewritten.

Both substitutions are performed on the frozen **string**, not on a re-serialized object graph,
so every other byte is carried across by construction. **Undoing both substitutions reproduces
the frozen body byte-for-byte and rehashes to the pinned approved value** — that reversal is the
proof `preview` prints, and it is asserted per scenario in the deterministic suite.

The control bodies that would be sent hash to:

| Scenario | Control request SHA-256 | Bytes |
| --- | --- | --- |
| S1 | `1F0C7528E6E25F80887FE37746CE53AEABBCBF429A711B3064A103754E5FB42B` | 1765 |
| S2 | `44C62EED698AE84429872BD77A150922D67AAAFADF8F0A42924906B189A88790` | 1774 |
| S11 | `45F7B2E83FD656C764A4E1389F5F3901391DB1C0ED9D703035BD7617D20ECAFB` | 1763 |
| S12 | `4B0C8A77E5FD72A0F0871B3E79DBCD5F3CB190D8B9F1B0BA58E533E3E9EB2537` | 1969 |

Each control body is exactly 11 bytes shorter than its frozen original: −12 from the shorter
model identifier, +1 from `256` becoming `4096`, and nothing else. That arithmetic is asserted
per scenario, so a third edit of any size would fail the build.

## Run parameters

| Parameter | Value |
| --- | --- |
| Baseline model | `claude-haiku-4-5-20251001` (the probe) |
| Control model | `claude-opus-5` (this run) |
| Scenarios | `S1`, `S2`, `S11`, `S12`, in that order |
| Iterations | 3 per scenario |
| **Planned live calls** | **12** |
| `max_tokens` | **4096** (the probe sent 256; see the compatibility correction above) |
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

## Attempt history

### 2026-07-31 01:58:25 — FAILED, INFRASTRUCTURE EVIDENCE ONLY

**This is not model evidence. There is nothing here to grade, and nothing here may be graded.**

| Field | Value |
| --- | --- |
| Executed on | July 31, 2026, 01:58:25 local |
| Model reached | `claude-opus-5` — the call succeeded in reaching the model |
| Frozen evidence verified | Yes, all four against the pinned hashes |
| Live calls attempted | **1** |
| Calls saved by the fail-fast stop | **11** |
| Usable model outputs | **0** |
| Failure | `API FAILURE: response carried no content[0].text` |
| Cause | The runner assumed `content[0]` was a text block. Opus 5 has thinking enabled by default, so a thinking block led the array and `content[0].text` did not exist. |
| Grading status | **Not graded and never to be graded.** No output was produced. |

**Preserved evidence — do not delete, overwrite, or grade:**

- `docs/claude-memory/evidence/stronger-model-control-20260731-015825.md`
- `docs/claude-memory/evidence/blind-grading-packet-20260731-015825.md`

The transcript carries its verified hashes and its un-blinding key; the packet reads
`Outputs recorded so far: 0 of 12` with an empty grade table. Both are kept exactly as the run
left them. They are the record of an **infrastructure failure**, and their value is that they
prove what did *not* happen: the frozen requests verified, the records were created before call
one, one call was made, and the run stopped rather than spending the other eleven against a
broken response path.

**What it cost and what it bought.** One billable call, no usable output. In exchange it found
a defect that would otherwise have consumed all twelve, and it surfaced the `max_tokens`
interaction — thinking and reply sharing one budget — that no amount of local testing would have
revealed. The fail-fast design worked exactly as specified.

**Corrections made in response** (see the runner section below):

1. The whole `content` array is parsed; every `text` block is appended in order. Thinking blocks
   are skipped, and their contents and signatures are never read, recorded, or reported.
2. A response with no text block fails safely, reporting only `stop_reason`, the content block
   types, and output-token usage — never the raw response, thinking text, request body, or key.
3. `max_tokens` for the control request moves 256 → 4096.

**The blind seed and un-blinding key in the failed transcript are spent.** The next attempt
generates its own; do not reuse them.

## Results — COMPLETED AND GRADED

The raw transcript and the shuffled grading packet are preserved in `evidence/`. Manley's notes
under each shuffled reply remain unchanged; the tables below organize those judgments and the
later trust review without rewriting the raw record.

**Run metadata**

| Field | Value |
| --- | --- |
| Date run | 2026-07-31 02:34:33 local |
| Model returned by the control request | `claude-opus-5` |
| Commit | `1efb68a` |
| Frozen evidence verified against pinned hashes | Yes — all four approved hashes matched before the calls |
| Transcript path | `docs/claude-memory/evidence/stronger-model-control-20260731-023433.md` |
| Blind packet path | `docs/claude-memory/evidence/blind-grading-packet-20260731-023433.md` |
| Blind seed | `55384303551600` |
| Graded blind before un-blinding | The shuffled packet was graded before the output mapping was opened; the model identity was already known |
| Calls attempted | 12 |
| Calls not spent | 0 |

**Blind grades** — fill this from the packet **before** un-blinding.

| Blind label | Hard trust (pass / fail + which check) | Creative notes | Label |
| --- | --- | --- | --- |
| A | Pass | Weather opening had value; label use and ELEMENT. wordplay felt forced | Miss |
| B | Pass | Strong comparison idea; labels felt inserted and music ran too long | Near-hit |
| C | Fail — invented dust | Energy shift worked; opening and Eminem explanation need compression | Near-hit |
| D | Fail — invented uneven ground | “Charged full price” worked; the rest was over-described | Near-hit |
| E | Pass | No usable portion identified | Miss |
| F | Pass | Strong opening and energy movement; song explanation too long | Near-hit |
| G | Pass | Smooth opening; title explanation weak and overlong | Near-hit |
| H | Fail — invented first-step heaviness and breathing | One heat/trail fragment had potential; Drake idea unclear | Miss |
| I | Pass | Specific, productive, complete arc; music ending worked | Hit |
| J | Fail — unsupported steady-pace claim | Framing did not fit; comparison too long and music forced | Miss |
| K | Fail — invented mile-and-a-half event | Concise comparison; music needs cleaner fusion | Near-hit |
| L | Fail — invented “no one to impress” | Run framing worked; Kendrick connection began too literally | Near-hit |

**Blind tally — write this down before opening the transcript**

| Measure | Value |
| --- | --- |
| Hits | 1 |
| Near-hits | 7 |
| Misses | 4 |
| Hard trust failures | 6 |

**Raw outputs** — fill this only after un-blinding.

| # | Scenario | Iteration | Blind label | Latency (ms) | Output |
| --- | --- | --- | --- | --- | --- |
| 1 | S1 | 1 | C | 8753 | See the exact S1 iteration 1 text in the preserved transcript |
| 2 | S1 | 2 | F | 10086 | See the exact S1 iteration 2 text in the preserved transcript |
| 3 | S1 | 3 | I | 10085 | See the exact S1 iteration 3 text in the preserved transcript |
| 4 | S2 | 1 | D | 6255 | See the exact S2 iteration 1 text in the preserved transcript |
| 5 | S2 | 2 | L | 5857 | See the exact S2 iteration 2 text in the preserved transcript |
| 6 | S2 | 3 | A | 7449 | See the exact S2 iteration 3 text in the preserved transcript |
| 7 | S11 | 1 | G | 8633 | See the exact S11 iteration 1 text in the preserved transcript |
| 8 | S11 | 2 | H | 7712 | See the exact S11 iteration 2 text in the preserved transcript |
| 9 | S11 | 3 | E | 9175 | See the exact S11 iteration 3 text in the preserved transcript |
| 10 | S12 | 1 | J | 10765 | See the exact S12 iteration 1 text in the preserved transcript |
| 11 | S12 | 2 | B | 6749 | See the exact S12 iteration 2 text in the preserved transcript |
| 12 | S12 | 3 | K | 9773 | See the exact S12 iteration 3 text in the preserved transcript |

**Hard trust checks**

| # | Scenario | Iteration | Trust result (pass / fail + which check) |
| --- | --- | --- | --- |
| 1 | S1 | 1 | **Fail** — invented dust |
| 2 | S1 | 2 | Pass |
| 3 | S1 | 3 | Pass |
| 4 | S2 | 1 | **Fail** — invented uneven ground |
| 5 | S2 | 2 | **Fail** — invented the motivation “no one to impress” |
| 6 | S2 | 3 | Pass |
| 7 | S11 | 1 | Pass |
| 8 | S11 | 2 | **Fail** — invented first-step heaviness and the runner's breathing |
| 9 | S11 | 3 | Pass |
| 10 | S12 | 1 | **Fail** — asserted a steady pace throughout without evidence |
| 11 | S12 | 2 | Pass |
| 12 | S12 | 3 | **Fail** — invented a mile-and-a-half event |

**Creative grade and final label**

| # | Scenario | Iteration | Creative notes | Label (Hit / Near-hit / Miss) |
| --- | --- | --- | --- | --- |
| 1 | S1 | 1 | Energy shift worked; opening and music explanation need compression | Near-hit |
| 2 | S1 | 2 | Strong opening and movement; music explanation too long | Near-hit |
| 3 | S1 | 3 | Specific and productive; complete arc with a working music ending | Hit |
| 4 | S2 | 1 | One sharp line surrounded by too much description | Near-hit |
| 5 | S2 | 2 | Good run framing; music should be fused rather than explained | Near-hit |
| 6 | S2 | 3 | Forced label use and wordplay | Miss |
| 7 | S11 | 1 | Smooth opening; title explanation needs compression | Near-hit |
| 8 | S11 | 2 | Unclear, overworked Drake connection | Miss |
| 9 | S11 | 3 | No usable portion identified | Miss |
| 10 | S12 | 1 | Framing and music did not fit | Miss |
| 11 | S12 | 2 | Strong comparison idea; too long and label-heavy | Near-hit |
| 12 | S12 | 3 | Concise comparison; music explanation needs cleaner fusion | Near-hit |

**Tally**

| Measure | Value |
| --- | --- |
| Hits | 1 |
| Near-hits | 7 |
| Misses | 4 |
| Hard trust failures | 6 |
| Hit rate | 8.3% |

**Latency**

| Measure | Value |
| --- | --- |
| Fastest call | 5857 ms |
| Slowest call | 10765 ms |
| Median call | 8693 ms |

**Conclusion**

| Field | Value |
| --- | --- |
| Decision-rule band reached | 0–3 Hits — 1 Hit |
| Trust override triggered | Yes — 6 hard-trust failures |
| Comparison against the probe baseline | More Near-hits and fewer Misses, but still unreliable and below the approved bar |
| Conclusion | Opus shows that the creative core is reachable, but model strength alone does not solve structure, editing judgment, or trust |
| Manley's decision | Do not accept or ship these replies; use the grading evidence to design the next bounded revision |

### Grading-method clarification — added after the control

The pre-registered rule above explicitly said that any hard-trust failure also changes the
output's label to **Miss**. Manley did not use the labels that way while grading: he judged the
creative value of the writing and later reviewed factual trust separately. That is why four
replies with trust failures remain Near-hits in the recorded creative table.

Both views are preserved rather than silently rewriting the experiment:

| View | Hit | Near-hit | Miss | Trust failures |
| --- | ---: | ---: | ---: | ---: |
| Manley's diagnostic creative assessment | 1 | 7 | 4 | 6, recorded separately |
| Strict application of the old control rule | 1 | 3 | 8 | 6 |

The experimental conclusion is unchanged: both views remain in the 0–3 Hit band, and six trust
failures independently trigger the override.

**Future diagnostics do not reuse the collapsed rule.** They record three separate judgments:

1. **Creative value:** Strong / Promising / Weak.
2. **Trust:** Pass / Fail—removable / Fail—load-bearing. “Removable” means the unsupported
   detail can be deleted without destroying the core creative move; “load-bearing” means the
   creative move depends on the unsupported claim.
3. **Ready for the app:** Yes / No. Any trust failure makes the current wording **No** until it
   is repaired, but it does not erase what the creative assessment can teach us.

This is a post-control methodology improvement. It does not retroactively change the approved
decision bands or turn any diagnostic output into acceptance evidence.

### What the control taught us

- The main creative problem is no longer an absence of ideas. It is **selection and compression**:
  the model often finds a useful connection and then explains it until it becomes heavy.
- The target voice is not formal or scholarly. It is fun, run-connected, deliberate, and polished:
  **creative wording that lands cleanly**.
- Music and run facts should often arrive fused. Do not narrate what the music means and then
  explain why it fits. Use a recognizable shard — title, persona, or concept — inside the run.
- Cleverness and clarity work together. A connection may take a quick beat to click, but it
  should not require rereading or an explanation, and it should not be flattened into literal
  wording merely because literal wording is safer.
- No single construction becomes mandatory. Leading with performance, using a persona tag, or
  fusing a title into a state are available techniques, not formulas.
- The control used hip-hop fixtures only. These results do not prove transfer to other genres.
  Manley later chose not to force a non-hip-hop example into the first calibration set; genre
  transfer remains a later evaluation question before any claim that the behavior generalizes,
  without rewriting this completed experiment.
- A possible future change to the user-facing `Ready-ish` label and a stronger preference for
  persona-based connections are parked; neither changes this control's result.

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
- **The whole response `content` array is parsed.** Every block whose `type` is `text` is
  appended **in order**; the API may split a reply across several text blocks, and combining
  them out of order would silently scramble the graded output. Every other block type —
  `thinking`, `redacted_thinking`, or anything added later — is skipped without failing.
- **Thinking is never recorded or exposed.** Its contents and signatures are not read into the
  reply, not written to either record, and not quoted in any diagnostic. Thinking is model
  working-notes, not the reply; grading it would corrupt the experiment as badly as fabricating
  an output.
- **A failure is never converted into model output.** A non-200, or a response carrying no text
  block, throws; nothing is written to either record for that call, and no latency is recorded.
- **A no-text failure reports exactly three facts and nothing else:** `stop_reason`, the content
  block types, and output-token usage. The raw response, any thinking text, any signature, the
  request body, and the API key are all excluded — this string reaches a console, a
  `LiveResult`, and pasted documents, so anything that must not leave the process never enters it.
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

`test/com/runstate/MusicIntelligenceStrongerModelControlTest.java` — 67 local tests, no key, no
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
- every control request differs from its frozen original **only** in `model` and `max_tokens` —
  checked field by field, so field set and order, `system`, and the entire `messages` array all
  compare equal
- the frozen request still carries `256`; the control request carries `4096`
- **undoing both substitutions reproduces the frozen bytes exactly and rehashes to the approved
  value**, per scenario, with the exact −11 byte arithmetic pinned
- each substitution refuses when its target does not appear exactly once, and a `256` inside run
  data survives untouched because the match is on the serialized field, not the bare number
- no `temperature`, `effort`, explicit `thinking`, `output_config`, `top_p`, or `top_k` is
  added, and the body carries exactly four fields

**Opus 5 response shape** — the defect that ended the first attempt:

- a **thinking block followed by text returns the text**
- **multiple text blocks are combined in order**
- a plain single-text (Haiku-shaped) response still works
- **a thinking-only response fails** with `stop_reason`, block types, and output-token usage
- **the diagnostic never exposes thinking text, signatures, the raw response, or input tokens** —
  asserted against a response carrying a realistic signature value
- an empty `content` array, an absent one, and an empty text block all fail rather than
  returning an empty reply that would be recorded as gradable
- unknown future block types are skipped without losing the text or crashing
- end to end, **a failed extraction is never recorded as an output** in either record, records no
  latency, and stops the run after one call
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

Full clean suite with `ANTHROPIC_API_KEY` unset: **362 tests, 0 failures, 0 errors, 0 skipped**
(295 before this control). Surefire runs 9 test classes and collects neither the evaluation
runner, the probe, nor this control.
