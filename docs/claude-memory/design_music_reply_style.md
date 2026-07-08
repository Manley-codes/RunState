---
name: design-music-reply-style
description: "Music reply craft rules (July 6, 2026) — early-user leniency, convergence-scaled references, stat-first structure, cross-run frequency balance. Prompt-only change, slots after weather cleanup."
metadata:
  type: project
---

# Music reply style — craft rules for the AI agent (July 6, 2026)

How the agent should reference music in post-run replies. Extends the Phase 5 Step 1
music feature (manual input). These are SYSTEM_PROMPT + buildUserMessage() changes only —
no schema or console changes. Natural next backend task AFTER the weather cleanup
(design_weather_cleanup.md).

## The anti-pattern vs. the north star

**Anti-pattern (real output, July 2026):** "Till I Collapse by Eminem was a great song choice."
It EVALUATES the song — a compliment about the user's taste, disconnected from the run.

**North star (Manley's mockup, transcribed — image kept out of repo):**
> "4.2 at 8:24 before the city woke up — eleven seconds under your rolling pace.
> Larry June on Cedar Trail at sunrise: the taste matched the discipline."
> (Now playing: "6am In Sausalito" — Larry June, Orange Print)

Why it works: it never names the song; it references the ARTIST and lets circumstances
carry the weight. The connection is a CONVERGENCE — 6am-titled song + sunrise run +
artist whose persona is discipline/taste + a real stat anchoring it.

NOTE: the mockup itself needs time-of-day + a now-playing integration — both Phase 6.
The STYLE rules below are fully portable to today's manual feature.

## Craft rules (for SYSTEM_PROMPT revision)

1. **Never evaluate the song choice.** No "great song choice," no taste compliments.
   Connect what the song/artist is ABOUT to what the run WAS.
2. **Convergence scaling.** Boldness of the reference scales with how many run details
   converge with the music (effort, mood, energy arc, grind, title/persona fit).
   One thin link → stay quiet or stay light. Multiple links → lean in confidently.
   This is also the frequency regulator: convergence is common enough to appear
   regularly, rare enough to never feel spammed.
3. **Stat first, poetry second.** Earn trust with the run fact, then get creative.
   The anti-pattern inverts this (style first, substance never).
4. **Clarity over cleverness.** The connection must land for someone who only
   half-knows the song. No deep-cut logic puzzles.
5. **Reference spectrum (corrected July 7, 2026 — Manley's ruling).** Default: creative,
   theme-fitting references a listener instantly recognizes as the song (the north star
   above) — but recognition beats wordplay; if a reference drifts too far from the song,
   it loses the idea entirely, and special wordplay should never be forced when the run
   details don't earn it. Exact or NEAR-exact lines ARE allowed selectively — strongest
   when the line is proverb-grade / common speech ("what doesn't kill you makes you
   stronger") or when paraphrase would break recognition. LEGAL NOTE (deferred — Manley
   decides later, see the legal milestone): verbatim quoting of DISTINCTIVE signature
   lines carries copyright risk; proverb-grade phrases don't. This is a flag, not a ban.
6. **Artist reference can beat song reference.** When persona fits better than the
   track (the Larry June case), reference the artist, not the song.

## Early-user leniency (decided July 6, 2026)

Discovery problem: a new user who types music and gets ignored concludes the field is
decorative and stops using it. The feature must prove it's listening early.

Rule: keep the quality bar, change the default POSTURE. For roughly the first 10 runs,
the agent actively hunts for the genuine link (one almost always exists) instead of
staying silent unless the fit is undeniable. A forced hollow reference in week one is
worse than silence — leniency means trying harder, not accepting worse.

## Cross-run frequency balance (mechanism, small build)

The agent is stateless — it cannot know it referenced music three replies in a row, so
it cannot self-regulate. Fix: pass cross-run context into buildUserMessage():
- "Music referenced in X of the last Y replies"
- "Runs logged so far: N"
Early runs → lean in. Recent streak of music replies → hold back.
This SAME mechanism solves the June 26 variety note in project_current_state.md
(never call out the same song every run once Spotify automation exists).
Implementation note: requires deciding how "was music referenced" is detected/stored —
open question, resolve when building (options: flag column on runs, or lightweight
string scan of stored replies — replies are not currently stored, so likely a flag).

## Out of scope here

Reflective-song SELECTION from full listening history — see the addition in
parked_music_recommendation.md (Phase 6: needs run start-time + play timestamps).
