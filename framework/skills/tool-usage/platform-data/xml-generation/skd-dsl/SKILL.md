---
name: skd-dsl
description: JSON DSL для генерации схем компоновки данных 1С (SKD) с отборами, сортировкой и условным оформлением. Используй при skd compile для отчётов.
---

# SKD DSL

JSON DSL для генерации схем компоновки данных 1С (DataCompositionSchema).

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать отчёт (СКД) | `skd compile` с JSON DSL |
| Нужно добавить параметр в существующую схему | `skd add-parameter` → [xml-gen-cli](../xml-gen-cli/) |
| Нужно добавить поле в DataSet | `skd add-field` → [xml-gen-cli](../xml-gen-cli/) |
| Нужен DataSetUnion | Workaround: DataSetQuery с UNION в запросе |
| Нужны вычисляемые поля | Workaround: вычисления в SELECT запроса |
| Нужно анализировать существующую СКД | `skd info <Schema.xml>` |

## Команда compile

```bash
xml-gen skd compile [--format designer|edt] <input.json> <output.xml>
```

**Редактирование** (add-parameter, add-field) — см. [xml-gen-cli](../xml-gen-cli/)

## Команда info

Анализ СКД: наборы данных, поля, параметры, варианты настроек.

```bash
xml-gen skd info <Schema.xml>
```

## Структура DSL

### Минимальная схема

```json
{
  "dataSets": [
    {
      "name": "НаборДанных1",
      "query": "ВЫБРАТЬ Наименование, Количество ИЗ Номенклатура",
      "fields": [
        {"dataPath": "Наименование"},
        {"dataPath": "Количество"}
      ]
    }
  ]
}
```

### DataSetQuery (запрос)

```json
{
  "name": "Продажи",
  "query": "ВЫБРАТЬ Организация, Номенклатура, Количество, Сумма ИЗ РегистрНакопления.Продажи",
  "fields": [
    {"dataPath": "Организация", "title": "Организация"},
    {"dataPath": "Сумма", "title": "Сумма", "type": "number(15,2)"}
  ]
}
```

**Типы полей:** `string`, `string(N)`, `number`, `number(D,F)`, `boolean`, `date`, `CatalogRef.Name`, `DocumentRef.Name`

### Параметры

```json
{
  "parameters": [
    {"name": "Период", "title": "Период", "type": "StandardPeriod", "value": "LastMonth"},
    {"name": "Организация", "title": "Организация", "type": "CatalogRef.Организации"}
  ]
}
```

### Варианты настроек (settingsVariants)

```json
{
  "settingsVariants": [{
    "name": "Основной",
    "settings": {
      "selection": ["Организация", "Сумма"],
      "filter": ["Сумма > 0", "Дата >= 2024-01-01T00:00:00"],
      "order": ["Сумма desc", "Организация"],
      "structure": [
        {"type": "group", "groupBy": ["Организация"], "selection": ["Auto"]}
      ],
      "conditionalAppearance": [
        {
          "selection": ["Сумма"],
          "filter": ["Сумма > 10000"],
          "appearance": {"ЦветТекста": "web:Red"}
        }
      ]
    }
  }]
}
```

### Операторы filter

`=`, `<>`, `>`, `>=`, `<`, `<=`, `in`, `notIn`, `contains`, `filled`, `notFilled`

### Итоговые поля (totalFields)

```json
{
  "totalFields": [
    {"dataPath": "Количество", "expression": "Сумма(Количество)"},
    {"dataPath": "Сумма", "expression": "Сумма(Сумма)"}
  ]
}
```

## Ограничения

- Только DataSetQuery (DataSetObject/Union не поддерживаются)
- Нет CalculatedFields
- Workaround: используй вычисления в запросах

## Правильно / Неправильно

```json
// ❌ Неправильно — filter с русским оператором (поддерживаются только латинские)
"filter": ["Сумма больше 0"]

// ✅ Правильно — операторы: =, <>, >, >=, <, <=, in, notIn, contains, filled, notFilled
"filter": ["Сумма > 0"]
```

> Парсер filter ожидает операторы из фиксированного списка. `больше` не распознаётся.

Поля в selection, order, filter, structure должны существовать в dataSets — иначе СКД не скомпонуется.


---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
