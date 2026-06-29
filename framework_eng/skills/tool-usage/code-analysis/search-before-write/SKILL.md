---
name: search-before-write
description: "Find an existing equivalent before new BSL code"
alwaysApply: false
---

# Search Before Write

Any code-writing task is first and foremost a search task. First we search, then we write.

## Search Cascade

Each next step is used only if the previous one did not produce a result:

| Step | Tool | What we search for |
|-----|------------|----------|
| 1 | `navigate_symbol` (LSP) | Similar functions/procedures in the project |
| 2 | `list_metadata_objects`, `get_metadata_structure` | Configuration objects (catalogs, registers, documents) |
| 3 | `search_syntax_reference`, `get_type_info` | Built-in platform types/methods |
| 4 | `search_ssl_functions` | BСП functions (if the configuration includes BСП) |
| 5a | `ask_ai_assistant` (SEARCH_DOCS template) | Platform documentation - if the question is about API, methods, or types |
| 5b | `ask_ai_assistant` (SEARCH_ITS → FETCH_ITS template) | Standards, methodology, ITS examples - if the question is about development rules |
| 5c | `ask_ai_assistant` (generic) | Other questions - only if 5a/5b do not fit |

> Prompt templates for steps 5a-5c are in the `buddy-prompting` skill.

## Triggers

| Task | Initial cascade step |
|--------|-----------------------|
| New function/procedure | 1 - search for analogs by name |
| Business logic | 2 - search for metadata objects |
| Using the platform API | 3 - syntax help; fallback 5a (documentation) |
| Print form | 2 → 4 (metadata + BСП API) |
| Development standards and rules | 5b - search in ITS |
| Migration between versions | 5a (DIFF_VERSIONS template) |
| Query | 1 - existing queries in the project |

## Capabilities

| Capability | Purpose |
|------------|------------|
| `navigate_symbol` | Search symbols, definitions, usages |
| `list_metadata_objects` | Metadata objects by type and mask |
| `get_metadata_structure` | Object structure (attributes, dimensions, resources) |
| `search_syntax_reference` | Platform syntax reference |
| `get_type_info` | Platform type details |
| `search_ssl_functions` | BСП functions |
| `ask_ai_assistant` | Best practices, templates |

## Typical Mistakes

| Mistake | Workaround |
|--------|---------------|
| Skipping the search | Hard rule: code creation → first step = search |
| `list_metadata_objects` is empty | Is the configuration loaded? `v8-runner build` (or `v8-runner dump --mode incremental` if the information base is the source of truth); check metaType/nameMask |
| `navigate_symbol` is empty | Clarify the name (Russian/Latin, case); `ask_ai_assistant` (SEARCH_DOCS template) |
| `ask_ai_assistant` returns an empty result | Rephrase the query; see the rules in `buddy-prompting` |
| `search_ssl_functions` is unavailable | Without BСП - `search_syntax_reference` + `navigate_symbol` across common modules |

---
depends_on: []
---
