---
name: skd-dsl
description: "xml-gen SKD schemas from JSON DSL"
---

# SKD DSL

## When to use

| Trigger | Action |
|---------|----------|
| Create a report (SKD) from scratch | `xml-gen skd compile` with JSON DSL |
| External dataset (without a query) | `dataSets[].objectName` (DataSetObject) |
| Combine datasets | `dataSets[].items` (DataSetUnion) |
| Calculated fields | `calculatedFields` (shorthand or object) |
| Output templates | `templates` + `groupTemplates` — see [references/templates-dsl.md](references/templates-dsl.md) |
| Drill down into cells | `parameters[].drilldown` in the template |
| Links between datasets | `dataSetLinks` |
| Understand another SKD | `xml-gen skd info --mode overview` → then `trace`/`query`/`variant` |
| Check correctness | `xml-gen validate --type skd` |
| Targeted editing | `skd add-parameter` / `skd add-field` → [xml-generation](../SKILL.md) §3 |

## CLI commands

```bash
# Compilation: JSON DSL → Template.xml
xml-gen skd compile [--format designer|edt] <input.json> <output.xml>

# Analysis: Template.xml → compact summary (11 modes)
xml-gen skd info <Template.xml> [--mode <mode>] [--name <name>] [--batch <N>] [--outfile <path>]

# Structure validation
xml-gen validate --type skd <Template.xml> [--detailed] [--max-errors 20]
```

`output.xml` is the path to the Template.xml layout: `.../Templates/<Name>/Ext/Template.xml`.

## Root DSL structure

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

Defaults: `dataSources` → auto `ИсточникДанных1/Local`; `settingsVariants` → auto "Основной" with detailed records.

## Data sets

The type is determined by the key: `query` → DataSetQuery, `objectName` → DataSetObject, `items` → DataSetUnion.

**DataSetQuery:** `{ "name": "...", "query": "ВЫБРАТЬ ...", "fields": [...] }`. The query is either an inline string or a file `"query": "@queries/sales.sql"` (path relative to JSON, then CWD).
**DataSetObject:** external dataset without a query. Data is passed through `ПроцессорКомпоновкиДанных.Инициализировать(Макет, Новый Структура("<objectName>", ТЗ), …)`. Fields are described explicitly in `fields[]`. `name` is the dataset name, `objectName` is the key in the data transfer structure.
**DataSetUnion:** `{ "name": "...", "items": [...], "fields": [...] }` — a union of datasets with common fields.

## Fields - object form only

> **WARNING:** in the current CLI implementation, shorthand strings (`"Name [Title]: type @role #restriction"`) for the `fields`, `calculatedFields`, `totalFields`, `parameters` collections are **NOT supported** - the Jackson deserializer for `SkdDsl$Field`, `SkdDsl$CalculatedField`, `SkdDsl$Parameter`, `SkdDsl$TotalField` has no String constructor and rejects strings with the error `Cannot construct instance ... no String-argument constructor`. Use **only object form** in all examples below, regardless of what is shown in the shorthand fragments of the documentation. Shorthand forms are left in the file as a reference description of the target semantics.

Object form of a field:
```json
{ "field": "Сумма", "title": "Сумма продажи", "type": "decimal(15,2)",
  "appearance": { "ГоризонтальноеПоложение": "Right", "МинимальнаяШирина": "80" } }
```
`dataPath` is taken from `field` if not specified explicitly.

If role/restrictions are needed, use the object equivalents of the shorthand flags:
```json
{ "field": "Организация", "type": "CatalogRef.Организации", "role": "@dimension" }
{ "field": "Служебное", "type": "string", "restrict": ["noFilter", "noOrder"] }
```

**Title:** multilingual `"title": { "ru": "...", "en": "..." }`. Supported everywhere `title/presentation` is accepted.

**Types:** `string`, `string(N)`, `decimal`, `decimal(D,F)`, `boolean`, `date`, `dateTime`. `decimal` without parentheses = `decimal(10,2)`. `decimal(N)` = `decimal(N,0)`. Suffix `,nonneg` → `AllowedSign=Nonnegative`. Aliases `number`/`число` ≡ `decimal`.

