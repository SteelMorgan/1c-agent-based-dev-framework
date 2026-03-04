---
name: test-execution
description: Test execution and analysis (Test Execution). The skill teaches the agent to **run YaxUnit tests, analyze the results, and connect test failures to the code**.
---

# Test execution and analysis (Test Execution)

## Purpose

The skill teaches the agent to **run YaxUnit tests, analyze the results, and connect test failures to the code**. Tests are the only reliable way to make sure the code works correctly after changes.

**Writing tests** is a separate skill [`test-writing`](../../bsl-practices/test-writing/SKILL.md). There is also [`references/yaxunit-cheatsheet.md`](../../bsl-practices/test-writing/references/yaxunit-cheatsheet.md) — a complete reference for the YaxUnit API.

**Principle:** Run the `run_tests` test. If the test reports that `build_project` must be executed — do so, then rerun `run_tests`. If a test failed → determine whether your code changes (if any) are to blame. If your changes caused the failure (a bug, typo, overlooked detail) — fix the code, rebuild, and rerun the tests. If your changes are not to blame or you didn’t make any — record the reason in the task directory in the `<role>-context.md` file and close the task.

---

## Test location in the project

Tests are stored in a **separate configuration extension** at the path:

```
<project root>/exts/TESTS/
```

When analyzing failures and navigating to test code — look for the sources here:

```
exts/TESTS/
  CommonModules/          ← shared test modules (ТестыXxx)
    <ModuleName>/
      Module.bsl          ← test source
```

When using `navigate_symbol` to jump to a test module — expect the file to be under `exts/TESTS/src/CommonModules/`, not the main `src/`.

---

## When to apply

| Trigger | Action |
|---------|--------|
| After implementing functionality | First `build_project` (if there were changes), then `run_tests` to verify |
| The user asks to run module X tests | First `build_project` (if there were changes), then `run_tests` with `scope: "X"` |
| After refactoring | First `build_project`, then run all tests or the affected modules |
| Bug fix | Write/find a test, fix the issue, then `build_project` (if there were changes) and rerun |
| Before committing | Recommend `build_project` + full test run |

---

## Mandatory two-step execution order

1. **Step 1 — Build:** if files in the codebase (BSL, XML metadata, test modules) were modified in the current iteration, run `build_project` first.
2. **Step 2 — Tests:** after a successful build, run `run_tests` (targeted scope or full suite).

If there were no codebase changes — a direct `run_tests` without a build is acceptable.

## Usage scenarios

### Scenario 1: Standard run after changes

**Steps:**

1. The agent changed a module (for example, `УправлениеСкладом`).
2. `build_project` — ensure the project builds without errors.
3. `run_tests` with `scope: "УправлениеСкладом"` — run tests for the modified module.
4. If `success = true` — the task is completed.
5. If `success = false` — analyze `errors`, fix issues, rerun.

### Scenario 2: Analyzing a test failure

**Steps:**

1. `run_tests` returns `failed > 0`, and `errors` contains details.
2. Read `errors[].module`, `errors[].test`, `errors[].message`.
3. `navigate_symbol` — go to the failing test by name (for example, `ТестПолучитьОстатки`).
4. Analyze the Assert and the code under test.
5. `navigate_symbol` — go to the tested procedure/function.
6. Fix the code or the test.
7. `run_tests` — rerun to verify.

**Example error structure:**

```
errors: [{ module: "УправлениеСкладом", test: "ТестПолучитьОстатки", message: "Expected 10, got 0" }]
```

### Scenario 2.1: failedTests > 0 — extracting the cause from logs

**Condition for starting diagnostics:** if `failedTests > 0`, the agent must extract the failure reason from logs (even if the `errors` field is incomplete).

**Order of sources (strict sequentially):**

1. `logFile`
2. The registration log (the last N error entries)
3. `enterpriseLogPath`

Move to the next source only if the current one did not provide a clear reason.

**Algorithm for `logFile`:**

1. Find lines with `[ERR]`.
2. For each such line, take the multi-line block up to the next timestamp.
3. From the block keep:
   - **short reason**: the first `[ERR]` line;
   - **detailed reason**: lines containing `Exception|Исключение|Assertion|expected|actual|stack`.
4. Filter out noise: all `[INF]`, `[DBG]`, and irrelevant lines.

If the cause is still unclear — read the last N error entries from the registration log.

**Algorithm for `enterpriseLogPath`:**

Search for failure markers (case-insensitive):

- `Ошибка`, `Исключение`, `Exception`, `Critical`, `Fatal`
- `Тест .* не пройден`, `failed`, `assert`, `Assertion`
- `по причине`, `Причина`, `Stack`, `Стек`

Block extraction pattern:

1. Find the line with a marker.
2. Capture the next `0..15` lines until encountering:
   - an empty line, or
   - a new system entry (timestamp/level prefix).

### Scenario 3: TDD cycle

**Steps:**

1. Write a test for a new feature (expect it to fail).
2. `run_tests` — confirm the failure (`failed > 0`).
3. Implement the minimal logic to pass the test.
4. `run_tests` — expect `success = true`.
5. Refactor if necessary, then rerun.

### Scenario 4: Full run before commit

**Steps:**

1. `build_project` — clean build.
2. `run_tests` with `scope: "all"` — all project tests.
3. Fix any failures before committing.
4. `check_syntax` — final check (optional but recommended).

---

## Interpreting results

| Field | Value | Action |
|-------|-------|--------|
| `success` | `true` | All tests passed. |
| `success` | `false` | There are failures — analyze `errors`. |
| `totalTests` | N | Total tests (a legacy synonym `total` may appear). |
| `passedTests` | N | Passed tests (legacy synonym `passed` may appear). |
| `failedTests` | N | Failed tests (legacy synonym `failed` may appear). If `failedTests > 0` — extract reasons from logs per scenario 2.1. |
| `errors` | `[{module, test, message}]` | Details of each failure. |
| `duration` | ms | Execution time. |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `run_tests` | Run YaxUnit tests |
| `build_project` | Build before testing (clean build) |
| `navigate_symbol` | Jump to failing test and tested code |
| `check_syntax` | Syntax check before/after changes |

---

## Common mistakes and workarounds

| Mistake | Workaround |
|---------|------------|
| Build failure before tests | First `build_project`; if the build fails — run `check_syntax`, fix compilation errors. |
| `run_tests` unavailable | Capability depends on the test runner; record the skip reason and inform the user. |
| Module not found | Verify the module name (`scope`); ensure tests are located in the correct sourceSet. |
| Tests "fail" because of infobase data | Tests may depend on test data; clarify the infobase settings with the user; if needed, use a separate test infobase. |
| Long full test run | Run the tests for a specific module (`scope: "ModuleName"`) during iterative development. |
| Failure does not reproduce | Check the test order and isolation; use `run_tests` with a narrow scope to reproduce. |

---
depends_on: [test-writing]
---
