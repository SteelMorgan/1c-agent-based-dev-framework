# SKD: output templates (templates DSL)

Compact tabular description of SKD output templates instead of raw XML.

## Basic structure

```json
"templates": [
  {
    "name": "Макет1",
    "style": "header",
    "widths": [36, 33, 16, 17],
    "minHeight": 24.75,
    "rows": [
      ["Виды кассы", "Валюта", "Остаток на начало\nпериода", "Остаток на\nконец периода"],
      ["|", "|", "|", "|"],
      ["К1", "К2", "К3", "К4"]
    ]
  },
  {
    "name": "Макет2",
    "style": "data",
    "widths": [36, 33, 16, 17],
    "rows": [["{ВидКассы}", "{Валюта}", "{Остаток}", "{ОстатокКонец}"]],
    "parameters": [
      { "name": "ВидКассы", "expression": "Представление(Счет)" },
      { "name": "Остаток",  "expression": "ОстатокНаНачалоПериода" }
    ]
  }
]
```

## Cell syntax

| Entry | Meaning |
|--------|-------|
| `"text"` | Static label |
| `"{Name}"` | Parameter (`ExpressionAreaTemplateParameter`) |
| `"\|"` | Merge with the cell above |
| `">"` | Merge with the cell to the left |
| `null` | Empty cell |

Line breaks in text use `\n`.

## Two-level header with horizontal merging

```json
"rows": [
  ["Вид актива", "Остаток начало", "Поступление", ">", ">", ">", "Выбытие", ">", ">", "Остаток конец"],
  ["|",          "|",              "из произв.",   "из п/ф", "со сч.40", "прочее", "Реализ.", "отгруж.", "прочее", "|"],
  ["К1",         "К2",             "К3",           "К4",     "К5",       "К6",     "К7",      "К8",      "К9",     "К10"]
]
```

## Built-in styles

| `style` | Purpose |
|---------|------------|
| `header` | Header: background, centered, wrapping |
| `data` | Data rows: group background |
| `subheader` | Subheader: no background, centered |
| `total` | Totals: no background |

All styles use Arial 10, Solid 1px borders, platform-style colors.

## Custom styles

The file `skd-styles.json` is searched in this order:

1. Next to the JSON definition.
2. In the current directory.
3. In `presets/skills/skd/skd-styles.json` (search upward from `OutputPath`).

The first one found wins.

Example (`skd-styles.json`):

```json
{
  "header": {
    "font": { "name": "Arial", "size": 10, "bold": true },
    "background": "style:ФонШапки",
    "horizontalAlign": "Center",
    "verticalAlign": "Center",
    "wrap": true,
    "border": { "style": "Solid", "width": 1 }
  },
  "data": {
    "font": { "name": "Arial", "size": 10 },
    "background": "style:ФонДанных",
    "border": { "style": "Solid", "width": 1 }
  }
}
```

## Drilldown

The `drilldown` key in a template parameter automatically generates `DetailsAreaTemplateParameter` and the `DrillDown` binding in cell `appearance`:

```json
"parameters": [
  { "name": "Сырье", "expression": "ПоступлениеСырья", "drilldown": "ПоступлениеСырья" }
]
```

What gets emitted:

- `ExpressionAreaTemplateParameter` (regular) — for `{RawMaterial}`.
- `DetailsAreaTemplateParameter` with name `Расшифровка_ПоступлениеСырья`, `fieldExpression` by resource name, `mainAction=DrillDown`.
- All `{RawMaterial}` cells automatically receive `appearance: { DrillDown: Расшифровка_ПоступлениеСырья }`.

## Binding templates to groupings (groupTemplates)

```json
"groupTemplates": [
  { "groupName": "ДанныеОтчета", "templateType": "GroupHeader", "template": "Макет1" },
  { "groupField": "Счет",        "templateType": "Header",       "template": "Макет2" },
  { "groupField": "Счет",        "templateType": "OverallHeader","template": "Макет3" }
]
```

| Field | What it sets |
|------|------------|
| `groupField` | Binding to grouping field |
| `groupName` | Binding to named grouping in the variant structure |
| `templateType` | `Header` (data rows) → `<groupTemplate>`; `OverallHeader` (totals) → `<groupTemplate>`; `GroupHeader` (header) → `<groupHeaderTemplate>` |
| `template` | Template name from `templates` |

## Raw XML as fallback

If the template has a `template` key with an XML string, it is used as-is (raw). Detection: presence of `rows` → DSL, otherwise → raw.

```json
{ "name": "СтарыйМакет", "template": "<v8:Template ...>...</v8:Template>" }
```

Useful for migrating existing templates before moving to DSL.
