---
name: mxl-layout-patterns
installable: true
description: "Designing and analyzing 1C MXL spreadsheet document layouts: areas, parameters, cells, merges, dimensions, borders, styles, and result verification. Use as a shared formatting layer for print forms and Data Composition System reports with their own layouts."
---

# MXL Spreadsheet Document Layouts

## Purpose

An MXL layout is a spreadsheet document template with:

- areas;
- cells;
- rows and columns;
- merges;
- parameters;
- widths and heights;
- borders;
- fills;
- fonts;
- alignment;
- number and date formats.

This skill covers the visual and structural model of a layout. It does not define who fills in the parameters:

- a print form fills in parameters using BSL code;
- a Data Composition System report with its own layout fills in parameters through Data Composition System expressions;
- another mechanism may use the same areas differently.

## Choosing the Reference Source

If the reference is an image, use `layout-from-image` first.

If the reference is an Excel file, use `layout-from-excel`: analyze Excel as a workbook structure or through a verified platform import into `ТабличныйДокумент`, not as an image. Treat imported MXL from Excel as a visual framework; design named areas, parameters, and application bindings separately.

If the reference is an existing MXL/Template.xml, first obtain its structure using MXL tools and compare areas/parameters/styles.

If the reference is text, turn it into an explicit `layout/border/style spec`.

## Unified Specification

Before implementing a layout, prepare a specification:

- `layout spec` — areas, bbox, grid, GAP, repeatability;
- `border spec` — line segments, color, thickness, presence/absence;
- `style spec` — fills, fonts, alignment, formats;
- `data binding spec` — which cells are parameters;
- `runtime proof plan` — how the result will be produced and verified.

## Functional and technical layers

Separate two different tasks.

The functional layer answers these questions:

- which sections the user sees;
- which part repeats;
- where the table header, row, total, and footer are;
- which values are data and which are static labels;
- which blocks should be printed conditionally.

The result of the functional layer is an area map:

```text
Заголовок -> один раз
ШапкаТаблицы -> один раз перед строками
Строка -> N раз
Итого -> один раз после строк
Подвал -> один раз
```

The technical layer answers these questions:

- how many columns are in the grid;
- what the widths and heights are;
- which cells are merged;
- which styles are applied;
- which parameters each area has;
- which number/date formats are needed;
- how the area will be obtained and output.

In `mxl-dsl`, the technical layer is usually expressed through:

- `columns`, `page`, `columnWidths`;
- `fonts`, `styles`;
- `areas[].name`;
- `rows`, `rowStyle`, `cells`;
- `text`, `param`, `template`, `detail`;
- `span`, `rowspan`, `format`.

MXL resembles an Excel sheet at the level of rows, columns, cells, merges, widths, heights, borders, fonts, alignment, and formats. But an MXL layout is usually not the final table: it stores template fragments that are output by code, СКД, or another mechanism.

If the source came from Excel via import into `ТабличныйДокумент`, do not automatically transfer trust in Excel-specific properties to MXL: freeze panes, auto filter, print area, exact column widths, formulas, and some formats must be considered unverified until round-trip proof shows otherwise.

## Areas

Typical areas:

- `Заголовок`;
- `Шапка`;
- `ШапкаТаблицы`;
- `Строка`;
- `Группа`;
- `Итого`;
- `Подвал`.

For a variable number of rows, do not draw a fixed set of rows. Create a repeating `Строка` area.

## Parameters

Keep static text as text. Make values that depend on data into parameters.

For each parameter cell, specify:

- parameter name;
- value format;
- value source;
- example value;
- alignment and style;
- allowed behavior when empty.

If the layout started from an imported Excel framework, separately specify which data will be:

- parameters of named ranges;
- written directly by coordinates;
- static text from the imported template.

Do not leave this transition "self-evident": the imported framework does not automatically create an application parameter model.

## Grid and Merges

Design the overall layout grid deliberately.

If different visual zones use different column divisions, find a common base grid and express the zones through merges. Otherwise, the resulting spreadsheet document may shift: row widths will align to the common grid rather than exist independently.

## Borders

Check borders separately from geometry.

For each block, describe:

- outer outline;
- inner dividers;
- table borders;
- missing lines;
- color and thickness;
- distinction between accent and auxiliary lines.

Missing lines require separate negative proof: the source layout/DSL must not contain a border parameter, and the runtime result must not have a visible segment. If a segment appears only at the runtime layer, check the target technology's capabilities: merging cells/rows, area type, post-processing the spreadsheet document, or another way to build the block.

### Outer Bottom Border of a Repeating Table

If the table's final outer bottom border should be drawn once, do not set it in the repeating row template: that border will be drawn beneath every row. Keep inner borders in the repeating area, and set the outer left, right, and final bottom borders in a one-time closing area (`GroupFooter`/end of table). If a separate closing area is not possible, only controlled post-processing of the generated spreadsheet document is allowed.

If the technology does not allow the required border to be expressed precisely, first prove this through the API/documentation/runtime test, then record the limitation.

## Checking MXL API capabilities

If the reference contains an element that looks like freeform graphics rather than a regular table cell
(for example, a rounded corner, shadow, nonstandard outline, decorative background), first check the
spreadsheet document platform API:

- `ОбластьЯчеекТабличногоДокумента` — cell, border, background, font, and picture properties;
- `Линия` — supported line types and thicknesses;
- `РисунокТабличногоДокумента` — picture, line, background, and placement capabilities;
- `ТабличныйДокумент` — picture collections, background image, and output methods.

If a cell/line/picture has no property for the required effect, do not approximate it through arbitrary
changes to the grid, thicknesses, or colors. Record one of the following decisions:

- standard implementation using a discovered API property;
- deliberate imitation using a picture/drawing, if acceptable for the task;
- a proven acceptable deviation, if the effect came from a sample image and is not supported by MXL.

## Result verification

Minimum proof after implementation:

1. Generate/build the layout with a supported tool.
2. Check the structure through `info/validate`, if the tool is available.
   If MCP `1c-form-viewer` is connected, after generating or editing the layout run `open_preview` (`audience="agent"`) and `capture_preview`. This is a quick visual assessment without a build. The server is read-only and does not replace the runtime proof in step 3: `xml-generation/references/form-viewer-mcp.md`.
3. Obtain the runtime `ТабличныйДокумент`, MXL/XLSX/PDF, or screenshot.
4. Check:
   - layout proof;
   - border proof;
   - style proof;
   - negative checks.
5. In the report, keep proven matches separate from remaining differences.

If supported generation of MXL or the MXL layer of a custom СКД layout creates noncanonical XML
that is lost during round-trip, fails to open, or otherwise diverges from the platform, stop the
dependent workflow and apply `xml-generation` §4.1. A local manual XML edit is not a fix: a separate
bug report, reproduction, regression test, full tool build, and live check of the newly generated
artifact are required.

## Tools

For MXL/XML, use specialized tools:

```bash
xml-gen mxl info <Template.xml> --with-text --limit 200
xml-gen mxl compile layout.json Template.xml
xml-gen validate --type mxl Template.xml
```

For an MXL layer in an SKD layout, use `skd-custom-layout`; technical generation is done through `skd-dsl`.

## Related skills

- `layout-from-image` — if the reference is an image.
- `layout-from-excel` — if the reference is Excel.
- `skd-custom-layout` — if SKD fills the MXL areas.
- `print-form-patterns` — if BSL code for the print form fills the MXL areas.
- `mxl-dsl` — a technical tool for generating MXL.

---
depends_on:
  - layout-from-image
  - layout-from-excel
---
