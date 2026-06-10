---
name: code-navigation
description: "Use for navigating BSL code through LSP (finding definitions, references, call graphs, renaming). Helps precisely locate symbols from the project index without guessing their location."
uses_capabilities:
  - navigate_symbol
  - get_call_graph
  - get_code_actions
  - rename_symbol
  - search_ssl_functions
  - get_completion
  - get_symbol_impact
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
| Object structure / tabular sections / attributes, enum values, predefined items | `get_completion` after a dot (see «Metadata discovery») |
| Where a metadata object is used in code | `search_ssl_functions` references on `Документы.X` + grep (see below) |
| Estimate who a procedure/function change affects | `get_symbol_impact` (callers + references + classification) |

## Algorithms

### Find all calls to a function

1. `navigate_symbol(query: "ИмяФункции", operation: "search")` → get `uri`, `line`, `character`
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

### Metadata discovery via `get_completion`

BSL LS Type System v2 serves configuration metadata through completion. One tool answers different questions — only the cursor position changes (after a dot). Each item's `detail` carries the signature and the return type.

| Question | Where to place the cursor | What you get |
|----------|---------------------------|--------------|
| Object attributes / tabular sections / columns | after `Объект.` (a typed variable) | attributes, tabular sections, their columns, methods — with types |
| Enum values | after `Перечисления.ИмяПеречисления.` | the enum values |
| Predefined items | after `Справочники.Имя.` / `ПланыСчетов.Имя.` | predefined items + manager methods |
| Composition of a DefinedType | cursor on an attribute of type ОпределяемыйТип | `get_completion` + `get_hover_info` expand the composing types |

**Inverse signal:** if `get_completion` after `перем.` returns nothing or lacks the expected member, the variable's type is inferred wrong/unknown. An absent completion here is a type-error indicator (a common 1C bug), not «no data».

### Find where a metadata object is used in code

The picture is hybrid (matching how the object is actually used in BSL):

1. `search_ssl_functions` (references mode) on the manager symbol `Документы.ИмяОбъекта` → **semantically precise** manager-access sites. References exclude matches in comments/strings/query text.
2. **Complement with grep** for what is not a symbol and therefore invisible to references: string type literals (`"ДокументСсылка.ИмяОбъекта"`, `Тип("ДокументСсылка.…")`) and metadata paths inside query text (`ИЗ Документ.ИмяОбъекта`).

> For «where used», prefer references over a bare name grep: grep gives false positives in comments and strings. grep only picks up the string/query usages.

### Change-impact analysis: `get_symbol_impact`

Before renaming/changing a procedure, estimate the blast radius in one call:

1. `navigate_symbol` → `uri`, `line`, `character` of the symbol.
2. `get_symbol_impact(uri, line, character)` → incoming callers (call hierarchy) + all references + **caller classification by module type** (CommonModule / FormModule / ManagerModule / ObjectModule / …) — you see where the change is pulled from: UI, server, or background.

**Two blind spots (built into the output, keep in mind):**
- **Triggers.** Call hierarchy shows only direct calls. A method is also reached via event subscriptions, form handlers, scheduled jobs, extension `&Вместо/&Перед/&После` — NOT visible here (declarative; pick up via 1c-mcp/XML).
- **Namesakes.** For shared object methods (`ОбработкаПроведения`, `ПередЗаписью`, present in hundreds of modules), anchor call hierarchy to the specific module — otherwise the result includes false edges from same-named methods of other objects.

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
| `get_completion` | Metadata discovery: object/tabular-section members, enum values, predefined items, types |
| `get_symbol_impact` | Impact analysis: incoming callers + references + caller classification by module type |

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
