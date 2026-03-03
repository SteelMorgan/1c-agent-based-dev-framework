---
name: test-execution
description: The skill teaches the agent to run YaxUnit tests, analyze the results, and link test failures to the code.
---

# Test Execution

## Purpose

The skill teaches the agent to run YaxUnit tests, analyze the results, and link test failures to the code. Tests are the only reliable way to make sure the code works correctly after changes.

**Writing tests** is a separate skill [`test-writing`](../../bsl-practices/test-writing/SKILL.md). There is also the full YaxUnit API reference in [`references/yaxunit-cheatsheet.md`](../../bsl-practices/test-writing/references/yaxunit-cheatsheet.md).

**Principle:** Run the `run_tests` test. If the test reports that `build_project` needs to be performed, do it and then repeat `run_tests`. If a test fails → evaluate whether your code changes are to blame (if you made them) or not. If your changes are to blame (a bug, typo, or oversight on your part) → fix the code, rebuild, and rerun the tests. If your changes are not to blame or you did not make them → record the reason in the task directory in the `<role>-context.md` file and close the task.

---

## Location of tests in the project

Tests are stored in a **separate configuration extension** at:

```
<project root>/exts/TESTS/
```

When analyzing failures and navigating to test code, look for sources exactly here:

```
exts/TESTS/
  CommonModules/          ← shared test modules (ТестыXxx)
    <ModuleName>/
      Module.bsl          ← test source
```

When using `navigate_symbol` to jump to a test module, expect the file to be in `exts/TESTS/src/CommonModules/` rather than the main `src/`.

---

## When to apply

| Trigger | Action |
|---------|--------|
| After implementing functionality | First `build_project` (if changes were made), then `run_tests` to verify |
| User requests running module X tests | First `build_project` (if changes were made), then `run_tests` with `scope: "X"` |
| After refactoring | First `build_project`, then run all tests or the affected modules |
| Bug fix | Write/find a test, fix the issue, then `build_project` (if changes were made) and rerun |
| Before committing | Prefer `build_project` + full test run |

---

## Mandatory 2-step execution order

1. **Step 1 — Build:** if files in the codebase (BSL, XML metadata, test modules) were modified in this iteration, run `build_project` first.
2. **Step 2 — Tests:** after a successful build, run `run_tests` (either targeted scope or full suite).

If the codebase was not changed, a direct `run_tests` without build is acceptable.

## Usage scenarios

### Scenario 1: Standard run after changes

**Steps:**

1. The agent made changes to a module (for example, `УправлениеСкладом`).
2. `build_project` — make sure the project compiles without errors.
3. `run_tests` with `scope: "УправлениеСкладом"` — run the tests for the modified module.
4. If `success = true` — the task is complete.
5. If `success = false` — analyze `errors`, fix them, and rerun.

### Scenario 2: Test failure investigation

**Steps:**

1. `run_tests` reports `failed > 0` and `errors` contains details.
2. Read `errors[].module`, `errors[].test`, `errors[].message`.
3. `navigate_symbol` — jump to the failing test by name (for example, `ТестПолучитьОстатки`).
4. Analyze the Assert and the code under test.
5. `navigate_symbol` — jump to the procedure/function under test.
6. Fix the code or the test.
7. `run_tests` — rerun to verify.

**Example error structure:**

```
errors: [{ module: "УправлениеСкладом", test: "ТестПолучитьОстатки", message: "Ожидалось 10, получено 0" }]
```

### Scenario 3: TDD cycle

**Steps:**

1. Write a test for the new feature (expect it to fail).
2. `run_tests` — confirm the failure (`failed > 0`).
3. Implement the minimal logic to pass the test.
4. `run_tests` — expect `success = true`.
5. Refactor if needed, then rerun.

### Scenario 4: Full run before committing

**Steps:**

1. `build_project` — clean build.
2. `run_tests` with `scope: "all"` — every test in the project.
3. If any tests fail — fix them before committing.
4. `check_syntax` — final verification (optional but recommended).

---

## Interpretation of results

| Field | Value | Action |
|-------|-------|--------|
| `success` | `true` | All tests passed. |
| `success` | `false` | There are failures — analyze `errors`. |
| `total` | N | Total number of tests. |
| `passed` | N | Number of successes. |
| `failed` | N | Number of failures. |
| `errors` | `[{module, test, message}]` | Details for each failure. |
| `duration` | ms | Execution time. |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `run_tests` | Run YaxUnit tests |
| `build_project` | Build before testing (clean build) |
| `navigate_symbol` | Jump to failing tests and tested code |
| `check_syntax` | Syntax check before/after changes |

---

## Common mistakes and workarounds

| Mistake | Workaround |
|---------|------------|
| Build error before tests | Run `build_project` first; if the build fails — run `check_syntax` and fix compilation issues. |
| `run_tests` is unavailable | Capability depends on the test runner; note the reason for skipping and inform the user. |
| Module not found | Check the module name (`scope`); ensure tests are in the correct sourceSet. |
| Tests fail due to IB data | Tests may rely on test data; clarify the IB settings with the user; if needed, use a dedicated test IB. |
| Running all tests takes long | Run tests for a specific module (`scope: "ModuleName"`) during iterative development. |
| Failure is not reproducible | Check test order and isolation; use `run_tests` with a narrow scope to reproduce. |

---
depends_on: [test-writing]
---
