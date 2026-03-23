---
name: code-navigation
description: Code Navigation (Code Navigation). The skill teaches the agent to **efficiently navigate BSL code** using LSP (Language Server Protocol).
---

# Code Navigation (Code Navigation)

Don't guess where the code is - use LSP. Precise results come from the project index.

## When to apply

| Trigger | Action |
|---------|--------|
| Finding the definition of a procedure/function | `navigate_symbol` operation `definition` |
| All calls to function X | `navigate_symbol` `search` or `get_call_graph` `incoming` |
| Who a function calls | `get_call_graph` `outgoing` |
| Project-wide rename | `rename_symbol` (start with `preview: true`) |
| Quick Fixes | `get_code_actions` |
| File diagnostics | `get_diagnostics` |
| Investigating unfamiliar code | `navigate_symbol` -> `get_call_graph` -> hover |
| "method not found" error on a platform type | `getMembers` / `getMember` / `getConstructors` |

## Algorithms

### Find all calls to a function

1. `navigate_symbol(query: "ИмяФункции", operation: "search")` -> get `uri`, `line`, `character`
2. `get_call_graph(uri, line, character, direction: "incoming")`

### Project-wide rename

1. `navigate_symbol` -> `uri`, `line`, `character`
2. `rename_symbol(..., preview: true)` -> review `changes`
3. `rename_symbol(..., preview: false)`
4. `check_syntax`

### Quick Fixes

1. `get_diagnostics(uri)` -> list the diagnostics
2. `get_code_actions(uri, range, diagnostic)` -> apply

### Platform API verification after an error

**Trigger:** "Object method not found" / "Wrong number of parameters" on a platform type. Don't guess again - verify.

1. `search_syntax_reference(query: "ТипОбъекта")` -> confirm the name, collect the `id`
2. `getMembers(typeId)` -> exact list of methods/properties
3. `getMember(typeId, member)` -> signature of the specific method
4. `getConstructors(typeId)` -> if the error mentions parameters of `Новый`

**Important:** Only react when an error has occurred, not proactively.

## Capabilities

| Capability | Purpose |
|------------|---------|
| `navigate_symbol` | Symbol search, definition, hover |
| `get_call_graph` | Call graph (incoming/outgoing) |
| `rename_symbol` | Project-wide rename |
| `get_diagnostics` | LSP file diagnostics |
| `get_code_actions` | Quick Fixes |
| `search_syntax_reference` | Platform type lookup |
| `getMembers` / `getMember` | Methods/properties of a platform type |
| `getConstructors` | Constructors of the type (`Новый`) |

## Typical errors

| Error | Workaround |
|-------|------------|
| LSP is not connected | Check `lsp_status`; start the BSL Language Server |
| Symbol not found | Verify the name (case, language); `ask_ai_assistant` (SEARCH_DOCS template from `buddy-prompting`) by the method/type name |
| `get_call_graph` timeout | Decrease `depth` |
| `rename_symbol` not applicable | Check the cursor position; protected region -> edit manually |
| File not indexed | Wait for the LSP indexing |

---
depends_on: []
---
