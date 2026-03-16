---
name: syntax-checking
description: Syntax Checking. The skill teaches the agent to **properly use the syntax checking capabilities** of BSL code.
---

# Syntax Checking (Syntax Checking)

## Purpose

The skill teaches the agent to **properly use the syntax checking capabilities** of BSL code. Syntax checking is a required step after every code change. Without it, the agent might “successfully” finish a task while the code remains broken.

**Principle:** Every code change must be verified. Syntax errors are caught before committing, not at runtime.

---

## When to apply

| Trigger | Action |
|---------|--------|
| After each BSL code change | Call `check_syntax` |
| Before commit / saving | Mandatory check of the modified files |
| After refactoring | Check the entire impacted area |
| After bulk renaming (`rename_symbol`) | Check the affected modules |
| User reports a compilation error | Run `check_syntax` first to localize |
| Before running tests | Recommended (tests might not run with syntax errors) |

---

## Usage scenarios

### Scenario 1: Standard check after edits

**Steps:**

1. The agent modifies a module (for example, `CommonModule.УправлениеСкладом`).
2. Call `check_syntax` with the `target` parameter pointing to the modified module or project.
3. Default mode: `edt` (via EDT validate) — the fastest and most accurate.
4. If `success = false` — read `errors`, fix issues, and repeat the check.
5. If there are `warnings` — assess severity and fix if needed.

**Example call:**

```
check_syntax(target: "путь/к/модулю/УправлениеСкладом/Module.bsl", mode: "edt")
```

### Scenario 2: Checking after refactoring multiple modules

**Steps:**

1. Identify the list of affected modules.
2. Call `check_syntax` with `target: "all"` to check the entire configuration (if available).
3. Or run the check sequentially for each module.
4. Collect all errors, resolve them, and recheck.

### Scenario 3: EDT unavailable — fallback to Designer

**Steps:**

1. Call `check_syntax` with `mode: "edt"`.
2. If the capability returns an error (EDT not running, project not open):
   - `mode: "designer_config"` — configuration checking via Конфигуратор (CheckConfig);
   - `mode: "designer_modules"` — module checking via Конфигуратор (CheckModules).
3. Designer modes require connecting to the ИБ through Конфигуратор.

### Scenario 4: Combining with LSP diagnostics

**Steps:**

1. For the currently open file — `get_diagnostics` (uri: path to the file).
2. LSP provides quick feedback without checking the entire configuration.
3. For the final check — `check_syntax` (formal EDT/Designer verification).
4. `get_diagnostics` acts as a partial substitute if `check_syntax` is unavailable.

---

## Interpreting results

| Field | Value | Action |
|------|-------|--------|
| `success` | `true` | The check passed, you can continue. |
| `success` | `false` | Fix the `errors` before proceeding. |
| `errors` | non-empty array | Each error: `file`, `line`, `message`, `severity`. Fix them in order. |
| `warnings` | non-empty array | Assess the warnings: fix critical ones, handle the rest as needed. |
| `check_time` | large value | On a timeout — narrow the `target` (check individual modules). |

**Severity levels (severity):**

- `error` — blocking issue, the code will not compile.
- `warning` — a warning, compilation is still possible but problems may occur.
- `information` / `hint` — recommendations that do not block progress.

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `check_syntax` | Formal syntax checking (EDT / Designer) |
| `get_diagnostics` | LSP diagnostics for the current file (quick feedback) |

---

## Common mistakes and workarounds

| Issue | Workaround |
|--------|-----------|
| Skipping the check after edits | Rule: any BSL change → immediate call to `check_syntax`. |
| `check_syntax` unavailable (EDT not running) | Use `get_diagnostics` as a partial substitute; switch to `designer_config` or `designer_modules` if Конфигуратор is available. |
| Timeout for `target: "all"` | Check modules individually; increase the timeout in the project configuration. |
| EDT project not found | Verify the project path and `sourceSet` in the settings; if needed, use the Designer modes. |
| Errors in `errors` are unclear | Read the `message` and `file`/`line`; use `navigate_symbol` to go to the error location; if needed, `ask_ai_assistant` for clarification. |
| `get_diagnostics` shows errors but `check_syntax` does not | LSP may be stricter or use different rules; rely on `check_syntax` as the final verdict. |

---
depends_on: []
---
