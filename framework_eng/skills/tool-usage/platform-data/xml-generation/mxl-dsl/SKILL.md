---
name: mxl-dsl
description: "JSON DSL for generating 1С tabular documents (MXL) - print forms. Rich canon: page/columns/rowStyle/rowspan/empty/detail/template/format. Use for xml-gen mxl compile/decompile/info and xml-gen validate --type mxl for print forms."
---

# MXL DSL

Compact JSON format for describing 1С tabular documents (SpreadsheetDocument). Claude describes **what** (areas, cells, styles, parameters), while the CLI ensures XML **correctness** (palettes, indices, merges, namespace).

The canon is taken from Shirokov's specification (cc-1c-skills) and extended with the `--format designer|edt` flag for two output formats.

## When to use

| Trigger | Action |
|---------|----------|
| Create a print form from scratch | `mxl compile` + JSON DSL → `references/dsl-spec.md` |
| Refine an existing template | `mxl decompile` → edit JSON → `mxl compile` |
| Understand the structure of someone else's template (areas, parameters, drilldowns) | `mxl info` → `references/info-modes.md` |
| Check the correctness of the assembled Template.xml | `xml-gen validate --type mxl` → `references/validate-classes.md` |
| Reverse-engineer printing from a sample (screenshot/scan) | `mxl decompile` or build from scratch against the grid — set `page` + `"Nx"` widths |

## Commands

```bash
# Компиляция JSON → Template.xml
xml-gen mxl compile [--format designer|edt] <input.json> <output.xml>

# Декомпиляция Template.xml → JSON DSL
xml-gen mxl decompile <Template.xml> <output.json>

# Анализ структуры (области, параметры, расшифровки, текст)
xml-gen mxl info <Template.xml> [--with-text] [--limit N] [--offset N] [--format text|json]

# Валидация
xml-gen validate --type mxl <Template.xml> [--detailed] [--max-errors N]
```

**`output.xml`** for compile is the path to the template in EPF/ERF: `.../Templates/<Name>/Ext/Template.xml`.

**`--format`** (compile only):
- `designer` — the configurator format (Template.xml in `Ext/`)
- `edt` — the EDT format (XML inside the `.mxl` folder of an EDT project)

## Minimal DSL

```json
{
  "columns": 4,
  "areas": [
    {
      "name": "Заголовок",
      "rows": [
        { "cells": [
          { "col": 1, "span": 4, "text": "Накладная" }
        ]}
      ]
    }
  ]
}
```

## Key differences from the old inline format

| Was (old) | Became (canon) |
|--------------|---------------|
| `{"text": "[Parameter]"}` — parameter through brackets in the text | `{"param": "Parameter"}` — separate cell type |
| `{"text": "Inv. No. [Number]"}` — parameter in the template through brackets | `{"template": "Inv. No. [Number]"}` — separate type |
| `span` without `col` (sequential filling) | `col` 1-based + `span` (explicit positioning) |
| Column widths were not set | `columns` + `columnWidths` + `page` + `"Nx"` |
| Solid row borders — every cell explicitly | `rowStyle` — auto-filling empty spaces |
| Only horizontal merging | `rowspan` (vertical) + rowStyle accounts for occupied cells |
| Drilldown was not described | `detail` — drilldown parameter next to `param` |
| Row height was not set | `height` on the row |
| N empty rows — N objects `{}` | `{ "empty": N }` |

The full specification of fields (top level, fonts, styles, areas, rows, cells, fillType detection, rowStyle with rowspan, `"Nx"` proportions, 1С `ЧДЦ=`/`ДФ=` formats) — **`references/dsl-spec.md`**.

## Using Areas in BSL

Area names from the DSL (`name`) and parameter names (`param`) are what BSL uses to access the template:

```bsl
ТД = ЭтотОбъект.ПолучитьМакет("ПечатнаяФорма");
ТабДок = Новый ТабличныйДокумент;

ОбластьШапка = ТД.ПолучитьОбласть("Заголовок");
ОбластьШапка.Параметры.ТекстЗаголовка = "Накладная № 1";
ТабДок.Вывести(ОбластьШапка);

Для Каждого Стр Из ТЧ Цикл
    Строка = ТД.ПолучитьОбласть("Строка");
    Строка.Параметры.Товар       = Стр.Товар;        // detail = Номенклатура подставит ссылку для расшифровки
    Строка.Параметры.Количество  = Стр.Количество;
    Строка.Параметры.Сумма       = Стр.Сумма;
    ТабДок.Вывести(Строка);
КонецЦикла;
```

For **intersections** (Rows area + Columns area, for example labels/price tags), use `|`:

```bsl
Область = ТД.ПолучитьОбласть("ВысотаЭтикетки|ШиринаЭтикетки");
```

## Decompile — what is important to know

- Fonts and styles receive **automatic meaningful names** (`default`, `bold`, `header`, `bordered`, `bordered-right`, `bold-right`, `border-top`, etc.) based on property combinations — they do not have to match the original.
- If all empty cells in a row have the same style, it is collapsed into `rowStyle`, and empty cells are removed from the output.
- Template parameters (`[Name]` in text) are split out into separate `template` cells.

## Workflow (typical)

1. (optional) If the template is being created from an image — overlay a grid, determine column proportions → set `page: "A4-landscape"` + `"Nx"` widths.
2. Write the JSON (`Write`).
3. `mxl compile` → Template.xml.
4. `xml-gen validate --type mxl` → if there are errors, see `references/validate-classes.md`.
5. `mxl info` → inspect the structure of areas and parameters visually as the agent sees it.
6. (for adapting someone else's template) `mxl decompile` → edit → compile.

## Correct / Incorrect

```json
// ❌ static text and parameter are crammed into one cell through brackets
{ "col": 2, "text": "Инв № [Номер]" }

// ✅ this is a template with substitution → template
{ "col": 2, "template": "Инв № [Номер]" }
```

```json
// ❌ solid row borders — every empty cell is written manually
{ "cells": [
  { "col": 1, "style": "bordered", "param": "А" },
  { "col": 2, "style": "bordered" },
  { "col": 3, "style": "bordered" },
  { "col": 4, "style": "bordered", "param": "Б" }
]}

// ✅ rowStyle auto-fills empty spaces
{ "rowStyle": "bordered", "cells": [
  { "col": 1, "param": "А" },
  { "col": 4, "param": "Б" }
]}
```

## Links

- `references/dsl-spec.md` — full DSL field specification
- `references/info-modes.md` — how to read `mxl info` output (area types, intersections, `[tpl]` parameters, detail)
- `references/validate-classes.md` — validator error classes
- Adjacent DSLs: `../role-dsl/`, `../form-dsl/`

---
depends_on: []
metadata:
  category: 1c-development
  version: "2.0"
---
