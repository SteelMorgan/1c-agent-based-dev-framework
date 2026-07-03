---
name: search-before-write
description: "Find an existing equivalent before new BSL code"
alwaysApply: false
---

# Search Before Write

Any code-writing task is first and foremost a search task. First we search, then we write.

For 1C/BSL projects with RLM available, broad reconnaissance starts with `rlm_start` / `rlm_execute`, not with LSP or raw grep. Use LSP after RLM, when a concrete file, symbol, or cursor position is already known.

## Search Cascade

Each next step is used only if the previous one did not produce a result:

| Step | Tool | What we search for |
|-----|------------|----------|
| 1 | `rlm_start` → `rlm_execute` | Broad search for analogs, business mechanisms, queries, XML/MDO/forms/rights, and project relationships |
| 2 | `navigate_symbol` / BSL LS | Exact verification of a found symbol, definition/references/call graph by known position |
| 3 | `list_metadata_objects`, `get_metadata_structure` | Configuration objects (catalogs, registers, documents), if RLM is unavailable or a structured MCP response is needed |
| 4 | `search_syntax_reference`, `get_type_info` | Built-in platform types/methods |
| 5 | `search_ssl_functions` | BСП functions (if the configuration includes BСП) |
| 6a | `ask_ai_assistant` (SEARCH_DOCS template) | Platform documentation - if the question is about API, methods, or types |
| 6b | `ask_ai_assistant` (SEARCH_ITS → FETCH_ITS template) | Standards, methodology, ITS examples - if the question is about development rules |
| 6c | `ask_ai_assistant` (generic) | Other questions - only if 6a/6b do not fit |

> Prompt templates for steps 6a-6c are in the `buddy-prompting` skill.

## Triggers

| Task | Initial cascade step |
|--------|-----------------------|
| New function/procedure | 1 - RLM search for analogs by meaning, name, and neighboring terms |
| Business logic | 1 - RLM reconnaissance of the mechanism and related objects/modules |
| Using the platform API | 4 - syntax help; fallback 6a (documentation) |
| Print form | 1 → 5 (RLM over forms/templates/commands + BСП API) |
| Development standards and rules | 6b - search in ITS |
| Migration between versions | 6a (DIFF_VERSIONS template) |
| Query | 1 - RLM search for existing queries and related registers/objects |

## Capabilities

| Capability | Purpose |
|------------|------------|
| `rlm_start` / `rlm_execute` | Broad project reconnaissance over 1C/BSL before writing code |
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
| Skipping the search | Hard rule: BSL code creation → first step = RLM reconnaissance, if available |
| Started with `navigate_symbol`, but there is no exact name/position | Return to `rlm_start`: the task is broad, LSP is premature here |
| `list_metadata_objects` is empty | Is the configuration loaded? `v8-runner build` (or `v8-runner dump --mode incremental` if the information base is the source of truth); check metaType/nameMask |
| `navigate_symbol` is empty | If this is broad reconnaissance, switch to RLM. If this is a positional LSP task, clarify the name (Russian/Latin, case) |
| `ask_ai_assistant` returns an empty result | Rephrase the query; see the rules in `buddy-prompting` |
| `search_ssl_functions` is unavailable | Without BСП - `search_syntax_reference` + `navigate_symbol` across common modules |

---
depends_on:
  - framework/skills/tool-usage/code-analysis/rlm-bsl-search/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
---
