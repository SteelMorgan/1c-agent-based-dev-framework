---
name: code-navigation
description: "Code navigation (Code Navigation). The skill teaches the agent to **efficiently navigate BSL code** using LSP (Language Server Protocol)."
uses_capabilities:
  - navigate_symbol
  - get_call_graph
  - get_code_actions
  - rename_symbol
  - search_ssl_functions
---

# Code navigation (Code Navigation)

Do not guess where the code lives - use LSP. Precise results come from the project index.

## When to apply

| Trigger | Action |
|---------|--------|
| Finding definitions of a procedure/function | `navigate_symbol` operation `definition` |
| All calls to function X | `navigate_symbol` `search` or `get_call_graph` `incoming` |
| What a function calls | `get_call_graph` `outgoing` |
| Project-wide rename | `rename_symbol` (first `preview: true`) |
| Quick Fixes | `get_code_actions` |
| File diagnostics | `get_diagnostics` |
| Investigating unfamiliar code | `navigate_symbol` → `get_call_graph` → hover |
| "method not found" error on a platform type | `getMembers` / `getMember` / `getConstructors` |

## Algorithms

### Find all calls to a function

1. `navigate_symbol(query: "ИмяФункции", operation: "search")` → get `uri`, `line`, `character`
2. `get_call_graph(uri, line, character, direction: "incoming")`

### Project-wide rename

1. `navigate_symbol` → `uri`, `line`, `character`
2. `rename_symbol(..., preview: true)` → review `changes`
3. `rename_symbol(..., preview: false)`
4. `get_diagnostics` for the affected files

### Quick Fixes

1. `get_diagnostics(uri)` → list of diagnostics
2. `get_code_actions(uri, range, diagnostic)` → apply

### Platform API verification after an error

**Trigger:** error "Method not found" / "Wrong number of parameters" on a platform type. Do not guess again - verify.

1. `search_syntax_reference(query: "ТипОбъекта")` → confirm the name, get `id`
2. `getMembers(typeId)` → exact list of methods/properties
3. `getMember(typeId, member)` → signature of the specific method
4. `getConstructors(typeId)` → if the error is about `Новый` parameters

**Important:** Only react to the error, do not search proactively.

## Capabilities

| Capability | Purpose |
|------------|---------|
| `navigate_symbol` | Symbol search, definition, hover |
| `get_call_graph` | Call graph (incoming/outgoing) |
| `rename_symbol` | Project-wide rename |
| `get_diagnostics` | LSP file diagnostics |
| `get_code_actions` | Quick Fixes |
| `search_syntax_reference` | Platform type lookup |
| `getMembers` / `getMember` | Methods/properties of the platform type |
| `getConstructors` | Constructors of the type (`Новый`) |

## Typical errors

| Error | Workaround |
|-------|------------|
| LSP is not connected | Check `lsp_status`; start the BSL Language Server |
| Symbol not found | Check the name (case, language); `ask_ai_assistant` (SEARCH_DOCS template from `buddy-prompting`) by method/type name |
| `get_call_graph` timeout | Decrease `depth` |
| `rename_symbol` not applicable | Check the cursor position; protected area → manual editing |
| File not indexed | Wait for LSP indexing |

---
depends_on: []
---
