# RunState — UI Design Brief v1
**Screens in scope:** Start (Idle) · Pre-Run Energy
**Purpose:** High-fidelity direction for Claude Design / Figma execution

---

## 1. Design Identity

**Name:** Editorial Athletic

RunState is not a fitness tracker. It is a personal performance journal with an identity — the kind of app that belongs in a luxury streetwear campaign, not a sporting goods store. It is calm and confident. It does not shout. It sees the runner, contextualizes their effort, and reflects it back with intelligence and style. The closest cultural analogue is what you would get if a high-end headphone brand and a record label co-designed a running app. Clean enough for a boardroom, cool enough for the culture.

Every screen should feel like it was art-directed, not just designed.

---

## 2. Color System

| Role | Name | Hex | Usage |
|---|---|---|---|
| Base | Deep Slate | `#16181C` | Primary background on dark screens, cards, overlays |
| Surface | Elevated Slate | `#1F2228` | Cards, input surfaces sitting above base |
| Accent — Warm | Aged Gold | `#C4A35A` | PRs, highlight moments, selected states, hero button glow |
| Accent — Cool | Sage Whisper | `#7A9185` | Nature presence — label dots, subtle dividers, passive data only |
| Text — Primary | Cream | `#EDE8DF` | All primary body and display text on dark surfaces |
| Text — Secondary | Warm Mist | `#9A948C` | Labels, captions, secondary data |
| Text — Dark surface | Deep Ink | `#1A1B1F` | Text on light or cream surfaces if any light mode moment exists |

**Palette philosophy:** The slate is never pure black — it has a warmth in the undertone that lets the cream and gold breathe against it. Sage Whisper (`#7A9185`) is the nature presence: it shows up in one accent at a time, never dominant, always earned. It is felt before it is noticed.

---

## 3. Typography System

**Three-font hierarchy — used strictly, never mixed casually:**

### Display — Barlow Condensed
- Weight: Black (900) for large stats and metric numbers
- Weight: Bold (700) for section headers and primary labels
- Usage: Run time, pace, distance, PR callouts, START label, LIFETIME mileage
- Letter-spacing: `-0.02em` on large sizes, `0.04em` on small caps labels
- This is the athletic voice. It is confident and takes up space.

### Serif — DM Serif Display (Italic preferred in emotional moments)
- Weight: Regular italic for questions and moment-of-feeling copy
- Usage: "How's your energy going into this run?" and any copy that speaks directly to the runner as a person rather than presenting data
- This is the human voice. Warm, personal, unhurried.

### Body — Inter
- Weight: Regular (400) and Medium (500)
- Usage: Navigation labels, option pill text, captions, ambient UI text
- Letter-spacing: `0.01em` for readability at small sizes

**Label treatment (locked pattern):**
Small caps labels like `PRE-RUN · OPTIONAL` use Inter Medium, tracked at `0.12em`, rendered in Warm Mist (`#9A948C`). The dot separator is always a filled circle in Sage Whisper (`#7A9185`), 5px diameter, vertically centered. This is the one place sage lives consistently across every screen.

---

## 4. Art Direction

### The Visual Signature
The geometric dissolving runner — the low-poly wireframe figure mid-stride, fragmenting at the edges into scattered triangular shards — is RunState's identity mark. It is not a logo. It is an art layer: a texture-level presence that appears behind content, partially visible, never competing for attention. Think of it as a watermark with character.

- Render it at `8–12% opacity` over the base slate
- Position it bleeding off one edge, never centered
- It embodies the futuristic quality the app should make you *feel* rather than read explicitly

### Motion Philosophy
Motion in RunState is never decorative. Every transition serves a directional metaphor — you are moving forward, always. Slides are horizontal (pre-run answers pull in from the right as the START prompt slides left). Data reveals are vertical (stats build upward, not just fade in). The runner is always moving forward in space.

- Easing: `cubic-bezier(0.4, 0, 0.2, 1)` — ease-out for entries, ease-in for exits
- Duration: 280ms standard, 400ms for hero transitions
- No bounce, no spring. Controlled, elegant.

