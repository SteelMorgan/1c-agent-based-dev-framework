---
name: skd-dsl
description: "Use for generating 1C data composition schemas (SKD) from scratch via JSON DSL: datasets, calculated fields, output templates, variants, conditional formatting. Helps build Schema.xml through xml-gen skd compile/info/validate."
---

# SKD DSL

## When to Use

| Trigger | Action |
|---------|--------|
| Create a report (SKD) from scratch | `xml-gen skd compile` with JSON DSL |
| External dataset (without a query) | `dataSets[].objectName` (DataSetObject) |
| Merge datasets | `dataSets[].items` (DataSetUnion) |
| Calculated fields | `calculatedFields` (shorthand or object) |
| Output templates | `templates` + `groupTemplates` - see [references/templates-dsl.md](references/templates-dsl.md) |
| Cell drilldown | `parameters[].drilldown` in the template |
| Links between datasets | `dataSetLinks` |
| Understand someone else's SKD | `xml-gen skd info --mode overview` → then `trace`/`query`/`variant` |
| Check validity | `xml-gen validate --type skd` |
| Targeted editing | `skd add-parameter` / `skd add-field` → [xml-generation](../SKILL.md) §3 |

## CLI Commands

```bash
# Compilation: JSON DSL → Template.xml
xml-gen skd compile [--format designer|edt] <input.json> <output.xml>

# Analysis: Template.xml → compact summary (11 modes)
xml-gen skd info <Template.xml> [--mode <mode>] [--name <name>] [--batch <N>]

# Structure validation
xml-gen validate --type skd <Template.xml> [--detailed] [--max-errors 20]
```

`output.xml` - path to Template.xml layout: `.../Templates/<Name>/Ext/Template.xml`.

## Root DSL Structure

```json
{
  "dataSets": [...],
  "calculatedFields": [...],
  "totalFields": [...],
  "parameters": [...],
  "templates": [...],
  "groupTemplates": [...],
  "dataSetLinks": [...],
  "settingsVariants": [...]
}
```

Defaults: `dataSources` → auto `ИсточникДанных1/Local`; `settingsVariants` → auto "Main" with detail records.

## Datasets

Type is determined by key: `query` → DataSetQuery, `objectName` → DataSetObject, `items` → DataSetUnion.

**DataSetQuery:** `{ "name": "...", "query": "ВЫБРАТЬ ...", "fields": [...] }`. The query can be an inline string or a file `"query": "@queries/sales.sql"` (path relative to the JSON, then CWD).
**DataSetObject:** external dataset without a query. Data is passed through `ПроцессорКомпоновкиДанных.Инициализировать(Макет, Новый Структура("<objectName>", ТЗ), …)`. Fields are described explicitly in `fields[]`. `name` is the dataset name, `objectName` is the key in the data-passing structure.
**DataSetUnion:** `{ "name": "...", "items": [...], "fields": [...] }` - merge of datasets with common fields.

## Fields - object form only

> **WARNING:** in the current CLI implementation, shorthand strings (`"Name [Title]: type @role #constraint"`) for the collections `fields`, `calculatedFields`, `totalFields`, `parameters` are **NOT supported** - the Jackson deserializer in `SkdDsl$Field`, `SkdDsl$CalculatedField`, `SkdDsl$Parameter`, `SkdDsl$TotalField` does not have a String constructor and rejects strings with the error `Cannot construct instance ... no String-argument constructor`. Use **only the object form** in all examples below, regardless of what is shown in the shorthand fragments of the documentation. The shorthand forms are left in the file as a reference description of the target semantics.

Object form of a field:
```json
{ "field": "Сумма", "title": "Сумма продажи", "type": "decimal(15,2)",
  "appearance": { "ГоризонтальноеПоложение": "Right", "МинимальнаяШирина": "80" } }
```
`dataPath` is taken from `field` if not specified explicitly.

If role/constraints are needed, use object equivalents of shorthand flags:
```json
{ "field": "Организация", "type": "CatalogRef.Организации", "role": "@dimension" }
{ "field": "Служебное", "type": "string", "restrict": ["noFilter", "noOrder"] }
```

**Title:** multilingual `"title": { "ru": "...", "en": "..." }`. Supported everywhere that accepts title/presentation.

