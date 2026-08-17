---
name: ui-phase-handoff
description: State of the UI design phase through August 13 2026. Log History and Start/Active Run have stable foundations; records the process, decisions, backend requirements, and current queue.
metadata:
  type: project
---

# UI phase — handoff, updated August 13 2026

Written because the session that produced this got very long. Everything needed to continue is here.
Amended August 7 2026: interaction behavior, `Share` status, the song-location requirement, the
`READ FROM` parking, and a corrected housekeeping section.
Amended August 8 2026: State Scan became a Start-screen energy selector, the Log History empty states
were separated, and the expanded record gained the current `REPLY | SPLITS` direction.
Amended August 9 2026: added the most-recent-record quick peek and the current visual vision.
Amended August 13 2026: reconciled the completed Start/Active Run design pass, its current visual
proof and pause controls; then recorded the completed most-recent-record quick peek and the remaining
four-task queue.
Amended August 14 2026: bounded post-run Energy and Effort as compact optional additions for the
upcoming Run Complete pass; no sequential required-question flow carries into mobile.
Amended August 16 2026: separated the immediate factual run receipt from the contextual reflection,
settled late-input behavior, and left exact response timing to the Run Complete prototype.

---

## 1. Where things stand

**Music Intelligence reached a stable stopping point on August 3** and is not being worked on.
Roadmap step one is closed. See `music_intelligence_v1_evaluation.md`.

**The UI phase started August 4. Log History and Start/Active Run now have stable design
foundations.** Neither is production mobile code. The Start screen's canonical current behavior,
including its active tracking transformation and Pause → Stop / Play state, lives in
`design_start_active_run.md`. Its optional energy rules live in `design_state_scan.md`.

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
metrics panel updates to the opened record, and the reply is read on open. Approved August 7: these
are behavior decisions, not build artifacts, and they hold unless a backend limit or a later
direction change forces a revision.

**Most-recent-record quick peek — prototype completed and accepted August 13.** When Log History
opens, the most recent record automatically expands and then returns to its closed state. It uses the
same expansion and collapse behavior as an ordinary record interaction, providing a clear glimpse of
the richer state without leaving the row open.

The accepted prototype timing is longer than the original sub-half-second estimate. Expansion is
triggered `0.32s` after entry, and collapse is triggered at `1.20s`. The row's existing transition is
`780ms` in each direction: it reaches full height at roughly `1.10s`, remains fully open for about
`0.10s`, and finishes closing around `1.98s`. That produces approximately **1.66 seconds of visible
motion** from first movement to fully closed. The brief fully-open dwell — not an artificially fast
total animation — is what keeps it feeling like a peek. The earlier “less than half a second” note is
superseded.

`REPLAY` remains **provisional and currently leans toward removal, but removal is not decided.** It
was added so Manley could repeatedly hear the voice replies while judging and adjusting them; that
testing purpose is not evidence that runners need historical playback. If later user testing shows
that people want to hear a saved reply again, it can remain or return. Separately,
`creative_direction_ui.md` describes auto-read as **optional**, so whether reading on open is
always-on or a default-on setting is still unsettled.

**Voice-playback prototype — successful test result, not a production voice decision.** The current
Claude Design prototype exposes one voice selector in the tweak controls with six natural Microsoft
Edge personas: Aria (American, calm/reflective), Guy (American, motivational), Sonia (British,
sophisticated), Connor (Irish, slightly informal), Ezinne (Nigerian, observant), and Prabhat (Indian,
productive). Three are women and three are men. Manley tested the exported versions in Edge and
found the voices smooth and human enough for the design prototype.

The playback treatment matters as much as the persona: synthesize each reply as one continuous
utterance; use punctuation and phrase boundaries to create natural cadence; speak pace,
temperature, heart rate, distance, and time as human language rather than raw notation; and avoid
audible joins, clipped fragments, uniform robotic timing, exaggerated accents, or pitch-shifting one
base voice into several characters. Motivational, productive, sophisticated, lightly informal,
calm, and observant/reflective deliveries are all valid variations — no single emotional register
should govern every run.

The downloaded standalone HTML files preserve a browser prototype, not deployable voice assets or a
chosen production TTS provider. Edge supplies those voices at runtime.

**Confirmed August 16, 2026 on a real device.** The same hosted file sounds correct in desktop
Edge and robotic in Safari on an iPhone. The cause is structural, not a bug: browser speech
synthesis can only use voices already installed on the device, and phones ship compact voices
while keeping the higher-quality ones behind a manual download most people never make. This is
not fixable by choosing different persona names. Production voice availability, platform consistency, licensing,
offline behavior, and cost remain open. Do not treat the six names or the Edge engine as locked.

**Music-free records.** One treatment covers both *ran in silence* and *not recorded*. The
distinction is real in the data — the reply system must never claim anything about music it doesn't
know about — but on screen it's the app narrating its own data quality. The crossed-out headphones
carries it. **No runner figure on these rows**: every row is a run, and marking only some implies
otherwise.