### Photography Direction (for hero image slot)
Editorial, not commercial. The runner should not be posed for a camera. They are in motion or at rest in an authentic moment — like the tennis athlete at rest in the stadium with eyes closed. Slightly desaturated. The image always sits behind a gradient overlay — dark at the bottom, transparent at the top — so the card layer integrates rather than sits on top.

---

## 5. Screen 1 — Start (Idle)

*Reference: wireframe labeled "1 · Idle" — the card/photo composition with START hero button*

### Layout Structure
Full-bleed photography in the upper ~60% of the screen. Below the photo, content lives on the deep slate base. A dark card (`#16181C` at 92% opacity with `backdrop-filter: blur(12px)`) straddles the photo's lower edge — its top edge sits approximately 40px up into the photo, its bottom sits on the slate. This is the architectural move that makes the screen feel premium and layered, not flat. Preserve this.

### Navigation — Run / Style / You
Three tabs, horizontally centered, sitting above the photo area or pinned to the very top of the screen.
- Inactive: Inter Medium, Warm Mist (`#9A948C`), no underline
- Active: Inter Medium, Cream (`#EDE8DF`), with a 2px underline in Aged Gold (`#C4A35A`) sitting 4px below the baseline
- No background pill, no box — the underline alone signals active state. Minimal.
- Tab dividers between items: a single forward slash `/` in Warm Mist at 40% opacity, matching the wireframe's style — this is a good detail, keep it.

### The Straddle Card
- Background: `#16181C` at 92% with blur
- Rounded top corners: `16px`. Square bottom (it runs to the screen edge).
- Left side: Time and location stack
  - Time: `6:15 AM` — Barlow Condensed Bold, 28px, Cream
  - Time label: `TIME` — Inter Medium small caps, 10px, Warm Mist, `0.12em` tracking
  - Location: `Cedar Trail Loop` — Inter Medium, 14px, Cream
  - Location label: `LOCATION` — same label treatment as TIME
  - A small location dot icon in Sage Whisper precedes the location label
- Right side: START hero button

### START Button
Circle, minimum 72px diameter, sitting centered vertically on the card and visually dominant.
- Background: Aged Gold (`#C4A35A`) with a very subtle radial gradient darkening toward center by 10%
- Label: `START` — Barlow Condensed Black, 18px, Deep Ink (`#1A1B1F`), `0.08em` tracking
- Outer ring: A thin (1.5px) ring in Aged Gold at 30% opacity, 8px outside the button edge — this creates a soft aura without a harsh glow
- Idle state animation: The outer ring pulses slowly — scales from 1.0 to 1.08 and back over 2.4 seconds, infinite, ease-in-out. Barely perceptible. It breathes.
- The button sits at the card's right edge, partially overhanging — it straddles the card boundary the same way the card straddles the photo. Layering as a design principle applied consistently.

### Art Layer
The geometric dissolving runner appears in the photo zone, rendered at very low opacity (8%), offset to the right, legs extending downward. It sits between the photo and the card, reinforcing the sense of movement behind the content.

---

## 6. Screen 2 — Pre-Run Energy

*Reference: the actual screenshot showing the sage-to-cream gradient version — keep the layout logic, replace the palette and visual treatment entirely*

### What transfers from the current build
- Layout structure: label at top-left, question below it, three option pills, lifetime stat at bottom — this hierarchy is correct, keep it
- The `• PRE-RUN · OPTIONAL` label — keep the pattern, re-skin with the locked label treatment (Inter Medium small caps, Warm Mist, Sage dot)
- `I'm Here / Ready-ish / Let's Go!` — keep these exact labels. They have personality and voice.
- `LIFETIME · 1,284 MI` at the bottom — this is one of the strongest product decisions in the current build. Keep it, elevate it.
- The circular reset/refresh button bottom-right — keep it, give it a subtle slate border treatment

### What changes
The sage-to-cream gradient becomes the deep slate base (`#16181C`). This single change moves the screen from wellness app to editorial athletic. Everything else adjusts accordingly.

### Background
Deep Slate (`#16181C`) top to bottom. In the large empty upper section above the question, the geometric dissolving runner appears as the art layer — positioned upper-right, fading off the right edge, at `10% opacity`. This resolves the empty-feeling upper half without adding content that competes with the question.

