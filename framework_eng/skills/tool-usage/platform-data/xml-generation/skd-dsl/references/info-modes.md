# SKD info — 11 analysis modes

Complete reference for `xml-gen skd info --mode <mode>`. Compact description is in [SKILL.md](../SKILL.md).

Pattern: without `--name` — map/index; with `--name` — details of a specific element.

## overview (default) — schema map

Compact navigation map (10–25 lines) with structure and next-step hints.

```
=== DCS: ОсновнаяСхемаКомпоновкиДанных (362 lines) ===

Sources: ИсточникДанных1 (Local)

Datasets:
  [Query]  НоменклатураСЦенами   7 fields, query 40 lines
Calculated: 1
Resources: 1
Templates: 1 templates, 1 group bindings
Params: (none)

Variants:
  [1] НоменклатураИЦены   "Номенклатура и цены"   Table(detail)  3 filters
  [2] НоменклатураБезЦен  "Номенклатура без цен"  Group(detail)  2 filters

Next:
  --mode query              query text
  --mode fields             field tables by dataset
  --mode calculated         calculated field expressions
  --mode resources          resource aggregation
  --mode variant --name <N> variant structure (1..2)
```

For `DataSetUnion` — dataset tree + links:

```
Datasets:
  [Union] РасчетНалогаНаИмущество  52 fields
    ├─ [Query] РасчетНалогаНаИмущество   51 fields, query 181 lines
    ├─ [Query] ДанныеПоКадастровой       29 fields, query 40 lines
    ├─ [Query] ДанныеПоСреднегодовой     34 fields, query 41 lines
Links: РасчетНалогаНаИмущество -> СостояниеОС (2 fields)
```

Parameters are split into visible/hidden:

```
Params: 18 (7 visible, 11 hidden): Период, Ответственный, ...
```

## query — dataset query text

`--name <dataset>` is required if there is more than one dataset.

Extracts raw text with XML unescaping (`&amp;` → `&`, `&gt;` → `>`). For batch queries — batch table of contents:

```
=== Query: ДанныеТ13 (334 lines, 13 batches) ===
  Batch 1: lines 1-8     → ПОМЕСТИТЬ Представления_Периоды
  Batch 2: lines 9-26    → ПОМЕСТИТЬ Представления_СотрудникиОрганизации
  ...
--- Batch 1 ---
ВЫБРАТЬ
  ДАТАВРЕМЯ(1, 1, 1) КАК Период
ПОМЕСТИТЬ Представления_Периоды
...
```

`--batch 3` — show only the third batch.

## fields — dataset fields

**Without `--name`** — map of names by dataset:

```
=== Fields map ===
СостояниеОС [Query] (3): Организация, ОсновноеСредство, ДатаСостояния
РасчетНалогаНаИмущество [Union] (52): ДоляСтоимостиЧислитель, ...
  РасчетНалогаНаИмущество [Query] (51): КадастроваяСтоимость, ...
```

**With `--name <field>`** — details:

```
=== Field: ДатаСостояния "Дата ввода в эксплуатацию" ===

Dataset: СостояниеОС [Query]
Format: ДФ=dd.MM.yyyy
```

Shows: dataset, title, type, role, useRestriction, format, presentationExpression.

## links — dataset links

```
=== Links (4) ===

РасчетНалогаНаИмущество -> СостояниеОС :
  Организация       -> Организация
  ОсновноеСредство  -> ОсновноеСредство
```

Grouped by dataset pairs. Shows link fields and parameters.

## calculated — calculated fields

**Without `--name`** — map:

```
=== Calculated fields (23) ===
  ДоляСтоимости  "Доля стоимости"
  КоэффициентКи  "Коэффициент Ки"
  ...
```

**With `--name <field>`** — full expression:

```
=== Calculated: ДоляСтоимости ===

Expression:
  ВЫБОР КОГДА ... ТОГДА "1" ИНАЧЕ ... КОНЕЦ
Title: Доля стоимости
Restrict: condition
```

## resources — resources (group totals)

**Without `--name`** — map (`*` = has group formulas):

```
=== Resources (51) ===
  НалоговаяБаза
  КоэффициентКи *
  ...
  * = has group-level formulas
```

**With `--name <field>`** — aggregation formulas:

```
=== Resource: ДатаСостояния ===

  [ОсновноеСредство] ЕстьNull(ДатаСостояния, "")
```

## params — schema parameters

