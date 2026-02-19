---
name: mxl-dsl
description: JSON DSL для генерации табличных документов 1С (MXL) с областями, ячейками, шрифтами и стилями
category: 1c-development
tags: [1c, mxl, spreadsheet, template]
version: 1.0.0
---

# MXL DSL

JSON DSL для генерации табличных документов 1С (SpreadsheetDocument).

## Команда

```bash
java -jar xml-gen.jar mxl compile <input.json> <output.xml>
```

**Параметры:**
- `<input.json>` — JSON DSL файл
- `<output.xml>` — выходной Template.xml

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

### Полная структура

```json
{
  "fonts": {
    "HeaderFont": {"face": "Arial", "size": 14, "bold": true}
  },
  "styles": {
    "HeaderStyle": {"font": "HeaderFont", "horizontalAlignment": "Center"}
  },
  "areas": [
    {
      "name": "Header",
      "rows": [
        {"cells": [{"text": "Заголовок", "style": "HeaderStyle", "span": 3}]}
      ]
    }
  ],
  "parameters": [
    {"name": "Организация", "type": "CatalogRef.Организации"}
  ]
}
```

## Области (areas)

### Базовый синтаксис

```json
{
  "name": "ИмяОбласти",
  "rows": [
    {"cells": [...]}
  ]
}
```

### Пример области

```json
{
  "name": "Header",
  "rows": [
    {"cells": [
      {"text": "Отчёт по продажам"},
      {"text": "за период"},
      {"text": "[ДатаНачала] - [ДатаОкончания]"}
    ]}
  ]
}
```

## Ячейки (cells)

### Базовые свойства

```json
{
  "text": "Текст ячейки",
  "span": 2,
  "style": "ИмяСтиля"
}
```

### Свойства ячеек

| Свойство | Тип | Описание |
|----------|-----|----------|
| `text` | string | Текст ячейки |
| `span` | number | Объединение ячеек (colspan) |
| `style` | string | Имя стиля |

### Примеры ячеек

```json
{
  "cells": [
    {"text": "Простая ячейка"},
    {"text": "Объединённая", "span": 3},
    {"text": "Со стилем", "style": "HeaderStyle"},
    {"text": "[Параметр]"}
  ]
}
```

## Шрифты (fonts)

### Базовый синтаксис

```json
{
  "fonts": {
    "ИмяШрифта": {
      "face": "Arial",
      "size": 12,
      "bold": true,
      "italic": false,
      "underline": false,
      "strikeout": false
    }
  }
}
```

### Свойства шрифтов

| Свойство | Тип | Значения | По умолчанию |
|----------|-----|----------|--------------|
| `face` | string | Arial, Times New Roman, Courier New | Arial |
| `size` | number | 8-72 | 10 |
| `bold` | boolean | true/false | false |
| `italic` | boolean | true/false | false |
| `underline` | boolean | true/false | false |
| `strikeout` | boolean | true/false | false |

### Примеры шрифтов

```json
{
  "fonts": {
    "HeaderFont": {
      "face": "Arial",
      "size": 14,
      "bold": true
    },
    "BodyFont": {
      "face": "Times New Roman",
      "size": 10
    },
    "MonoFont": {
      "face": "Courier New",
      "size": 9,
      "italic": true
    }
  }
}
```

## Стили (styles)

### Базовый синтаксис

```json
{
  "styles": {
    "ИмяСтиля": {
      "font": "ИмяШрифта",
      "horizontalAlignment": "Center",
      "verticalAlignment": "Top",
      "border": "all",
      "borderWidth": "thin",
      "textPlacement": "Wrap",
      "format": "ЧДЦ=2"
    }
  }
}
```

### Свойства стилей

| Свойство | Тип | Значения |
|----------|-----|----------|
| `font` | string | Имя шрифта из fonts |
| `horizontalAlignment` | string | Left, Center, Right, Justify |
| `verticalAlignment` | string | Top, Center, Bottom |
| `border` | string | all, top, bottom, left, right, top,bottom |
| `borderWidth` | string | thin, thick |
| `textPlacement` | string | Wrap, Block |
| `format` | string | Формат 1С (ЧДЦ=2, ДФ=dd.MM.yyyy) |

### Примеры стилей

