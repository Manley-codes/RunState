---
name: adr-001-runstyle-surfacing
description: Architecture Decision Record — RunStyle's visible layer becomes contextual surfacing rather than a multi-card destination. The detection engine is unchanged and already works this way. One decision remains open - whether a summary recomputes from history or reads a persisted insight log.
metadata:
  type: project
---

# ADR-001: RunStyle's visible layer — contextual surfacing over a destination

**Status:** Accepted (multi-card destination rejected by Manley, August 4 2026; summary composition still open)
**Date:** August 4, 2026
**Deciders:** Manley

---

## Context

RunStyle V1 is built and verified. Its architecture, confirmed in code:

- `RunStyleService.analyze(Run current, List<Run> history)` returns a `RunStyleInsight`.
- `RunStyleInsight.shouldAnnounce()` gates output; `Runner.detectRunStyle(Run)` returns `null` when
  nothing new is worth saying.
- `RunConsole` prints it at step 9, after the run is durably saved — **one surfacing moment.**
- Detection is confidence-graded: EARLY/FORMING/ESTABLISHED windows, a descriptive minimum of 3
  supporting runs, comparative minimums of 5 with and 5 without, an 80% support rate, a 30% lead
  over runs lacking the context, and habit thresholds of 5 recorded in the last 10 at 70% presence.
- The profile is local and deterministic. **It is never sent to the AI.**

**What does not exist:** no cards, no RunStyle tab, no summary screen. Those were design concepts
in `creative_direction_ui.md` and the product strategy; none were implemented.

**What is not persisted:** every RunStyle *input* is stored in the `runs` table — surface type,
shoe label, run company, energies, effort, weather, route. **No insight is stored.** Each is
computed from history on demand, printed, and discarded.

**The forces at play.** Manley's judgment is that a card-based RunStyle destination would be viewed
once and then buried — reflection features have weaker repeat use than features that assist a
decision. The mechanics and purpose are not in doubt; only the presentation is. Pre- and post-run
feeling capture stays exactly as it is, surfaced less visibly and only when it is called for.

An external review independently reached the same position: keep RunStyle as a low-priority visible
surface and a higher-priority background engine.

---

## Decision

**Adopt contextual surfacing as RunStyle's primary expression. A summary view may exist as a
secondary, low-priority surface. The detection engine is unchanged.**

Concretely:

1. `RunStyleService` keeps its current responsibility, thresholds, and silence-by-default behavior.
   No change to detection logic.
2. Expand *where* the engine speaks beyond the single post-run moment, as new surfaces appear.
3. Do not build a multi-card RunStyle destination.
4. A summary view is permitted but is not the feature's headline and is not built until the engine
   has real accumulated evidence to show.

---

## Options Considered

### Option A: Destination-first — a RunStyle tab of cards

| Dimension | Assessment |
| --- | --- |
| Complexity | Medium — a new screen, layout system, and card taxonomy |
| Build cost | High relative to value; needs full UI phase |
| Repeat value | Low — the known failure mode of reflection surfaces |
| Familiarity | Design direction already sketched |

**Pros:** emotionally legible; makes the learning tangible and shareable; the aesthetic direction
(white ground, black type, selective colour, thin lines, symbols, highlighted phrases) is
distinctive and already articulated.

**Cons:** the learning only pays off if the runner visits; competes for navigation space against
screens with daily use; risks months of polish on a page that may be opened once; unbuildable until
the mobile UI phase.

### Option B: Contextual surfacing — the engine speaks at relevant moments (recommended)

| Dimension | Assessment |
| --- | --- |
| Complexity | Low — the mechanism already exists |
| Build cost | Near zero to continue; incremental per new surface |
| Repeat value | High — every run can carry value without a visit |
| Familiarity | This is the built architecture |

**Pros:** already implemented and tested; value accrues on every run rather than on visits; the
silence gate means the app only speaks when it has something; nothing new to design before it works;
consistent with the app's core (log a run → track how you felt → learn something meaningful).

**Cons:** the learning becomes harder for the runner to see, which weakens the felt sense that the
app understands them; no single place to inspect or correct what the app believes; an insight seen
once and never again is easy to forget.

