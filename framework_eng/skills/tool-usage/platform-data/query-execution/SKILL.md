---
name: query-execution
description: "Query execution. The skill teaches the agent to **check and execute queries in the 1C query language** - syntax validation before execution, retrieving data from the database."
---

# Query Execution (Query Execution)

**Syntax reference:** [`references/query-syntax-cheatsheet.md`](references/query-syntax-cheatsheet.md)

## Workflow

1. **Metadata** (if you are unsure about the names): `list_metadata_objects` → `get_metadata_structure`
2. **Build the query** with exact object and field names
3. **Validation:** `validate_query`
4. **Execution:** `execute_query`

Never guess names - 1C configurations differ. One metadata call is cheaper than debugging a failed query.

---

## Critical MCP constraints

When executing queries through `execute_query` (HTTP/MCP without parameters), values are set **explicitly in the query text**. `&Имя` parameters might not be supported.

### 1. Limit the selection with ПЕРВЫЕ N

For queries that return data rows, always limit the number of records:

```sql
ВЫБРАТЬ ПЕРВЫЕ 100
    Документы.Номер,
    Документы.Дата
ИЗ
    Документ.РеализацияТоваровУслуг КАК Документы
```

**Exceptions - `ПЕРВЫЕ N` is unnecessary and harmful:**

| Case | Why it is unnecessary |
|------|-----------------------|
| Aggregate queries (`КОЛИЧЕСТВО`, `СУММА`, `МАКСИМУМ`) | They return one row; `ПЕРВЫЕ 1` is redundant and can give an incorrect result when grouping |
| Existence check (`ВЫБРАТЬ ПЕРВЫЕ 1 Ссылка ИЗ ... ГДЕ ...`) | `ПЕРВЫЕ 1` is appropriate here and already limits the output |
| `ОБЪЕДИНИТЬ ВСЕ` in subqueries | The limit is set at the outer level |

### 2. Parameters - if they are not supported

❌ **INCORRECT** (if `&Параметр` is unavailable):
```sql
ГДЕ Товар = &Товар
```

✅ **CORRECT** (comparison through primitive attributes):
```sql
ГДЕ Товар.Наименование = "iPhone 17 Pro Max"
ГДЕ Контрагент.ИНН = "7707083893"
```

### 3. Comparing reference fields - only through primitives

`execute_query` over HTTP/MCP does not support `&Ссылка` parameters, so comparing reference fields directly is impossible - use primitive attributes of the object instead.

❌ **INCORRECT** (comparing references directly):
```sql
ГДЕ Документ.Контрагент = Справочник.Контрагенты.Ссылка
```

✅ **CORRECT** (comparison through primitive attributes):
```sql
ГДЕ Документ.Контрагент.Наименование = "ООО Ромашка"
ГДЕ Документ.Контрагент.Код = "000001"
ГДЕ Документ.Контрагент.ИНН = "7707083893"
```

> ⚠️ **Important - risk of ambiguity.** Name and Code do not guarantee uniqueness:
> - `Name` can repeat in different folders or among duplicates
> - `Code` is unique within the catalog, so it is preferable
> - `TIN`, `SKU`, and other business identifiers - use them if they are unique in this configuration
>
> When acceptable: diagnostics, data analysis, one-off queries where duplicates are unlikely.
> When unacceptable: critically important selection where a false match by name would produce an incorrect result - in that case, ask the user for the exact identifier or use `ПОДОБНО` with a warning about possible duplicates.

### 4. Working with dates - the ДАТАВРЕМЯ function

```sql
ГДЕ Документы.Дата >= ДАТАВРЕМЯ(2026, 1, 1)
    И Документы.Дата < ДАТАВРЕМЯ(2026, 2, 1)
```

**Format:** `ДАТАВРЕМЯ(Year, Month, Day[, Hour, Minute, Second])`

### 5. String literals - double quotes

```sql
ГДЕ Номенклатура.Наименование = "iPhone 17 Pro Max, 512 Гб"
ГДЕ Контрагент.ИНН = "7707083893"
```

---

## Typical mistakes

| Mistake | Solution |
|---------|----------|
| NULL in calculations | `ЕСТЬNULL(Field, DefaultValue)` with LEFT JOIN |
| Slow query with OR | Replace with `В (...)` or `ОБЪЕДИНИТЬ ВСЕ` |
| Slow query against a Registrar | `ВЫРАЗИТЬ(Регистратор КАК Документ.Имя)` + `ССЫЛКА` in WHERE |

---
depends_on: []
---
