---
name: mxl-dsl
description: "xml-gen MXL print forms: compile/edit/info"
---

# MXL DSL

A compact JSON format for describing 1C tabular documents (SpreadsheetDocument). Claude describes **what** (areas, cells, styles, parameters), while the CLI ensures **correct** XML (palettes, indexes, merges, namespace).

The canon comes from Shirokov's specification (cc-1c-skills) and is extended with the `--format designer|edt` flag for two output formats.

## When to use

| Trigger | Action |
|---------|----------|
| Create a print form from scratch | `mxl compile` + JSON DSL → `references/dsl-spec.md` |
| Improve an existing layout | `mxl decompile` → edit JSON → `mxl compile` |
| Understand the structure of someone else's layout (areas, parameters, drilldowns) | `mxl info` → `references/info-modes.md` |
| Check the correctness of a built Template.xml | `xml-gen validate --type mxl` → `references/validate-classes.md` |
| Reverse-engineer printing from an example (screenshot/scan) | `mxl decompile` or build from scratch using the grid — set `page` + `"Nx"` widths |

## Intentionally outside the DSL - do it in code

The DSL covers **static** cell formatting - `font/align/valign/border/wrap/format` through the `styles` map. It intentionally does NOT generate **runtime-conditional** formatting: coloring/styling a cell depending on the rendered value. This is done programmatically when filling the tabular document - `Область.ТекстЦвет = …`, `Область.ЦветФона = …` on the filled area. Its absence is a **design choice**, not a tool defect; see rule `no-manual-xml-edit.md` § "What is done in code, and NOT through xml-gen".

## Commands

```bash
# Compile JSON → Template.xml
xml-gen mxl compile [--format designer|edt] <input.json> <output.xml>

# Decompile Template.xml → JSON DSL
xml-gen mxl decompile <Template.xml> <output.json>

# Structure analysis (areas, parameters, drilldowns, text)
xml-gen mxl info <Template.xml> [--with-text] [--limit N] [--offset N] [--format text|json]

# Validation
xml-gen validate --type mxl <Template.xml> [--detailed] [--max-errors N]
```

**`output.xml`** for compile is the path to the layout in EPF/ERF: `.../Templates/<Name>/Ext/Template.xml`.

**`--format`** (compile only):
- `designer` — configuration designer format (Template.xml in `Ext/`)
- `edt` — EDT format (XML inside the `.mxl` folder of the EDT project)

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
| `{"text": "[Parameter]"}` — parameter in square brackets in text | `{"param": "Parameter"}` — a separate cell type |
| `{"text": "Inv. No. [Number]"}` — parameter in the template through square brackets | `{"template": "Inv. No. [Number]"}` — a separate type |
| `span` without `col` (sequential filling) | `col` 1-based + `span` (explicit positioning) |
| Column widths were not specified | `columns` + `columnWidths` + `page` + `"Nx"` |
| Solid row borders - each cell explicitly | `rowStyle` - automatic filling of gaps |
| Only horizontal merging | `rowspan` (vertical) + rowStyle takes occupied cells into account |
| Drilldown was not described | `detail` — a drilldown parameter next to `param` |
| Row height was not specified | `height` on the row |
| N empty rows - N `{}` objects | `{ "empty": N }` |

The full field specification (top level, fonts, styles, areas, rows, cells, fillType detection, rowStyle with rowspan, `"Nx"` proportions, 1C formats `ЧДЦ=`/`ДФ=`) — **`references/dsl-spec.md`**.

## Using areas in BSL

Area names from the DSL (`name`) and parameter names (`param`) are what BSL uses to access the layout:

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

## Decompile - what is important to know

- Fonts and styles get **automatic meaningful names** (`default`, `bold`, `header`, `bordered`, `bordered-right`, `bold-right`, `border-top`, etc.) based on combinations of properties - they do not have to match the original ones.
- If all empty cells in a row have the same style, it is collapsed into `rowStyle`, and empty cells are removed from the output.
- Template parameters (`[Name]` in text) are extracted into separate `template` cells.

## Workflow (typical)

1. (optional) If the layout is created from an image - overlay a grid, determine column proportions → set `page: "A4-landscape"` + `"Nx"` widths.
2. Write JSON (`Write`).
3. `mxl compile` → Template.xml.
4. `xml-gen validate --type mxl` → if there are errors, see `references/validate-classes.md`.
5. `mxl info` → check the structure of areas and parameters visually as an agent.
6. (when improving someone else's layout) `mxl decompile` → edit → compile.

## Correct / Incorrect

```json
// ❌ статический текст и параметр свалены в одну ячейку через скобки
{ "col": 2, "text": "Инв № [Номер]" }

// ✅ это шаблон с подстановкой → template
{ "col": 2, "template": "Инв № [Номер]" }
```

```json
// ❌ сплошные рамки строки — каждая пустая ячейка прописана вручную
{ "cells": [
  { "col": 1, "style": "bordered", "param": "А" },
  { "col": 2, "style": "bordered" },
  { "col": 3, "style": "bordered" },
  { "col": 4, "style": "bordered", "param": "Б" }
]}

// ✅ rowStyle автозаполняет пустоты
{ "rowStyle": "bordered", "cells": [
  { "col": 1, "param": "А" },
  { "col": 4, "param": "Б" }
]}
```

## Links

- `references/dsl-spec.md` — full DSL field specification
- `references/info-modes.md` — how to read `mxl info` output (area types, intersections, `[tpl]` parameters, detail)
- `references/validate-classes.md` — validator error classes
- Neighboring DSLs: `../role-dsl/`, `../form-dsl/`

---
depends_on: []
metadata:
  category: 1c-development
  version: "2.0"
---
