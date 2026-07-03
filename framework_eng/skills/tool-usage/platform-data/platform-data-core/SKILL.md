---
name: platform-data-core
description: "Platform data: metadata, nav links, safe queries"
uses_capabilities:
  - list_metadata_objects
  - get_metadata_structure
  - navigate_symbol
  - get_call_graph
  - parse_nav_link
  - get_nav_link
  - validate_query
  - execute_query
---

# Working with 1C Platform Data (Platform Data Core)

Three operations that are usually used together in one workflow:

1. **Metadata Discovery** — find a metadata object and its structure
2. **Query Execution** — build and execute a query by the found names
3. **Nav Link** — parse an incoming navigation link → query → generate an outgoing link

---

## §1 Metadata Discovery (metadata search)

**Principle:** Before working with business logic, explore the metadata. Do not create objects "by guess" if the user did not explicitly ask for it.

### Tools

| Tool | Parameters | Purpose |
|------|-----------|------------|
| `list_metadata_objects` | metaType, nameMask, maxItems | Search for objects by type and name mask |
| `get_metadata_structure` | metaType, name | Structure: attributes, tabular sections, dimensions, resources |
| `navigate_symbol` | — | Navigate to modules and procedures of found objects |
| `get_call_graph` | — | Analyze call chains in modules |

### Workflow

1. **Find the object:** `list_metadata_objects(metaType, nameMask)` — check existence and type. For fuzzy search: `nameMask: "*Номенклатура*"`.
2. **Get the structure:** `get_metadata_structure(metaType, name)` — attributes, tabular sections, dimensions, resources. Required before building a query.
3. **Code analysis (if needed):** `navigate_symbol` → `get_call_graph`.
4. **Create a new object:** the agent does NOT create metadata objects automatically. Algorithm: check that it does not exist → describe to the user (name, attributes, tabular sections) → the user creates it in Configurator/EDT → `get_metadata_structure` for verification.

### Typical Errors

| Error | Solution |
|--------|---------|
| Searching by a generic word returns many results | Narrow `nameMask`; specify `metaType`; reduce `maxItems` |
| The agent tries to create the object itself | Protocol: the agent describes it, the user creates it in Configurator/EDT |

---

## §2 Query Execution (выполнение запросов)

**Syntax reference:** [`references/query-syntax-cheatsheet.md`](references/query-syntax-cheatsheet.md)

### Workflow

1. **Metadata** (if you are not sure about the names): `list_metadata_objects` → `get_metadata_structure`
2. **Build the query** with exact object and field names
3. **Validation:** `validate_query`
4. **Execution:** `execute_query`

Never guess names — 1C configurations differ. One metadata lookup call is cheaper than debugging a failed query.

---

### Critical MCP limitations

When executing queries through `execute_query` (HTTP/MCP without parameters), values are specified **explicitly in the query text**. Parameters `&Name` may not be supported.

#### 1. Limit the selection with FIRST N

For queries that return data rows, always limit the number of records:

```sql
ВЫБРАТЬ ПЕРВЫЕ 100
    Документы.Номер,
    Документы.Дата
ИЗ
    Документ.РеализацияТоваровУслуг КАК Документы
```

**Exceptions - `ПЕРВЫЕ N` is not needed and is harmful:**

| Case | Why not needed |
|--------|-----------------|
| Aggregate queries (`КОЛИЧЕСТВО`, `СУММА`, `МАКСИМУМ`) | They return one row; `ПЕРВЫЕ 1` is redundant and can produce an incorrect result when grouping |
| Existence check (`ВЫБРАТЬ ПЕРВЫЕ 1 Ссылка ИЗ ... ГДЕ ...`) | `ПЕРВЫЕ 1` is appropriate here and already limits the result |
| `ОБЪЕДИНИТЬ ВСЕ` in subqueries | The limit is set at the outer level |

#### 2. Parameters - if they are not supported

❌ **INCORRECT** (if `&Параметр` is unavailable):
```sql
ГДЕ Товар = &Товар
```

✅ **CORRECT** (comparison through primitive attributes):
```sql
ГДЕ Товар.Наименование = "iPhone 17 Pro Max"
ГДЕ Контрагент.ИНН = "7707083893"
```

#### 3. Comparing reference fields only through primitives

`execute_query` over HTTP/MCP does not support `&Ссылка` parameters, so comparing reference fields directly is impossible - use the object's primitive attributes.

❌ **INCORRECT** (direct reference comparison):
```sql
ГДЕ Документ.Контрагент = Справочник.Контрагенты.Ссылка
```

✅ **CORRECT** (comparison through primitive attributes):
```sql
ГДЕ Документ.Контрагент.Наименование = "ООО Ромашка"
ГДЕ Документ.Контрагент.Код = "000001"
ГДЕ Документ.Контрагент.ИНН = "7707083893"
```

> ⚠️ **Important - ambiguity risk.** `Наименование` and `Код` do not guarantee uniqueness:
> - `Наименование` - can repeat in different folders or when duplicates exist
> - `Код` - unique within a catalog, preferable
> - `ИНН`, `Артикул` and other business identifiers - use them if they are unique in this configuration
>
> When acceptable: diagnostics, data analysis, one-off queries where duplicates are unlikely.
> When unacceptable: a critical lookup where a false match by name would give the wrong result - in that case, ask the user for the exact identifier or use `ПОДОБНО` with a warning about possible duplicates.

#### 4. Literal values - dates and strings

Since `&Имя` parameters are unavailable, values are specified as literals: dates - via `ДАТАВРЕМЯ(Год, Месяц, День[, Час, Минута, Секунда])`, strings - in double quotes. Syntax and examples: [`references/query-syntax-cheatsheet.md`](references/query-syntax-cheatsheet.md) §§ "Working with dates", "String values".

---

### Typical query mistakes

| Error | Solution |
|--------|---------|
| NULL in calculations | `ЕСТЬNULL(Поле, ЗначениеПоУмолчанию)` with LEFT JOIN |
| Slow query with OR | Replace with `IN (...)` or `UNION ALL` |
| Slow query against Registrar | `ВЫРАЗИТЬ(Регистратор КАК Документ.Имя)` + `ССЫЛКА` in `ГДЕ` |

---

## §3 Nav Link (navigation links)

### When to use

| Trigger | Action |
|---------|----------|
| User provides an e1cib/data/... link | `parse_nav_link` → type + link → queries/analysis |
| Need to generate a link from query data | `get_nav_link(type, link)` → link for the response |

### Algorithm

- **Incoming link:** `parse_nav_link` → extract type and ref → build query → `execute_query`.
- **Outgoing link:** `execute_query` returned a link → `get_nav_link(type, link)` → return to the user.

### Capabilities

| Capability | Purpose |
|------------|------------|
| `parse_nav_link` | Parsing e1cib/data/... — extracting the object type and link |
| `get_nav_link` | Forming a navigation link by type + link |

---

depends_on: []
---