**Types:** `string`, `string(N)`, `decimal`, `decimal(D,F)`, `boolean`, `date`, `dateTime`. `decimal` without parentheses = `decimal(10,2)`. `decimal(N)` = `decimal(N,0)`. Suffix `,nonneg` → `AllowedSign=Nonnegative`. Aliases `number`/`numeric` ≡ `decimal`.

Reference types: `CatalogRef.X`, `DocumentRef.X`, `EnumRef.X`, `ChartOfAccountsRef.X`, `StandardPeriod`. They are emitted with the inline namespace `d5p1:`. Building an EPF with reference types requires a base with a suitable configuration.

Composite type - an array in object form: `"type": ["CatalogRef.А", "CatalogRef.Б"]`. Qualifiers apply to each element.

**Roles:** `@dimension`, `@account`, `@balance`, `@period`.

**Constraints:** shorthand flags `#noField`, `#noFilter`, `#noGroup`, `#noOrder`; object form: `"restrict": ["noField", "noFilter"]`.

**Additional:** `presentationExpression` - presentation expression (the value remains available for drilldown). `appearance` - default column formatting (platform parameter keys).

## Calculated Fields (calculatedFields)

Shorthand: `"Name [Title]: type = Expression #flags"` - everything except the name is optional.
```json
"calculatedFields": [
  "Маржа = Цена - Закупка",
  "Наценка [Наценка, %]: decimal(10,2) = Маржа / Закупка * 100",
  "Служебное: string = \"\" #noField #noFilter #noGroup #noOrder"
]
```
Object form is used when `appearance` or composite settings are needed: `{ "name", "title", "expression", "type", "useRestriction" }`.

## Totals (totalFields)

Shorthand: `"totalFields": ["Quantity: Sum", "Cost: Sum(Qty * Price)"]`.

With linkage to groupings - object form:
```json
{ "dataPath": "Кол", "expression": "Сумма(Кол)", "group": ["Группа1", "Группа1 Иерархия", "ОбщийИтог"] }
```

## Parameters

Shorthand: `"Name [Title]: type = value @flags"`.

| Flag | Effect |
|------|--------|
| `@autoDates` | For `StandardPeriod` - adds derived `НачалоПериода`/`КонецПериода`. Use `&НачалоПериода`/`&КонецПериода` in the query. The parameter gets `use=Always`, `denyIncompleteValues=true`. |
| `@valueList` | `valueListAllowed=true` - allows a list of values. |
| `@hidden` | `availableAsField=false` + exclusion from `"dataParameters": "auto"`. |
| `@always` | `use=Always`. |

Object form: `title`, `hidden`, `valueListAllowed`, `availableAsField`, `denyIncompleteValues`, `use: "Always"`, `availableValues[]`.

`"dataParameters": "auto"` in a settings variant - outputs all non-hidden parameters with `userSettingID`. Parameters without a default value are disabled (the user will enable them manually).

## Filters

Shorthand: `"Field operator value @flags"`. Value `_` = empty (placeholder).

Operators: `=`, `<>`, `>`, `>=`, `<`, `<=`, `in`, `notIn`, `contains`, `filled`, `notFilled`, `InHierarchy`.

Flags: `@off` (use=false), `@user` (userSettingID=auto), `@quickAccess`, `@normal`, `@inaccessible`.

Groups:
```json
{ "group": "Or", "items": [
  { "group": "And", "items": [
    { "field": "Статус", "op": "=", "value": "Активен" },
    { "field": "Сумма",  "op": ">", "value": 1000 }
  ]},
  { "field": "Количество", "op": "filled" }
]}
```

Value types: `Перечисление.*` / `Справочник.*` / `ПланСчетов.*` / `Документ.*` → DesignTimeValue (auto-detected).

## Dataset Links (dataSetLinks)

```json
"dataSetLinks": [{ "source": "...", "target": "...",
  "items": [{ "sourceExpression": "Организация", "targetExpression": "Организация" }]
}]
```

## Variant Structure (structure)

String shorthand: `"structure": "Organization > Nomenclature > details"` (`>` separates levels, `details` = detail records).

Object form:
```json
"structure": [{ "name": "...", "groupFields": ["Organization"],
  "selection": ["Organization", "Amount", "Auto"], "children": [{ "groupFields": [] }] }]
```
`type` defaults to `"group"`. Supported: `name`, `selection`, `order`, `filter`, `outputParameters`, recursive `children`, `type: "table"`, `type: "chart"`.

