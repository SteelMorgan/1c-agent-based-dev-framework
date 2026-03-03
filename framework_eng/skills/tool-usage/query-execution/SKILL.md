---
name: query-execution
description: Query execution (Query Execution). The skill teaches the agent to **validate and execute queries in the 1С query language** — syntax validation before execution, fetching data from the database.
---

# Query Execution (Query Execution)

## Purpose

The skill teaches the agent to **work with queries in the 1С query language** — check syntax without execution and retrieve data from the database. It is used during data analysis, diagnostics, and completeness checks.

**Principle:** Before executing — `validate_query`. Not sure about table/field names — `get_metadata_structure`.

**Syntax reference and query examples:** [`references/query-syntax-cheatsheet.md`](references/query-syntax-cheatsheet.md)

---

## CRITICAL: Metadata verification

**Never guess object or field names.** 1С configurations differ — names are unique for each database.

Before building a query, if you are unsure about the exact names:
1. `list_metadata_objects` — find the object by `metaType` and `nameMask`
2. `get_metadata_structure` — get the attributes, tabular parts, dimensions, resources

**Workflow:** metadata → confirm names → build query → `validate_query` → `execute_query`

A query with an incorrect object or field name will result in an error. One metadata call is cheaper than debugging a failed query.

---

## When to apply

| Trigger | Action |
|---------|--------|
| Need to run a query against the 1С database | `validate_query` → `execute_query` |
| User asks to check a query | `validate_query` |
| Data analysis, completeness diagnostics | `get_metadata_structure` (if the structure is unknown) → `execute_query` |
| Iterative analysis — the model examines results and makes additional queries | `execute_query` with a refined query |

---

## Usage scenarios

### Scenario 1: Query verification and execution

**Steps:**

1. `validate_query` — check syntax without running the query.
2. If there are no errors — `execute_query` to fetch data.
3. If validation fails — fix the query and repeat.

### Scenario 2: Query against an unknown structure

**Steps:**

1. `list_metadata_objects` — find the object (if the name is unknown).
2. `get_metadata_structure` — obtain the names of tables, fields, dimensions, resources.
3. Build the query using the correct names.
4. `validate_query` → `execute_query`.

### Scenario 3: Limit the result set

For large volumes of data — use `ВЫБРАТЬ ПЕРВЫЕ N` or filter conditions to avoid overloading the response.

---

## Critical MCP limitations

When running queries through `execute_query` (HTTP/MCP without parameters), values must be **explicitly provided in the query text**. Parameters like `&Имя` may not be supported.

### 1. Limit the result set with ПЕРВЫЕ N

For queries returning data rows, always limit the number of records:

```sql
ВЫБРАТЬ ПЕРВЫЕ 100
    Документы.Номер,
    Документы.Дата
ИЗ
    Документ.РеализацияТоваровУслуг КАК Документы
```

**Exceptions — `ПЕРВЫЕ N` is unnecessary and harmful:**

| Case | Why it is not needed |
|------|----------------------|
| Aggregate queries (`КОЛИЧЕСТВО`, `СУММА`, `МАКСИМУМ`) | They return a single row; `ПЕРВЫЕ 1` is redundant and may yield incorrect results when grouping |
| Existence checks (`ВЫБРАТЬ ПЕРВЫЕ 1 Ссылка ИЗ ... ГДЕ ...`) | `ПЕРВЫЕ 1` is already appropriate and limits the result |
| `ОБЪЕДИНИТЬ ВСЕ` in subqueries | The limitation is set at the outer level |

### 2. Parameters — when they are not supported

❌ **INCORRECT** (if `&Параметр` is unavailable):
```sql
ГДЕ Товар = &Товар
```

✅ **CORRECT** (comparison via primitive attributes):
```sql
ГДЕ Товар.Наименование = "iPhone 17 Pro Max"
ГДЕ Контрагент.ИНН = "7707083893"
```

### 3. Comparing reference fields — only via primitives

`execute_query` over HTTP/MCP does not support `&Ссылка` parameters, so it is impossible to compare reference fields directly — use primitive attributes of the object instead.

❌ **INCORRECT** (comparing references directly):
```sql
ГДЕ Документ.Контрагент = Справочник.Контрагенты.Ссылка
```

✅ **CORRECT** (comparison via primitive attributes):
```sql
ГДЕ Документ.Контрагент.Наименование = "ООО Ромашка"
ГДЕ Документ.Контрагент.Код = "000001"
ГДЕ Документ.Контрагент.ИНН = "7707083893"
```

> ⚠️ **Important — risk of ambiguity.** Наименование and Код do not guarantee uniqueness:
> - `Наименование` — can repeat under different folders or duplicates
> - `Код` — unique within the catalog, preferable
> - `ИНН`, `Артикул`, and other business identifiers — use them if they are unique in this configuration
>
> When acceptable: diagnostics, data analysis, one-off queries where duplicates are unlikely.
> When unacceptable: critically important selection where a false match by name yields incorrect results — in this case clarify the exact identifier with the user or use `ПОДОБНО` with a warning about possible duplicates.

### 4. Working with dates — the ДАТАВРЕМЯ function

```sql
ГДЕ Документы.Дата >= ДАТАВРЕМЯ(2026, 1, 1)
    И Документы.Дата < ДАТАВРЕМЯ(2026, 2, 1)
```

**Format:** `ДАТАВРЕМЯ(Year, Month, Day[, Hour, Minute, Second])`

### 5. String values — double quotes

```sql
ГДЕ Номенклатура.Наименование = "iPhone 17 Pro Max, 512 Гб"
ГДЕ Контрагент.ИНН = "7707083893"
```

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `validate_query` | Validate the syntax of a 1С query without execution |
| `execute_query` | Run the query and retrieve data from the 1С database |
| `get_metadata_structure` | Retrieve the structure of an object before building the query (if unsure about names) |

---

## Common mistakes and workarounds

| Mistake | Workaround |
|--------|------------|
| Error in `validate_query` | Check table/field names via `get_metadata_structure`. |
| “Parameter not found” | `&Параметр` is not supported — use explicit values in the query text. |
| Error when comparing references | `&Ссылка` is not supported over MCP — compare via `.Код` (preferred), `.Наименование`, `.ИНН`. Код is unique; Наименование can have duplicates — take that into account for critical queries. |
| Too much data / timeout | Use `ПЕРВЫЕ N` for row selections; for aggregates (`КОЛИЧЕСТВО`, `СУММА`) `ПЕРВЫЕ` is not needed. |
| NULL in calculations | Use `ЕСТЬNULL(Поле, ЗначениеПоУмолчанию)` with LEFT JOIN. |
| `execute_query` returns empty | Check filter conditions and data existence. |
| Unknown exact object name | Use `list_metadata_objects` before building the query. |
| Slow query with OR | Replace with `В (...)` or split with `ОБЪЕДИНИТЬ ВСЕ`. |
| Slow query on the Registrar | Use `ВЫРАЗИТЬ(Регистратор КАК Документ.Имя)` and `ССЫЛКА` in WHERE. |

---
depends_on: []
---