```
=== Parameters (16) ===
  Name              Type                    Default      Visible  Expression
  Период            StandardPeriod          LastMonth    yes      -
  НачалоПериода     DateTime                -            hidden   &Период.ДатаНачала
  Организация       CatalogRef.Организации  null         yes      -
```

## variant — report variants

**Without `--name`** — list:

```
=== Variants (2) ===
  [1] НоменклатураИЦены   "Номенклатура и цены"   Table(detail)  3 filters
  [2] НоменклатураБезЦен  "Номенклатура без цен"  Group(detail)  2 filters
```

**With `--name <N|name>`** — variant structure:

```
=== Variant [1]: НоменклатураИЦены "Номенклатура и цены" ===

Structure:
  Table "Таблица"
  ├── Columns: [ТипЦен Items]
  │     Selection: Auto, Цена
  └── Rows: [Номенклатура Items]
        Selection: Номенклатура, УИД, Auto

Filter:
  [ ] Номенклатура InHierarchy  [user]
  [ ] ТипЦен Equal
  [x] ВАрхиве = false  "Исключая скрытые товары"

DataParams: КлючВарианта="НоменклатураИЦены"
Output: style=ЧерноБелый  groups=Separately  totalsH=None  totalsV=None
```

## templates — output template bindings

Three types: `fieldTemplate` (to a field), `groupTemplate` (to a grouping: Header/Footer), `groupHeaderTemplate` (group header).

**Without `--name`** — bindings map:

```
=== Templates (70 defined: 49 field, 37 group) ===

Field bindings (49): (all trivial)
  ОстаточнаяСтоимостьНа0101, ОстаточнаяСтоимостьНа0102, ...

Group bindings (37):
  ВидНалоговойБазы
    Header -> Макет3 (1 rows, 1 params)
  СреднегодоваяСтоимость2019
    Footer      -> Макет50 (1 rows) spacer
    GroupHeader -> Макет40 (3 rows)
```

**With `--name <grouping|field>`** — contents:

```
=== Templates: СреднегодоваяСтоимость2019 ===

Footer -> Макет50 [1 rows, 1 cells]:
  Row 1: (empty)

GroupHeader -> Макет40 [3 rows, 78 cells]:
  Row 1: "№ п/п" | "###Группировки1###" | "Инв. номер" | ...
  Row 2: "01.01" | "01.02" | ... | "31.12"
  Row 3: "1"     | "2"     | ... | "26"
```

For field bindings:

```
=== Field template: ОстаточнаяСтоимостьНа0101 -> Макет4 ===
[1 rows, 1 cells]
  Row 1: {ОстаточнаяСтоимостьНа0101}
  (all params trivial)
```

**Expression triviality**: `Field = Field` and `Field = Presentation(Field)` are trivial and are NOT shown. Only non-trivial ones are shown.

## trace — field trace from title to query

Searches for a field by dataPath OR title (including substring) and shows the full provenance chain in a single call.

```
=== Trace: КоэффициентКи "Коэффициент Ки" ===

Dataset: (schema-level only, not in dataset fields)

Calculated:
  ВЫБОР КОГДА ... ТОГДА 0 ИНАЧЕ ... КОНЕЦ
  Operands:
    КоличествоМесяцевИспользования -> РасчетНалогаНаИмущество [Query]
    КоличествоМесяцевВладения      -> РасчетНалогаНаИмущество [Query]

Resource:
  [ОсновноеСредство] Сумма(КоэффициентКи)
```

Typical scenario: the user sees the column "Coefficient Ki" in a report and asks how it is calculated. One `trace` shows the formula, operand sources, and aggregation into a resource.

The most valuable mode for an analyst and an architect.

## full — combined summary

`overview + query + fields + resources + params + variant` in one call. Useful for generating a complete SKD snapshot (for example, for an LLM agent context).

## What is not shown

- XML namespace declarations.
- Wrappers `v8:item` / `v8:lang` / `v8:content` — clean text is extracted.
- `userSettingID` (GUID of user settings).
- Default `periodAdditionBegin` / `periodAdditionEnd` = `0001-01-01`.
- `viewMode`, if it has the default value.

## Target subagents

| Subagent | Typical modes |
|----------|-----------------|
| `analyst` | `overview`, `trace`, `variant`, `params` |
| `architect` | `trace` (primary), `links`, `full` |
| `developer-code` | `query`, `fields`, `calculated`, `templates` |
| `reviewer` | `validate` + `full` / `trace` for suspicious fields |
| `explorer` | `overview` → `query` → `variant` |
