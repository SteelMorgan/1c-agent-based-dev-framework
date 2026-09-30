---
name: layout-from-excel
installable: true
description: "Transfer a layout from Excel to 1C through structural analysis or platform import into SpreadsheetDocument: what is preserved, what is lost, and how to use imported MXL honestly as a visual framework."
---

# Layout from Excel

## Purpose

Use this skill when the layout source is an Excel file (`.xlsx`, `.xls`) and you need to understand
how to transfer it honestly into the world of `SpreadsheetDocument`/MXL.

An Excel source should not be processed as an image by default. Unlike PNG/JPG, Excel already
contains structural data:

- sheets;
- cells;
- merged cells;
- column widths;
- row heights;
- borders;
- fills;
- fonts;
- number formats;
- alignment;
- print areas.

The basic strategy should be programmatic, not visual.

## Skill scope

This skill handles converting an Excel source into a verifiable spreadsheet document framework.
It does not mean automatically obtaining:

- a ready-made DCS template with `template`, `groupTemplate`, and `ExpressionAreaTemplateParameter`;
- a ready-made print form with named areas `ПолучитьОбласть()`;
- full round-trip preservation of all Excel-specific properties.

The result of this skill is usually one of two things:

1. Either an Excel structural specification for subsequent manual implementation.
2. Or an imported MXL/`SpreadsheetDocument` used as a visual framework for
   further application-level population.

## Two working paths

### 1. Structural workbook analysis

Use when you need to:

- understand the logic of sheets and ranges;
- reconstruct named zones, repeating blocks, and merged ranges;
- handle formulas, print areas, filters, and freeze panes separately;
- decide in advance what will be translated into parameters/areas and what will remain static.

This is done with a tool that reads the workbook as a structure, not as an image —
for example, `openpyxl`. A ready-made script for comparing “source vs round-trip result”:
`references/compare_xlsx_fidelity.py` (sheets, grid, merges, values, widths, heights,
number formats, formulas, `freeze panes`, `auto filter`, `print area`).

### 2. Platform Import `Excel -> ТабличныйДокумент`

The platform can read Excel directly:

```bsl
ТабДок = Новый ТабличныйДокумент;
ТабДок.Прочитать("template.xlsx");
ТабДок.Записать("result.mxl");
```

Treat this route as evidence of behavior narrower than “full Excel transfer”:

- Excel can be opened in `ТабличныйДокумент`;
- the result can be saved in `MXL/XLSX`;
- the imported document can be used as the basis for the next step.

In practice, this import often requires the thick client. If the method or route is unavailable in the thin client, document this limitation explicitly in the proof and account for it when choosing the runtime route.

Ready-made generalized procedures for this route:

- `references/platform-import-roundtrip.bsl` — import with `Прочитать()`, save as `MXL/XLSX`,
  restore column widths, row heights, and numeric formats in code;
- `references/imported-scaffold-fill.bsl` — fill the imported scaffold with data by
  coordinates, platform recalculation of totals, print form mode;
- `references/platform-import-fidelity.md` — measured “what is preserved / what is lost” matrix
  with actual run numbers.

### 3. Generating `Template.xml` with the `1c-form-viewer` MCP tool

If the `1c-form-viewer` (BslEdit) MCP server is connected in the environment, the print form layout
is built from `.xlsx` without launching the platform:

`convert_xlsx_to_template(xlsx_path, output_path, sheet?, overwrite?)` — writes
`<Объект>/Templates/<Макет>/Ext/Template.xml` and opens a preview immediately.

- Transfers text, fonts, colors, fill, borders, alignment, widths and heights, merged cells,
  number formats, headers and footers, page settings, print area, images.
- A named Excel range becomes an area, a cell containing exactly `[Имя]` becomes a parameter,
  text with `[Имя]` inside becomes a template. Prepare the workbook for this convention in advance.
- Does not transfer: formulas (values only), shapes, charts, conditional formatting, repeating
  print rows — the response summary lists losses, which are included in the proof.

This is a generation tool (like `xml-gen` for a single operation), not manual XML editing: the ban
on manual editing does not apply to it. Procedure:

