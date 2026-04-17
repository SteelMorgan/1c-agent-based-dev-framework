---
name: syntax-checking
description: Syntax checking (Syntax Checking). The skill teaches the agent **how to properly use syntax checking capabilities** of BSL code.
---

# Syntax Checking (Syntax Checking)

Any change to BSL code → immediate verification. Without checking, the agent may "successfully" finish the task with non-working code.

**Two tools — different cost:**

| Tool | Speed | When to use |
|------------|----------|-------------------|
| `get_diagnostics` | Fast (seconds) | After each change, interim checks |
| `check_syntax` | Slow (tens of seconds — minutes) | Final verification: before commit, before PR, after large refactor |

## When to apply

| Trigger | Action |
|---------|----------|
| After changing BSL code | `get_diagnostics` — quick check |
| Iterative edit loop (edit → check) | `get_diagnostics` |
| After refactoring / `rename_symbol` | `get_diagnostics` for touched files |
| Compilation error | `get_diagnostics` to localize |
| **Before commit / before PR** | **`check_syntax`** — final verification |
| **Task completion** | **`check_syntax`** — final verdict |

## Verification algorithm

### Interim check (after each change)

1. `get_diagnostics(uri)` — LSP diagnostics of the changed file.
2. If there is an `error` severity — fix and repeat.
3. `warning` — assess criticality.

### Final check (before commit)

1. `check_syntax(target: "путь/Module.bsl", mode: "edt")` — default mode.
2. `success = false` → read `errors`, fix, repeat.
3. EDT unavailable → fallback: `mode: "designer_modules"` (CheckModules). Requires connection to ИБ.
4. After refactoring several modules — `target: "all"` or per module.

### Preflight: checking availability of a Конфигуратор session

**MANDATORY** before any tool call that works through Конфигуратор (`check_syntax_designer_modules`, `build_project`, `dump_config`, `launch_app`):

1. Ensure no dangling sessions:
   ```bash
   ps aux | grep "1cv8.*DESIGNER" | grep -v grep
   ```
2. If a live Designer process is found — **kill it** (`kill <PID>`) before launching a new one.
3. Two concurrent Конфигуратор sessions for the same ИБ = deadlock. This is the main cause of hangs.

## Interpreting results

| Field | Action |
|------|----------|
| `success: true` | Continue |
| `success: false` | Fix `errors` (each: `file`, `line`, `message`, `severity`) |
| `warnings` | Evaluate severity |
| Timeout | Narrow `target` to individual modules |

Severity: `error` (blocks compilation) > `warning` > `information` / `hint`.

When `get_diagnostics` and `check_syntax` disagree — treat `check_syntax` as the final verdict.

## Capabilities

| Capability | Purpose | Cost |
|------------|------------|-----------|
| `get_diagnostics` | LSP diagnostics of a file | Fast — primary tool |
| `check_syntax` | Formal compiler verification | Slow — only for final verification |

## Common mistakes

| Issue | Workaround |
|--------|---------------|
| LSP not running | `check_syntax` as fallback |
| EDT not launched | `get_diagnostics` or `designer_modules` |
| Timeout `target: "all"` | Check module by module |
| EDT project not found | Verify path, `sourceSet`; use Designer |
| Unclear `errors` | `navigate_symbol` to the error location; `ask_ai_assistant` |

---
depends_on: []
---
