---
name: platform-data-core
description: "Working with 1C platform data (Platform Data Core). The skill combines three operations: searching and analyzing configuration metadata, parsing navigation links, and executing database queries."
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

Three operations that are usually used together in a single workflow:

1. **Metadata Discovery** — find a metadata object and its structure
2. **Query Execution** — build and execute a query using the found names
3. **Nav Link** — parse an incoming navigation link → query → generate an outgoing link

---

## §1 Metadata Discovery (metadata search)

**Principle:** Before working with business logic, inspect the metadata. Do not create objects "at random" unless the user explicitly asked for it.

### Tools

| Tool | Parameters | Purpose |
|------|-----------|------------|
| `list_metadata_objects` | metaType, nameMask, maxItems | Search objects by type and name mask |
| `get_metadata_structure` | metaType, name | Structure: attributes, tabular sections, dimensions, resources |
| `navigate_symbol` | — | Navigate to the modules and procedures of found objects |
| `get_call_graph` | — | Analyze call chains in modules |

### Workflow

1. **Find the object:** `list_metadata_objects(metaType, nameMask)` — check existence and type. For fuzzy search: `nameMask: "*Номенклатура*"`.
2. **Get the structure:** `get_metadata_structure(metaType, name)` — attributes, tabular sections, dimensions, resources. Required before building a query.
3. **Code analysis (if needed):** `navigate_symbol` → `get_call_graph`.
4. **Creating a new object:** the agent does NOT create metadata objects automatically. Algorithm: verify that it does not exist → describe it to the user (name, attributes, tabular sections) → user creates it in Configurator/EDT → `get_metadata_structure` for verification.

### Typical mistakes

| Mistake | Solution |
|--------|---------|
| Searching by a generic word returns too many results | Narrow nameMask; specify metaType; reduce maxItems |
| The agent tries to create the object itself | Protocol: the agent describes it, the user creates it in Configurator/EDT |

---

## §2 Query Execution (running queries)

**Syntax cheat sheet:** [`references/query-syntax-cheatsheet.md`](references/query-syntax-cheatsheet.md)

### Workflow

1. **Metadata** (if you are not sure about the names): `list_metadata_objects` → `get_metadata_structure`
2. **Build the query** with the exact object and field names
3. **Validation:** `validate_query`
4. **Execution:** `execute_query`

Never guess names — 1C configurations differ. One metadata lookup is cheaper than debugging a failed query.

---

### Critical MCP restrictions

When executing queries through `execute_query` (HTTP/MCP without parameters), values are set **explicitly in the query text**. Parameters `&Имя` may not be supported.

#### 1. Limit the result set with `ПЕРВЫЕ N`

For queries that return data rows, always limit the number of records:

```sql
ВЫБРАТЬ ПЕРВЫЕ 100
    Документы.Номер,
    Документы.Дата
ИЗ
    Документ.РеализацияТоваровУслуг КАК Документы
```

**Exceptions — `ПЕРВЫЕ N` is not needed and is harmful:**

| Case | Why it is not needed |
|--------|-----------------|
| Aggregate queries (`КОЛИЧЕСТВО`, `СУММА`, `МАКСИМУМ`) | Return one row; `ПЕРВЫЕ 1` is redundant and can produce an incorrect result when grouping |
| Existence check (`ВЫБРАТЬ ПЕРВЫЕ 1 Ссылка ИЗ ... ГДЕ ...`) | `ПЕРВЫЕ 1` is appropriate here and already limits the result |
| `ОБЪЕДИНИТЬ ВСЕ` in subqueries | The limit is applied at the outer level |

#### 2. Parameters, if they are not supported

❌ **WRONG** (if `&Параметр` is unavailable):
```sql
ГДЕ Товар = &Товар
```

✅ **RIGHT** (compare through primitive attributes):
```sql
ГДЕ Товар.Наименование = "iPhone 17 Pro Max"
ГДЕ Контрагент.ИНН = "7707083893"
```

#### 3. Comparing reference fields - only through primitives

`execute_query` via HTTP/MCP does not support `&Ссылка` parameters, so it is impossible to compare reference fields directly - use the object's primitive attributes.

❌ **WRONG** (direct reference comparison):
```sql
ГДЕ Документ.Контрагент = Справочник.Контрагенты.Ссылка
```

✅ **RIGHT** (compare through primitive attributes):
```sql
ГДЕ Документ.Контрагент.Наименование = "ООО Ромашка"
ГДЕ Документ.Контрагент.Код = "000001"
ГДЕ Документ.Контрагент.ИНН = "7707083893"
```

> ⚠️ **Important — ambiguity risk.** Name and Code are not guaranteed to be unique:
> - `Наименование` — may repeat in different folders or when duplicates exist
> - `Код` — unique within a catalog, preferable
> - `ИНН`, `Артикул`, and other business identifiers — use them if they are unique in this configuration
>
> When acceptable: diagnostics, data analysis, one-off queries where duplicates are unlikely.
> When not acceptable: critical selection where a false match by name will produce the wrong result — in this case, ask the user for the exact identifier or use `ПОДОБНО` with a warning about possible duplicates.

#### 4. Working with dates - the `ДАТАВРЕМЯ` function

```sql
ГДЕ Документы.Дата >= ДАТАВРЕМЯ(2026, 1, 1)
    И Документы.Дата < ДАТАВРЕМЯ(2026, 2, 1)
```

**Format:** `ДАТАВРЕМЯ(Год, Месяц, День[, Час, Минута, Секунда])`

#### 5. String values - double quotes

```sql
ГДЕ Номенклатура.Наименование = "iPhone 17 Pro Max, 512 Гб"
ГДЕ Контрагент.ИНН = "7707083893"
```

---

### Typical query mistakes

| Mistake | Solution |
|--------|---------|
| NULL in calculations | `ЕСТЬNULL(Поле, ЗначениеПоУмолчанию)` for LEFT JOIN |
| Slow query with OR | Replace with `В (...)` or `ОБЪЕДИНИТЬ ВСЕ` |
| Slow query against the Recorder | `ВЫРАЗИТЬ(Регистратор КАК Документ.Имя)` + `ССЫЛКА` in WHERE |

---

## §3 Nav Link (navigation links)

### When to use

| Trigger | Action |
|---------|----------|
| The user provides a link `e1cib/data/...` | `parse_nav_link` → type + link → queries/analysis |
| You need to generate a link from query data | `get_nav_link(type, link)` → link for the response |

### Algorithm

- **Incoming link:** `parse_nav_link` → extract type and ref → build query → `execute_query`.
- **Outgoing link:** `execute_query` returned a link → `get_nav_link(type, link)` → return it to the user.

### Capabilities

| Capability | Purpose |
|------------|------------|
| `parse_nav_link` | Parsing `e1cib/data/...` — extracting the object type and link |
| `get_nav_link` | Generating a navigation link from type + link |

---
depends_on: []
---
