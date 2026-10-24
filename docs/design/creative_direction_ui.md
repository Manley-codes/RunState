---
name: creative-direction-ui
description: Creative + UI direction for the RunState mobile app phase; includes the primary visual vision, August 13 working proof, and exploratory history
metadata:
  type: project
status: v0.4 — August 13 working proof recorded; execution remains open and is found through design passes
---

# RunState — Creative Direction (v0.4)

> **Current status, August 13:** the UI phase is active. Log History has a stable visual foundation,
> and the Start screen has now been prototyped through its transition into active tracking. That
> Start/Active Run pass is stable enough to leave for now; it is design evidence, not mobile code.
> The most-recent Log-record quick peek is now complete. The next queued pass is the
> progressive-input ladder, followed by Run Complete and Log History refinement. The Core Running
> Foundation Review follows those screens; music feature inventory and prioritization comes after
> that, with RunStyle Sound treated as one candidate rather than a predetermined screen. Full screens
> continue to expose backend and mobile requirements before Spring Boot and mobile contracts are
> finalized.
>
> Music Intelligence remains at its separate stable stopping point, and the built RunStyle V1
> remains current behavior. Their status lives in their own records; neither should be inferred from
> this visual-direction file.
>
> This doc is the map for that later full design phase. §1–12 are the original June
> 28 brainstorm synthesis; **§0 records the
> decisions made in the later prompt-iteration sessions (early July 2026) — where §0 conflicts with
> §1–12, §0 wins.** Superseded ideas are kept, marked, not erased.
> Moodboard images are being moved OUT of the repo (July 6, 2026) — visual references in §5–6
> ("image-1", the H₂O scanner, "Today Running / 25°") and the newer references (Zendaya scene,
> Warwick Acoustics, geometric runner art, Apple Watch panel) live outside this folder now.
> Build tech (native / Flutter / mobile web) is still deliberately deferred.

---

## 0. v0.2 — Decisions from the prompt-iteration sessions (July 2026)

> **Visual-history notice, August 9:** the fixed clean-white/black/orange palette, geometric-athlete
> foundation, and other visual translations in this section no longer govern the current direction.
> They remain here as evidence of what was tried. Product, behavior, and flow decisions in this
> section are unaffected. For current visual guidance, use **The primary visual vision** below.

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

**Historical screen-flow translation — superseded visually August 13:** the overall journey from
pre-run setup → Active Run → Run Complete remains useful. The former `HOLD TO START`, full-screen GPS
foundation, and mandatory environmental-background interpretation do not describe the current Start
prototype. The current screen uses a direct Start button and a luminous near-white atmospheric field;
details live in `design_start_active_run.md`.

**Signature moment — RESOLVES §11:** the AI reply card IS RunState's face ("the taste matched
the discipline"). The completion screen and share card merged: completion = full version in-app,
share card = curated export subset. Manley: possibly more signature than the feeling idea itself.

**Locked product details:** "Remove response" allowed; regenerate deliberately NOT (the response
is a moment, not a formula). "Miles Logged" replaces "Lifetime" everywhere. Shoe pill (with
sheen) + calorie ring (single orange arc, no gradients) on the completion screen. Rugged
mountain-ridge line for the feelings visualization (jagged horizon ABOVE the two states — not a
smooth arc, not an EKG). Static screens one at a time for design iteration; video only as a
finishing step.

**RESOLVED (July 27, 2026) — State Scan energy states:** the stored domain remains
**LOW / MODERATE / HIGH**. The FOUR-state State Scan concept (Low Charge / Building /
Ready-ish / Sharp) is **superseded as a domain proposal** — it does not change the stored
enum. Later UI work may still refine **labels and presentation** freely, provided the three
stored meanings are unchanged. Decision record: `design_music_intelligence_v1.md`.

**Open questions (v0.2):**
- Collapsible block → frosted sphere docking: personalization (permanent layout) vs focus mode
  (temporary)? Worth pursuing either way; discoverability needs an affordance.

