---
name: ui-phase-handoff
description: State of the UI design phase as of August 6 2026. Log History screen is near-complete; records the working process, what's decided, what's parked, the backend requirements the screen surfaced, and what comes next. Written so a fresh session can pick up without re-deriving anything.
metadata:
  type: project
---

# UI phase — handoff, August 6 2026

Written because the session that produced this got very long. Everything needed to continue is here.
Amended August 7 2026: interaction behavior, `Share` status, the song-location requirement, the
`READ FROM` parking, and a corrected housekeeping section.

---

## 1. Where things stand

**Music Intelligence reached a stable stopping point on August 3** and is not being worked on.
Roadmap step one is closed. See `music_intelligence_v1_evaluation.md`.

**The UI phase started August 4 and the first screen is near-complete.**

**`RunState - LogPhase2.html` in the repo root is NOT the current state.** More stable and preferred
versions exist in Claude Design and have not been downloaded yet. The repo copy is a snapshot from
part-way through; changes have been made since. **Do not treat it as authoritative** — ask for the
current export before working from a file.

Earlier states are `logphase1.html` and `Music Replies.html`. The `RunState - Soundtrack Log v2–v6`,
`Type Scale`, `Metric Ring Options`, `Merge Options`, and `Accent Comparison` files are Cowork's
decision mockups, not builds.

**None of these files are committed.**

---

## 2. The working process — this is the important part

**Roles for this phase — reverted to the project's standard split:**

1. **Manley plans, with ChatGPT.** Direction, decisions, and what goes in a pass.
2. **Cowork reviews.** Checks work against the repo, flags contradictions, gives a verdict first with
   only the caveats that change the decision. Builds comparison mockups when a choice isn't obvious.
3. **Claude Design executes**, since it has the visual craft. It also holds the live file.
4. **Claude Code** if code is needed in this phase.
5. **Manley decides**, and reviews each result before the next pass goes out.

*(During August 4–6 Cowork also planned and wrote the handoffs. That was situational, not the
standing arrangement.)*

**Two process rules hold regardless of who writes the handoff.** Both were learned by getting them
wrong, and neither should be relaxed.

**Cowork's mockups are diagrams of decisions, not visual targets.** They show what goes where and
what marks state. They are deliberately plain. Claude Design produces the finish.

- **A handoff covers one pass.** A full-screen spec was written on August 5, and it was wrong — it
  threw away the ability to tell which change caused which result. Split into passes instead.
- **A handoff never includes other screens.** The same spec carried notes for four screens that
  weren't being built. Anything in the document that isn't the job is a chance to build the wrong
  thing.

**Files:** `HANDOFF - Log pass 2.md` is the most recent, `SPEC - RunState Log v1.md` is the running
decision queue for this screen — not a handoff, never sent whole.

---

## 3. The Log History screen — what's decided

Named **RunState Log History**. It absorbed Run History; it holds every run, not only runs with music.

**Structure**

- **The `14 runs, 14 records.` headline is removed.** The header is now the eyebrow and `Share` only.
  `Share` is a visual placeholder; no behavior is decided. The nearest real thing is the RunStyle
  Sound shareable card in `music_feature_register.md`.
- Metrics panel at top follows the open record, and rests on the most recent run when nothing is
  open.
- Filter pill: `All · PRs · Fastest · Longest`. Manley's set, and it beats the earlier
  `All · Music · Silence · Best` — Music would return most records, which isn't a filter.
- Records grouped by month, newest first. Every header carries month and year plus that month's
  total miles. Label left, total right, **no rule** — nothing else on the screen uses hairlines.
- Under a filter, the list sorts across everything and month headers disappear. Months are a
  browsing structure; Fastest and Longest are ranking structures, and they can't both hold.

**The row — three slots, three jobs, no overlap**

> **Tile** — music state: equalizer bars, or crossed-out headphones
> **Label** — run type: `Group run` · `Morning run` · `Lunch run` · `Evening run` · `Night run`.
> Company beats time of day; *Solo run* is deliberately not used because it would land on most rows
> **Text below** — song and artist, or for music-free runs, distance and finish time
> **Right column** — weather glyph above day and date. Time removed: nearly every run starts
> between 5:41 and 6:33, so the field said the same thing on every row

**The expanded record's stamp** now reads `SONG DETECTED — MILE 2.4` rather than the mile marker
alone. It names what the app noticed, not just where.

**Interaction.** Opening a record closes the one already open — one expanded record at a time. The
metrics panel updates to the opened record, and the reply is read on open. `REPLAY` is provisional.
Approved August 7: these are behavior decisions, not build artifacts, and they hold unless a backend
limit or a later direction change forces a revision. *Small open point, cheap to settle later:*
`creative_direction_ui.md` describes auto-read as **optional**, so whether it is always-on or a
default-on setting is unsettled.

**Music-free records.** One treatment covers both *ran in silence* and *not recorded*. The
distinction is real in the data — the reply system must never claim anything about music it doesn't
know about — but on screen it's the app narrating its own data quality. The crossed-out headphones
carries it. **No runner figure on these rows**: every row is a run, and marking only some implies
otherwise.

