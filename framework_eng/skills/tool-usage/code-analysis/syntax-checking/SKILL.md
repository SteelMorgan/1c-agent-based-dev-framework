---
name: syntax-checking
description: Syntax checking (Syntax Checking). The skill teaches the agent to **properly use syntax-checking capabilities** for BSL code.
---

# Syntax Checking (Syntax Checking)

Any change to BSL code → immediate `check_syntax`. Without validation the agent may "successfully" finish the task with non-working code.

## When to apply

| Trigger | Action |
|---------|--------|
| After modifying BSL code | `check_syntax` |
| Before commit | Mandatory check of changed files |
| After refactoring / `rename_symbol` | Validate the affected modules |
| Compilation error | `check_syntax` to localize |

## Algorithm

1. `check_syntax(target: "путь/Module.bsl", mode: "edt")` — default mode (EDT validate).
2. `success = false` → read `errors`, fix them, repeat.
3. EDT unavailable → fallback: `mode: "designer_config"` (CheckConfig) or `mode: "designer_modules"` (CheckModules). Require connection to ИБ.
4. For quick feedback on the file — `get_diagnostics(uri)`. Partial substitute if `check_syntax` is unavailable.
5. After refactoring several modules — `target: "all"` or per module.

## Interpreting results

| Field | Action |
|-------|--------|
| `success: true` | Continue |
| `success: false` | Fix `errors` (each: `file`, `line`, `message`, `severity`) |
| `warnings` | Assess criticality |
| Timeout | Narrow `target` to individual modules |

Severity: `error` (blocks compilation) > `warning` > `information` / `hint`.

When `get_diagnostics` and `check_syntax` diverge — rely on `check_syntax` as the final verdict.

## Capabilities

| Capability | Purpose |
|------------|---------|
| `check_syntax` | Formal validation (EDT / Designer) |
| `get_diagnostics` | File diagnostics via LSP (fast) |

## Typical errors

| Error | Workaround |
|-------|------------|
| EDT not running | `get_diagnostics` or `designer_config` / `designer_modules` |
| Timeout for `target: "all"` | Validate modules individually |
| EDT project not found | Verify the path, `sourceSet`; use Designer |
| Unclear `errors` | `navigate_symbol` to the problematic location; `ask_ai_assistant` |

---
depends_on: []
---
