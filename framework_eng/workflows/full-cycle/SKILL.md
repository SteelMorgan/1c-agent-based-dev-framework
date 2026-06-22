---
name: full-cycle
description: Full development cycle with mandatory cross-review at every phase.
---

# Workflow: Full Development Cycle (Full Cycle)

> Deterministic workflow with cross-review at every phase. For medium and high complexity tasks.

> **Place in the hierarchy (Layer 3, read-on-choice).** This is detailed phase mechanics. The orchestration discipline
> and the phase form are already durable in the **orchestrator profile** (`framework/subagents/orchestrator.md`,
> Layer 2). The orchestrator does NOT "load this document as a rule" - it raises the phase mechanics
> from here **when entering a phase**, from its own profile. Launching the full cycle is a Lead-layer
> decision (the "medium/high" classification), not loading an external document into an arbitrary session.

## Phases

### Phase 0: Classification (Explorer -> Economy)

Explorer investigates the codebase -> modules, call graphs, dependencies. The orchestrator classifies (Lead-layer of the profile): Simple -> short cycle (skill `quick-fix`); Medium/Complex -> Phase 1.

Explorer artifacts are passed into Phase 1 and Phase 2 as context.

### Phase 1: Analysis (Analyst -> Mid/High)

Input: task + `explorer-context.md`. Analyst creates a MADR 4.0 + RFC 2119 spec. Reviewer review (Premium). Max. 3 BLOCK iterations. Review + cross-provider-review + **STOP: wait for user OK**.

Approval gate Phase 1 is needed because the specification fixes business decisions (RFC 2119 levels, scope boundaries, choice between alternatives), which the user MUST confirm BEFORE Architect spends resources on a design based on a possibly incorrect contract. Skipping this gate has historically led to multiple iterations: cross-provider-review or Architect found contradictions in the spec that could have been eliminated by one clarification from the user at this stage.

### Phase 2: Architecture (Architect -> High/Premium)

Input: approved spec + `explorer-context.md`. Architect -> `technical-design.md` + `task-breakdown.json`. Review + **STOP: wait for user OK**.

### Phase 3: SEQUENTIALLY (3a -> 3b -> 3c -> 3d)

Phases 3a-3d proceed strictly sequentially. Each next phase starts only after review of the previous one (and cross-provider-review in advisory).

- **3a (Scenario-Author -> Mid):** intent scenarios from the spec -> `.feature` Vanessa with `# unknown_step_candidate` for steps not found. Review (scope=bdd).
- **3b (Developer-Tests -> Mid/High):** MUST scenarios from the Test Plan -> unit/integration tests (Red). Review (scope=tests).
- **3c (Scenario-Coder -> Mid):** makes the `.feature` from 3a executable - selects/implements Vanessa steps (`@exportscenarios` or, as an escape hatch, BSL steps in `vanessa-tests/support/`), replaces `unknown_step_candidate`. Red gate: `v8-runner test va` on the task scenarios shows failure on missing production logic, not on unknown steps. Review (scope=bdd-steps).
- **3d (Developer-Code -> High):** input - everything from Phase 2 + tests from 3b + Red-executable `.feature` from 3a/3c. Writes code (Green for Phase 3b unit tests AND 3a scenarios). On `test_failure` + `suspected_test_error` -> Reviewer arbitration -> routing (to 3b if unit test, to 3c if step, otherwise to 3d).

**Why 3a and 3c are separated.** Scenario-Author is responsible for **what** should happen (business intent, readable Gherkin). Scenario-Coder is responsible for **how** this is expressed in Vanessa steps (technical implementation of the step library, reuse). Previously no one explicitly did this - steps either stayed `TODO`, or were finished by Developer-Code, blurring the Green gate. The role split gives: (a) a clean Red gate at the scenario level before any production code is written, (b) an owner for step-library quality and reuse, (c) the ability to parameterize steps by domain functionality, not by task.

### Phase 4: Coverage and Regression (Tester -> Mid/High)

Tester runs all tests, adds edge cases, integration tests, and regression tests. Review (High). Phase 4 does NOT duplicate Phase 3.

---

## Artifact Transfer

| From -> To | Artifact |
|--------|----------|
| 0 -> 1, 2 | `explorer-context.md` |
| 1 -> 2 | `spec.md` |
| 2 -> 3a, 3b | spec + technical-design + task-breakdown.json |
| 3a -> 3c | `.feature` (intent) with `unknown_step_candidate` |
| 3b -> 3d | test modules (.bsl) |
| 3c -> 3d | `.feature` with implemented steps + new `@exportscenarios` / BSL steps in `vanessa-tests/support/` |
| 3d -> 4 | BSL + `.feature` + green unit and scenario tests |

**Required fields:** Specification - Context, Requirements, Scope, Test Plan. Technical Design - components, interfaces. Task Breakdown JSON - task_id, task_type, depends_on, spec_refs, completion criteria. Code - coding standards. Tests - linkage with MUST scenarios.

---

## Error Handling

| Situation | Action |
|----------|--------|
| BLOCK, <= 3 iterations | Return to author |
| BLOCK, > 3 | Escalate to user |
| User rejected Phase 1 | Analyst revises |
| User rejected Phase 2 | Architect revises |
| `test_failure` in Phase 3d | Developer-Code: if own code -> fix it; if unit test -> `suspected_test_error` -> Reviewer arbitration -> 3b; if Vanessa step -> `suspected_step_error` -> Reviewer arbitration -> 3c |
| A step in Phase 3c requires an API outside `technical-design.md` | Scenario-Coder: `clarification_needed` -> Architect (Phase 2) defines the contract further |
| Phase 3c scenario turns green before production code | Sign of a mock in the step -> Scenario-Coder removes the mock, restarts the Red gate |
| `test_failure` in Phase 4 | Tester: if it is their own test -> fix it; if it is a code bug -> `implementation_error` -> Developer |
| `check_syntax` fails | Developer fixes it before review |
| MCP unavailable | Escape hatch -> escalation |

---
depends_on:
  - framework/subagents/orchestrator.md
  - framework/skills/agent-process/quick-fix/SKILL.md
  - framework/subagents/explorer.md
  - framework/subagents/analyst.md
  - framework/subagents/architect.md
  - framework/subagents/scenario-author.md
  - framework/subagents/developer-tests.md
  - framework/subagents/scenario-coder.md
  - framework/subagents/developer-code.md
  - framework/subagents/tester.md
  - framework/subagents/reviewer.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
---
