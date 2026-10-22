---
name: design-shoe-selection
description: "Current shoe selection and mileage direction as of August 14 2026 — the first resolved slice of the progressive-input ladder"
metadata:
  type: project
status: READY TO TEST — October 22 native feature installed and software/device checks passed; user visual and real-run review remain
---

# Shoe selection and mileage

## October 22, 2026 — native shoe feature

Manley explicitly authorized completing the shoe mechanics and redesigning its Start object while
away. Energy discussion remains paused; no commit/push or other new feature is authorized by this slice.

The original `Add Shoes` flow is retained in a near-white/sage bottom sheet: searchable saved pairs
first, a small six-model browse list, and a custom brand/model entry for any other pair. Optional
nicknames distinguish two physical pairs of the same model. Selecting or adding a pair closes only
after storage confirms it. `Run without tracking shoes` explicitly clears the remembered selection;
dismissal leaves it unchanged. This is local selection, not a product recommendation or live catalog.

The Start control uses an original illustrated sneaker with an irregular sage/lime backing, closer
to the music artwork and visually distinct from the Start dial. Its footprint preserves the main
Start placement. Selected model/nickname and mileage replace `LATER`. Illustrations are neutral
shoe artwork, not photographs of specific listed products: the recovered prototype had mismatched
product filenames/labels and did not contain photo licenses, so those product pictures are not reused.

Room 19 stores physical-pair IDs and current selection. The official start freezes the selected ID
in the existing run row; pause/resume, start retries and recovery retain it. Mileage is derived from
finalized GPS runs with known measured distance, once per run ID. Pending completion, discarded runs,
Samples and unavailable distance add nothing. A measured partial GPS distance counts only the known
distance, with no extrapolation. Old runs remain unassigned; no historical mileage is invented.

Removal archives the pair, never deletes its run associations. Undo restores it and restores the
former selection only if the runner has made no later selection. Archived pairs can be restored
with all their mileage. The browse list suppresses already-owned/archived models; adding a second
physical pair is explicit through the custom entry. History and the saved-activity recap show the
associated brand/model/nickname. Shoe data stays on the phone and is not sent to reflection services.

Verification and installation receipts are recorded in `project_current_state.md`. New files are
limited to `ShoeDao.kt`, `ShoeSelectionSheet.kt`, and Room's required exported `19.json`; existing
test files are extended. There is no shoe service, network request, new permission or backend change.

The August/October 15 descriptions below remain the design history and explain the original flow;
their statements that native mechanics are unimplemented are superseded by this dated section.

## Status and boundary

This file is the canonical record for the shoe control on the Start screen. It records the first
resolved slice of the progressive-input ladder: a runner can deliberately add and select a shoe,
then RunState can remember that choice and associate future completed-run mileage with it.

The Claude Design result is an **interactive design prototype**, not production shoe search,
persistence, or mileage code. The accepted prototype demonstrates opening the shoe surface,
functional search, adding a shoe to the saved list, selecting a saved shoe, removing a saved shoe,
and returning the selected shoe name and mileage to Start.

This does not resolve route, surface, or run-company input. Those remain separate progressive-input
decisions. Starting energy also remains separate and optional.

## October 15, 2026 — native status and catalog research

Native Start now shows circular shoe and music objects beside Start, using Apache-licensed
Material Design Icons vectors. Both are labeled `LATER`. Shoe search, selection, saved pairs and
mileage remain unimplemented; no pair is assigned by default and no owned shoes or mileage are
invented. The interactions below still describe the accepted prototype and later production work.

A small candidate catalog was checked against official sources on October 15:

