---
name: full-cycle
description: Full development cycle with mandatory cross-review at every phase.
---



# Workflow: Full Development Cycle (Full Cycle)

> **DETERMINISTIC** workflow. Cross-review at every step. Used for medium and high complexity tasks.

---

## Purpose

Full development cycle is a tightly structured process with mandatory artifact checks at every phase. Each output from one agent is reviewed by the next agent (the reviewer) according to a checklist. It guarantees quality and predictability of the result.

---

## Process diagram

```
  ┌──────────────────┐
  │ Task from        │
  │ user             │
  └────────┬─────────┘
           ▼
╔══════════════════════════╗
║  Phase 0: Classification ║
║  Explorer (Economy)      ║
╚════════════╤═════════════╝
             │
     ┌───────┴───────┐
     ▼               ▼
 [Simple]    [Medium/Complex]
     │               │
     ▼               ▼
┌─────────┐  ╔═══════════════════════════════════════╗
│quick-fix│  ║  Phase 1: Analysis                     ║
│ 3 steps │  ║  Analyst (Mid/High) ──► Reviewer (P)  ║
└────┬────┘  ║       ▲ BLOCK ◄──────────┘            ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 2: Architecture               ║
     │       ║  Architect (High) ──► Reviewer (P)    ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ╠═══════════════════════════════════════╣
     │       ║  ⏸  STOP: awaiting OK from user       ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 3a: Tests                      ║
     │       ║  Developer-Tests (High) ──► Reviewer  ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 3b: Implementation             ║
     │       ║  Developer-Code (High) ──► Reviewer   ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ║  test_failure → Reviewer → cause      ║
     │       ║  → return to the appropriate agent    ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 4: Coverage and Regression     ║
     │       ║  Tester (Mid/High) ──► Reviewer (H)   ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ╚══════════════════╤════════════════════╝
     │                          │
     └──────────┬───────────────┘
                ▼
  ┌──────────────────────┐
  │ Result               │
  │ to user              │
  └──────────────────────┘

  Legend: (P) = Premium tier, (H) = High tier
           BLOCK = return to the author, max. 3 iterations
```

---

## Workflow phases

### Phase 0: Classification (Explorer → Economy)

| Element | Description |
|---------|-------------|
| **Input** | Task from user |
| **Action** | Explorer investigates the codebase: finds affected modules, builds call graphs (incoming + outgoing), identifies transitive dependencies |
| **Output** | List of affected modules + call graphs + factual data (dependency depth, number of call points) |
| **Routing** | Orchestrator classifies the task based on Explorer data: Simple → `quick-fix.md`; Medium/Complex → Phase 1 |

> Explorer artifacts (`explorer-context.md`) are passed to the orchestrator and to Phase 1 (Analyst) and Phase 2 (Architect) — as context of impacted modules and dependencies.

**Tools:** `navigate_symbol`, `get_call_graph`, `list_metadata_objects`, `get_metadata_structure`, `get_diagnostics`

---

### Phase 1: Analysis and specification (Analyst → Mid/High)

| Element | Description |
|---------|-------------|
| **Input** | Task + `explorer-context.md` (list of affected modules, call graphs) |
| **Action** | Analyst creates a specification in MADR 4.0 format + RFC 2119 |
| **Output** | SPEC document (specification file) |
| **Review** | Reviewer (Premium) checks the spec against the specification checklist |
| **Iterations** | Maximum 3, then escalation to the user |

**Review checklist:** [cross-review-policy.md](../rules/cross-review-policy.md) → Specification checklist

---

### Phase 2: Architecture (Architect → High/Premium)

| Element | Description |
|---------|-------------|
| **Input** | Approved specification + `explorer-context.md` (call graphs, dependencies of affected modules) |
| **Action** | Architect designs the solution (Technical Design) and decomposes the spec into a Task Breakdown JSON (tasks, dependencies, task types, links to spec sections) |
| **Output** | Technical Design + Task Breakdown JSON (separate `.json` file) + link/brief JSON summary in the spec |
| **Review** | Reviewer (Premium) checks the architecture and Task Breakdown JSON using checklists |
| **STOP** | Await user confirmation before Phase 3 |

**Important:** The phase is blocked until explicit user approval. This is the control point for architectural decisions.

---

### Phase 3a: Writing tests (Developer-Tests → High)

| Element | Description |
|---------|-------------|
| **Input** | Approved spec + Test Plan + `task_dir` |
| **Action** | Developer-Tests writes YaxUnit unit tests for ALL MUST scenarios from the Test Plan — strictly according to the spec, without implementation. Tests MUST fail (Red). |
| **Output** | Test modules (.bsl) — tests do not pass (implementation is not ready yet) |
| **Review** | Reviewer (Premium) checks the tests: coverage of MUST scenarios, correctness of assertions |

> Developer-Tests DOES NOT see or influence the implementation. Conflict of interest is eliminated.

---

### Phase 3b: Implementation (Developer-Code → High)

