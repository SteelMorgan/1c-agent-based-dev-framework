---
name: full-cycle
description: Full development cycle with mandatory cross-review at every phase.
---

# Workflow: Full Development Cycle (Full Cycle)

> A deterministic workflow with cross-review at every phase. For medium and high complexity tasks.

## Phases

### Phase 0: Classification (Explorer → Economy)

Explorer examines the codebase → modules, call graphs, dependencies. The orchestrator classifies: Simple → quick-fix; Medium/Complex → Phase 1.

Explorer artifacts are passed to Phase 1 and Phase 2 as context.

### Phase 1: Analysis (Analyst → Mid/High)

Input: task + `explorer-context.md`. Analyst creates a MADR 4.0 + RFC 2119 spec. Reviewer review (Premium). Max. 3 BLOCK iterations. Review + cross-provider-review + **STOP: wait for user OK**.

Phase 1 approval gate is needed because the specification fixes business decisions (RFC 2119 levels, scope boundaries, choice between alternatives), which the user MUST confirm BEFORE Architect spends resources on a design based on a potentially incorrect contract. Skipping this gate has historically led to multiple iterations: cross-provider-review or Architect would find contradictions in the spec that could have been resolved with a single clarification from the user at this stage.

### Phase 2: Architecture (Architect → High/Premium)

Input: approved spec + `explorer-context.md`. Architect → `technical-design.md` + `task-breakdown.json`. Review + **STOP: wait for user OK**.

### Phase 3: SEQUENTIAL (3a → 3b → 3c → 3d)

Phases 3a–3d proceed strictly sequentially. Each next phase starts only after the previous one has been reviewed (and cross-provider-review in advisory mode).

- **3a (Scenario-Author → Mid):** intent scenarios from the spec → `.feature` Vanessa with `# unknown_step_candidate` marking for steps not found. Review (scope=bdd).
- **3b (Developer-Tests → Mid/High):** MUST scenarios from the Test Plan → unit/integration tests (Red). Review (scope=tests).
- **3c (Scenario-Coder → Mid):** makes `.feature` from 3a executable — selects/implements Vanessa steps (`@exportscenarios` or, as an escape hatch, BSL steps in `vanessa-tests/support/`), replaces `unknown_step_candidate`. Red gate: `v8-runner test va` on the task scenarios shows failure on missing production logic, not on unknown steps. Review (scope=bdd-steps).
- **3d (Developer-Code → High):** input is everything from Phase 2 + tests from 3b + Red-executable `.feature` from 3a/3c. Writes code (Green for unit tests from Phase 3b AND scenarios from 3a). On `test_failure` + `suspected_test_error` → Reviewer arbitration → routing (to 3b if it is a unit test, to 3c if it is a step, otherwise to 3d).

**Why 3a and 3c are split.** Scenario-Author is responsible for **what** should happen (business intent, readable Gherkin). Scenario-Coder is responsible for **how** this is expressed in Vanessa steps (technical implementation of the step library, reuse). Previously nobody explicitly handled this work - steps either stayed `TODO`, or Developer-Code finished them while blurring the Green gate. Separating the roles gives: (a) a clean Red gate at the scenario level before writing production code, (b) an owner for step library quality and reuse, (c) the ability to parameterize steps by domain functionality rather than by task.

### Phase 4: Coverage and Regression (Tester → Mid/High)

Tester runs all tests, adds edge cases, integration tests, regression tests. Review (High). Phase 4 does NOT duplicate Phase 3.

---

## Artifact Handover

| From → To | Artifact |
|--------|----------|
| 0 → 1, 2 | `explorer-context.md` |
| 1 → 2 | `spec.md` |
| 2 → 3a, 3b | spec + technical-design + task-breakdown.json |
| 3a → 3c | `.feature` (intent) with `unknown_step_candidate` |
| 3b → 3d | test modules (.bsl) |
| 3c → 3d | `.feature` with implemented steps + new `@exportscenarios` / BSL steps in `vanessa-tests/support/` |
| 3d → 4 | BSL + `.feature` + green unit and scenario tests |

**Mandatory fields:** Specification — Context, Requirements, Scope, Test Plan. Technical Design — components, interfaces. Task Breakdown JSON — task_id, task_type, depends_on, spec_refs, completion criteria. Code — coding-standards. Tests — linkage to MUST scenarios.

---

## Error Handling

| Situation | Action |
|----------|----------|
| BLOCK, <= 3 iterations | Return to author |
| BLOCK, > 3 | Escalate to user |
| User rejected Phase 1 | Analyst reworks |
| User rejected Phase 2 | Architect reworks |
| `test_failure` in Phase 3d | Developer-Code: if own code → fix; if unit test → `suspected_test_error` → Reviewer arbitration → 3b; if Vanessa step → `suspected_step_error` → Reviewer arbitration → 3c |
| Step in Phase 3c requires API outside `technical-design.md` | Scenario-Coder: `clarification_needed` → Architect (Phase 2) further defines the contract |
| Phase 3c scenario is green before production code | Indicator of a mock in the step → Scenario-Coder removes the mock, reruns the Red gate |
| `test_failure` in Phase 4 | Tester: own test → fix; bug in code → `implementation_error` → Developer |
| `check_syntax` failure | Developer fixes before review |
| MCP unavailable | Escape hatch → escalation |

---
depends_on:
  - framework/workflows/quick-fix.md
  - framework/subagents/explorer.md
  - framework/subagents/analyst.md
  - framework/subagents/architect.md
  - framework/subagents/scenario-author.md
  - framework/subagents/developer-tests.md
  - framework/subagents/scenario-coder.md
  - framework/subagents/developer-code.md
  - framework/subagents/tester.md
  - framework/subagents/reviewer.md
  - framework/workflows/source-of-truth-policy.md
  - framework/rules/tdd-policy.md
---
