---
name: mxl-dsl
description: JSON DSL for generating 1С tabular documents (MXL) with areas, cells, fonts, and styles. Use with mxl compile for print forms.
---

# MXL DSL

JSON DSL for generating 1С tabular documents (SpreadsheetDocument).

## When to use

| Trigger | Action |
|---------|--------|
| Need to create a print form (tabular document) | `mxl compile` with the JSON DSL |
| Need to add an area to a template | Describe it in `areas`, recompile |
| Need to use parameters in cells | `parameters` + `[ParameterName]` in `text` |
| Need to apply styles | `fonts` + `styles` + `style` in a cell |

## Compile command

```bash
xml-gen mxl compile [--format designer|edt] <input.json> <output.xml>
```

**output.xml** — path to Template.xml or template inside EPF: `.../Templates/<Name>/Ext/Template.xml`

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

Use in cells: `{"text": "[Организация]"}`

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

## Right / Wrong

```json
// ❌ Wrong — parameter in the cell without brackets (will not be substituted during output)
{"cells": [{"text": "Организация"}]}

// ✅ Right — [ParameterName] for substitution
{"cells": [{"text": "[Организация]"}]}
```

> 1С searches for `[Name]` inside the area text and inserts the value from the parameters when rendering. Without brackets — static text.

```json
// ❌ Wrong — style references a font that does not exist
"styles": {"HeaderStyle": {"font": "MyFont"}}
// fonts does not contain MyFont

// ✅ Right — font must exist in fonts
"fonts": {"HeaderFont": {"face": "Arial", "size": 14, "bold": true}},
"styles": {"HeaderStyle": {"font": "HeaderFont"}}
```

> A style refers to a font by name. If the font is missing — generation fails.

## Using areas in BSL code

The names of areas from the DSL (the `"name"` field) are used directly in BSL when rendering the print form:

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

> Area parameter names (`Parameters.Наименование`) must match the keys from the DSL `"params"` field of the corresponding area.

## See also

- [xml-generation](../xml-generation/) — general overview
- [epf-operations](../epf-operations/) — creating operations

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
