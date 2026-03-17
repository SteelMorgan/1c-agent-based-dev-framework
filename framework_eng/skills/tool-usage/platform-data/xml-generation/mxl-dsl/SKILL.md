---
name: mxl-dsl
description: JSON DSL for generating 1C tabular documents (MXL) with areas, cells, fonts, and styles. Use with mxl compile for print forms.
---

# MXL DSL

JSON DSL for generating 1C tabular documents (SpreadsheetDocument).

## When to use

| Trigger | Action |
|---------|--------|
| Need to create a print form (tabular document) | `mxl compile` with JSON DSL |
| Need to add an area to the template | Describe in `areas`, then recompile |
| Need to use parameters in cells | `parameters` + `[ParameterName]` in text |
| Need to apply styles | `fonts` + `styles` + `style` in the cell |
| Need to analyze an existing layout | `mxl info <Template.xml>` |
| Need reverse XML → JSON conversion | `mxl decompile <Template.xml> <output.json>` |

## Compile command

```bash
xml-gen mxl compile [--format designer|edt] <input.json> <output.xml>
```

**output.xml** — path to Template.xml or layout in EPF: `.../Templates/<Name>/Ext/Template.xml`

## Info command

Analyze the layout structure: areas, parameters, columns.

```bash
xml-gen mxl info <Template.xml>
```

## Decompile command

Reverse conversion from Template.xml to JSON DSL.

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

### Areas (areas)

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
| `span` | Cell merging (colspan) |
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
// ❌ Incorrect — parameter in a cell without brackets (won't be substituted when rendered)
{"cells": [{"text": "Организация"}]}

// ✅ Correct — `[ParameterName]` for substitution
{"cells": [{"text": "[Организация]"}]}
```

> 1С looks for `[Name]` in the area text and substitutes the value from parameters during rendering. Without brackets — static text.

```json
// ❌ Incorrect — style references a non-existing font
"styles": {"HeaderStyle": {"font": "MyFont"}}
// fonts does not contain MyFont

// ✅ Correct — font must exist in fonts
"fonts": {"HeaderFont": {"face": "Arial", "size": 14, "bold": true}},
"styles": {"HeaderStyle": {"font": "HeaderFont"}}
```

> A style references a font by name. If the font is missing — generation fails.

## Using areas in BSL code

Area names from the DSL (the `"name"` field) are used directly in BSL when rendering the print form:

```bsl
// ПечатнаяФорма — имя макета в обработке (соответствует name в epf add-template)
ТД = ЭтотОбъект.ПолучитьМакет("ПечатнаяФорма");
ТабДок = Новый ТабличныйДокумент;

// "Header", "Row", "Footer" — значения поля "name" из DSL-секции areas
ОбластьШапка = ТД.ПолучитьОбласть("Header");
ТабДок.Вывести(ОбластьШапка);

Для Каждого Строка Из ДанныеДляВывода Цикл
    ОбластьСтрока = ТД.ПолучитьОбласть("Row");
    ОбластьСтрока.Параметры.Наименование = Строка.Наименование;
    ОбластьСтрока.Параметры.Сумма       = Строка.Сумма;
    ТабДок.Вывести(ОбластьСтрока);
КонецЦикла;

ОбластьПодвал = ТД.ПолучитьОбласть("Footer");
ТабДок.Вывести(ОбластьПодвал);
```

> Area parameter names (`Параметры.Наименование`) must match the keys from the DSL `"params"` field of the corresponding area.

## See also

- [xml-generation](../xml-generation/) — general overview
- [epf-operations](../epf-operations/) — creating documents

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
