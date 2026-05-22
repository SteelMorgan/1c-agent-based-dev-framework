---
name: skd-dsl
description: "JSON DSL for generating and analyzing 1C data composition schemas (SKD). Use with xml-gen skd compile/info and xml-gen validate --type skd - data sets (Query/Object/Union), calculated fields, output templates, settings variants, conditional appearance."
---

# SKD DSL

## When to Use

| Trigger | Action |
|---------|----------|
| Create a report (SKD) from scratch | `xml-gen skd compile` with JSON DSL |
| External data set (without a query) | `dataSets[].objectName` (DataSetObject) |
| Combine data sets | `dataSets[].items` (DataSetUnion) |
| Calculated fields | `calculatedFields` (shorthand or object) |
| Output templates | `templates` + `groupTemplates` - see [references/templates-dsl.md](references/templates-dsl.md) |
| Cell drilldown | `parameters[].drilldown` in the template |
| Links between data sets | `dataSetLinks` |
| Understand someone else's SKD | `xml-gen skd info --mode overview` → then `trace`/`query`/`variant` |
| Check correctness | `xml-gen validate --type skd` |
| Targeted editing | `skd add-parameter` / `skd add-field` → [xml-generation](../SKILL.md) §3 |

## CLI Commands

```bash
# Компиляция: JSON DSL → Template.xml
xml-gen skd compile [--format designer|edt] <input.json> <output.xml>

# Анализ: Template.xml → компактная сводка (11 режимов)
xml-gen skd info <Template.xml> [--mode <mode>] [--name <name>] [--batch <N>]

# Валидация структуры
xml-gen validate --type skd <Template.xml> [--detailed] [--max-errors 20]
```

`output.xml` is the path to the Template.xml layout: `.../Templates/<Name>/Ext/Template.xml`.

## DSL Root Structure

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

Defaults: `dataSources` → auto `ИсточникДанных1/Local`; `settingsVariants` → auto «Main» with detailed records.

## Data Sets

The type is determined by the key: `query` → DataSetQuery, `objectName` → DataSetObject, `items` → DataSetUnion.

**DataSetQuery:** `{ "name": "...", "query": "ВЫБРАТЬ ...", "fields": [...] }`. The query can be an inline string or a file `"query": "@queries/sales.sql"` (path relative to the JSON file, then CWD).
**DataSetObject:** external data set without a query. Data is passed through `ПроцессорКомпоновкиДанных.Инициализировать(Макет, Новый Структура("<objectName>", ТЗ), …)`. Fields are described explicitly in `fields[]`. `name` is the name of the data set, `objectName` is the key in the data transfer structure.
**DataSetUnion:** `{ "name": "...", "items": [...], "fields": [...] }` - combining data sets with common fields.

## Fields - shorthand and object form

Shorthand: `"Name [Title]: type @role #restriction"`. Examples:
```
"Наименование"
"Количество: decimal(15,2)"
"Организация: CatalogRef.Организации @dimension"
"Служебное: string #noFilter #noOrder"
```

Object form:
```json
{ "field": "Сумма", "title": "Сумма продажи", "type": "decimal(15,2)",
  "appearance": { "ГоризонтальноеПоложение": "Right", "МинимальнаяШирина": "80" } }
```
`dataPath` is taken from `field` if not specified explicitly.

**Title:** multilingual `"title": { "ru": "...", "en": "..." }`. Supported everywhere that accepts title/presentation.

**Types:** `string`, `string(N)`, `decimal`, `decimal(D,F)`, `boolean`, `date`, `dateTime`. `decimal` without parentheses = `decimal(10,2)`. `decimal(N)` = `decimal(N,0)`. Suffix `,nonneg` → `AllowedSign=Nonnegative`. Aliases `number` and the Russian word for number ≡ `decimal`.

Reference types: `CatalogRef.X`, `DocumentRef.X`, `EnumRef.X`, `ChartOfAccountsRef.X`, `StandardPeriod`. Emitted with inline namespace `d5p1:`. Building EPF with reference types requires a database with a matching configuration.

Composite type is an array in object form: `"type": ["CatalogRef.А", "CatalogRef.Б"]`. Qualifiers apply to each element.

**Roles:** `@dimension`, `@account`, `@balance`, `@period`.

**Restrictions:** shorthand flags `#noField`, `#noFilter`, `#noGroup`, `#noOrder`; object form: `"restrict": ["noField", "noFilter"]`.

**Additional:** `presentationExpression` - expression for presentation (the value remains for drilldown). `appearance` - default column formatting (platform parameter keys).

## Calculated Fields (calculatedFields)

Shorthand: `"Name [Title]: type = Expression #flags"` - everything except the name is optional.
```json
"calculatedFields": [
  "Маржа = Цена - Закупка",
  "Наценка [Наценка, %]: decimal(10,2) = Маржа / Закупка * 100",
  "Служебное: string = \"\" #noField #noFilter #noGroup #noOrder"
]
```
Object form - when `appearance` or composite settings are needed: `{ "name", "title", "expression", "type", "useRestriction" }`.

## Totals (totalFields)

