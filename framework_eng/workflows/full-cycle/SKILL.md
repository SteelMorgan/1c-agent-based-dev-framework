---
name: full-cycle
description: "For medium and complex tasks, run the full cycle with review"
---

# Workflow: Full Development Cycle (Full Cycle)

> A deterministic workflow with cross-review at every phase. For medium and high complexity tasks.

> **Placement in the inheritance tree (Layer 3, read-on-choice).** This is detailed phase mechanics. The discipline
> of orchestration and the phase form are already durable in the **orchestrator profile** (`framework/subagents/orchestrator.md`,
> Layer 2). The orchestrator does NOT "load this document as a rule" - it brings up the phase mechanics
> from here **upon entering the phase**, from its profile. Starting the full cycle is a Lead-layer decision
> (classification "medium/complex"), not loading an external document into an arbitrary session.

## Phases

### Phase 0: Classification (Explorer → Economy)

Explorer studies the codebase → modules, call graphs, dependencies. The orchestrator classifies (Lead layer of the profile): Simple → short cycle (skill `quick-fix`); Medium/Complex → Phase 1.

Explorer artifacts are passed to Phase 1 and Phase 2 as context.

### Phase 1: Analysis (Analyst → Mid/High)

Input: task + `explorer-context.md`. Analyst creates a MADR 4.0 + RFC 2119 spec. Reviewer review (Premium). Max. 3 BLOCK iterations. Review + cross-provider-review + **STOP: wait for user OK**.

In the Test Plan, Analyst MUST break requirements down by runtime layers and assign a mandatory check
type: server logic/server context → YaxUnit; UI/client context → scenario
UI/BDD test; linked user process → end-to-end process scenario; integration/background
jobs → integration/job check. For existing coverage, the plan must explicitly say which test
is updated and rerun; if there is no coverage, which test is created.

The Phase 1 approval gate is needed because the specification fixes business decisions (RFC 2119 levels, scope boundaries, choice between alternatives) that the user MUST confirm BEFORE the Architect spends resources on a design based on a possibly incorrect contract. Skipping this gate has historically led to multiple iterations: cross-provider-review or the Architect found contradictions in the spec that could be resolved with a single clarification from the user at this stage.

### Phase 2: Architecture (Architect → High/Premium)

Input: approved spec + `explorer-context.md`. Architect → `technical-design.md` + `task-breakdown.json`. Review + **STOP: wait for user OK**.

### Phase 3: 3a ∥ 3b → 3c → 3d

Phases 3a and 3b run in PARALLEL: they share the same input (spec + `technical-design.md` + `task-breakdown.json`) and do not read each other's artifacts. Each goes through its own review (and advisory cross-provider-review) independently. 3c starts after 3a is accepted (requires `.feature`), 3d starts after BOTH 3b AND 3c are accepted.

- **3a (Scenario-Author → Mid):** before writing new UI/form scenarios, researches the form through the Vanessa MCP workflow (`vanessa-authoring`: run VA manager -> `connect_test_client` -> VA-tools -> `close_test_client`) and records exact commands/elements/required fields in their context. Then spec intent scenarios -> `.feature` Vanessa with `# unknown_step_candidate` markers for steps that were not found. Review (scope=bdd).
- **3b (Developer-Tests → Mid/High):** MUST scenarios from the Test Plan that relate to server logic/server context → YaxUnit unit/integration tests (Red). If a server method was changed and a test already exists, update and rerun it; if there is no test, create one. Review (scope=tests).
- **3c (Scenario-Coder → Mid):** makes the `.feature` from 3a executable - selects/implements Vanessa steps (`@exportscenarios` or, as an escape hatch, BSL steps in `vanessa-tests/support/`), replaces `unknown_step_candidate`. If a step depends on real UI state, checks it through the Vanessa MCP workflow and closes the test client after the check. Red gate: `v8-runner test va` on the task scenarios shows failure due to missing production logic, not unknown steps. Review (scope=bdd-steps).
- **3d (Developer-Code → High):** input - everything from Phase 2 + tests from 3b + Red-executable `.feature` from 3a/3c. Writes code (Green for unit tests from Phase 3b AND scenarios from 3a). With `test_failure` + `suspected_test_error` → Reviewer arbitration → routing (to 3b if it's a unit test, to 3c if it's a step, otherwise to 3d).

**Why 3a and 3c are separated.** Scenario-Author is responsible for **what** should happen (business intent, readable Gherkin). Scenario-Coder is responsible for **how** this is expressed in Vanessa steps (technical implementation of the step library, reuse). Previously nobody did this explicitly - steps either stayed `TODO`, or were finished by Developer-Code with a blurred Green gate. The separation of roles provides: (a) a clean Red gate at the scenario level before writing production code, (b) ownership of quality and reuse in the step library, (c) the ability to parameterize steps by domain functionality rather than by task.

**Place of the vendor Vanessa MCP workflow.** The exploratory MCP workflow does not replace Red/Green gates and is not a separate phase of full-cycle. It is a mandatory technique inside 3a/3c for UI/form scenarios: first obtain a runtime map of the form and reference data through live VA-tools, then write or fix Gherkin. For visual artifacts, `va-visual-check` is applied: VA MCP is the preferred route, browser/web fallback is allowed only after recording the completed VA steps, the reasons, and the residual risk.

### Phase 4: Coverage and Regression (Tester → Mid/High)

Tester runs all tests, adds edge cases, integration, and regression tests. Before closing
Phase 4, it checks the coverage matrix from the Test Plan: each server/server-context MUST be covered
by YaxUnit, each UI/client-context MUST be covered by a scenario UI/BDD test, and each linked user process
must be covered by an end-to-end scenario. Review (High). Phase 4 does NOT duplicate Phase 3.

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

**Required fields:** Specification - Context, Requirements, Scope, Test Plan. Technical Design - components, interfaces. Task Breakdown JSON - task_id, task_type, depends_on, spec_refs, completion criteria. Code - coding standards. Tests - link to MUST scenarios.

---

## Error Handling

| Situation | Action |
|----------|----------|
| BLOCK, <= 3 iterations | Return to the author |
| BLOCK, > 3 | Escalate to the user |
| User rejected Phase 1 | Analyst revises |
| User rejected Phase 2 | Architect revises |
| `test_failure` in Phase 3d | Developer-Code: if own code → fix; if unit test → `suspected_test_error` → Reviewer arbitration → 3b; if Vanessa step → `suspected_step_error` → Reviewer arbitration → 3c |
| A step in Phase 3c requires an API outside `technical-design.md` | Scenario-Coder: `clarification_needed` → Architect (Phase 2) further defines the contract |
| Phase 3c scenario is green before production code | Sign of a mock in the step → Scenario-Coder removes the mock, restarts the Red gate |
| `test_failure` in Phase 4 | Tester: if it's their own test → fix; if it's a code bug → `implementation_error` → Developer |
| `check_syntax` failure | Developer fixes before review |
| MCP/VA unavailable for a UI task | Apply fallback rules `va-visual-check`; if the fallback does not provide sufficient signal, blocker → escalation |

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
