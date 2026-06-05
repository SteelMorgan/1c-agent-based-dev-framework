---
name: tdd-policy
description: You write tests or code → tests before implementation (Red→Green). Apply the test-writing skill.
alwaysApply: true
---

# TDD Policy (Test-Driven Development)

> **Trigger:** test-writing phase (Phase 3b) or implementation (Phase 3c/3d). When triggered, apply the `test-writing` skill (`framework/skills/bsl-practices/test-writing/SKILL.md`).

## MUST

- The test plan is described in the specification **BEFORE** the code.
- YaxUnit tests for MUST scenarios are written BEFORE implementation (Red → Green → Refactor).
- Tests are reviewed by the reviewer (coverage against the spec).
- After fixing review comments, rerun ALL affected tests.
- **User/Role context in Test Plan:** if the code uses `SetPrivilegedMode`, role checks (`AccessRight`, `RoleAvailable`), or the result depends on the current user, the specification MUST explicitly state for each test in the "Test Plan" section: the user name/role set, the required mode (privileged or not), and the expected result (success/failure). Without this, a test under a full-rights runner (for example `AgentAI`) will produce a false positive. If this is technically impossible for unit tests, record it in the spec as an ADR with transfer to integration scope (Phase 4).

## Exceptions

- UI-only, configuration without code, documentation — tests MAY be skipped.
- Quick-fix — testing after the fix is allowed (with a note in the report).

## Rule When a Tester Test Fails

```
Тест упал
  ├── Ошибка в тесте → Tester исправляет (test_error)
  └── Баг в коде → СТОП. Метка implementation_error + описание.
                   Оркестратор возвращает задачу Developer.
```

---
depends_on:
  - framework/skills/bsl-practices/test-writing/SKILL.md
  - framework/rules/sdd-policy.md
---
