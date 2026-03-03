---
name: search-before-write
description: Search Before Writing. Skill teaches the agent **to always perform a search before writing new code**.
---

# Search Before Writing (Search Before Write)

## Purpose

The skill teaches the agent **to always perform a search before writing new code**. This prevents duplication, ensures alignment with existing project patterns, and makes the most of available configuration features (БСП, metadata, platform APIs).

**Principle:** Any task that involves writing code is first and foremost a search task. First search, then write.

---

## When to apply

| Trigger | Action |
|---------|--------|
| User asks to create a new function or procedure | First search for similar implementations via `navigate_symbol`, `search_ssl_functions` |
| User asks to implement business logic | Search metadata (`list_metadata_objects`, `get_metadata_structure`) — are the required objects present |
| User uses built-in platform types/methods | Search syntax reference (`search_syntax_reference`, `get_type_info`) — ensure the invocation is correct |
| User asks to write a query | Search for existing queries in the project (`navigate_symbol`) |
| User asks to create a print form | Search for existing print forms and verify БСП patterns |
| Any task that involves creating new code | Apply the search strategy (see below) |

---

## Usage scenarios

### Scenario 1: Creating a new function

**Steps:**

1. **Search by name** — `navigate_symbol` (operation: `search`) with the expected function name.
2. **If nothing found** — `search_ssl_functions` in case the function already exists in БСП.
3. **If not found in БСП** — `search_syntax_reference` to verify platform built-in capabilities.
4. **If still nothing** — `ask_ai_assistant` to clarify best practices (optional).
5. **Only after that** — write the code.

**Example:** The user requests a function that retrieves product reserves.

```
1. navigate_symbol(query: "ПолучитьОстатки", operation: "search")
2. search_ssl_functions(query: "остатки товаров")
3. list_metadata_objects(metaType: "РегистрНакопления", nameMask: "*Остатки*")
4. get_metadata_structure(metaType: "РегистрНакопления", name: "ОстаткиТоваров") — if needed
5. If registers are found — inspect their modules via navigate_symbol
6. Write code based on the discovered patterns
```

### Scenario 2: Implementing business logic

**Steps:**

1. `list_metadata_objects` — find metadata objects (catalogs, documents, registers) by metaType and nameMask.
2. For the found objects — `navigate_symbol` to jump to modules and analyze the existing code.
3. `search_syntax_reference` — if built-in platform types are used (СправочникСсылка, ДокументОбъект, etc.).
4. Write code based on the discovered patterns.

### Scenario 3: Creating a print form

**Steps:**

1. `list_metadata_objects` — locate existing print forms (metaType: "Обработка" or "Отчет", nameMask: "*Печатн*").
2. `navigate_symbol` — find implementations of print forms (for example, by the names «Печать» or «ПечатнаяФорма»).
3. `search_ssl_functions` — review the БСП API for print forms.
4. `ask_ai_assistant` — request a print form template based on БСП patterns (via `ask_1c_ai`).
5. Implement according to the discovered examples.

### Scenario 4: Using the platform API

**Steps:**

1. `search_syntax_reference` — lookup in the platform syntax help (for example, «СтрНайти», «ТаблицаЗначений").
2. `get_type_info` — detailed type information (methods, properties, signatures).
3. Use it in code only after verification.

---

## Search strategy

| Priority | Tool | Characteristics |
|----------|------|-----------------|
| 1 | LSP (`navigate_symbol`) | Fast, precise, project-wide. First choice. |
| 2 | `list_metadata_objects`, `get_metadata_structure` | For configuration objects. |
| 3 | `search_syntax_reference`, `get_type_info` | For built-in platform types/methods. |
| 4 | `search_ssl_functions` | For БСП (if the configuration includes it). |
| 5 | `ask_ai_assistant` | Broad search but slower. When LSP and metadata yield no result. |

**Rule:** LSP → metadata → platform API → БСП → AI assistant. The cascade is sequential: each subsequent tool is invoked only if the previous one produced no results.

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `navigate_symbol` | Search symbols, jump to definitions, find usages |
| `list_metadata_objects` | Search metadata objects by type and mask |
| `get_metadata_structure` | Object structure (attributes, dimensions, resources) |
| `search_syntax_reference` | Search the 1С platform syntax reference |
| `get_type_info` | Detailed information about platform types |
| `search_ssl_functions` | Search БСП functions |
| `ask_ai_assistant` | Questions about best practices, request code/function templates |

---

## Typical mistakes and workarounds

| Mistake | Workaround |
|---------|------------|
| Skipping the search and writing code immediately | Strict rule: for any code creation task, the first step is search. |
| Searching only one source | Use the cascade: LSP → metadata → API → БСП → AI. |
| `list_metadata_objects` returns empty | Check: is the configuration loaded? Use `dump_config` if needed. Verify metaType and nameMask. |
| `navigate_symbol` returns empty | Clarify the name (Russian/Latin, case). Use fuzzy search via `ask_ai_assistant`. |
| `search_ssl_functions` is unavailable | Configuration lacks БСП — use `search_syntax_reference` and `navigate_symbol` for common modules. |
| Search results are not relevant | Narrow the query: add context (object type, subsystem). |

---
depends_on: []
---
