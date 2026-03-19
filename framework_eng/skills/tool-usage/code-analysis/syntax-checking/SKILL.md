---
name: syntax-checking
description: Syntax Checking. The skill teaches the agent to **properly use the syntax checking capabilities** for BSL code.
---

# Syntax Checking (Syntax Checking)

Any change to BSL code → immediate `check_syntax`. Without the check the agent can "successfully" finish the task with non-working code.

## When to apply

| Trigger | Action |
|---------|--------|
| After modifying BSL code | `check_syntax` |
| Before a commit | Mandatory verification of the changed files |
| After refactoring / `rename_symbol` | Check the affected modules |
| Compilation error | `check_syntax` to localize |

## Checking algorithm

1. `check_syntax(target: "путь/Module.bsl", mode: "edt")` — default mode (EDT validate).
2. `success = false` → read `errors`, fix them, repeat.
3. EDT unavailable → fallback: `mode: "designer_config"` (CheckConfig) or `mode: "designer_modules"` (CheckModules). Require connection to the infobase.
4. For quick feedback on a file — `get_diagnostics(uri)`. A partial replacement if `check_syntax` is unavailable.
5. After refactoring multiple modules — `target: "all"` or each one individually.

## Interpreting results

| Field | Action |
|------|--------|
| `success: true` | Continue |
| `success: false` | Fix `errors` (each: `file`, `line`, `message`, `severity`) |
| `warnings` | Assess severity |
| Timeout | Narrow `target` to individual modules |

Severity: `error` (blocks compilation) > `warning` > `information` / `hint`.

When `get_diagnostics` and `check_syntax` disagree — rely on `check_syntax` as the final verdict.

## Capabilities

| Capability | Purpose |
|------------|---------|
| `check_syntax` | Formal checking (EDT / Designer) |
| `get_diagnostics` | LSP diagnostics for the file (fast) |

## Common mistakes

| Mistake | Workaround |
|--------|------------|
| EDT not running | `get_diagnostics` or `designer_config` / `designer_modules` |
| Timeout on `target: "all"` | Check per-module |
| EDT project not found | Verify the path, `sourceSet`; use Designer |
| Unclear `errors` | `navigate_symbol` to the error location; `ask_ai_assistant` |

---
depends_on: []
---