**Weather glyphs.** Outlined, no fill, uniform stroke. Sun, dawn, half moon, cloud, sun behind cloud,
rain, fog, wind. The moon is reserved for genuine night runs. **Dawn is its own glyph** because runs
cluster at 5:41–6:33 and *clear* would otherwise swallow almost every record.

**The visual vision — primary working direction, updated August 13**

> **RunState should feel like a purposeful running experience held within a soft, sensorial
> atmosphere. Real running environments, physical exertion, sweat, movement, and confidence give
> it athletic substance; strategically placed dark elements and precise structure add premium
> discipline; and everything remains productive and forward-moving. The surrounding softness
> never weakens the physical intensity—it gives that intensity somewhere fresh, human, and
> inviting to live.**

The running is not soft; the world holding it is. Physical fitness remains first. Softness is the
surrounding visual atmosphere, while real running environments, intensity, sweat, confidence,
productivity, and premium discipline give it substance. Soft colours dominate; near-black, darker
colours, or darker shades of those colours are used strategically. Real-life running imagery is part
of the identity and may carry indirect sensuality through natural light, reflected or sweaty skin,
movement, attitude, and confident editorial photography.

**Productivity remains a top identity trait.** The experience must stay clear, useful, and
forward-moving rather than becoming passive or spa-like.

**Frosted panels over a photographic environment remain a material approach, not the whole
identity.** They let real environments and their colours live through the interface. Sony is now a
supporting influence for precision, spacing, materials, restraint, and premium structure; its heavy
technological atmosphere is not the target. No single colour, including green, is the identity.

The full reasoning, updates to older palette/art/tonal-arc statements, the August 13 Start/Active Run
working proof, and open execution rules live in `creative_direction_ui.md`.

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

**The flat August 5 execution of the *clean light canvas* did not survive.** It failed because the
spec removed borders, shadows, gradients, and atmosphere without replacing their depth — not because
light or near-white is wrong for RunState. The foggy porcelain Start/Active Run field now proves that
a luminous near-white surface can work when tonal variation, photography, material, or motion gives
it substance.

**So: Sony survives as a supporting discipline, not the visual atmosphere.** Its precision and
premium restraint can strengthen the soft, sensorial running environment without dominating it.

**Rejected, with reasons**

- **The flat August 5 light treatment.** Rejected on sight because it was a list of removals — no
  borders, no shadows, no gradients, and nothing else creating depth. This rejects an untreated,
  generic execution, not lightness itself. The current foggy near-white screen is the successful
  counterexample; any future light surface still needs a deliberate atmospheric source.
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
- **`READ FROM` chips — current direction is removal from the persistent row.** The chips were meant
  to show what the reply drew on, which is the UI answer to fabricated telemetry, but as permanent
  static decoration they answer a question nobody asked. Removing the chips does **not** discard
  provenance as an idea: the better later form remains on-demand, revealed from the reply when a
  runner wants to know what informed it.
- **`Remove response`** remains a separate wording/behavior cleanup if the control survives; it only
  collapses the record and does not remove anything.
- **Dawn and sun glyphs read too similarly** at small size.
- **`Morning run` will dominate** the label, given the run times. Milder than the `SOUNDTRACK`
  problem it replaced, and the weather glyph now carries differentiation.
- **The split-music view — accepted later direction, presentation refined August 8.** This is the
  already recorded second view inside the expanded record, not a newly separate music feature. The
  current interaction direction is a two-view control in the existing action row: `REPLY | SPLITS`.
  `REPLY` is the default; `SPLITS` changes the body of the same expanded record rather than opening a
  permanent new screen.

  The first design target is the familiar running-app split list: split number/distance, time, pace,
  and the song title connected to that part of the run. **Song title only in this view — no album or
  artist.** Do not manufacture a one-song-per-split relationship: a song may cross multiple splits,
  and several songs may overlap one split, so the eventual treatment must be able to show overlap or
  a song-change location honestly. Both split timing and song timing read from shared run-session
  telemetry; music does not build a second split system.

  The switch and both states should be explored during the later Log History refinement pass so the
  panel reserves the right structure. A shippable split view waits for automatic splits from the
  mobile/GPS phase plus structured song playback timestamps. `REPLAY` does not currently have a
  settled place beside this switch because its removal is still open.

---

## 5. Backend requirements this screen surfaced

**This is the UI-first loop working.** Recorded as requirements, not absorbed as promises.

