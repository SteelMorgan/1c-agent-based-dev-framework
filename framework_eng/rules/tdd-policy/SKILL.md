---
name: tdd-policy
description: "When working on code or tests, go Red -> Green"
alwaysApply: true
---

# TDD Policy (Test-Driven Development)

> **Trigger:** the test-writing phase (Phase 3b) or implementation (Phase 3c/3d). When triggered, apply the `test-writing` skill (`framework/skills/bsl-practices/test-writing/SKILL.md`).

## Layer Responsibilities (MUST)

- **Server/business logic is covered by unit tests (YaxUnit)**: filling checks, posting, calculations, queries, registers, schedules. **Client/UI behavior** (opening a form, `ПриСозданииНаСервере`/`ПриОткрытии`, visibility/accessibility/requiredness of elements, response to input, form commands) is the domain of **scenario (Vanessa) tests through the UI**, see `vanessa-scenario-policy`. Do not mix up the layers: when bypassing the UI with a server call, all client logic (client handlers, form events, conditional formatting, visibility/accessibility, response to input) remains completely untested - it is covered by neither such a "scenario" nor unit tests.

## MUST

- The test plan is described in the specification **BEFORE** the code.
- YaxUnit tests for MUST scenarios are written BEFORE implementation (Red -> Green -> Refactor).
- Tests are reviewed by the reviewer (coverage against the spec).
- After fixing review remarks, rerun ALL affected tests.
- **User/Role context in Test Plan:** if the code uses `SetPrivilegedMode`, role checks (`AccessRight`, `RoleAvailable`), or the result depends on the current user, the specification MUST explicitly state for each test in the "Test Plan" section: user name/role set, required mode (privileged or not), expected result (success/failure). Without this, a test under a full-rights runner (for example `AgentAI`) will produce a false positive. If this is technically impossible for unit, record it in the spec as an ADR with a move to the integration scope (Phase 4).

## Exceptions

- UI-only, configuration without code, documentation - tests MAY be omitted.
- Quick fix - a test after the fix is allowed (with a note in the report).

## Rule When a Test Fails in Tester

```
Test failed
  ├── Error in test → Tester fixes it (test_error)
  └── Bug in code → STOP. Label implementation_error + description.
                   Orchestrator returns the task to Developer.
```

---
depends_on:
  - framework/skills/bsl-practices/test-writing/SKILL.md
  - framework/rules/sdd-policy/SKILL.md
  - framework/rules/vanessa-scenario-policy/SKILL.md
---
