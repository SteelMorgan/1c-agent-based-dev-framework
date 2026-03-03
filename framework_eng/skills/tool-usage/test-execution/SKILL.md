---
name: test-execution
description: Running and analyzing tests (Test Execution). The skill teaches the agent **to run YaxUnit tests, analyze results, and connect failing tests to the code**.
---

# Running and analyzing tests (Test Execution)

## Purpose

The skill teaches the agent **to run YaxUnit tests, analyze results, and connect failing tests to the code**. Tests are the only reliable way to ensure that the code works correctly after changes.

**Writing tests** is a separate skill [`test-writing`](../../bsl-practices/test-writing/SKILL.md). There you can also find [`references/yaxunit-cheatsheet.md`](../../bsl-practices/test-writing/references/yaxunit-cheatsheet.md) — a complete YaxUnit API reference.

**Principle:** Write code → run tests. If a test fails → find the reason, fix it, rerun.

---

## Location of tests in the project

Tests are stored in a **separate configuration extension** at the path:

```
<project-root>/exts/TESTS/
```

When analyzing failures and navigating to test code — look for sources precisely here:

```
exts/TESTS/
  src/
    CommonModules/          ← shared test modules (TestsXxx)
      <ModuleName>/
        Module.bsl          ← test source
```

When using `navigate_symbol` to jump to a test module — expect the file to be located in `exts/TESTS/src/CommonModules/`, not the main `src/`.

---

## When to apply

| Trigger | Action |
|---------|--------|
| After implementing functionality | `run_tests` to verify |
| The user asks to run tests for module X | `run_tests` with `scope: "X"` |
| TDD approach: test written, now implementing logic | Run tests after each stage |
| After refactoring | Run all tests or the affected modules |
| Fixing a bug | Write/find a test, fix, rerun |
| Before committing | A full test run is recommended |

---

## Usage scenarios

### Scenario 1: Standard run after changes

**Steps:**

1. The agent made changes in a module (for example, `УправлениеСкладом`).
2. `build_project` — ensure the project builds without errors.
3. `run_tests` with `scope: "УправлениеСкладом"` — run tests for the modified module.
4. If `success = true` — the task is done.
5. If `success = false` — analyze `errors`, fix, rerun.

### Scenario 2: Analyzing a test failure

**Steps:**

1. `run_tests` returns `failed > 0`, `errors` contains details.
2. Read `errors[].module`, `errors[].test`, `errors[].message`.
3. `navigate_symbol` — go to the failing test by name (for example, `ТестПолучитьОстатки`).
4. Analyze the Assert and the code under test.
5. `navigate_symbol` — go to the tested procedure/function.
6. Fix the code or the test.
7. `run_tests` — rerun to verify.

**Example of an error structure:**

```
errors: [{ module: "УправлениеСкладом", test: "ТестПолучитьОстатки", message: "Expected 10, got 0" }]
```

### Scenario 3: TDD loop

**Steps:**

1. Write a test for the new function (expect it to fail).
2. `run_tests` — confirm the failure (`failed > 0`).
3. Implement the minimal logic to pass the test.
4. `run_tests` — expect `success = true`.
5. If needed — refactor, then rerun.

### Scenario 4: Full run before committing

**Steps:**

1. `build_project` — clean build.
2. `run_tests` with `scope: "all"` — all project tests.
3. Fix any failures before committing.
4. `check_syntax` — final check (optional but recommended).

---

## Interpreting results

| Field | Meaning | Action |
|-------|---------|--------|
| `success` | `true` | All tests passed. |
| `success` | `false` | There are failures — analyze `errors`. |
| `total` | N | Total tests. |
| `passed` | N | Passed. |
| `failed` | N | Failed. |
| `errors` | `[{module, test, message}]` | Details of each failure. |
| `duration` | ms | Execution time. |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `run_tests` | Run YaxUnit tests |
| `build_project` | Build before tests (clean build) |
| `navigate_symbol` | Jump to the failing test and the tested code |
| `check_syntax` | Syntax check before/after changes |

---

## Typical mistakes and workarounds

| Mistake | Workaround |
|---------|------------|
| Build error before tests | Run `build_project` first; if there are build errors — run `check_syntax`, fix compilation issues. |
| `run_tests` unavailable | Capability depends on the test runner; document the reason for skipping and inform the user. |
| Module not found | Check the module name (`scope`); ensure tests reside in the correct sourceSet. |
| Tests "fail" due to ИБ data | Tests might depend on test data; clarify the ИБ setup with the user; if needed, use a separate test ИБ. |
| Long execution of all tests | Run tests for a specific module (`scope: "ModuleName"`) during iterative development. |
| Failure not reproducible | Check test order and isolation; use `run_tests` with a narrow scope to reproduce. |

---
depends_on: [test-writing]
---