**HISTORICAL FOUNDATION — August 6, 2026.** Frosted panels over a photographic environment
replaced the Sony entry's *clean light canvas* translation. On August 9 this became one material
expression inside the broader visual vision below, rather than the identity by itself.

---

## ⚠️ HOW TO WORK WITH THIS SECTION — read before advising on anything visual

**Nothing here is locked. Manley's own words: *"keep an open mind — this is something I might
constantly do until I find what I want."***

This is a standing instruction, not a caveat. It changes how anyone should behave:

- **Do not cite this document as authority against a new visual direction.** Give the reasons behind
  a recorded decision and what would reopen it — never "the docs say X." Manley: *"don't worry too
  much about the style rules in the docs — consider them, but keep an open mind."*
- **Direction gets found by making, not by specifying.** The foundation below was arrived at by
  building several variants and looking, after a specified version failed. Do not short-circuit that
  with a spec that decides in advance.
- **Colour, shape, and layout are all open.** *"I don't like to be restrained when it comes to
  visual art... when it comes to colors, shapes, layouts etc I'm open to all suggestions."* That
  includes replacing orange.
- **Iteration is not indecision.** Repeatedly revisiting the look is the process working. Treat a
  reversal as new information, not as churn.
- **What is stable is the target, not the execution:** premium, stylish, light, productive-looking,
  and *very slightly* futuristic — advanced-looking rather than futuristic.

**A lesson worth keeping.** A light version failed on August 5 because its spec was a list of
**removals** — no borders, no shadows, no gradients, flat fills. In the dark build those read as
restraint because translucency and depth were already carrying the quality. In light, nothing was
left doing that work and the result was structurally correct and visually empty. **Specifying visual
quality by prohibition does not work.** A direction has to say what *creates* the quality, not only
what to avoid.

---

## The primary visual vision — August 9, 2026

This description is important and is the **primary working reference for atmosphere and identity**:

> **RunState should feel like a purposeful running experience held within a soft, sensorial
> atmosphere. Real running environments, physical exertion, sweat, movement, and confidence give
> it athletic substance; strategically placed dark elements and precise structure add premium
> discipline; and everything remains productive and forward-moving. The surrounding softness
> never weakens the physical intensity—it gives that intensity somewhere fresh, human, and
> inviting to live.**

**The running is not soft; the world holding it is.** Physical fitness and running remain first.
Softness describes the persistent visual atmosphere—present before, around, and after the stronger
substance—not the effort, athlete, or sport. Real running environments, intensity, sweat, attitude,
confidence, productivity, and structure inhabit that atmosphere. They give it weight and purpose
without destroying it. The tension between a soft surrounding world and genuine physical intensity
is central to the freshness and indirect sexiness of the identity.

**Productivity is a top identity trait.** The softness must never drift into passive, sleepy, spa-like
wellness. RunState should remain clear, useful, and forward-moving: it helps the runner prepare, act,
reflect, and improve without adopting the cold or mechanical atmosphere common to productivity and
fitness software.

**Current visual interpretation:**

- Soft, luminous colour and tonal variation dominate. Near-black, darker colours, or darker shades
  of the dominant soft colours are placed strategically for structure, confidence, navigation,
  emphasis, and premium contrast. They do not take over the atmosphere.
- Real-life running and related athletic environments are part of the identity, not incidental
  decoration. The environment may supply green, clay red, blue, amber, morning grey, or another
  natural colour relationship; this is not a rule that everything becomes green.
- Photography may carry indirect sensuality through reflected or sweaty skin, natural light,
  physical texture, movement, close editorial crops, attitude, and self-possessed confidence.
  The aim is athletic presence and allure, not explicit sexualisation.
- Sony remains a **supporting influence**, used minimally and strategically for precision,
  proportion, materials, hierarchy, restraint, and premium discipline. Its heavier technological
  atmosphere is not the target.
- Frosted or glass-like panels remain a promising material because they let the environment live
  through the interface. Glassmorphism is not the identity and is not required everywhere.
