---
name: creative-direction-ui
description: Creative + UI direction for the RunState mobile app phase; includes the primary visual vision, August 13 working proof, and exploratory history
metadata:
  type: project
status: v0.4 — August 13 working proof recorded; execution remains open and is found through design passes
---

# RunState — Creative Direction (v0.4)

## October 24 — three live countdown assembly studies

**Native integration approved and installed; user review pending:** Manley explicitly requests replacing the
phone countdown with Tall frame and bringing the assembly into Active Run. This supersedes
the prototype-only limits below. Final native layout removes Calories, enlarges Average pace
in a full-width panel, and pairs This mile with Music below. The split means the current
mile's average pace and must use real data. Retain the accepted Night look and softened Day
palette. Auto/Day/Night belongs in existing Settings. In-ring Spotify controls remain deferred;
Music currently opens Spotify. Native verification (28 silent device checks, five inspected
captures and preserved saved data) is recorded in `project_current_state.md`. The accepted
browser remains available for comparison; it still contains the older sample calorie layout.
The following notes preserve the exploration chronology rather than the current build status.

**Latest decision — Tall frame selected:** Manley explicitly loves the refined magnetic motion
and selects #2 Tall frame as the direction to replace the current countdown. He requests appearance
refinement first; phone replacement is still pending. Keep this prototype and existing phone
countdown separate until the refined direction is ready for implementation.