**Weather glyphs.** Outlined, no fill, uniform stroke. Sun, dawn, half moon, cloud, sun behind cloud,
rain, fog, wind. The moon is reserved for genuine night runs. **Dawn is its own glyph** because runs
cluster at 5:41–6:33 and *clear* would otherwise swallow almost every record.

**The visual foundation — current stable direction, August 6**

**Frosted panels over a photographic environment.** A runner in motion, foliage, rain sits behind
everything; translucent panels float over it. The screen reads bright because the panels glow, while
staying dark enough inside to hold text.

Manley: *"a more soft tone of Sony, to connect better with the environment of running — that's why I
also added 'light' in my description."* **Light meant luminous, not a white page.** Off-white and
cream were rejected specifically; lightness never was.

**This is a return to §5 of `creative_direction_ui.md`** — *"frosted glass over a warm, natural
world"* — written months earlier and buried. Neither Cowork nor the build referenced it; the
direction was rearrived at independently.

**Claude Design exposes live controls** for background surface, panel clarity, palette, and pill
style. That is the iteration surface — tune by looking, not by respecifying.

> ⚠️ **Before advising on anything visual, read the open-mind section at the top of
> `creative_direction_ui.md`.** Nothing about the look is locked, direction is found by making rather
> than specifying, and recorded decisions are references — never authority against a new idea.

**Where the Sony direction landed**

`creative_direction_ui.md` records a Sony-inspired direction from August 2, exploratory and not
locked: *design philosophy not visual template — visionary, simple, human-centered, confident,
premium; "smart, not intellectual" and "cool, not trendy."* Its stated visual translation was a
**clean light canvas**, disciplined black, one concentrated warm accent, sculptural hierarchy,
premium coming from proportion, spacing, materials, and precision rather than decoration.

**The direction split in two during this build, and only half of it survived.**

**The philosophy holds.** Restraint, one accent, quiet chrome, quality from proportion and spacing
rather than ornament. That is the Sony brief being met.

**The literal *clean light canvas* did not**, and the frosted-environment foundation above replaces
it. The August 5 light attempt was that translation taken literally, and it failed on execution
rather than on direction — a spec made of removals, with nothing left creating depth.

**So: Sony as a softened philosophy, expressed through material and environment rather than through a
white page.** That is a better fit for a running app than the original translation was, and it is
Manley's phrasing, not a reinterpretation.

**Rejected, with reasons**

- **The light palette.** Built August 5 and rejected on sight. The spec was a list of removals — no
  borders, no shadows, no gradients. In the dark build those read as restraint because translucency
  and depth were already carrying the quality; in light, nothing was left doing that work. If light
  is ever revisited: off-white and creams are out, and a light system needs something specified that
  *creates* depth rather than a list of things not to use.
- **Nature motif tiles.** Specified, then Manley changed direction. The tile carries music state, not
  identity — identity lives in the song title and run type.
- **Curved metrics on the ring's inner edge.** A number you have to tilt your head for fails exactly
  when the runner is most tired.

---

## 4. Outstanding on this screen

Not blocking; revisit later.

- **PRs.** Revisit later; nothing here is a requirement. Changes have already been made since these
  notes. The only durable item is that **what counts as a PR still needs defining** — the term covers
  several different achievements. Ideas discussed but not adopted: a marker on the record row so the
  PRs filter returns a list where something looks distinct, and a fuller treatment in the metrics
  panel naming the achievement rather than showing a badge.
- **Empty state — two different states, not one.** Current thinking as of August 8 2026, Manley's
  idea; nothing built and nothing settled.

  **Cold start, no runs at all — rich.** Show a sample record previewing what a logged run becomes,
  and let it go once the runner has one of their own. Two reasons it earns its place. First, this
  screen *is* the product's argument — the Soundtrack Log frame graded Distinct 9 in
  `idea_organization_analysis.md` for turning disposable replies into an accumulating record, and on
  day one that frame is invisible. Second, it answers the input question by demonstration: seeing a
  song, a weather glyph, a run type and a reply tells the runner what to give the app without a
  prompt or an onboarding tour, which suits this app's voice better than either.

  **The craft problem to solve is distinguishability.** The sample must be unmistakably not the
  runner's, or someone could believe they logged a run, or wonder later where it went. Worth being
  precise about the neighbouring rule: *never fabricate* was written about the app claiming things
  about the runner's **own** runs — the fabricated-telemetry failure from the music work. A clearly
  marked demo record isn't that. Still open: when the preview leaves — first real run, runner
  dismissal, or something else.

  **Empty filter, runs already exist — minimal.** PRs with no PRs yet, Fastest with a single run.
  The runner already knows what a record looks like by then, so a sample would be redundant and a
  little condescending. Different problem, different answer.

  **The metrics panel has the same cold-start hole.** It rests on the most recent run when nothing is
  open, and with zero runs there is no most recent run. Same screen, same day — worth deciding
  together.
