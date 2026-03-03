---
name: syntax-checking
description: Syntax checking. The skill teaches the agent **how to properly use the syntax checking capabilities** of BSL code.
---

# Syntax Checking (Syntax Checking)

## Purpose

The skill teaches the agent **how to properly use the syntax checking capabilities** of BSL code. Syntax checking is a mandatory step after any code change. Without it the agent may “successfully” complete the task while the code ends up nonfunctional.

**Principle:** Every code change must be verified. Syntax errors are caught before committing, not at runtime.

---

## When to apply

| Trigger | Action |
|---------|--------|
| After every BSL code change | Call `check_syntax` |
| Before committing / saving | Mandatory check of the modified files |
| After refactoring | Check the entire affected area |
| After bulk renaming (`rename_symbol`) | Check the affected modules |
| User reports a compilation error | First `check_syntax` to localize the issue |
| Before running tests | Recommended (tests may not run if there are syntax errors) |

---

## Usage scenarios

### Scenario 1: Standard check after edits

**Steps:**

1. The agent applies changes to a module (for example, `CommonModule.УправлениеСкладом`).
2. Call `check_syntax` with the `target` parameter pointing to the modified module or project.
3. Default mode: `edt` (via EDT validate) — the fastest and most precise option.
4. If `success = false` — read the `errors`, fix them, and rerun the check.
5. If there are `warnings` — assess their severity and fix them if necessary.

**Example call:**

```
check_syntax(target: "путь/к/модулю/УправлениеСкладом/Module.bsl", mode: "edt")
```

### Scenario 2: Check after refactoring multiple modules

**Steps:**

1. Identify the list of affected modules.
2. Call `check_syntax` with `target: "all"` to validate the whole configuration (if available).
3. Or perform the check for each module sequentially.
4. Gather all errors, fix them, and recheck.

### Scenario 3: EDT unavailable — fallback to Designer

**Steps:**

1. Call `check_syntax` with `mode: "edt"`.
2. If the capability returns an error (EDT is not running, the project is not opened):
   - `mode: "designer_config"` — configuration check via Конфигуратор (CheckConfig);
   - `mode: "designer_modules"` — module check via Конфигуратор (CheckModules).
3. Designer modes require a connection to the ИБ through the Конфигуратор.

### Scenario 4: Combine with LSP diagnostics

**Steps:**

1. For the currently open file — `get_diagnostics` (uri: path to the file).
2. LSP provides quick feedback without a full configuration check.
3. For the final verification — `check_syntax` (formal EDT/Designer check).
4. `get_diagnostics` acts as a partial substitute if `check_syntax` is unavailable.

---

## Interpreting results

| Field | Value | Action |
|-------|-------|--------|
| `success` | `true` | The check passed; you may continue. |
| `success` | `false` | Fix the `errors` before proceeding. |
| `errors` | non-empty array | Each error includes `file`, `line`, `message`, `severity`. Fix them in order. |
| `warnings` | non-empty array | Evaluate: correct critical warnings, handle the rest based on the situation. |
| `check_time` | large value | On timeout — narrow the `target` (check individual modules). |

**Severity levels:**

- `error` — blocking error; the code will not compile.
- `warning` — warning; compilation is possible but issues may occur.
- `information` / `hint` — recommendations; they do not block compilation.

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `check_syntax` | Formal syntax check (EDT / Designer) |
| `get_diagnostics` | LSP diagnostics for the current file (quick feedback) |

---

## Common mistakes and workarounds

| Mistake | Workaround |
|---------|------------|
| Skipping the check after an edit | Rule: every BSL change → immediate `check_syntax` call. |
| `check_syntax` unavailable (EDT not running) | Use `get_diagnostics` as a partial substitute; switch to `designer_config` or `designer_modules` if Конфигуратор is available. |
| Timeout when `target: "all"` | Check individual modules; increase the timeout in the project configuration. |
| EDT project not found | Verify the project path and `sourceSet` in settings; use Designer modes if needed. |
| Errors in `errors` are unclear | Read the `message` along with `file`/`line`; use `navigate_symbol` to jump to the issue; if necessary, `ask_ai_assistant` for clarification. |
| `get_diagnostics` shows issues but `check_syntax` does not | LSP may be stricter or use different rules; treat `check_syntax` as the final verdict. |

---
depends_on: []
---
