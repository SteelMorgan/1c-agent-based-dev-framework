---
name: tdd-policy
description: TDD Policy — tests are written before implementation and recorded in the specification.
---



# TDD Policy (Test-Driven Development)

> RULES — mandatory policies that constrain agent behavior. Tests are written before implementation, not after.

---

## Purpose

This rule ensures code quality and predictability through TDD discipline.

**Key principle:** tests and implementation are written by **different agents** in **different phases**. The phase order (tests first, code second) is enforced by the **orchestrator**, not by the agents themselves. This eliminates conflicts of interest: the test author does not know the implementation, and the code author does not modify the tests.

```
Оркестратор управляет порядком:
  Phase 3a: Developer-Tests → пишет тесты по спеке (Red)
  Phase 3b: Developer-Code  → пишет код чтобы тесты прошли (Green)
  Phase 4:  Tester          → дополняет edge cases и регрессию
```

---

## Trigger conditions

The rule applies when an agent:

- Implements new functionality with business logic
- Makes changes to already tested modules
- Fixes bugs in code that already has tests
- Works in a full-cycle workflow with an approved specification

---

## Requirements

### MUST (mandatory)

| Requirement | Description |
|------------|-------------|
| Test plan in the specification | The test plan MUST be described in the specification BEFORE the code is written |
| Tests before implementation | YaxUnit tests for MUST scenarios MUST be written BEFORE the business logic is implemented |
| Red → Green → Refactor cycle | Follow the order: first RED (test fails), then GREEN (minimal code), then Refactor |
| Test review | Tests MUST be reviewed by another agent (coverage against the specification) |
| Re-run after fixes | After review comments are fixed, run ALL impacted tests |

### SHOULD (strongly recommended)

| Requirement | Description |
|------------|-------------|
| Tests for SHOULD scenarios | Tests for SHOULD-priority scenarios SHOULD be written after the basic implementation |

### MAY (optional)

| Situation | Description |
|----------|-------------|
| UI-only changes | Tests MAY be skipped |
| Configuration changes | Tests MAY be skipped |
| Documentation | Tests MAY be skipped |

---

## TDD Workflow

```
1. Прочитать спецификацию → выявить тест-сценарии из Test Plan
2. Написать тест-модуль → выполнить run_tests (тест RED — падает)
3. Реализовать минимальный код для прохождения теста
4. Запустить run_tests → убедиться в GREEN
5. Рефакторинг (если нужен)
6. Запустить run_tests снова → остаётся GREEN
7. Выполнить check_syntax
```

---

## Test requirements

- Tests MUST invoke `run_tests` after changes
- The test MUST fail before implementation (Red) — otherwise the scenario is already covered or the test is incorrect
- After review comments are fixed — ALL impacted tests MUST be rerun
- The reviewer checks: alignment of tests with the Test Plan, coverage of MUST scenarios, boundary cases

---

## Exceptions

- UI-only changes (forms, styling) — tests are not required
- Configuration changes (attributes, metadata without code) — tests are not required
- Documentation, comments — tests are not required
- Quick-fix for simple issues — it is acceptable to write the test after the fix if time is critical (with a note in the report)

---

## Responsibility boundary: Developer vs Tester

Developer and Tester are not in competition — they cover **different layers** of testing.

### Developer writes tests **from within** (knows the implementation)

| What is covered | Description |
|-----------------|-------------|
| Unit tests for each public method | Red→Green→Refactor for every implemented method |
| MUST scenarios from the Test Plan | Primary positive paths described in the specification |
| Basic negative cases | Obvious input errors anticipated by the specification |

Developer **is not required** to cover: combinatorial edge cases, integration scenarios between modules, regression across adjacent subsystems — that is Tester’s area.

### Tester writes tests **from outside** (knows only the specification)

| What is covered | Description |
|-----------------|-------------|
| Edge cases | Boundary values, atypical input combinations |
| Negative scenarios | Invalid data, violations of business rules |
| Integration tests | Interaction between modules and subsystems |
| Regression tests | Protection against breaking adjacent functionality |

Tester **does not duplicate** Developer tests — first analyzes what is already covered, then supplements it.

### Rule when Tester encounters a failing test

```
Тест упал
    │
    ├── Это ошибка в тесте?  →  ДА  →  Tester исправляет тест, перезапускает
    │                                   (метка test_error — оркестратор не вмешивается)
    │
    └── Это баг в коде?      →  НЕТ →  СТОП. Tester НЕ правит BSL-код.
                                        Tester сигнализирует оркестратору (метка implementation_error):
                                        - какой тест упал
                                        - что ожидалось
                                        - что получено фактически
                                        Оркестратор возвращает задачу Developer.
```

> Subagents do not communicate directly. Tester does not call Developer —
> they finish their work with the `implementation_error` tag, and the orchestrator
> decides whether to rerun Developer.
> See [orchestrator.md](../workflows/orchestrator.md) → section 3.

---

## Related skills and policies

| Resource | Relation |
|----------|----------|
| [sdd-policy.md](./sdd-policy.md) | Test plan in the specification |
| [cross-review-policy.md](./cross-review-policy.md) | Reviewer checks test coverage |
| [mandatory-tools.md](./mandatory-tools.md) | `run_tests` after code changes |
| [skills/tool-usage/test-execution.md](../skills/tool-usage/test-execution.md) | Running tests |
| [skills/spec-writing/spec-standard.md](../skills/spec-writing/spec-standard.md) | Test Plan section in the specification |

---
depends_on:
  - framework/rules/sdd-policy.md
  - framework/rules/cross-review-policy.md
  - framework/rules/mandatory-tools.md
  - framework/skills/tool-usage/test-execution/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