| Element | Description |
|---------|-------------|
| **Input** | Approved spec + Technical Design + Task Breakdown JSON + test modules from Phase 3a + `task_dir` |
| **Action** | Developer-Code writes BSL code so that the tests from Phase 3a pass (Green). DOES NOT write or modify tests. Runs only Phase 3a tests (targeted run), not the full regression suite. |
| **Output** | BSL modules + XML metadata — all tests from Phase 3a pass |
| **Review** | Reviewer (Premium) checks the code against the BSL checklist |
| **test_failure** | If tests fail → Developer-Code notifies the orchestrator with the `test_failure` tag. If `suspected_test_error` flag is set, orchestrator triggers Reviewer arbitration: compare spec + technical design + tests + code, identify which artifact is faulty (tests or code). Based on the reviewer summary the orchestrator routes the task: to Developer-Tests or back to Developer-Code. |

**The TDD rule is enforced by the orchestrator:** Phase 3a ALWAYS precedes Phase 3b.
See [tdd-policy.md](../rules/tdd-policy.md)

---

### Phase 4: Coverage and regression (Tester → Mid/High)

| Element | Description |
|---------|-------------|
| **Input** | Code + tests from Phase 3 + test plan from the spec |
| **Action** | Tester verifies test plan coverage, adds missing tests (edge cases, integration, regression), runs the full suite |
| **Output** | Complete set of tests (unit + regression) + run results + report to the user |
| **Review** | Reviewer (High) checks the tests using the test checklist |

**Important:** Phase 4 does NOT duplicate Phase 3. The developer writes unit tests via TDD. Tester augments coverage: edge cases, negative scenarios, integration tests, regression.

**Tools:** `run_tests`, `check_syntax`, `get_diagnostics`

---


## Artifact handoff between phases

### Handoff rules

| From phase | To phase | Artifact | Format |
|------------|----------|----------|--------|
| Phase 0 | Phase 1 | List of affected modules + call graphs (incoming/outgoing) + dependency depth | `explorer-context.md` in `task_dir` |
| Phase 0 | Phase 2 | Same Explorer artifacts — orchestrator forwards them again | `explorer-context.md` in `task_dir` |
| Phase 0 | quick-fix | Classification + module list | `explorer-context.md` in `task_dir` |
| Phase 1 | Phase 2 | SPEC document | Markdown, MADR 4.0 |
| Phase 2 | Phase 3a | SPEC + Technical Design + Task Breakdown JSON | Markdown + JSON |
| Phase 3a | Phase 3b | Test modules (.bsl) — failing tests | `.bsl` files |
| Phase 3b | Phase 4 | BSL modules + all tests green | `.bsl` files |
| Phase 4 | user | Entire artifact set | Folder/task bundle |

### Required fields in artifacts

- **Specification:** Context, Requirements (RFC 2119), Scope, Test Plan, Acceptance Criteria
- **Technical Design:** Components, interfaces, separation of responsibilities (user/agent)
- **Task Breakdown JSON:** Separate `.json` file in the "template + example" format (no JSON Schema); required fields: task identifiers (`task_id`), task types (`task_type`), dependencies (`depends_on`), references to spec sections (`spec_refs`), completion criteria
- **Code:** File path, compliance with coding standards
- **Tests:** Link to MUST scenarios from the spec

### Transfer channels

- **Within a single session:** Transfer via the orchestrator context
- **Between sessions:** Store in `.tasks/task-[name]/` or in project configuration

---

## Error handling

### Review blocks (BLOCK)

| Situation | Action |
|-----------|--------|
| Reviewer applied BLOCK | Artifact author fixes according to comments |
| Iteration ≤ 3 | Resubmit for review |
| Iteration > 3 | **Escalation to user.** Work stops. The user decides: fix manually, cancel, or remove BLOCK with justification |

### User rejection

| Rejection point | Action |
|-----------------|--------|
| Phase 2 (architecture) | Architect reworks the design considering user comments. Return to architecture review. |
| Any phase | User can request rollback to the previous phase. Context is preserved. |

### Tests failed

| Situation | Action |
|-----------|--------|
| `run_tests` returned failures in Phase 3 | **Developer-Code** analyzes the cause. If the issue is in the code → fix the implementation and rerun the targeted Phase 3a tests. If the issue is believed to be in the test → set `test_failure` + `suspected_test_error` and finish. The orchestrator launches Reviewer arbitration and, based on the summary, decides where to route: Developer-Tests or Developer-Code. |
| `run_tests` returned failures in Phase 4 | **Tester** identifies the cause: incorrect test → fix the test; implementation bug → **return to Developer** with the issue described (Tester does NOT fix implementation code). |
| Failure during `check_syntax` | Developer fixes the syntax. Mandatory check before review. |
| Tests do not cover MUST items from spec | Reviewer issues BLOCK per test checklist. Tester adds the missing tests. |

### Capability unavailable

| Situation | Action |
|-----------|--------|
| MCP tool unavailable | Agent records the reason for skipping (see mandatory-tools escape hatch). Continues with limitations or escalates to the user. |

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
  - framework/subagents/developer-tests.md
  - framework/subagents/developer-code.md
  - framework/subagents/tester.md
  - framework/subagents/reviewer.md
  - framework/rules/cross-review-policy.md
  - framework/rules/tdd-policy.md
  - framework/rules/mandatory-tools.md
---
