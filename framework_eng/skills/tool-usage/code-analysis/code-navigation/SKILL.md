---
name: code-navigation
description: "Use for navigating BSL code through LSP (finding definitions, references, call graphs, renaming). Helps precisely locate symbols from the project index without guessing their location."
uses_capabilities:
  - navigate_symbol
  - get_call_graph
  - get_code_actions
  - rename_symbol
  - search_ssl_functions
---

# Code Navigation

Do not guess where code is located - use LSP. Accurate results from the project index.

## When to use

| Trigger | Action |
|---------|----------|
| Search for procedure/function definitions | `navigate_symbol` operation `definition` |
| All calls to function X | `navigate_symbol` `search` or `get_call_graph` `incoming` |
| Who a function calls | `get_call_graph` `outgoing` |
| Rename across the project | `rename_symbol` (first `preview: true`) |
| Quick Fixes | `get_code_actions` |
| File diagnostics | `get_diagnostics` |
| Investigating unknown code | `navigate_symbol` → `get_call_graph` → hover |
| Error «method not found» on a platform type | `getMembers` / `getMember` / `getConstructors` |

## Algorithms

### Find all calls to a function

1. `navigate_symbol(query: "FunctionName", operation: "search")` → get `uri`, `line`, `character`
2. `get_call_graph(uri, line, character, direction: "incoming")`

### Rename across the project

1. `navigate_symbol` → `uri`, `line`, `character`
2. `rename_symbol(..., preview: true)` → check `changes`
3. `rename_symbol(..., preview: false)`
4. `get_diagnostics` for affected files

### Quick Fixes

1. `get_diagnostics(uri)` → list of diagnostics
2. `get_code_actions(uri, range, diagnostic)` → apply

### Verify the platform API after an error

**Trigger:** error «Object method not found» / «Incorrect number of parameters» on a platform type. Do not guess again - verify.

1. `search_syntax_reference(query: "ТипОбъекта")` → confirm the name, get `id`
2. `getMembers(typeId)` → exact list of methods/properties
3. `getMember(typeId, member)` → signature of a specific method
4. `getConstructors(typeId)` → if the error is about `Новый` parameters

**Important:** Only react to an error, not proactive search.

## Capabilities

| Capability | Purpose |
|------------|---------|
| `navigate_symbol` | Search symbols, definition, hover |
| `get_call_graph` | Call graph (incoming/outgoing) |
| `rename_symbol` | Rename across the project |
| `get_diagnostics` | LSP diagnostics for a file |
| `get_code_actions` | Quick Fixes |
| `search_syntax_reference` | Search for a platform type |
| `getMembers` / `getMember` | Methods/properties of a platform type |
| `getConstructors` | Type constructors (`Новый`) |

## Common mistakes

| Error | Workaround |
|--------|------------|
| LSP is not connected | Check `lsp_status`; start the BSL Language Server |
| Symbol not found | Check the name (case, language); `ask_ai_assistant` (SEARCH_DOCS template from `buddy-prompting`) by method/type name |
| `get_call_graph` times out | Reduce `depth` |
| `rename_symbol` is not applicable | Check cursor position; protected area → manual editing |
| File is not indexed | Wait for LSP indexing |

---
depends_on: []
---
