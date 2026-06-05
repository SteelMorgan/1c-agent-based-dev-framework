---
name: query-optimize
description: "MUST use WHEN you need to speed up an existing query or rewrite a DCS dataset. Provides rules for eliminating query-in-loop, dot-dereference, virtual tables without parameters, and excessive totals."
target_agents:
  - developer-code
  - architect
alwaysApply: false
---

# Query Optimize — optimization of queries and СКД

A skill for optimizing **existing** queries and data composition schemas. For writing queries from scratch - `query-patterns`. For DB diagnostics (plan, locks, evidence) - `db-performance`.

---

## Relationship with other skills

```
db-performance          ← lower evidentiary layer (DB evidence, plan, locks)
    ↓ passes query + reason
query-optimize          ← rewrite (this skill)
    ↓ uses writing rules
query-patterns          ← basic patterns (parameterization, NULL, loops)
```

Without `db-performance` evidence, optimization is a guess. If the cause is unknown, start with `db-performance`.

---

## Algorithm

### 1. Extract the query and execution context

- Find the query text: `rg "Запрос.Текст\s*=" --type-add "bsl:*.bsl" -t bsl`
- For DCS - find the `.xml` schema through `code-navigation`, determine the dataset
- Record:
  - Module / DCS dataset
  - Virtual table parameters (passed / not passed)
  - Temp table chain
  - Calling loop (yes / no)
  - Expected row count / actual

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
| Broad virtual table read | `Остатки()` / `Обороты()` without period or dimension parameters |
| Query-in-loop | Query inside `Для Каждого` / `Пока` / recursion |
| Dot-dereference without ВЫРАЗИТЬ | `Движения.Регистратор.Контрагент` when the type is composite |
| Excessive temp tables | Intermediate tables with a full field set instead of a minimal one |
| Extra totals | `ИТОГИ` in the query when a flat result set is needed |
| Filtering after join | `ГДЕ` conditions on fields of a large table instead of virtual table parameters |
| Implicit row multiplication through JOIN | LEFT JOIN without aggregation duplicates rows |
| DISTINCT masks the problem | `ВЫБРАТЬ РАЗЛИЧНЫЕ` hides an unnecessary JOIN instead of fixing it |

### 4. Apply the optimization rule

For each cause, use the specific rule (see the "Rules" section below).

### 5. Check syntax and semantics

- Syntax: `v8-runner` after any change
- Semantics: do not silently remove `РАЗРЕШЕННЫЕ` filters; preserve safety rules
- For join changes: make sure the row count has not changed unexpectedly

### 6. Request DB verification if the effect is non-obvious

If the change affects the DB plan (index, virtual table parameters, join type), ask the user for `EXPLAIN` / trace logs before and after. Without measurement, record it as "expected effect, requires verification."

---

## Optimization Rules

### Virtual tables: parameters inside

**Problem:** The DB computes all data first, then filters in `ГДЕ`.

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

Rule: the virtual table always receives period and dimension parameters. The only exception is an explicit justification (a report for all warehouses without a filter).

### Query-in-loop: one query + Correspondence

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

### Dot-dereference: ВЫРАЗИТЬ for a composite type

**Problem:** `Движения.Регистратор.Контрагент` when the type is composite - the DB performs a LEFT JOIN to all tables of the composite type.

```bsl
// ПЛОХО — N LEFT JOIN по составному типу
"ВЫБРАТЬ Движения.Регистратор.Контрагент ..."

// ХОРОШО — один JOIN
"ВЫБРАТЬ
|   ВЫРАЗИТЬ(Движения.Регистратор КАК Документ.РеализацияТоваровУслуг).Контрагент КАК Контрагент
|...
|ГДЕ Движения.Регистратор ССЫЛКА Документ.РеализацияТоваровУслуг"
```

### Temporary tables: minimal fields and ИНДЕКСИРОВАТЬ

```bsl
// Только поля, нужные следующим этапам
"ВЫБРАТЬ
|   Реализация.Ссылка КАК Документ,
|   Реализация.Контрагент КАК Контрагент
|ПОМЕСТИТЬ втРеализации
|...
|ИНДЕКСИРОВАТЬ ПО Контрагент"  // только поле соединения
```

