---
name: layout-from-description
description: "Create a single-sheet .xlsx prototype and a layout/border/style/data-binding specification from an approved verbal description, using only the closed MXL-compatible whitelist."
---

# Layout from a Verbal Description

Transform an approved description of a spreadsheet document into an Excel prototype for review, without
Excel properties that have no guaranteed MXL equivalent. The prototype defines the presentation, but
does not prove the correctness of runtime data and is not a finished MXL layout.

## When to Apply

| Trigger | Action |
|---|---|
| The source of truth is an approved verbal description | Create a single-sheet `.xlsx` and a specification |
| A visual prototype is needed before MXL implementation | Use only the closed whitelist |
| The prototype is planned for transfer to `layout-from-excel` | Perform a pre-export check of all properties |
| A non-portable Excel property is found | Stop the workflow and perform mandatory self-correction |

Do not apply this skill to an existing Excel workbook as the source of truth: use `layout-from-excel`
for that instead. Do not apply it instead of `layout-from-image` if the approved
source is an image.

## Result

Prepare a single review package:

1. one `.xlsx` with exactly one visible sheet;
2. `layout spec`: regions, rows, columns, merges, widths, and heights;
3. `border spec`: border segments, sides, styles, and colors;
4. `style spec`: fills, fonts, alignment, wrapping, and formats;
5. `data-binding spec`: purpose of static text and future runtime parameters;
6. a workbook structure snapshot and a checklist of allowed/prohibited properties.

Explicitly mark all example values as illustrative. They show geometry and formatting,
but do not replace the data contract from `tabular-output-data-verification`.

A completed example of this specification: `references/layout-spec-example.md`.

## Closed positive whitelist

Allow only the listed properties. Any property not listed is prohibited until it is separately
verified and explicitly added together with its MXL equivalent.

| Allowed Excel prototype property | MXL equivalent |
|---|---|
| One visible sheet | One designed spreadsheet document/layout |
| Ordinary cells | MXL cells and areas |
| Static text and sample values | Cell text and area parameters |
| Rows and columns | Spreadsheet document grid |
| Merges | Merged cell areas |
| Explicit column widths and row heights | MXL column widths and row heights |
| Borders | MXL cell/area borders |
| Solid fills | Cell/area background color |
| Font: family, size, bold, italic, color | MXL font formatting |
| Horizontal and vertical alignment | MXL cell/area alignment |
| Text wrapping | MXL cell text wrapping |

### Allowed format codes

This is a closed list of numeric and date-time displays:

| Excel format code | Semantics | MXL equivalent |
|---|---|---|
| `0` | integer without separators | numeric parameter with `ЧДЦ=0` |
| `0.00` | number with two decimal places | numeric parameter with `ЧДЦ=2` |
| `#,##0` | integer with thousands grouping | numeric parameter with `ЧГ=3,0;ЧДЦ=0` |
| `#,##0.00` | number with two decimal places and grouping | numeric parameter with `ЧГ=3,0;ЧДЦ=2` |
| `dd.mm.yyyy` | calendar date | text parameter, preformatted as `dd.MM.yyyy` |
| `dd.mm.yyyy hh:mm` | date and minutes | text parameter, preformatted as `dd.MM.yyyy HH:mm` |

Transfer dates and date-times as prepared text. Do not promise to transfer Excel type/locale
semantics. Prohibit percentages, currency symbols, scientific notation, fractions, custom
masks, and any other format code until a proven check adds it to the whitelist together
with its exact MXL equivalent.

## Unconditionally prohibited properties

Do not allow in the prototype:

- multiple sheets;
- formulas and business logic;
- pivot tables;
- macros and VBA;
- external links and queries;
- Power Query and the data model;
- filters and slicers;
- frozen panes;
- a specified print area;
- conditional formatting;
- charts;
- images, shapes, and other free-form graphic objects;
- sheet or workbook protection;
- hidden rows, columns, or sheets;
- named ranges as a carrier of logic;
- Excel tables and other Excel-specific interactive features.

## Workflow

1. Record the approved verbal description as the source of truth. Do not silently fill in
   missing visual or semantic requirements.
2. Break the description down into a `layout/border/style/data-binding` specification.
3. Create an `.xlsx` with one visible sheet, using only the closed whitelist. Record the MXL equivalent
   for each property in the specification.
4. Mark demonstration data and separate it from future runtime sources.
5. Perform a structural pre-export check of the workbook: sheets, cells, dimensions, merged cells,
   styles, format code, and the absence of all prohibited classes.
6. Obtain approval of the prototype and specification.
7. Run `tabular-output-data-verification`. Data verification can proceed in parallel with
   prototype approval, but it must be completed before runtime composition or manual output.
8. If structural transfer is required, pass the approved file to `layout-from-excel`,
   then implement the visual layer through `mxl-layout-patterns`.
9. Pass the implemented layout to the downstream consumer: `skd-report-patterns` together with
   `skd-custom-layout` or `print-form-patterns`.

End-to-end sequence:

```text
verbal description
  -> layout-from-description
  -> prototype and specification approval
  -> tabular-output-data-verification
  -> layout-from-excel (if structural transfer is needed)
  -> mxl-layout-patterns
  -> skd-report-patterns + skd-custom-layout OR print-form-patterns
```

## Mandatory Self-Correction

If an already-used Excel property without a portable MXL equivalent is found during generation or pre-export:

1. Immediately stop `layout-from-excel`, MXL generation, and any other dependent route.
2. First update the canonical Russian-language source of this skill: add or strengthen the prohibition on the property class and document the reason it is not portable. If the skill has language mirrors, synchronize them using the standard mechanism before continuing.
3. Replace the property in the prototype with a proven MXL equivalent from the whitelist, or remove it.
4. Update the `layout/border/style/data-binding` specification.
5. Regenerate the MXL-compatible prototype and repeat the full structural check.
6. Continue the migration only after a new `PASS`.

Never accept a known loss of an Excel-only property as an acceptable silent downgrade.
If an equivalent is not proven, the property remains prohibited.

## Correct and Incorrect

Correct:

```text
Cell: demonstration number 1234.50
Excel format code: #,##0.00
MXL: numeric parameter with ЧГ=3,0;ЧДЦ=2
```

Incorrect:

```text
Cell: =SUM(B2:B10)
Purpose: calculate the total inside the Excel prototype
```

A formula moves business logic into Excel and has no place in an MXL-compatible prototype.
The total must come from a verified data contract, and the prototype defines only its display.

## Acceptance Checklist

- [ ] The source of truth is the approved verbal description.
- [ ] The workbook is saved as `.xlsx` and contains exactly one visible sheet.
- [ ] Only properties from the closed whitelist are used.
- [ ] Each format code is included in the table of six allowed variants.
- [ ] Each property has a documented MXL equivalent.
- [ ] All prohibited classes have been checked and are absent.
- [ ] Demonstration values are clearly separated from runtime data-binding.
- [ ] A structure snapshot and property checklist are attached.
- [ ] If a non-portable property is found, self-correction and rechecking are performed.
- [ ] `tabular-output-data-verification` is completed before runtime output.

---
depends_on:
  - framework/skills/bsl-practices/layout-from-excel/SKILL.md
  - framework/skills/bsl-practices/mxl-layout-patterns/SKILL.md
  - framework/skills/bsl-practices/tabular-output-data-verification/SKILL.md
---
