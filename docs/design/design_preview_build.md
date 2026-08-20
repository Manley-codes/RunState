---
name: design-preview-build
description: "The public hosted design preview - what is live, where, how to update it, and what putting it on a real phone taught us. August 19 2026."
metadata:
  type: project
status: LIVE - a real public URL. Contents are early prototypes and are expected to be replaced.
---

# The public design preview

**What this is:** two RunState screens hosted on the open web so someone with no context can open a
link on their phone and see the work. Built August 16–19, 2026, with AfroTech in November as the
reason it exists.

**These are prototypes, not the app.** Everything on them uses example data. They will be replaced
as the design continues, and that's expected — this file describes what's live now, not a target to
preserve.

---

## What's live

**Repository:** `Manley-codes/runstate-preview` — public, separate from the main project repo.
Hosted with GitHub Pages.

| Page | URL | What it is |
| --- | --- | --- |
| Landing | `manley-codes.github.io/runstate-preview/` | Dark page, two cards, one line of framing. `index.html` |
| Start a run | `.../start.html` | Start Run V7 |
| Log history | `.../log.html` | Log History, City dawn background |

**How to update a screen:** upload a new file with the same name, replacing the old one. The URL
never changes, so any link already shared keeps working.

**One thing that will bite again: GitHub paths are case-sensitive.** Uploading `Start.html` while
the link points at `start.html` produces a 404 that looks like the hosting is broken. It isn't.
Match the filename exactly, and delete the old file if the casing changed.

---

## The Start screen preview

Start Run V7. A prep-only pass was run before hosting — no new design, just the things that make a
cold viewer's thirty seconds work:

- **Phone viewport treatment.** Below 700px the page drops the desktop stage and the phone mockup
  fills the screen. No bezel, no reserved gutter for controls.
- **The device's real safe-area inset is reserved, not the mock status bar's height.** This came
  from Claude Design and corrected an assumption: the mock status bar scales down to about 33px,
  which is *less* than a real one, so reserving the mock's height would put the header under the
  notch.
- **A line of guidance outside the device frame**, because this screen is a sequence and not
  self-explanatory: *Choose an energy level, press Start — then tap Pause, and hold Stop to end.*
  Without it, a viewer presses Start, watches a timer for ten seconds, finds nothing, and closes
  the tab.
- **The Reset Prototype chip stays.** It's honest about what this is and it lets a returning
  visitor clear the shoe bag.

---

## The Log screen preview

Log History, with the **City dawn** background.

Ten backgrounds were extracted from the export and compared as rendered screens rather than as
thumbnails. **Rain sprint was the best-looking screen of the set** — and was not chosen, because it
is a dark mode rather than a background, and it wouldn't sit next to the light Start screen. Worth
remembering: it may be the better answer to a different question later.

### The correctness pass that ran first — August 16

Five fixes went in before this screen was hosted. All five landed.

1. **Phone-viewing variant.** Same treatment as Start — the mockup fills the viewport instead of
   being a small phone inside a phone, and the clarity control stops reserving a 344px gutter.
2. **`READ FROM` chips removed** from the persistent expanded record. Provenance isn't discarded as
   an idea — the intended later form is revealed on demand from the reply, which is a separate
   future pass.
3. **The reply card sizes to its content.** It previously animated to a fixed 470px on a container
   with `overflow: hidden`. Current copy fits, so nothing was visibly clipping — but a clipped
   reply is a reply whose ending exists and can't be seen, which is worse than a shorter reply.
4. **Month headers computed from the data.** The grouping logic recognised only `JUL` and `JUN`;
   any other month rendered with no header at all, silently. Totals are now calculated rather than
   typed.
5. **One source for each reply's text.** It existed twice — once for display, once for speech.
   They matched, but they could drift, and a reply that reads differently from how it looks is a
   hard bug to notice.

---

## What a real phone taught us

Three things surfaced only after this went on an actual device. That's the value of hosting it.

**The voice is robotic on iPhone and correct in desktop Edge.** Same file, same code. This is
structural, not a bug: browser speech synthesis can only use voices already installed on the device,
and phones ship compact voices while keeping the better ones behind a manual download most people
never make. It is not fixable by choosing different persona names. Full detail in
`ui_phase_handoff.md` §3 and §5 — a production version needs the platform's own speech system or a
hosted text-to-speech provider, which is a real architecture decision, not a settings tweak.

**The Start screen was choppy on mobile** during the map-into-circle collapse, and long-pressing
Stop selected text on the screen behind it. Both were handled. ⚠️ **The fix that actually worked
was found in a Claude Design thread and is not written down anywhere** — including here. If the
choppiness returns after a future export, that reasoning will have to be rediscovered.

**Desktop was fine throughout.** Both screens ran smoothly there, including the voice. Testing on
desktop alone would have caught none of this.

---

## Where this sits for someone finding the project

**The main repo is the front door, not the preview.** The preview is one link inside it. A visitor
who lands on a bare preview sees two screens and no engineering; a visitor who lands on the repo
sees the tests, the decisions and the design record, and can click through to see it move.

**Still to do:** put the preview link at the top of the main repo's `README.md`. Not done yet.

**Also stale:** `README.md` in the preview repo still describes the older single-screen setup, from
before the Start screen and the landing page existed.

---

## Related files

- `design_start_run_v7.md` — what the Start screen actually does
- `ui_phase_handoff.md` — the Log screen's decisions, §3 and §4
- `creative_direction_ui.md` — the visual direction these are working within