| Needed by the screen | Status |
| --- | --- |
| **Run start time** — time-of-day run labels, any displayed time | Fields and columns exist; `RunConsole` writes `null`. Needs a prompt, or arrives free on mobile |
| **Heart rate / BPM** — shown in the metrics panel | Not collected. Mobile phase |
| **Splits** — required by the split-music view | Not collected. Mobile/GPS phase |
| **Rolling pace comparison** — the reply says *"eleven seconds under your rolling pace"* | Removed as a comparison baseline in July. Manley: it returns as an app-visible stat; whether it returns as a *comparison* is decided at redesign |
| **Monthly aggregates** — month header totals | Derivable from stored runs; nothing new needed |
| **PR categories** — for the PRs filter and marker | Personal records exist in the console; categories aren't defined |
| **Structured song playback history** — every song title plus when it started and stopped during the run | Not collected. Does not require GPS and can exist earlier; it becomes distance-aware only when joined to run timing/GPS telemetry |
| **Song location** — the `SONG DETECTED — MILE 2.4` stamp, and the split-music view | **Not collected, not needed now, and parked for the mobile/GPS phase.** The mile marker requires a playback timestamp joined to time-aligned cumulative-distance/GPS telemetry; stored splits are not a prerequisite. An earlier non-GPS form could be time-based once playback and run-start timestamps exist. |
| **Active run session states** — countdown, running, paused, resumed, stopped | Not present in the console flow. The mobile tracker needs a durable local session state machine. Current UI behavior: Pause reveals Stop and Play; Play resumes; Stop ends. Stop confirmation is not yet decided. |
| **Live now-playing metadata and music-reactive visualizer** | Prototype only. Needs music-provider/playback integration and a defined no-music state; must fail independently of core tracking. |
| **Natural reflection speech** | The Edge-based six-voice selector is a successful design prototype, not a production TTS choice. Provider/platform voice availability, licensing, cost, offline behavior, accessibility controls, and cross-device consistency remain open. **Cross-device inconsistency is now observed rather than anticipated** — see §3. A production version cannot rely on browser speech synthesis; it needs either the platform's own speech system, which exposes better voices than the web layer does, or audio from a hosted text-to-speech provider. That choice is a real architecture decision with cost, licensing and offline consequences, not a settings tweak. |
| **Run Complete save / reflection separation** | Mobile must save the run durably before saying `Run saved`, show metrics with a deterministic factual audio receipt, then request the contextual reflection separately after the optional-input window. `Saved` means durable on-device storage, not completed cloud sync. Late Energy/Effort values persist for future learning but never regenerate the reflection. |

**Already available and used correctly:** distance, duration, pace, route name, surface, shoes, run
company, weather condition, temperature, pre- and post-run energy, effort.

---

## 6. What comes next

**Roadmap position:** step one (music to a stable direction) is done. Step two is rough screens.
Step three is the Core Running Foundation Review, driven by those screens. Then RunStyle V2 review,
then screen finalisation, then Spring Boot from the screen contracts, then mobile and GPS.

**Immediate sequence — one small pass at a time**

**Completed August 13:** the pre-run energy, Start, and Active Run pass has a stable prototype
foundation, and the most-recent-record quick peek has been added to Log History with its accepted
timing above. See `design_start_active_run.md`; do not re-derive Start behavior from the older State
Scan notes.

1. **Progressive-input ladder planning.** The shoe slice now has an accepted interactive foundation:
   `Add Shoes` search, saved/add/select behavior, Start-screen confirmation, and per-shoe mileage.
   `design_shoe_selection.md` is canonical; image cleanup and the accepted recoverable Undo removal
   remain. Continue with route, then decide where surface and run company are asked, suggested,
   prefilled and corrected. The route icon is still an entry point, not the answer.
2. **Run Complete rough-screen pass.** A Claude Design mockup exists but has not been through this
   process. The August 16 correction partly supersedes the August 14 addition pattern: all three
   Energy choices are visible and prominent at rest, while Effort alone stays behind the quieter
   `EFFORT +` action. Either, both or neither may be recorded; selecting Energy never opens Effort;
   and ignoring them stores unknown without a `Skip` action. The response contract is also settled:
   after durable local save, metrics and a short factual audio receipt appear immediately;
   the one-time contextual reflection follows after the runner stops adding values, with a longer
   fallback if nothing is entered. Late values still save for future patterns and comparisons but do
   not regenerate the reflection. `ADD TO THIS RUN` is no longer a settled group label now that only
   one input is collapsed. Exact layout, motion, group label, discoverability and timing are found in
   this prototype pass rather than specified as a fixed delay. Canonical detail lives in
   `design_effort_cost.md` and `design_run_response_system.md`. Rewriting the reflection is explicitly
   outside this screen pass; only its placement, timing and input snapshot are in scope.
3. **RunStyle Sound rough-screen pass.** The artists and songs recurring around strongest runs, plus
   the shareable card. See `music_feature_register.md`.
4. **Log History refinement pass.** Resolve the cold-start and empty-filter states, mock the expanded
   record's `REPLY | SPLITS` states, and revisit the remaining row/glyph QA. This is when the expanded
   interaction is designed; its real split-and-song implementation still waits for the required
   telemetry.

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
