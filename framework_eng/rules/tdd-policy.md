---
name: tdd-policy
description: You write tests or code → tests before implementation (Red→Green). Apply the `test-writing` skill.
alwaysApply: true
---

# TDD Policy (Test-Driven Development)

> **Trigger:** the test-writing phase (Phase 3b) or implementation (Phase 3c/3d). When triggered, apply the `test-writing` skill (`framework/skills/bsl-practices/test-writing/SKILL.md`).

## Layer Responsibilities (MUST)

- **Server/business logic is covered by unit tests (YaxUnit)**: data filling, posting, calculations, queries, registers, scheduled jobs. **Client/UI behavior** (opening a form, `ПриСозданииНаСервере`/`ПриОткрытии`, visibility/availability/requiredness of elements, reaction to input, form commands) is the domain of **scenario (Vanessa) tests through the UI**, see `vanessa-scenario-policy`. Do not mix layers: if you bypass the UI with a server call, all client logic (client handlers, form events, conditional formatting, visibility/availability, reaction to input) remains completely untested - it is covered by neither such a "scenario" nor unit tests.

## MUST

- The test plan is described in the specification **BEFORE** code.
- YaxUnit tests for MUST scenarios are written BEFORE implementation (Red → Green → Refactor).
- Tests are reviewed by the reviewer (coverage against the spec).
- After fixing comments, rerun ALL affected tests.
- **User/Role context in Test Plan:** if the code uses `SetPrivilegedMode`, role checks (`AccessRight`, `RoleAvailable`), or the result depends on the current user, the specification MUST explicitly state for each test in the "Test Plan" section: user name/role set, required mode (privileged or not), expected result (success/failure). Without this, a test under a full-rights runner (for example `AgentAI`) will produce a false positive. If this is technically impossible for unit tests, record an ADR in the spec with transfer to the integration scope (Phase 4).

## Exceptions

- UI-only, configuration without code, documentation - tests MAY be skipped.
- Quick fix - a test after the fix is allowed (with a note in the report).

## Rule When Tester's Test Fails

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
  - framework/rules/vanessa-scenario-policy.md
---