1. The `Template` metadata object itself (the layout description in the owner object's XML) is created normally
   through `xml-gen`; `convert_xlsx_to_template` writes only the `Ext/Template.xml` body.
2. Check the result: `capture_preview` and `list_markup` from the same server — areas and parameters.
3. The strongest check is loading the configuration/extension into the infobase (Designer build), not just
   the preview.
4. A generation defect is recorded in the project's tools defect registry, as with `xml-gen`.

## What Is Usually Preserved by Platform Import

Check with an actual file, but as a baseline expectation, an imported document usually preserves:

- merge areas;
- the basic row and column grid;
- many fill colors;
- fonts and styles;
- alignment;
- borders;
- the overall visual geometry of the header, table, and totals blocks;
- already calculated displayed values.

This makes imported MXL a good candidate for use as a visual framework.

## What Cannot Be Assumed to Be Preserved

Do not promise an Excel round-trip without checking. Losses and conversions are typical for the platform route:

- the sheet name may change;
- `freeze panes` may be lost;
- `auto filter` may be lost;
- `print area` may be lost;
- exact column widths may change;
- formulas may be converted to their current values;
- some number formats may be simplified;
- Excel-specific features that are not ordinary cell geometry may not survive import.

Therefore, interpret imported MXL as a **visual-structural framework**, not as an exact copy of
all Excel features.

## How to Use Imported MXL Honestly

If an imported document became the basis for a report or print form, distinguish two layers:

1. What the import itself transferred:
   - the grid;
   - merged cells;
   - some styles;
   - the overall geometry.
2. What had to be built on top of it with application code:
   - binding runtime data to coordinates;
   - recalculating totals;
   - replacing demo data with real data;
   - service headers and captions;
   - named areas/parameters, if needed by the application mechanism.

Imported MXL can be used:

- as a framework for a custom report with values written to cells manually;
- as a framework for a print form;
- as an intermediate source for manually reconstructing a proper MXL/СКД layout.

But imported MXL does not by itself become a declarative СКД layout and does not automatically create
named areas for an application print form.

## Workflow

1. Record the workbook as the structural source of truth: sheets, ranges, merged areas, important
   styles, formulas, and Excel-specific functions.
2. Decide whether you need structural analysis of the workbook, platform import, or generation of `Template.xml`
   with the `convert_xlsx_to_template` tool (route 3 is preferred for a spreadsheet layout in
   configuration source files).
3. If platform import is selected, obtain the runtime `ТабличныйДокумент` and save it at least in
   `MXL` and `XLSX` formats.
4. Compare what was preserved and what was lost:
   - merges;
   - dimensions;
   - borders;
   - styles;
   - number formats;
   - formulas;
   - print/freeze/filter properties.
5. Determine the next implementation layer:
   - `mxl-layout-patterns`, if the imported result will become the visual scaffold;
   - `print-form-patterns`, if a printed form will be built on top of the scaffold;
   - `skd-custom-layout`, if a real СКД layout needs to be designed based on Excel, rather than
     writing by coordinates.
6. In the proof, explicitly separate:
   - what was proven by the import;
   - what was implemented manually on top of the imported scaffold;
   - which Excel properties were not preserved.

## Reference materials

| File | What it provides | How to use it |
|---|---|---|
| `references/platform-import-roundtrip.bsl` | platform import, saving as `MXL/XLSX`, restoring geometry and formats | move the procedures to the processing module, pass paths as parameters |
| `references/imported-scaffold-fill.bsl` | writing data/totals to the scaffold by coordinates, print mode | call after `Прочитать()`, when the scaffold becomes a report or printed form |
| `references/platform-import-fidelity.md` | measured matrix of property preservation | read before promising “what will survive the import” |
| `references/compare_xlsx_fidelity.py` | structural comparison of two `.xlsx` files by fidelity categories | `python3 compare_xlsx_fidelity.py source.xlsx result.xlsx [--json out.json]`; `openpyxl` dependency; exit code `1` if there are losses |

## What Not to Do

- Do not replace the Excel source with a screenshot by default and work only through `layout-from-image`.
- Do not declare an imported `ТабличныйДокумент` a complete СКД layout without explicit `template`,
  `groupTemplate`, and parameters.
- Do not declare an imported document a finished print form just because it looks like one.
- Do not treat a successful `Прочитать()` as proof that widths, filters, the print area, and formulas were fully transferred.
- Do not combine “transferred by import” and “added in code” in a single verdict.

## Related Skills

- `mxl-layout-patterns` — visual layer of the imported scaffold and layout/border/style checks.
- `print-form-patterns` — if the imported scaffold is used as a print form.
- `skd-custom-layout` — if Excel serves as a reference for a future СКД layout, rather than as the final runtime scaffold.
- `layout-from-image` — only as a fallback if the source of truth is no longer the workbook, but its screenshot/render.

---
depends_on: []
---
