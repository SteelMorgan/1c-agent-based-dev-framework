---
name: syntax-checking
description: Syntax checking (Syntax Checking). The skill teaches the agent to **properly use the syntax checking capabilities** for BSL code.
---

# Syntax Checking (Syntax Checking)

Any change to BSL code → immediate validation. Without checking the agent might “successfully” finish the task with non-working code.

**Two tools — different cost:**

| Instrument | Speed | When to use |
|------------|-------|-------------|
| `get_diagnostics` | Fast (seconds) | After every change, intermediate checks |
| `check_syntax` | Slow (tens of seconds — minutes) | Final verification: before commit, before PR, after major refactoring |

## When to apply

| Trigger | Action |
|---------|--------|
| After modifying BSL code | `get_diagnostics` — quick check |
| Iterative edit cycle (edit → check) | `get_diagnostics` |
| After refactoring / `rename_symbol` | `get_diagnostics` for affected files |
| Compilation error | `get_diagnostics` for localization |
| **Before commit / before PR** | **`check_syntax`** — final check |
| **Task completion** | **`check_syntax`** — final verdict |

## Checking algorithm

### Intermediate check (after each change)

1. `get_diagnostics(uri)` — LSP diagnostics of the modified file.
2. If there is an `error` level issue — fix it and repeat.
3. `warning` — assess criticality.

### Final check (before commit)

1. `check_syntax(target: "путь/Module.bsl", mode: "edt")` — default mode.
2. `success = false` → read `errors`, fix them, repeat.
3. EDT unavailable → fallback: `mode: "designer_config"` (CheckConfig) or `mode: "designer_modules"` (CheckModules). Require connection to ИБ.
4. After refactoring multiple modules — use `target: "all"` or run per module.

## Interpreting results

| Field | Action |
|-------|--------|
| `success: true` | Proceed |
| `success: false` | Fix `errors` (each contains `file`, `line`, `message`, `severity`) |
| `warnings` | Evaluate criticality |
| Timeout | Narrow `target` to individual modules |

Severity: `error` (blocks compilation) > `warning` > `information` / `hint`.

If `get_diagnostics` and `check_syntax` disagree — treat `check_syntax` as the final verdict.

## Capabilities

| Capability | Purpose | Cost |
|------------|---------|------|
| `get_diagnostics` | File diagnostics via LSP | Fast — primary tool |
| `check_syntax` | Formal compiler validation | Slow — only for final verification |

## Typical errors

| Error | Workaround |
|-------|------------|
| LSP not running | `check_syntax` as fallback |
| EDT not running | `get_diagnostics` or `designer_config` / `designer_modules` |
| Timeout for `target: "all"` | Check modules individually |
| EDT project not found | Verify the path, `sourceSet`; use Designer |
| Unclear `errors` | `navigate_symbol` to the error location; `ask_ai_assistant` |

---
depends_on: []
---
