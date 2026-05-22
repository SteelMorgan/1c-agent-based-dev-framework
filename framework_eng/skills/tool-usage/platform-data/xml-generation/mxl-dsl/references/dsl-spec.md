# MXL DSL — complete specification

Canonical format for describing a 1C tabular document. Used by `xml-gen mxl compile/decompile`.

## Top Level

| Field | Req. | Default | Description |
|------|:-----:|-----------|----------|
| `columns` | yes | — | Number of layout columns |
| `page` | no | — | Page format. Values: `"A4-landscape"` (780), `"A4-portrait"` (540), or a number. If set and all columns use `"Nx"`, it **automatically calculates `defaultWidth`** from the sum of proportions so that the total width fits within `page` |
| `defaultWidth` | no | `10` | Default column width. Ignored if `page` is set and all widths are `"Nx"` |
| `columnWidths` | no | `{}` | Map of column widths. See below. |
| `fonts` | no | — | Named fonts. If not set, `default` (Arial 10) is created |
| `styles` | no | `{}` | Named styles |
| `areas` | yes | — | Array of named areas in output order |

### `columnWidths` — keys and values

**Keys** (1-based):
- single index: `"1"`, `"5"`
- range: `"2-8"` — columns 2..8 inclusive
- comma-separated list: `"5,7,9"` — specified columns
- combinations: `"2-8,12"` are allowed

**Values**:
- number — absolute width in 1C units
- string `"Nx"` — multiplier relative to `defaultWidth` (for example `"2x"`, `"0.5x"`, `"1.5x"`). Used together with `page` for proportional scaling

Example:

```json
"columnWidths": {
  "1":     5,        // absolute width of the first column
  "2-8":   "1x",     // 7 columns at defaultWidth
  "9-10":  "2x",     // 2 columns with double width
  "11,13": 30        // specific odd columns
}
```

## Fonts — `fonts.<name>`

| Field | Default | Description |
|------|-----------|----------|
| `face` | `"Arial"` | Font family |
| `size` | `10` | Point size |
| `bold` | `false` | Bold |
| `italic` | `false` | Italic |
| `underline` | `false` | Underlined |
| `strikeout` | `false` | Strikethrough |

The font named `"default"` is used if a style does not explicitly set `font`. If absent from the DSL, it is created automatically (Arial 10).

## Styles — `styles.<name>`

| Field | Default | Description |
|------|-----------|----------|
| `font` | `"default"` | Reference to a font name from `fonts` |
| `align` | — | Horizontal alignment: `left`, `center`, `right` |
| `valign` | — | Vertical alignment: `top`, `center`, `bottom` |
| `border` | — | Border sides. Values: `all`, `none`, `top`, `bottom`, `left`, `right`. Comma-separated: `"top,bottom"`, `"left,right,bottom"` |
| `borderWidth` | `"thin"` | Border thickness: `thin` (1px) or `thick` (2px). Set **at the style level**, not on the cell |
| `wrap` | `false` | Wrap long text by words |
| `format` | — | 1C data format. Examples: `"ЧЦ=15; ЧДЦ=2"`, `"ДФ=dd.MM.yyyy"`, `"ЧРГ=,"`, `"Л=ru_RU"` |

## Areas — `areas[]`

| Field | Req. | Description |
|------|:-----:|----------|
| `name` | yes | Area name (used by `Макет.ПолучитьОбласть("Имя")`) |
| `rows` | yes | Array of rows |

The order of areas in the array = the order of rows in the resulting XML. Decompile preserves the order by position in the document (not alphabetically).

## Rows — `rows[]`

| Field | Default | Description |
|------|-----------|----------|
| `height` | — | Row height. If not set, auto |
| `rowStyle` | — | Name of the style applied to **all** columns in the row (see below) |
| `cells` | `[]` | Array of explicit cells |
| `empty` | — | Number of consecutive **empty rows**. `{ "empty": 3 }` ≡ `{}, {}, {}` |

A row without `cells` and `rowStyle` is empty.

## Cells — `cells[]`

| Field | Req. | Default | Description |
|------|:-----:|-----------|----------|
| `col` | yes | — | Column position (1-based) |
| `span` | no | `1` | Horizontal merge (number of columns) |
| `rowspan` | no | `1` | Vertical merge (number of rows) |
| `style` | no | row's `rowStyle` | Style name. Overrides `rowStyle` |
| `text` | no | — | Static text (fillType=Text) |
| `param` | no | — | Fill parameter name (fillType=Parameter) |
| `template` | no | — | Template text with substitutions `[Parameter]` (fillType=Template) |
| `detail` | no | — | Detail reference parameter. Use **together** with `param`. When clicked, opens the object specified by this parameter |

### Auto-detection of `fillType`

The compiler chooses the cell fill type based on the presence of keys:

| Key in cell | fillType |
|---------------|----------|
| `param` | `Parameter` |
| `template` | `Template` |
| `text` | `Text` |
| nothing (only `style`/`col`/`span`) | without fillType — empty cell/border |

**Do not mix**: one cell, one fill type. If you need text and a parameter in one cell (`"Inv No. [Number]"`) — that is `template`, not `text + param`.

### `detail` — drill-down reference

```json
{ "col": 2, "span": 6, "param": "Товар", "detail": "Номенклатура" }
```

