---
name: full-cycle
description: Full development cycle with mandatory cross-review on every phase.
---



# Workflow: Full development cycle (Full Cycle)

> **DETERMINISTIC** workflow. Cross-review at every step. Used for medium and high complexity tasks.

---

## Purpose

The full development cycle is a strictly structured process with mandatory artifact verification at each phase. Every output from one agent is reviewed by the next agent (reviewer) against a checklist. Ensures quality and predictability of the result.

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
     │       ║  Phase 3a + 3b: ПАРАЛЛЕЛЬНО           ║
     │       ║                                       ║
     │       ║  ┌─────────────────────────────────┐  ║
     │       ║  │ 3a: BDD-сценарии                │  ║
     │       ║  │ Scenario-Author ──► Reviewer     │  ║
     │       ║  │     ▲ BLOCK ◄──────────┘         │  ║
     │       ║  └─────────────────────────────────┘  ║
     │       ║  ┌─────────────────────────────────┐  ║
     │       ║  │ 3b: Unit-тесты                  │  ║
     │       ║  │ Developer-Tests ──► Reviewer     │  ║
     │       ║  │     ▲ BLOCK ◄──────────┘         │  ║
     │       ║  └─────────────────────────────────┘  ║
     │       ║                                       ║
     │       ║  Оба MUST завершиться перед Phase 3c  ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 3c: Реализация                 ║
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
|---------|-------------|
| **Input** | Task from the user |
| **Action** | Explorer inspects the codebase: locates affected modules, builds call graphs (incoming + outgoing), identifies transitive dependencies |
| **Output** | List of affected modules + call graphs + factual data (dependency depth, number of call sites) |
| **Routing** | Orchestrator classifies the task based on Explorer data: Simple → `quick-fix.md`; Medium/Complex → Phase 1 |

> Explorer artifacts (`task_dir/.context/explorer-context.md`) are passed by the orchestrator to both Phase 1 (Analyst) and Phase 2 (Architect) as context for the affected modules and dependencies.

**Tools:** `navigate_symbol`, `get_call_graph`, `list_metadata_objects`, `get_metadata_structure`, `get_diagnostics`

---

### Phase 1: Analysis and specification (Analyst → Mid/High)

| Element | Description |
|---------|-------------|
| **Input** | Task + `task_dir/.context/explorer-context.md` (list of affected modules, call graphs) |
| **Action** | Analyst creates a specification using MADR 4.0 + RFC 2119 |
| **Output** | `task_dir/.spec/spec.md` (SPEC document) |
| **Review** | Reviewer (Premium) verifies the spec against the specification checklist |
| **Iterations** | Up to 3, then escalate to the user |

**Review checklist:** [cross-review-policy.md](../rules/cross-review-policy.md) → Specification checklist

---

### Phase 2: Architecture (Architect → High/Premium)

| Element | Description |
|---------|-------------|
| **Input** | Approved `task_dir/.spec/spec.md` + `task_dir/.context/explorer-context.md` (call graphs, dependencies of affected modules) |
| **Action** | Architect designs the solution (Technical Design) and decomposes the spec into a Task Breakdown JSON (tasks, dependencies, task types, references to spec sections) |
| **Output** | `task_dir/.spec/technical-design.md` + `task_dir/.context/task-breakdown.json` + a link/summary of the JSON inside `task_dir/.spec/spec.md` |
| **Review** | Reviewer (Premium) checks the architecture and Task Breakdown JSON against the checklists |
| **STOP** | Await user confirmation before Phase 3 |

**Important:** The phase is blocked until explicit user approval. This is the control point for architectural decisions.

---

### Phase 3a: BDD scenarios (Scenario-Author → Mid/High)

| Element | Description |
|---------|-------------|
| **Input** | Approved `task_dir/.spec/spec.md` with the Acceptance Scenarios section + `task_dir` |
| **Action** | Scenario-Author converts intent scenarios from the specification into executable Vanessa Automation `.feature` files. Intent scenarios are formalized requirements, NOT templates. |
| **Output** | `.feature` files in `<project_root>/vanessa-tests/features/` |
| **Review** | Reviewer (scope=bdd) verifies: coverage of scenarios from the spec, correct Gherkin, proper use of the step library |

> Phase 3a runs **IN PARALLEL** with Phase 3b. They are independent.

---

### Phase 3b: Writing unit tests (Developer-Tests → High)

| Element | Description |
|---------|-------------|
| **Input** | Approved `task_dir/.spec/spec.md` + Test Plan + `task_dir` |
| **Action** | Developer-Tests writes YaxUnit unit tests for ALL MUST scenarios from the Test Plan — strictly following the spec, without implementation. Tests MUST fail (Red). |
| **Output** | Test modules (.bsl) — tests do not pass (implementation is absent) |
| **Review** | Reviewer (Premium) checks the tests: coverage of MUST scenarios, correctness of assertions |

> Developer-Tests DOES NOT see or modify the implementation. Conflict of interest is excluded.
> Phase 3b runs **IN PARALLEL** with Phase 3a. They are independent.

---

### Phase 3c: Implementation (Developer-Code → High)

| Element | Description |
|---------|-------------|
| **Input** | `task_dir/.spec/spec.md` + `task_dir/.spec/technical-design.md` + `task_dir/.context/task-breakdown.json` + test modules from Phase 3b + `.feature` files from Phase 3a + `task_dir` |
| **Action** | Developer-Code writes BSL code so that the tests from Phase 3b pass (Green). Does NOT write or modify the tests. Runs only the Phase 3b tests (targeted run), not the full regression suite. |
| **Output** | BSL modules + metadata XML — all Phase 3b tests pass |
| **Review** | Reviewer (Premium) checks the code using the BSL checklist |
| **test_failure** | If tests fail → Developer-Code signals the orchestrator with the `test_failure` tag. If the `suspected_test_error` flag is set, the orchestrator triggers Reviewer arbitration: align spec + technical-design + tests + code and record which artifact is wrong (tests or code). Based on the reviewer summary the orchestrator routes back to Developer-Tests or Developer-Code. |

