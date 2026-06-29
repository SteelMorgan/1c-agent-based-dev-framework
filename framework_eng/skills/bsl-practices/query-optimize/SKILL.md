---
name: query-optimize
description: "Optimize slow 1C queries and DCS datasets"
target_agents:
  - developer-code
  - architect
alwaysApply: false
---

# Query Optimize — query and SKD optimization

Skill for optimizing **existing** queries and data composition schemas. For writing queries from scratch — `query-patterns`. For DBMS diagnostics (plan, locks, evidence) — `db-performance`.

---

## Relationship with other skills

```
db-performance          ← lower evidence layer (DBMS-evidence, plan, locks)
    ↓ passes query + reason
query-optimize          ← rewriting (this skill)
    ↓ uses writing rules
query-patterns          ← basic patterns (parameterization, NULL, loops)
```

Without `db-performance` evidence, optimization is a guess. If the cause is unknown, start with `db-performance`.

---

## Algorithm

### 1. Extract the query and execution context

- Find the query text: `rg "Запрос.Текст\s*=" --type-add "bsl:*.bsl" -t bsl`
- For SKD — find the `.xml` schemas via `code-navigation`, determine the dataset
- Record:
  - Module / SKD dataset
  - Virtual table parameters (passed / not passed)
  - Temporary table chain
  - Calling loop (yes / no)
  - Expected number of rows / actual

### 2. Check metadata

Before rewriting, check the metadata object structure:
- Register: type (accumulation / information), periodicity, dimensions, resources
- Catalog / document: attributes, tabular sections
- Indexed attributes (affects condition applicability)

Tool: `code-navigation` → object structure.

### 3. Determine one cause

Choose from the categories (one per iteration):

| Cause | Sign |
|---------|---------|
| Broad virtual table read | balance/turnover virtual table without period or dimension parameters |
| Query-in-loop | Query inside a `for each` / `while` loop / recursion |
| Dot-dereference without explicit casting | `Движения.Регистратор.Контрагент` when the type is composite |
| Excessive temporary tables | Intermediate tables with the full field set instead of the minimum |
| Unnecessary totals | totals in a query when a flat result set is needed |
| Filtering after join | `WHERE` conditions on fields of a large table instead of virtual table parameters |
| Implicit row multiplication through JOIN | LEFT JOIN without aggregation duplicates rows |
| `DISTINCT` masks the problem | `ВЫБРАТЬ РАЗЛИЧНЫЕ` hides an unnecessary JOIN instead of fixing it |

### 4. Apply the optimization rule

For each cause — a specific rule (see the "Rules" section below).

### 5. Check syntax and semantics

- Syntax: `v8-runner` after any change
- Semantics: do not silently remove permission filters; security rules must remain intact
- For join changes: make sure the row count did not change unexpectedly

### 6. Request DBMS verification if the effect is not obvious

If the change affects the DBMS plan (index, virtual table parameters, join type), ask the user for `EXPLAIN` / log before and after. Without measurement, record it as an "expected effect, requires verification."

---

## Optimization Rules

### Virtual tables: pass parameters inside

**Problem:** The DBMS calculates all data first, then filters in `WHERE`.

```bsl
// ПЛОХО — фильтр в WHERE, СУБД читает всё
"ИЗ РегистрНакопления.ТоварыНаСкладах.Остатки КАК Ост
|ГДЕ Ост.Номенклатура В (&Список)"

// ХОРОШО — параметры внутри виртуальной таблицы
"ИЗ РегистрНакопления.ТоварыНаСкладах.Остатки(
|       &ДатаОстатков,
|       Номенклатура В (&Список) И Склад = &Склад
|   ) КАК Ост"
```

Rule: a virtual table always receives period and dimension parameters. The only exception is when there is an explicit justification (a report across all warehouses without a filter).

### Query-in-loop: one query + a map

**Problem:** N iterations × (latency + query time).

```bsl
// ПЛОХО — запрос в цикле
Для Каждого Строка Из Документ.Товары Цикл
    Запрос.УстановитьПараметр("Ном", Строка.Номенклатура);
    // ... выполнить запрос
КонецЦикла;

// ХОРОШО — один запрос, Соответствие для доступа
МассивНом = Документ.Товары.ВыгрузитьКолонку("Номенклатура");
Запрос.УстановитьПараметр("Список", МассивНом);
// ... один запрос
РезСоответствие = Новый Соответствие;
Пока Выборка.Следующий() Цикл
    РезСоответствие.Вставить(Выборка.Ссылка, Выборка);
КонецЦикла;

Для Каждого Строка Из Документ.Товары Цикл
    Данные = РезСоответствие.Получить(Строка.Номенклатура);
КонецЦикла;
```

