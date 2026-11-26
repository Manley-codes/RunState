---
name: design-preview-build
description: "Current public RunState browser preview, source and update workflow. Updated November 25, 2026."
metadata:
  type: project
status: LIVE — accepted connected browser preview; mobile phone framing confirmed by Manley.
---

# RunState interactive preview

**November 25 title-only follow-up:** Manley requested the Start pill read **Trails** for a future
city/location-based direction. Published preview `54cc7ee`, live bundle `index-Dsc9C26T.js`;
build/export checks and focused local/live phone/desktop checks passed. Only the pill text changed:
Houston remains the current browser content/settings label; no location discovery was added.
Android source matches; the owner subsequently requested and received the in-place phone update.


## November 25 approved Android design synchronization — published

Manley explicitly requested publishing the accumulated Android changes. Preview commit `3f70fac`
replaces the old green Start footer with the approved compact Shoe/Music/Start arrangement,
icon-only controls, one entrance twist, borderless Choose/Energy prompt and white Map/Goal/Run/
Settings/Activity navigation. The simplified distance/time Goal editor uses 3 miles / 00:30 defaults,
draft-only editing, validation, Save/Remove/Close; a saved target follows the fictional run into
Completion and History. Plan entry is hidden. Native app code and real run data were not changed.

Houston trails is available on the original Hermann Park background. Settings offers the optional
same-height moving trail bands, swiping, detail browser, reduced-motion behavior and source credits;
Hermann Park remains the default. All eight trail assets are unchanged licensed Android copies.
Shoes use all 26 brands / 338 model names, required personal mileage goals, saved demo pairs and
simple custom entry. Private product photos are excluded; the public picker uses text-only rows.
The existing opening, silent music controls, three sample album covers and scripted reflections remain.

Type-check/build and **26 distinct local checks** passed: six export/privacy/subpath checks, four
opening, five existing interactions, seven Start/Goal/shoe tests and four trail tests. The added
end-to-end goal test initially targeted the wrong accessible Start name; corrected the test and
confirmed the goal through countdown, completion and History. Source behavior did not need a fix.
Live bundle `index-BJlk7IVG.js` is confirmed; **all 16 live phone/desktop checks passed**, including
the complete goal-to-History flow, all new panels, trail motion and existing album artwork.
Receipts: `.local/nov25-design-sync/{local,live}/` inside the preview checkout.

Necessary new preview files: GoalPanel, ShoePicker, ShoeArt and TrailPreview components, their
three style files, text shoe catalog, two focused browser-check scripts, trail attribution and eight
licensed assets (also copied into the generated Pages export). Source/export were committed together.

This approval resolves the prior design hold **for this synchronization only**. Future public design
updates still require explicit approval. The earlier entries below are historical deployment receipts.


**November 23 scoped artwork fix — live:** Manley explicitly approved restoring the preview's
missing sample album images. Preview commit `d988a1b` bundles the three existing study covers and
displays the correct one for each sample track, with a safe missing-image background. Default
Headphones, controls, Start's original green footer and every other screen design stay unchanged.
The live bundle is `index-TP4ONEwW.js`; type-check/build, six export checks, five local interaction
checks and three focused live phone/desktop cases passed. Cover decoding, next/previous, preference
reload and failure recovery are checked. Screenshots are private under
`.local/album-art-restoration/{local,live}/` in the preview checkout. The broader design-publication
hold below remains; this approval covers only the album-art fix.

**November 22 publication hold:** Manley is experimenting with Android designs and is not ready
to change the public preview. The scoped Start footer removal was published as `1bbd02f`, then
restored at his request with `e8c1772`. The live site serves the original `index-BYAo8HzC.js` and
green footer again; phone/desktop verification passed and the tree matches `42b6d0a` exactly.
Do not publish further preview design changes without his explicit approval. This supersedes
automatic same-milestone preview sync; Android implementation/acceptance is not that approval.
Proof is private in the preview checkout's `.local/footer-removal/*-restored-live.*`.

**November 1 local fix:** the opening retains a complete photo collage while all moving photos
decode, then reveals them together. The original native still is embedded to avoid a separate
loading-image request. Photos, motion and4.8-second exit remain unchanged; slow/failed loading and
Skip are covered by `npm run test:opening` (after building). Build, six export checks and four
browser checks pass. Source and generated files are ready locally; Manley still needs to commit
and push this update. The public site has not been changed by Codex.

