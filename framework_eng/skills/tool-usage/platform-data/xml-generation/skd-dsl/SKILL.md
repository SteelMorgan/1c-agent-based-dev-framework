---
name: skd-dsl
description: "JSON DSL for generating 1C data composition schemas (SKD) with filters, sorting, and conditional appearance. Use with skd compile for reports."
---

# SKD DSL

JSON DSL for generating 1C data composition schemas (DataCompositionSchema).

## When to use

| Trigger | Action |
|---------|----------|
| Need to create a report (SKD) | `skd compile` with JSON DSL |
| Need to add a parameter to an existing schema | `skd add-parameter` → [xml-gen-cli](../xml-gen-cli/) |
| Need to add a field to a DataSet | `skd add-field` → [xml-gen-cli](../xml-gen-cli/) |
| Need DataSetUnion | Workaround: DataSetQuery with UNION in the query |
| Need calculated fields | Workaround: calculations in the SELECT part of the query |
| Need to analyze an existing SKD | `skd info <Schema.xml>` |

## compile command

```bash
xml-gen skd compile [--format designer|edt] <input.json> <output.xml>
```

**Editing** (add-parameter, add-field) — see [xml-gen-cli](../xml-gen-cli/)

## info command

Analyze SKD: data sets, fields, parameters, settings variants.

```bash
xml-gen skd info <Schema.xml>
```

## DSL structure

### Minimal schema

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

### DataSetQuery (query)

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

**Field types:** `string`, `string(N)`, `number`, `number(D,F)`, `boolean`, `date`, `CatalogRef.Name`, `DocumentRef.Name`

### Parameters

```json
{
  "parameters": [
    {"name": "Период", "title": "Период", "type": "StandardPeriod", "value": "LastMonth"},
    {"name": "Организация", "title": "Организация", "type": "CatalogRef.Организации"}
  ]
}
```

### Settings variants (settingsVariants)

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

### filter operators

`=`, `<>`, `>`, `>=`, `<`, `<=`, `in`, `notIn`, `contains`, `filled`, `notFilled`

### Total fields (totalFields)

```json
{
  "totalFields": [
    {"dataPath": "Количество", "expression": "Сумма(Количество)"},
    {"dataPath": "Сумма", "expression": "Сумма(Сумма)"}
  ]
}
```

## Limitations

- Only DataSetQuery (DataSetObject/Union are not supported)
- No CalculatedFields
- Workaround: use calculations in queries

## Correct / Incorrect

```json
// ❌ Неправильно — filter с русским оператором (поддерживаются только латинские)
"filter": ["Сумма больше 0"]

// ✅ Правильно — операторы: =, <>, >, >=, <, <=, in, notIn, contains, filled, notFilled
"filter": ["Сумма > 0"]
```

> The filter parser expects operators from a fixed list. `больше` is not recognized.

Fields in selection, order, filter, structure must exist in dataSets, otherwise the SKD will not be composed.

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
