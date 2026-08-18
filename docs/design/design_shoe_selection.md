---
name: design-shoe-selection
description: "Current shoe selection and mileage direction as of August 14 2026 — the first resolved slice of the progressive-input ladder"
metadata:
  type: project
status: CURRENT DESIGN FOUNDATION — interactive prototype accepted; removal Undo and image cleanup remain
---

# Shoe selection and mileage

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