- The strongest current reference pattern is a soft, luminous environment with one concentrated
  dark structural element, such as a confident navigation or action surface. Exact components are
  still resolved screen by screen.
- Typography is also resolved screen by screen. Strong condensed or instrument-like numerals,
  clean functional labels, and selective editorial character are all available; the historical
  universal `NO serif` rule does not govern the current direction.

**Seasonal palette exploration — provisional, August 21:** RunState may eventually let its palette
shift with the season, giving several successful directions a purposeful home rather than forcing
one palette to carry the entire year. Possible references include ACG Frost for winter, Court Sage
for spring, Adventure × ACG Frost for summer, and Sonny or King for autumn. This is not a locked
decision or a showcase requirement; it is a future theming possibility to revisit after the core
journey is stable.

### August 13 working proof — Start to Active Run

The circular Start/Active Run prototype is the clearest current test of the vision. Its canonical
screen and behavior record is `design_start_active_run.md`.

- A foggy porcelain near-white field carries softness through tonal depth rather than through a
  full-screen color or photograph. This proves that near-white can be atmospheric; **flat, untreated
  white**, not white itself, is the failure mode.
- A grayscale group-running photograph is contained inside a circular timer/music instrument. Real
  athletic imagery remains part of the identity even when it is not the whole background.
- The neutral **State Glass** energy tiles and a concentrated near-black Start/Pause control provide
  structure without letting dark technology take over the screen.
- The **Electrolyte** blend — softened blue-gray, eucalyptus/forest, and pale luminous yellow — adds
  life only to the radial music visualizer on the neutral screen. It is adjustable evidence, not a
  locked palette or permission to spread those colors across every component.
- The same circle transforms from preparation to active tracking: pace, BPM, and elapsed time live
  inside; large heavy distance numerals live below. Productivity comes from this immediate hierarchy,
  not from adding dashboard density.
- The compact live now-playing strip borrows Log History's material DNA while remaining clearly part
  of the current run. This creates continuity between the live moment and what will later be saved.

The successful ingredients are the relationships — softness around physical substance, selective
dark structure, real athletic presence, clear metric hierarchy, and restrained living color. Future
screens should not copy the circular composition merely because it worked here.

**What this updates without erasing the design history:**

- The fixed clean-white/black/orange palette is no longer an active identity rule. Exact colours
  remain open; the stable relationship is soft colour dominance with selective dark structure.
- Geometric athlete art is no longer the environmental foundation. It remains an optional technique;
  real-life running and athletic imagery is the primary identity candidate.
- The earlier dark-open → light-use → dark-close arc is not a governing rule. A screen may still use
  a dark moment when it strengthens that moment without overwhelming the soft atmosphere.
- The photographic environment, natural freshness, editorial confidence, productive feeling,
  restrained futurism, selective darkness, and premium quality through precision all remain useful.

This is a strong vision, not an immutable component specification. The open-mind rules above still
apply: design passes may discover better executions without re-arguing the identity from scratch.

---

## Earlier material foundation — frosted panels over a photographic environment

**Manley's articulation:** *"a more soft tone of Sony, to connect better with the environment of
running — that's why I also added 'light' in my description."*

That resolves what looked like a contradiction. *Light* means luminous, but it neither requires nor
forbids a near-white page. A foggy porcelain field is valid when tonal depth, material, photography,
or motion gives it atmosphere. The rejected direction was blank, flat, and generic — not white itself.

**How it works.** A photographic environment behind translucent panels remains one useful
expression. The August 13 Start screen demonstrates another: photography can be contained inside a
live interface instrument while a luminous atmospheric field carries the surrounding softness. Both
approaches can belong to RunState; neither is required on every screen.

**What survives from the earlier foundation.** Interfaces need an atmospheric source — photography,
fog, tonal light, glass, texture, motion, or a deliberate relationship among them. They should not
feel like controls dropped onto an untreated blank canvas. That is a quality requirement, not a rule
that every screen needs a full-bleed photograph or colored background.

