---
name: test-execution
description: Running and analyzing tests (Test Execution). The skill teaches the agent to **run YaxUnit tests, analyze the results, and link test failures to the code**.
---

# Running and analyzing tests (Test Execution)

Writing tests is the `test-writing` skill. The YaxUnit API reference is `test-writing/references/yaxunit-cheatsheet.md`.

Test failed → assess whether your changes are to blame. If yes — fix, build, rerun. If no — record the reason in `<role>-context.md`.

## Test locations

```
exts/TESTS/CommonModules/<ИмяМодуля>/Module.bsl
```

When using `navigate_symbol` — the file is under `exts/TESTS/src/CommonModules/`, not in the main `src/`.

## Mandatory order: Build → Tests

1. Files changed (BSL, XML, tests) → `build_project` → `run_tests`
2. No changes → `run_tests` directly

## When to apply

| Trigger | Action |
|---------|--------|
| After implementation | `build_project` → `run_tests` |
| Module X tests | `build_project` → `run_tests(scope: "ModuleName")` |
| After refactoring | `build_project` → all tests |
| Before commit | `build_project` → `run_tests(scope: "all")` → `check_syntax` |

## Failure analysis

1. `errors[].module`, `errors[].test`, `errors[].message` — details.
2. `navigate_symbol` → failing test → tested code → fix → rerun.

### Extracting the cause from logs (when `failedTests > 0`)

Source order (strictly sequential; move to the next only if the current one does not yield a cause):

1. **`logFile`**: lines with `[ERR]` → the multiline block up to a timestamp → `Exception|Исключение|Assertion|expected|actual|stack`
2. Registration log: the last N error entries
3. **`enterpriseLogPath`**: markers `Ошибка|Исключение|Exception|Critical|Fatal|failed|assert` → block of 0..15 lines before an empty line or timestamp

## Interpreting results

| Field | Action |
|-------|--------|
| `success: true` | All tests passed |
| `success: false` | Analyze `errors` |
| `failedTests > 0` | Extract causes from logs (see above) |

Legacy synonyms: `total`/`totalTests`, `passed`/`passedTests`, `failed`/`failedTests`.

## Capabilities

| Capability | Purpose |
|------------|---------|
| `run_tests` | Run YaxUnit tests |
| `build_project` | Build before tests |
| `navigate_symbol` | Jump to the test and the tested code |
| `check_syntax` | Check syntax |

## Common mistakes

| Mistake | Workaround |
|---------|------------|
| Build failure | `build_project` → `check_syntax` → fix |
| `run_tests` unavailable | Record the reason, inform the user |
| Module not found | Check `scope`, sourceSet |
| Failures caused by database data | Clarify settings; use a separate test database |
| Long execution | `scope: "ModuleName"` during iterative development |

---
depends_on:
  - framework/skills/bsl-practices/test-writing/SKILL.md
---
