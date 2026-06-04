---
name: query-optimize
description: "1С query and SCD optimization. Use when you need to speed up an existing query, rewrite an SCD dataset, eliminate a virtual table without parameters, query-in-loop, dot-dereference, or excessive totals. Complements query-patterns (that one is for writing from scratch, this one is for optimizing existing queries)."
target_agents:
  - developer-code
  - architect
alwaysApply: false
---

# Query Optimize — query and SCD optimization

A skill for optimizing **existing** queries and data composition schemas. For writing queries from scratch use `query-patterns`. For DBMS diagnostics (plan, locks, evidence) use `db-performance`.

---

## Relationship with other skills

```
db-performance          ← lower evidence layer (DBMS evidence, plan, locks)
    ↓ passes the query + reason
query-optimize          ← rewriting (this skill)
    ↓ uses writing rules
query-patterns          ← basic patterns (parameterization, NULL, loops)
```

Without `db-performance` evidence, optimization is a guess. If the cause is unknown, start with `db-performance`.

---

## Algorithm

### 1. Extract the query and execution context

- Find the query text: `rg "Запрос.Текст\s*=" --type-add "bsl:*.bsl" -t bsl`
- For SCD, find the `.xml` schema through `code-navigation` and identify the dataset
- Record:
  - Module / SCD dataset
  - Virtual table parameters (passed / not passed)
  - Temporary table chain
  - Calling loop (yes / no)
  - Expected row count / actual

### 2. Check metadata

Before rewriting, check the metadata object structure:
- Register: type (accumulation / information), periodicity, dimensions, resources
- Catalog / document: attributes, tabular sections
- Indexed attributes (affects whether conditions can be applied)

Tool: `code-navigation` → object structure.

### 3. Identify one cause

Choose from the categories below (one per iteration):

| Cause | Symptom |
|---------|---------|
| Broad read of a virtual table | `Остатки()` / `Обороты()` without period or dimension parameters |
| Query-in-loop | Query inside `Для Каждого` / `Пока` / recursion |
| Dot-dereference without `ВЫРАЗИТЬ` | `Движения.Регистратор.Контрагент` for a composite type |
| Excessive temporary tables | Intermediate tables with the full field set instead of the minimum |
| Redundant totals | `ИТОГИ` in a query when a flat result set is needed |
| Filtering after join | `ГДЕ` conditions on fields from a large table instead of virtual table parameters |
| Implicit row multiplication through JOIN | LEFT JOIN without aggregation duplicates rows |
| `РАЗЛИЧНЫЕ` masks the problem | `ВЫБРАТЬ РАЗЛИЧНЫЕ` hides an extra JOIN instead of fixing it |

### 4. Apply the optimization rule

For each cause, use the specific rule below (see the "Rules" section).

### 5. Check syntax and semantics

- Syntax: `v8-runner` after any change
- Semantics: do not silently remove `РАЗРЕШЕННЫЕ` filters; keep security rules intact
- For join changes: make sure the row count did not change unexpectedly

### 6. Request DBMS verification when the effect is not obvious

If the change affects the DBMS plan (index, virtual table parameters, join type), ask the user for `EXPLAIN` / tech log before and after. Without measurement, mark it as "expected effect, requires verification".

---

## Optimization Rules

### Virtual tables: push parameters inside

**Problem:** The DBMS computes all data first, then filters in `ГДЕ`.

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

Rule: a virtual table always gets period and dimension parameters. The only exception is when there is explicit justification (a report across all warehouses with no filter).

### Query-in-loop: one query + `Соответствие`

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

### Dot-dereference: `ВЫРАЗИТЬ` for a composite type

**Problem:** `Движения.Регистратор.Контрагент` on a composite type causes the DBMS to do a LEFT JOIN to all tables in the composite type.