Shorthand: `"totalFields": ["Количество: Сумма", "Стоимость: Сумма(Кол * Цена)"]`.

Bound to groupings - object form:
```json
{ "dataPath": "Кол", "expression": "Сумма(Кол)", "group": ["Группа1", "Группа1 Иерархия", "ОбщийИтог"] }
```

## Parameters

Shorthand: `"Name [Title]: type = value @flags"`.

| Flag | Effect |
|------|--------|
| `@autoDates` | For `StandardPeriod` - adds derived `НачалоПериода`/`КонецПериода`. Use `&НачалоПериода`/`&КонецПериода` in the query. The parameter gets `use=Always`, `denyIncompleteValues=true`. |
| `@valueList` | `valueListAllowed=true` - allows a list of values. |
| `@hidden` | `availableAsField=false` + excluded from `"dataParameters": "auto"`. |
| `@always` | `use=Always`. |

Object form: `title`, `hidden`, `valueListAllowed`, `availableAsField`, `denyIncompleteValues`, `use: "Always"`, `availableValues[]`.

`"dataParameters": "auto"` in a settings variant outputs all non-hidden parameters with `userSettingID`. Parameters without a default value are disabled (the user will enable them manually).

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

Filter value types: `Перечисление.*` / `Справочник.*` / `ПланСчетов.*` / `Документ.*` → DesignTimeValue (auto-detected).

## Data Set Links (dataSetLinks)

```json
"dataSetLinks": [{ "source": "...", "target": "...",
  "items": [{ "sourceExpression": "Организация", "targetExpression": "Организация" }]
}]
```

## Variant Structure (structure)

String shorthand: `"structure": "Организация > Номенклатура > details"` (`>` separates levels, `details` = detail records).

Object form:
```json
"structure": [{ "name": "...", "groupFields": ["Организация"],
  "selection": ["Организация", "Сумма", "Auto"], "children": [{ "groupFields": [] }] }]
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

## Conditional Appearance (conditionalAppearance)

```json
{ "selection": ["Поле1"], "filter": ["Поле1 notFilled"],
  "appearance": { "Текст": "Не указано", "ЦветТекста": "style:XXX" },
  "presentation": "...", "viewMode": "Normal", "userSettingID": "auto" }
```

Values of `appearance`: `style:XXX`/`web:XXX`/`win:XXX` → Color; `true`/`false` → Boolean; `Format`/`Text`/`Title` → LocalStringType; everything else → String.

In `settingsVariants.settings`, add it under the key `"conditionalAppearance": [...]`.

## Output Templates and Groupings

The full specification (cell syntax, styles, drilldown, groupTemplates) - [references/templates-dsl.md](references/templates-dsl.md).

## Analysis - `xml-gen skd info`

11 modes. Detailed output examples - [references/info-modes.md](references/info-modes.md).

| Mode | Without `--name` | With `--name` |
|-------|--------------|-------------|
| `overview` (default) | Schema map + hints for next steps | — |
| `query` | — | Data set query text (with batch table of contents) |
| `fields` | Field map by data set | Field details: dataset, type, role, format |
| `links` | All data set links | — |
| `calculated` | Calculated field map | Expression + title + restrictions |
| `resources` | Resource map (`*` = has group formulas) | Aggregation formulas by groupings |
| `params` | Parameter table (type, value, visibility) | — |
| `variant` | List of variants | Grouping structure + filters + output |
| `templates` | Template binding map | Template content: rows, cells, expressions |
| `trace` | — | Full chain: data set → calculation → resource |
| `full` | overview + query + fields + resources + params + variant | — |

Workflow: `overview` → `trace --name <field>` → `query --name <dataset>` → `variant --name <N>`. Parameters: `--mode`, `--name`, `--batch` (`0` = all batches), `--limit`/`--offset` (default 150), `--out-file`.

## Example - with external query, resources, @autoDates

```json
{
  "dataSets": [{
    "query": "@queries/sales.sql",
    "fields": [
      "Организация: CatalogRef.Организации @dimension",
      "Номенклатура: CatalogRef.Номенклатура @dimension",
      "Количество: decimal(15,3)", "Сумма: decimal(15,2)"
    ]
  }],
  "totalFields": ["Количество: Сумма", "Сумма: Сумма"],
  "parameters": ["Период: StandardPeriod = LastMonth @autoDates"],
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

`"filter": ["Amount > 0"]` - **incorrect**: the parser accepts operators only from the fixed set (`=`, `<>`, `>`, `>=`, `<`, `<=`, `in`, `notIn`, `contains`, `filled`, `notFilled`, `InHierarchy`). `greater` is not recognized.

Fields in `selection`/`order`/`filter`/`structure` must exist in `dataSets` or `calculatedFields` - otherwise the SKD will not be composed.

**Verification after compile:** `xml-gen validate --type skd <output.xml>` → `xml-gen skd info <output.xml>` → if needed `skd info --mode trace --name <field>`.

## See Also

- [references/templates-dsl.md](references/templates-dsl.md) — templates, drilldown, styles.

---
depends_on: []
metadata:
  category: 1c-development
  version: "2.0"
---
