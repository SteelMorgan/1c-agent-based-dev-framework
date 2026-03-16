---
name: test-execution
description: Test execution and analysis (Test Execution). The skill teaches the agent to **run YaxUnit tests, analyze the results, and correlate test failures with the code**.
---

# Test execution and analysis (Test Execution)

## Purpose

The skill teaches the agent to **run YaxUnit tests, analyze the results, and correlate test failures with the code**. Tests are the only reliable way to ensure the code still works correctly after changes.

**Writing tests** is a separate skill [`test-writing`](../../bsl-practices/test-writing/SKILL.md). There you can also find [`references/yaxunit-cheatsheet.md`](../../bsl-practices/test-writing/references/yaxunit-cheatsheet.md) — a complete reference for the YaxUnit API.

**Principle:** Run the `run_tests` test first. If the test reports that you need to perform `build_project`, do it and then rerun `run_tests`. If a test fails → evaluate whether your code changes (if any) are to blame. If your changes are to blame (a bug, typo, or missed case) — fix the code, rebuild, and rerun the tests. If your changes are not to blame or you did not make any — document the reason in `<role>-context.md` inside the task directory and finish the task.

---

## Test location in the project

Tests live in a **separate configuration extension** under:

```
<project root>/exts/TESTS/
```

When analyzing failures and navigating to test code — look for sources here:

```
exts/TESTS/
  CommonModules/          ← shared test modules (ТестыXxx)
    <ModuleName>/
      Module.bsl          ← test source
```

When you use `navigate_symbol` to jump to a test module — expect the file to be located in `exts/TESTS/src/CommonModules/`, not under the main `src/`.

---

## When to apply

| Trigger | Action |
|---------|--------|
| After implementing functionality | Run `build_project` first (if there were changes), then `run_tests` to verify |
| User asks to run module X tests | Run `build_project` first (if there were changes), then `run_tests` with `scope: "X"` |
| After refactoring | Run `build_project`, then all tests or the affected modules |
| Bug fix | Write/find the test, fix it, then `build_project` (if there were changes) and rerun |
| Before committing | Recommended: `build_project` + full test run |

---

## Mandatory two-step execution order

1. **Step 1 — Build:** if files in the codebase (BSL, XML metadata, test modules) changed in this iteration, run `build_project` first.
2. **Step 2 — Tests:** after a successful build, run `run_tests` (targeted scope or full suite).

If no codebase changes occurred — a direct `run_tests` without build is acceptable.

## Usage scenarios

### Scenario 1: Standard run after changes

**Steps:**

1. The agent made changes in a module (for example, `УправлениеСкладом`).
2. `build_project` — ensure the project builds without errors.
3. `run_tests` with `scope: "УправлениеСкладом"` — run tests for the modified module.
4. If `success = true` — the task is complete.
5. If `success = false` — analyze `errors`, fix issues, rerun.

### Scenario 2: Analyzing a test failure

**Steps:**

1. `run_tests` returns `failed > 0`, `errors` contains details.
2. Read `errors[].module`, `errors[].test`, `errors[].message`.
3. Use `navigate_symbol` — open the failing test by name (for example, `ТестПолучитьОстатки`).
4. Analyze the Assert and the code under test.
5. Use `navigate_symbol` — go to the tested procedure/function.
6. Fix the code or test.
7. Run `run_tests` again to verify.

**Sample error structure:**

```
errors: [{ module: "УправлениеСкладом", test: "ТестПолучитьОстатки", message: "Ожидалось 10, получено 0" }]
```

### Scenario 2.1: failedTests > 0 — extracting the cause from logs

**Diagnostic trigger:** if `failedTests > 0`, the agent must extract the failure cause from logs (even if the `errors` field is incomplete).

**Source order (strict sequential):**

1. `logFile`
2. Registration journal (latest N error entries)
3. `enterpriseLogPath`

Only proceed to the next source if the current one does not provide a clear reason.

**Algorithm for `logFile`:**

1. Locate lines containing `[ERR]`.
2. For each such line, take the multi-line block up to the next timestamp.
3. From the block, keep:
   - **short reason:** the first `[ERR]` line;
   - **detailed reason:** lines that contain `Exception|Исключение|Assertion|expected|actual|stack`.
4. Filter out noise: all `[INF]`, `[DBG]`, and irrelevant lines.

If the cause is still unclear — read the latest N error entries from the registration journal.

**Algorithm for `enterpriseLogPath`:**

Look for failure markers (case-insensitive):

- `Ошибка`, `Исключение`, `Exception`, `Critical`, `Fatal`
- `Тест .* не пройден`, `failed`, `assert`, `Assertion`
- `по причине`, `Причина`, `Stack`, `Стек`

Block extraction pattern:

1. Find the line with a marker.
2. Capture the next `0..15` lines until you encounter:
   - an empty line, or
   - a new system entry (timestamp/level prefix).

### Scenario 3: TDD cycle

**Steps:**

1. Write a test for a new function (expect it to fail).
2. `run_tests` — confirm the failure (`failed > 0`).
3. Implement minimal logic to satisfy the test.
4. `run_tests` — expect `success = true`.
5. If needed — refactor, then rerun.

### Scenario 4: Full run before commit

**Steps:**

1. `build_project` — clean build.
2. `run_tests` with `scope: "all"` — run all project tests.
3. Fix any failures before committing.
4. `check_syntax` — final check (optional but recommended).

---

## Result interpretation

| Field | Meaning | Action |
|-------|---------|--------|
| `success` | `true` | All tests passed. |
| `success` | `false` | There are failures — analyze `errors`. |
| `totalTests` | N | Total tests (legacy alias `total` may appear). |
| `passedTests` | N | Successful tests (legacy alias `passed`). |
| `failedTests` | N | Failed tests (legacy alias `failed`). If `failedTests > 0` — extract reasons from logs following Scenario 2.1. |
| `errors` | `[{module, test, message}]` | Details for each failure. |
| `duration` | ms | Execution time. |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `run_tests` | Run YaxUnit tests |
| `build_project` | Build before testing (clean build) |
| `navigate_symbol` | Jump to the failing test and tested code |
| `check_syntax` | Syntax check before/after changes |

---

## Common errors and workarounds

| Error | Workaround |
|-------|------------|
| Build failure before testing | Run `build_project` first; if build errors appear — run `check_syntax` and fix compilation issues. |
| `run_tests` unavailable | Capability depends on the test runner; document the skipped reason and inform the user. |
| Module not found | Verify the module name (`scope`); ensure the tests live in the correct sourceSet. |
| Tests fail due to IB data | Tests may rely on test data; clarify the IB setup with the user; use a separate test IB if necessary. |
| Long full test run | Execute tests for a specific module (`scope: "ModuleName"`) during iterative work. |
| Failure is not reproducible | Check test order and isolation; use `run_tests` with a narrow scope to reproduce. |

---
depends_on: [test-writing]
---
