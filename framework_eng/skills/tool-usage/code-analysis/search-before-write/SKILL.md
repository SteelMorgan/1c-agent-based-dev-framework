---
name: search-before-write
description: Search Before Write. The skill teaches the agent to **always perform a search before writing new code**.
---

# Search Before Write (Search Before Write)

Any code-writing task is first and foremost a search task. Search first, then write.

## Search Cascade

Each next step runs only if the previous one returned no results:

| Step | Tool | What we are looking for |
|-----|------------|----------|
| 1 | `navigate_symbol` (LSP) | Similar functions/procedures in the project |
| 2 | `list_metadata_objects`, `get_metadata_structure` | Configuration objects (справочники, регистры, документы) |
| 3 | `search_syntax_reference`, `get_type_info` | Platform built-in types/methods |
| 4 | `search_ssl_functions` | БСП functions (if the configuration includes БСП) |
| 5 | `ask_ai_assistant` | Best practices, templates (if cascade 1–4 produced no results) |

## Triggers

| Task | Initial cascade step |
|--------|-----------------------|
| New function/procedure | 1 — search for name-based analogs |
| Business logic | 2 — search metadata objects |
| Using platform API | 3 — syntax reference |
| Print form | 2 → 4 (metadata + БСП API) |
| Query | 1 — existing project queries |

## Capabilities

| Capability | Purpose |
|------------|------------|
| `navigate_symbol` | Search symbols, definition, usages |
| `list_metadata_objects` | Metadata objects by type and mask |
| `get_metadata_structure` | Object structure (requisites, dimensions, resources) |
| `search_syntax_reference` | Platform syntax reference |
| `get_type_info` | Platform type details |
| `search_ssl_functions` | БСП functions |
| `ask_ai_assistant` | Best practices, templates |

## Common mistakes

| Mistake | Workaround |
|--------|---------------|
| Skipping the search | Hard rule: code creation → first step = search |
| `list_metadata_objects` is empty | Is the configuration loaded? `dump_config`; check metaType/nameMask |
| `navigate_symbol` is empty | Clarify the name (Cyrillic/Latin, case); `ask_ai_assistant` |
| `search_ssl_functions` unavailable | Without БСП – `search_syntax_reference` + `navigate_symbol` over common modules |

---
depends_on: []
---
