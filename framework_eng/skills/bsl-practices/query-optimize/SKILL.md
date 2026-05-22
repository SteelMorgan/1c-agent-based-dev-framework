---
name: query-optimize
description: "1C query and SCD optimization. Use when you need to speed up an existing query, rewrite an SCD dataset, remove an unparameterized virtual table, query-in-loop, dot-dereference, or excessive totals. Complements query-patterns (that one is for writing from scratch, this one is for optimizing an existing one)."
target_agents:
  - developer-code
  - architect
---

# Query Optimize — query and SCD optimization

A skill for optimizing **existing** queries and data composition schemas. For writing queries from scratch — `query-patterns`. For DBMS diagnostics (plan, locks, evidence) — `db-performance`.

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
- For SCD - find the `.xml` schema through `code-navigation`, identify the dataset
- Record:
  - Module / SCD dataset
  - Virtual table parameters (passed / not passed)
  - Chain of temporary tables
  - Calling loop (yes / no)
  - Expected row count / actual row count

### 2. Check metadata

Before rewriting, check the metadata object structure:
- Register: type (accumulation / information), periodicity, dimensions, resources
- Справочник / документ: attributes, tabular sections
- Indexed attributes (affects condition applicability)

Tool: `code-navigation` → object structure.

### 3. Determine one cause

Choose from the categories (one per iteration):

| Cause | Sign |
|---------|---------|
| Broad virtual table read | `Остатки()` / `Обороты()` without period or dimension parameters |
| Query-in-loop | Query inside `Для Каждого` / `Пока` / recursion |
| Dot-dereference without ВЫРАЗИТЬ | `Движения.Регистратор.Контрагент` with a composite type |
| Excess temporary tables | Intermediate tables with the full field set instead of the minimum needed |
| Excess totals | `ИТОГИ` in the query when a flat result set is needed |
| Filtering after join | `ГДЕ` conditions on large-table fields instead of virtual table parameters |
| Implicit row multiplication via JOIN | LEFT JOIN without aggregation duplicates rows |
| РАЗЛИЧНЫЕ masks the problem | `ВЫБРАТЬ РАЗЛИЧНЫЕ` hides an extra JOIN instead of fixing it |

### 4. Apply the optimization rule

For each cause, apply the concrete rule (see the "Rules" section below).

### 5. Check syntax and semantics

- Syntax: `v8-runner` after any change
- Semantics: do not silently remove `РАЗРЕШЕННЫЕ` filters; security rules must be preserved
- For join changes: make sure the row count does not change unexpectedly

### 6. Request DBMS verification if the effect is not obvious

If the change affects the DBMS plan (index, virtual table parameters, join type), ask the user for `EXPLAIN` / `ТЖ` before and after. Without measurement, record it as "expected effect, requires verification".

---

## Optimization rules

### Virtual tables: parameters inside

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

Rule: the virtual table always receives period and dimension parameters. The only exception is an explicit justification (a report for all warehouses without a filter).

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

### Dot-dereference: ВЫРАЗИТЬ for composite types

**Problem:** `Движения.Регистратор.Контрагент` with a composite type - the DBMS performs a LEFT JOIN to all tables of the composite type.

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

Rule: use `ИНДЕКСИРОВАТЬ ПО` for fields that will be joined in the next query of the package. Do not add an index to every field.

### Excess totals: replace ИТОГИ with СГРУППИРОВАТЬ ПО

`ИТОГИ` generates extra total rows. If a flat result set is needed, use `СГРУППИРОВАТЬ ПО`.

```bsl
// ИТОГИ нужны только при иерархическом обходе Выбрать(ПоГруппировкам)
// Для плоской выборки — только СГРУППИРОВАТЬ ПО
"ВЫБРАТЬ Контрагент, СУММА(Сумма) КАК Итог
|ИЗ ...
|СГРУППИРОВАТЬ ПО Контрагент"
```

### Filter on composite type: IN instead of JOIN + РАЗЛИЧНЫЕ

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

- SCD parameters passed to virtual tables of the dataset work the same as query parameters: pass them inside, do not filter through a selection afterward
- For register period conditions - always parameterize in the dataset query text

### Resources and calculated fields

- SCD calculated fields that access other datasets through links are a potential query-in-loop at the platform level
- Dataset links (`СВЯЗЬ`) with conditions without an index on the driven side - check the metadata

### SCD filters

- Filters applied by the user through settings may not reach the virtual table parameters - this is an architectural limitation; document it
- For critical filters (period, organization) - pass them as dataset query parameters, do not rely only on SCD filters

---

## Query review checklist

- [ ] Virtual tables receive period and dimension parameters (not filtering in `ГДЕ`)
- [ ] Temporary tables contain only the fields needed by subsequent stages
- [ ] Join fields in temporary tables are indexed (`ИНДЕКСИРОВАТЬ ПО`)
- [ ] There are no repeated subqueries or query-in-loop
- [ ] JOIN does not multiply rows; totals and groupings match the business meaning
- [ ] Date and organization filters are applied as early as possible
- [ ] Composite types are expanded through `ВЫРАЗИТЬ` before dot-dereference
- [ ] `ЛЕВОЕ СОЕДИНЕНИЕ` does not turn into `ВНУТРЕННЕЕ` because of a condition in `ГДЕ`
- [ ] `РАЗЛИЧНЫЕ` does not mask an extra JOIN
- [ ] `РАЗРЕШЕННЫЕ` and other permission filters are preserved

---

## Stop rules

1. **Do not remove `РАЗРЕШЕННЫЕ`** without explicit security approval.
2. **Do not recommend an index without** a specific predicate + write-cost assessment - that is a `db-performance` task.
3. **Do not rewrite multiple causes in one step** - the contribution of each one cannot be measured.
4. **Do not replace LEFT JOIN with INNER JOIN** without checking the business requirement: do all rows need to be returned, or only matching ones.
5. **Do not transfer an optimization based on one DBMS** to another without verification: PostgreSQL and MS SQL Server have different planner models.

---
depends_on:
  - framework/skills/bsl-practices/query-patterns/SKILL.md
  - framework/skills/tool-usage/diagnostics/db-performance/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
