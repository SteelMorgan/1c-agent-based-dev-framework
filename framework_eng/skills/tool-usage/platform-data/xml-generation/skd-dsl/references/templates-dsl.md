# SKD: output templates (templates DSL)

Compact tabular description of SKD output layouts instead of raw XML.

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
|-------|---------|
| `"text"` | Static label |
| `"{Name}"` | Parameter (`ExpressionAreaTemplateParameter`) |
| `"\|"` | Merge with the cell above |
| `">"` | Merge with the cell to the left |
| `null` | Empty cell |

Line break in text is `\n`.

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
|---------|---------|
| `header` | Header: background, center, wrapping |
| `data` | Data rows: group background |
| `subheader` | Subheader: no background, center |
| `total` | Totals: no background |

All styles are Arial 10, Solid 1px borders, colors via platform styles.

## Custom styles

The `skd-styles.json` file is searched in this order:

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

The `drilldown` key in the template parameter automatically generates `DetailsAreaTemplateParameter` and the `Drilldown` binding in the `appearance` of cells:

```json
"parameters": [
  { "name": "Сырье", "expression": "ПоступлениеСырья", "drilldown": "ПоступлениеСырья" }
]
```

What is emitted:

- `ExpressionAreaTemplateParameter` (regular) - for `{Сырье}`.
- `DetailsAreaTemplateParameter` with the name `Расшифровка_ПоступлениеСырья`, `fieldExpression` by the resource name, `mainAction=DrillDown`.
- All `{Сырье}` cells automatically get `appearance: { Расшифровка: Расшифровка_ПоступлениеСырья }`.

## Binding layouts to groupings (groupTemplates)

```json
"groupTemplates": [
  { "groupName": "ДанныеОтчета", "templateType": "GroupHeader", "template": "Макет1" },
  { "groupField": "Счет",        "templateType": "Header",       "template": "Макет2" },
  { "groupField": "Счет",        "templateType": "OverallHeader","template": "Макет3" }
]
```

| Field | What it defines |
|------|------------|
| `groupField` | Binding to the grouping field |
| `groupName` | Binding to the named grouping in the variant structure |
| `templateType` | `Header` (data rows) -> `<groupTemplate><templateType>Header</templateType>`; `OverallHeader` (totals) -> `<groupTemplate>`; `GroupHeader` (header) -> `<groupHeaderTemplate><templateType>Header</templateType>` |
| `template` | The layout name from `templates` |

`GroupHeader` selects a separate Designer XML element, but the platform value of the nested
`templateType` remains `Header`. Therefore, you must not replace `GroupHeader` with `Header` in the DSL just to
make the build pass: this will create an ordinary `<groupTemplate>` and change where the area is rendered. You also must not
rewrite the generated XML manually. If the compiler violates this structure, stop the dependent
pipeline and run the tool defect process from `xml-generation` §4.1, including the regression test,
full build, and the Designer package/load oracle.

## Raw XML as a fallback

If the template has a `template` key with an XML string, it is used as-is (raw). Detection: `rows` present → DSL, otherwise → raw.

This mode is intended only for migrating an existing canonical layout. It is not
a way to work around a defect in the supported DSL serialization: an incorrect generator result cannot
be copied into raw XML and passed off as a fix.

```json
{ "name": "СтарыйМакет", "template": "<v8:Template ...>...</v8:Template>" }
```

Useful for migrating existing layouts before switching to DSL.
