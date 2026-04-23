---
name: test-execution
description: "Running and analyzing tests (Test Execution). The skill teaches the agent to **run YaxUnit tests, analyze results, and link test failures to code**."
---

# Running and analyzing tests (Test Execution)

Writing tests is the `test-writing` skill. YaxUnit API reference — `test-writing/references/yaxunit-cheatsheet.md`.

Test failed → determine whether your changes are responsible or not. If yes — fix, build, rerun. If not — record the reason in `<role>-context.md`.

## Test location

```
exts/TESTS/CommonModules/<ИмяМодуля>/Module.bsl
```

When using `navigate_symbol` — the file is in `exts/TESTS/src/CommonModules/`, not in the main `src/`.

## Required order: Build → Tests

1. Files changed (BSL, XML, tests) → `build_project` → `run_tests`
2. No changes → `run_tests` directly

> `build_project` normally takes **5–15 minutes** depending on the size of the configuration. Do not kill or rerun it prematurely.

## When to use

| Trigger | Action |
|---------|----------|
| After implementation | `build_project` → `run_tests` |
| Module X tests | `build_project` → `run_tests(scope: "X")` |
| After refactoring | `build_project` → all tests |
| Before commit | `build_project` → `run_tests(scope: "all")` → `check_syntax` |

## Failure analysis

1. `errors[].module`, `errors[].test`, `errors[].message` — details.
2. `navigate_symbol` → failing test → code under test → fix → rerun.

### Extracting the cause from logs (when `failedTests > 0`)

Source order (strictly in sequence, next only if the current one did not provide the cause):

1. **`logFile`**: `[ERR]` lines → multiline block up to the timestamp → `Exception|Исключение|Assertion|expected|actual|stack`
2. **Registration log**: last N error entries
3. **`enterpriseLogPath`**: markers `Ошибка|Исключение|Exception|Critical|Fatal|failed|assert` → block of 0..15 lines up to a blank line or timestamp

## Result interpretation

| Field | Action |
|------|----------|
| `success: true` | All tests passed |
| `success: false` | Analyze `errors` |
| `failedTests > 0` | Extract causes from logs (see above) |

Legacy synonyms: `total`/`totalTests`, `passed`/`passedTests`, `failed`/`failedTests`.

## Capabilities

| Capability | Purpose |
|------------|------------|
| `run_tests` | Run YaxUnit tests |
| `build_project` | Build before tests |
| `navigate_symbol` | Jump to the test and the code under test |
| `check_syntax` | Syntax check |

## Monitoring the registration log during tests

When running tests — check the registration log every **20 seconds** for new errors (first of all, test compilation errors). If a compilation error appears — do not wait for `run_tests` to finish, but immediately:

1. Extract the error text from the registration log.
2. Find the problematic module (`navigate_symbol`).
3. Fix → `build_project` → rerun the tests.

## Common errors

| Error | Workaround |
|--------|---------------|
| Build error | `build_project` → `check_syntax` → fix |
| `run_tests` unavailable | Record the reason, inform the user |
| Module not found | Check `scope`, sourceSet |
| Failures due to infobase data | Clarify settings; separate test infobase |
| Long execution | `scope: "ИмяМодуля"` during iterative development |

---
depends_on:
  - framework/skills/bsl-practices/test-writing/SKILL.md
---