**TDD+BDD rule is enforced by the orchestrator:** Phase 3a (BDD) and Phase 3b (unit tests) run in parallel. Phase 3c starts ONLY after both have completed and passed review.
See [tdd-policy.md](../rules/tdd-policy.md)

---

### Phase 4: Coverage and regression (Tester → Mid/High)

| Element | Description |
|---------|-------------|
| **Input** | Code from Phase 3c + unit tests from Phase 3b + `.feature` files from Phase 3a + the test plan from `task_dir/.spec/spec.md` |
| **Action** | Tester runs all tests (unit + BDD), verifies coverage, adds missing tests (edge cases, integration, regression), and runs the full pass |
| **Output** | Full test suite (unit + BDD + regression) + run results + `task_dir/.spec/test-report.md` |
| **Review** | Reviewer (High) checks the tests using the test checklist |

**Important:** Phase 4 does NOT duplicate Phase 3. Developers write unit tests via TDD. Tester supplements coverage: edge cases, negative scenarios, integration tests, regression.

**Tools:** `run_tests`, `check_syntax`, `get_diagnostics`

---

## Artifact handoff between phases

### Handoff rules

| From phase | To phase | Artifact | Format |
|------------|----------|----------|--------|
| Phase 0 | Phase 1 | List of affected modules + call graphs (incoming/outgoing) + dependency depth | `task_dir/.context/explorer-context.md` |
| Phase 0 | Phase 2 | Same Explorer artifacts — orchestrator reuses them | `task_dir/.context/explorer-context.md` |
| Phase 0 | quick-fix | Classification + list of modules | `task_dir/.context/explorer-context.md` |
| Phase 1 | Phase 2 | `task_dir/.spec/spec.md` | Markdown, MADR 4.0 |
| Phase 2 | Phase 3a | `task_dir/.spec/spec.md` (Acceptance Scenarios) + `task_dir` | Markdown |
| Phase 2 | Phase 3b | `task_dir/.spec/spec.md` + `task_dir/.spec/technical-design.md` + `task_dir/.context/task-breakdown.json` | Markdown + JSON |
| Phase 3a | Phase 3c | `.feature` files (Vanessa BDD scenarios) | `.feature` |
| Phase 3b | Phase 3c | Test modules (.bsl) — failing unit tests | `.bsl` files |
| Phase 3c | Phase 4 | BSL modules + `.feature` + all unit tests green | `.bsl` + `.feature` |
| Phase 4 | user | Entire set of artifacts | Folder/task bundle |

### Required fields in artifacts

- **Specification:** Context, Requirements (RFC 2119), Scope, Test Plan, Acceptance Criteria
- **Technical Design (`task_dir/.spec/technical-design.md`):** Components, interfaces, division of responsibility (user/agent)
- **Task Breakdown JSON (`task_dir/.context/task-breakdown.json`):** Separate `.json` file in a “template + example” format (without JSON Schema); required identifiers for tasks (`task_id`), task types (`task_type`), dependencies (`depends_on`), references to spec sections (`spec_refs`), completion criteria
- **Code:** File path, compliance with coding standards
- **Tests:** Link to MUST scenarios inside `task_dir/.spec/spec.md`

### Channels for handoff

- **Within a single session:** Handoff via orchestrator context
- **Between sessions:** Store in `.tasks/task-[name]/` or in the project configuration

---

## Error handling

### Review blocks (BLOCK)

| Situation | Action |
|-----------|--------|
| Reviewer placed BLOCK | Artifact author resolves issues according to the comments |
| Iteration ≤ 3 | Resubmit for review |
| Iteration > 3 | **Escalate to the user.** Work stops. The user decides: fix manually, cancel, or remove the BLOCK with justification |

### User rejection

| Rejection point | Action |
|-----------------|--------|
| Phase 2 (architecture) | Architect reworks the design based on user feedback. Return to architecture review. |
| Any phase | The user may request rollback to the previous phase. Context is preserved. |

### Tests failed

| Situation | Action |
|-----------|--------|
| `run_tests` produced errors in Phase 3c | **Developer-Code** analyzes the cause. If the issue is in the code → fix the implementation and rerun the Phase 3b targeted tests. If the issue is suspected in the test → set `test_failure` + `suspected_test_error` and finish. The orchestrator starts Reviewer arbitration and, based on the summary, routes the task to Developer-Tests or Developer-Code. |
| `run_tests` produced errors in Phase 4 | **Tester** determines the cause: incorrect test → fix the test; implementation bug → **return to Developer** with the issue description (Tester DOES NOT fix the implementation code). |
| Failure during `check_syntax` | Developer fixes the syntax. Mandatory check before review. |
| Tests do not cover MUST scenarios from the spec | Reviewer places BLOCK per the test checklist. Tester adds tests. |

### Capability unavailable

| Situation | Action |
|-----------|--------|
| MCP tool is unavailable | Agent records the reason for skipping (see mandatory-tools escape hatch). Continues with limitations or escalates to the user. |

---

## Related resources

| Resource | Relation |
|----------|----------|
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
  - framework/subagents/scenario-author.md
  - framework/subagents/developer-tests.md
  - framework/subagents/developer-code.md
  - framework/subagents/tester.md
  - framework/subagents/reviewer.md
  - framework/rules/tdd-policy.md
---
