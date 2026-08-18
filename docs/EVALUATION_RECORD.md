# Music Intelligence V1 — Evaluation Record

**Status:** Implemented · deterministically verified · **manual evaluation not accepted**
**Last updated:** August 18, 2026
**Canonical design contract:** [`docs/design/design_music_intelligence_v1.md`](design/design_music_intelligence_v1.md)
**Full evaluation detail:** [`docs/design/music_intelligence_v1_evaluation.md`](design/music_intelligence_v1_evaluation.md)

---

## Why this document exists

Music Intelligence V1 is the feature intended to make RunState's post-run response distinctive:
a reply that uses what the runner listened to as real material rather than as a data point to
mention. It was planned, approved, built, and verified. It was then evaluated against a quality
bar written *before* the evaluation ran — and it did not meet that bar.

This document records that result. The feature is not shipped, not quietly reduced in scope, and
not declared finished. It sits at a stable, documented stopping point.

The reasoning is straightforward: a quality bar you move after seeing the results is not a quality
bar. Recording a failed evaluation costs a feature. Not recording it costs the ability to trust any
future evaluation in this project.

---

## What was built

- The Music Intelligence V1 system prompt, authored as two constants in `RunAgent.java` — the
  general mentor contract and the music policy — joined into a single system field
- Sanitized evaluation fixtures covering the intended range of run and music scenarios
- An **opt-in evaluation runner**, deliberately outside the Surefire naming pattern so it never
  executes as part of `mvn test` and never makes a live API call during ordinary development
- A pre-registered grading rubric with an explicit failure branch, written before any output was
  generated

**Deterministic verification passed.** The approved creative-policy revision is committed at
`693bfb3`, with the suite reporting **0 failures, 0 errors, 0 skipped**. The prompt-construction
logic, the injection contract, and the fixture handling are all covered by tests that run without
credentials.

Deterministic verification proves the code does what it was specified to do. It does not prove the
output is good. Those are separate questions, and only the first one passed.

---

## Evaluation history

| Run | Date | Result |
|---|---|---|
| Initial launch | July 2026 | **Void.** Authentication invalid — produced no model evidence. Not counted as a result either way. |
| First smoke, original prompt | July 2026 | **Failed product quality.** Voice was generic and music-avoidant — the replies talked around the music rather than using it. Drove the creative-policy revision. |
| Second smoke, revised prompt | July 30, 2026 | **Failed diagnostic quality and trust.** 12 calls, zero fallbacks — every call reached the model and returned. The prose improved. The reliability did not. |
| Creative-ceiling probe | July 30, 2026 | **Failed its bar.** 12 completed calls under minimal prompting, to test whether the constraints were the problem. Prose improved further; replies still were not reliable. |
| Stronger-model control | July 31, 2026 | **Failed its bar.** A more capable model alone did not clear it. |
| Final 36-output evaluation | — | **Never run.** All gates still stand. |

### What the graders found

On the creative-ceiling probe, **both independent graders independently reached the pre-registered
0–3 branch** — the failure branch defined in the rubric before any output existed — and counted
**nine hard-trust failures** between them.

On the two replies where the graders disagreed, the tiebreak was mine, and I found neither
app-worthy.

Independent agreement on a pre-registered failure branch is the cleanest negative result available
here. It is not ambiguous and it did not depend on my own judgment to reach.

### What the probe established

The creative-ceiling probe existed to answer one question: *were the constraints suppressing the
quality, or was the quality not there?*

Removing nearly all prompting improved the prose and did not produce reliable replies. That points
away from over-constraint as the cause. It is diagnostic evidence, not proof — but it redirected
the work away from prompt-loosening as the fix.

---

## What this evaluation settled elsewhere

The planning work produced one decision that stands independently of the feature's outcome:

**The stored energy domain is CLOSED at three levels** — LOW / MODERATE / HIGH. The four-state
State Scan sketch is **superseded as a domain proposal**. Later UI presentation and label wording
may still change; the stored meanings do not.

---

## Where the evidence lives

Raw material is kept rather than summarized away, in [`docs/design/evidence/`](design/evidence/):

- Blind grading packets, as the graders received them
- Creative-ceiling probe outputs and the exact request payloads
- Smoke-test transcripts from each run
- Stronger-model control results
- Screenshots of the July 30 revised-prompt smoke, preflight through summary

---

## Status of the path forward

Recorded with explicit status words. Nothing below is in progress.

| Step | Status |
|---|---|
| 1. Design and separately approve a stronger-model control as a live branch | **Not approved** |
| 2. Review that control as a diagnostic; correct only real problems | Blocked on 1 |
| 3. Separately approve and conduct the 36-output final evaluation | Blocked on 2 |
| 4. Reconcile independent review against the locked rubric; final decision | Blocked on 3 |
| 5. Core Running Foundation Review — is `record → preserve → understand → manage → use later` ready for a real interface? | Blocked on combined V1 being complete |
| 6. Resume UI design so screens define what the backend must return | **In progress** — running ahead of 5 by decision |
| 7. Migrate/design the Spring Boot API from those screen contracts | Not started |
| 8. Build the mobile client and GPS/automatic-tracking layer | Not started |

**Combined Music Intelligence V1 is not complete.** Completion requires implementation,
deterministic verification, manual evaluation, independent review reconciliation, final
documentation, and approval — as defined by the canonical plan. Only implementation and
deterministic verification are done.

---

## Related possibilities, out of current scope

Spotify integration and live-DJ behavior remain later possibilities with separate legal, privacy,
provider, and platform dependencies. They are **out of current scope by stage, not rejected on
merit**, and are not blocked by this evaluation's outcome.

---

## Reading further

- [`docs/design/design_music_intelligence_v1.md`](design/design_music_intelligence_v1.md) — the canonical plan and contract
- [`docs/design/music_intelligence_v1_evaluation.md`](design/music_intelligence_v1_evaluation.md) — fixtures, gates, full diagnostic history
- [`docs/design/music_intelligence_creative_ceiling_probe.md`](design/music_intelligence_creative_ceiling_probe.md) — the probe and its result
- [`docs/design/music_intelligence_stronger_model_control.md`](design/music_intelligence_stronger_model_control.md) — the control and its result
- [`docs/design/design_music_reply_style.md`](design/design_music_reply_style.md) — craft reference: assert vs observe, music as a supply of language
