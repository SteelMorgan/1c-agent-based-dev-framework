---
name: skd-dsl
description: JSON DSL for generating 1C DataCompositionSchema (SKD) with filters, sorting, and conditional formatting. Use it with skd compile for reports.
---

# SKD DSL

JSON DSL for generating 1C DataCompositionSchema (DataCompositionSchema).

## When to use

| Trigger | Action |
|---------|----------|
| Need to create a report (СКД) | `skd compile` with JSON DSL |
| Need to add a parameter to an existing schema | `skd add-parameter` → [xml-gen-cli](../xml-gen-cli/) |
| Need to add a field to a DataSet | `skd add-field` → [xml-gen-cli](../xml-gen-cli/) |
| Need a DataSetUnion | Workaround: DataSetQuery with UNION in the query |
| Need calculated fields | Workaround: calculations in the SELECT of the query |
| Need to analyze an existing СКД | `skd info <Schema.xml>` |

## Command compile

```bash
xml-gen skd compile [--format designer|edt] <input.json> <output.xml>
```

**Editing** (add-parameter, add-field) — see [xml-gen-cli](../xml-gen-cli/)

## Command info

Analyze a СКД: data sets, fields, parameters, layout variants.

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

### Layout variants (settingsVariants)

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

### Filter operators

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

## Full example

```json
{
  "dataSets": [{
    "name": "Продажи",
    "query": "ВЫБРАТЬ Организация, Номенклатура, Количество, Сумма ИЗ РегистрНакопления.Продажи",
    "fields": [
      {"dataPath": "Организация", "title": "Организация"},
      {"dataPath": "Номенклатура", "title": "Номенклатура"},
      {"dataPath": "Количество", "title": "Количество", "type": "number(15,2)"},
      {"dataPath": "Сумма", "title": "Сумма", "type": "number(15,2)"}
    ]
  }],
  "totalFields": [
    {"dataPath": "Количество", "expression": "Сумма(Количество)"},
    {"dataPath": "Сумма", "expression": "Сумма(Сумма)"}
  ],
  "settingsVariants": [{
    "name": "Основной",
    "settings": {
      "selection": ["Организация", "Номенклатура", "Количество", "Сумма"],
      "filter": ["Количество > 0"],
      "order": ["Сумма desc"],
      "structure": [{"type": "group", "groupBy": ["Организация"], "selection": ["Auto"]}]
    }
  }]
}
```

## Limitations (15%)

- Only DataSetQuery (DataSetObject/Union are not supported)
- No CalculatedFields
- Workaround: use calculations in queries

## Right / Wrong

```json
// ❌ Wrong — filter with a Russian operator (only Latin operators are supported)
"filter": ["Сумма больше 0"]

// ✅ Right — operators: =, <>, >, >=, <, <=, in, notIn, contains, filled, notFilled
"filter": ["Сумма > 0"]
```

> The filter parser expects operators from a fixed list. `больше` is not recognized.

```json
// ❌ Wrong — dataPath in selection is not from dataSets
"settings": {"selection": ["НесуществующееПоле"]}

// ✅ Right — selection only from dataSets fields
"fields": [{"dataPath": "Сумма", "title": "Сумма"}],
"settings": {"selection": ["Сумма"]}
```

> Fields used in selection, order, filter, structure must exist in dataSets. Otherwise the СКД will not compile.

## See also

- [xml-generation](../xml-generation/) — general description
- [xml-gen-cli](../xml-gen-cli/) — add-parameter, add-field
- [epf-operations](../epf-operations/) — creating processing routines

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