## Settings Variants (settingsVariants)

```json
"settingsVariants": [{
  "name": "Основной", "title": "Продажи по организациям",
  "settings": {
    "selection": ["Номенклатура", "Количество", "Auto"],
    "filter": ["Организация = _ @off @user"],
    "order": ["Количество desc", "Auto"],
    "outputParameters": { "Заголовок": "Мой отчёт" },
    "dataParameters": ["Период = LastMonth @user"],
    "structure": "Организация > details"
  }
}]
```

`selection`: `"Auto"` = all available fields; `{ "folder": "...", "items": [...] }` → `SelectedItemFolder`.

## Conditional Formatting (conditionalAppearance)

```json
{ "selection": ["Поле1"], "filter": ["Поле1 notFilled"],
  "appearance": { "Текст": "Не указано", "ЦветТекста": "style:XXX" },
  "presentation": "...", "viewMode": "Normal", "userSettingID": "auto" }
```

Values in `appearance`: `style:XXX`/`web:XXX`/`win:XXX` → Color; `true`/`false` → Boolean; `Формат`/`Текст`/`Заголовок` → LocalStringType; the rest → String.

In `settingsVariants.settings`, it is added under the key `"conditionalAppearance": [...]`.

## Output and Group Templates

Full specification (cell syntax, styles, drilldown, groupTemplates) - [references/templates-dsl.md](references/templates-dsl.md).

## Analysis - `xml-gen skd info`

11 modes. Detailed examples of output - [references/info-modes.md](references/info-modes.md).

| Mode | Without `--name` | With `--name` |
|------|------------------|---------------|
| `overview` (default) | Schema map + hints for next steps | — |
| `query` | — | Dataset query text (with batch table of contents) |
| `fields` | Field map by datasets | Field detail: dataset, type, role, format |
| `links` | All dataset links | — |
| `calculated` | Map of calculated fields | Expression + title + constraints |
| `resources` | Map of resources (`*` = has group formulas) | Aggregation formulas by groupings |
| `params` | Parameter table (type, value, visibility) | — |
| `variant` | List of variants | Grouping structure + filters + output |
| `templates` | Map of template bindings | Template contents: rows, cells, expressions |
| `trace` | — | Full chain: dataset → calculation → resource |
| `full` | overview + query + fields + resources + params + variant | — |

Workflow: `overview` → `trace --name <field>` → `query --name <dataset>` → `variant --name <N>`. Parameters: `--mode`, `--name`, `--batch` (`0` = all batches), `--limit`/`--offset` (default 150), `--out-file`.

## Example - with external query, resources, @autoDates

> All collections are in object form (the CLI rejects shorthand strings; see warning above).

```json
{
  "dataSets": [{
    "query": "@queries/sales.sql",
    "fields": [
      {"field": "Организация",  "type": "CatalogRef.Организации", "role": "@dimension"},
      {"field": "Номенклатура", "type": "CatalogRef.Номенклатура", "role": "@dimension"},
      {"field": "Количество",   "type": "decimal(15,3)"},
      {"field": "Сумма",        "type": "decimal(15,2)"}
    ]
  }],
  "totalFields": [
    {"dataPath": "Количество", "expression": "Сумма(Количество)"},
    {"dataPath": "Сумма",      "expression": "Сумма(Сумма)"}
  ],
  "parameters": [
    {"name": "Период", "type": "StandardPeriod", "value": "LastMonth", "autoDates": true}
  ],
  "settingsVariants": [{
    "name": "Основной",
    "settings": {
      "selection": ["Организация", "Номенклатура", "Количество", "Сумма"],
      "filter": ["Организация = _ @off @user"],
      "dataParameters": "auto",
      "structure": "Организация > details"
    }
  }]
}
```

## Anti-patterns

`"filter": ["Amount greater than 0"]` - **incorrect**: the parser accepts operators strictly from the fixed set (`=`, `<>`, `>`, `>=`, `<`, `<=`, `in`, `notIn`, `contains`, `filled`, `notFilled`, `InHierarchy`). `greater` is not recognized.

---
depends_on: []
metadata:
  category: 1c-development
  version: "2.0"
---
