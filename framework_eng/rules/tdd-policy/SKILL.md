---
name: tdd-policy
description: "When writing code or tests, go Red -> Green"
alwaysApply: true
---

# TDD Policy (Test-Driven Development)

> **Trigger:** test-writing phase (Phase 3b) or implementation phase (Phase 3c/3d). When triggered, apply the `test-writing` skill (`framework/skills/bsl-practices/test-writing/SKILL.md`).

## Layer Responsibilities (MUST)

- **Server/business logic is verified by unit tests (YaxUnit)**: filling, posting, calculations, queries, registers, routines. **Client/UI behavior** (opening a form, `ПриСозданииНаСервере`/`ПриОткрытии`, visibility/availability/requiredness of elements, reaction to input, form commands) is the domain of **scenario (Vanessa) tests through UI**, see `vanessa-scenario-policy`. Do not substitute the layer: if you bypass the UI with a server call, all client logic (client handlers, form events, conditional formatting, visibility/availability, reaction to input) remains completely untested - it is not covered by either such a "scenario" or a unit test.

## MUST

- The test plan is described in the specification **BEFORE** code.
- YaxUnit tests for MUST scenarios are written BEFORE implementation (Red -> Green -> Refactor).
- Tests are reviewed for coverage against the spec.
- After fixing remarks, rerun ALL affected tests.
- **User/Role context in Test Plan:** if code uses `SetPrivilegedMode`, role checks (`AccessRight`, `RoleAvailable`) or the result depends on the current user - the specification MUST explicitly specify for each test in the "Test Plan" section: user name/role set, required mode (privileged or not), expected result (success/failure). Without this, a test under a full-rights runner (for example `AgentAI`) will produce a false positive. If this is technically impossible for unit, record an ADR in the spec with a transfer to integration scope (Phase 4).

## Exceptions

- UI-only, configuration without code, documentation - tests MAY be skipped.
- Quick-fix - a test after the fix is allowed (with a note in the report).

## Rule when Tester test fails

```
Тест упал
  ├── Ошибка в тесте → Tester исправляет (test_error)
  └── Баг в коде → СТОП. Метка implementation_error + описание.
                   Оркестратор возвращает задачу Developer.
```

---
depends_on:
  - framework/skills/bsl-practices/test-writing/SKILL.md
  - framework/rules/sdd-policy/SKILL.md
  - framework/rules/vanessa-scenario-policy/SKILL.md
---
