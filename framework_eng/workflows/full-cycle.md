---
name: full-cycle
description: Full development cycle with mandatory cross-review at every phase.
---


# Workflow: Full Cycle Development (Full Cycle)

> **DETERMINISTIC** workflow. Cross-review at every step. Used for medium- and high-complexity tasks.

---

## Purpose

Full cycle development is a tightly structured process with mandatory artifact verification at every phase. Each output of one agent is reviewed by the next agent (reviewer) following a checklist. Ensures quality and predictability of the result.

---

## Process diagram

```
  ┌──────────────────┐
  │ User task        │
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
     │       ║  Phase 3a: Tests                     ║
     │       ║  Developer-Tests (High) ──► Reviewer  ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 3b: Implementation            ║
     │       ║  Developer-Code (High) ──► Reviewer   ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ║  test_failure → Reviewer → root cause ║
     │       ║  → returns to the appropriate agent  ║
     │       ╚══════════════════╤════════════════════╝
     │                          ▼
     │       ╔═══════════════════════════════════════╗
     │       ║  Phase 4: Coverage and regression     ║
     │       ║  Tester (Mid/High) ──► Reviewer (H)   ║
     │       ║       ▲ BLOCK ◄──────────┘            ║
     │       ╚══════════════════╤════════════════════╝
     │                          │
     └──────────┬───────────────┘
                ▼
  ┌──────────────────────┐
  │ Result               │
  │ to the user          │
  └──────────────────────┘

  Legend: (P) = Premium tier, (H) = High tier
           BLOCK = return to author, max 3 iterations
```

---

## Workflow phases

### Phase 0: Classification (Explorer → Economy)

| Element | Description |
|---------|-------------|
| **Input** | User task |
| **Action** | Explorer investigates the codebase: identifies affected modules, builds call graphs (incoming + outgoing), uncovers transitive dependencies |
| **Output** | List of affected modules + call graphs + factual data (dependency depth, number of call sites) |
| **Routing** | The orchestrator classifies the task using Explorer data: Simple → `quick-fix.md`; Medium/Complex → Phase 1 |

> Explorer artifacts (`task_dir/.context/explorer-context.md`) are passed by the orchestrator to both Phase 1 (Analyst) and Phase 2 (Architect) as context for the affected modules and dependencies.

**Tools:** `navigate_symbol`, `get_call_graph`, `list_metadata_objects`, `get_metadata_structure`, `get_diagnostics`

---

### Phase 1: Analysis and specification (Analyst → Mid/High)

| Element | Description |
|---------|-------------|
| **Input** | Task + `task_dir/.context/explorer-context.md` (list of affected modules, call graphs) |
| **Action** | Analyst creates a specification in MADR 4.0 format + RFC 2119 |
| **Output** | `task_dir/.spec/spec.md` (SPEC document) |
| **Review** | Reviewer (Premium) checks the specification against the specification checklist |
| **Iterations** | Maximum 3, then escalate to the user |

**Review checklist:** [cross-review-policy.md](../rules/cross-review-policy.md) → Specification checklist

---

### Phase 2: Architecture (Architect → High/Premium)

| Element | Description |
|---------|-------------|
| **Input** | Approved `task_dir/.spec/spec.md` + `task_dir/.context/explorer-context.md` (call graphs, dependencies of the affected modules) |
| **Action** | Architect designs the solution (Technical Design) and decomposes the specification into a Task Breakdown JSON (tasks, dependencies, task types, links to specification sections) |
| **Output** | `task_dir/.spec/technical-design.md` + `task_dir/.context/task-breakdown.json` + link/summary of the JSON in `task_dir/.spec/spec.md` |
| **Review** | Reviewer (Premium) checks the architecture and Task Breakdown JSON against checklists |
| **STOP** | Awaiting explicit confirmation from the user before Phase 3 |

**Important:** The phase is blocked until the user explicitly approves. This is the control point for architectural decisions.

---

### Phase 3a: Writing tests (Developer-Tests → High)

