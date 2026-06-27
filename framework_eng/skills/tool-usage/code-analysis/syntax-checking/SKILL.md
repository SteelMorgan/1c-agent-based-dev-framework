---
name: syntax-checking
description: "Before BSL handoff: LSP and full syntax check"
uses_capabilities:
  - get_diagnostics
  - get_quality_diagnostics
  - get_method_complexity
  - get_module_health
  - syntax_check_designer_modules
  - syntax_check_designer_config
  - syntax_check_edt
alwaysApply: false
---

# Syntax Checking

Any change to BSL code requires immediate validation. Without checking, the agent can "successfully" complete the task with broken code.

**Two levels of checking — different cost:**

| Tool | Speed | When to use |
|------------|----------|-------------------|
| `get_diagnostics` (LSP) | Fast (seconds) | After every change, intermediate checks |
| `v8-runner syntax …` | Slow (dozens of seconds to minutes) | Final check: before commit, before PR, after major refactoring |

Server-side checking is now done **only through the `v8-runner` CLI** — separate MCP tools `check_syntax`/`build_project`/`dump_config` have been deprecated. Details of the commands and selection rules are in the `v8-runner` skill (`framework/skills/tool-usage/v8-runner/`).

## When to use

| Trigger | Action |
|---------|----------|
| After changing BSL code | `get_diagnostics` — fast check |
| Iterative editing (edit → check loop) | `get_diagnostics` |
| After refactoring / `rename_symbol` | `get_diagnostics` for the affected files |
| Compilation error | `get_diagnostics` for localization |
| **Before commit / before PR** | **`v8-runner syntax …`** — final check |
| **Task completion** | **`v8-runner syntax …`** — final verdict |

## Quality self-checking (besides syntax)

Syntax is the minimum required, but "compiles" does not mean "high quality". After
`get_diagnostics`/`v8-runner syntax` confirm there are no errors, run a self-check
on the modified files — it is cheap (LSP, seconds) and catches what syntax misses:

| Capability | What it shows | When to use |
|------------|----------------|----------------|
| `get_diagnostics` | All BSL LS diagnostics for the file (more precise than workspace checks) | After edits - the full list of findings for the file |
| `get_quality_diagnostics` | Only security / performance / sql (query in a loop, disabling safe mode, missing aliases, etc.) | Before commit - targeted coder self-check for risks |
| `get_method_complexity` | Cyclomatic + cognitive complexity by method, flags threshold breaches | After writing/editing a method - a signal that it is time to refactor (cyclomatic > 20 / cognitive > 15) |
| `get_module_health` | Combo: complexity + security/perf/sql, aggregated by method and ranked by "what to refactor first" | Triage of the whole module in one call - instead of separate complexity + quality_diagnostics + manual aggregation |

> This is **coder self-checking**, not a replacement for review. `get_quality_diagnostics`,
> `get_method_complexity` and `get_module_health` rely on BSL LS; complexity metrics
> require the complexity CodeLens to be enabled in the BSL LS config. For metrics of one method,
> use `get_method_complexity`; for one risk category, use `get_quality_diagnostics`; for
> triage of the entire module, use `get_module_health` (the individual ones do not cancel each other out).

## Checking algorithm

### Intermediate check (after each change)

1. `get_diagnostics(uri)` — LSP diagnostics for the changed file.
2. If there is an `error`-level issue, fix it and repeat.
3. `warning` — assess criticality.

### Final check (before commit)

The choice of command depends on `format`/`builder` in `v8project.yaml` (see `v8-runner/references/config-and-backends.md`):

```bash
# Designer modules (requires Designer + Designer format)
v8-runner build
v8-runner syntax designer-modules --server --thin-client

# Designer configuration
v8-runner build
v8-runner syntax designer-config

# EDT
v8-runner build
v8-runner syntax edt
```

Tests (`v8-runner test yaxunit …`, `test va`) run `build` themselves — a separate `build` before them is not needed.

If LSP and `v8-runner syntax` disagree, rely on v8-runner as the final verdict.

## Interpreting results

| Field | Action |
|------|----------|
| `success: true` | Continue |
| `success: false` | Fix `errors` (each: `file`, `line`, `message`, `severity`) |
| `warnings` | Assess criticality |
| Timeout | Narrow the scope (`--source-set <NAME>`) or return to LSP for specific modules |

Severity: `error` (blocks compilation) > `warning` > `information` / `hint`.

## Suppression markers as evidence

A suppression comment is **evidence**, not decorative noise. From it, extract concrete codes and check whether disabling is justified.

### Marker syntax

| Tool | Syntax |
|------------|-----------|
| **APK** | `//{ APK:142 - comment` … `//}` |
| **BSL Language Server** | `// BSLLS:LineLength-off` … `// BSLLS:LineLength-on` |
| **EDT** | `// @suppress-warning("module-empty-method")` or `//@skip-check` |

### Interpretation method

1. **Extract literal codes** from the comment: a numeric or mnemonic identifier (APK:142, LineLength, the EDT rule name).
2. **Resolve through the standards reference**: for APK codes — `ask_1c_ai` ("decode diagnostic APK:142"); for BSL LS — ITS documentation for the rule name; for EDT — EDT rules documentation.
3. **Mark as justified** only with **triple support**: literal code + suppression range + reference to the standard. If at least one element is missing, mark it as "suppression not justified".
4. **Action priority**: first fix the code -> then narrow the suppression range -> keep suppression only as a last resort with an explicit reference to the standard or a platform limitation.

### Sign of "suppression not justified"

The comment contains neither the diagnostic code nor a reference to the standard — it must be flagged for review.

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
|------------------|------------|-----------|
| `get_diagnostics` (MCP `lsp-bsl-bridge`) | LSP diagnostics for a file | Fast - the main tool |
| `v8-runner syntax designer-modules` | Check Designer modules through the platform | Slow - final check only |
| `v8-runner syntax designer-config` | Check Designer configuration | Slow |
| `v8-runner syntax edt` | Check an EDT project | Slow |

## Final check result monitoring

`v8-runner syntax …` can take dozens of seconds to minutes. For long runs, use the Monitor tool:

1. Start it in the background (`Bash run_in_background: true`), redirect stdout to a log file.
2. Subscribe through **Monitor** with the filter `ERROR:|error:|Errors:|success` - you will get a notification on the first match.
3. End the wait when the process finishes OR `error:` / `Errors: 0` / `success` appears in stdout.
4. After completion, read the final result from stdout: if there are errors - file, line, text.

For short runs (`--source-set <NAME>`), Monitor is not required - a synchronous run is enough.

## Typical errors

| Error | Workaround |
|--------|---------------|
| LSP is not running | `v8-runner syntax …` as a fallback |
| The `syntax …` command is not supported for the current `format`/`builder` | See `v8-runner/references/config-and-backends.md`; do not invent raw `1cv8`/`ibcmd` flags |
| Timeout on the full check | Narrow it with `--source-set <NAME>`; LSP for specific modules |
| The EDT project is not found | Check `format`/`builder` and `source-set` in `v8project.yaml` |
| Unclear `errors` | `navigate_symbol` to the error location; `ask_ai_assistant` |

---
depends_on:
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
