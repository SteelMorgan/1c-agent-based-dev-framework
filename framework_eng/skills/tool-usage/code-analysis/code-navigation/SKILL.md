---
name: code-navigation
description: Code Navigation. The skill teaches the agent to **navigate BSL code efficiently** using LSP (Language Server Protocol).
---

# Code Navigation (Code Navigation)

## Purpose

The skill trains the agent to **navigate BSL code efficiently** using LSP (Language Server Protocol). Navigation is the foundation of understanding the codebase, refactoring, and finding the root causes of bugs.

**Principle:** Do not guess where the code lives — use structured search. LSP delivers precise results from the project index.

---

## When to apply

| Trigger | Action |
|---------|--------|
| Finding a procedure/function definition | `navigate_symbol` operation `definition` |
| Finding all calls to function X | `navigate_symbol` operation `search` or `get_call_graph` direction `incoming` |
| Understand who a function calls | `get_call_graph` direction `outgoing` |
| Renaming a symbol across the project | `rename_symbol` (start with `preview: true`) |
| Quick fixes suggested by LSP | `get_code_actions` |
| Diagnosing the current file | `get_diagnostics` |
| Exploring unknown code | Chain: `navigate_symbol` → `get_call_graph` → hover |
| Error “method not found” / “wrong parameters” on a platform type | `getMembers` / `getMember` / `getConstructors` — verify the API |

---

## Use cases

### Use case 1: Find every caller of a function

**Steps:**

1. `navigate_symbol` with `query: "ПолучитьОстатки"`, `operation: "search"` — locate the definition.
2. Capture the definition's `uri`, `line`, `character`.
3. `get_call_graph` with `uri`, `line`, `character`, `direction: "incoming"` — who is calling it.
4. Alternatively use `navigate_symbol` with `operation: "search"` on the name and filter by the “references” kind.

**Example:** Find every call to `ПолучитьОстатки()`.

```
1. navigate_symbol(query: "ПолучитьОстатки", operation: "search")
2. Get the first result — definition
3. get_call_graph(uri: "...", line: N, character: M, direction: "incoming")
```

### Use case 2: Rename a symbol across the project

**Steps:**

1. `navigate_symbol` — find the symbol and get its `uri`, `line`, `character`.
2. `rename_symbol` with `preview: true` — preview changes in every file.
3. Review `changes` — ensure replacements are correct.
4. If everything looks good, rerun `rename_symbol` with `preview: false` to apply.
5. `check_syntax` — validate after renaming.

**Example:** Rename `ОбработатьДанные` to `ЗагрузитьДанные`.

```
1. navigate_symbol(query: "ОбработатьДанные", operation: "search")
2. rename_symbol(uri: "...", line: N, character: M, new_name: "ЗагрузитьДанные", preview: true)
3. Analyze changes
4. rename_symbol(..., preview: false)
5. check_syntax(...)
```

### Use case 3: Explore unknown code

**Steps:**

1. `navigate_symbol` (operation `search`) — find the symbol by name.
2. `navigate_symbol` (operation `hover`) — inspect documentation and type.
3. `get_call_graph` — understand incoming and outgoing calls.
4. Recursively traverse related symbols.

**Strategy:** `navigate_symbol` → `get_call_graph` → hover for the details.

### Use case 4: Quick fixes

**Steps:**

1. `get_diagnostics` for the file — gather diagnostics.
2. For each diagnostic with a `range`, call `get_code_actions` with `uri`, `range`, `diagnostic`.
3. Apply the suggested fix if it fits.

### Use case 5: Jump to definition from a usage site

**Steps:**

1. Start from known `uri`, `line`, `character` of the call site.
2. Call `navigate_symbol` with `operation: "definition"`, `uri`, `line`, `character`.
3. Result — `symbols` containing the definition (uri, range).

### Use case 6: Verify platform API after an error

**When:** code failed with “Method of object not found”, “Incorrect number of parameters”, “Field not found on object” — and the error points to a call on a *platform* type (not project BSL code).

**Do not guess again — verify.**

**Steps:**

1. Identify the object type from the error (for example, `ТабличныйДокумент`, `ДвоичныеДанные`, `HTTPСоединение`).
2. `search_syntax_reference(query: "ТабличныйДокумент")` — confirm the type name and obtain its `id`.
3. `getMembers(typeId: "...")` — get the precise list of methods and properties.
4. If you need a specific entry — `getMember(typeId: "...", member: "ЗаписатьPDF")`.
5. If the error complains about parameters — `getConstructors(typeId: "...")` for `Новый`-created types.
6. Fix the call based on the current signature.

**Example:** Error “Method of object not found (СохранитьВФайл)” on `ТабличныйДокумент`.
```
1. getMembers(typeId: "ТабличныйДокумент")
   → Locate writing methods: "Записать", "ЗаписатьPDF", "НапечататьМакет"
2. getMember(typeId: "ТабличныйДокумент", member: "Записать")
   → Signature: Записать(ИмяФайла, ТипФайлаТабличногоДокумента)
3. Correct the call
```

**Important:** This scenario addresses an error — not a preventive search. Do not call `getMembers` for every type while coding; only when the platform explicitly reported an issue.

---

## Search strategy for unknown code

| Step | Capability | Goal |
|-----|------------|------|
| 1 | `navigate_symbol` (search) | Find the symbol by name |
| 2 | `get_call_graph` | Understand call chains |
| 3 | `navigate_symbol` (hover) | Details: type, documentation, signature |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `navigate_symbol` | Symbol search, go-to-definition, hover |
| `get_call_graph` | Call graph (incoming/outgoing) |
| `rename_symbol` | Safe project-wide rename |
| `get_diagnostics` | LSP diagnostics for the file |
| `get_code_actions` | Quick fixes |
| `search_syntax_reference` | Locate a platform type by name (for verification after an error) |
| `getMembers` | List of all methods and properties for a platform type |
| `getMember` | Signature of a specific method/property |
| `getConstructors` | Constructors for a type (parameters for `Новый`) |

---

## Common issues and workarounds

| Issue | Workaround |
|--------|-------------|
| LSP server not connected | Capability `unavailable`; check `lsp_status` (if available); inform the user to start the BSL Language Server. |
| Symbol not found | Verify the name (case, language); try fuzzy search via `ask_ai_assistant`; ensure the file is in the project scope. |
| `get_call_graph` timeout | Reduce `depth`; inspect the graph in segments. |
| `rename_symbol` not applicable | Verify cursor position (symbol must be in the rename range); the symbol could be in a protected area; fall back to manual editing. |
| File not indexed | Wait until LSP indexing completes; `get_diagnostics` may return nothing until then. |
| `get_code_actions` empty | Not all diagnostics come with fixes; resolve manually based on the diagnostic `message`. |

---
depends_on: []
---
