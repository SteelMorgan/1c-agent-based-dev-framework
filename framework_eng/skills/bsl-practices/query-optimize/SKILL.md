---
name: query-optimize
description: "For optimizing slow 1C queries and DCS datasets"
target_agents:
  - developer-code
  - architect
alwaysApply: false
---

# Query Optimize — query and DCS optimization

A skill for optimizing **existing** queries and data composition schemas. For writing queries from scratch, use `query-patterns`. For DBMS diagnostics (plan, locks, evidence), use `db-performance`.

---

## Relationship with other skills

```
db-performance          ← lower evidence layer (DBMS evidence, plan, locks)
    ↓ passes query + reason
query-optimize          ← rewriting (this skill)
    ↓ uses writing rules
query-patterns          ← basic patterns (parameterization, NULL, loops)
```

Without `db-performance` evidence, optimization is a guess. If the reason is unknown, start with `db-performance`.

---

## Algorithm

### 1. Extract the query and execution context

- Find the query text: `rg "Запрос.Текст\s*=" --type-add "bsl:*.bsl" -t bsl`
- For DCS, find the scheme `.xml` via `code-navigation`, determine the dataset
- Record:
  - Module / DCS dataset
  - Virtual table parameters (passed / not passed)
  - Temporary table chain
  - Calling loop (yes / no)
  - Expected row count / actual

### 2. Check metadata

Before rewriting, check the metadata object structure:
- Register: type (accumulation / information), periodicity, dimensions, resources
- Catalog / document: attributes, tabular sections
- Indexed attributes (affects condition applicability)

Tool: `code-navigation` → object structure.

### 3. Determine one reason

Choose from the categories (one per iteration):

| Reason | Sign |
|---------|---------|
| Broad virtual table read | `Остатки()` / `Обороты()` without period or dimension parameters |
| Query-in-loop | Query inside `Для Каждого` / `Пока` / recursion |
| Dot-dereference without ВЫРАЗИТЬ | `Движения.Регистратор.Контрагент` for a composite type |
| Excess temporary tables | Intermediate tables with the full field set instead of the minimum |
| Extra totals | `ИТОГИ` in a query when a flat result set is needed |
| Filtering after join | `ГДЕ` conditions on fields of a large table instead of virtual table parameters |
| Implicit row multiplication via JOIN | LEFT JOIN without aggregation duplicates rows |
| DISTINCT masks the problem | `ВЫБРАТЬ РАЗЛИЧНЫЕ` hides an extra JOIN instead of fixing it |

### 4. Apply the optimization rule

For each cause, use a specific rule (see the “Rules” section below).

### 5. Check syntax and semantics

- Syntax: run `v8-runner` after any change
- Semantics: do not silently remove `РАЗРЕШЕННЫЕ` filters; security rules must remain intact
- For join changes: make sure the row count did not change unexpectedly

### 6. Request DBMS verification when the effect is not obvious

If the change affects the DBMS plan (index, virtual table parameters, join type), ask the user for `EXPLAIN` / ТЖ before and after. Without measurement, record it as “expected effect, requires verification”.

---

## Rewriting Rules

The concrete “how to write it correctly” rules are canonical in `query-patterns`. Here is the mapping from the cause (step 3) to the rule from `query-patterns`; apply the rule from there, do not duplicate query patterns in this skill.

| Cause (step 3) | `query-patterns` rule |
|---|---|
| Broad virtual table read | Rule 5 — put period/dimension parameters inside the virtual table, not in `ГДЕ` |
| Query-in-loop | Rule 1 — one query + `Соответствие` for access |
| Dot-dereference without `ВЫРАЗИТЬ` | Rule 12 — `ВЫРАЗИТЬ(… КАК …)` + `ССЫЛКА` for a composite type |
| Excessive temp tables / no join index | Rules 2, 8 — minimal fields, `ИНДЕКСИРОВАТЬ ПО` only on the join field |
| Redundant totals | Rule 16 — `ИТОГИ` only for `Выбрать(ПоГруппировкам)`, otherwise `СГРУППИРОВАТЬ ПО` |
| Filtering after join | Rules 5, 13 — VT parameters / `ПО` vs `ГДЕ` |
| `РАЗЛИЧНЫЕ` masks an extra JOIN | Rule 11 — subquery `В (…)` instead of JOIN + `РАЗЛИЧНЫЕ` |

**Optimization specifics (beyond the general `query-patterns` rules):**
- A virtual table without parameters is allowed **only** with an explicit justification (for example, a report across all warehouses without a filter); otherwise always parameterize the period and dimensions.
- Rewrite **one** cause per iteration (see Stop rules) — otherwise the contribution of each change is not measurable.

---

## СКД: optimization specifics

### Dataset parameters

- СКД parameters passed into the dataset's virtual tables work the same way as query parameters: pass them in, do not filter them later through selection
- For period conditions in a register, always parameterize them in the dataset query text

### Resources and calculated fields

- СКД calculated fields that access other datasets through relations are a potential query-in-loop at the platform level
- Dataset relations (СВЯЗЬ) with conditions without an index on the detail side - check the metadata

### Selections in СКД

- Selections applied by the user through settings may not be passed into virtual table parameters - this is an architectural limitation; document it
- For critical filters (period, organization), pass them as dataset query parameters, do not rely only on СКД selections

---

## Query review checklist

- [ ] Virtual tables receive period and dimension parameters (not filtering in `WHERE`)
- [ ] Temporary tables contain only the fields needed by the next stages
- [ ] Join fields in temporary tables are indexed (`INDEX BY`)
- [ ] There are no repeated subqueries or query-in-loop
- [ ] JOIN does not multiply rows; totals and groupings match the business meaning
- [ ] Filters by date and organization are applied as early as possible
- [ ] Composite types are expanded through `CAST` before dot dereference
- [ ] `LEFT JOIN` does not turn into `INNER JOIN` because of a condition in `WHERE`
- [ ] `DISTINCT` does not mask an extra JOIN
- [ ] `ALLOWED` and other rights filters are preserved

---

## Stop rules

1. **Do not remove `ALLOWED`** without explicit security approval.
2. **Do not recommend an index without** a specific predicate + write-cost assessment - that is the responsibility of `db-performance`.
3. **Do not rewrite several causes in one step** - you cannot measure each contribution.
4. **Do not replace LEFT JOIN with INNER JOIN** without checking the business requirement: do we need all rows or only matching ones.
5. **Do not перенос optimization for one DBMS** to another without verification: PostgreSQL and MS SQL Server have different planner models.

---

depends_on:
  - framework/skills/bsl-practices/query-patterns/SKILL.md
  - framework/skills/tool-usage/diagnostics/db-performance/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
