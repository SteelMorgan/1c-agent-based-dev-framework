---
name: tdd-policy
description: TDD policy - tests are written before implementation and fixed in the specification.
---

# TDD Policy (Test-Driven Development)

> Tests and implementation are written by **different agents** in **different phases**. The test author does not know the implementation, and the code author does not modify the tests.

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
- Tests are reviewed by the reviewer (coverage against the spec)
- After fixing review comments, rerun ALL affected tests
- **User/Role context in Test Plan:** if the code uses `SetPrivilegedMode`, role checks (`AccessRight`, `RoleAvailable`), or the result depends on the current user, the specification MUST explicitly state for each test in the "Test Plan" section: the user name/role set, the required mode (privileged or not), and the expected result (success/failure). This is similar to the BDD policy (`vanessa-scenario-policy`), but for unit tests. Without this, a test under a full-rights runner (for example `AgentAI`) will produce a false positive: it will pass "by coincidence" through the privileged branch without checking role-dependent behavior. If this is technically impossible for unit tests, it is recorded in the spec as a separate ADR with transfer to integration scope (Phase 4)

## Exceptions

- UI-only, configuration without code, documentation - tests MAY be skipped
- Quick fix - testing after the fix is allowed (with a note in the report)

## Testing Layers

| Layer | Phase | Agent | Covers |
|------|------|-------|-----------|
| BDD (acceptance) | 3a | Scenario-Author | Behavior through the UI |
| TDD (unit) | 3b | Developer-Tests | Public methods, MUST scenarios, basic negatives |
| TDD (green) | 3c | Developer-Code | Implementation that passes unit tests |
| Coverage | 4 | Tester | Edge cases, integration, regression |

Phase 3a and 3b - **in parallel**. Phase 3c starts after both are complete.

## Agent Boundaries

- **Scenario-Author:** DOES NOT write unit tests, DOES NOT run scenarios, DOES NOT extend beyond the specification
- **Developer-Tests:** MUST scenarios + basic negatives; DOES NOT cover combinatorial edge cases and integration
- **Tester:** supplements coverage; DOES NOT duplicate Developer tests; DOES NOT modify BSL code

## Rule When a Tester Test Fails

```
Test failed
  ├── Error in the test → Tester fixes it (test_error)
  └── Bug in the code → STOP. Label implementation_error + description.
                   Orchestrator returns the task to Developer.
```

---
depends_on:
  - framework/rules/sdd-policy.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