**Parameterised in Claude Design.** The screens expose live controls — background surface, panel
clarity, palette, and pill style — so the look can be tuned by looking rather than by respecifying.
Treat those as the iteration surface.

**Open and being handled by Manley:** legibility over busy photography, what determines which
background appears, and the production cost of sourcing imagery. Raised August 6; he is ahead of it.

**Candidate Sony-inspired direction (August 2, 2026 — historical exploration, not current authority):**
- Use Sony as a **design philosophy, not a visual template**: visionary, simple, human-centered,
  confident, immersive, and premium; "smart, not intellectual" and "cool, not trendy."
- Translate that into a clean light canvas, disciplined black, one concentrated warm accent,
  sculptural hierarchy, purposeful asymmetry, one strong hero scene, and quiet supporting controls.
  Premium should come from proportion, spacing, materials, and precision rather than decoration.
- Blend technology into human and natural environments. Keep RunState's athletic energy, bold
  metrics, orange action/music signal, GPS blue, sparse positive green, and selective dark cinematic
  moments; Sony-like calm should make those elements more deliberate, not drain their movement.
- Do not copy Sony logos, product-showcase compositions, spheres/stones, repeated pill controls, or
  tiny low-contrast labels. A runner should still understand the screen quickly while tired and sweaty.
- The white Active Run / Run Complete explorations are currently closer to this direction than the
  darker photo-heavy concept, but neither is final. Their instrument-like hierarchy is promising;
  the next pass should add more human warmth and material depth without adding clutter.
- The supplied sand/mineral, white, silver-gray, black, and restrained yellow reference is a
  **palette experiment only**. Test its color relationship against the current white/black/orange
  system; it does not yet supersede the clean-white base or make beige/gold a locked brand color.

**Soundtrack Log direction (August 3, 2026 — promising prototype, not locked or implemented):**
- The Claude Design exploration revealed a stronger home for the music reflection: a newest-first
  **run-and-soundtrack log** that people can revisit, rather than a one-time AI reply that disappears
  after the post-run moment. The working header `RUNSTATE · SOUNDTRACK LOG` and the idea of each run
  becoming a record are directionally strong; final naming is still open.
- Each collapsed row shows the selected soundtrack moment, artist/album context, and run date/time.
  Opening a row reveals the reflection and updates the visible run-facts panel for that record. Keep
  only one row expanded at a time so the screen remains focused and the chosen reply has room to land.
- The expanded record may show the reply, song metadata, a run-relative stamp such as a mile marker,
  and `READ FROM` evidence chips. A chip must name information the reply actually used. Mile markers,
  splits, cadence, heart rate, elevation, wind, and other richer mock data are **future capability
  references, not claims about what the current console app records**; show them only when the real
  run data eventually supports them.
- **Reflection voice behavior:** opening a record may automatically read its AI reply aloud; `Replay`
  repeats it. Collapsing it or opening another record stops the current reading, and the newly chosen
  record may then begin. This is audio presentation of the existing reflection—not a revival of the
  previously parked general-purpose voice-message feature. Later prototypes must still test mute,
  silent-mode, accessibility, and an obvious way to stop audio so the app never surprises the runner.
- The visual split of a concrete run read followed by a human/music read is a useful card-composition
  reference, not a mandatory two-line writing formula. The reply still follows the strongest idea.
- **Unresolved data-model question:** a `record` currently reads best as one intentionally selected
  signature soundtrack moment for a run, not every track that happened to play. Do not let `14 runs,
  14 records` silently lock one-song-per-run or prevent multiple meaningful moments later. Resolve
  what qualifies as a record before persistence or provider integration is designed.

**Social position refinement (August 4, 2026) — REFINES the profile exclusion below.** Manley's
decision: a **shareable personal card** carrying the runner's picture, top runs, and the songs
beside them is **permitted**, as `RunStyle Sound` (see `music_feature_register.md`). His stated
reasoning: he accepts that some comparison will happen and does not want comparison to become the
app's **core identity** — an optional exported artifact is not a core mechanic.

