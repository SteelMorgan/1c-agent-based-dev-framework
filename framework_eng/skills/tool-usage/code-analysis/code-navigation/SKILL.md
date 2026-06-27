---
name: code-navigation
description: "BSL LSP navigation: definitions, refs, call graph"
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

Do not guess code location - use LSP. Exact results from the project index.

## When to use

| Trigger | Action |
|---------|----------|
| Search for procedure/function definitions | `navigate_symbol` operation `definition` |
| All calls to function X | `navigate_symbol` `search` or `get_call_graph` `incoming` |
| What a function calls | `get_call_graph` `outgoing` |
| Rename across the project | `rename_symbol` (first `preview: true`) |
| Quick Fixes | `get_code_actions` |
| File diagnostics | `get_diagnostics` |
| Investigating unknown code | `navigate_symbol` → `get_call_graph` → hover |
| Error "method not found" on a platform type | `getMembers` / `getMember` / `getConstructors` |
| Object structure / tabular sections / attributes, enum values, predefined items | `get_completion` after a dot (see "Metadata discovery") |
| Where a metadata object is used in code | `search_ssl_functions` references on `Документы.X` + grep (see below) |
| Estimate who a procedure/function change affects | `get_symbol_impact` (incoming + references + classification) |
| Parameter hints while writing a call | `signature_help` — cursor INSIDE the call parentheses (see "Parameter hints") |

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

**Trigger:** error "Object method not found" / "Incorrect number of parameters" on a platform type. Do not guess again - verify.

1. `search_syntax_reference(query: "ТипОбъекта")` → confirm the name, get `id`
2. `getMembers(typeId)` → exact list of methods/properties
3. `getMember(typeId, member)` → signature of a specific method
4. `getConstructors(typeId)` → if the error is about `Новый` parameters

**Important:** Only react to an error, not proactive search.

### Metadata discovery via `get_completion`

BSL LS Type System v2 exposes configuration metadata through completion. One tool answers different questions — only the cursor position changes (after a dot). In each item's `detail` is the signature and return type.

| Question | Where to place the cursor | What you get |
|----------|---------------------------|--------------|
| Object attributes / tabular sections / columns | after `Объект.` (a typed variable) | attributes, tabular sections, their columns, methods — with types |
| Enum values | after `Перечисления.ИмяПеречисления.` | enum values |
| Predefined items | after `Справочники.Имя.` / `ПланыСчетов.Имя.` | predefined items + manager methods |
| Composition of a DefinedType | cursor on an attribute of type ОпределяемыйТип | `get_completion` + `get_hover_info` reveal the composing types |

**Inverse signal:** if `get_completion` after `перем.` returns nothing or does not include the expected member, the variable type was inferred incorrectly/unknown. No completion here = a type error indicator (a common 1C bug), not "no data".

### Find where a metadata object is used in code

The picture is hybrid (how the object is used in BSL itself):

1. `search_ssl_functions` (references mode) on the manager symbol `Документы.ИмяОбъекта` → **semantically exact** manager-access locations. References exclude matches in comments/strings/query text.
2. **Supplement with grep** for what is not a symbol and therefore is not visible to references: string literals of the type (`"ДокументСсылка.ИмяОбъекта"`, `Тип("ДокументСсылка.…")`) and metadata paths inside query text (`ИЗ Документ.ИмяОбъекта`).

> For "where used", prefer references over a bare grep by name: grep produces false positives in comments and strings. Use grep only to pick up string/query usages.

### Change-impact analysis: `get_symbol_impact`

Before renaming/changing a procedure, estimate the blast radius in one call:

1. `navigate_symbol` → `uri`, `line`, `character` of the symbol.
2. `get_symbol_impact(uri, line, character)` → incoming callers (call hierarchy) + all references + **caller classification by module type** (CommonModule / FormModule / ManagerModule / ObjectModule / …) — shows where the change is pulled through from: UI, server, or background.

