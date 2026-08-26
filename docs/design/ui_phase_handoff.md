---
name: ui-phase-handoff
description: State of the UI design phase through August 25 2026. Start, Active Run, Run Complete and Log History have stable prototype foundations; records the process, decisions, backend requirements, and current queue.
metadata:
  type: project
---

# UI phase — handoff, updated August 25 2026

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
Amended August 20 2026: the Log correctness pass and three Start passes landed, and both screens went
up as a public preview. The Start screen's current description moved to `design_start_run_v7.md`; the
preview itself is documented in `design_preview_build.md`. Section 7's rule about working documents
was narrowed after it was being applied more broadly than intended. The mobile post-run contract was
also refined: RunState prepares four Energy-conditioned responses after durable save; Energy selects
the immediate response, while Effort is stored for longitudinal learning rather than changing it.
Amended August 22 2026: the Run Complete pass was exercised through happy, no-selection, late-input,
save-failure, reflection-failure, retry, reload, History and Back paths. Energy absorption now leads
directly into reflection arrival; Effort uses a lightweight sheet; and Run Complete and Log History
occupy one permanent phone shell backed by one stored run record.
Amended August 25 2026: saved-run management Pass 2 made the Java console path usable through
View Run History. It supports real-ID selection, post-run Energy/Effort updates, confirmed deletion,
stale-History failure handling, and silent PR rebuilds. The mobile History interface remains a
prototype, and no full editor for other run fields was added.

---

## 1. Where things stand

**Music Intelligence reached a stable stopping point on August 3** and is not being worked on.
Roadmap step one is closed. See `music_intelligence_v1_evaluation.md`.

**The UI phase started August 4. Log History and Start/Active Run now have stable design
foundations.** Neither is production mobile code. **The Start screen's current behavior lives in
`design_start_run_v7.md`** — `design_start_active_run.md` describes the August 13 version and is now
history. The optional energy rules still live in `design_state_scan.md`.

**Run Complete now also has a stable, tested prototype contract.** It saves before success language,
absorbs a chosen Energy state into the visualizer and reveals the matching reflection from that same
motion, keeps Effort separate and editable, preserves the run through reflection failure, and moves
to Log History inside one fixed phone shell. Canonical behavior lives in
`design_run_response_system.md` and `design_effort_cost.md`.

**Both screens are now publicly hosted** at `manley-codes.github.io/runstate-preview`, built
August 16–19 as a public phone-review surface. What's live, how to update it, and what testing on a
real phone surfaced are all in `design_preview_build.md`.

**`RunState - LogPhase2.html` and `RunState - logphase1.html` in the repo root are NOT the current
state.** They are snapshots from part-way through, kept as history. The current exports are the
hosted ones. **Do not work from the repo-root files** — use the hosted version, or ask Claude Design
for the current export.

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

**A correctness pass ran August 16 and all five items landed.** Nothing new was designed; these were
fixes to the existing build. In short: a phone-viewport variant, the `READ FROM` chips removed, the
reply card sized to its content instead of a fixed 470px ceiling, month headers computed from the
data instead of recognising only `JUL` and `JUN`, and one source of truth for each reply's text
instead of separate copies for display and speech. Full detail with the reasoning behind each is in
`design_preview_build.md`.

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
- **`READ FROM` chips — removed August 16, done.** The chips were meant to show what the reply drew
  on, which is the UI answer to fabricated telemetry, but as permanent static decoration they
  answered a question nobody asked. Removing them did **not** discard provenance as an idea: the
  better later form is on-demand, revealed from the reply when a runner wants to know what informed
  it. **That on-demand form has not been designed** and is a separate future pass.
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
| **Active run session states** — countdown, running, paused, resumed, stopped | Lifecycle, recovery and timestamp contract approved August 25; no mobile implementation exists. Countdown creates no session; Running persists immediately; Paused remains durable; resume is an action; hold-to-end completes. Recovery restores only through the last trustworthy checkpoint. Canonical contract: `run_initiation_register.md`. |
| **Live now-playing metadata and music-reactive visualizer** | Prototype only. Needs music-provider/playback integration and a defined no-music state; must fail independently of core tracking. |
| **Natural reflection speech** | The Edge-based six-voice selector is a successful design prototype, not a production TTS choice. Provider/platform voice availability, licensing, cost, offline behavior, accessibility controls, and cross-device consistency remain open. **Cross-device inconsistency is now observed rather than anticipated** — see §3. A production version cannot rely on browser speech synthesis; it needs either the platform's own speech system, which exposes better voices than the web layer does, or audio from a hosted text-to-speech provider. That choice is a real architecture decision with cost, licensing and offline consequences, not a settings tweak. |
| **Run Complete save / response separation** | Mobile must save the run durably before saying `Run saved`, then show metrics with a deterministic factual audio receipt. After save, it prepares four responses from one factual foundation: `Spent`, `Feeling Good`, `Powered Up`, and no selection. Energy selects the one immediate response that is revealed and stored. Effort does not change it; Effort persists for future comparisons, Quiet Gains and longitudinal learning. `Saved` means durable on-device storage, not completed cloud sync. |

**Already available and used correctly:** distance, duration, pace, route name, surface, shoes, run
company, weather condition, temperature, pre- and post-run energy, effort.

---

## 6. What comes next

**Roadmap position:** the earlier music lane is at a stable stopping point. The immediate product
sequence is the remaining rough screens, then the Core Running Foundation Review, then a full music
feature inventory and prioritization before choosing the next music surface. RunStyle Sound is one
candidate in that later review, not a predetermined rough screen. RunStyle V2 review, screen
finalisation, Spring Boot from the screen contracts, and mobile/GPS remain later work.