| Brand | Model | Official source |
| --- | --- | --- |
| ASICS | Novablast 6 | [Product](https://www.asics.com/us/en-us/novablast-6/p/ANA_1011C243-102.html) |
| Nike | Pegasus 42 | [Announcement](https://about.nike.com/en/newsroom/releases/nike-pegasus-42-official-images) |
| HOKA | Clifton 11 | [Product](https://au.hoka.com/products/clifton-11-1176572-flck-flck) |
| Brooks | Ghost 18 | [Product](https://www.brooksrunning.com/en_us/mens/shoes/road-running-shoes/ghost-18/1104931D462.075.html) |
| Saucony | Ride 19 | [Product](https://www.saucony.com/en/ride-19/60823M.html) |
| HOKA | Speedgoat 7 | [Official catalog](https://stores.hoka.com/) and [Speedgoat 7 event](https://eu-events.hoka.com/speedgoat7quest) |

[Strava's 2025 report](https://press.strava.com/articles/strava-releases-12th-annual-year-in-sport-trend-report-2025)
names Novablast, Pegasus and Clifton as its three most-logged shoe families. That supports including
those families; it does not rank these particular versions or establish a best shoe for a runner.
The catalog is research for later options, not a populated native shoe collection.

For later mileage, give each physical pair its own identity and associate it with the official run.
Derive totals from that pair's completed GPS runs with known saved distance, rather than incrementing
a mutable counter on Stop. Exclude fixture/sample distance and do not invent unavailable distance.
This keeps retries from adding the same run twice. An optional pair nickname, such as `Blue pair`,
would help distinguish two pairs of the same model; that remains a later refinement.

---

## Start-screen entry and selected state

- The existing circular shoe control to the left of Start is the entry point. It does not create a
  dedicated navigation destination.
- Tapping it raises a bottom sheet on the same Start screen.
- With no shoe selected, the generic line-art shoe glyph remains and no mileage is shown.
- After selection, the generic glyph is replaced by the chosen shoe image while the circle keeps its
  existing color and material relationship to the Start screen.
- The selected model name appears as compact secondary confirmation near the controls.
- Mileage is shown beneath the shoe circle as a vertical stack: the numeric value first and `MI`
  directly below it. It remains subordinate to the central Start action.
- Reopening the control lets the runner change the current selection. Starting without choosing a
  shoe leaves the run's shoe unknown; RunState never silently assigns one.

Some uploaded shoe images still carry backgrounds that do not sit cleanly inside the circular
control. That is accepted temporary asset debt, not a reason to reopen the interaction. A later
visual cleanup should use transparent cutouts or consistent background removal, preserve each full
shoe silhouette and aspect ratio, and use contain-fit rather than cropping or stretching.

---

## `Add Shoes` bottom sheet

- The sheet rises from the bottom over the same Start screen and uses a light mineral-glass surface:
  luminous near-white, softly translucent, and consistent with the foggy Start environment.
- It uses the exact title `Add Shoes` and a functional `Search brand or model` field.
- Search filters the visible shoes while the runner types.
- `YOUR SHOES` appears first and contains saved shoes with image, correct brand/model name, and
  accumulated mileage only.
- `BROWSE SHOES` follows with specific models that can be added. Brand-first browsing is a possible
  later shortcut, not part of the accepted pass.
- Selecting a saved shoe closes the sheet and updates Start.
- Adding an unsaved shoe moves it into `YOUR SHOES`, begins it at `0 MI`, selects it for the upcoming
  run, and closes the sheet.
- The intended supporting explanation is `Mileage tracking begins with your next completed run.`
- The sheet can be dismissed without selecting anything; dismissal preserves the prior selection.

The pass deliberately excludes product facts, review summaries, popularity, athlete endorsements,
shopping links, break-in advice, wear claims, and surface selection. The earlier `/ 400 MI` goal and
retirement-style progress bars were removed. Accumulated mileage is the current value; a personal
mileage reminder may be explored later from a shoe profile, never required during Add Shoes.

---

## Mileage behavior — production contract exposed by the screen

- Selecting a saved shoe associates that physical saved-shoe entry with the upcoming run and enables
  mileage tracking automatically. There is no repeated tracking toggle.
- A completed and saved run adds its distance to the selected shoe exactly once.
- Pause, resume, reset, cancellation, an unsaved run, and repeated Stop presses must not add mileage.
- With no selected shoe, the run is saved without a shoe and no shoe mileage changes.
- Saved shoes, their mileage, and the current selection should survive reloads in the prototype and
  durable app restarts in production.
- Mileage belongs to a saved physical pair, not merely a general model. How runners distinguish two
  pairs of the same model remains open and is not needed for this prototype pass.

The current Java console already stores a shoe string on a run, but it does not yet provide the
saved-shoe identity, per-pair accumulated mileage, archive state, search catalog, or completed-run
update path implied here. Those are future implementation requirements, not claims about current
code.

---

## Remove becomes recoverable Undo

The current prototype exposes a remove action for saved shoes. Manley's accepted next correction is
to replace destructive-feeling removal with a recoverable Undo pattern:

1. Removing a shoe takes it out of the active `YOUR SHOES` list immediately.
2. Show a brief confirmation such as `On Cloud X removed` with an `UNDO` action.
3. Undo restores the same saved entry, selection state when applicable, and accumulated mileage.
4. Removing the currently selected shoe clears the current-run selection and restores the generic
   shoe glyph with no mileage beneath it.
5. Past run records and their original shoe association remain unchanged. Removal means archive from
   the active shoe list, not erasure of run history.
6. Re-adding an archived shoe should restore its preserved mileage rather than create a false new
   `0 MI` history.

This Undo behavior is **accepted but not yet applied to the Claude Design prototype** as of August
14. The existing remove control is temporary.

---

## What remains open

- Route is the next obvious Start-screen entry to apply the progressive-input approach to; its
  behavior is still undesigned.
- Surface and run company still have no chosen input or correction home.
- Shoe facts, aggregated review patterns, community usage, notable-runner associations, optional
  mileage reminders, brand-first discovery, and multiple-pair naming remain later possibilities.
- Camera-based wear inspection is only a future idea. A photograph may reveal visible wear but
  cannot reliably diagnose internal foam condition; any later result would need to be framed as an
  estimate, not a safety verdict.