| Element | Description |
|---------|-------------|
| **Input** | Approved `task_dir/.spec/spec.md` + Test Plan + `task_dir` |
| **Action** | Developer-Tests writes YaxUnit unit tests for ALL MUST scenarios from the Test Plan — strictly according to the spec, without implementation. Tests MUST fail (Red). |
| **Output** | Test modules (.bsl) — tests fail (implementation is not yet present) |
| **Review** | Reviewer (Premium) checks the tests: coverage completeness for MUST scenarios, correctness of assertions |

> Developer-Tests DOES NOT see or influence the implementation. Conflict of interest is excluded.

---

### Phase 3b: Implementation (Developer-Code → High)

| Element | Description |
|---------|-------------|
| **Input** | `task_dir/.spec/spec.md` + `task_dir/.spec/technical-design.md` + `task_dir/.context/task-breakdown.json` + test modules from Phase 3a + `task_dir` |
| **Action** | Developer-Code writes BSL code so that the tests from Phase 3a pass (Green). Does NOT write or modify tests. Runs only Phase 3a tests (targeted run), not the full regression suite. |
| **Output** | BSL modules + XML metadata — all Phase 3a tests pass |
| **Review** | Reviewer (Premium) checks the code against the BSL checklist |
| **test_failure** | If the tests fail → Developer-Code notifies the orchestrator with the `test_failure` tag. If the `suspected_test_error` flag is set, the orchestrator initiates Reviewer arbitration: compare spec + technical design + tests + code and determine which artifact is incorrect (tests or code). Based on the Reviewer summary, the orchestrator routes: to Developer-Tests or back to Developer-Code. |

**The TDD rule is enforced by the orchestrator:** Phase 3a ALWAYS precedes Phase 3b. See [tdd-policy.md](../rules/tdd-policy.md)

---

### Phase 4: Coverage and regression (Tester → Mid/High)

| Element | Description |
|---------|-------------|
| **Input** | Code + tests from Phase 3 + test plan from `task_dir/.spec/spec.md` |
| **Action** | Tester verifies coverage of the test plan, adds missing tests (edge cases, integration, regression), runs the full run |
| **Output** | Full set of tests (unit + regression) + run results + `task_dir/.spec/test-report.md` |
| **Review** | Reviewer (High) checks the tests against the testing checklist |

**Important:** Phase 4 DOES NOT duplicate Phase 3. Developer writes unit tests following TDD. Tester supplements coverage: edge cases, negative scenarios, integration tests, regression.

**Tools:** `run_tests`, `check_syntax`, `get_diagnostics`

---

## Artifact handover between phases

### Handover rules

| From phase | To phase | Artifact | Format |
|------------|----------|----------|--------|
| Phase 0 | Phase 1 | List of affected modules + call graphs (incoming/outgoing) + dependency depth | `task_dir/.context/explorer-context.md` |
| Phase 0 | Phase 2 | Same Explorer artifacts — orchestrator forwards them again | `task_dir/.context/explorer-context.md` |
| Phase 0 | quick-fix | Classification + list of modules | `task_dir/.context/explorer-context.md` |
| Phase 1 | Phase 2 | `task_dir/.spec/spec.md` | Markdown, MADR 4.0 |
| Phase 2 | Phase 3a | `task_dir/.spec/spec.md` + `task_dir/.spec/technical-design.md` + `task_dir/.context/task-breakdown.json` | Markdown + JSON |
| Phase 3a | Phase 3b | Test modules (.bsl) — failing tests | .bsl files |
| Phase 3b | Phase 4 | BSL modules + all tests green | .bsl files |
| Phase 4 | User | Entire set of artifacts | Folder/task bundle |

### Mandatory fields in artifacts

- **Specification:** Context, Requirements (RFC 2119), Scope, Test Plan, Acceptance Criteria
- **Technical Design (`task_dir/.spec/technical-design.md`):** Components, interfaces, division of responsibilities (user/agent)
- **Task Breakdown JSON (`task_dir/.context/task-breakdown.json`):** A separate `.json` file in the "template + example" format (without JSON Schema); required fields: task identifiers (`task_id`), task types (`task_type`), dependencies (`depends_on`), links to specification sections (`spec_refs`), completion criteria
- **Code:** File path, compliance with coding standards
- **Tests:** Link to MUST scenarios from `task_dir/.spec/spec.md`

### Handover channels

- **Within a single session:** Transfer through the orchestrator context
