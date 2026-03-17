---
name: tdd-policy
description: TDD policy — tests are written before implementation and fixed in the specification.
---

# TDD Policy (Test-Driven Development)

> Tests and implementation are written by **different agents** in **different phases**. The test author does not know the implementation, the code author does not modify the tests.

```
Phase 3a: Scenario-Author  → .feature (BDD)   ┐ параллельно
Phase 3b: Developer-Tests  → unit-тесты (Red)  ┘
Phase 3c: Developer-Code   → код (Green)
Phase 4:  Tester           → edge cases, регрессия, BDD + unit
```

## MUST

- The test plan is described in the specification BEFORE the code
- YaxUnit tests for MUST scenarios are written BEFORE implementation
- Red -> Green -> Refactor cycle
- Tests are reviewed by a reviewer (coverage vs. spec)
- After fixing comments — rerun ALL affected tests

## Exceptions

- UI-only, configuration without code, documentation — tests MAY be skipped
- Quick-fix — allowed to add the test after the fix (noted in the report)

## Testing layers

| Layer | Phase | Agent | Covers |
|------|------|-------|-----------|
| BDD (acceptance) | 3a | Scenario-Author | Behavior through UI |
| TDD (unit) | 3b | Developer-Tests | Public methods, MUST scenarios, basic negatives |
| TDD (green) | 3c | Developer-Code | Implementation that passes unit tests |
| Coverage | 4 | Tester | Edge cases, integration, regression |

Phase 3a and 3b are **parallel**. Phase 3c starts after both are completed.

## Agent boundaries

- **Scenario-Author:** DOES NOT write unit tests, DOES NOT run scenarios, DOES NOT go beyond the specification
- **Developer-Tests:** MUST scenarios + basic negatives; DOES NOT cover combinatorial edge cases and integration
- **Tester:** complements coverage; DOES NOT duplicate Developer tests; DOES NOT edit BSL code

## Rule when a Tester test fails

```
Тест упал
  ├── Ошибка в тесте → Tester исправляет (test_error)
  └── Баг в коде → СТОП. Метка implementation_error + описание.
                   Оркестратор возвращает задачу Developer.
```

---
depends_on:
  - framework/rules/sdd-policy.md
  - framework/skills/tool-usage/code-analysis/test-execution/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