### Dot-dereference: explicit casting for a composite type

**Problem:** `Движения.Регистратор.Контрагент` for a composite type means the DBMS makes a LEFT JOIN to all tables of the composite type.

```bsl
// ПЛОХО — N LEFT JOIN по составному типу
"ВЫБРАТЬ Движения.Регистратор.Контрагент ..."

// ХОРОШО — один JOIN
"ВЫБРАТЬ
|   ВЫРАЗИТЬ(Движения.Регистратор КАК Документ.РеализацияТоваровУслуг).Контрагент КАК Контрагент
|...
|ГДЕ Движения.Регистратор ССЫЛКА Документ.РеализацияТоваровУслуг"
```

### Temporary tables: minimal fields and indexes

```bsl
// Только поля, нужные следующим этапам
"ВЫБРАТЬ
|   Реализация.Ссылка КАК Документ,
|   Реализация.Контрагент КАК Контрагент
|ПОМЕСТИТЬ втРеализации
|...
|ИНДЕКСИРОВАТЬ ПО Контрагент"  // только поле соединения
```

Rule: add indexes for fields that will be JOINed in the next query of the package. Do not add an index on every field.

### Unnecessary totals: replace totals with `GROUP BY`

Totals generate extra summary rows. If a flat result set is needed, use `GROUP BY`.

```bsl
// Totals are needed only for hierarchical traversal
// For a flat selection — only GROUP BY
"ВЫБРАТЬ Контрагент, СУММА(Сумма) КАК Итог
|ИЗ ...
|СГРУППИРОВАТЬ ПО Контрагент"
```

### Filter on a composite type: `IN` instead of JOIN + `DISTINCT`

```bsl
// BAD — JOIN multiplies rows, DISTINCT hides it
"ВЫБРАТЬ РАЗЛИЧНЫЕ Контрагенты.Ссылка
|ИЗ Справочник.Контрагенты КАК Контрагенты
|   ВНУТРЕННЕЕ СОЕДИНЕНИЕ Документ.Реализация КАК Реал
|   ПО Контрагенты.Ссылка = Реал.Контрагент"

// GOOD — subquery
"ВЫБРАТЬ Контрагенты.Ссылка
|ИЗ Справочник.Контрагенты КАК Контрагенты
|ГДЕ Контрагенты.Ссылка В
|       (ВЫБРАТЬ РАЗЛИЧНЫЕ Реал.Контрагент
|        ИЗ Документ.Реализация КАК Реал)"
```

---

## SKD: optimization specifics

### Dataset parameters

- SKD parameters passed into dataset virtual tables work the same way as query parameters: pass them inside, do not filter through a selection after the fact
- For register period conditions — always parameterize them in the dataset query text

### Resources and calculated fields

- SKD calculated fields that access other datasets through links are a potential query-in-loop at the platform level
- Dataset links with conditions and no index on the dependent side — check metadata

### SKD selections

- Selections applied by the user through settings may not reach virtual table parameters — this is an architectural limitation; document it
- For critical filters (period, organization) — pass them as dataset query parameters, do not rely only on SKD selections

---

## Query Review Checklist

- [ ] Virtual tables receive period and dimension parameters (not filtering in `WHERE`)
- [ ] Temporary tables contain only fields needed by later stages
- [ ] Join fields in temporary tables are indexed
- [ ] There are no repeated subqueries or query-in-loop patterns
- [ ] JOIN does not multiply rows; totals and groupings match the business meaning
- [ ] Date and organization filters are applied as early as possible
- [ ] Composite types are expanded before dot-dereference
- [ ] LEFT JOIN does not turn into INNER JOIN because of a WHERE condition
- [ ] DISTINCT does not hide an unnecessary JOIN
- [ ] Permission filters and other access filters are preserved

---

## Stop Rules

1. **Do not remove permission filters** without explicit security approval.
2. **Do not recommend an index without** a specific predicate + write-cost assessment — that is a `db-performance` task.
3. **Do not rewrite multiple causes in one step** — you cannot measure each contribution.
4. **Do not replace LEFT JOIN with INNER JOIN** without checking the business requirement: do all rows need to be kept, or only matching ones.
5. **Do not transfer an optimization based on one DBMS** to another without verification: PostgreSQL and MS SQL Server have different planner models.

---
depends_on:
  - framework/skills/bsl-practices/query-patterns/SKILL.md
  - framework/skills/tool-usage/diagnostics/db-performance/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