The distinction that keeps this consistent with the July 6 line: **a card is exported, a page is
browsed.** What remains excluded is the browsable surface — profile pages other runners navigate to,
follower graphs, feeds, rankings, and head-to-head. What is now allowed is an artifact the runner
chooses to share, in the same family as the existing share card.

Two constraints attach: the exported card carries no feature name (it would be jargon to a
non-user), and the associations shown must be worded associatively rather than causally, per the
overclaim guard.

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

- **Avoid unconsidered equal-card dashboards.** Hierarchy and asymmetry should usually prevent the
  generic tidy-grid cliché: one hero block with supporting scenes at different sizes. A bounded
  one-of-three control can still use equal adjacent forms when equality communicates the domain;
  the current State Glass energy tiles are the deliberate exception, not a new dashboard template.
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

---

## October 22, 2026 — Existing brand wording collected for review

**Purpose:** preserve the wording already scattered through the project so Manley can compare it
without losing its original context. The search found no dedicated list of approved slogans.
These 21 entries are existing positioning statements, historical phrases, or music-specific
expressions; collecting them here does not approve them as public slogans. Short excerpts remain
excerpts, and historical material does not become current merely by appearing in this list.
Wording is reproduced exactly, with wrapped source lines joined for readability.

**Opening-screen boundary:** none of these entries is approved for the opening screen. Its text
remains unchanged; this is a reference collection, not an instruction to add a subtitle or slogan.

### Existing candidates and their original context

