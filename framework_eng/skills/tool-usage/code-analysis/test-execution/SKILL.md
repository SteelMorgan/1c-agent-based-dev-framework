---
name: test-execution
description: Running and analyzing tests (Test Execution). The skill teaches the agent to **run YaxUnit tests, analyze results, and link test failures to code**.
---

# Running and analyzing tests (Test Execution)

## Purpose

The skill teaches the agent to **run YaxUnit tests, analyze results, and link test failures to code**. Tests are the only reliable way to ensure that code still works correctly after changes.

**Writing tests** is a separate skill [`test-writing`](../../bsl-practices/test-writing/SKILL.md). There is also [`references/yaxunit-cheatsheet.md`](../../bsl-practices/test-writing/references/yaxunit-cheatsheet.md) — the complete YaxUnit API reference.

**Principle:** Run the `run_tests` test. If the test reports that `build_project` must be executed, do it and rerun `run_tests`. If a test failed → evaluate whether your code changes (if any) caused it or not. If your changes are guilty (bug, typo, something overlooked) — fix the code, rebuild, and rerun the tests. If your changes are not guilty or you made none — document the reason in the task folder in `<role>-context.md` and conclude the task.

---

## Location of tests in the project

Tests are stored in a **separate configuration extension** at the path:

```
<project root>/exts/TESTS/
```

When analyzing failures and navigating to test code, look for sources right here:

```
exts/TESTS/
  CommonModules/          ← test shared modules (ТестыXxx)
    <ModuleName>/
      Module.bsl          ← test source
```

When using `navigate_symbol` to jump to a test module, expect the file to live in `exts/TESTS/src/CommonModules/`, not the main `src/`.

---

## When to apply

| Trigger | Action |
|---------|--------|
| After implementing functionality | Run `build_project` first (if changes were made), then `run_tests` to verify |
| User asks to run module X tests | Run `build_project` first (if changes were made), then `run_tests` with `scope: "X"` |
| After refactoring | Run `build_project`, then execute all tests or the affected modules |
| Bug fix | Write/find a test, fix the issue, then `build_project` (if changes exist) and rerun |
| Before committing | Recommended: `build_project` + full test run |

---

## Mandatory 2-step execution order

1. **Step 1 — Build:** if any source files were modified this iteration (BSL, XML metadata, test modules), run `build_project` first.
2. **Step 2 — Tests:** after a successful build, execute `run_tests` (targeted scope or full suite).

If no source changes occurred — a plain `run_tests` without building is acceptable.

## Usage scenarios

### Scenario 1: Standard run after changes

**Steps:**

1. The agent changed a module (e.g., `УправлениеСкладом`).
2. Run `build_project` to ensure the project builds cleanly.
3. Run `run_tests` with `scope: "УправлениеСкладом"` — run the tests for the changed module.
4. If `success = true` — the task is complete.
5. If `success = false` — analyze `errors`, fix, and rerun.

### Scenario 2: Analyzing a test failure

**Steps:**

1. `run_tests` returns `failed > 0` and `errors` holds the details.
2. Read `errors[].module`, `errors[].test`, `errors[].message`.
3. Use `navigate_symbol` to go to the failing test by name (e.g., `ТестПолучитьОстатки`).
4. Analyze the Assert and the code under test.
5. Use `navigate_symbol` to jump to the tested procedure/function.
6. Fix the code or the test.
7. Run `run_tests` again to verify.

**Example of an error structure:**

```
errors: [{ module: "УправлениеСкладом", test: "ТестПолучитьОстатки", message: "Ожидалось 10, получено 0" }]
```

### Scenario 2.1: failedTests > 0 — extracting reason from logs

**Diagnostics trigger:** if `failedTests > 0`, the agent must extract the failure reason from logs (even if the `errors` field is incomplete).

**Order of sources (strictly sequential):**

1. `logFile`
2. Registration journal (last N error entries)
3. `enterpriseLogPath`

Move to the next source only if the current one yields no clear reason.

**Algorithm for `logFile`:**

1. Find lines with `[ERR]`.
2. For each such line, capture the multi-line block up to the next timestamp.
3. From the block keep:
   - **short reason:** the first `[ERR]` line;
   - **detailed reason:** lines containing `Exception|Assertion|expected|actual|stack`.
4. Filter out noise: all `[INF]`, `[DBG]`, and irrelevant lines.

If the reason remains unclear — read the last N error entries in the registration journal.

**Algorithm for `enterpriseLogPath`:**

Search for failure markers (case-insensitive):

- `Error`, `Exception`, `Critical`, `Fatal`
- `Test .* not passed`, `failed`, `assert`, `Assertion`
- `due to`, `Reason`, `Stack`

Block extraction pattern:

1. Find the line with the marker.
2. Capture the next `0..15` lines until encountering:
   - an empty line, or
   - a new technical entry (timestamp/level prefix).

### Scenario 3: TDD cycle

**Steps:**

1. Write a test for the new feature (expect a failure).
2. Run `run_tests` — confirm it fails (`failed > 0`).
3. Implement the minimal logic to pass the test.
4. Run `run_tests` — expect `success = true`.
5. Refactor if needed, then rerun.

### Scenario 4: Full run before commit

**Steps:**

1. Run `build_project` — clean build.
2. Run `run_tests` with `scope: "all"` — every test in the project.
3. Fix any failures before committing.
4. Optionally run `check_syntax` as a final sanity check.

---

## Results interpretation

| Field | Meaning | Action |
|------|---------|--------|
| `success` | `true` | All tests passed. |
| `success` | `false` | There are failures — analyze `errors`. |
| `totalTests` | N | Total tests (legacy synonym `total` might appear). |
| `passedTests` | N | Passed tests (legacy synonym `passed` might appear). |
| `failedTests` | N | Failed tests (legacy synonym `failed` might appear). If `failedTests > 0` — extract reasons from logs per Scenario 2.1. |
| `errors` | `[{module, test, message}]` | Details of each failure. |
| `duration` | ms | Execution time. |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `run_tests` | Execute YaxUnit tests |
| `build_project` | Build before tests (clean build) |
| `navigate_symbol` | Jump to the failing test and the code under test |
| `check_syntax` | Syntax check before/after changes |

---

## Common pitfalls and workarounds

| Problem | Workaround |
|--------|-------------|
| Build failure before tests | Run `build_project` first; if the build fails — run `check_syntax` and fix compilation errors. |
| `run_tests` unavailable | The capability depends on the test runner; document the skipped reason and notify the user. |
| Module not found | Verify the module name (`scope`); ensure the tests reside in the correct sourceSet. |
| Tests fail because of database data | Tests may depend on test data; clarify database settings with the user; use a separate test database if needed. |
| Full test run takes too long | Run tests for a specific module (`scope: "ModuleName"`) during iterative development. |
| Failure is unreproducible | Check test order and isolation; run `run_tests` with a narrow scope to reproduce. |

---
depends_on:
  - framework/skills/bsl-practices/test-writing/SKILL.md
---
