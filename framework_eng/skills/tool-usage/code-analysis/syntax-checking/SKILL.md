---
name: syntax-checking
description: "Syntax Checking. This skill teaches the agent to **use syntax checking capabilities correctly** for BSL code."
---

# Syntax Checking

Any BSL code change → immediate verification. Without verification, the agent can "successfully" complete a task with non-working code.

**Two tools — different cost:**

| Tool | Speed | When to use |
|------------|----------|-------------------|
| `get_diagnostics` | Fast (seconds) | After every change, intermediate checks |
| `check_syntax` | Slow (tens of seconds — minutes) | Final check: before commit, before PR, after a major refactor |

## When to apply

| Trigger | Action |
|---------|----------|
| After changing BSL code | `get_diagnostics` — fast check |
| Iterative edit cycle (edit → check) | `get_diagnostics` |
| After refactoring / `rename_symbol` | `get_diagnostics` for affected files |
| Compilation error | `get_diagnostics` for localization |
| **Before commit / before PR** | **`check_syntax`** — final check |
| **Task completion** | **`check_syntax`** — final verdict |

## Verification algorithm

### Intermediate check (after each change)

1. `get_diagnostics(uri)` — LSP diagnostics for the changed file.
2. If there is an `error` level — fix it and repeat.
3. `warning` — assess criticality.

### Final check (before commit)

1. `check_syntax(target: "path/Module.bsl", mode: "edt")` — default mode.
2. `success = false` → read `errors`, fix them, repeat.
3. EDT unavailable → fallback: `mode: "designer_modules"` (CheckModules). Requires a connection to the infobase.
4. After refactoring several modules — `target: "all"` or each one individually.

### Preflight: checking Configurator session availability

**REQUIRED** before any call to a tool that works through the Configurator (`check_syntax_designer_modules`, `build_project`, `dump_config`, `launch_app`):

1. Check that there are no hanging sessions:
   ```bash
   ps aux | grep "1cv8.*DESIGNER" | grep -v grep
   ```
2. If a live Designer process is found — **kill it** (`kill <PID>`) before starting a new one.
3. Two simultaneous Configurator sessions for one infobase = deadlock. This is the main cause of hangs.

## Result interpretation

| Field | Action |
|------|----------|
| `success: true` | Continue |
| `success: false` | Fix `errors` (each: `file`, `line`, `message`, `severity`) |
| `warnings` | Assess criticality |
| Timeout | Narrow `target` to individual modules |

Severity: `error` (blocks compilation) > `warning` > `information` / `hint`.

If `get_diagnostics` and `check_syntax` differ, rely on `check_syntax` as the final verdict.

## Capabilities

| Capability | Purpose | Cost |
|------------|------------|-----------|
| `get_diagnostics` | LSP diagnostics for a file | Fast — primary tool |
| `check_syntax` | Formal compiler check | Slow — final check only |

## Typical errors

| Error | Workaround |
|--------|---------------|
| LSP is not running | `check_syntax` as fallback |
| EDT is not running | `get_diagnostics` or `designer_modules` |
| Timeout with `target: "all"` | Check module by module |
| EDT project not found | Check the path, `sourceSet`; use Designer |
| Unclear `errors` | `navigate_symbol` to the error location; `ask_ai_assistant` |

---
depends_on: []
---
