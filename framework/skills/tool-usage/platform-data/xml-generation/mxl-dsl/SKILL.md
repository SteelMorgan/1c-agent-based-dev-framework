---
name: mxl-dsl
description: JSON DSL для генерации табличных документов 1С (MXL) с областями, ячейками, шрифтами и стилями. Используй при mxl compile для печатных форм.
---

# MXL DSL

JSON DSL для генерации табличных документов 1С (SpreadsheetDocument).

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать печатную форму (табличный документ) | `mxl compile` с JSON DSL |
| Нужно добавить область в шаблон | Описать в `areas`, перекомпилировать |
| Нужно использовать параметры в ячейках | `parameters` + `[ИмяПараметра]` в text |
| Нужно применить стили | `fonts` + `styles` + `style` в ячейке |
| Нужно анализировать существующий макет | `mxl info <Template.xml>` |
| Нужно обратная конвертация XML → JSON | `mxl decompile <Template.xml> <output.json>` |

## Команда compile

```bash
xml-gen mxl compile [--format designer|edt] <input.json> <output.xml>
```

**output.xml** — путь к Template.xml или макету в EPF: `.../Templates/<Name>/Ext/Template.xml`

## Команда info

Анализ структуры макета: области, параметры, колонки.

```bash
xml-gen mxl info <Template.xml>
```

## Команда decompile

Обратная конвертация Template.xml → JSON DSL.

```bash
xml-gen mxl decompile <Template.xml> <output.json>
```

## Структура DSL

### Минимальный документ

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

### Области (areas)

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

### Шрифты и стили

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

### Параметры

```json
{
  "parameters": [
    {"name": "Организация", "type": "CatalogRef.Организации"},
    {"name": "ДатаНачала", "type": "date"}
  ]
}
```

Использование в ячейках: `{"text": "[Организация]"}`

### Свойства ячеек

| Свойство | Описание |
|----------|----------|
| `text` | Текст ячейки |
| `span` | Объединение ячеек (colspan) |
| `style` | Имя стиля |

### Свойства стилей

`font`, `horizontalAlignment` (Left/Center/Right), `verticalAlignment`, `border` (all/top/bottom/left/right), `borderWidth` (thin/thick), `textPlacement` (Wrap/Block), `format` (ЧДЦ=2, ДФ=dd.MM.yyyy)

## Полный пример

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

## Правильно / Неправильно

```json
// ❌ Неправильно — параметр в ячейке без скобок (не подставится при выводе)
{"cells": [{"text": "Организация"}]}

// ✅ Правильно — [ИмяПараметра] для подстановки
{"cells": [{"text": "[Организация]"}]}
```

> 1С ищет `[Имя]` в тексте области и подставляет значение из параметров при выводе. Без скобок — статический текст.

```json
// ❌ Неправильно — style ссылается на несуществующий шрифт
"styles": {"HeaderStyle": {"font": "MyFont"}}
// fonts не содержит MyFont

// ✅ Правильно — font должен быть в fonts
"fonts": {"HeaderFont": {"face": "Arial", "size": 14, "bold": true}},
"styles": {"HeaderStyle": {"font": "HeaderFont"}}
```

> Стиль ссылается на шрифт по имени. Если шрифта нет — ошибка при генерации.

## Использование областей в BSL-коде

Имена областей из DSL (поле `"name"`) напрямую используются в BSL при выводе печатной формы:

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

> Имена параметров областей (`Параметры.Наименование`) должны совпадать с ключами из DSL-поля `"params"` соответствующей области.

## См. также

- [xml-generation](../xml-generation/) — общее описание
- [epf-operations](../epf-operations/) — создание обработок

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