```bsl
// ПЛОХО — N LEFT JOIN по составному типу
"ВЫБРАТЬ Движения.Регистратор.Контрагент ..."

// ХОРОШО — один JOIN
"ВЫБРАТЬ
|   ВЫРАЗИТЬ(Движения.Регистратор КАК Документ.РеализацияТоваровУслуг).Контрагент КАК Контрагент
|...
|ГДЕ Движения.Регистратор ССЫЛКА Документ.РеализацияТоваровУслуг"
```

### Temporary tables: minimal fields and `ИНДЕКСИРОВАТЬ ПО`

```bsl
// Только поля, нужные следующим этапам
"ВЫБРАТЬ
|   Реализация.Ссылка КАК Документ,
|   Реализация.Контрагент КАК Контрагент
|ПОМЕСТИТЬ втРеализации
|...
|ИНДЕКСИРОВАТЬ ПО Контрагент"  // только поле соединения
```

Rule: use `ИНДЕКСИРОВАТЬ ПО` for fields that will be used in a JOIN in the next query in the batch. Do not add an index to every field.

### Redundant totals: replace `ИТОГИ` with `СГРУППИРОВАТЬ ПО`

`ИТОГИ` generates extra summary rows. If a flat result set is needed, use `СГРУППИРОВАТЬ ПО`.

```bsl
// ИТОГИ нужны только при иерархическом обходе Выбрать(ПоГруппировкам)
// Для плоской выборки — только СГРУППИРОВАТЬ ПО
"ВЫБРАТЬ Контрагент, СУММА(Сумма) КАК Итог
|ИЗ ...
|СГРУППИРОВАТЬ ПО Контрагент"
```

### Composite-type filter: `IN` instead of JOIN + `РАЗЛИЧНЫЕ`

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

## SCD: optimization specifics

### Dataset parameters

- SCD parameters passed into virtual tables of the dataset work the same way as query parameters: pass them inside, do not filter through selections afterward
- For register period conditions, always parameterize them in the dataset query text

### Resources and calculated fields

- SCD calculated fields that access other datasets through relations are a potential query-in-loop at the platform level
- Dataset relations (`СВЯЗЬ`) with conditions and no index on the dependent side — check the metadata

### SCD filters

- Filters applied by the user through settings may not reach virtual table parameters - this is an architectural limitation; document it
- For critical filters (period, organization), pass them as dataset query parameters; do not rely only on SCD filters

---

## Query review checklist

- [ ] Virtual tables receive period and dimension parameters (not filtering in `ГДЕ`)
- [ ] Temporary tables contain only the fields needed by the next steps
- [ ] Join fields in temporary tables are indexed (`ИНДЕКСИРОВАТЬ ПО`)
- [ ] There are no repeated subqueries or query-in-loop
- [ ] JOIN does not multiply rows; totals and groupings match the business meaning
- [ ] Date and organization filters are applied as early as possible
- [ ] Composite types are expanded with `ВЫРАЗИТЬ` before dot-dereference
- [ ] `ЛЕВОЕ СОЕДИНЕНИЕ` does not turn into `ВНУТРЕННЕЕ` because of a condition in `ГДЕ`
- [ ] `РАЗЛИЧНЫЕ` does not mask an extra JOIN
- [ ] `РАЗРЕШЕННЫЕ` and other rights filters are preserved

---

## Stop rules

1. **Do not remove `РАЗРЕШЕННЫЕ`** without explicit security agreement.
2. **Do not recommend an index without** a specific predicate + write-cost estimate - that is `db-performance`'s job.
3. **Do not rewrite multiple causes in one step** - you cannot measure each contribution.
4. **Do not replace LEFT JOIN with INNER JOIN** without checking the business requirement: do we need all rows or only matching ones.
5. **Do not transfer an optimization based on one DBMS's data to another** without verification: PostgreSQL and MS SQL Server have different planner models.

---
depends_on:
  - framework/skills/bsl-practices/query-patterns/SKILL.md
  - framework/skills/tool-usage/diagnostics/db-performance/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