### Option C: Both — surfacing primary, lightweight summary secondary

| Dimension | Assessment |
| --- | --- |
| Complexity | Medium — depends entirely on how the summary is composed |
| Build cost | Deferred; the summary waits for accumulated evidence |
| Repeat value | High from surfacing; the summary adds inspection and correction |
| Familiarity | Half built |

**Pros:** keeps the benefits of B while restoring a place to inspect, correct, and revisit; the
summary earns its existence from insights already delivered rather than manufacturing new ones.

**Cons:** introduces the composition question below; a second surface to maintain; the summary can
become a card page by drift if its scope is not stated.

---

## Trade-off analysis

**The core trade-off is visibility against demonstrated use.** Option A maximises how visible the
learning is and bets that runners will come look. Option B maximises how often the learning is
useful and accepts that it is largely invisible. The evidence available — no users, one builder's
judgment, and a well-known pattern of reflection features going unused — does not support paying
Option A's cost up front. It does not refute Option A either; it argues for deferring it until the
engine has something worth a page.

Option C is A deferred behind B, which is the same bet with an option to change your mind.

**The decision hiding inside Option C is how the summary is composed.** Two mechanisms, materially
different:

**C1 — Recompute on open.** The summary regenerates from run history each time. No new storage,
never stale, always reflects current confidence.
*Problem:* it can present a pattern the runner has never seen, which makes it a report rather than
an accumulated picture — the thing Option A was rejected for.

**C2 — Persist an insight log.** Each surfaced insight is stored with its date, confidence, and
supporting evidence. The summary is a view over that log.
*Cost:* a new table and a write on every announcement.
*Benefit:* matches the stated intent — an evolving profile built from insights the runner has
already seen — and enables correction, dismissal, and "this was true in June, is it still true?"
*New problem:* stored insights can go stale when later runs contradict them, so the log needs a
notion of an insight still holding or having weakened.

**C2 is the mechanism that matches the intent.** C1 is cheaper and would quietly reintroduce the
failure mode.

---

## Consequences

**Easier**

- Nothing must be built to keep the current value; the engine already works this way.
- The UI phase loses a screen, freeing navigation space for surfaces with daily use.
- No RunStyle card taxonomy has to be designed before screens can proceed.
- RunStyle stops competing with the post-run reply for the role of signature moment.

**Harder**

- The felt sense that the app understands the runner has to come from timing and wording, since
  there is no page carrying it. That is a harder design problem than a good-looking card.
- Without a visible surface there is nowhere to correct a wrong inference. The engine has no
  correction path today.
- Demonstrating the feature — to a portfolio reviewer, for instance — is harder when it is invisible
  by design.

**To revisit**

- Whether insights get persisted (C1 vs C2). This is the one real architectural decision here and it
  is unresolved.
- Additional surfacing moments. Pre-run is the obvious next one and does not exist.
- Whether an insight that later evidence contradicts should be withdrawn, and how.
- The correction path, which becomes necessary the moment a summary exists.

---

## Action items

1. [ ] Record the decision: RunStyle is a background engine with contextual surfacing; the
       multi-card destination is not built.
2. [ ] Note in `creative_direction_ui.md` that the RunStyle card concept is superseded as the
       primary expression, without disturbing the aesthetic direction — it still applies to whatever
       summary is eventually built.
3. [ ] Decide C1 versus C2 before any summary surface is designed. Nothing else blocks on it.
4. [ ] When rough screens are drawn, identify the surfacing moments each one offers. Pre-run is
       currently the only obvious gap.
5. [ ] Leave `RunStyleService` untouched. This ADR changes no detection logic, thresholds, or the
       rule that the profile never reaches the AI.

---

## Notes

**This ADR does not adopt the wider product strategy.** Prepare to Detect, provisional run
detection, and environmental intelligence are separate decisions with their own dependencies. Only
RunStyle's presentation is settled here.

**Nothing is removed.** Pre- and post-run feeling capture, effort, and every RunStyle input continue
to be collected exactly as they are today. Only the plan for a visible destination changes.
