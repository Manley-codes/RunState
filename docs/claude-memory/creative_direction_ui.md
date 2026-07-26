---
name: creative-direction-ui
description: Exploratory creative + UI direction for the RunState mobile app phase (brainstorm synthesis, not locked)
metadata:
  type: project
status: v0.2 — UI PHASE PAUSED; resume after the first new console music slice and the Core Running Foundation Review, before Spring Boot
---

# RunState — Creative Direction (v0.2)

> Status: UI work remains PAUSED. When development resumes, first plan Music Intelligence V1
> and ship one bounded console music slice, then complete the tightly fenced Core Running
> Foundation Review. After that review, resume this UI work before Spring Boot so the real State
> Scan, history, and post-run reply screens shape the API; build mobile/GPS after the Spring Boot
> foundation. This doc is the map for that design phase. §1–12 are the original June
> 28 brainstorm synthesis; **§0 records the
> decisions made in the later prompt-iteration sessions (early July 2026) — where §0 conflicts with
> §1–12, §0 wins.** Superseded ideas are kept, marked, not erased.
> Moodboard images are being moved OUT of the repo (July 6, 2026) — visual references in §5–6
> ("image-1", the H₂O scanner, "Today Running / 25°") and the newer references (Zendaya scene,
> Warwick Acoustics, geometric runner art, Apple Watch panel) live outside this folder now.
> Build tech (native / Flutter / mobile web) is still deliberately deferred.

---

## 0. v0.2 — Locked decisions from the prompt-iteration sessions (July 2026)

**Palette — SUPERSEDES §5's warm/cream direction:**
- Clean WHITE base. No cream, no beige, no warm gold. "White is a canvas, not emptiness" —
  activate it with layering (map texture, structural grids, frosted depth), never by darkening.
- Strict hierarchy: white surface · black selective/thin (labels, precision UI) · ORANGE primary
  accent (recording, music, action, energy) · sky blue for GPS/map/weather · green sparingly
  (positive state, outdoors only). Never spread colors evenly.

**Typography:** NO serif — dropped everywhere, including the share card. Athletic condensed
sans for numbers/hero stats ("precision sports instruments"), clean body sans for labels.

**Art — REVISES §5's "no illustration" rule:** colorless low-poly geometric athlete art
(cinematic "editorial realism," LD+R-adjacent, low color variation) is IN as the environmental
foundation on State Scan + Run Complete (bookends: poised before / settled after). Real
photography stays on the share/completion card. The actual rule: no childish flat illustration;
cinematic geometric art welcome. Panels = material glassmorphism (blur/tonal depth, NO border
strokes — avoid the "gift card" look).

