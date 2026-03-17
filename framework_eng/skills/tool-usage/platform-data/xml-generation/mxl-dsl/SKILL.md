---
name: mxl-dsl
description: JSON DSL for generating 1C spreadsheet documents (MXL) with areas, cells, fonts, and styles. Use it with mxl compile for print layouts.
---

# MXL DSL

JSON DSL for generating 1C spreadsheet documents (SpreadsheetDocument).

## When to use

| Trigger | Action |
|---------|--------|
| Need to create a print layout (spreadsheet document) | `mxl compile` with JSON DSL |
| Need to add an area to the template | Describe it in `areas`, recompile |
| Need to use parameters in cells | `parameters` + `[ParameterName]` inside text |
| Need to apply styles | `fonts` + `styles` + `style` in the cell |
| Need to analyze an existing layout | `mxl info <Template.xml>` |
| Need reverse conversion XML → JSON | `mxl decompile <Template.xml> <output.json>` |

## Compile command

```bash
xml-gen mxl compile [--format designer|edt] <input.json> <output.xml>
```

**output.xml** — path to Template.xml or a layout in an EPF: `.../Templates/<Name>/Ext/Template.xml`

## Info command

Analyzes the layout structure: areas, parameters, columns.

```bash
xml-gen mxl info <Template.xml>
```

## Decompile command

Reverse conversion Template.xml → JSON DSL.

```bash
xml-gen mxl decompile <Template.xml> <output.json>
```

## DSL structure

### Minimal document

```json
{
  "areas": [
    {
      "name": "Header",
      "rows": [
        {"cells": [{"text": "Заголовок"}]}
      ]
    }
  ]
}
```

### Areas (`areas`)

```json
{
  "name": "ИмяОбласти",
  "rows": [
    {"cells": [
      {"text": "Текст ячейки"},
      {"text": "Объединённая", "span": 3},
      {"text": "[Параметр]", "style": "HeaderStyle"}
    ]}
  ]
}
```

### Fonts and styles

```json
{
  "fonts": {
    "HeaderFont": {"face": "Arial", "size": 14, "bold": true}
  },
  "styles": {
    "HeaderStyle": {
      "font": "HeaderFont",
      "horizontalAlignment": "Center",
      "border": "all",
      "borderWidth": "thick"
    }
  }
}
```

### Parameters

```json
{
  "parameters": [
    {"name": "Организация", "type": "CatalogRef.Организации"},
    {"name": "ДатаНачала", "type": "date"}
  ]
}
```

Usage in cells: `{"text": "[Организация]"}`

### Cell properties

| Property | Description |
|----------|-------------|
| `text` | Cell text |
| `span` | Cell merge (colspan) |
| `style` | Style name |

### Style properties

`font`, `horizontalAlignment` (Left/Center/Right), `verticalAlignment`, `border` (all/top/bottom/left/right), `borderWidth` (thin/thick), `textPlacement` (Wrap/Block), `format` (ЧДЦ=2, ДФ=dd.MM.yyyy)

## Full example

```json
{
  "fonts": {
    "HeaderFont": {"face": "Arial", "size": 14, "bold": true},
    "BodyFont": {"face": "Arial", "size": 10}
  },
  "styles": {
    "HeaderStyle": {
      "font": "HeaderFont",
      "horizontalAlignment": "Center",
      "border": "all"
    },
    "NumberStyle": {
      "font": "BodyFont",
      "horizontalAlignment": "Right",
      "format": "ЧДЦ=2"
    }
  },
  "areas": [
    {"name": "Header", "rows": [{"cells": [{"text": "Отчёт по продажам", "style": "HeaderStyle", "span": 3}]}]},
    {"name": "TableHeader", "rows": [{"cells": [{"text": "Наименование", "style": "HeaderStyle"}, {"text": "Сумма", "style": "HeaderStyle"}]}]},
    {"name": "Row", "rows": [{"cells": [{"text": "[Наименование]"}, {"text": "[Сумма]", "style": "NumberStyle"}]}]}
  ],
  "parameters": [
    {"name": "Наименование", "type": "string"},
    {"name": "Сумма", "type": "number(15,2)"}
  ]
}
```

## Correct / Incorrect

```json
// ❌ Неправильно — параметр в ячейке без скобок (не подставится при выводе)
{"cells": [{"text": "Организация"}]}

// ✅ Правильно — [ИмяПараметра] для подстановки
{"cells": [{"text": "[Организация]"}]}
```