A very faint vertical gradient from `#1A2220` (a slate with the faintest green undertone, nearly invisible) to `#16181C` runs behind everything — this is the nature-futuristic presence. You feel it; you cannot name it.

### PRE-RUN Label
`• PRE-RUN · OPTIONAL` — Inter Medium, `0.12em` tracking, 11px, Warm Mist (`#9A948C`). The `•` is a filled circle in Sage Whisper (`#7A9185`), 5px, vertically aligned to cap height. 28px below the status bar.

### The Question
`How's your energy going into this run?`
- Font: DM Serif Display Italic
- Size: 32px
- Color: Cream (`#EDE8DF`)
- Line height: 1.25
- Max width: 80% of screen width, left-aligned
- 12px below the PRE-RUN label
- This is the emotional center of the screen. Give it room.

### Option Pills — I'm Here / Ready-ish / Let's Go!
Three full-width rounded rectangles, `16px` border radius, stacked with `10px` gaps.

**Unselected state:**
- Background: Elevated Slate (`#1F2228`)
- Border: 1px, `#2E3138` (barely visible, gives the card an edge in the dark)
- Text: Cream (`#EDE8DF`), Inter Medium, 17px
- The circle indicator on the right: replace the generic radio circle with a thin (1.5px) ring, 22px diameter, in `#3A3D44` — present but quiet

**Selected state:**
- Background: `#1F2228` unchanged
- Border: 1px, Aged Gold (`#C4A35A`) — the gold border is the selection signal, clean and confident
- The right ring fills: a solid gold circle at 70% opacity
- Text: Cream, no weight change — the border does the work, the text does not shout
- A very subtle gold inner glow on the border: `box-shadow: inset 0 0 0 1px rgba(196, 163, 90, 0.15)` — the card barely warms

**Transition:** 160ms, ease-out. Fast and responsive, not performative.

### LIFETIME Stat
`LIFETIME` label: Inter Medium, 10px small caps, Warm Mist, `0.12em` tracking, centered
`1,284 MI`: Barlow Condensed Black, 42px, Warm Mist (`#9A948C`) — intentionally not cream. This is ambient context, not a headline. It should be readable and impressive without competing with the question above.

The `MI` unit sits baseline-aligned, Barlow Condensed Bold, 18px, at 60% opacity of the number color.

28px above the home indicator line.

---

## 7. Shared Component Rules

- **Corner radius:** Cards and pills: `16px`. Buttons: fully circular or `16px`. Never `8px` or less — that is too sharp for this identity.
- **Spacing unit:** Base 4px. Prefer multiples of 8px for all vertical rhythm.
- **Dividers:** Never lines. Spacing alone separates content.
- **Iconography:** Single-weight, 1.5px stroke, rounded caps. No fill icons. Keep them small — 16–20px — they are functional, not decorative.
- **Status bar:** Dark content (cream icons) against the slate background. No special treatment needed.

---

## 8. Images to Include When Passing to Claude Design

**Include these moodboard images:**

1. **Warwick Acoustics screenshot** — overall editorial premium tone, dark warm base, mixed typography (bold sans + italic serif coexisting)
2. **Amber/gold Dribbble running app (3-screen)** — stat display hierarchy, dark athletic energy, how photography and UI coexist, the "Today Running" screen especially
3. **Geometric dissolving runner — black/white particle version** — the particle/shard art style for the visual signature
4. **Geometric low-poly wireframe runner — colorful version** — the wireframe triangulation aesthetic
5. **Tennis athlete photo** — editorial photography mood and quality, the "cool athlete at rest" energy

**Include these working files:**
6. **Wireframe image (Screens 1 and 2 from the first image)** — so the designer understands the layout architecture and card-straddling composition that must be preserved
7. **Pre-run energy screenshot (the sage version)** — so the designer understands the layout logic and label hierarchy to keep, while understanding the palette is what changes

**Do not include:**
- The UpTech screenshots — too generic, would pull toward standard tech aesthetic
- The sage/green nature mockup — this is directionally opposite to where the palette is going; including it risks confusion about the green direction
- The running tracker mobile UI kit — too light and generic, no editorial quality
