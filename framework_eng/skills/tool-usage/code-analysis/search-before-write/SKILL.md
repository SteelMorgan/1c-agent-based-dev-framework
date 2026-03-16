---
name: search-before-write
description: Search Before Write skill. It trains the agent to **always perform a search before writing new code**.
---

# Search Before Write (Search Before Write)

## Purpose

This skill teaches the agent to **always perform a search before writing new code**. It prevents duplication, ensures alignment with existing project patterns, and makes the most of the configuration capabilities already available (БСП, metadata, platform APIs).

**Principle:** Any code-writing task is primarily a search task. Search first, write second.

---

## When to apply

| Trigger | Action |
|---------|--------|
| The user asks to create a new function or procedure | First search for similar implementations via `navigate_symbol`, `search_ssl_functions` |
| The user asks to implement business logic | Search the metadata (`list_metadata_objects`, `get_metadata_structure`) — is the needed object present |
| The user uses built-in platform types/methods | Search the syntax reference (`search_syntax_reference`, `get_type_info`) — verify correct usage |
| The user asks to write a query | Search for existing queries in the project (`navigate_symbol`) |
| The user asks to create a print layout | Search existing print layouts, verify БСП patterns |
| Any task that involves creating new code | Apply the search strategy (see below) |

---

## Usage scenarios

### Scenario 1: Creating a new function

**Steps:**

1. **Name search** — `navigate_symbol` (operation: `search`) with the assumed function name.
2. **If nothing found** — `search_ssl_functions` in case the function already exists in БСП.
3. **If БСП also lacks it** — `search_syntax_reference` to check built-in platform capabilities.
4. **If still nothing** — `ask_ai_assistant` to clarify best practices (optional).
5. **Only after that** — write the code.

**Example:** The user asks to create a function that retrieves item balances.

```
1. navigate_symbol(query: "ПолучитьОстатки", operation: "search")
2. search_ssl_functions(query: "остатки товаров")
3. list_metadata_objects(metaType: "РегистрНакопления", nameMask: "*Остатки*")
4. get_metadata_structure(metaType: "РегистрНакопления", name: "ОстаткиТоваров") — при необходимости
5. Если найдены регистры — проверить их модули через navigate_symbol
6. Писать код на основе найденных паттернов
```

### Scenario 2: Implementing business logic

**Steps:**

1. `list_metadata_objects` — locate metadata objects (Справочники, Документы, регистры) by metaType and nameMask.
2. For the discovered objects — use `navigate_symbol` to jump to their modules and analyze existing code.
3. `search_syntax_reference` — when built-in platform types are used (СправочникСсылка, ДокументОбъект, etc.).
4. Write code based on the discovered patterns.

### Scenario 3: Creating a print layout

**Steps:**

1. `list_metadata_objects` — find existing print layouts (metaType: "Обработка" or "Отчет", nameMask: "*Печатн*").
2. `navigate_symbol` — find print layout implementations (for example, by a name like “Печать” or “ПечатнаяФорма”).
3. `search_ssl_functions` — verify БСП API for print layouts.
4. `ask_ai_assistant` — request a print layout template following БСП patterns (via `ask_1c_ai`).
5. Implement following the located samples.

### Scenario 4: Using platform APIs

**Steps:**

1. `search_syntax_reference` — search the platform syntax reference (for example, “СтрНайти”, “ТаблицаЗначений”).
2. `get_type_info` — detailed type information (methods, properties, signatures).
3. Only after verification — use in code.

---

## Search strategy

| Priority | Tool | Characteristics |
|-----------|------|-----------------|
| 1 | LSP (`navigate_symbol`) | Fast, precise, project-wide. First choice. |
| 2 | `list_metadata_objects`, `get_metadata_structure` | For configuration objects. |
| 3 | `search_syntax_reference`, `get_type_info` | For built-in platform types/methods. |
| 4 | `search_ssl_functions` | For БСП (if the configuration includes БСП). |
| 5 | `ask_ai_assistant` | Broad search but slower. When LSP and metadata yield nothing. |

**Rule:** LSP → metadata → platform API → БСП → AI assistant. The cascade is sequential: each tool is used only if the previous stage produced no result.

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `navigate_symbol` | Symbol search, go to definition, find usages |
| `list_metadata_objects` | Search metadata objects by type and mask |
| `get_metadata_structure` | Object structure (attributes, dimensions, resources) |
| `search_syntax_reference` | Search the 1C platform syntax reference |
| `get_type_info` | Detailed platform type information |
| `search_ssl_functions` | Search БСП functions |
| `ask_ai_assistant` | Questions about best practices, request code/function templates |

---

## Typical mistakes and workarounds

| Mistake | Workaround |
|--------|------------|
| Skipping the search and writing code immediately | Strict rule: for any code creation task — first step = search. |
| Searching only one source | Use the cascade: LSP → metadata → API → БСП → AI. |
| `list_metadata_objects` returns empty | Check: is the configuration loaded? Use `dump_config` if needed. Verify metaType and nameMask. |
| `navigate_symbol` returns empty | Clarify the name (Russian/Latin, case). Use fuzzy search via `ask_ai_assistant`. |
| `search_ssl_functions` is unavailable | Configuration lacks БСП — use `search_syntax_reference` and `navigate_symbol` in common modules. |
| Search results are irrelevant | Narrow the query: add context (object type, subsystem). |

---
depends_on: []
---
