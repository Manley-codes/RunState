---
name: ui-phase-handoff
description: State of the UI design phase as of August 6 2026. Log History screen is near-complete; records the working process, what's decided, what's parked, the backend requirements the screen surfaced, and what comes next. Written so a fresh session can pick up without re-deriving anything.
metadata:
  type: project
---

# UI phase — handoff, August 6 2026

Written because the session that produced this got very long. Everything needed to continue is here.

---

## 1. Where things stand

**Music Intelligence reached a stable stopping point on August 3** and is not being worked on.
Roadmap step one is closed. See `music_intelligence_v1_evaluation.md`.

**The UI phase started August 4 and the first screen is near-complete.**

**Current file: `RunState - LogPhase2.html`** in the repo root. Earlier states are `logphase1.html`
and `Music Replies.html`; the `RunState - Soundtrack Log v2–v6`, `Type Scale`, `Metric Ring Options`,
`Merge Options`, and `Accent Comparison` files are Cowork's decision mockups, not builds.

**None of these files are committed.**

---

## 2. The working process — this is the important part

Established August 4 and it works. Do not collapse it back into one big pass.

1. **Decisions get made in conversation with Cowork**, one change at a time, with mockups built to
   compare options where a choice isn't obvious.
2. **Cowork writes a short handoff for Claude Design** — one pass, one job.
3. **Claude Design executes**, since it has the visual craft. It also holds the file in its platform.
4. **Manley reviews the result before the next pass goes out.**

**Cowork's mockups are diagrams of decisions, not visual targets.** They show what goes where and
what marks state. They are deliberately plain. Claude Design produces the finish.

**Two rules learned the hard way:**

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

- Metrics panel at top follows the open record, and rests on the most recent run when nothing is
  open. *(The resting state is currently wrong in the build — see section 4.)*
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

**Music-free records.** One treatment covers both *ran in silence* and *not recorded*. The
distinction is real in the data — the reply system must never claim anything about music it doesn't
know about — but on screen it's the app narrating its own data quality. The crossed-out headphones
carries it. **No runner figure on these rows**: every row is a run, and marking only some implies
otherwise.

**Weather glyphs.** Outlined, no fill, uniform stroke. Sun, dawn, half moon, cloud, sun behind cloud,
rain, fog, wind. The moon is reserved for genuine night runs. **Dawn is its own glyph** because runs
cluster at 5:41–6:33 and *clear* would otherwise swallow almost every record.

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

- **The metrics panel's resting state is wrong.** It follows the open record correctly, but with
  nothing open it shows an arbitrary run rather than the most recent.
- **PRs.** Manley's direction: lime as the accent, a reward marker rather than a trophy or medal
  (those read gamified). Cowork's note — it needs to appear **in two places**: a small marker on the
  record row so the PRs filter returns a list where something looks special, and a fuller treatment
  in the metrics panel naming what the record was (*Longest run*, *Fastest pace*). Naming the
  achievement beats a badge. **What counts as a PR still needs defining.**
- **Empty state** for a filter with no results. Cold-start rule applies: never fabricate, never leave
  a blank.
- **`READ FROM` chips and `Remove response`.** Agreed to remove; left in for now. *Remove response*
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

**Nothing in the repo root is committed** — three handoffs, the decision queue, and eleven HTML files
including six Cowork mockups that have served their purpose. Worth a cleanup and a commit before the
next screen starts.

**Live documents to read first:** `idea_organization_analysis.md`, `music_feature_register.md`,
`run_initiation_register.md`, `adr_001_runstyle_surfacing.md`. All four are indexed in `MEMORY.md`
and marked LIVE.
