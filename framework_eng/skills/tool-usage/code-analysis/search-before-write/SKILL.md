---
name: search-before-write
description: "Before new BSL code, find an existing analogue"
alwaysApply: false
---

# Search Before Write

Any coding task is first and foremost a search task. First search, then write.

## Search Cascade

Each next step is only if the previous one did not produce a result:

| Step | Tool | What we search for |
|-----|------------|----------|
| 1 | `navigate_symbol` (LSP) | Similar functions/procedures in the project |
| 2 | `list_metadata_objects`, `get_metadata_structure` | Configuration objects (catalogs, registers, documents) |
| 3 | `search_syntax_reference`, `get_type_info` | Built-in platform types/methods |
| 4 | `search_ssl_functions` | БСП functions (if the configuration contains БСП) |
| 5a | `ask_ai_assistant` (SEARCH_DOCS template) | Platform documentation — if the question is about API, methods, types |
| 5b | `ask_ai_assistant` (SEARCH_ITS → FETCH_ITS template) | Standards, methodology, ИТС examples — if the question is about development rules |
| 5c | `ask_ai_assistant` (generic) | Other questions — only if 5a/5b do not fit |

> Prompt templates for steps 5a–5c — see the `buddy-prompting` skill.

## Triggers

| Task | Initial cascade step |
|--------|-----------------------|
| New function/procedure | 1 — search for analogs by name |
| Business logic | 2 — search for metadata objects |
| Platform API usage | 3 — syntax reference; fallback 5a (documentation) |
| Print form | 2 → 4 (metadata + БСП API) |
| Standards and development rules | 5b — search in ИТС |
| Migration between versions | 5a (DIFF_VERSIONS template) |
| Query | 1 — existing queries in the project |

## Capabilities

| Capability | Purpose |
|------------|------------|
| `navigate_symbol` | Search symbols, definition, usages |
| `list_metadata_objects` | Metadata objects by type and mask |
| `get_metadata_structure` | Object structure (attributes, dimensions, resources) |
| `search_syntax_reference` | Platform syntax reference |
| `get_type_info` | Platform type details |
| `search_ssl_functions` | БСП functions |
| `ask_ai_assistant` | Best practices, templates |

## Typical Errors

| Error | Workaround |
|--------|---------------|
| Skipping search | Hard rule: code creation → first step = search |
| `list_metadata_objects` empty | Configuration loaded? `v8-runner build` (or `v8-runner dump --mode incremental` if the ИБ is the source of truth); check metaType/nameMask |
| `navigate_symbol` empty | Clarify the name (Russian/Latin, case); `ask_ai_assistant` (SEARCH_DOCS template) |
| `ask_ai_assistant` empty result | Rephrase query; see rules in `buddy-prompting` |
| `search_ssl_functions` unavailable | Without БСП — `search_syntax_reference` + `navigate_symbol` for common modules |

---
depends_on: []
---