[Open the preview](https://manley-codes.github.io/runstate-preview/) · [Preview repository](https://github.com/Manley-codes/runstate-preview) · [Android project](https://github.com/Manley-codes/RunState)

The public preview presents seven accepted RunState screens as one connected experience.
Both laptop and mobile views use a rounded phone frame against black surroundings. Desktop
navigation sits beside the phone; mobile keeps the screen dropdown above it. Small screens
can scroll the page while the app display retains its 393:794 proportions.

The mobile framing update is published and confirmed working by Manley. The corresponding
[Pages deployment](https://github.com/Manley-codes/runstate-preview/actions/runs/40832834729) succeeded.

## What visitors can try

| Screen | Interaction |
| --- | --- |
| Opening | Enter through the moving-photo RunState introduction. |
| Set your energy | Choose pre-run energy and explore the example plan, shoes and music options. |
| Countdown | Watch the countdown assemble into Active Run, or select Start now. |
| Your run | View distance and pace; pause, resume or hold Stop to finish. |
| Music within reach | Bring the player inside the distance visualizer and try its controls. |
| Finish & reflect | Choose optional post-run Energy and Effort, read a reflection and save the example. |
| Revisit a run | Sort All runs, Longest or Fastest pace and open a saved example. |

Dark is the default run appearance; Light is available through Appearance. History's compact
**Your Energy → View check-ins** row opens the detailed sheet, including Similar easy runs.
This compact row is a browser change; its Android counterpart and review of that content remain
later tasks.

## Browser preview and native app

This is an interactive browser adaptation of the accepted design, not the Android runtime.
Runs and routes are fictional, music controls are silent, and reflections are scripted examples.
It makes no live GPS, Spotify, ElevenLabs or backend requests and asks for no account or device
permissions. Example runs last for the page session; the appearance preference stays in the browser.

The Android project contains the real tracking, saved-run storage and connected features.
Changes to the web presentation do not automatically change the phone app.

## Source and update workflow

The preview is maintained in the separate `Manley-codes/runstate-preview` repository.
The current local checkout is `docs/local/github-preview-2026-10-29/` within the main workspace.

- Edit `preview-src/src/`; original artwork and fonts are in `preview-src/public/media/`.
- In a fresh checkout, run `npm ci` with Node.js 22.12+ before development.
- Run the following from the preview repository root:

```powershell
npm run dev
```

The development preview opens at `http://127.0.0.1:5194/`. To prepare and check the Pages export:

```powershell
npm run build
npm test
npm run preview -- --base /runstate-preview/
```

The production preview is then at `http://127.0.0.1:5194/runstate-preview/`; stop the development
server first because both use port 5194. The build type-checks the source, creates `dist/`, and
runs `scripts/export-pages.mjs` to prepare the repository-root HTML, assets, media and history
entry used by GitHub Pages. Edit source files rather than generated exports.

Building does not publish. Manley's November 20 standing authorization lets Codex handle commits
and pushes for approved work; the November 22 preview hold additionally requires explicit approval
for preview design publication. Commit approved source and generated files together. Main-project
documentation belongs to its separate repository.

## Stable links and design history

Existing shared links remain valid: [start.html](https://manley-codes.github.io/runstate-preview/start.html)
opens Start, [complete.html](https://manley-codes.github.io/runstate-preview/complete.html) opens
Completion, and [log.html](https://manley-codes.github.io/runstate-preview/log.html) opens History
within the same experience. Keep these lowercase filenames and aliases when exporting.

The August studies used a two-card landing and separate prototype exports; those layouts and
the former full-screen mobile treatment are historical. The October refresh replaced them with
the accepted connected preview. [Earlier source](https://github.com/Manley-codes/runstate-preview/tree/28a974a73c10a7cdec03acc21c6efd5e7eb1c6ba)
and `prototypes/run-complete-log-history/` preserve that work.
[Asset notes](https://github.com/Manley-codes/runstate-preview/blob/main/ASSETS.md) contain artwork,
photo credits and font licenses.
