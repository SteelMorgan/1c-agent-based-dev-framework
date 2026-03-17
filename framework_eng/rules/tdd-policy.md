---
name: tdd-policy
description: TDD policy — tests are written before implementation and captured in the specification.
---



# TDD Policy (Test-Driven Development)

> RULES — mandatory policies that constrain agent behavior. Tests are written before implementation, not after.

---

## Purpose

This rule ensures code quality and predictability through the discipline of TDD.

**Key principle:** tests and implementation are written by **different agents** in **different phases**. The phase order (tests first, code second) is enforced by the **orchestrator**, not the agents themselves. This prevents conflicts of interest: the test author does not know the implementation, and the code author does not modify the tests.

```
Оркестратор управляет порядком:
  Phase 3a: Scenario-Author  → конвертирует intent-сценарии в .feature (BDD)  ┐ параллельно
  Phase 3b: Developer-Tests  → пишет тесты по спеке (Red)                    ┘
  Phase 3c: Developer-Code   → пишет код чтобы тесты прошли (Green)
  Phase 4:  Tester           → запускает все тесты (unit + BDD), дополняет edge cases
```

---

## Trigger Conditions

This rule applies when the agent:

- Implements new functionality with business logic
- Makes changes to tested modules
- Fixes bugs in code that already has tests
- Works within a full-cycle workflow with an approved specification

---

## Requirements

### MUST (mandatory)

| Requirement | Description |
|------------|-------------|
| Test plan in the specification | The test plan MUST be described in the specification BEFORE writing code |
| Tests before implementation | YaxUnit tests for MUST-scenarios MUST be written BEFORE implementing the business logic |
| Red → Green → Refactor cycle | Follow the order: RED (test fails), then GREEN (minimal code), then Refactor |
| Test review | Tests MUST be reviewed by another agent (coverage vs specification) |
| Rerun after fixes | After addressing review comments — run ALL impacted tests |

### SHOULD (strong recommendation)

| Requirement | Description |
|------------|-------------|
| Tests for SHOULD scenarios | Tests for SHOULD-priority scenarios SHOULD be written after the basic implementation |

### MAY (acceptable exceptions)

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

## Test Requirements

- Tests MUST call `run_tests` after changes
- The test MUST fail before implementation (Red) — otherwise the scenario is already covered or the test is incorrect
- After fixing review comments — MUST rerun all impacted tests
- Reviewer checks: alignment of tests with the Test Plan, coverage of MUST-scenarios, edge cases

---

## Exceptions

- UI-only changes (forms, styling) — tests are optional
- Configuration changes (attributes, metadata without code) — tests are optional
- Documentation, comments — tests are optional
- Quick-fix for simple tasks — it is acceptable to write the test after the fix if time is critical (with a note in the report)

---

## BDD Integration

BDD scenarios (Vanessa Automation, Phase 3a) are a **parallel acceptance layer**, not part of the Red/Green TDD cycle. They verify observable behavior through the UI, not the correctness of implementation at the method level.

| Layer | Phase | Agent | What it verifies |
|-------|-------|-------|------------------|
| BDD (acceptance) | Phase 3a | Scenario-Author | User-facing behavior |
| TDD (unit) | Phase 3b | Developer-Tests | Correctness of public methods |
| TDD (green) | Phase 3c | Developer-Code | Implementation that passes unit tests |
| Coverage | Phase 4 | Tester | Edge cases, regression, running BDD + unit |

Phase 3a and Phase 3b run **in parallel** — they are independent. Phase 3c starts only after both finish.

---

## Responsibility boundary: Scenario-Author vs Developer vs Tester

Agents do not compete — they cover **different testing layers**.

### Scenario-Author writes BDD scenarios **per the specification** (Phase 3a)

| What is covered | Description |
|-----------------|-------------|
| Acceptance scenarios | Converts intent from the specification into executable `.feature` files |
| Observable behavior | One scenario equals one user-level behavior via the UI |

Scenario-Author **does not write** unit tests, **does not run** scenarios, and **does not expand** beyond the specification.

### Developer writes unit tests **per the specification** (Phase 3b, understands implementation in Phase 3c)

| What is covered | Description |
|-----------------|-------------|
| Unit tests for each public method | Red→Green→Refactor for every method being implemented |
| MUST-scenarios from the Test Plan | Primary positive flows described in the specification |
| Basic negative cases | Obvious bad inputs foreseen by the specification |

Developer is **not required** to cover: combinatorial edge cases, integration flows between modules, regression across adjacent subsystems — that is Tester’s domain.

### Tester writes tests **from outside** (knows only the specification)

| What is covered | Description |
|-----------------|-------------|
| Edge cases | Boundary values, atypical input combinations |
| Negative scenarios | Invalid data, business-rule violations |
| Integration tests | Interactions between modules and subsystems |
| Regression tests | Protection against breakage in adjacent functionality |

Tester **does not duplicate** Developer tests — first analyzes what is already covered, then fills in gaps.

### Rule when a Tester test fails

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
> it finishes with the label `implementation_error`, and the orchestrator
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
  - framework/skills/tool-usage/code-analysis/test-execution/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
