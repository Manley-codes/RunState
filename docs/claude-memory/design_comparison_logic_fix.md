---
name: design-comparison-logic-fix
description: Phase 5.5 planned fix — the run comparison logic is flawed in all three code paths, deferred until after UI phase
metadata:
  type: project
---

> UPDATE July 7, 2026: the effort measure this fix has been waiting for now has a design —
> see design_effort_cost.md ("How did that run land?"). Build them together or effort-first.
>
> HARVESTED July 7, 2026 (from archived ENERGY_STATE_DESIGN.md): **Run Type**
> (Easy / Steady / Speed / Long Run / Race — optional, selected pre-run from a secondary
> area, never asked after completion, never auto-inferred) is the third leg of fair
> comparison alongside effort data. Comparing a sprint day against a long-run day is the
> exact blended-average flaw this fix exists for. Consider capturing run type with or
> after the effort input.

## What's wrong

The current comparison passes a pre-labeled fact to the AI and uses it mechanically in fallback logic:

```
Pace this run vs average: Above average
Distance this run vs average: Above average
```

These labels are derived from a blended 20-run rolling average that mixes easy runs, long runs, tempo, and sprints. "Above average pace" often just means "today was a short fast day" — not "I got fitter." The LLM cannot know the difference because the distortion happens before the input arrives.

The system prompt instruction ("reference trends only when meaningful") controls whether the LLM *mentions* the comparison — it does not fix the comparison itself. The AI path is better-masked, not actually sound.

Additionally, "faster AND farther than average simultaneously" rewards a physiologically unusual combination. The two healthiest run types — easy long run (farther, slower) and hard short workout (faster, shorter) — can never trigger Run Style. The detector is blind to good training.

## Three code paths that need fixing (Phase 5.5)

**1. `buildUserMessage()` in RunAgent.java** — highest leverage
- Currently: sends `aboveAvgPace` / `aboveAvgDistance` boolean labels
- Fix: send effort-relative framing instead — "pace at similar effort vs. past similar runs"
- This is the root fix. Both AI path and fallback improve from a single change here.

**2. `buildFallbackResponse()` in RunAgent.java**
- Currently: mechanically appends "You ran farther and faster than usual" when flags are true
- Fix: remove the mechanical performance note, or reframe around effort efficiency
- Minor impact (fallback path only), but it's the most literal expression of the flaw

**3. `detectRunStyle()` in Runner.java**
- Currently: requires faster AND farther than rolling average + moderate/high energy
- Fix: rebase qualifying criteria — options include showing up consistently, LOW→HIGH lift habit, or effort efficiency (same pace, less effort over time)
- The consistency gate and earned/quiet character should stay — only what counts as qualifying changes

## The right long-term direction (Option A)

The gold-standard amateur improvement signal is: *same pace, less effort over time*.

Pre/post energy is essentially RPE. The app already captures both halves (pace + energy) but currently uses energy only for message tone. Redirecting it as a progress metric is:
- Correct (scientifically)
- Always positively framed (so "never mention below average" comes for free)
- Built from data already in the model

**Why:** Agreed June 2026 after research review. Not blocking UI phase — treat as Phase 5.5 polish pass.