Rule: use `ИНДЕКСИРОВАТЬ ПО` for fields that will be used in a JOIN in the next query of the package. Do not add an index on every field.

### Extra totals: replace ИТОГИ with GROUP BY

`ИТОГИ` generates extra total rows. If a flat result set is needed, use `СГРУППИРОВАТЬ ПО`.

```bsl
// ИТОГИ нужны только при иерархическом обходе Выбрать(ПоГруппировкам)
// Для плоской выборки — только СГРУППИРОВАТЬ ПО
"ВЫБРАТЬ Контрагент, СУММА(Сумма) КАК Итог
|ИЗ ...
|СГРУППИРОВАТЬ ПО Контрагент"
```

### Filter on a composite type: IN instead of JOIN + DISTINCT

```bsl
// ПЛОХО — JOIN умножает строки, РАЗЛИЧНЫЕ скрывает
"ВЫБРАТЬ РАЗЛИЧНЫЕ Контрагенты.Ссылка
|ИЗ Справочник.Контрагенты КАК Контрагенты
|   ВНУТРЕННЕЕ СОЕДИНЕНИЕ Документ.Реализация КАК Реал
|   ПО Контрагенты.Ссылка = Реал.Контрагент"

// ХОРОШО — подзапрос
"ВЫБРАТЬ Контрагенты.Ссылка
|ИЗ Справочник.Контрагенты КАК Контрагенты
|ГДЕ Контрагенты.Ссылка В
|       (ВЫБРАТЬ РАЗЛИЧНЫЕ Реал.Контрагент
|        ИЗ Документ.Реализация КАК Реал)"
```

---

## DCS: optimization specifics

### Dataset parameters

- DCS parameters passed into dataset virtual tables work the same as query parameters: pass them inside, do not filter through selection afterward
- For register period conditions - always parameterize them in the dataset query text

### Resources and calculated fields

- DCS calculated fields that access other datasets through relations are a potential query-in-loop at the platform level
- Dataset relations (`СВЯЗЬ`) with conditions and no index on the detail side - check metadata

### DCS filters

- Filters applied by the user through settings may not reach virtual table parameters - this is an architectural limitation; document it
- For critical filters (period, organization) - pass them as dataset query parameters, do not rely only on DCS filters

---

## Query review checklist

- [ ] Virtual tables receive period and dimension parameters (not filtering in `ГДЕ`)
- [ ] Temp tables contain only the fields needed for later stages
- [ ] Join fields in temp tables are indexed (`ИНДЕКСИРОВАТЬ ПО`)
- [ ] No repeated subqueries or query-in-loop
- [ ] JOIN does not multiply rows; totals and groupings match the business meaning
- [ ] Date and organization filters are applied as early as possible
- [ ] Composite types are expanded through `ВЫРАЗИТЬ` before dot-dereference
- [ ] `ЛЕВОЕ СОЕДИНЕНИЕ` does not turn into `ВНУТРЕННЕЕ` because of a condition in `ГДЕ`
- [ ] `РАЗЛИЧНЫЕ` does not mask an unnecessary JOIN
- [ ] `РАЗРЕШЕННЫЕ` and other rights filters are preserved

---

## Stop rules

1. **Do not remove `РАЗРЕШЕННЫЕ`** without explicit security approval.
2. **Do not recommend an index without** a concrete predicate + write-cost assessment - that is a `db-performance` task.
3. **Do not rewrite multiple causes in one step** - it is impossible to measure each contribution.
4. **Do not replace LEFT JOIN with INNER JOIN** without checking the business requirement: are all rows needed, or only matching ones.
5. **Do not transfer an optimization based on one DBMS's data behavior** to another without verification: PostgreSQL and MS SQL Server have different planner models.

---
depends_on:
  - framework/skills/bsl-practices/query-patterns/SKILL.md
  - framework/skills/tool-usage/diagnostics/db-performance/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
