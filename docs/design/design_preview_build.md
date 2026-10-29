---
name: design-preview-build
description: "Current public RunState browser preview, source and update workflow. Updated October 29, 2026."
metadata:
  type: project
status: LIVE — accepted connected browser preview; mobile phone framing confirmed by Manley.
---

# RunState interactive preview

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

**Manley handles all Git commands.** Building does not publish. After reviewing the result,
he commits and pushes the preview repository's source and generated files together. Main-project
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