```json
{
  "styles": {
    "HeaderStyle": {
      "font": "HeaderFont",
      "horizontalAlignment": "Center",
      "verticalAlignment": "Center",
      "border": "all",
      "borderWidth": "thick"
    },
    "NumberStyle": {
      "font": "BodyFont",
      "horizontalAlignment": "Right",
      "format": "ЧДЦ=2"
    },
    "DateStyle": {
      "font": "BodyFont",
      "format": "ДФ=dd.MM.yyyy"
    },
    "WrapStyle": {
      "textPlacement": "Wrap",
      "border": "top,bottom"
    }
  }
}
```

### Рамки (border)

Поддерживаются следующие варианты:

| Значение | Описание |
|----------|----------|
| `all` | Все стороны |
| `top` | Только сверху |
| `bottom` | Только снизу |
| `left` | Только слева |
| `right` | Только справа |
| `top,bottom` | Сверху и снизу |
| `left,right` | Слева и справа |

## Параметры (parameters)

```json
{
  "parameters": [
    {"name": "Организация", "type": "CatalogRef.Организации"},
    {"name": "ДатаНачала", "type": "date"},
    {"name": "ДатаОкончания", "type": "date"},
    {"name": "Заголовок", "type": "string"}
  ]
}
```

**Использование в ячейках:**
```json
{"text": "[Организация]"}
{"text": "[ДатаНачала] - [ДатаОкончания]"}
```

## Полные примеры

### Пример 1: Простой отчёт

```json
{
  "areas": [
    {
      "name": "Header",
      "rows": [
        {"cells": [{"text": "Отчёт по продажам", "span": 3}]}
      ]
    },
    {
      "name": "TableHeader",
      "rows": [
        {"cells": [
          {"text": "Наименование"},
          {"text": "Количество"},
          {"text": "Сумма"}
        ]}
      ]
    },
    {
      "name": "Row",
      "rows": [
        {"cells": [
          {"text": "[Наименование]"},
          {"text": "[Количество]"},
          {"text": "[Сумма]"}
        ]}
      ]
    }
  ]
}
```

### Пример 2: Отчёт с шрифтами и стилями

```json
{
  "fonts": {
    "HeaderFont": {"face": "Arial", "size": 14, "bold": true},
    "BodyFont": {"face": "Arial", "size": 10},
    "TotalFont": {"face": "Arial", "size": 10, "bold": true}
  },
  "styles": {
    "HeaderStyle": {
      "font": "HeaderFont",
      "horizontalAlignment": "Center",
      "verticalAlignment": "Center",
      "border": "all",
      "borderWidth": "thick"
    },
    "BodyStyle": {
      "font": "BodyFont",
      "border": "all"
    },
    "NumberStyle": {
      "font": "BodyFont",
      "horizontalAlignment": "Right",
      "format": "ЧДЦ=2",
      "border": "all"
    },
    "TotalStyle": {
      "font": "TotalFont",
      "horizontalAlignment": "Right",
      "format": "ЧДЦ=2",
      "border": "all",
      "borderWidth": "thick"
    }
  },
  "areas": [
    {
      "name": "Header",
      "rows": [
        {"cells": [
          {"text": "Отчёт по продажам", "style": "HeaderStyle", "span": 3}
        ]}
      ]
    },
    {
      "name": "TableHeader",
      "rows": [
        {"cells": [
          {"text": "Наименование", "style": "HeaderStyle"},
          {"text": "Количество", "style": "HeaderStyle"},
          {"text": "Сумма", "style": "HeaderStyle"}
        ]}
      ]
    },
    {
      "name": "Row",
      "rows": [
        {"cells": [
          {"text": "[Наименование]", "style": "BodyStyle"},
          {"text": "[Количество]", "style": "NumberStyle"},
          {"text": "[Сумма]", "style": "NumberStyle"}
        ]}
      ]
    },
    {
      "name": "Total",
      "rows": [
        {"cells": [
          {"text": "Итого:", "style": "TotalStyle"},
          {"text": "[ИтогоКоличество]", "style": "TotalStyle"},
          {"text": "[ИтогоСумма]", "style": "TotalStyle"}
        ]}
      ]
    }
  ],
  "parameters": [
    {"name": "Наименование", "type": "string"},
    {"name": "Количество", "type": "number(15,2)"},
    {"name": "Сумма", "type": "number(15,2)"},
    {"name": "ИтогоКоличество", "type": "number(15,2)"},
    {"name": "ИтогоСумма", "type": "number(15,2)"}
  ]
}
```

### Пример 3: Сложная таблица с объединением