The cell displays `Product`, but on double-click in view mode the user drills down into the `Nomenclature` object. In BSL, **both** parameters are filled:

```bsl
Область.Параметры.Товар        = Строка.НоменклатураПредставление;
Область.Параметры.Номенклатура = Строка.Номенклатура; // ссылка для расшифровки
```

## `rowStyle` — row auto-fill

When a row has `rowStyle` set, the compiler:

1. Creates explicit cells in all columns from 1 to `columns`.
2. Columns that have an **explicit cell** in `cells` use their own `style` (or inherit `rowStyle` if `style` is not set).
3. Columns without an explicit cell receive an empty cell with `rowStyle` — this gives continuous borders.
4. If there are cells with `rowspan > 1` in **previous rows** of the same area, the columns occupied by them are skipped (not overwritten by fill).

Used for:
- table rows with continuous borders (only some columns contain data)
- table headers
- total rows with a top border

## `rowspan` — vertical merge

```json
[
  { "rowStyle": "bordered", "cells": [
    { "col": 1, "rowspan": 3, "param": "НомерЛота" },
    { "col": 2, "text": "Стр1" }
  ]},
  { "rowStyle": "bordered", "cells": [
    { "col": 2, "text": "Стр2" }
  ]},
  { "rowStyle": "bordered", "cells": [
    { "col": 2, "text": "Стр3" }
  ]}
]
```

In rows 2-3, column 1 is already occupied by rowspan — `rowStyle` will skip it during auto-fill.

## 1C formats (`format` field)

Most common:

| Format | Purpose |
|--------|------------|
| `"ЧДЦ=2"` | Numbers: 2 digits after the decimal separator |
| `"ЧЦ=15; ЧДЦ=2"` | Numbers: 15 digits total, 2 after the decimal separator |
| `"ЧРГ=,"` | Digit group separator - comma |
| `"ДФ=dd.MM.yyyy"` | Date: DD.MM.YYYY |
| `"ДФ=dd.MM.yyyy HH:mm"` | Date + time |
| `"Л=ru_RU"` | Locale |

Multiple tokens are separated by `;`.

## Current version limitations

The specification does **not** cover:
- Multiple column sets (`columnsID`) for Rows×Columns intersections - for **creation**. Reading via `mxl info` is supported.
- Drawing areas (drawings/barcodes/images) - compilation is not implemented, info is yes.
- Cell background (`BackColor`).

If you need these capabilities, build the layout in the configurator, decompile it, and then extend it.

## Full Example

```json
{
  "columns": 10,
  "page": "A4-landscape",
  "columnWidths": { "1": 5, "2-8": "1x", "9-10": "2x" },

  "fonts": {
    "default":   { "face": "Arial", "size": 10 },
    "bold":      { "face": "Arial", "size": 10, "bold": true },
    "header":    { "face": "Arial", "size": 14, "bold": true },
    "small-ital":{ "face": "Arial", "size": 8,  "italic": true }
  },

  "styles": {
    "header":         { "font": "header", "align": "center" },
    "label":          { "font": "bold" },
    "bordered":       { "border": "all" },
    "bordered-right": { "border": "all", "align": "right" },
    "bordered-thick": { "border": "all", "borderWidth": "thick" },
    "money":          { "border": "all", "align": "right", "format": "ЧЦ=15; ЧДЦ=2" },
    "date":           { "format": "ДФ=dd.MM.yyyy" },
    "total":          { "font": "bold", "border": "top", "align": "right", "format": "ЧДЦ=2" },
    "note":           { "font": "small-ital", "wrap": true }
  },

  "areas": [
    { "name": "Заголовок", "rows": [
      { "height": 24, "cells": [
        { "col": 1, "span": 10, "style": "header", "param": "ТекстЗаголовка" }
      ]},
      { "empty": 1 }
    ]},

    { "name": "Поставщик", "rows": [
      { "cells": [
        { "col": 1, "span": 2, "style": "label", "text": "Поставщик:" },
        { "col": 3, "span": 8, "param": "ПредставлениеПоставщика", "detail": "Поставщик" }
      ]},
      { "cells": [
        { "col": 1, "span": 2, "style": "label", "text": "Дата:" },
        { "col": 3, "style": "date", "param": "ДатаДокумента" }
      ]}
    ]},

    { "name": "ШапкаТаблицы", "rows": [
      { "rowStyle": "bordered-thick", "cells": [
        { "col": 1, "text": "№" },
        { "col": 2, "span": 6, "text": "Товар" },
        { "col": 9, "text": "Кол-во" },
        { "col": 10, "text": "Сумма" }
      ]}
    ]},

    { "name": "Строка", "rows": [
      { "rowStyle": "bordered", "cells": [
        { "col": 1, "param": "НомерСтроки" },
        { "col": 2, "span": 6, "param": "Товар", "detail": "Номенклатура" },
        { "col": 9, "style": "bordered-right", "param": "Количество" },
        { "col": 10, "style": "money", "param": "Сумма" }
      ]}
    ]},

    { "name": "Итого", "rows": [
      { "cells": [
        { "col": 8, "span": 2, "style": "total", "text": "Итого:" },
        { "col": 10, "style": "total", "param": "Всего" }
      ]},
      { "cells": [
        { "col": 1, "span": 10, "style": "note", "template": "Документ сформирован [Дата] пользователем [Пользователь]" }
      ]}
    ]}
  ]
}
```
