---
name: code-navigation
description: Code Navigation. The skill teaches the agent to **navigate BSL code effectively** using LSP (Language Server Protocol).
---

# Code Navigation (Code Navigation)

## Purpose

The skill teaches the agent to **navigate BSL code effectively** using LSP (Language Server Protocol). Navigation is the foundation for understanding the codebase, refactoring, and tracing issues.

**Principle:** Do not guess code locations — use structured search. LSP provides precise results based on the project index.

---

## When to apply

| Trigger | Action |
|---------|--------|
| Search for a procedure/function definition | `navigate_symbol` operation `definition` |
| Find all calls of function X | `navigate_symbol` operation `search` or `get_call_graph` direction `incoming` |
| Understand whom a function calls | `get_call_graph` direction `outgoing` |
| Rename a symbol across the project | `rename_symbol` (start with `preview: true`) |
| Quick fixes suggested by LSP | `get_code_actions` |
| Diagnostics of the current file | `get_diagnostics` |
| Exploring unfamiliar code | Sequence: `navigate_symbol` → `get_call_graph` → hover |
| Error “method not found” / “invalid parameters” on a platform type | `getMembers` / `getMember` / `getConstructors` — verify the API |

---

## Usage scenarios

### Scenario 1: Find every place that calls a function

**Steps:**

1. `navigate_symbol` with `query: "ПолучитьОстатки"`, `operation: "search"` — locate the definition.
2. Retrieve the definition’s `uri`, `line`, `character`.
3. `get_call_graph` with `uri`, `line`, `character`, `direction: "incoming"` — determine who calls it.
4. Alternatively, `navigate_symbol` with `operation: "search"` by name and filter by the “references” type.

**Example:** Find all calls of `ПолучитьОстатки()`.

```
1. navigate_symbol(query: "ПолучитьОстатки", operation: "search")
2. Retrieve the first result — definition
3. get_call_graph(uri: "...", line: N, character: M, direction: "incoming")
```

### Scenario 2: Rename a symbol across the project

**Steps:**

1. `navigate_symbol` — locate the symbol, get `uri`, `line`, `character`.
2. `rename_symbol` with `preview: true` — preview changes across all files.
3. Inspect `changes` — ensure replacements are correct.
4. If everything is fine — `rename_symbol` with `preview: false` to apply.
5. `check_syntax` — run a syntax check after the rename.

**Example:** Rename `ОбработатьДанные` to `ЗагрузитьДанные`.

```
1. navigate_symbol(query: "ОбработатьДанные", operation: "search")
2. rename_symbol(uri: "...", line: N, character: M, new_name: "ЗагрузитьДанные", preview: true)
3. Analyze changes
4. rename_symbol(..., preview: false)
5. check_syntax(...)
```

### Scenario 3: Explore unfamiliar code

**Steps:**

1. `navigate_symbol` (operation `search`) — locate the symbol by name.
2. `navigate_symbol` (operation `hover`) — fetch documentation and type information.
3. `get_call_graph` — understand incoming and outgoing calls.
4. Recursively jump to related symbols.

**Strategy:** `navigate_symbol` → `get_call_graph` → hover for details.

### Scenario 4: Quick Fixes

**Steps:**

1. `get_diagnostics` for the file — obtain the list of diagnostics.
2. For each diagnostic with a `range` — call `get_code_actions` with `uri`, `range`, `diagnostic`.
3. Apply the suggested fix if it’s appropriate.

### Scenario 5: Jump to the definition from a usage site

**Steps:**

1. Already know the usage site’s `uri`, `line`, `character`.
2. `navigate_symbol` with `operation: "definition"`, `uri`, `line`, `character`.
3. The result contains `symbols` with the definition (uri, range).

### Scenario 6: Verify platform API after an error

**When:** code fails with an error such as “Method of object not found,” “Incorrect number of parameters,” or “Object field not found,” and the error refers to invoking a method/property of a *platform* type (not project BSL code).

**Do not guess again — verify.**

**Steps:**

1. Identify the object type from the error (for example, `ТабличныйДокумент`, `ДвоичныеДанные`, `HTTPСоединение`).
2. `search_syntax_reference(query: "ТабличныйДокумент")` — confirm the type name and get its `id`.
3. `getMembers(typeId: "...")` — retrieve the exact list of methods and properties for the type.
4. Locate the correct method; if searching for a specific one — `getMember(typeId: "...", member: "ЗаписатьPDF")`.
5. If the issue concerns parameters — `getConstructors(typeId: "...")` for types instantiated via `Новый`.
6. Correct the call based on the actual signature.

**Example:** Error “Method of object not found (СохранитьВФайл)” on `ТабличныйДокумент`.
```
1. getMembers(typeId: "ТабличныйДокумент")
   → Find writing methods: "Записать", "ЗаписатьPDF", "НапечататьМакет"
2. getMember(typeId: "ТабличныйДокумент", member: "Записать")
   → Signature: Записать(ИмяФайла, ТипФайлаТабличногоДокумента)
3. Fix the call
```

**Important:** This scenario is a reaction to an error, not a preventive search. Do not invoke `getMembers` for every type while writing code — only when the platform explicitly reported an error.

---

## Search strategy for unfamiliar code

| Step | Capability | Goal |
|-----|------------|------|
| 1 | `navigate_symbol` (search) | Locate the symbol by name |
| 2 | `get_call_graph` | Understand call chains |
| 3 | `navigate_symbol` (hover) | Details: type, documentation, signature |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `navigate_symbol` | Search for symbols, jump to definitions, hover |
| `get_call_graph` | Call graph (incoming/outgoing) |
| `rename_symbol` | Safe renaming across the project |
| `get_diagnostics` | LSP diagnostics for a file |
| `get_code_actions` | Quick fixes |
| `search_syntax_reference` | Look up a platform type by name (for post-error verification) |
| `getMembers` | List all methods and properties of a platform type |
| `getMember` | Signature of a specific method/property of a type |
| `getConstructors` | Constructors for a type (parameters for `Новый`) |

---

## Common issues and workarounds

| Issue | Workaround |
|--------|-------------|
| LSP server is not connected | Capability `unavailable`; check `lsp_status` (if available); inform the user that BSL Language Server must be running. |
| Symbol not found | Verify the name (case, language); try fuzzy search via `ask_ai_assistant`; ensure the file is within the project scope. |
| `get_call_graph` timeout | Reduce `depth`; inspect the graph in segments. |
| `rename_symbol` inapplicable | Check the cursor position (symbol must be in the renameable region); the symbol may reside in a protected section; fall back to manual edits. |
| File not indexed | Wait for LSP indexing to finish; `get_diagnostics` may return nothing until indexing completes. |
| `get_code_actions` returns nothing | Not every diagnostic has a fix; apply manual changes guided by the diagnostic `message`. |

---
depends_on: []
---
