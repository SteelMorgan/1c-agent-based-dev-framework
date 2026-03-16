---
name: query-execution
description: Query execution (Query Execution). The skill teaches the agent to **check and execute queries in the 1C query language** — syntax validation before execution, fetching data from the database.
---

# Query Execution (Query Execution)

## Purpose

The skill teaches the agent to **work with queries in the 1C query language** — validate syntax without execution and retrieve data from the database. It is used for data analysis, diagnostics, and verifying data completion.

**Principle:** Before execution — `validate_query`. Not sure about table or field names — `get_metadata_structure`.

**Syntax reference and query examples:** [`references/query-syntax-cheatsheet.md`](references/query-syntax-cheatsheet.md)

---

## CRITICAL: Metadata validation

**Never guess object or field names.** 1C configurations differ — names are unique for each database.

Before building a query, if you are not sure about the exact names:
1. `list_metadata_objects` — find the object by metaType and nameMask
2. `get_metadata_structure` — get the attributes, tabular parts, dimensions, and resources

**Workflow:** metadata → confirm names → build the query → `validate_query` → `execute_query`

A query with an incorrect object or field name will fail. One metadata call is cheaper than debugging a failed query.

---

## When to apply

| Trigger | Action |
|---------|--------|
| Need to run a query against a 1C database | `validate_query` → `execute_query` |
| User asks to check a query | `validate_query` |
| Data analysis, diagnostics of data filling | `get_metadata_structure` (if the structure is unknown) → `execute_query` |
| Iterative analysis — the model reviews results and issues follow-up queries | `execute_query` with the refined query |

---

## Use cases

### Scenario 1: Query validation and execution

**Steps:**

1. `validate_query` — check syntax without execution.
2. If there are no errors — `execute_query` to retrieve data.
3. On validation errors — fix the query and retry.

### Scenario 2: Query against an unknown structure

**Steps:**

1. `list_metadata_objects` — locate the object (if the name is unknown).
2. `get_metadata_structure` — obtain table, field, dimension, and resource names.
3. Build the query with correct names.
4. `validate_query` → `execute_query`.

### Scenario 3: Limiting the result set

With large datasets — use `ВЫБРАТЬ ПЕРВЫЕ N` or filter conditions so the answer remains manageable.

---

## Critical MCP limitations

When running queries through `execute_query` (HTTP/MCP without parameters) values must be supplied **explicitly in the query text**. Parameters `&Имя` might not be supported.

### 1. Limit the result set with ПЕРВЫЕ N

For queries returning rows of data, always limit the number of records:

```sql
ВЫБРАТЬ ПЕРВЫЕ 100
    Документы.Номер,
    Документы.Дата
ИЗ
    Документ.РеализацияТоваровУслуг КАК Документы
```

**Exceptions — `ПЕРВЫЕ N` is unnecessary and can be harmful:**

| Case | Why it is unnecessary |
|------|----------------------|
| Aggregate queries (`КОЛИЧЕСТВО`, `СУММА`, `МАКСИМУМ`) | They return a single row; `ПЕРВЫЕ 1` is redundant and can yield incorrect results when grouping |
| Existence check (`ВЫБРАТЬ ПЕРВЫЕ 1 Ссылка ИЗ ... ГДЕ ...`) | `ПЕРВЫЕ 1` is appropriate here and already limits the output |
| `ОБЪЕДИНИТЬ ВСЕ` in subqueries | The limit is applied at the outer level |

### 2. Parameters — when they are not supported

❌ **INCORRECT** (if `&Параметр` is unavailable):
```sql
ГДЕ Товар = &Товар
```

✅ **CORRECT** (comparison through primitive attributes):
```sql
ГДЕ Товар.Наименование = "iPhone 17 Pro Max"
ГДЕ Контрагент.ИНН = "7707083893"
```

### 3. Comparing reference fields — only via primitives

`execute_query` over HTTP/MCP does not support `&Ссылка` parameters, so comparing reference fields directly is impossible — use primitive attributes of the object instead.

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
> - `Наименование` can repeat in different folders or duplicates
> - `Код` is unique within the catalog, so it is preferable
> - `ИНН`, `Артикул`, and other business identifiers — use them if they are unique in this configuration
>
> When acceptable: diagnostics, data analysis, one-off queries where duplicates are unlikely.
> When unacceptable: critically important selections where a false match by name would yield incorrect results — in that case, clarify the exact identifier with the user or use `ПОДОБНО` while warning about possible duplicates.

### 4. Working with dates — the ДАТАВРЕМЯ function

```sql
ГДЕ Документы.Дата >= ДАТАВРЕМЯ(2026, 1, 1)
    И Документы.Дата < ДАТАВРЕМЯ(2026, 2, 1)
```

**Format:** `ДАТАВРЕМЯ(Year, Month, Day[, Hour, Minute, Second])`

### 5. String literals — double quotes

```sql
ГДЕ Номенклатура.Наименование = "iPhone 17 Pro Max, 512 Гб"
ГДЕ Контрагент.ИНН = "7707083893"
```

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `validate_query` | Syntax validation of a 1C query language statement without execution |
| `execute_query` | Execute the query and fetch data from the 1C database |
| `get_metadata_structure` | Retrieve the structure of the object before building the query (if you are unsure about the names) |

---

## Common mistakes and workarounds

| Mistake | Workaround |
|---------|------------|
| Validation error in `validate_query` | Verify table/field names via `get_metadata_structure`. |
| “Parameter not found” | `&Параметр` is not supported — use explicit values in the query text. |
| Error when comparing references | `&Ссылка` is not supported over MCP — compare via `.Код` (preferred), `.Наименование`, or `.ИНН`. Код is unique; Наименование can yield duplicates — account for that in critical queries. |
| Too much data / timeout | For row selection use `ПЕРВЫЕ N`; for aggregates (`КОЛИЧЕСТВО`, `СУММА`) `ПЕРВЫЕ` is not needed. |
| NULL in calculations | Use `ЕСТЬNULL(Field, DefaultValue)` with LEFT JOIN. |
| `execute_query` returns nothing | Check the filter conditions and whether the data exists. |
| Unknown object name | `list_metadata_objects` before building the query. |
| Slow query with OR | Replace with `В (...)` or split into `ОБЪЕДИНИТЬ ВСЕ`. |
| Slow query against a Registrar | Use `ВЫРАЗИТЬ(Регистратор КАК Документ.Имя)` and `ССЫЛКА` in WHERE. |

---
depends_on: []
---