- **`READ FROM` chips and `Remove response`.** **Left in place; removal is parked until explicitly
  revisited.** *Remove response*
  is mislabeled — it only collapses. The chips were meant to show what the reply drew on, which is
  the UI answer to fabricated telemetry, but as permanent static decoration they answer a question
  nobody asked. **The better form is on-demand** — revealed by tapping the reply. Worth parking
  rather than dropping. Note `REPLAY` will be alone in that row once they go.
- **Dawn and sun glyphs read too similarly** at small size.
- **`Morning run` will dominate** the label, given the run times. Milder than the `SOUNDTRACK`
  problem it replaced, and the weather glyph now carries differentiation.
- **The split-music view.** Not built, needs playback timing that doesn't exist. Design the expanded
  record so a second view can live inside it later without restructuring.

---

## 5. Backend requirements this screen surfaced

**This is the UI-first loop working.** Recorded as requirements, not absorbed as promises.

| Needed by the screen | Status |
| --- | --- |
| **Run start time** — time-of-day run labels, any displayed time | Fields and columns exist; `RunConsole` writes `null`. Needs a prompt, or arrives free on mobile |
| **Heart rate / BPM** — shown in the metrics panel | Not collected. Mobile phase |
| **Splits** — referenced by the old `READ FROM` chips, and by the split-music view | Not collected. Mobile/GPS phase |
| **Rolling pace comparison** — the reply says *"eleven seconds under your rolling pace"* | Removed as a comparison baseline in July. Manley: it returns as an app-visible stat; whether it returns as a *comparison* is decided at redesign |
| **Monthly aggregates** — month header totals | Derivable from stored runs; nothing new needed |
| **PR categories** — for the PRs filter and marker | Personal records exist in the console; categories aren't defined |
| **Song location** — the `SONG DETECTED — MILE 2.4` stamp, and the split-music view | **Not collected, not needed now, and parked for the mobile/GPS phase.** The mile marker requires a playback timestamp joined to time-aligned cumulative-distance/GPS telemetry; stored splits are not a prerequisite. An earlier non-GPS form could be time-based once playback and run-start timestamps exist. |

**Already available and used correctly:** distance, duration, pace, route name, surface, shoes, run
company, weather condition, temperature, pre- and post-run energy, effort.

---

## 6. What comes next

**Roadmap position:** step one (music to a stable direction) is done. Step two is rough screens.
Step three is the Core Running Foundation Review, driven by those screens. Then RunStyle V2 review,
then screen finalisation, then Spring Boot from the screen contracts, then mobile and GPS.

**Screens still to design**

- **State Scan** — the pre-run screen, first in the flow. Carries the progressive-input ladder: what
  gets asked versus prefilled. Stored energy domain is `LOW / MODERATE / HIGH` and that doesn't
  change; labels and presentation are open.
- **Run Complete** — a Claude Design mockup exists but hasn't been through this process.
- **RunStyle Sound** — the artists and songs recurring around strongest runs, plus the shareable
  card. See `music_feature_register.md`.

**Then the Core Running Foundation Review.** One question only: is the central journey — record a run,
preserve it safely, understand it, manage it, use it later — credible and structurally ready for a
real interface? Output is a short gap list, not a feature hunt. **"Manage" is the known thin spot:
there is no edit or delete for a logged run anywhere in the code.** That's ordinary CRUD against a
table where INSERT and SELECT already exist — the smallest item on any list, just never written.

**How screens drive code.** The gap table in section 5 is the mechanism. Screens expose what the
backend can't deliver, those become requirements, the foundation gets fixed, screens refine, and only
then do API contracts lock. Spring Boot gets designed **from** the screen payloads, not before them.

---

## 7. Housekeeping

**Committed as of August 7.** The working tree is clean. `RunState - LogPhase2.html` and
`logphase1.html` are tracked (`7aeb2c3`); the doc updates landed at `13776c9`. The Cowork decision
mockups and `Music Replies.html` were never tracked and are no longer in the repo — they served
their purpose.

**On per-pass handoffs and the decision queue.** `HANDOFF - Log pass 2.md` and
`SPEC - RunState Log v1.md` were never tracked by git and have since been deleted, deliberately.
That is correct: they specified a version Claude Design did not build — it made its own typographic
choices, which Manley preferred — so the documents describe a screen that does not exist. **The
built file is the source of truth, not the spec that was never followed.** Claude Design holds it
and can carry the treatment forward to later screens, which is what consistency actually depends on.

⚠️ **The durable rule, which outlives those two files:** per-pass handoffs and decision queues are
working documents. They live outside the repo and they get deleted. A decision only survives if it
is written into `docs/claude-memory/` before its working document goes away. **This project has
already lost decisions that way once** — see the July 9 DJ-session note in
`music_feature_register.md`.

**Live documents to read first:** `idea_organization_analysis.md`, `music_feature_register.md`,
`run_initiation_register.md`, `adr_001_runstyle_surfacing.md`. All four are indexed in `MEMORY.md`
and marked LIVE.
