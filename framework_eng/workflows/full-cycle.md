---
name: full-cycle
description: Full development cycle with mandatory cross-review at every phase.
---


# Workflow: Full development cycle (Full Cycle)

> **DETERMINISTIC** workflow. Cross-review at every step. Used for mid- and high-complexity tasks.

---

## Purpose

The full development cycle is a tightly structured process with mandatory artifact verification at each phase. Every output from one agent is reviewed by the next agent (reviewer) against a checklist. Guarantees quality and predictable results.

---

## Process diagram

```
  ┌──────────────────┐
  │ Задача от        │
  │ пользователя     │
  └────────┬─────────┘
           ▼
╔══════════════════════════╗
║  Phase 0: Классификация  ║
║  Explorer (Economy)      ║
╚════════════╤═════════════╝
             │
     ┌───────┴───────┐
     ▼               ▼
 [Простая]    [Средняя/Сложная]
     │               │
     ▼               ▼
┌─────────┐  ╔═══════════════════════════════════════╗
│quick-fix│  ║  Phase 1: Анализ                      ║
│ 3 шага  │  ║  Analyst (Mid/High) ──► Reviewer (P)  ║
└────┬────┘  ║       ▲ BLOCK ◄──────────┘            ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 2: Архитектура                 ║
     │       ║  Architect (High) ──► Reviewer (P)    ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ╠═══════════════════════════════════════╣
     │       ║  ⏸  STOP: ждём ОК от пользователя    ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 3a: Тесты                      ║
     │       ║  Developer-Tests (High) ──► Reviewer  ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 3b: Реализация                 ║
     │       ║  Developer-Code (High) ──► Reviewer   ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ║  test_failure → Reviewer → причина    ║
     │       ║  → возврат нужному агенту             ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 4: Покрытие и регрессия        ║
     │       ║  Tester (Mid/High) ──► Reviewer (H)   ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ╚══════════════════╤════════════════════╝
     │                          │
     └──────────┬───────────────┘
                ▼
  ┌──────────────────────┐
  │ Результат            │
  │ пользователю         │
  └──────────────────────┘

  Легенда: (P) = Premium tier, (H) = High tier
           BLOCK = возврат автору, макс. 3 итерации
```

---

## Workflow phases

### Phase 0: Classification (Explorer → Economy)

| Element | Description |
|---------|----------|
| **Input** | Task from the user |
| **Action** | Explorer investigates the codebase: locates affected modules, builds call graphs (incoming + outgoing), identifies transitive dependencies |
| **Output** | List of affected modules + call graphs + factual data (dependency depth, number of call sites) |
| **Routing** | Orchestrator classifies the task based on Explorer data: Simple → `quick-fix.md`; Mid/High → Phase 1 |

> Explorer artifacts (`explorer-context.md`) are forwarded by the orchestrator to both Phase 1 (Analyst) and Phase 2 (Architect) as context for affected modules and dependencies.

**Tools:** `navigate_symbol`, `get_call_graph`, `list_metadata_objects`, `get_metadata_structure`, `get_diagnostics`

---

### Phase 1: Analysis and specification (Analyst → Mid/High)

| Element | Description |
|---------|----------|
| **Input** | Task + `explorer-context.md` (list of affected modules, call graphs) |
| **Action** | Analyst drafts a specification in MADR 4.0 format + RFC 2119 |
| **Output** | SPEC document (specification file) |
| **Review** | Reviewer (Premium) checks the spec against the specification checklist |
| **Iterations** | Maximum 3, then escalate to the user |

**Review checklist:** [cross-review-policy.md](../rules/cross-review-policy.md) → Specification checklist

---

### Phase 2: Architecture (Architect → High/Premium)

| Element | Description |
|---------|----------|
| **Input** | Approved specification + `explorer-context.md` (call graphs, dependencies of affected modules) |
| **Action** | Architect designs the solution (Technical Design) and decomposes the spec into a Task Breakdown JSON (tasks, dependencies, task types, links to spec sections) |
| **Output** | Technical Design + Task Breakdown JSON (separate `.json` file) + link/short excerpt of the JSON in the spec |
| **Review** | Reviewer (Premium) checks the architecture and Task Breakdown JSON against checklists |
| **STOP** | Await user confirmation before Phase 3 |

**Important:** This phase is blocked until the user explicitly approves. It is the control point for architectural decisions.

---

### Phase 3a: Writing tests (Developer-Tests → High)

| Element | Description |
|---------|----------|
| **Input** | Approved spec + Test Plan + `task_dir` |
| **Action** | Developer-Tests writes YaxUnit unit tests for ALL MUST scenarios from the Test Plan — strictly per the spec, without implementation. The tests MUST fail (Red). |
| **Output** | Test modules (.bsl) — failing tests (implementation is not yet available) |
| **Review** | Reviewer (Premium) verifies the tests: coverage of MUST scenarios, correctness of assertions |

> Developer-Tests DOES NOT see or influence the implementation. Conflict of interest is eliminated.

---

### Phase 3b: Implementation (Developer-Code → High)