```json
{
  "fonts": {
    "TitleFont": {"face": "Arial", "size": 16, "bold": true},
    "HeaderFont": {"face": "Arial", "size": 11, "bold": true},
    "BodyFont": {"face": "Arial", "size": 10}
  },
  "styles": {
    "TitleStyle": {
      "font": "TitleFont",
      "horizontalAlignment": "Center",
      "border": "bottom",
      "borderWidth": "thick"
    },
    "HeaderStyle": {
      "font": "HeaderFont",
      "horizontalAlignment": "Center",
      "verticalAlignment": "Center",
      "border": "all",
      "textPlacement": "Wrap"
    },
    "BodyStyle": {
      "font": "BodyFont",
      "border": "all"
    }
  },
  "areas": [
    {
      "name": "Title",
      "rows": [
        {"cells": [
          {"text": "Отчёт о продажах за [Период]", "style": "TitleStyle", "span": 5}
        ]}
      ]
    },
    {
      "name": "Header",
      "rows": [
        {"cells": [
          {"text": "Товар", "style": "HeaderStyle"},
          {"text": "Количество", "style": "HeaderStyle", "span": 2},
          {"text": "Сумма", "style": "HeaderStyle", "span": 2}
        ]},
        {"cells": [
          {"text": ""},
          {"text": "План", "style": "HeaderStyle"},
          {"text": "Факт", "style": "HeaderStyle"},
          {"text": "План", "style": "HeaderStyle"},
          {"text": "Факт", "style": "HeaderStyle"}
        ]}
      ]
    },
    {
      "name": "Row",
      "rows": [
        {"cells": [
          {"text": "[Товар]", "style": "BodyStyle"},
          {"text": "[КолПлан]", "style": "BodyStyle"},
          {"text": "[КолФакт]", "style": "BodyStyle"},
          {"text": "[СумПлан]", "style": "BodyStyle"},
          {"text": "[СумФакт]", "style": "BodyStyle"}
        ]}
      ]
    }
  ]
}
```

## Использование в коде 1С

### Вывод табличного документа

```bsl
Процедура Печать(ТабличныйДокумент)
    Шаблон = ПолучитьМакет("ПечатнаяФорма");
    
    // Вывести заголовок
    Область = Шаблон.ПолучитьОбласть("Header");
    ТабличныйДокумент.Вывести(Область);
    
    // Вывести шапку таблицы
    Область = Шаблон.ПолучитьОбласть("TableHeader");
    ТабличныйДокумент.Вывести(Область);
    
    // Вывести строки
    Запрос = Новый Запрос("ВЫБРАТЬ Наименование, Количество, Сумма ИЗ ...");
    Выборка = Запрос.Выполнить().Выбрать();
    
    Область = Шаблон.ПолучитьОбласть("Row");
    Пока Выборка.Следующий() Цикл
        Область.Параметры.Наименование = Выборка.Наименование;
        Область.Параметры.Количество = Выборка.Количество;
        Область.Параметры.Сумма = Выборка.Сумма;
        ТабличныйДокумент.Вывести(Область);
    КонецЦикла;
КонецПроцедуры
```

## Особенности

### Автоматическая генерация

1. **UUID** — для документа и всех областей
2. **Координаты** — автоматический расчёт позиций ячеек
3. **Объединение** — автоматическое объединение при span > 1
4. **Стили** — автоматическое применение шрифтов и форматирования

### Формат файла

- **Без BOM** — Template.xml создаётся без UTF-8 BOM
- **Правильная структура** — соответствует формату 1С Designer

## Ограничения

### Текущие ограничения

1. **Нет ширины колонок** — ширина колонок не задаётся
2. **Нет rowStyle** — автозаполнение пустых ячеек не поддерживается
3. **Нет картинок** — вставка картинок не поддерживается
4. **Нет backColor** — цвет фона ячеек не поддерживается
5. **Нет примечаний** — примечания к ячейкам не поддерживаются

### Workaround

- **Ширина колонок** — задавай вручную в 1С
- **Картинки** — добавляй вручную в Template.xml
- **Цвет фона** — добавляй вручную в стили

## См. также

- [XML Generation](./xml-generation.md) — общее описание модуля
- [EPF Operations](./epf-operations.md) — создание обработок
- [SPEC-002](../../docs/SPEC-002-xml-generation.md) — полная спецификация

## Версия

**Текущая версия:** 1.0.0  
**Статус:** Production Ready  
**Последнее обновление:** 2026-02-12
