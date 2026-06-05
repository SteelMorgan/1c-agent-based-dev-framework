---
name: syntax-checking
description: "MUST use BEFORE committing or handing BSL code off for review. Defines a two-level process (LSP get_diagnostics → full Configurator check) as proof that there are no syntax errors."
uses_capabilities:
  - get_diagnostics
  - syntax_check_designer_modules
  - syntax_check_designer_config
  - syntax_check_edt
alwaysApply: false
---

# Syntax Checking

Any BSL code change → immediate verification. Without verification, the agent can "successfully" complete a task with non-working code.

**Two levels of verification — different cost:**

| Tool | Speed | When to use |
|------------|----------|-------------------|
| `get_diagnostics` (LSP) | Fast (seconds) | After every change, intermediate checks |
| `v8-runner syntax …` | Slow (tens of seconds — minutes) | Final check: before commit, before PR, after a major refactor |

Server-side verification is now done **only through the `v8-runner` CLI** — the separate MCP tools `check_syntax`/`build_project`/`dump_config` have been removed. Details of the commands and selection rules are in the `v8-runner` skill (`framework/skills/tool-usage/v8-runner/`).

## When to apply

| Trigger | Action |
|---------|----------|
| After changing BSL code | `get_diagnostics` — fast check |
| Iterative edit cycle (edit → check) | `get_diagnostics` |
| After refactoring / `rename_symbol` | `get_diagnostics` for affected files |
| Compilation error | `get_diagnostics` for localization |
| **Before commit / before PR** | **`v8-runner syntax …`** — final check |
| **Task completion** | **`v8-runner syntax …`** — final verdict |

## Verification algorithm

### Intermediate check (after every change)

1. `get_diagnostics(uri)` — LSP diagnostics for the changed file.
2. If there is an `error` level — fix it and repeat.
3. `warning` — assess criticality.

### Final check (before commit)

The command choice depends on `format`/`builder` in `v8project.yaml` (see `v8-runner/references/config-and-backends.md`):

```bash
# Designer-модули (requires Designer + Designer-format)
v8-runner build
v8-runner syntax designer-modules --server --thin-client

# Designer-конфигурация
v8-runner build
v8-runner syntax designer-config

# EDT
v8-runner build
v8-runner syntax edt
```

Tests (`v8-runner test yaxunit …`, `test va`) run `build` themselves — a separate `build` before them is not needed.

If `get_diagnostics` and `v8-runner syntax` disagree, rely on `v8-runner` as the final verdict.

## Result interpretation

| Field | Action |
|------|----------|
| `success: true` | Continue |
| `success: false` | Fix `errors` (each: `file`, `line`, `message`, `severity`) |
| `warnings` | Assess criticality |
| Timeout | Narrow the scope (`--source-set <NAME>`) or return to LSP for specific modules |

Severity: `error` (blocks compilation) > `warning` > `information` / `hint`.

## Suppression markers as evidence

Suppression comment is a clue, not decorative noise. Extract concrete codes from it and assess whether the suppression is justified.

### Marker syntax

| Tool | Syntax |
|------------|-----------|
| **АПК** | `//{ АПК:142 - comment` … `//}` |
| **BSL Language Server** | `// BSLLS:LineLength-off` … `// BSLLS:LineLength-on` |
| **EDT** | `// @suppress-warning("module-empty-method")` or `//@skip-check` |

### Interpretation method

1. **Extract the literal codes** from the comment: a numeric or mnemonic identifier (АПК:142, LineLength, EDT rule name).
2. **Resolve through the standards reference**: for АПК codes — `ask_1c_ai` ("decode the АПК:142 diagnostic"); for BSL LS — ITS documentation by rule name; for EDT — EDT rules documentation.
3. **Consider it justified** only with **triple corroboration**: literal code + suppression range + link to the standard. If at least one element is missing, mark it as "suppression not justified".
4. **Action priority**: first fix the code → then narrow the suppression range → leave suppression only as a last resort with an explicit link to the standard or platform limitation.

### Sign of "suppression not justified"

The comment contains neither a diagnostic code nor a link to the standard — it must be flagged for review.

```bsl
// Плохо — нет кода, нет обоснования:
// BSLLS:LineLength-off
ОченьДлиннаяСтрокаБезПояснения = ...
// BSLLS:LineLength-on

// Хорошо — код + диапазон + обоснование:
// BSLLS:LineLength-off // АПК:142: строка формирования запроса, разбиение ухудшает читаемость (ITS: стандарт 720)
ТекстЗапроса = "SELECT ... FROM ...";
// BSLLS:LineLength-on
```

## Capabilities and tools

| Capability / CLI | Purpose | Cost |
|------------------|-----------|-----------|
| `get_diagnostics` (MCP `lsp-bsl-bridge`) | LSP diagnostics for a file | Fast — primary tool |
| `v8-runner syntax designer-modules` | Check Designer modules through the platform | Slow — final check only |
| `v8-runner syntax designer-config` | Check Designer configuration | Slow |
| `v8-runner syntax edt` | Check an EDT project | Slow |

## Monitoring the Final Check

`v8-runner syntax …` can take tens of seconds to minutes. For long runs use the Monitor tool:

1. Launch in the background (`Bash run_in_background: true`) and redirect stdout to a log file.
2. Subscribe via **Monitor** with the filter `ERROR:|error:|Errors:|success` — a notification arrives on the first match.
3. Stop waiting when the process exits OR stdout contains `error:` / `Errors: 0` / `success`.
4. After completion, read the result from stdout: if errors are present, note the file, line, and text.

For short runs (`--source-set <NAME>`) Monitor is not required — synchronous execution is enough.

## Typical mistakes

| Error | Workaround |
|--------|---------------|
| LSP is not running | `v8-runner syntax …` as a fallback |
| The `syntax …` command is not supported for the current `format`/`builder` | See `v8-runner/references/config-and-backends.md`; do not invent raw `1cv8`/`ibcmd` flags |
| Timeout on a full check | Narrow it with `--source-set <NAME>`; use LSP for specific modules |
| EDT project not found | Check `format`/`builder` and `source-set` in `v8project.yaml` |
| Unclear `errors` | `navigate_symbol` to the error location; `ask_ai_assistant` |

---
depends_on:
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