| Element | Description |
|---------|----------|
| **Input** | Approved spec + Technical Design + Task Breakdown JSON + test modules from Phase 3a + `task_dir` |
| **Action** | Developer-Code writes BSL code so that the tests from Phase 3a pass (Green). DOES NOT write or modify tests. |
| **Output** | BSL modules + metadata XML — all Phase 3a tests pass |
| **Review** | Reviewer (Premium) checks the code against the BSL checklist |
| **test_failure** | If tests fail → Developer-Code signals the orchestrator with the `test_failure` tag; Reviewer determines the reason: bug in test → return to Developer-Tests, bug in code → return to Developer-Code |

**TDD rule is enforced by the orchestrator:** Phase 3a ALWAYS precedes Phase 3b.
See [tdd-policy.md](../rules/tdd-policy.md)

---

### Phase 4: Coverage and regression (Tester → Mid/High)

| Element | Description |
|---------|----------|
| **Input** | Code + tests from Phase 3 + test plan from the spec |
| **Action** | Tester verifies test plan coverage, writes missing tests (edge cases, integration, regression), runs a full suite |
| **Output** | Complete set of tests (unit + regression) + execution results + report to the user |
| **Review** | Reviewer (High) checks the tests against the test checklist |

**Important:** Phase 4 DOES NOT duplicate Phase 3. Developers write unit tests via TDD. Tester supplements coverage: edge cases, negative scenarios, integration tests, regression.

**Tools:** `run_tests`, `check_syntax`, `get_diagnostics`

---

## Artifact handoff between phases

### Handoff rules

| From phase | To phase | Artifact | Format |
|---------|--------|-----------|--------|
| Phase 0 | Phase 1 | List of affected modules + call graphs (incoming/outgoing) + dependency depth | `explorer-context.md` in `task_dir` |
| Phase 0 | Phase 2 | Same Explorer artifacts — orchestrator forwards again | `explorer-context.md` in `task_dir` |
| Phase 0 | quick-fix | Classification + list of modules | `explorer-context.md` in `task_dir` |
| Phase 1 | Phase 2 | SPEC document | Markdown, MADR 4.0 |
| Phase 2 | Phase 3a | SPEC + Technical Design + Task Breakdown JSON | Markdown + JSON |
| Phase 3a | Phase 3b | Test modules (.bsl) — failing tests | .bsl files |
| Phase 3b | Phase 4 | BSL modules + all tests green | .bsl files |
| Phase 4 | User | Entire artifact set | Folder/task bundle |

### Required fields in artifacts

- **Specification:** Context, Requirements (RFC 2119), Scope, Test Plan, Acceptance Criteria
- **Technical Design:** Components, interfaces, division of responsibility (user/agent)
- **Task Breakdown JSON:** Separate `.json` file in the “template + example” format (no JSON Schema); required fields are task identifiers (`task_id`), task types (`task_type`), dependencies (`depends_on`), links to spec sections (`spec_refs`), completion criteria
- **Code:** File path, adherence to coding standards
- **Tests:** Link to MUST scenarios from the spec

### Handoff channels

- **Within a single session:** Passed via the orchestrator context
- **Between sessions:** Stored in `.tasks/task-[name]/` or in the project configuration

---

## Error handling

### Review blocks (BLOCK)

| Situation | Action |
|----------|----------|
| Reviewer placed BLOCK | Artifact author fixes issues from the comments |
| Iteration ≤ 3 | Resubmit for review |
| Iteration > 3 | **Escalate to the user.** Work is paused. The user decides: fix manually, cancel, or remove the BLOCK with justification |

### User rejection

| Rejection point | Action |
|------------------|----------|
| Phase 2 (architecture) | Architect reworks the design per the user’s comments. Return to architecture review. |
| Any phase | User may request rollback to the previous phase. Context is preserved. |

### Tests failed

| Situation | Action |
|----------|----------|
| `run_tests` reported errors in Phase 3 | **Developer** analyzes the cause: bug in the test → fix the test; bug in the code → fix the implementation. Re-run. |
| `run_tests` reported errors in Phase 4 | **Tester** identifies the cause: incorrect test → fix the test; implementation bug → **return to Developer** with a description of the problem (Tester DOES NOT fix implementation code). |
| `check_syntax` failure | Developer fixes the syntax. Mandatory check before review. |
| Tests miss MUST from the spec | Reviewer places BLOCK based on the test checklist. Tester adds missing tests. |

### Capability unavailable

| Situation | Action |
|----------|----------|
| MCP tool unavailable | Agent records the reason for skipping (see mandatory-tools escape hatch). Continues with limitations or escalates to the user. |

---

## Related resources

| Resource | Relation |
|--------|-------|
| [quick-fix.md](./quick-fix.md) | Route for simple tasks |
| [orchestrator.md](./orchestrator.md) | Orchestration protocol |
| [cross-review-policy.md](../rules/cross-review-policy.md) | Review protocol, checklists |
| [docs/SPEC-001-framework-architecture.md](../../docs/SPEC-001-framework-architecture.md) | Overall architecture |

---
depends_on:
  - framework/workflows/quick-fix.md
  - framework/subagents/explorer.md
  - framework/subagents/analyst.md
  - framework/subagents/architect.md
  - framework/subagents/developer-tests.md
  - framework/subagents/developer-code.md
  - framework/subagents/tester.md
  - framework/subagents/reviewer.md
  - framework/rules/cross-review-policy.md
  - framework/rules/tdd-policy.md
  - framework/rules/mandatory-tools.md
---
