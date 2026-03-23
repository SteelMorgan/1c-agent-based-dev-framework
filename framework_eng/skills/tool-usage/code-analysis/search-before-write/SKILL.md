---
name: search-before-write
description: Search Before Write (Search Before Write). The skill teaches the agent **to always perform a search before writing new code**.
---

# Search Before Write (Search Before Write)

Any coding task is, first and foremost, a search task. Search first, then write.

## Search Cascade

Each next step happens only if the previous one produced no results:

| Step | Tool | What we search for |
|-----|------------|----------|
| 1 | `navigate_symbol` (LSP) | Analogous functions/procedures in the project |
| 2 | `list_metadata_objects`, `get_metadata_structure` | Configuration objects (справочники, регистры, документы) |
| 3 | `search_syntax_reference`, `get_type_info` | Built-in types/methods of the platform |
| 4 | `search_ssl_functions` | БСП functions (if the configuration contains БСП) |
| 5a | `ask_ai_assistant` (SEARCH_DOCS template) | Platform documentation — when the question is about API, methods, types |
| 5b | `ask_ai_assistant` (SEARCH_ITS → FETCH_ITS template) | Standards, methodology, ИТС examples — when the question is about development rules |
| 5c | `ask_ai_assistant` (generic) | Other questions — only if 5a/5b do not fit |

> Prompt templates for steps 5a–5c — see the `buddy-prompting` skill.

## Triggers

| Task | Initial cascade step |
|--------|-----------------------|
| New function/procedure | 1 — search for name analogs |
| Business logic | 2 — search for metadata objects |
| Using the platform API | 3 — syntax reference; fallback 5a (documentation) |
| Print form | 2 → 4 (metadata + БСП API) |
| Development standards and rules | 5b — search in ИТС |
| Migration between versions | 5a (DIFF_VERSIONS template) |
| Query | 1 — existing queries in the project |

## Capabilities

| Capability | Purpose |
|------------|------------|
| `navigate_symbol` | Search for symbols, definitions, usages |
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
| `list_metadata_objects` returns nothing | Is the configuration loaded? `dump_config`; check metaType/nameMask |
| `navigate_symbol` returns nothing | Clarify the name (Rus/Lat, case); `ask_ai_assistant` (SEARCH_DOCS template) |
| `ask_ai_assistant` returns empty result | Reformulate the query; see rules in `buddy-prompting` |
| `search_ssl_functions` unavailable | Without БСП — use `search_syntax_reference` + `navigate_symbol` through common modules |

---
depends_on: []
---