**Screen flow (decided):** State Scan (pre-run) → GPS Route Start → Active Run → Run Complete.
"HOLD TO START" — explicitly no swipe-to-start. Full-bleed GPS map is the start screen's
foundation (supersedes §7's block-home concept). Each screen needs an environmental foundation
layer — UI must never sit on empty white.

**Signature moment — RESOLVES §11:** the AI reply card IS RunState's face ("the taste matched
the discipline"). The completion screen and share card merged: completion = full version in-app,
share card = curated export subset. Manley: possibly more signature than the feeling idea itself.

**Locked product details:** "Remove response" allowed; regenerate deliberately NOT (the response
is a moment, not a formula). "Miles Logged" replaces "Lifetime" everywhere. Shoe pill (with
sheen) + calorie ring (single orange arc, no gradients) on the completion screen. Rugged
mountain-ridge line for the feelings visualization (jagged horizon ABOVE the two states — not a
smooth arc, not an EKG). Static screens one at a time for design iteration; video only as a
finishing step.

**Open questions (v0.2):**
- **State Scan energy states — decide during Music Intelligence V1 planning, before its first
  implementation slice.** The UI concept used FOUR states (Low Charge / Building / Ready-ish /
  Sharp) but the backend energy system is THREE levels. Because pre-run energy is a music input,
  settle this as a domain/data-contract decision before music logic hardcodes the current enum.
  This is one bounded decision, not an early restart of the full UI phase; do not change the enum
  merely to match an old sketch.
- Collapsible block → frosted sphere docking: personalization (permanent layout) vs focus mode
  (temporary)? Worth pursuing either way; discoverability needs an affordance.

**Community clarification (July 6, 2026) — refines §2's social-comparison line:** the ban
is on direct social-network mechanics (profiles, followers, feeds-as-graph, head-to-head),
NOT on community presence. Anonymous, aggregate culture content — community shoe-mileage
norms, playlists other runners ran well to — is welcome and on-identity: "culture-community,
not network-community." Full detail in user_goals.md.

**Cold-start principle (July 6, 2026):** RunState's best features (Run Style, taste-aware
playlists, shoe analytics, comparisons) only turn on after user data accumulates — but
day one must still feel intelligent for an app whose identity is "the app that knows you"
(retention is decided in that window). Until personal data matures, offer REAL substitute
information: published reference knowledge (shoe retirement norms, RPE science,
weather-pace effects), honest progress framing ("3 runs in — your Run Style starts
revealing around run 11"), previews of what's coming. HARD RULE: never fabricate personal
insight — the agent must not pretend to know the runner before it does; honesty about
newness is on-brand and protects the reply card's credibility. (Same move as the shoe
screen's published-mileage fallback.)

**Culture details as designed moments (July 6, 2026):** culture details get real visual
presence — designed screens, not buried settings-text. First two elevated screens:
- **Shoes** — the runner's rotation shown visually: per-pair mileage, PRs carried, a
  retirement arc as a pair approaches end-of-life (published 300–500 mi reference data per
  shoe category — no user base needed).
- **Music** — "your best-run soundtracks," built from the runner's own run/music/energy history.
Anti-sprawl guard (user's own restraint rule): elevate these two FIRST and make them
unforgettable — do not systematize "every detail gets a screen." Community versions (crowd
shoe stats, browsable community playlists) are filed in parked_music_recommendation.md,
Phase 7.

---

## 1. Positioning

- **More than a fitness tracker.** It still tracks well — accurate pace, distance, and PRs are table
  stakes for serious runners — but what it's *really* about is a reflection of your productivity and a
  sense of belonging. (Be careful using "not a tracker" as a literal claim: the tracking is the *bones*,
  the reflection is the *soul* layered on top. Don't drop tracking competence in the name of positioning.)
- **"A special weapon in a runner's arsenal."** Not the center of their life — the piece that
  *signals who they are*. Small footprint, high meaning (the watch-to-a-suit instinct, resolved
  toward "weapon, but one that fits a productive life").
- **Exclusive the way Claude is exclusive:** a serious core audience that everyday people can still
  use and aspire into. Premium as a *feeling*, not a velvet rope (Lululemon).
- **The market gap is a feeling gap, not a feature gap.** Everyone competes on metrics/features;
  nobody owns the feeling. That's the wedge — wide open because it's hard and unsexy to build.

## 2. Audience

- **Primary:** frequent / committed runners, any type. The vision fits them best.
- **Welcomed:** everyday runners — aspirational, not gatekept. Designed for the serious, open to all.
- **Primary aim: active / committed runners** (any type still welcome). If forced to choose, lean here — and
  these runners tend to cluster in clubs, racing, and training-group culture, so that world is the *backdrop*
  even though we're not building club features.
- **Competition: keep the fire, skip the scoreboard.** Do **not** downplay competition in the visuals — the
  natural competitive energy of running culture (PRs, pushing yourself, racing, intensity) is welcome and should
  be *felt*. What to avoid is **direct, intentional social-comparison features** — leaderboards, ranking against
  friends, head-to-head. Competitive in spirit and aesthetic; not a social-comparison machine. Not a run-club app.
- **Not only for runners who want support.** Support and competition aren't opposites — they serve different
  moments. RunState has heart *and* edge; never frame it as "the gentle, non-competitive app."

## 3. What it must make people feel

- **Opening it:** productive, in control, motivated, a little exclusive.
- **Right after a run:** seen, productive, connected, empowered — "your hard work isn't going unnoticed."
- **The soul move:** on a bad day or a hard stretch, the *experience* should leave them feeling like they're
  **hanging in there** — resilient, still in it. This is a feeling to **evoke**, NOT a literal line the app prints.
  Do not display "you're hanging in there" (or similar) as text. Never shame a down day. (Extension of the app's
  existing rule: never mention below-average — the runner showed up, that counts.) No running app does this — protect it.

## 4. Personality — adjectives, ranked

Raw list: refreshing, productive, slight futuristic, sexy/fit, culture, sophisticated, nature.
Seven can't lead equally. Proposed center of gravity (Manley to confirm):

- **Lead with three:** Warm & alive (refreshing / nature) · Productive & sophisticated · Sexy & charged (the *Challengers* heat).
- **Season with:** slight futurism · running-culture texture.

## 5. Visual thesis — "frosted glass over a warm, natural world"

> ⚠ PARTIALLY SUPERSEDED by §0 (July 2026): base is now clean WHITE (cream/warm palette dropped),
> no serif type, and cinematic geometric athlete art is allowed. Frosted glass survived and grew
> into the material-glassmorphism panel system. Kept below for the reasoning history.

The through-line across every reference Manley pulled:

- **Translucent / blurred glass blocks** floating over a **warm background** (trail map, park, sun) so
  the world glows through the gaps. One device that solves blocks-not-cliché + the "glimpse of background"
  + warm futurism all at once.
- **Real athletic photography** — sun, sweat, health — *not* flat illustration or blob animation
  (the move every fitness app makes; avoid it).
- **Confident editorial type.**
- **Restrained futuristic-instrument layer** — calm and clean (like the white H₂O scanner reference),
  not Tony-Stark chrome.
- **Warm futurism is the wedge:** natural, sunlit warmth — skin-tones, daylight, heat-shimmer — instead
  of cold blue/chrome. *"Warm" is the temperature, not a literal gold palette.* Exact colors are still
  open, and **definitely not** the saturated amber/gold of the reference apps. Everyone else's futurism
  is cold; yours radiates — but quietly.
- **Layout reference only (and loose):** the "Today Running / 25°" app shows a usable block + photography
  *layout* direction. Take the **layout only** — not the colors, and know it's still a good distance from
  the target. Do not read it as "the look."
- **Base tone = light (decided).** The primary surface — the "using and navigating" state — is light, warm,
  daylight. It should breathe and feel fresh / healthy / alive.
- **The arc: dark open → light use.** Dark, premium, cinematic is reserved for *moments*, not the whole app.
  You open into something moody and expensive (Grow-the-Future energy) for the entry / anticipation, and it
  *opens into daylight* as you actually use it. Likely dark again for the post-run reflection — the moody
  close. Think of it as a film: dark cold-open → daylight body → reflective close. This layers directly onto
  the state model (§8): tone tracks state, light is home.

## 6. Rules of the look

- **No equal blocks.** Identical rounded cards in a tidy grid = the cliché Manley hates. Use
  hierarchy + asymmetry: one hero block, supporting "scenes" at different sizes — film-poster /
  *Kill Bill* cutaway, not dashboard.
- **The UI dresses like a runner.** Style components like running gear/fashion, not software widgets —
  the start control "worn like shades," the hero piece that carries the fit.
- **Restraint is the brand.** Fewer features, done beautifully. Cutting lazy half-features is strategy,
  not laziness. "Without overdoing it" is the whole craft — imply the heat with one warm photo and a lot
  of calm space, don't crank it.
- **Nature texture, formatted.** Nature woven into the UI (the leafy, organic, alive feel) is welcome —
  but it has to be *disciplined*. The formula: image-1 ingredients (nature, freshness) with Grow-the-Future
  structure (rigor, hierarchy, restraint). Texture without formatting reads cluttered; that's the failure mode.
- **Let photography break the frame.** A runner photo that spills out of its card/block (subject crossing the
  edge) reads dynamic and premium — an editorial device worth using, especially on the reflection screen.

## 7. Home screen concept (the block idea)

- 3–5 blocks, slightly apart, the warm world glowing through the gaps; lean into the frosted-glass card.
- One hero; the rest supporting scenes — e.g. a run-style keyword (tap to expand), a live GPS/run block,
  a single metric block (calories), a shoe-mileage tag, a reflection block.
- Possible **Claude-like clean top selector** (a few simple modes) as a light, authentic AI touch.

## 8. States, not theme-switching

Resolves the "adaptive interface" itch without the chaos of maintaining multiple UIs. Same bones,
hierarchy reflows by state:

- **Idle** → start block is the hero; anticipation.
- **Running** → live block takes over (GPS, metrics breathing).
- **Just-finished** → the reflection / response block becomes the hero (the voice-message moment,
  shoe miles ticking up, "this was a different kind of run" signal).

Optional lighter accent: warm by day / cool-calm before sunrise — skin only, never the skeleton.

## 9. The AI reflection

AI is authentic here — `RunAgent` already powers the post-run response. Let AI show up as **warmth and
intelligence in the response** — the *feeling* of being seen — not as robotic chrome aesthetics. That also keeps
it on the warm side of futurism, away from Tony Stark. (The "hanging in there" energy is a feeling to evoke, not
a line to print.)

## 10. Guardrail: bold skin, familiar bones

The one correction worth holding: "user-friendly" ≠ "generic." Manley's heroes (Claude, Nike Run Club,
Lululemon) are all distinctive *and* effortless. Used sweaty, one-handed, heart pounding, post-run — if the
screen is gorgeous but unfindable, the art never lands; it's hidden behind frustration.

- **Bones = usability:** predictable structure, one obvious primary action, reachable with a sweaty thumb.
- **Skin = creativity:** warmth, glass, cinematic tone, the gear metaphor — go as hard as you want.
- Keep the bones quietly friendly *so the skin can go harder, not softer.*

## 11. The signature moment (RESOLVED — see §0)

- The post-run response (voice-message vision): assuring — you were seen, your work isn't unnoticed.
- The Run Style reveal should feel **rewarding / earned**.
- Still cooking — come back to this with intent.

## 12. Open questions / parked

- Rank the 7 adjectives (Manley's call).
- Fully define the signature moment.
- A night / pre-dawn version of the palette?
- Clear-ocean accent under consideration — sunlit aqua (not cold navy / tech-blue), tied to water / hydration /
  "refreshing"; play it against the warm daylight + green so it stays fresh, not clinical.
- Self-competitiveness without a social club (PRs, streaks, "beat your shadow")?
- Competition vs social-comparison: **keep competitive energy in the feel and visuals** (PRs, intensity, racing —
  don't downplay it). **For now, leave social-comparison / friend features out entirely** — not a rejection, just
  deferred; Manley isn't fully against them, just undecided, so revisit later. Self-comparison and personal stats
  boards are still fine. The soul governs *tone*, not whether competition exists.
- Run-picture gallery — a future feature (a home for run photos). Out of current scope; revisit later under the
  scope rule. The frame-breaking photo *device* can be used now regardless.
- Build-tech decision (native vs Flutter vs mobile web) — later.
