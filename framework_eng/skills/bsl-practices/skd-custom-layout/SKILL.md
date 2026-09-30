---
name: skd-custom-layout
installable: true
description: "SKD reports on custom result layout areas: mapping SKD settings to template/groupTemplate, area parameters, expressions, runtime validation of the standard report form. Use mxl-layout-patterns to design the areas."
---

# SKD on a Custom Layout

## Purpose

Use this skill when a report is generated through SKD, but the result's appearance is defined by custom areas on the `Макеты` tab.

This skill describes the SKD linkage:

```text
data sets and fields
  -> settings and variant structure
  -> groupTemplate
  -> template
  -> cell parameter
  -> parameter expression
  -> ТабличныйДокумент
```

Design the layout of the areas themselves, the grid, borders, fills, fonts, and visual verification through `mxl-layout-patterns`.

Check the core SKD capabilities through `skd-report-patterns`.

## Do Not Confuse with a Print Form

A print form fills an area with BSL code:

```bsl
Область = Макет.ПолучитьОбласть("Строка");
Область.Параметры.Поле = Значение;
ТабличныйДокумент.Вывести(Область);
```

SKD on a custom layout uses a similar visual area model, but SKD calculates and outputs the values:

- the cell references an area parameter;
- the parameter has an SKD expression;
- `groupTemplate` decides when the area is output;
- variant settings decide the data structure.

## Main Entities

- `template.name` — technical name of the area.
- `groupTemplate.groupName` — name of the settings structure element.
- `groupTemplate.templateType` — where the area is output: `Header`, `GroupHeader`, `OverallHeader`, etc.
- `groupTemplate.template` — reference to `template.name`.
- `ExpressionAreaTemplateParameter.name` — name of the area parameter.
- `ExpressionAreaTemplateParameter.expression` — expression that SKD uses to fill the parameter.

Do not derive the value from the parameter name. The source of truth is `expression`.

## Workflow

1. Design the data and structure of the report through `skd-report-patterns`.
2. Design the areas and visual model through `mxl-layout-patterns`.
3. For each area, define:
   - when it is output;
   - which structural element it is bound to;
   - which parameters it contains;
   - which expressions populate the parameters.
4. Generate/modify the СКД with a supported tool.
5. Check the XML structure through `xml-gen skd info`.
6. Perform a runtime check on the platform.

## Layers

Separate four layers.

The functional layer describes which report the user needs:

- which data must be obtained by the query;
- which data needs to be formed by code or external object datasets;
- which sections, groups, rows, and totals must be visible;
- which filters and sorts are available to the user;
- where the standard СКД output is not enough and a custom layout is needed.

The technical layer of СКД describes how this is stored and executed:

- `dataSets` — query/object/union datasets;
- `fields` — dataset fields;
- `totalFields` — resources;
- `parameters` — data parameters;
- `settingsVariants.settings.structure` — output order, groupings, filters, sorts, selected fields;
- `templates` — result layout areas;
- `groupTemplates` — mapping of the variant structure to layout areas.

The technical layer of MXL describes the visual form of the areas:

- the column grid;
- widths and heights;
- merges;
- styles;
- text cells;
- parameter cells;
- number and date formats.

For this layer, use `mxl-layout-patterns`; for generating ordinary MXL - `mxl-dsl`; for СКД areas - the capabilities of `skd-dsl.templates`.

The runtime layer describes what happens when the report is executed:

- obtaining the executed settings;
- building the layout of the composition;
- preparing external object datasets;
- initializing the composition processor;
- outputting the result to `ТабличныйДокумент`;
- permissible post-processing of the result.

An external imported MXL/Excel scaffold does not by itself become an СКД layout. To make Excel or ordinary
MXL a source for this skill, you need to explicitly restore the СКД structure:

- `template`;
- `groupTemplate`;
- area parameters;
- `expression` for parameters;
- linkage to the elements of the variant structure.

## What to check