**Day/night preview — revised after rejection:** Manley rejected the initial cream/ivory field and
pastel-filled tiles as flat, card-like and less premium than Night. Current Day uses clear sky-white
light, transparent countdown and final metric panels, dark green-gray readings, and restrained
teal/blue/green/violet detail accents. Faint original Start leaf silhouettes stay at opposite edges;
the central field remains clear. Reference qualities come from the actual icy Frosted Drift and
bright sky/green History screens. Exact Start moon, sun and three-line geometry replaces the old
plus/two-line marks in all six countdown tiles; these are decorative motifs, not Energy readings.
Night retains charcoal and28 sparse stationary white specks without orb shapes, glow or twinkling.
Same Tall frame geometry, hierarchy, magnetic arrival and assembly motion. Revised Day awaits review.
Manley then requested yellow instead of purple for Day Pause: warm golden yellow with a dark
green-gray symbol; hover deepens slightly and paused Resume uses a lighter yellow. Night stays purple.
**Next motion refinement:** supplied `20261025-0008-47.3467917.mp4` shows Endel line groups
expanding/collapsing, rotating into shorter strokes, and alternating faint/bright gray tones.
Manley wants that living tonal quality in the middle countdown objects, not flat fixed colors.
Current trial staggers border brightness and symbol scale/opacity, gently rocks moon/sun shapes,
and expands/contracts the three-line spacing. Night energy symbols are white-gray; Day retains
muted motif hues. The numeral varies only subtly in tone and preserves its magnetic inward arrival.
This is a countdown treatment: settled run metrics remain steady. Reduced motion removes ambient
cycling. Explicit Pause/Resume countdown lives beside Replay in the external preview options;
one elapsed clock freezes number, symbols, tones, tile relocation and final assembly together.
This refinement is exploratory and awaits visual feedback, not approval of a new phone build.
**Latest choreography and visualizer trial:** Manley requests faster connected motion and a brief
absence before each next numeral. The shared one-second beat now lands number and all six motifs
together, articulates their shapes during the hold, fades them at.80–.88s, and clears them for the
last120ms. Lines expand/collapse; paired moon tilts and sun turns share that phrase. This supersedes
the earlier fully opaque numeral and independent2.8s symbol breathing. Reduced motion stays static.
The optional Visualizer finish is on for review: segmented ring turns once during10.45–11.07s,
then blends into76 fixed-angle bars by11.19s. Bar geometry and the5.464s three-lobed traveling wave
adapt the actual Start `RunAgentVisualizer.kt`; they extend outward to roughly radius161, preserving
the full-size distance text and metric arrangement. Night uses steel whites/grays; Day uses muted
blue/teal with a small gold section. The settled ring does not keep rotating. This is decorative
ambient motion, not live audio or a measured running signal. External toggle restores the segmented
ring for comparison; Pause/Resume also freezes this finish. No Android integration or voice change.
**User acceptance and movement refinement:** Manley: “This looks absolutely great. It came out
really good.” He wants more recognizable visualizer movement, preserving the smooth slow pace;
thickness changes are explicitly deferred. Bar excursion now spans11px instead of6px, shortening
farther between peaks while keeping the same maximum radius,5.464s wave,1.8px stroke, palette
and metric layout. This is the only visual adjustment; countdown and assembly stay accepted.
**Next refinement — prominence and variety:** Manley approved the stronger motion, then explicitly
requested thicker bars closer to Start, more variation in the lines and better Day visibility.
The new preview uses3.2px strokes (previous1.8), inward base radius134 and lengths8–31px, with
bounded uneven neighboring heights and a smooth secondary ripple on the same5.464s clock.
Maximum visible radius including caps is166.6; current readings/labels remain full-size and clear.
Day uses deeper blue/teal/indigo with a small ochre section; Night uses brighter steel grays.
Opacity now stays.90–.98. This supersedes the earlier thickness deferral; no layout/countdown change.
Both themed captures, motion/pause, TypeScript/build and28 protected-runtime checks reviewed.
**Night accepted; Day hue separation:** Manley calls Night perfect and explicitly requests no
changes to it. Day looks good but its colors are harder to distinguish than Start's. Day now
uses Start's three distinct sectors:25 sage-green bars(#699e78),23 lavender(#a080c2),28 warm
gold(#c6a044). This replaces continuous interpolation through muted blue/teal/indigo/ochre;
Night color logic, both themes' geometry, opacity and motion are unchanged. Live Day capture
and76 bar colors verified; type/build/runtime checks passed. Day palette awaits visual feedback.
**Day balance follow-up:** Manley finds those hues clearer but the hard sectors too separated;
he prefers the earlier merged treatment with the new clarity. Current Day retains sage/lavender/
gold anchors and uses cosine-eased circular interpolation, lingering near each clear hue while
blending across former boundaries. Centers are bars12/36/62 with a continuous75→0 seam.
Night, thickness, opacity, motion and layout are unchanged. Updated Day capture and live colors
reviewed; type/build/runtime checks passed. This replaces the hard sectors, pending feedback.
**Accepted for now; Music-in-ring idea:** Manley says the Day balance is good enough. Night
remains explicitly accepted. He proposes tapping Music to show Spotify playback inside the ring,
including next/previous, and asks whether to defer until the screen is implemented in Android.
Recommendation: integrate and verify the accepted countdown/Active UI first, then consider a
bounded compact RunState player using Spotify, with track/artwork, previous/play-pause/next and
clear return to distance; timer/run Pause stay accessible. This is an idea, not an approved build.
The existing SpotifyAppRemoteConnection already handles authorization, connection, state
subscription and passage-scoped play/pause/seek. General live-player controls/state/artwork exposure
still need implementation and must respect passage/reflection ownership. Official PlayerApi supports
next and previous (previous restarts the track unless within its first3s). No screen/code changes
made for this discussion. Source: https://spotify.github.io/android-sdk/app-remote-lib/docs/com/spotify/android/appremote/api/PlayerApi.html
Auto/Day/Night choices are in the external preview controls and saved locally. Prototype Auto uses
device-local07:00–19:00 as Day, with Night otherwise; this is a clock schedule, not sunrise/weather.
Auto resolves before countdown and stays fixed through that run; explicit overrides apply immediately.
For eventual app integration, the override belongs in existing Run Settings. No new app screen.
Browser builds/runtime checks and earlier day/night switching, saved Night after reload, and both
run endpoints were reviewed; no phone test claimed. Earlier contrast results for the rejected
ivory/pastel palette do not validate the current revision.

**User authorization:** build two or three motion versions to test the countdown becoming Active Run.
Use the saved flat premium image as the common destination, shorten its pace/music row, reuse the
actual Start music art, and add split average and calories as visual test fields. Manley explicitly
clarified that split average means **the current mile's average pace**, labeled **This mile**.
These are browser-only mock readings; adding a real calorie estimator or live split field to Android
is not part of this prototype. The installed app and accepted countdown/voice behavior are unchanged.

**Comparison:** `http://127.0.0.1:5192/` (local server). Files are in
`C:/Users/Owner/.codex/visualizations/2026/10/03/01a103b6-bff0-72d1-8008-7b8b20f78035/runstate-assembly/`.
The new isolated preview uses the existing mobile runtime, with shared existing dependencies;
no new image generation or external images. Actual Start art is copied from
`android/app/src/main/res/drawable/start_music_orb.jpg`; existing Anton/Barlow fonts are reused.

1. **Open numeral** — unobstructed Anton number, with controlled changing outlines beside it.
2. **Tall frame** — number inside a vertical frame spanning the three grid rows; the frame opens
   into the distance instrument while supporting panels arrive.
3. **Constructed numeral** — geometric digit strokes move outward and hand over to the segmented
   distance ring. No loose strokes remain once the layout settles.

All use the same destination: timer/Pause group, primary distance ring, shorter average-pace/music
row, then This mile and illustrative calories. Six persistent outlined tiles become their own
controls/readings with staggered arrivals, completing the assembly in approximately 0.85 seconds.
The demo countdown is ten seconds and silent; it does not simulate or replace production voice.
The counter and distance advance together after arrival; Pause/Resume acts on sample readings only.
Replay, Last 3 counts, See run layout, half-speed playback, and Freeze/Continue are outside-phone
review controls. Reduced-motion preference removes assembly movement.

**Number motion refinement:** Manley liked all three and initially leaned toward Open numeral;
the later Tall frame selection above supersedes that preference.
His “landing on the screen” means depth toward the glass, like magnets pulling together, not a
vertical fall down the screen. The vertical trial was rejected. All three now use a centered,
fully opaque numeral contracting from 1.48× to its final size over 300ms (600ms in half speed).
No vertical translation, bounce or rotation. Geometric strokes use a fixed viewBox-center origin;
their existing handoff into the ring and all surrounding tile/layout behavior are preserved.
The magnetic motion is accepted; the new day/night appearance refinement awaits visual feedback.

**Status:** local build/type checks and runtime integrity passed; browser replay/variant endpoints,
Pause/Resume, hidden countdown controls, clean ring endpoint and console were reviewed. The saved
foundation and rendered layout were visually compared; supporting rows fit without clipping.
Independent review found no major visual issue. This is a design test, not outdoor or phone validation.
Tall frame is now the user's selected direction. Necessary new files are confined to the isolated
prototype folder and its review captures/comparison assets. Existing design notes updated; no Git actions.

## October 24 — countdown assembles into Active Run: motion idea, interpretation first

Manley calls the latest flat premium static concept a good foundation and asks to save it.
Durable private copy: `../local/active-run-design-2026-10-24/flat-premium-foundation.png`.
This does not select a final layout or authorize implementation. The new motion concept may
start fresh; use earlier visual ideas only where they strongly serve this direction.

Reference: supplied Endel Focus recording
`C:/Users/Owner/AppData/Local/Packages/Microsoft.ScreenSketch_8wekyb3d8bbwe/TempState/Recordings/20261024-2132-26.4273022.mp4`.
Six sampled frames across the 19.82-second recording show a thin grid, an outlined vertical
rectangle spanning roughly three cells, smaller outlined squares and changing line/plus marks.
This is light geometric linework, not solid chunky blocks or a 3D mechanical dashboard.

- Countdown: borrow the appearance behavior of that tall rectangle for the numerals.
  Whether the number stands alone, takes a block-like form, or sits inside a tile is unresolved.
- Surroundings: controlled square/grid changes during the countdown, adapted to the running
  screen. Grid density, movements and eventual metric mapping remain open.
- Transition: after the countdown, the pieces rapidly assemble into an organized Active Run
  layout, arriving in a tight rhythmic sequence. The trained-team analogy describes coordinated
  precision and readiness, not military styling, literal weapons, or requested sound effects.
- Preserve the accepted voice/countdown timing. Motion should not delay tracking or obstruct
  the number. Assistant recommendation, not yet approved: settle the metric areas after the
  assembly; any continued background movement should stay restrained and separate from readings.

**Authorization at that checkpoint:** save the static foundation and explain understanding only.
Manley explicitly said not to start the live design yet. That restriction applied to the interpretation
turn. His subsequent approval and the three local browser studies are recorded above; Android work
still has not been requested for this direction.

## October 24 — reset toward one flat, premium Active Run composition

**Latest user feedback supersedes the first concept shortlist:** Manley did not like those designs.
Only the fourth photographic concept has tentative appeal; save it for later, with its screen/use
still undecided. A durable private copy is `../local/active-run-design-2026-10-24/field-edition-parked.png`.
It is not selected for Active Run. Do not keep treating the assistant's earlier favorites as accepted.

**New task:** generate exactly ONE static design and refine that direction. Use the black Runkeeper
tracking screen as the structural foundation; explicitly ignore its white Review/Save/feeling half.
Fuse appropriate qualities from the four references in the user's attached composite screenshot
`C:/Users/Owner/AppData/Local/Temp/codex-clipboard-13ccb48f-5d2f-4926-9a3a-546b7ccb20c6.png`:

- Runkeeper: strong white metrics on black, clear hierarchy, distinct colored Pause action.
- Porsche: the dashboard's visual/image area beside information, contrasting softer-tone and dark
  rectangular regions. This is not a request for a literal car, leather, chrome or 3D dashboard.
- B&G: sparse editorial precision, interrupted/segmented circular framing, contrasting instrument
  and supporting rectangular metric areas. Keep only significant information; extra visual marks
  may serve design, but must not invent extra metrics or measurement scales.
- Endel: premium clean surfaces; pause/clock/time grouped more prominently than a tiny corner item,
  while secondary to the main metric. Also likes its restrained audio line, small music bars, title
  treatment and circular symbol family. Their precise functions are unresolved, not approved features.

**Explicit style correction:** flat, smooth, clean premium in a Sony-inspired sense; avoid the prior
3D/beveled/sculpted/neumorphic looks. Black and restrained other tones can be softened to fit RunState.
Move away from lime green, which now feels generic to Manley; at most tiny hints, with no obligation
to use it. Purple Pause in the reference is an example of contrast, not a required accent color.
The new request takes precedence over any older palette/light-surface preference. No app changes.

**Single revised concept generated:**
[Flat premium fusion preview](C:/Users/Owner/.codex/generated_images/01a103b6-bff0-72d1-8008-7b8b20f78035/exec-04dc2f3c-4bd6-4865-ac0a-33f5ebd383f5.png).
Displayed inline as the only generated image in this turn. Uses black/charcoal, white numerals,
violet Pause grouped with a stopwatch and active time, flat segmented distance framing, and a
dark pace block beside a softer gray musical graphic block. Same fictional 3.24mi / 00:31:06 /
9:36-per-mi readings. Visual inspection found all three readings and main controls legible with
no clipped values; no raised sculpted circles or lime accents. The Music graphic is illustrative
design exploration, not a newly implemented audio visualizer/control. Subsequent user feedback:
good foundation, save it; no final layout or implementation selected. A durable private copy now
exists at `../local/active-run-design-2026-10-24/flat-premium-foundation.png`; the new countdown
assembly idea above remains open and must not be forced into this static composition.

---

## October 24 — Active Run static concept comparison

**User request:** create the three discussed directions, review the broader project/design history,
experiment carefully, and include additional concepts when they offer a worthwhile alternative.
This authorizes static visual exploration, not implementation or a new feature set. Four independent
directions were generated with the built-in Image Gen tool, followed by one targeted refinement.
All were displayed inline. **Subsequently rejected by Manley**, apart from tentative interest in the
fourth, which is parked above. These are illustrative previews, not pixel-verified or implemented screens.

**Review grounding:** current Active Run metrics and lifecycle, actual Opening/Start/Countdown
screens, History/Completion materials, earlier Start/Active prototypes, archived branding/ideas,
the creative-direction history and the eleven user-provided references. Historical rules were
treated as evidence, not mandates. Useful recurring relationship: luminous surroundings, confident
athletic information and selective dark structure. The fourth concept tests photographic/editorial
identity because the Opening screen is the user's strongest visual reference.

**Common fictional run for comparison:** 3.24 mi, 00:31:06 active time, 9:36 /mi average pace.
Each shows Running/GPS recording, Map and Pause run; no invented current pace, heart rate, calories,
goals, weather or music metadata. The first three use instrument/typographic treatments; the fourth
uses an existing opening photograph as an image reference. No existing app asset was replaced.

The following numbers are the **actual displayed generated-image order**. The fifth image is a
refinement of the second, not a fifth independent direction. Final comparison: **1, 3, 4 and 5**.
Preview files remain in the generator's default folder; copy a selected source into the project
before any future implementation depends on it.

| Displayed image | Internal direction | Local preview | Review note |
| --- | --- | --- | --- |
| 1 | Porcelain Instrument | [Preview](C:/Users/Owner/.codex/generated_images/01a103b6-bff0-72d1-8008-7b8b20f78035/exec-5a76ba02-d22d-444d-92d2-aaa430c267bb.png) | Pale main distance dial, separate dark rectangular time/pace readings. Strongest direct response to favored B&G references. Fine dial marks are stylistic, not a new distance scale. |
| 2 | Studio Cluster — initial | [Initial preview](C:/Users/Owner/.codex/generated_images/01a103b6-bff0-72d1-8008-7b8b20f78035/exec-7c0fa57a-9687-4af0-9e6a-e30a496a9603.png) | Unequal circles and sculpted pale framing; superseded by image5 because partial lime arcs implied undefined progress/targets. Keep numbering stable for chat references. |
| 3 | Resonance | [Preview](C:/Users/Owner/.codex/generated_images/01a103b6-bff0-72d1-8008-7b8b20f78035/exec-b5a93c23-e470-4cc4-ac69-fc93066adfbc.png) | Dark forest atmosphere, fine flowing lines, large open distance typography and compact top timer. Expressive alternative; atmospheric/mystical versus athletic balance remains a user judgment. Lines do not claim measured audio or physiology. |
| 4 | Field Edition | [Preview](C:/Users/Owner/.codex/generated_images/01a103b6-bff0-72d1-8008-7b8b20f78035/exec-4d3e05e7-1b2b-4ed1-9405-d2650b810a1e.png) | Experimental photo/editorial composition with foreground distance and paired dark readings. Source-inspired generated mockup, not an unchanged approved photo asset. The photograph dominates more than other versions; any carried-forward implementation should use the exact approved original photo, preserving the user's preference for real photography. |
| 5 | Studio Cluster — refined | [Refined preview](C:/Users/Owner/.codex/generated_images/01a103b6-bff0-72d1-8008-7b8b20f78035/exec-0f6f1ff6-2ef1-4cd2-8ef1-3206947df34b.png) | Image2's composition preserved; partial lime arcs replaced with short fixed accents. No goals or normalized measurement scales implied. This is the current circular-ensemble candidate. |

**Visual review:** all four current candidates retain the exact three readings and clear Pause;
no clipped values or missing controls found. This verifies the visible mockups, not accessibility,
outdoor performance, motion, responsive layout or phone usability. Compare composition first;
refine the chosen direction before app implementation. No app/source/asset edits, device actions,
new repository files, commits or pushes occurred. Only existing design documentation was updated.

---

## October 24 — Manley's eleven uploaded Active Run references

**Status: interpretation and feedback only.** Manley supplied eleven screenshots and described
what attracts him to each. His examples are deliberately exploratory; do not turn suggested
metric assignments, song circles or a spiral into approved features. No final composition,
palette or main metric has been selected. No mockup or app change requested at this checkpoint.
The numbering here is the order of these eleven attachments, **not** the earlier twelve-reference
research board. Source images remain in `C:/Users/Owner/OneDrive/RS activeScreenImages/`.

| # / screenshot filename | Reference | Manley's response and the distinction to preserve |
| --- | --- | --- |
| 1 — `Screenshot 2026-10-24 121448.png` | Hammerhead Ride profile card | Likes the nature/activity photography, raised foreground Ride rectangle over the larger background, and their contrast. Unsure whether it belongs on Active Run or elsewhere. The red help-page annotation is not part of the intended design. |
| 2 — `Screenshot 2026-10-24 130019.png` | Ableton Note instrument controls / Clip Preview | Likes the circular instruments and unusual composition; imagines varying their sizes for main and supporting metrics. That unequal hierarchy is his possible adaptation, not already present in the mostly similar-sized source knobs. He connects it with the car in #5. |
| 3 — `Screenshot 2026-10-24 132319.png` | B&G marine instruments, separate light/dark versions | Along with #4, strongest favorite so far. Likes the dominant circular instrument, supporting rectangular readings and editorial information within the dial. Small source labels cannot all be read reliably; do not invent their meanings. |
| 4 — `Screenshot 2026-10-24 132338.png` | B&G display shown with a light/dark split | Explicitly does NOT want the literal half-light/half-dark composition. His example is one light overall screen like #3 with separate dark supporting panels like those showing 45.6 and 48 degrees at the right of #4. |
| 5 — `Screenshot 2026-10-24 132023.png` | Porsche interior | The car referred to in #2. Thinks the two could work together. Pale material framing dark instruments, organized readings and restrained bright accents are possible contributions; no literal car dashboard selected. |
| 6 — `Screenshot 2026-10-24 132121.png` | Runkeeper Active tracking and separate Review/Save screen | Likes broad dominant distance, paired smaller pace regions, and the connection to RunState's post-run feeling question; feels both screens fit together. Preserve that these are different stages, not one screen to merge. Current pace belongs to the reference; RunState currently displays distance, active time and average pace. |
| 7 — `Screenshot 2026-10-24 131105.png` | Endel mobile Relax | Likes the atmosphere and lower circular symbols, tentatively imagining secondary metrics. The source symbols represent modes; their possible RunState function remains open. |
| 8 — `Screenshot 2026-10-24 131137.png` | Endel for Mac | Specifically likes the upper-right pause, clock and time grouping, premium surroundings and musical character below it. Wonders about song-related circles without choosing that function. Borrowing the grouping would not imply copying a desktop-sized touch target. |
| 9 — `Screenshot 2026-10-24 131410.png` | Endel desktop/phone/watch and symbol family | Sees possibilities in mode circles, scenario choices, duration choices and their relationship to RunState's plan selection. This is a multi-device collage, not one densely populated screen to import. |
| 10 — `Screenshot 2026-10-24 133517.png` | NASA climate spiral, final side-view frame | Watched the video and imagines a central metric surrounded by an accumulating trace, potentially revealing splits/detail after finishing. Recognizes that this would need major adaptation; an exploratory longer-term idea, not an approved analysis feature. |
| 11 — `Screenshot 2026-10-24 130143.png` | Bell & Ross BR 03-92 HUD watch | Likes the design without a fixed application. Possible contributions include a circular face in a square boundary, visual layers, precise marks and contained lime illumination. No all-green face or aviation theme selected. |

### Assistant interpretation — proposals, not user decisions

The strongest common thread is **a purposeful arrangement of instruments with unequal visual
roles**: a main reading that anchors the composition, supporting readings with their own place,
precise typography and selective contrast. This is more specific than liking circles or wanting
a generic dashboard. A circle can be the focal point while rectangular supporting fields make
the whole arrangement more disciplined and easier to scan.

Three complementary contributions emerge: B&G supplies information structure and instrument
identity; Porsche supplies framing/material contrast; Endel supplies a consistent line language,
compact state/time grouping and a sense of musical life. The watch can refine the treatment of
light and precision. These references need not all be combined in one composition.

Candidate static directions for a later request:

1. A pale instrument composition based on #3/#4, with one dominant dial, separate dark supporting
   readings and carefully limited lime detail. This is the assistant's recommended first study
   because it follows Manley's strongest expressed preference.
2. A distinct circular ensemble based on #2/#5, with deliberately different metric sizes and
   pale structure around dark instrument surfaces. Avoid making every reading equally prominent.
3. A more atmospheric composition based on #7–9, keeping large explicit readings while using
   consistent fine lines and controlled motion for character. Do not sacrifice immediate reading
   to abstract artwork.

#6 can inform readable hierarchy across those studies without becoming the visual template for
all of them. Keep metric assignments provisional and use the existing metrics/control behavior
for the first comparisons. Do not invent extra measurements just to fill attractive shapes.

NASA's [source animation](https://svs.gsfc.nasa.gov/5190/) was opened and played, with intermediate
frames inspected at the circular buildup, final circular record, rotation and side-view stages.
The transferable idea is an unfolding activity becoming an inspectable record. Any running
adaptation needs an understandable meaning for a turn, radius and color; an open-ended run
cannot imply a known finish percentage. A restrained accumulating trace could inform a visual
study, while a functional spiral/splits visualization remains a separate later product decision.

Potential reuse elsewhere: #1's context-behind/action-in-front layering could suit a later Start
refinement; #9's consistent line-symbol family could connect Energy and run-plan choices across
screens. Record these as future opportunities, preserving the agreed AfroTech scope.

---

## October 24 — screen satisfaction and future reuse of the method

Manley wants to consider the design-roots/adaptation method for both future screens and screens
already built. This is feedback and a future opportunity, **not authorization to redesign those
screens now**. AfroTech remains the near-term priority; avoid significant changes elsewhere until
afterward unless a serious problem makes an earlier change worth discussing.

These are Manley's informal visual impressions, not usability measurements or final ratings:

| Screen | Current impression | Detail to preserve |
| --- | --- | --- |
| Start | Roughly **6+ / 7−** | Likes it and considers it okay, but it is not fully fulfilling; the lower half is the main concern. |
| Log History | A **strong 6+** | Room to improve its design after the current priorities; no specific redesign selected. |
| Countdown | Tentatively **around 7**, possibly substantially lower | Uncertain overall; liked it at nighttime and needs more thought. Do not treat its visual design as permanently approved or definitively rejected. |
| Run Completion | A **good 8**, not an unequivocal 8 | Highest-rated functional screen among those discussed; useful reference for what already feels closer to the intended quality. |
| Opening | **9** for visual design | Closest to his original vision of the app. He explicitly corrected "run screen" to "opening screen." Its limited role in app mechanics does not reduce the value of that visual preference. |

**Meaning for future work:** "Accepted for now" and implemented functionality do not imply
complete visual satisfaction. Preserve the working screens while keeping their design open to
later improvement. Do not automatically change technical feature statuses because of these
aesthetic impressions.

**Proactive collaboration requested:** if an element or underlying principle from the ongoing
research could fit another screen especially well, briefly name the screen, the transferable
idea and the reason. Save it as a candidate for later discussion rather than silently expanding
the current task. Manley welcomes useful connections without extensive unsolicited redesign work.

**Assistant interpretation, not a new user decision:** the opening is the strongest evidence of
the desired character; Completion is the strongest current reference for presenting functional
information. Explore how the opening's confident composition, visual rhythm, human running
culture and selective color could inform other purposes. The opening can devote nearly all its
attention to expression, whereas active tracking also needs immediate reading and controls;
each adaptation still needs to suit that screen. No requirement to repeat the collage or its motion.

---

## Reusable design research method — recorded October 24, 2026

**Purpose:** preserve the strategy Manley used to guide the Active Run design search so it can
be reused and improved on future screens or products. This records his approach, with the steps
organized for clarity; it is not a claim that the approach has already produced a successful
Active Run design.

**Current evidence:** Manley finds the research helpful and is organizing the images and resources
to communicate what he likes. A final direction, designed screen and real-use outcome are still
pending. Keep this method provisional until those results can be reviewed.

**Central emphasis, clarified by Manley:** Step 3 is the core of this approach: understand the
foundation of a design idea and adapt it to a new purpose. The other steps support discovery,
interpretation and evaluation of that transfer. This is more than collecting a moodboard, but
it still leaves room for references chosen for atmosphere, beauty or cultural connection.

### 1. Describe the intended character before specifying a layout

Start with how the product should feel, what it represents and what the screen helps someone do.
Use current accepted screens and earlier ideas to understand that intent without requiring the
next screen to reproduce them. Everyday descriptions are useful even without design terminology.

For this exploration: classy, premium, clean, editorial and informational; an interest in pale
materials, precise lime accents, music and running culture. These are clues for searching, not
a requirement that every reference contain all of them or a fixed palette for every future design.

### 2. Search beyond the product category

Look at apps, websites, physical products, photography, instruments and other visual systems.
Consider both direct connections to the activity and indirect connections to its culture or
purpose. Ask what could be adapted, not only what was already designed for the same task.

Manley's example: a Puma shopping experience helped clarify the moving-photo opening screen.
A reference does not need to be a running screen to contribute to a running product. Keep the
search open enough for unexpected discoveries; do not prematurely narrow it to the assistant's
first preferred category, such as audio equipment.

### 3. Look for the underlying design idea and how it changes purpose

**This is the central step, not merely one item in the search checklist.** Manley specifically
asked to emphasize the foundation of adapted ideas for future reuse.

Go deeper than collecting attractive screenshots. Ask what the design is fundamentally doing:
showing a position, organizing measurements, marking time, indicating progress, focusing attention
or expressing movement. Then examine how another product has adapted that idea.

Use this explanation for each useful reference:

**Original design idea → specialized interpretation → possible use in our screen.**

Examples from the search: aircraft display → layered watch → precise information at different
visual depths; transit diagram → live journey guidance → a possible record of miles completed.
These translations are suggestions. Clearly distinguish documented design history from an analogy
the assistant is making; a visual resemblance alone does not prove historical influence.

For the most promising references, answer four practical questions:

1. **What does the original idea help someone understand or feel?** Identify the useful principle:
   for example, an ordered journey, one dominant reading, layered depth or a sense of rhythm.
2. **What does that principle have in common with our screen's purpose?** Explain the connection
   in a sentence. A shared shape or appealing color is useful visual inspiration, but does not
   by itself establish a functional relationship.
3. **What must change for the new context?** Consider the person using it, attention available,
   screen size, actual data and actions. Keep the useful principle while changing the form as needed.
4. **How will we judge the adaptation?** In a later composition or prototype, check whether it
   creates the intended character and whether the runner can understand the readings and controls.

For example, a transit line makes ordered stops and current position easy to locate. A run can
share the sequence/progress idea, but an open-ended run has no known destination: a suitable
adaptation might show completed miles and the current segment without suggesting a fixed finish.
That change is part of designing the new idea, not a failure to reproduce the reference.

**Assistant's assessment:** this deserves more emphasis because it gives exploration a reason
beyond resemblance and can help familiar ideas become distinctive and useful in a new context.
It is not a guarantee of originality, usability or a successful final screen. An attractive
aircraft display may depend on trained interpretation and sustained attention that a runner
will not give it; importing its symbols or density could make the app harder to understand.
Do not force a clever analogy when the connection is weak, or keep researching roots indefinitely
instead of eventually trying the strongest ideas. Aesthetic references remain legitimate; label
their contribution honestly rather than inventing a functional justification for every detail.

### 4. Make the research visual, varied and easy to discuss

For an initial broad pass, Manley requested **at least ten actual reference images**, with source
links as well as the pictures whenever possible. Choose meaningfully different design ideas,
not ten near-duplicates to meet a quota. Inspect the images and explain the particular detail
worth considering in plain language. Label speculative concepts or historical examples honestly.

Give references stable numbers or names so Manley can point to them later. Distinguish published
reference imagery from any subsequently generated concept. Source links preserve context and
credit; they do not by themselves mean the assets should be incorporated into the app.

### 5. Let specific reactions refine the search

Manley and the assistant both research. Manley can collect and arrange references before presenting
his preferences; do not rush that step or assume that silence means a design has been selected.

Record what he likes **within** an image: typography, hierarchy, spacing, color, material, depth,
motion, atmosphere or arrangement. Liking one element is not approval of the whole screen.
Uncertainty is useful information too: Endel felt compatible before he could identify the exact
element to use. Keep such references available without forcing an explanation or commitment.

An optional note for organizing a reference is:

| Reference | Detail that caught my eye | Why it fits / feeling it creates | What I am still unsure about |
| --- | --- | --- | --- |
| Name, number or source | A specific element, or simply the overall atmosphere | Plain-language reaction | Open question; can remain blank |

The assistant should offer interpretation and creative connections, explain useful tradeoffs,
and update its recommendations when corrected. Preserve Manley's direction rather than treating
the assistant's shortlist or synthesis as an approved brief.

### 6. Establish the visual direction before comparing category conventions

This was an important correction in this session. Although the first research pass included
running apps, Manley then chose to establish his own visual direction **first**. After that,
review actual active-running screens to check the general functional essentials.

The purpose is to avoid inheriting a conventional running layout before exploring a distinctive
one. Basic readability, reachable controls and the screen's existing responsibilities still
matter throughout. Studying a reference does not automatically add its features to RunState.

### Later stages to evaluate — not yet completed

Once Manley has organized and communicated his preferences, translate the chosen principles into
a few distinct static compositions. Combine ideas thoughtfully into a coherent design, leaving
room for the assistant to contribute unexpected but relevant connections; do not simply paste
favorite elements together or copy one reference wholesale.

Then review the practical running requirements, refine the chosen composition and evaluate it
on the actual phone. When a design is accepted, return here and record:

- Which references influenced the result, which exact details survived and what was discarded.
- Whether the result expresses the intended character and belongs with the accepted app screens.
- Whether metrics, controls, paused state and changes during use remain understandable.
- What felt successful or unsuccessful on the phone and during a real run.
- Whether this process helped discover a useful direction, and which steps were unnecessary.

These are proposed ways to assess the method, not evidence of success yet. Preserve revisions
and unsuccessful directions so future reuse includes what was learned, not just the polished end.

### Reusable request for a future design search

> Help me find a visual direction for [screen or product] that should feel [qualities] and help
> someone [purpose]. Review the current product and earlier ideas for context without treating
> old layouts as requirements. Explore beyond similar apps: websites, physical products,
> instruments and other relevant visual systems. Make underlying design ideas and their adaptation
> to new purposes the central focus. Explain what the original idea does, why that principle fits
> our purpose, and what needs to change for our context. Keep aesthetic inspiration welcome without
> forcing a functional explanation for it. Show at least ten
> varied actual reference images with source links. Explain the useful detail in each, rather
> than recommending that I copy the whole design. Keep your recommendations provisional while
> I organize the references and explain what I like. Establish the visual direction before using
> competing apps to check functional completeness. Do not begin implementation during this search.

**Documentation checkpoint:** this method lives in the existing creative-direction file; no
separate document or app changes were needed. Manley is still curating references. Resume design
work when he brings back that input, rather than starting another task from this documentation request.

---

## October 24 — Active Run reference research, before design

**Status: initial research reviewed; direction still open.** Manley's later feedback below changes
the sequence: establish visual direction first, then consult active-running apps for functional
checks. The initial research included running-screen references and wider exploration. The Puma shopping-app influence
on the accepted opening is the model: find useful visual relationships anywhere, then adapt them
to RunState. No new Active Run mockups or app changes were made in this research pass. The August
status and earlier concepts below are historical, not the current implementation plan.

### What the current app contributes

Reviewed the latest Start capture (`../local/rpe-cleanup-2026-10-23/energy-symbol-spacing.png`),
accepted Frosted Drift countdown, October 24 opening captures, recent History, current Active
source and older Completion visual history. The October 15 Completion screenshot is not current
behavior and must not be used to restore its obsolete controls or smaller visualizer.

The useful shared language is luminous outdoor depth, concentrated dark forest structure,
confident condensed numerals, selective circles, and contained lime/lavender/gold color. The
opening brings stylish diverse runners, music, motion and equipment into that world. Active Run
can express that culture without repeating the opening collage or floating countdown objects.

Current functional foundation: distance, active time and average pace; visible metrics while
paused; obvious Pause, then Resume and hold-to-finish; honest GPS/error/sample states; optional
real map access. Average pace may legitimately be unavailable. Research screenshots containing
heart rate, cadence or other metrics do not authorize new fields or features.

### Running apps — functional references

| Reference, verified October 24 | What was visually inspected | Useful translation |
| --- | --- | --- |
| [Nike Run Club](https://www.nike.com/pl/aplikacja-nrc) | Official published phone image: lime field, huge distance, smaller metrics above, black circular Pause below. App version/date unspecified. | Confident athletic number hierarchy and a distinct control. Do not copy the neon palette or add its BPM field. |
| [Strava Record redesign](https://press.strava.com/articles/strava-launches-redesigned-record-experience), July 16, 2025 | Official three-screen image: setup, map with compact stats, full stats. [Current recording help](https://support.strava.com/en-us/articles/15402137-how-do-i-record-an-activity-on-strava) checked too. | Let the map and metrics have different emphasis instead of competing equally. RunState already has a separate real-map view. |
| [ASICS Runkeeper Android tracking](https://help.runkeeper.com/en/hc/understanding-your-android-tracking-screen) | Official April 2025 light and dark active screenshots: large centered distance, time above, pace fields below, clear Pause. Dark image is active, not paused. | Readable grouping without a separate card for every metric. Its plain white dashboard is not the proposed RunState appearance. |

### Wider references — visual character

| Reference | What was visually inspected | Possible contribution and tradeoff |
| --- | --- | --- |
| [Bang & Olufsen Beosonic](https://support.bang-olufsen.com/hc/en-us/articles/18107022097041-What-can-I-use-the-Bang-Olufsen-app-for) | White circular surface with a soft peach halo, restrained black type and a separate dark bottom action. | Luminous material and circular identity. Alone it is too quiet and its tiny labels too delicate for running; pair the material with strong numbers. |
| [teenage engineering TP–7](https://teenage.engineering/products/tp-7), [official press images](https://teenage.engineering/press/tp-7) | Large circular reel, small dark information window, three separated transport controls, one orange accent on a silver body. | Personal music-equipment character, tactile-looking controls and deliberate geometry. Borrow organization rather than imitate metal, screws or tiny technical labels. |
| [Endel](https://endel.io/) | Official mobile/player presentation: thin generative curves, isolated labels and sparse composition on black. | A musical visual signature can live around steady information. Do not assume actual audio-reactivity, new playback controls or health claims; a busy central animation would hurt glanceability. |
| [Porsche Taycan instrument cluster](https://newsroom.porsche.com/en_US/products/taycan/interior-design-18552.html) | 2019 official dashboard with dominant center instrument, smaller secondary readings and reduced-display options. Historical reference, not a claim about latest Taycan UI. | Give one metric clear priority and keep locations stable. Avoid three vehicle dials or decorative gauges implying measured progress/zones. |
| [On LightSpray](https://www.on.com/en-us/explore/lightspray) | Pale industrial environment, white/gray product silhouettes and precise lime/teal accents. | Athletic color and material discipline. The campaign's full imagery/video is not an appropriate default behind live numbers. |
| [Tracksmith No Days Off 2026, page 13](https://ndo-2026.tracksmith.com/tracksmith-ndo-2026-13/) | Deep blue/purple, large red typography and staggered winter-running photographs. | Editorial scale and human running culture. Useful as a counterweight to overly clinical equipment references; its overlapping collage/low-contrast colors and slogan should not become the live-screen base. |

### Source-image index

These are reference links, not licensed assets approved for inclusion in RunState. Actual images
were inspected, not selected solely from brand descriptions. No remote images were downloaded or
added to the Android app.

- [Nike active phone](https://static.nike.com/a/images/f_auto/dpr_1.0%2Ccs_srgb/w_599%2Cc_limit/a7d429d6-9eae-49db-95c7-aeca3ffab96d/aplikacja-nike-run-club.jpg)
- [Strava official Record montage](https://press.strava.com/cms/assets/https%3A%2F%2Fimages.secure.ctfassets.net%2Fwad4jonn1ykp%2F7GZSD5gVnmLANRuOxumlTK%2Ff227820f5cdb65ef295f47a25a241c7a%2FPerformancePredictions-Press-Hero__1_.png?fm=webp&q=50&w=1162) — filename is misleading; the rendered image is the Record montage.
- [Runkeeper light active](https://help.runkeeper.com/hs-fs/hubfs/Screenshot%202025-04-08%20at%2016.05.24.png?height=763&name=Screenshot+2025-04-08+at+16.05.24.png&width=340)
- [Runkeeper dark active](https://help.runkeeper.com/hs-fs/hubfs/Screenshot%202025-04-08%20at%2016.06.12.png?height=760&name=Screenshot+2025-04-08+at+16.06.12.png&width=337)
- [Beosonic screen](https://support.bang-olufsen.com/hc/article_attachments/27923483367313)
- [TP–7 front](https://assets.teenage.engineering/_img/646b1dfeb6087d221cee53fa_original.jpeg)
- [Endel interface presentation](https://endel.io/pages/index/hero-top-banner/hero-top-banner-desktop.jpg)
- [Taycan instruments](https://newsroom.porsche.com/.imaging/mte/porsche-templating-theme/image_690x388/dam/pnr/2019/products/taycan-world-premiere-18445/gallery-Interior/b-P19_0576_a3_rgb.jpg/jcr%3Acontent/b-P19_0576_a3_rgb.jpg)
- [On LightSpray still](https://images.ctfassets.net/hnk2vsx53n6l/1EJqZj5qwkSAJmp2Hlf7go/1903814ff786425907ef963d21c8a498/fw26-lightspray-pr-product_still-cloudboom_strike_2-white_lime-rgb-02.jpg?fm=webp)
- [Tracksmith campaign frame](https://c-p.rmcdn1.net/5526e3a76b81fe6c42424e1c/5970390/Screenshot-dc179a6b-7ea5-4f8c-8940-52add72b63c2_readyscr_1024.jpg)

### Manley's response — visual direction first

Manley was unsure about much of the shortlist. The initial assistant recommendation combining
B&O, TP–7 and Nike is not the chosen foundation. Three references caught his attention:

- **Porsche:** connects to his original aim of classy, premium, clean, editorial and informational
  design. The perceived lime-green accents are another positive. Interest is in these qualities,
  not approval to recreate an automotive dashboard.
- **Endel:** feels compatible with RunState and worth exploring, but no particular element or
  treatment has been selected yet.
- **On LightSpray:** pale materials and precise lime accents connect with his original direction.

These are provisional interests, not an approved combination, palette, layout or final shortlist.
Manley is continuing his own research. Keep exploration open to further references and avoid
prematurely narrowing it to a literal music device or a circular dashboard.

**Working sequence:** explore broader visual references and establish the desired character;
then consider active-running interfaces to check general functional essentials. Manley explicitly
does not want conventional running layouts to anchor the creative direction. Keep basic metric
readability and reachable controls in mind throughout, while saving running-app comparisons for
the later practical review. No new mockups or app edits authorized by this feedback alone.

### October 24 follow-up — design roots adapted to new purposes

Manley requested at least ten actual images of foundational design systems that have been
adapted, or could be adapted, to a specialized purpose related to Active Run. His example was
running products reinterpreting GPS maps. This pass looks at the underlying visual relationship
(measurement, orientation, sequence, time, focus), not merely attractive brands. Existing running
apps remain deferred until the visual direction is clearer. No direction is selected yet.

Twelve references below were visually inspected using the published image, not inferred from
search descriptions. Proposed RunState translations are design suggestions, not claims that the
original designers intended them or that RunState already has the illustrated functions. Where
historical influence is not documented, "root" means a visual analogy, not proven ancestry.

| Reference and image | Root and specialized use | Potential RunState translation |
| --- | --- | --- |
| **1. Bell & Ross BR 03-92 HUD** — [source](https://bellross.com/en-sg/products/br-03-92-hud), [image](https://bellross.com/cdn/shop/files/831-BR0392-HUD-CE-SRB-cases-vue-produit-3.jpg?v=1761060546&width=925), [2020 design press kit](https://www.bellross.com/mods/watches/HUD/BR_PRESSKIT_BR03-92-H.U.D-EN.pdf) | Documented aircraft head-up display to layered mechanical watch. Bright lime marks float at separate depths on black. | Precise information on a stable layer, with a restrained lime accent on another. Strong premium/material reference; avoid importing targeting symbolism. |
| **2. Leica SL3** — [image source: Leica Store Heidelberg](https://leica-store-heidelberg.de/en/products/leica-sl3), [image](https://leica-store-heidelberg.de/cdn/shop/files/10607_Leica_SL3_display_foto_LoRes.jpg?v=1709810535&width=2400) | Exposure readings associated with separate camera controls become a coherent digital information bank. Tall numerals, quiet units, thin divisions, sparse red selection mark. | Distance, active time and average pace share one deliberately composed instrument area instead of unrelated cards. Layout analogy; this exact image is retailer-hosted. |
| **3. B&G Triton2** — [source](https://www.bandg.com/bg/type/instruments/), [image](https://www.bandg.com/globalassets/bandg/products/type/instruments/triton2_ff_boatspeed.png) | Marine gauges become a pale digital instrument dominated by one giant number and a subordinate measurement strip. | Clear distance priority with time/average pace aligned underneath. Borrow scale and grouping, not thick housing or nautical metrics. Established-product imagery, not a latest-version claim. |
| **4. Apple Depth, 2022** — [source](https://www.apple.com/newsroom/2022/09/introducing-apple-watch-ultra/), [image](https://www.apple.com/newsroom/images/product/watch/standard/Apple-Watch-Ultra-Yellow-Ocean-band-Depth-app-220907_inline.jpg.large.jpg) | Depth gauge and graduated scale become a compact watch display with a level boundary and large central value. | A meaningful edge scale could show progress through the current mile while leaving the central reading still. A design analogy, not a proposal to add diving/elevation sensing. |
| **5. Transit 6.0** — [source](https://blog.transitapp.com/six-o/), [image](https://blog.transitapp.com/wp-content/uploads/2025/08/ETA-Cards-Screens-EN-9-1536x1028.png) | Line diagrams, ordered stops and timetable typography become live journey guidance and a compact progress ribbon. | A thin record of miles completed and the current segment, with no implied finish-distance target for an open-ended run. Strong candidate for structure without a conventional full map. |
| **6. PeakFinder** — [source](https://www.peakfinder.com/mobile/), [image](https://cdn2web.peakfinder.com/web/videos/mobile/manual/v4/light/en/manual_gps.jpg?width=800) | Panoramic cartographic drawing becomes a landscape-identification instrument with precise fine lines. | Pale outdoor linework around clear metrics, connecting informational/editorial character with the Endel interest. Decorative scenery must not masquerade as measured surroundings. |
| **7. Hammerhead Karoo CLIMBER** — [source](https://support.hammerhead.io/hc/en-us/articles/25602163627163-Karoo-OS-CLIMBER), [image](https://support.hammerhead.io/hc/article_attachments/50983425047067) | Elevation cross-section becomes an in-motion cycling display with current position and past/upcoming segments. | The current-position marker and completed/uncompleted relationship could inform a restrained mile-progress band. The full interface is too dense to import; no new elevation feature is implied. |
| **8. TerraPirata digital roadbook** — [source](https://terrapirata.com/rally-roadbooks/), [image](https://terrapirata.com/wp-content/uploads/2023/08/theapp-600x1024.png) | Paper rally instructions become a digital cockpit retaining sequential distance/diagram rows with a highlighted current instruction. | A current-mile or run-state focus with quieter completed information. Especially clear root-to-digital adaptation, but the 2023 pictured skin is a structural reference rather than a polished aesthetic target. |
| **9. Awair Home** — [source](https://support.getawair.com/hc/en-us/articles/360039235533-Using-the-Awair-Home-App), [image](https://support.getawair.com/hc/article_attachments/1500004681441) | Environmental sensor readings become a sparse phone snapshot with pale space and a lime focal point. | One strong metric, aligned supporting readings and small status marks. No air-quality score, "Good" judgment or invented runner readiness score should be imported. |
| **10. MiniMeters VU** — [source](https://minimeters.app/), [image](https://minimeters.app/images/meters/vu.png) | The creator's explicitly named classic VU meter becomes a contemporary software audio module. Open scale, rhythmic ticks and one needle. | A thin elapsed-time ruler could provide musical character without a player panel. Audio overload/level semantics are not runner effort or energy measurements. |
| **11. Ableton Note MIDI Editor** — [source](https://www.ableton.com/en/note/manual/#midi-editor), [image](https://ableton-production.imgix.net/note-manual/MIDIEditorFullScreen.png?auto=&w=768) | Musical events ordered on a time grid become a compact mobile timeline editor. Sparse mint marks, fine ruler, stable controls. | A single time rail or segmented strip could bring musical rhythm to the composition. The full editor grid would be too dense during a run; this is the more exploratory reference. |
| **12. NASA Climate Spiral** — [source](https://svs.gsfc.nasa.gov/5190/), [image](https://svs.gsfc.nasa.gov/vis/a000000/a005100/a005190/GISTEMP_Spiral_English_degC_2160p60.01402_print.jpg) | NASA credits Ed Hawkins's climate-spiral approach and develops it through animation/3D. Fine accumulating loops, radial structure and open center. | An exploratory reference for one restrained time-driven line around a steady metric, linking Endel-like movement to information. Preserve the distinction between actual run data and atmosphere; the dense climate chart itself is not a proposed screen. Credit NASA's Scientific Visualization Studio. |

Most promising to discuss for the stated taste: Bell & Ross for layered precision/lime,
Leica for editorial measurement hierarchy, PeakFinder for pale outdoor linework, and Transit
for journey structure. Awair offers a simpler pale/lime alternative. These are assistant
recommendations for comparison, not user-approved directions. The user should be able to
reference these stable numbers when discussing images. All twelve images are shown in the chat
with source links; no remote assets downloaded, generated images, app edits or new files.

### GitHub presentation reminder

Manley requested a reminder after the main AfroTech app essentials are complete: replace old
GitHub design screenshots, update the README/current feature status, and label historical
concepts clearly. Reminder `refresh-github-after-afrotech-essentials` was created in Codex. It
checks the milestone daily at 10 AM Central, stays quiet while incomplete/unclear, and reminds
once after explicit completion before pausing itself. No automatic GitHub edits or publishing.

---

> **Current status, August 13:** the UI phase is active. Log History has a stable visual foundation,
> and the Start screen has now been prototyped through its transition into active tracking. That
> Start/Active Run pass is stable enough to leave for now; it is design evidence, not mobile code.
> The most-recent Log-record quick peek is now complete. The next queued pass is the
> progressive-input ladder, followed by Run Complete and Log History refinement. The Core Running
> Foundation Review follows those screens; music feature inventory and prioritization comes after
> that, with RunStyle Sound treated as one candidate rather than a predetermined screen. Full screens
> continue to expose backend and mobile requirements before Spring Boot and mobile contracts are
> finalized.
>
> Music Intelligence remains at its separate stable stopping point, and the built RunStyle V1
> remains current behavior. Their status lives in their own records; neither should be inferred from
> this visual-direction file.
>
> This doc is the map for that later full design phase. §1–12 are the original June
> 28 brainstorm synthesis; **§0 records the
> decisions made in the later prompt-iteration sessions (early July 2026) — where §0 conflicts with
> §1–12, §0 wins.** Superseded ideas are kept, marked, not erased.
> Moodboard images are being moved OUT of the repo (July 6, 2026) — visual references in §5–6
> ("image-1", the H₂O scanner, "Today Running / 25°") and the newer references (Zendaya scene,
> Warwick Acoustics, geometric runner art, Apple Watch panel) live outside this folder now.
> Build tech (native / Flutter / mobile web) is still deliberately deferred.

---

## 0. v0.2 — Decisions from the prompt-iteration sessions (July 2026)

> **Visual-history notice, August 9:** the fixed clean-white/black/orange palette, geometric-athlete
> foundation, and other visual translations in this section no longer govern the current direction.
> They remain here as evidence of what was tried. Product, behavior, and flow decisions in this
> section are unaffected. For current visual guidance, use **The primary visual vision** below.

**Palette — SUPERSEDES §5's warm/cream direction:**
- Clean WHITE base. No cream, no beige, no warm gold. "White is a canvas, not emptiness" —
  activate it with layering (map texture, structural grids, frosted depth), never by darkening.
- Strict hierarchy: white surface · black selective/thin (labels, precision UI) · ORANGE primary
  accent (recording, music, action, energy) · sky blue for GPS/map/weather · green sparingly
  (positive state, outdoors only). Never spread colors evenly.

**Typography:** NO serif — dropped everywhere, including the share card. Athletic condensed
sans for numbers/hero stats ("precision sports instruments"), clean body sans for labels.

**Art — REVISES §5's "no illustration" rule:** colorless low-poly geometric athlete art
(cinematic "editorial realism," LD+R-adjacent, low color variation) is IN as the environmental
foundation on State Scan + Run Complete (bookends: poised before / settled after). Real
photography stays on the share/completion card. The actual rule: no childish flat illustration;
cinematic geometric art welcome. Panels = material glassmorphism (blur/tonal depth, NO border
strokes — avoid the "gift card" look).

**Historical screen-flow translation — superseded visually August 13:** the overall journey from
pre-run setup → Active Run → Run Complete remains useful. The former `HOLD TO START`, full-screen GPS
foundation, and mandatory environmental-background interpretation do not describe the current Start
prototype. The current screen uses a direct Start button and a luminous near-white atmospheric field;
details live in `design_start_active_run.md`.

**Signature moment — RESOLVES §11:** the AI reply card IS RunState's face ("the taste matched
the discipline"). The completion screen and share card merged: completion = full version in-app,
share card = curated export subset. Manley: possibly more signature than the feeling idea itself.

**Locked product details:** "Remove response" allowed; regenerate deliberately NOT (the response
is a moment, not a formula). "Miles Logged" replaces "Lifetime" everywhere. Shoe pill (with
sheen) + calorie ring (single orange arc, no gradients) on the completion screen. Rugged
mountain-ridge line for the feelings visualization (jagged horizon ABOVE the two states — not a
smooth arc, not an EKG). Static screens one at a time for design iteration; video only as a
finishing step.

**RESOLVED (July 27, 2026) — State Scan energy states:** the stored domain remains
**LOW / MODERATE / HIGH**. The FOUR-state State Scan concept (Low Charge / Building /
Ready-ish / Sharp) is **superseded as a domain proposal** — it does not change the stored
enum. Later UI work may still refine **labels and presentation** freely, provided the three
stored meanings are unchanged. Decision record: `design_music_intelligence_v1.md`.

**Open questions (v0.2):**
- Collapsible block → frosted sphere docking: personalization (permanent layout) vs focus mode
  (temporary)? Worth pursuing either way; discoverability needs an affordance.

**HISTORICAL FOUNDATION — August 6, 2026.** Frosted panels over a photographic environment
replaced the Sony entry's *clean light canvas* translation. On August 9 this became one material
expression inside the broader visual vision below, rather than the identity by itself.

---

## ⚠️ HOW TO WORK WITH THIS SECTION — read before advising on anything visual

**Nothing here is locked. Manley's own words: *"keep an open mind — this is something I might
constantly do until I find what I want."***

This is a standing instruction, not a caveat. It changes how anyone should behave:

- **Do not cite this document as authority against a new visual direction.** Give the reasons behind
  a recorded decision and what would reopen it — never "the docs say X." Manley: *"don't worry too
  much about the style rules in the docs — consider them, but keep an open mind."*
- **Direction gets found by making, not by specifying.** The foundation below was arrived at by
  building several variants and looking, after a specified version failed. Do not short-circuit that
  with a spec that decides in advance.
- **Colour, shape, and layout are all open.** *"I don't like to be restrained when it comes to
  visual art... when it comes to colors, shapes, layouts etc I'm open to all suggestions."* That
  includes replacing orange.
- **Iteration is not indecision.** Repeatedly revisiting the look is the process working. Treat a
  reversal as new information, not as churn.
- **What is stable is the target, not the execution:** premium, stylish, light, productive-looking,
  and *very slightly* futuristic — advanced-looking rather than futuristic.

**A lesson worth keeping.** A light version failed on August 5 because its spec was a list of
**removals** — no borders, no shadows, no gradients, flat fills. In the dark build those read as
restraint because translucency and depth were already carrying the quality. In light, nothing was
left doing that work and the result was structurally correct and visually empty. **Specifying visual
quality by prohibition does not work.** A direction has to say what *creates* the quality, not only
what to avoid.

---

## The primary visual vision — August 9, 2026

This description is important and is the **primary working reference for atmosphere and identity**:

> **RunState should feel like a purposeful running experience held within a soft, sensorial
> atmosphere. Real running environments, physical exertion, sweat, movement, and confidence give
> it athletic substance; strategically placed dark elements and precise structure add premium
> discipline; and everything remains productive and forward-moving. The surrounding softness
> never weakens the physical intensity—it gives that intensity somewhere fresh, human, and
> inviting to live.**

**The running is not soft; the world holding it is.** Physical fitness and running remain first.
Softness describes the persistent visual atmosphere—present before, around, and after the stronger
substance—not the effort, athlete, or sport. Real running environments, intensity, sweat, attitude,
confidence, productivity, and structure inhabit that atmosphere. They give it weight and purpose
without destroying it. The tension between a soft surrounding world and genuine physical intensity
is central to the freshness and indirect sexiness of the identity.

**Productivity is a top identity trait.** The softness must never drift into passive, sleepy, spa-like
wellness. RunState should remain clear, useful, and forward-moving: it helps the runner prepare, act,
reflect, and improve without adopting the cold or mechanical atmosphere common to productivity and
fitness software.

**Current visual interpretation:**

- Soft, luminous colour and tonal variation dominate. Near-black, darker colours, or darker shades
  of the dominant soft colours are placed strategically for structure, confidence, navigation,
  emphasis, and premium contrast. They do not take over the atmosphere.
- Real-life running and related athletic environments are part of the identity, not incidental
  decoration. The environment may supply green, clay red, blue, amber, morning grey, or another
  natural colour relationship; this is not a rule that everything becomes green.
- Photography may carry indirect sensuality through reflected or sweaty skin, natural light,
  physical texture, movement, close editorial crops, attitude, and self-possessed confidence.
  The aim is athletic presence and allure, not explicit sexualisation.
- Sony remains a **supporting influence**, used minimally and strategically for precision,
  proportion, materials, hierarchy, restraint, and premium discipline. Its heavier technological
  atmosphere is not the target.
- Frosted or glass-like panels remain a promising material because they let the environment live
  through the interface. Glassmorphism is not the identity and is not required everywhere.
- The strongest current reference pattern is a soft, luminous environment with one concentrated
  dark structural element, such as a confident navigation or action surface. Exact components are
  still resolved screen by screen.
- Typography is also resolved screen by screen. Strong condensed or instrument-like numerals,
  clean functional labels, and selective editorial character are all available; the historical
  universal `NO serif` rule does not govern the current direction.

**Seasonal palette exploration — provisional, August 21:** RunState may eventually let its palette
shift with the season, giving several successful directions a purposeful home rather than forcing
one palette to carry the entire year. Possible references include ACG Frost for winter, Court Sage
for spring, Adventure × ACG Frost for summer, and Sonny or King for autumn. This is not a locked
decision or a showcase requirement; it is a future theming possibility to revisit after the core
journey is stable.

### August 13 working proof — Start to Active Run

The circular Start/Active Run prototype is the clearest current test of the vision. Its canonical
screen and behavior record is `design_start_active_run.md`.

- A foggy porcelain near-white field carries softness through tonal depth rather than through a
  full-screen color or photograph. This proves that near-white can be atmospheric; **flat, untreated
  white**, not white itself, is the failure mode.
- A grayscale group-running photograph is contained inside a circular timer/music instrument. Real
  athletic imagery remains part of the identity even when it is not the whole background.
- The neutral **State Glass** energy tiles and a concentrated near-black Start/Pause control provide
  structure without letting dark technology take over the screen.
- The **Electrolyte** blend — softened blue-gray, eucalyptus/forest, and pale luminous yellow — adds
  life only to the radial music visualizer on the neutral screen. It is adjustable evidence, not a
  locked palette or permission to spread those colors across every component.
- The same circle transforms from preparation to active tracking: pace, BPM, and elapsed time live
  inside; large heavy distance numerals live below. Productivity comes from this immediate hierarchy,
  not from adding dashboard density.
- The compact live now-playing strip borrows Log History's material DNA while remaining clearly part
  of the current run. This creates continuity between the live moment and what will later be saved.

The successful ingredients are the relationships — softness around physical substance, selective
dark structure, real athletic presence, clear metric hierarchy, and restrained living color. Future
screens should not copy the circular composition merely because it worked here.

**What this updates without erasing the design history:**

- The fixed clean-white/black/orange palette is no longer an active identity rule. Exact colours
  remain open; the stable relationship is soft colour dominance with selective dark structure.
- Geometric athlete art is no longer the environmental foundation. It remains an optional technique;
  real-life running and athletic imagery is the primary identity candidate.
- The earlier dark-open → light-use → dark-close arc is not a governing rule. A screen may still use
  a dark moment when it strengthens that moment without overwhelming the soft atmosphere.
- The photographic environment, natural freshness, editorial confidence, productive feeling,
  restrained futurism, selective darkness, and premium quality through precision all remain useful.

This is a strong vision, not an immutable component specification. The open-mind rules above still
apply: design passes may discover better executions without re-arguing the identity from scratch.

---

## Earlier material foundation — frosted panels over a photographic environment

**Manley's articulation:** *"a more soft tone of Sony, to connect better with the environment of
running — that's why I also added 'light' in my description."*

That resolves what looked like a contradiction. *Light* means luminous, but it neither requires nor
forbids a near-white page. A foggy porcelain field is valid when tonal depth, material, photography,
or motion gives it atmosphere. The rejected direction was blank, flat, and generic — not white itself.

**How it works.** A photographic environment behind translucent panels remains one useful
expression. The August 13 Start screen demonstrates another: photography can be contained inside a
live interface instrument while a luminous atmospheric field carries the surrounding softness. Both
approaches can belong to RunState; neither is required on every screen.

**What survives from the earlier foundation.** Interfaces need an atmospheric source — photography,
fog, tonal light, glass, texture, motion, or a deliberate relationship among them. They should not
feel like controls dropped onto an untreated blank canvas. That is a quality requirement, not a rule
that every screen needs a full-bleed photograph or colored background.

**Parameterised in Claude Design.** The screens expose live controls — background surface, panel
clarity, palette, and pill style — so the look can be tuned by looking rather than by respecifying.
Treat those as the iteration surface.

**Open and being handled by Manley:** legibility over busy photography, what determines which
background appears, and the production cost of sourcing imagery. Raised August 6; he is ahead of it.

**Candidate Sony-inspired direction (August 2, 2026 — historical exploration, not current authority):**
- Use Sony as a **design philosophy, not a visual template**: visionary, simple, human-centered,
  confident, immersive, and premium; "smart, not intellectual" and "cool, not trendy."
- Translate that into a clean light canvas, disciplined black, one concentrated warm accent,
  sculptural hierarchy, purposeful asymmetry, one strong hero scene, and quiet supporting controls.
  Premium should come from proportion, spacing, materials, and precision rather than decoration.
- Blend technology into human and natural environments. Keep RunState's athletic energy, bold
  metrics, orange action/music signal, GPS blue, sparse positive green, and selective dark cinematic
  moments; Sony-like calm should make those elements more deliberate, not drain their movement.
- Do not copy Sony logos, product-showcase compositions, spheres/stones, repeated pill controls, or
  tiny low-contrast labels. A runner should still understand the screen quickly while tired and sweaty.
- The white Active Run / Run Complete explorations are currently closer to this direction than the
  darker photo-heavy concept, but neither is final. Their instrument-like hierarchy is promising;
  the next pass should add more human warmth and material depth without adding clutter.
- The supplied sand/mineral, white, silver-gray, black, and restrained yellow reference is a
  **palette experiment only**. Test its color relationship against the current white/black/orange
  system; it does not yet supersede the clean-white base or make beige/gold a locked brand color.

**Soundtrack Log direction (August 3, 2026 — promising prototype, not locked or implemented):**
- The Claude Design exploration revealed a stronger home for the music reflection: a newest-first
  **run-and-soundtrack log** that people can revisit, rather than a one-time AI reply that disappears
  after the post-run moment. The working header `RUNSTATE · SOUNDTRACK LOG` and the idea of each run
  becoming a record are directionally strong; final naming is still open.
- Each collapsed row shows the selected soundtrack moment, artist/album context, and run date/time.
  Opening a row reveals the reflection and updates the visible run-facts panel for that record. Keep
  only one row expanded at a time so the screen remains focused and the chosen reply has room to land.
- The expanded record may show the reply, song metadata, a run-relative stamp such as a mile marker,
  and `READ FROM` evidence chips. A chip must name information the reply actually used. Mile markers,
  splits, cadence, heart rate, elevation, wind, and other richer mock data are **future capability
  references, not claims about what the current console app records**; show them only when the real
  run data eventually supports them.
- **Reflection voice behavior:** opening a record may automatically read its AI reply aloud; `Replay`
  repeats it. Collapsing it or opening another record stops the current reading, and the newly chosen
  record may then begin. This is audio presentation of the existing reflection—not a revival of the
  previously parked general-purpose voice-message feature. Later prototypes must still test mute,
  silent-mode, accessibility, and an obvious way to stop audio so the app never surprises the runner.
- The visual split of a concrete run read followed by a human/music read is a useful card-composition
  reference, not a mandatory two-line writing formula. The reply still follows the strongest idea.
- **Unresolved data-model question:** a `record` currently reads best as one intentionally selected
  signature soundtrack moment for a run, not every track that happened to play. Do not let `14 runs,
  14 records` silently lock one-song-per-run or prevent multiple meaningful moments later. Resolve
  what qualifies as a record before persistence or provider integration is designed.

**Social position refinement (August 4, 2026) — REFINES the profile exclusion below.** Manley's
decision: a **shareable personal card** carrying the runner's picture, top runs, and the songs
beside them is **permitted**, as `RunStyle Sound` (see `music_feature_register.md`). His stated
reasoning: he accepts that some comparison will happen and does not want comparison to become the
app's **core identity** — an optional exported artifact is not a core mechanic.

The distinction that keeps this consistent with the July 6 line: **a card is exported, a page is
browsed.** What remains excluded is the browsable surface — profile pages other runners navigate to,
follower graphs, feeds, rankings, and head-to-head. What is now allowed is an artifact the runner
chooses to share, in the same family as the existing share card.

Two constraints attach: the exported card carries no feature name (it would be jargon to a
non-user), and the associations shown must be worded associatively rather than causally, per the
overclaim guard.

**Community clarification (July 6, 2026) — refines §2's social-comparison line:** the ban
is on direct social-network mechanics (profiles, followers, feeds-as-graph, head-to-head),
NOT on community presence. Anonymous, aggregate culture content — community shoe-mileage
norms, playlists other runners ran well to — is welcome and on-identity: "culture-community,
not network-community." Full detail in user_goals.md.

**Cold-start principle (July 6, 2026):** RunState's best features (Run Style, taste-aware
playlists, shoe analytics, comparisons) only turn on after user data accumulates — but
day one must still feel intelligent for an app whose identity is "the app that knows you"
(retention is decided in that window). Until personal data matures, offer REAL substitute
information: published reference knowledge (shoe retirement norms, RPE science,
weather-pace effects), honest progress framing ("3 runs in — your Run Style starts
revealing around run 11"), previews of what's coming. HARD RULE: never fabricate personal
insight — the agent must not pretend to know the runner before it does; honesty about
newness is on-brand and protects the reply card's credibility. (Same move as the shoe
screen's published-mileage fallback.)

**Culture details as designed moments (July 6, 2026):** culture details get real visual
presence — designed screens, not buried settings-text. First two elevated screens:
- **Shoes** — the runner's rotation shown visually: per-pair mileage, PRs carried, a
  retirement arc as a pair approaches end-of-life (published 300–500 mi reference data per
  shoe category — no user base needed).
- **Music** — "your best-run soundtracks," built from the runner's own run/music/energy history.
Anti-sprawl guard (user's own restraint rule): elevate these two FIRST and make them
unforgettable — do not systematize "every detail gets a screen." Community versions (crowd
shoe stats, browsable community playlists) are filed in parked_music_recommendation.md,
Phase 7.

---

## 1. Positioning

- **More than a fitness tracker.** It still tracks well — accurate pace, distance, and PRs are table
  stakes for serious runners — but what it's *really* about is a reflection of your productivity and a
  sense of belonging. (Be careful using "not a tracker" as a literal claim: the tracking is the *bones*,
  the reflection is the *soul* layered on top. Don't drop tracking competence in the name of positioning.)
- **"A special weapon in a runner's arsenal."** Not the center of their life — the piece that
  *signals who they are*. Small footprint, high meaning (the watch-to-a-suit instinct, resolved
  toward "weapon, but one that fits a productive life").
- **Exclusive the way Claude is exclusive:** a serious core audience that everyday people can still
  use and aspire into. Premium as a *feeling*, not a velvet rope (Lululemon).
- **The market gap is a feeling gap, not a feature gap.** Everyone competes on metrics/features;
  nobody owns the feeling. That's the wedge — wide open because it's hard and unsexy to build.

## 2. Audience

- **Primary:** frequent / committed runners, any type. The vision fits them best.
- **Welcomed:** everyday runners — aspirational, not gatekept. Designed for the serious, open to all.
- **Primary aim: active / committed runners** (any type still welcome). If forced to choose, lean here — and
  these runners tend to cluster in clubs, racing, and training-group culture, so that world is the *backdrop*
  even though we're not building club features.
- **Competition: keep the fire, skip the scoreboard.** Do **not** downplay competition in the visuals — the
  natural competitive energy of running culture (PRs, pushing yourself, racing, intensity) is welcome and should
  be *felt*. What to avoid is **direct, intentional social-comparison features** — leaderboards, ranking against
  friends, head-to-head. Competitive in spirit and aesthetic; not a social-comparison machine. Not a run-club app.
- **Not only for runners who want support.** Support and competition aren't opposites — they serve different
  moments. RunState has heart *and* edge; never frame it as "the gentle, non-competitive app."

## 3. What it must make people feel

- **Opening it:** productive, in control, motivated, a little exclusive.
- **Right after a run:** seen, productive, connected, empowered — "your hard work isn't going unnoticed."
- **The soul move:** on a bad day or a hard stretch, the *experience* should leave them feeling like they're
  **hanging in there** — resilient, still in it. This is a feeling to **evoke**, NOT a literal line the app prints.
  Do not display "you're hanging in there" (or similar) as text. Never shame a down day. (Extension of the app's
  existing rule: never mention below-average — the runner showed up, that counts.) No running app does this — protect it.

## 4. Personality — adjectives, ranked

Raw list: refreshing, productive, slight futuristic, sexy/fit, culture, sophisticated, nature.
Seven can't lead equally. Proposed center of gravity (Manley to confirm):

- **Lead with three:** Warm & alive (refreshing / nature) · Productive & sophisticated · Sexy & charged (the *Challengers* heat).
- **Season with:** slight futurism · running-culture texture.

## 5. Visual thesis — "frosted glass over a warm, natural world"

> ⚠ PARTIALLY SUPERSEDED by §0 (July 2026): base is now clean WHITE (cream/warm palette dropped),
> no serif type, and cinematic geometric athlete art is allowed. Frosted glass survived and grew
> into the material-glassmorphism panel system. Kept below for the reasoning history.

The through-line across every reference Manley pulled:

- **Translucent / blurred glass blocks** floating over a **warm background** (trail map, park, sun) so
  the world glows through the gaps. One device that solves blocks-not-cliché + the "glimpse of background"
  + warm futurism all at once.
- **Real athletic photography** — sun, sweat, health — *not* flat illustration or blob animation
  (the move every fitness app makes; avoid it).
- **Confident editorial type.**
- **Restrained futuristic-instrument layer** — calm and clean (like the white H₂O scanner reference),
  not Tony-Stark chrome.
- **Warm futurism is the wedge:** natural, sunlit warmth — skin-tones, daylight, heat-shimmer — instead
  of cold blue/chrome. *"Warm" is the temperature, not a literal gold palette.* Exact colors are still
  open, and **definitely not** the saturated amber/gold of the reference apps. Everyone else's futurism
  is cold; yours radiates — but quietly.
- **Layout reference only (and loose):** the "Today Running / 25°" app shows a usable block + photography
  *layout* direction. Take the **layout only** — not the colors, and know it's still a good distance from
  the target. Do not read it as "the look."
- **Base tone = light (decided).** The primary surface — the "using and navigating" state — is light, warm,
  daylight. It should breathe and feel fresh / healthy / alive.
- **The arc: dark open → light use.** Dark, premium, cinematic is reserved for *moments*, not the whole app.
  You open into something moody and expensive (Grow-the-Future energy) for the entry / anticipation, and it
  *opens into daylight* as you actually use it. Likely dark again for the post-run reflection — the moody
  close. Think of it as a film: dark cold-open → daylight body → reflective close. This layers directly onto
  the state model (§8): tone tracks state, light is home.

## 6. Rules of the look

- **Avoid unconsidered equal-card dashboards.** Hierarchy and asymmetry should usually prevent the
  generic tidy-grid cliché: one hero block with supporting scenes at different sizes. A bounded
  one-of-three control can still use equal adjacent forms when equality communicates the domain;
  the current State Glass energy tiles are the deliberate exception, not a new dashboard template.
- **The UI dresses like a runner.** Style components like running gear/fashion, not software widgets —
  the start control "worn like shades," the hero piece that carries the fit.
- **Restraint is the brand.** Fewer features, done beautifully. Cutting lazy half-features is strategy,
  not laziness. "Without overdoing it" is the whole craft — imply the heat with one warm photo and a lot
  of calm space, don't crank it.
- **Nature texture, formatted.** Nature woven into the UI (the leafy, organic, alive feel) is welcome —
  but it has to be *disciplined*. The formula: image-1 ingredients (nature, freshness) with Grow-the-Future
  structure (rigor, hierarchy, restraint). Texture without formatting reads cluttered; that's the failure mode.
- **Let photography break the frame.** A runner photo that spills out of its card/block (subject crossing the
  edge) reads dynamic and premium — an editorial device worth using, especially on the reflection screen.

## 7. Home screen concept (the block idea)

- 3–5 blocks, slightly apart, the warm world glowing through the gaps; lean into the frosted-glass card.
- One hero; the rest supporting scenes — e.g. a run-style keyword (tap to expand), a live GPS/run block,
  a single metric block (calories), a shoe-mileage tag, a reflection block.
- Possible **Claude-like clean top selector** (a few simple modes) as a light, authentic AI touch.

## 8. States, not theme-switching

Resolves the "adaptive interface" itch without the chaos of maintaining multiple UIs. Same bones,
hierarchy reflows by state:

- **Idle** → start block is the hero; anticipation.
- **Running** → live block takes over (GPS, metrics breathing).
- **Just-finished** → the reflection / response block becomes the hero (the voice-message moment,
  shoe miles ticking up, "this was a different kind of run" signal).

Optional lighter accent: warm by day / cool-calm before sunrise — skin only, never the skeleton.

## 9. The AI reflection

AI is authentic here — `RunAgent` already powers the post-run response. Let AI show up as **warmth and
intelligence in the response** — the *feeling* of being seen — not as robotic chrome aesthetics. That also keeps
it on the warm side of futurism, away from Tony Stark. (The "hanging in there" energy is a feeling to evoke, not
a line to print.)

## 10. Guardrail: bold skin, familiar bones

The one correction worth holding: "user-friendly" ≠ "generic." Manley's heroes (Claude, Nike Run Club,
Lululemon) are all distinctive *and* effortless. Used sweaty, one-handed, heart pounding, post-run — if the
screen is gorgeous but unfindable, the art never lands; it's hidden behind frustration.

- **Bones = usability:** predictable structure, one obvious primary action, reachable with a sweaty thumb.
- **Skin = creativity:** warmth, glass, cinematic tone, the gear metaphor — go as hard as you want.
- Keep the bones quietly friendly *so the skin can go harder, not softer.*

## 11. The signature moment (RESOLVED — see §0)

- The post-run response (voice-message vision): assuring — you were seen, your work isn't unnoticed.
- The Run Style reveal should feel **rewarding / earned**.
- Still cooking — come back to this with intent.

## 12. Open questions / parked

- Rank the 7 adjectives (Manley's call).
- Fully define the signature moment.
- A night / pre-dawn version of the palette?
- Clear-ocean accent under consideration — sunlit aqua (not cold navy / tech-blue), tied to water / hydration /
  "refreshing"; play it against the warm daylight + green so it stays fresh, not clinical.
- Self-competitiveness without a social club (PRs, streaks, "beat your shadow")?
- Competition vs social-comparison: **keep competitive energy in the feel and visuals** (PRs, intensity, racing —
  don't downplay it). **For now, leave social-comparison / friend features out entirely** — not a rejection, just
  deferred; Manley isn't fully against them, just undecided, so revisit later. Self-comparison and personal stats
  boards are still fine. The soul governs *tone*, not whether competition exists.
- Run-picture gallery — a future feature (a home for run photos). Out of current scope; revisit later under the
  scope rule. The frame-breaking photo *device* can be used now regardless.
- Build-tech decision (native vs Flutter vs mobile web) — later.

---

## October 22, 2026 — Existing brand wording collected for review

**Purpose:** preserve the wording already scattered through the project so Manley can compare it
without losing its original context. The search found no dedicated list of approved slogans.
These 21 entries are existing positioning statements, historical phrases, or music-specific
expressions; collecting them here does not approve them as public slogans. Short excerpts remain
excerpts, and historical material does not become current merely by appearing in this list.
Wording is reproduced exactly, with wrapped source lines joined for readability.

**Opening-screen boundary:** none of these entries is approved for the opening screen. Its text
remains unchanged; this is a reference collection, not an instruction to add a subtitle or slogan.

### Existing candidates and their original context

| # | Existing wording | Classification and original context | Source |
| --- | --- | --- | --- |
| 1 | “RunState helps you understand what your run meant, not just what you recorded.” | Positioning — recorded core-purpose sentence; not an approved opening tagline. | [user_goals.md, lines 113–114](user_goals.md#L113) |
| 2 | “RunState is built from running culture outward. It helps runners understand the state they brought into a run, what the run demanded, and what they carried out of it.” | Positioning — explicitly called the positioning line in its source. | [user_goals.md, lines 118–119](user_goals.md#L118) |
| 3 | “A runner alone at 5am with no followers and no feed still feels like they belong to something.” | Positioning — recorded north-star sentence, rather than short public copy. | [user_goals.md, lines 120–121](user_goals.md#L120) |
| 4 | “RunState is a running app designed to understand a run beyond its numbers by interpreting the environment surrounding the experience.” | Positioning — current public project introduction. | [README.md, line 4](../../README.md#L4) |
| 5 | “More than a fitness tracker.” | Positioning — existing headline in the earlier creative direction. | [Earlier positioning, line 333](#1-positioning) |
| 6 | “A special weapon in a runner's arsenal.” | Positioning — quoted phrase in the earlier creative direction. | [Earlier positioning, line 337](#1-positioning) |
| 7 | “the app that knows you” | Positioning — identity shorthand in the cold-start discussion, which also cautions against pretending to know a runner before evidence exists. | [Earlier cold-start principle, line 309](creative_direction_ui.md#L309) |
| 8 | “personal in value, cultural in voice, communal only ever in texture.” | Positioning — exact fragment describing the product's relationship to culture and community. | [user_goals.md, line 122](user_goals.md#L122) |
| 9 | “the best run is not always the fastest” | Positioning — quoted principle behind the proposed Top Run Highlights feature. | [parked_feature_ideas.md, lines 116–117](parked_feature_ideas.md#L116) |
| 10 | “the best run is not always the fastest or longest run.” | Historical — exact fragment from the earlier Top Run Highlights rationale. | [Archived/UNIQUE_IDEAS.md, line 93](../Archived/UNIQUE_IDEAS.md#L93) |
| 11 | “progress is more than one number.” | Historical — exact fragment identified as a main theme in the earlier ideas document. | [Archived/IDEAS.md, line 182](../Archived/IDEAS.md#L182) |
| 12 | “a run has more than one result.” | Historical — exact fragment from the earlier brand-direction explanation. | [Archived/BRANDING.md, line 42](../Archived/BRANDING.md#L42) |
| 13 | “Clean enough for a boardroom, cool enough for the culture.” | Historical — identity sentence in the superseded visual brief; not a current design requirement. | [ui-design-brief-v1.md, line 20](../ui-design-brief-v1.md#L20) |
| 14 | “Small footprint, high meaning” | Positioning — exact fragment from the earlier creative direction. | [Earlier positioning, line 338](#1-positioning) |
| 15 | “you were seen, your work isn't unnoticed.” | Positioning — intended emotional effect of the post-run response, not previously designated a slogan. | [Signature moment, line 462](#11-the-signature-moment-resolved--see-0) |
| 16 | “the taste matched the discipline” | Music-specific — quoted signature-moment phrase associated with the AI reply card. | [Earlier signature-moment decision, lines 66–67](creative_direction_ui.md#L66) |
| 17 | “your best-run soundtracks” | Music-specific — existing expression for music tied to the runner's own run history. | [Earlier music-screen concept, line 323](creative_direction_ui.md#L323) |
| 18 | “RunState aims to help a runner feel seen after a run.” | Music-specific — purpose statement introducing the reflection feature's development story. | [music_reflection_development_story.md, line 61](music_reflection_development_story.md#L61) |
| 19 | “The music can answer the reflection without the reflection naming the song or explaining it.” | Music-specific — explanation of the reflection-to-song experience. | [music_reflection_development_story.md, line 64](music_reflection_development_story.md#L64) |
| 20 | “RunState is a run tracker that records performance and how each run felt, so runners can eventually learn what conditions help them perform their best.” | Historical — explicitly labeled “Pitch” in the earlier ideas document. | [Archived/IDEAS.md, line 78](../Archived/IDEAS.md#L78) |
| 21 | “The app should feel like a personal run tracker that helps runners understand how their runs are going, not just how far or how fast they ran.” | Historical — earlier brand-direction sentence, written as guidance rather than finished public copy. | [Archived/BRANDING.md, line 40](../Archived/BRANDING.md#L40) |

### Supporting principles, not public slogans

- **Identity pillars:** “RUN CULTURE,” “STATE-AWARE REFLECTION,” “MUSIC IDENTITY,” and
  “PRIVACY-FIRST PERSONAL INSIGHT” — [user_goals.md, lines 111–116](user_goals.md#L111).
- **Core product loop:** “log a run → track how you felt → learn something meaningful from it.”
  — [user_goals.md, line 134](user_goals.md#L134).
- **Internal design language:** “The UI dresses like a runner.”; “Restraint is the brand.”;
  “Fewer features, done beautifully.” — [Rules of the look](#6-rules-of-the-look).
- **Internal usability shorthand:** “bold skin, familiar bones” — [the existing guardrail](#10-guardrail-bold-skin-familiar-bones).
- **Historical design principle:** “Every screen should feel like it was art-directed, not just
  designed.” — [ui-design-brief-v1.md, line 22](../ui-design-brief-v1.md#L22).
- **Feature subtitle, not app slogan:** “Interesting habits and patterns from your runs.” —
  [Archived/IDEAS.md, line 231](../Archived/IDEAS.md#L231).

Ordinary UI questions, saved-run confirmations, Energy labels, and one-off reflection examples
are not included as brand candidates. The earlier names RunNet and MoodPace remain naming
history. The superseded claim “RunState is not a fitness tracker” is also excluded: the later
positioning explicitly keeps competent run tracking as the foundation.

### Recommendation for the next copy discussion — advice, not a prior decision

Start with these three existing lines. None is selected or approved by this recommendation:

1. **“RunState helps you understand what your run meant, not just what you recorded.”** — the
   clearest explanation of the product's distinct purpose.
2. **“a run has more than one result.”** — the shortest expression of the broader runner-state
   idea, preserving the existing source fragment rather than inventing a revised tagline.
3. **“the best run is not always the fastest”** — a concrete running principle that invites
   conversation about Energy, effort and context without implying a medical or training promise.

## October 23 — trailer and logo plan, saved for later

**Status: Planned, deferred.** Manley asked to preserve the presentation recommendation below,
not start production. Finish the active run screen and required existing-functionality work
first. He also explicitly wants a RunState logo and a custom Android launcher icon: the image
people tap on their phone should replace the generic Android graphic. No logo, icon, tagline or
final trailer script has been selected. Keep the accepted opening design and other screens.

Suggested order when this work resumes: finish/review Active Run UI; approve the logo and launcher
icon; capture the finished app; storyboard and produce the trailer. Settle the final branding
before final title/end cards and phone-launch recordings to avoid rework. This is documentation
only, not approval to generate assets, change the app, purchase anything or publish a video now.

### Purpose and format

Create a roughly 55-second trailer built around the working app for AfroTech conversations with
employers, leaders and other attendees, also usable afterward on a portfolio or professional
profile. Use it to earn interest, then offer the real phone demo and a clear explanation of
Manley's engineering/product decisions. Preserve the roadmap's short explanation, approximately
90-second demonstration and optional three-minute technical explanation as supporting material.

The central story: RunState connects recorded performance, the runner's experience and their
music into a meaningful finish. Follow one coherent run instead of listing unrelated features.
Favor real app recordings; device mockups, crops and transitions can frame them, but generated
visuals must not invent interface behavior. Let the actual reflection excerpt land rather than
cutting rapidly through the feature that makes the experience distinct.

| Approximate time | Material | Purpose |
| --- | --- | --- |
| 0–5 seconds | Accepted opening collage and RunState identity | Establish running culture and visual character. |
| 5–13 seconds | Runner preparing, optional Energy selection, countdown | Establish a personal starting point. |
| 13–23 seconds | Finished Active Run UI and real recorded metrics | Demonstrate functioning software. |
| 23–45 seconds | Completion and a strong excerpt from an actual generated reflection | Show the signature experience with enough time to understand it. |
| 45–55 seconds | Saved activity in History, then creator name and project link | Show durable value and enable follow-up. |

Timing is a proposed editing guide, not a locked script. Choose a dependable example after the
remaining UI and real-run review. Use actual app voice audio for the demonstrated reflection;
do not replace it with an advertising line and imply the app generated that result. Make any
controlled example or excerpt understandable as such. Energy Insights is a foundation for
noticing personal patterns, not proven fitness improvement or a universal readiness measure.
Avoid promises of perfect song matching or instantaneous generation in every circumstance.

### Production and presentation decisions

- Keep the established clean layouts, colorful running imagery, restrained motion and distinctive
  voices. Avoid effects that obscure what the product does.
- Make the story understandable with sound off using readable captions. Keep an offline video
  copy on the demo phone as a dependable backup.
- End with Manley Johnson, Creator of RunState, and an actual project/professional-profile link
  or QR code when chosen. Prefer an evergreen invitation such as Explore the project / Connect
  with Manley. Do not imply an app-store release that has not happened.
- The screenshot's ElevenLabs workflow is a starting point: confirm audience/message and footage,
  approve a short storyboard, assemble real screens, add voice/music/captions, review accuracy and
  mix/export. Its Studio supports imported video, speech, timeline alignment and captions:
  https://elevenlabs.io/docs/help-center/product/studio/studio/how-can-i-create-a-voiceover-for-my-video-using-studio
- Decide video music before editing. Spotify playback access is not permission to bake its
  recordings into a trailer; its developer policy prohibits synchronization with video. Use
  music cleared for the video, and do not present replacement background music as evidence that
  RunState selected that track. Source checked October 23: https://developer.spotify.com/policy
- For the employer conversation, prepare the real development story: music data alone initially
  failed to improve the writing; listening feedback led to revisions of song study/writing and
  real phone audio handoffs. Explain actual responsibilities and tools accurately. Refer to
  `music_reflection_development_story.md` rather than inventing a success story.

The logo/icon is a separate pending design choice. It should remain recognizable at small phone
icon sizes and fit the established RunState identity. Review visual options with Manley before
installing a replacement; no specific symbol, color scheme or use of the existing ring is assumed.