**Immediate sequence — one small pass at a time**

**Completed August 13:** the pre-run energy, Start and Active Run pass reached a stable prototype
foundation, and the most-recent-record quick peek was added to Log History with its accepted timing
above.

**Completed August 16–19, four passes:**

1. **Log History correctness pass** — five fixes, all landed. Summarised in §3.
2. **Start Run, the RunStyle card** — the pre-run screen now shows the runner's last run at this
   place. This is the one element on that screen saying something only RunState can say. Produced V7.
3. **Start Run, energy absorbed into the ring** — choosing an energy level sends the tiles up into
   the ring and the controls rise into the space. Manley added an Undo afterward.
4. **Start Run, preview prep** — phone viewport, a guidance line, no new design.

**Then both screens went up as a public preview.** See `design_preview_build.md`.

**For current Start behavior read `design_start_run_v7.md`**, not `design_start_active_run.md` and
not the older State Scan notes.

1. **Progressive-input ladder planning — partially complete and deferred.** The shoe slice now has
   an accepted interactive foundation:
   `Add Shoes` search, saved/add/select behavior, Start-screen confirmation, and per-shoe mileage.
   `design_shoe_selection.md` is canonical; image cleanup and the accepted recoverable Undo removal
   remain. Route, surface and run company return in a later input pass; they do not block the
   Foundation Review. The route icon is still an entry point, not the answer.
2. **Completed August 22 — Run Complete rough-screen pass.** All three
   Energy choices are visible and prominent at rest, while Effort alone stays behind the quieter
   `EFFORT +` action. Either, both or neither may be recorded; selecting Energy never opens Effort;
   and ignoring them stores unknown without a `Skip` action. The response contract is also settled:
   after durable local save, metrics and a short factual audio receipt appear immediately while
   RunState prepares four responses from one shared factual and evidentiary foundation — `Spent`,
   `Feeling Good`, `Powered Up`, and no selection. Energy selects the immediate response; a
   prototype-tuned fallback selects the no-selection response. Only that response is revealed,
   spoken and stored. The candidates may differ creatively in interpretation, emphasis and
   structure; they are not restricted to changing the ending. Effort does not change or regenerate
   the immediate response. It saves for future comparisons, Quiet Gains, runner learning, RunStyle
   or Run Rhythm analysis, and accurate historical references in later messages. No group label is
   used. Choosing Energy immediately sends its square into the visualizer and the selected reflection
   arrives directly from that choreography. No Energy uses a quiet fallback window; Effort opens in
   a lightweight sheet and may be saved late without regeneration. Save and reflection failure,
   retry, reload, History continuity and Back restoration all passed the final sweep. Canonical
   detail lives in `design_run_response_system.md` and `design_effort_cost.md`.
   Writing and evaluating the four candidate responses remains in the separate AI/music-response
   lane; this pass decides how the selected response appears.
3. **Log History refinement pass.** Resolve the cold-start and empty-filter states, mock the expanded
   record's `REPLY | SPLITS` states, and revisit the remaining row/glyph QA. This is when the expanded
   interaction is designed; its real split-and-song implementation still waits for the required
   telemetry.

**Core Running Foundation Review completed August 25.** The central journey — record a run,
preserve it safely, understand it, manage it, use it later — is credible in the completed-run
console, with four bounded gaps identified before mobile contracts. The comparison-trust gap is now
fixed: same-route runs must also pass the existing distance band. The active-session lifecycle,
recovery and timestamp contract is now approved but not implemented. The remaining contract gaps
are local-first identity/sync and a durable selected-reflection record. **Saved-run management is
usable in the Java console through View Run History.** It
displays database IDs, updates post-run Energy or Effort after storage confirmation, confirms
deletion, and silently rebuilds PR flags after a delete.
Missing IDs and storage failures leave memory unchanged and end the session with restart guidance.
The mobile History interface remains a prototype, and a full editor for distance, route, date,
weather, music, or other fields remains a separate later decision.

**After the Foundation Review: music feature inventory and prioritization.** Review the full music
layer before choosing what to design or build next. RunStyle Sound remains a valid candidate — its
personal view and shareable card are recorded in `music_feature_register.md` — but it is not the
automatic next screen. Use `design_music_selection_system.md` to separate features that improve
song selection from reflection, record-keeping and expression ideas before prioritizing them.

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

**What happens to working documents — narrowed August 20, 2026.**

Per-pass handoffs and decision queues live outside the repo and get deleted when they've served
their purpose. So anything worth keeping has to move into `docs/design/` before that happens. The
project has lost something that way once — see the July 9 DJ-session note in
`music_feature_register.md`.

⚠️ **This was being read too broadly, and Manley flagged it.** As originally written it implied that
any choice made during a design session had to be written into the repo or it "didn't count," which
turned active exploration into a series of forced commitments. That was never the point.

**What it actually means:** move the *conclusions* — why something failed, a constraint that was
discovered, reasoning that's worth having later. Working notes can stay working notes. A design
choice made mid-exploration is provisional and does not need to be promoted into a project decision
to be legitimate.

The collaboration side of this — that choices are provisional unless Manley says otherwise, and that
nobody else decides on his behalf that something is durable — is recorded in `collab_style.md`.

**Live documents to read first:** `idea_organization_analysis.md`, `music_feature_register.md`,
`run_initiation_register.md`, `adr_001_runstyle_surfacing.md`. All four are indexed in `MEMORY.md`
and marked LIVE.