Reference types: `CatalogRef.X`, `DocumentRef.X`, `EnumRef.X`, `ChartOfAccountsRef.X`, `StandardPeriod`. They are emitted with the inline namespace `d5p1:`. Building an EPF with reference types requires a database with a compatible configuration.

A composite type is an array in object form: `"type": ["CatalogRef.А", "CatalogRef.Б"]`. Qualifiers are applied to each element.

**Roles:** `@dimension`, `@account`, `@balance`, `@period` (object form - `"role": "@dimension"`). The canonical full catalog of roles, complex balance/period/account roles and their kv parameters (`balanceGroupName`, `balanceType`, `parentDimension`, `accountTypeExpression`, `periodNumber`, `periodType`, `@required`, `@autoOrder`, `@ignoreNullValues`) is in [skd-edit → fields.md § set-field-role](../../skd-edit/references/fields.md#set-field-role).

**Restrictions:** shorthand flags `#noField`, `#noFilter`, `#noGroup`, `#noOrder`; object form: `"restrict": ["noField", "noFilter"]`.

**Additional:** `presentationExpression` is a presentation expression (the value is retained for decoding). `appearance` is the default column styling (platform parameter keys).

## Computed fields (calculatedFields)

Shorthand: `"Имя [Заголовок]: тип = Выражение #флаги"` — everything except the name is optional.
```json
"calculatedFields": [
  "Маржа = Цена - Закупка",
  "Наценка [Наценка, %]: decimal(10,2) = Маржа / Закупка * 100",
  "Служебное: string = \"\" #noField #noFilter #noGroup #noOrder"
]
```
Object form is used when `appearance` or composite settings are needed: `{ "name", "title", "expression", "type", "useRestriction" }`.

## Totals (totalFields)

Shorthand: `"totalFields": ["Количество: Сумма", "Стоимость: Сумма(Кол * Цена)"]`.

With grouping bindings, use the object form:
```json
{ "dataPath": "Кол", "expression": "Сумма(Кол)", "group": ["Группа1", "Группа1 Иерархия", "ОбщийИтог"] }
```

## Parameters

Shorthand: `"Имя [Заголовок]: тип = значение @флаги"`.

| Flag | Effect |
|------|--------|
| `@autoDates` | For `StandardPeriod` — adds derived `НачалоПериода`/`КонецПериода`. Use `&НачалоПериода`/`&КонецПериода` in the query. The parameter gets `use=Always`, `denyIncompleteValues=true`. |
| `@valueList` | `valueListAllowed=true` — allows a value list. |
| `@hidden` | `availableAsField=false` + exclusion from `"dataParameters": "auto"`. |
| `@always` | `use=Always`. |

Object form: `title`, `hidden`, `valueListAllowed`, `availableAsField`, `denyIncompleteValues`, `use: "Always"`, `availableValues[]`.

> The table above shows the **DSL effect** of flags (which XML attributes are emitted during generation). The canonical behavior of flags at the level of editing a finished report, `availableValue=`, quotation marks, and edge-cases is described in [skd-edit → parameters.md](../../skd-edit/references/parameters.md). NB: the derived parameters of `@autoDates` in DSL are `НачалоПериода`/`КонецПериода`, while in CLI editing they are `ДатаНачала`/`ДатаОкончания`.

The default value can be a list: `"value": ["Справочник.X.A", "Справочник.X.B"]`. In XML this becomes several `<value>` elements in a row; `valueListAllowed=true` is set automatically. Reference values use `dcscor:DesignTimeValue`.

`"dataParameters": "auto"` in the settings variant outputs all non-hidden parameters with `userSettingID`. Parameters without a default value are disabled (the user will enable them manually).

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

Value types: `Перечисление.*` / `Справочник.*` / `ПланСчетов.*` / `Документ.*` → DesignTimeValue (auto-detect).

## Data set links (dataSetLinks)

```json
"dataSetLinks": [{ "source": "...", "target": "...",
  "items": [{ "sourceExpression": "Организация", "targetExpression": "Организация" }]
}]
```

## Variant structure (structure)

String shorthand: `"structure": "Организация > Номенклатура > details"` (`>` separates levels, `details`/`детали` = detail records).

Object form:
```json
"structure": [{ "name": "...", "groupFields": ["Организация"],
  "selection": ["Организация", "Сумма", "Auto"], "children": [{ "groupFields": [] }] }]
```
`type` defaults to `"group"`. Supported: `name`, `selection`, `order`, `filter`, `outputParameters`, recursive `children`, `type: "table"`, `type: "chart"`.

## Settings variants (settingsVariants)

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

## Conditional formatting (conditionalAppearance)

```json
{ "selection": ["Поле1"], "filter": ["Поле1 notFilled"],
  "appearance": { "Текст": "Не указано", "ЦветТекста": "style:XXX" },
  "presentation": "...", "viewMode": "Normal", "userSettingID": "auto" }
```

Values of `appearance`: `style:XXX`/`web:XXX`/`win:XXX` → Color; `true`/`false` → Boolean; `Формат`/`Текст`/`Заголовок` → LocalStringType; otherwise → String.

A key `"conditionalAppearance": [...]` is added in `settingsVariants.settings`.

## Output and grouping templates

Full specification (cell syntax, styles, drilldown, groupTemplates) — [references/templates-dsl.md](references/templates-dsl.md).

## Analysis — `xml-gen skd info`

11 modes. Detailed output examples — [references/info-modes.md](references/info-modes.md).

| Mode | Without `--name` | With `--name` |
|-------|--------------|-------------|
| `overview` (default) | Schema map + next-step hints | — |
| `query` | — | Dataset query text (with batch table of contents) |
| `fields` | Field map by datasets | Field detail: dataset, type, role, format |
| `links` | All dataset links | — |
| `calculated` | Calculated field map | Expression + title + constraints |
| `resources` | Resource map (`*` = has group formulas) | Aggregation formulas by groupings |
| `params` | Parameter table (type, value, visibility) | — |
| `variant` | List of variants | Grouping structure + filters + output |
| `templates` | Template binding map | Template contents: rows, cells, expressions |
| `trace` | — | Full chain: dataset → calculation → resource |
| `full` | overview + query + fields + resources + params + variant | — |

Workflow: `overview` → `trace --name <field>` → `query --name <dataset>` → `variant --name <N>`. Parameters: `--mode`, `--name`, `--batch N` (1-based, `query` only; without the flag all batches are output), `--raw` (verbatim query), `--limit`/`--offset` (default 150), `--outfile <path>`/`-OutFile <path>`.

## Example — with an external query, resources, @autoDates

> All collections use object form (CLI shorthand strings are rejected, see warning above).

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

`"filter": ["Сумма больше 0"]` — **incorrect**: the parser accepts operators only from a fixed set (`=`, `<>`, `>`, `>=`, `<`, `<=`, `in`, `notIn`, `contains`, `filled`, `notFilled`, `InHierarchy`). `больше` is not recognized.

Fields in `selection`/`order`/`filter`/`structure` must exist in `dataSets` or `calculatedFields` — otherwise the SKD will not compile.

**Verification after compile:** `xml-gen validate --type skd <output.xml>` → `xml-gen skd info <output.xml>` → if needed `skd info --mode trace --name <field>`.

## See also

- [references/templates-dsl.md](references/templates-dsl.md) — templates, drilldown, styles.
- [references/info-modes.md](references/info-modes.md) — 11 `skd info` modes with output examples.
- [xml-generation](../SKILL.md) — `skd add-parameter`, `skd add-field`, replace-text.
- [mxl-dsl](../mxl-dsl/) — print forms.

---
depends_on: []
metadata:
  category: 1c-development
  version: "2.0"
---
