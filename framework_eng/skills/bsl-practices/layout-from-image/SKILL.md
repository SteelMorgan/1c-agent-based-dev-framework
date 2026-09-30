---
name: layout-from-image
installable: true
description: "Extraction of a verifiable visual specification of a layout from a reference image: geometry, borders, fills, text styles, and negative checks. Use when the visual reference is given as a PNG/JPG/screenshot."
---

# Layout from Reference Image

## Purpose

Use this skill when the source of truth for appearance is an image: a screenshot, PNG/JPG mockup, export from a designer, an image of a printed form or report.

An image is not a ready-made specification. Before implementation, you need to extract a verifiable model from it:

- `layout spec` — geometry;
- `border spec` — lines and contours;
- `style spec` — fills and text;
- `negative checks` — areas where an element must not be present.

This skill is independent of the implementation technology. Its result can be used by an MXL layout, СКД layout, printed form, HTML report, or another output system.

## Output Artifact

Create a markdown specification with sections:

```text
1. Source
2. Normalization
3. Layout spec
4. Border spec
5. Style spec
6. Negative checks
7. Acceptance tolerances
8. Open questions
```

Do not move on to implementation until at least the major areas, borders, and text styles have been identified.

## 1. Source

Record:

- path to the image;
- image dimensions;
- working crop, if the image has margins, UI chrome, row/column numbers, or extraneous background;
- why this crop was chosen;
- comparison scale: original, normalized to width, normalized to the grid.

## 2. Normalization

Before comparing the result and the sample, bring them into the same coordinate system:

- content bbox;
- scale;
- rotation, if any;
- crop without external UI framing;
- column/row grid, if it is visible or can be inferred from repeating borders.

If screenshots of different sizes are compared, do not compare absolute pixels without normalization.

## 3. Layout Spec

Describe the geometry:

| Field | What to record |
|---|---|
| Zone | Name of the semantic block: header, KPI, table, row, footer |
| bbox | Coordinates in crop or in a normalized grid |
| Size | Width/height |
| Position | Relative to neighboring zones |
| GAP | Empty spaces between zones |
| Repetition | A single zone or a row/group template |

Layout proof answers only the question "where is the zone and what is its size". It does not prove the correctness of borders, color, or font.

## 4. Border Spec

Describe borders as segments, not as a general impression:

| Field | What to record |
|---|---|
| Segment | `top`, `left`, `right`, `bottom`, inner line, separator |
| From/to | Coordinates of the start and end |
| Color | RGB/HEX or an allowed range |
| Thickness | px or a range |
| Continuity | Solid, dashed, gap |
| Purpose | Outer contour, header line, table border |

Be sure to separate:

- the outer contour of the block;
- internal separators;
- table borders;
- gray auxiliary lines;
- accent colored lines.

For each important line, add a negative check for zones where the line should not be present. An extra line is the same defect as a missing one.

Border proof must confirm not only the presence of the required lines, but also the absence of extra segments:
check negative zones on the runtime image separately from the source spec. If in the source
layout/DSL the line is not defined, but runtime still renders it, this does not count as a passed check -
you need to record the discrepancy as a limitation or defect of the output technology and verify an alternative
layout structure.

## 5. Style Spec

Describe the styling:

| Field | What to record |
|---|---|
| Fill | Background color of the zone |
| Text | Meaningful text or a parameter pattern |
| Text color | RGB/HEX or a range |
| Size | Relative to the zone or an estimated px/pt value |
| Style | Regular, bold, italic, underline |
| Alignment | Horizontal and vertical |
| Indents | Internal text padding from the borders |
| Format | Numbers, dates, percentages, digit grouping spaces |

If the exact font cannot be proven from the image, record measurable traits: letter height, boldness, color, alignment, relative size.

## 6. Negative Checks

List the prohibitions separately:

- there must be no border in this area;
- there must be no separator between these lines;
- the background must be empty/white;
- the text must not extend beyond the bbox;
- the table must not have an extra column;
- the block must not go beyond the visible area.

These checks are needed because visual defects often appear as extra elements.

## 7. Acceptance Tolerances

For each class of check, specify the allowed deviation:

- bbox/positions: usually `1-3 px` after normalization or a fraction of a grid cell;
- color: allowable RGB/HSV delta if there is antialiasing;
- line thickness: exact value or range;
- text: antialiasing is allowed, but not a change in size/style;
- overall size: separately from local areas.

Do not declare "looks similar" as acceptance. Acceptance must refer to specific measurements.

## 8. Proof Workflow

1. Extract `layout/border/style spec` from the image.
2. For disputed visual requirements, check the target technology API before implementation:
   - if the API supports the element directly, implement it with the native property;
   - if the API supports only imitation through another mechanism, separately record the cost and risks of the imitation;
   - if the API does not support the element, record the proven acceptable deviation rather than trying to pixel-tune the impossible.
3. Implement the layout.
4. Get the runtime result in the same way the user will see it.
5. Normalize target and actual.
6. Check three layers:
   - layout proof;
   - border proof;
   - style proof.
7. Check negative checks separately.
8. In the report, separate:
   - proven;
   - not proven;
   - differs;
   - requires investigation of platform capabilities.

## What Not To Do

- Do not treat bbox/grid proof as proof of full visual equivalence.
- Do not check only large areas and ignore borders/fonts/fills.
- Do not treat the absence of a line in DSL/XML as proof of its absence in the result without a runtime check.
- Do not claim that an element cannot be reproduced until the target technology's API capabilities have been checked.
- Do not spend a long time tuning a visual element if the target technology does not have a corresponding property or supports it only through a separate imitation mechanism.
- Do not turn a specific example into a universal rule without generalization.

---
depends_on: []
---