- All cell parameters have `ExpressionAreaTemplateParameter`.
- All expressions reference available fields/resources/expressions.
- The fields used by the row area are available in the context of the group that outputs this area.
- `groupTemplate.groupName` matches the name of the settings structure element, not the name of the grouping field.
- `templateType` matches the expected output location.
- For DSL semantics `GroupHeader`, the canonical Designer XML contains a separate
  `<groupHeaderTemplate>` with nested `<templateType>Header</templateType>`. The ordinary DSL `Header`
  must remain `<groupTemplate>` with `<templateType>Header</templateType>`. You must not substitute
  `GroupHeader` with `Header` in the input DSL or manually rearrange nodes in the generated XML:
  this changes layout semantics and hides the generator defect.
- For `GroupHeader`, check both branches: targeted regression for `GroupHeader` and control `Header`,
  then `skd compile`, `validate --type skd`, the applicable container validation and Designer package/load
  oracle. `validate` without the Designer oracle is insufficient for serialization-sensitive structure.
- If the DSL declares area formatting, verify that the final XML actually contains the corresponding `dcsat:appearance` and that the runtime result shows that formatting.
- For area borders, distinguish the common parameter `СтильГраницы` from the side-specific parameters
  `СтильГраницы.Слева/Справа/Сверху/Снизу`. The canonical structure for side borders is:
  a common `dcscor:item` with the `СтильГраницы` parameter and a base line of `None`, and the side-specific
  `dcscor:item` entries must be nested inside it. Do not place `СтильГраницы.Слева/Справа/...`
  as adjacent elements at the `dcsat:appearance` level: such a structure may pass
  XML validation and loading, but be lost during round-trip through Configurator or produce
  incorrect runtime rendering. If the generator writes side-specific borders as not nested,
  fix the generator/tests, not the layout.
- For complex borders in a SKD layout, do a round-trip proof: generate XML, build the ERF/report,
  export it back through the platform's standard export, and verify that `СтильГраницы.*`
  were not lost and remained nested under `СтильГраницы`.
- An extra inner line in a SKD layout is checked as a negative proof: the absence of `СтильГраницы.Снизу` on the
  upper cell and `СтильГраницы.Сверху` on the lower cell in XML is not sufficient proof
  until the runtime tabular document has shown the absence of the segment. If the runtime draws a line without an explicit
  parameter, check an alternative area structure: merging cells/rows, one parameter instead of two
  rows, another output method for the block, or a platform limitation on mixed formatting inside a SKD cell.
- If explicit `СтильГраницы.Снизу=None` / `СтильГраницы.Сверху=None` also do not remove the separator between
  rows, do not continue tweaking colors/thicknesses. Check the structural variant: one `tableCell`
  with multiple `dcsat:item`, one multi-line parameter, another way to output the block, or a platform
  limitation on mixed formatting inside a SKD cell.
- Do not treat a successful `xml-gen validate` or Designer-pack as proof of the visual layer: they may confirm the XML structure, but not the rendering of colors, fonts, widths, merges, and borders.
- If any defect in supported serialization appears, stop work on the dependent report and execute
  the `xml-generation` §4.1 process: separate defect registration, reproduction, red/green,
  full tool build, regeneration, and live-check. Locally fixed XML is not a valid proof and does not
  allow the flow to continue.
- Do not declare imported Excel/MXL "a finished custom SKD layout" until the entire chain
  `groupTemplate -> template -> parameter -> expression -> runtime ТабличныйДокумент` is proven.
- The report opens in the standard report form if it does not have its own form.
- The generate button builds the actual `ТабличныйДокумент`.
- The result is saved or captured as evidence: MXL/XLSX/PDF/PNG.

## Runtime

For an external report with SKD, the usual user verification flow is:

1. Open the `.erf` through `File -> Open`.
2. Get the standard report form.
3. Click the standard generate button.
4. Save or capture the result.

Do not replace this flow with registering the external report, launching the processing through `/Execute`, or a script if the task is specifically verifying the user opening of the ERF.

## Related skills

- `skd-report-patterns` — data, settings, and SKD structure.
- `mxl-layout-patterns` — visual model of areas.
- `skd-dsl` / `skd-edit` — technical XML generation and editing.

---
depends_on:
  - skd-report-patterns
  - mxl-layout-patterns
---
