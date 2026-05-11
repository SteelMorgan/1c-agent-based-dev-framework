---
name: query-execution
description: "Executing queries (Query Execution). This skill teaches the agent to **validate and execute queries in the 1C query language** - syntax validation before execution, retrieving data from the database."
uses_capabilities:
  - validate_query
  - execute_query
---

# Query Execution (Query Execution)

**Syntax cheat sheet:** [`references/query-syntax-cheatsheet.md`](references/query-syntax-cheatsheet.md)

## Workflow

1. **Metadata** (if you are not sure about names): `list_metadata_objects` → `get_metadata_structure`
2. **Build the query** with exact object and field names
3. **Validation:** `validate_query`
4. **Execution:** `execute_query`

Never guess names - 1C configurations differ. One metadata call is cheaper than debugging a failed query.

---

## Critical MCP limitations

When executing queries through `execute_query` (HTTP/MCP without parameters), values are set **explicitly in the query text**. Parameters `&Имя` may not be supported.

### 1. Limit the result set with FIRST N

For queries that return data rows, always limit the number of records:

```sql
ВЫБРАТЬ ПЕРВЫЕ 100
    Документы.Номер,
    Документы.Дата
ИЗ
    Документ.РеализацияТоваровУслуг КАК Документы
```

**Exceptions - `ПЕРВЫЕ N` is not needed and is harmful:**

| Case | Why it is not needed |
|--------|-----------------|
| Aggregate queries (`КОЛИЧЕСТВО`, `СУММА`, `МАКСИМУМ`) | Return one row; `ПЕРВЫЕ 1` is redundant and may produce an incorrect result when grouping |
| Existence check (`ВЫБРАТЬ ПЕРВЫЕ 1 Ссылка ИЗ ... ГДЕ ...`) | `ПЕРВЫЕ 1` is appropriate here and already limits the result |
| `ОБЪЕДИНИТЬ ВСЕ` in subqueries | The limit is applied at the outer level |

### 2. Parameters - if they are not supported

❌ **INCORRECT** (if `&Параметр` is unavailable):
```sql
ГДЕ Товар = &Товар
```

✅ **CORRECT** (compare through primitive attributes):
```sql
ГДЕ Товар.Наименование = "iPhone 17 Pro Max"
ГДЕ Контрагент.ИНН = "7707083893"
```

### 3. Comparing reference fields - only through primitives

`execute_query` through HTTP/MCP does not support `&Ссылка` parameters, so reference fields cannot be compared directly - use the object's primitive attributes.

❌ **INCORRECT** (comparing references directly):
```sql
ГДЕ Документ.Контрагент = Справочник.Контрагенты.Ссылка
```

✅ **CORRECT** (compare through primitive attributes):
```sql
ГДЕ Документ.Контрагент.Наименование = "ООО Ромашка"
ГДЕ Документ.Контрагент.Код = "000001"
ГДЕ Документ.Контрагент.ИНН = "7707083893"
```

> ⚠️ **Important - ambiguity risk.** `Наименование` and `Код` do not guarantee uniqueness:
> - `Наименование` - may repeat in different folders or when there are duplicates
> - `Код` - unique within the catalog, preferable
> - `ИНН`, `Артикул` and other business identifiers - use them if they are unique in this configuration
>
> When acceptable: diagnostics, data analysis, one-off queries where duplicates are unlikely.
> When not acceptable: a critically important selection where a false match on the name would produce an incorrect result - in this case, ask the user for the exact identifier or use `ПОДОБНО` with a warning about possible duplicates.

### 4. Working with dates - the ДАТАВРЕМЯ function

```sql
ГДЕ Документы.Дата >= ДАТАВРЕМЯ(2026, 1, 1)
    И Документы.Дата < ДАТАВРЕМЯ(2026, 2, 1)
```

**Format:** `ДАТАВРЕМЯ(Year, Month, Day[, Hour, Minute, Second])`

### 5. String values - double quotes

```sql
ГДЕ Номенклатура.Наименование = "iPhone 17 Pro Max, 512 Гб"
ГДЕ Контрагент.ИНН = "7707083893"
```

---

## Typical errors

| Error | Solution |
|--------|---------|
| NULL in calculations | `ЕСТЬNULL(Поле, ЗначениеПоУмолчанию)` when LEFT JOIN |
| Slow query with OR | Replace with `В (...)` or `ОБЪЕДИНИТЬ ВСЕ` |
| Slow query by `Регистратор` | `ВЫРАЗИТЬ(Регистратор КАК Документ.Имя)` + `ССЫЛКА` in the WHERE clause |

---
depends_on: []
---