| # | Existing wording | Classification and original context | Source |
| --- | --- | --- | --- |
| 1 | “RunState helps you understand what your run meant, not just what you recorded.” | Positioning — recorded core-purpose sentence; not an approved opening tagline. | [user_goals.md, lines 113–114](user_goals.md#L113) |
| 2 | “RunState is built from running culture outward. It helps runners understand the state they brought into a run, what the run demanded, and what they carried out of it.” | Positioning — explicitly called the positioning line in its source. | [user_goals.md, lines 118–119](user_goals.md#L118) |
| 3 | “A runner alone at 5am with no followers and no feed still feels like they belong to something.” | Positioning — recorded north-star sentence, rather than short public copy. | [user_goals.md, lines 120–121](user_goals.md#L120) |
| 4 | “RunState is a running app designed to understand a run beyond its numbers by interpreting the environment surrounding the experience.” | Positioning — current public project introduction. | [README.md, line 4](../../README.md#L4) |
| 5 | “More than a fitness tracker.” | Positioning — existing headline in the earlier creative direction. | [Earlier positioning, line 333](#1-positioning) |
| 6 | “A special weapon in a runner's arsenal.” | Positioning — quoted phrase in the earlier creative direction. | [Earlier positioning, line 337](#1-positioning) |
| 7 | “the app that knows you” | Positioning — identity shorthand in the cold-start discussion, which also cautions against pretending to know a runner before evidence exists. | [Earlier cold-start principle, line 309](creative_direction_ui.md#L309) |
| 8 | “personal in value, cultural in voice, communal only ever in texture.” | Positioning — exact fragment describing the product's relationship to culture and community. | [user_goals.md, line 122](user_goals.md#L122) |
| 9 | “the best run is not always the fastest” | Positioning — quoted principle behind the proposed Top Run Highlights feature. | [parked_feature_ideas.md, lines 116–117](parked_feature_ideas.md#L116) |
| 10 | “the best run is not always the fastest or longest run.” | Historical — exact fragment from the earlier Top Run Highlights rationale. | [Archived/UNIQUE_IDEAS.md, line 93](../Archived/UNIQUE_IDEAS.md#L93) |
| 11 | “progress is more than one number.” | Historical — exact fragment identified as a main theme in the earlier ideas document. | [Archived/IDEAS.md, line 182](../Archived/IDEAS.md#L182) |
| 12 | “a run has more than one result.” | Historical — exact fragment from the earlier brand-direction explanation. | [Archived/BRANDING.md, line 42](../Archived/BRANDING.md#L42) |
| 13 | “Clean enough for a boardroom, cool enough for the culture.” | Historical — identity sentence in the superseded visual brief; not a current design requirement. | [ui-design-brief-v1.md, line 20](../ui-design-brief-v1.md#L20) |
| 14 | “Small footprint, high meaning” | Positioning — exact fragment from the earlier creative direction. | [Earlier positioning, line 338](#1-positioning) |
| 15 | “you were seen, your work isn't unnoticed.” | Positioning — intended emotional effect of the post-run response, not previously designated a slogan. | [Signature moment, line 462](#11-the-signature-moment-resolved--see-0) |
| 16 | “the taste matched the discipline” | Music-specific — quoted signature-moment phrase associated with the AI reply card. | [Earlier signature-moment decision, lines 66–67](creative_direction_ui.md#L66) |
| 17 | “your best-run soundtracks” | Music-specific — existing expression for music tied to the runner's own run history. | [Earlier music-screen concept, line 323](creative_direction_ui.md#L323) |
| 18 | “RunState aims to help a runner feel seen after a run.” | Music-specific — purpose statement introducing the reflection feature's development story. | [music_reflection_development_story.md, line 61](music_reflection_development_story.md#L61) |
| 19 | “The music can answer the reflection without the reflection naming the song or explaining it.” | Music-specific — explanation of the reflection-to-song experience. | [music_reflection_development_story.md, line 64](music_reflection_development_story.md#L64) |
| 20 | “RunState is a run tracker that records performance and how each run felt, so runners can eventually learn what conditions help them perform their best.” | Historical — explicitly labeled “Pitch” in the earlier ideas document. | [Archived/IDEAS.md, line 78](../Archived/IDEAS.md#L78) |
| 21 | “The app should feel like a personal run tracker that helps runners understand how their runs are going, not just how far or how fast they ran.” | Historical — earlier brand-direction sentence, written as guidance rather than finished public copy. | [Archived/BRANDING.md, line 40](../Archived/BRANDING.md#L40) |

### Supporting principles, not public slogans

- **Identity pillars:** “RUN CULTURE,” “STATE-AWARE REFLECTION,” “MUSIC IDENTITY,” and
  “PRIVACY-FIRST PERSONAL INSIGHT” — [user_goals.md, lines 111–116](user_goals.md#L111).
- **Core product loop:** “log a run → track how you felt → learn something meaningful from it.”
  — [user_goals.md, line 134](user_goals.md#L134).
- **Internal design language:** “The UI dresses like a runner.”; “Restraint is the brand.”;
  “Fewer features, done beautifully.” — [Rules of the look](#6-rules-of-the-look).
- **Internal usability shorthand:** “bold skin, familiar bones” — [the existing guardrail](#10-guardrail-bold-skin-familiar-bones).
- **Historical design principle:** “Every screen should feel like it was art-directed, not just
  designed.” — [ui-design-brief-v1.md, line 22](../ui-design-brief-v1.md#L22).
- **Feature subtitle, not app slogan:** “Interesting habits and patterns from your runs.” —
  [Archived/IDEAS.md, line 231](../Archived/IDEAS.md#L231).

Ordinary UI questions, saved-run confirmations, Energy labels, and one-off reflection examples
are not included as brand candidates. The earlier names RunNet and MoodPace remain naming
history. The superseded claim “RunState is not a fitness tracker” is also excluded: the later
positioning explicitly keeps competent run tracking as the foundation.

### Recommendation for the next copy discussion — advice, not a prior decision

Start with these three existing lines. None is selected or approved by this recommendation:

1. **“RunState helps you understand what your run meant, not just what you recorded.”** — the
   clearest explanation of the product's distinct purpose.
2. **“a run has more than one result.”** — the shortest expression of the broader runner-state
   idea, preserving the existing source fragment rather than inventing a revised tagline.
3. **“the best run is not always the fastest”** — a concrete running principle that invites
   conversation about Energy, effort and context without implying a medical or training promise.

## October 23 — trailer and logo plan, saved for later

**Status: Planned, deferred.** Manley asked to preserve the presentation recommendation below,
not start production. Finish the active run screen and required existing-functionality work
first. He also explicitly wants a RunState logo and a custom Android launcher icon: the image
people tap on their phone should replace the generic Android graphic. No logo, icon, tagline or
final trailer script has been selected. Keep the accepted opening design and other screens.

Suggested order when this work resumes: finish/review Active Run UI; approve the logo and launcher
icon; capture the finished app; storyboard and produce the trailer. Settle the final branding
before final title/end cards and phone-launch recordings to avoid rework. This is documentation
only, not approval to generate assets, change the app, purchase anything or publish a video now.

### Purpose and format

Create a roughly 55-second trailer built around the working app for AfroTech conversations with
employers, leaders and other attendees, also usable afterward on a portfolio or professional
profile. Use it to earn interest, then offer the real phone demo and a clear explanation of
Manley's engineering/product decisions. Preserve the roadmap's short explanation, approximately
90-second demonstration and optional three-minute technical explanation as supporting material.

The central story: RunState connects recorded performance, the runner's experience and their
music into a meaningful finish. Follow one coherent run instead of listing unrelated features.
Favor real app recordings; device mockups, crops and transitions can frame them, but generated
visuals must not invent interface behavior. Let the actual reflection excerpt land rather than
cutting rapidly through the feature that makes the experience distinct.

| Approximate time | Material | Purpose |
| --- | --- | --- |
| 0–5 seconds | Accepted opening collage and RunState identity | Establish running culture and visual character. |
| 5–13 seconds | Runner preparing, optional Energy selection, countdown | Establish a personal starting point. |
| 13–23 seconds | Finished Active Run UI and real recorded metrics | Demonstrate functioning software. |
| 23–45 seconds | Completion and a strong excerpt from an actual generated reflection | Show the signature experience with enough time to understand it. |
| 45–55 seconds | Saved activity in History, then creator name and project link | Show durable value and enable follow-up. |

Timing is a proposed editing guide, not a locked script. Choose a dependable example after the
remaining UI and real-run review. Use actual app voice audio for the demonstrated reflection;
do not replace it with an advertising line and imply the app generated that result. Make any
controlled example or excerpt understandable as such. Energy Insights is a foundation for
noticing personal patterns, not proven fitness improvement or a universal readiness measure.
Avoid promises of perfect song matching or instantaneous generation in every circumstance.

### Production and presentation decisions

- Keep the established clean layouts, colorful running imagery, restrained motion and distinctive
  voices. Avoid effects that obscure what the product does.
- Make the story understandable with sound off using readable captions. Keep an offline video
  copy on the demo phone as a dependable backup.
- End with Manley Johnson, Creator of RunState, and an actual project/professional-profile link
  or QR code when chosen. Prefer an evergreen invitation such as Explore the project / Connect
  with Manley. Do not imply an app-store release that has not happened.
- The screenshot's ElevenLabs workflow is a starting point: confirm audience/message and footage,
  approve a short storyboard, assemble real screens, add voice/music/captions, review accuracy and
  mix/export. Its Studio supports imported video, speech, timeline alignment and captions:
  https://elevenlabs.io/docs/help-center/product/studio/studio/how-can-i-create-a-voiceover-for-my-video-using-studio
- Decide video music before editing. Spotify playback access is not permission to bake its
  recordings into a trailer; its developer policy prohibits synchronization with video. Use
  music cleared for the video, and do not present replacement background music as evidence that
  RunState selected that track. Source checked October 23: https://developer.spotify.com/policy
- For the employer conversation, prepare the real development story: music data alone initially
  failed to improve the writing; listening feedback led to revisions of song study/writing and
  real phone audio handoffs. Explain actual responsibilities and tools accurately. Refer to
  `music_reflection_development_story.md` rather than inventing a success story.

The logo/icon is a separate pending design choice. It should remain recognizable at small phone
icon sizes and fit the established RunState identity. Review visual options with Manley before
installing a replacement; no specific symbol, color scheme or use of the existing ring is assumed.