**Two blind spots (built into the output, keep in mind):**
- **Triggers.** Call hierarchy shows only direct calls. A method is also reached via event subscriptions, form handlers, scheduled jobs, extension `&Вместо/&Перед/&После` — this is NOT visible here (declarative; pick up via 1c-mcp/XML).
- **Namesakes.** For shared object methods (`ОбработкаПроведения`, `ПередЗаписью`, present in hundreds of modules), anchor call hierarchy to the specific module — otherwise the result will include false links from same-named methods of other objects.

### Parameter hints at the call site: `signature_help`

Returns the list of parameters for the called method and which argument the cursor is on. The contract is strict — an incorrect position yields an empty result that looks like "not supported", but in reality the position is wrong.

**You MUST pass `line`/`character` as 0-based, with the cursor INSIDE the call parentheses** — between `(` and `)`, NOT on the method name and NOT before `(`. The provider finds the enclosing call (`doCall`), resolves the called method, and only then returns signatures.

```
// Строка (1-based 8):  Аккаунт = ПолучитьАккаунт(ДокументОперации, ПараметрыОперации);
signature_help(uri, line=7, character=29)   // сразу после "(" → param 0
//   → ПолучитьАккаунт(ДокументОперации?, ПараметрыОперации?), Active parameter: 0
signature_help(uri, line=7, character=47)   // after comma   → Active parameter: 1
```

**Empty result ≠ tool is broken.** `signature_help` returns signatures only when the called method resolves to a method with a known parameter list. It works reliably for **methods in the same module** (resolution from parsed source). It returns empty for:
- cursor NOT inside the parentheses (on the name / before `(`) — the most common mistake;
- cross-module call (`Модуль.Метод(`) — requires the configuration type index, and works only when BSL LS is pointed at a single configuration root (see "Common mistakes": cross-module resolution);
- global platform methods (`СтрШаблон(`, `ЗначениеЗаполнено(`) and platform object methods (`Запрос.УстановитьПараметр(`) — requires a loaded **1C platform context** (`.hbk` syntax helper). The mcp-lsp container is lightweight — the platform is NOT baked into the image, it is provisioned at runtime. The `bsl-ls` run script generates a global config and passes it through `-Dapp.globalConfiguration.path`: when `BSL_PLATFORM_BIN` is set (compose `docker-compose.platform.yml` mounts the `.hbk` directory read-only) — the explicit `v8platform.binPath` takes priority; when it is not set — BSL LS auto-detects the installed platform (including Windows). Without either, the startup log shows `Failed to load platform contexts: No 1C platform installations found`, and platform hover/completion/signatures are empty. With a loaded context (`Loaded N platform contexts from 1C syntax helper`) — they resolve with full parameter docs (in Russian when `language:ru`).

For a confirmed parameter list regardless of call site - `getMember(typeId, member)` (platform types) or `navigate_symbol`→hover (the method's own declaration).

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
| `signature_help` | Parameter hints at the call site (cursor inside parentheses) |

## Common mistakes

| Error | Workaround |
|--------|------------|
| LSP is not connected | Check `lsp_status`; start the BSL Language Server |
| Symbol not found | Check the name (case, language); `ask_ai_assistant` (SEARCH_DOCS template from `buddy-prompting`) by method/type name |
| `get_call_graph` times out | Reduce `depth` |
| `rename_symbol` is not applicable | Check cursor position; protected area → manual editing |
| File is not indexed | Wait for LSP indexing |
| `signature_help` returns empty | The cursor must be INSIDE the call parentheses (0-based), not on the method name; reliable for methods in the same module. For cross-module/global calls see the line below |
| Cross-module `Модуль.Метод()` does not resolve (empty `signature_help`, cross-module call hierarchy loses links, false `QueryToMissingMetadata`) | BSL LS must index a SINGLE main configuration root `src/xml`, not the project root with 9 `Configuration.xml` files (main + extensions). Session-manager auto-detects the main configuration root under the passed workspace (`detectConfigurationRoot`), so this should be fixed. If it regresses, check the bsl-ls startup log for `Detected 1C configuration root: …/src/xml`; absence means detection failed (for example, the workspace has no main `Configuration.xml` or the binary is older than the fix) → reindexing is looking at the wrong tree |

---
depends_on: []
---
