# RunState Current Run Flow

**Status:** Current Java console behavior — all four audit gaps resolved
**Last verified:** August 25, 2026
**Code baseline:** Saved-Run Management Pass 2 + comparison distance guard (August 25, 2026)
**Test suite:** 419 passing

## Purpose and scope

This document is the durable map of how RunState currently handles one completed run.
It is an **as-is flow**, not a future architecture diagram.

The main path follows one **Log Run** cycle. The **View Run History** path is also expanded through
saved-run management; the final nodes state return to the main menu or session end instead of drawing
long loops across the diagram.

Included:

- Java console startup and manual completed-run logging
- Pre-run state, run details, optional context, post-run energy, and effort
- Daily-mean weather lookup for the logged date
- MySQL loading and saving
- History selection, post-run feedback updates, and confirmed deletion by database Run ID
- Personal-record confirmation, the post-run response, and RunStyle analysis
- Current failure behavior and the regression protections for previously identified gaps

Excluded:

- Automatic run detection or mobile tracking
- Hourly or exact run-time weather
- Offline queues, pending sync, retries, or a second durable store
- Future Music Intelligence behavior

## Flowchart

Orange paths end the current session. A dashed node border marks a graceful fallback
that allows logging to continue.

[![RunState current console flow](diagrams/current-run-flow.svg)](diagrams/current-run-flow.svg)

## Trust rules represented by the flow

1. Saved history must load completely before the menu opens. RunState must never
   silently treat an unconfirmed or partial load as empty history.
2. A newly entered run is not considered logged until MySQL confirms exactly one inserted row and
   returns a valid generated `run_id`; only then is that ID assigned to the in-memory `Run`.
3. PR calculation, PR announcements, the post-run response, and RunStyle happen only
   after that confirmed save.
4. A save failure prints a recovery receipt — including recorded run context — and ends
   the session without adding the temporary run to in-memory history.
5. Weather and Anthropic failures may use an unavailable/local fallback because they do
   not determine whether the run itself was durably recorded.
6. Surface, shoes, company, music, and weather describe associations only. They never
   create or strengthen a primary RunStyle.
7. History management calls MySQL before changing memory. A missing ID or storage failure leaves
   in-memory History unchanged, explains that it may be stale, gives restart guidance, and ends the
   console session. A confirmed delete silently rebuilds remaining PR flags chronologically.

## Resolved flow-audit record

This table preserves the four findings that were once shown as numbered markers in the
diagram. All four are resolved, so the numbered marker graphics have been removed. This
is not RunState's complete product or engineering backlog.

| ID | Gap | Real effect | Priority | Status |
|---|---|---|---|---|
| 1 | Invalid stored enum text, or a missing required distance unit, bypasses the friendly load-error boundary. | Startup ended with an uncaught error instead of the controlled explanation. No history was deleted or partially accepted. | — | **Resolved July 16, 2026.** All seven enum columns decode through checked helpers; a malformed row raises `RunStorageException` and takes the same friendly load-error path as an unreachable database, naming the run and column on the `Details:` line. |
| 2 | The partial-history test fails before any database row is loaded. | It did not prove that a failure after one valid row still returns no partial history. | — | **Resolved July 16, 2026.** `readRuns_whenValidRowIsFollowedByMalformedRow_failsEntireLoad` decodes one valid row, then hits a malformed second row, and asserts the entire load throws. |
| 3 | The save-first console ordering is verified by code review and manual testing, but not by one orchestration regression test. | A future reorder could accidentally allow PR, AI, or RunStyle output after a failed save. | — | **Resolved July 25, 2026.** `saveAndCompleteRun()` extracted from `logRun()` as a behavior-neutral refactor. The regression test `saveAndCompleteRun_whenSaveFails_suppressesAllPostSaveWork` in `RunConsoleTest` injects a failing save delegate and asserts that in-memory history, PR flags, the AI response, and RunStyle are all suppressed when the save fails. |
| 4 | Run History and the save-failure receipt both reuse `Run.getRunSummary()`, which omits recorded context. | Surface, shoes, company, and music are saved but are not visible in history or available in the recovery record. | — | **Resolved July 25, 2026.** `Run.getContextSummary()` (private helper) builds a compact `Context: Surface \| Company \| Shoes: <label> \| Music: <note>` line from whichever fields are recorded, using `String.join(" \| ", pieces)` as the parts collector so absent fields produce no doubled separators. The line is inserted into `getRunSummary()` after pace/duration and before energy, so the shared summary supplies context to the immediate post-run view, Run History, and the failed-save recovery receipt. `RunContextTest` covers all music-state edge cases and the fully-populated ordering contract. `RunConsoleTest` asserts the exact context line appears in recovery output. |

## Important current details

- The startup prompt can capture starting energy and hold it for the next logged run.
  Choosing “Not running today,” or logging another run in the same session, leaves no
  pending answer; Log Run then asks for starting energy during capture.
- Weather is the **daily mean for the entered run date and runner location**. It is not
  exact run-time weather because RunState does not yet have real timestamps.
- MySQL is the console app's only durable copy. The future-mobile local-first contract is now
  approved but not implemented: the phone generates a permanent run UUID and uses `PENDING_CREATE`,
  `SYNCED`, `PENDING_UPDATE`, or `PENDING_DELETE`. Canonical detail lives in
  `design/run_initiation_register.md`.
- The storage layer can update post-run Energy and Effort together or delete one saved run by
  `run_id`. One affected row means success; zero means the ID is missing; SQL or impossible
  multi-row results throw `RunStorageException`. The Java console now calls these operations from
  View Run History, while editing other run fields remains out of scope.
- Stored enum text must match an enum constant exactly. RunState never trims, re-cases,
  guesses, or defaults a stored value: text that is not a real constant means the history
  itself is corrupted, and RunState says so rather than inventing a value that would
  silently poison PRs and RunStyle. `distance_unit` is the only required enum; the rest
  stay valid as null, which means "skipped" or "legacy row".

## When this document must be updated

Update the diagram, audit table, verification date, and code baseline whenever a change
affects any of these boundaries:

- startup history loading or row decoding
- the pre-run state prompt or Log Run question order
- weather timing or weather source
- what counts as a durably logged run
- PR, post-run response, or RunStyle ordering
- history or recovery-receipt display
- History management storage ordering, stale-history failure handling, or silent PR rebuilding
- the transition from manual console logging to automatic/mobile capture

Future-state mobile behavior should receive its own diagram until it replaces this
console flow; it should not be mixed into this current-state chart.
