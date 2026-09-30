---
name: full-cycle
description: "For medium and complex tasks, run the full cycle with review"
---

# Workflow: Full Cycle Development (Full Cycle)

> A deterministic workflow with cross-review at every phase. For medium and high-complexity tasks.

> **Place in the retiring (Layer 3, read-on-choice).** This is detailed phase mechanics. The orchestration discipline
> and phase shape are already durable in the **orchestrator profile** (`framework/subagents/orchestrator.md`,
> Layer 2). The orchestrator does NOT "load this document as a rule" - it raises the phase mechanics
> from here **when entering the phase**, from its profile. Launching the full cycle is a Lead-layer decision
> (classification "medium/complex"), not loading an external document into an arbitrary session.

## Phases

### Phase 0: Classification (Explorer → Luna Max)

Explorer investigates the codebase → modules, call graphs, dependencies. The orchestrator classifies (Lead layer of the profile): Simple → short cycle (skill `quick-fix`); Medium/Complex → Phase 1.

Explorer artifacts are passed into Phase 1 and Phase 2 as context.

### Phase 1: Analysis (Analyst → Opus 5 High / Sol High)

Input: task + `explorer-context.md`. Analyst-controller first executes the primary branch through
Consilium (`domain=requirements`) or records `CONSILIUM_NOT_APPLICABLE`, then Analyst
materializes the spec in MADR 4.0 + RFC 2119. No repeated architectural deliberation is launched:
Reviewer-controller performs one scope-aware `review-swarm --gate acceptance` over the durable verdict,
the specification, and MUST-traceability. Max. 3 BLOCK iterations. After the gate - **STOP: wait for user OK**.

In the Test Plan, Analyst MUST distribute requirements across runtime layers and assign the mandatory test
type: server logic/server context → YaxUnit; UI/client context → scenario
UI/BDD test; related user process → end-to-end process scenario; integration/background
jobs → integration/job check. For existing coverage, the plan must explicitly state which test
is updated and rerun; if there is no coverage, which test is created.

The approval gate in Phase 1 is needed because the specification captures business decisions (RFC 2119 levels, scope boundaries, choice between alternatives) that the user MUST confirm BEFORE Architect spends resources on a design based on a possibly incorrect contract. Skipping this gate has historically led to multiple iterations: cross-provider-review or Architect found contradictions in the spec that could have been removed with a single clarification from the user at this stage.

### Phase 2: Architecture (Architect → Opus 5 High / Sol High)

Input: approved spec + `explorer-context.md`. The Architect-controller handles material decision forks
through Consilium or records `CONSILIUM_NOT_APPLICABLE`, then Architect creates
`technical-design.md` + `task-breakdown.json`. One scope-aware acceptance gate checks faithful
materialization and design→spec traceability; the decision is not discussed again. After the gate —
**STOP: waiting for user OK**.

### Phase 3: 3a ∥ 3b → 3c → 3d

Phases 3a and 3b start in PARALLEL: they share the same input (the spec + `technical-design.md` + `task-breakdown.json`) and do not read each other's artifacts. Each passes its own scope-aware acceptance gate independently. 3c starts after 3a is accepted (it needs `.feature`), and 3d starts after BOTH 3b AND 3c are accepted.

- **3a (Scenario-Author → Opus 5 Medium / Sol Medium):** before writing new UI/form scenarios, performs form research through the Vanessa MCP workflow (`vanessa-authoring`: start VA manager → `connect_test_client` → VA-tools → `close_test_client`) and records exact commands/elements/required fields in its own context. Then intent scenarios from the spec → Vanessa `.feature` with the `# unknown_step_candidate` marker for steps not found. Review (scope=bdd).
- **3b (Developer-Tests → Luna Max / Opus 5 Medium):** MUST scenarios from the Test Plan that relate to server logic/server context → YaxUnit unit/integration tests (Red). If a server method changed and a test already exists, updates and reruns it; if there is no test, creates one. Review (scope=tests).
- **3c (Scenario-Coder → Luna Max / Opus 5 Medium):** makes 3a `.feature` files executable — selects/implements Vanessa steps (`@exportscenarios` or, as an escape hatch, BSL steps in `vanessa-tests/support/`), replaces `unknown_step_candidate`. If a step depends on real UI state, verifies it through the Vanessa MCP workflow and closes the test client after verification. Red gate: `v8-runner test va` on the task scenarios shows failure on missing production logic, not on unknown steps. Review (scope=bdd-steps).
- **3d (Developer-Code → Luna Max / Opus 5 Medium):** input — everything from Phase 2 + 3b tests + Red-executable `.feature` from 3a/3c. Writes code (Green for Phase 3b unit tests AND 3a scenarios). On `test_failure` + `suspected_test_error` → Reviewer arbitration → routing (to 3b if it is a unit test, to 3c if it is a step, otherwise to 3d).

**Why 3a and 3c are separated.** Scenario-Author is responsible for **what** should happen (business intent, readable Gherkin). Scenario-Coder is responsible for **how** this is expressed in Vanessa steps (technical implementation, step-library reuse). Previously nobody explicitly did this work - steps either stayed `TODO` or were finished by Developer-Code with a blurred Green gate. Separating the roles gives: (a) a clean Red gate at the scenario level before production code is written, (b) an owner for step-library quality and reuse, (c) the ability to parameterize steps by domain functionality rather than by task.

**Position of vendor workflow Vanessa MCP.** The exploratory MCP workflow does not replace Red/Green gates and is not a separate phase of full-cycle. It is a mandatory technique inside 3a/3c for UI/form scenarios: first obtain the form runtime map and reference data through live VA-tools, then write or fix Gherkin. For visual artifacts, `va-visual-check` is used: VA MCP is the preferred route, browser/web fallback is allowed after recording the completed VA steps, the reasons, and the residual risk.

### Phase 4: Coverage and Regression (Tester → Opus 5 Medium / Sol Medium)

Tester runs all tests, adds edge cases, integration tests, and regression tests. Before closing
Phase 4, it checks the coverage matrix from the Test Plan: every server/server-context MUST be covered
by YaxUnit, every UI/client-context MUST be covered by a scenario UI/BDD test, every related process by
an end-to-end scenario. Scope-aware acceptance gate Reviewer-controller with floor
of the actual author. Phase 4 does NOT duplicate Phase 3.

### Model and Environment Routing

The exact model/effort is selected according to the canonical `agent-development-ext` matrix. The values are defaults;
the orchestrator may change the effort with rationale, without violating the capability/reviewer floor. With
`HERDR_ENV=1`, the same-family phase owner launches a native child, cross-family - a full Herdr
agent task. Outside Herdr, only qualified routes for the current harness family are allowed. The common emergency
fallback Kimi K3 applies only after role-specific qualification and is not a silent fallback.

---

## Artifact Handoff

| From → To | Artifact |
|--------|----------|
| 0 → 1, 2 | `explorer-context.md` |
| 1 → 2 | `spec.md` |
| 2 → 3a, 3b | spec + technical-design + task-breakdown.json |
| 3a → 3c | `.feature` (intent) with `unknown_step_candidate` |
| 3b → 3d | test modules (.bsl) |
| 3c → 3d | `.feature` with implemented steps + new `@exportscenarios` / BSL steps in `vanessa-tests/support/` |
| 3d → 4 | BSL + `.feature` + green unit and scenario tests |

**Required fields:** Specification - Context, Requirements, Scope, Test Plan. Technical Design - components, interfaces. Task Breakdown JSON - task_id, task_type, depends_on, spec_refs, completion criteria. Code - coding-standards. Tests - linkage to MUST scenarios.

---

## Error Handling

| Situation | Action |
|----------|----------|
| BLOCK, <= 3 iterations | Return to author |
| BLOCK, > 3 | Escalate to user |
| User rejected Phase 1 | Analyst revises |
| User rejected Phase 2 | Architect revises |
| `test_failure` in Phase 3d | Developer-Code: if own code -> fix; if unit test -> `suspected_test_error` -> Reviewer arbitration -> 3b; if Vanessa step -> `suspected_step_error` -> Reviewer arbitration -> 3c |
| A step in Phase 3c requires an API outside `technical-design.md` | Scenario-Coder: `clarification_needed` -> Architect (Phase 2) further defines the contract |
| Phase 3c scenario is green before prod code | Mock marker in the step -> Scenario-Coder removes the mock, restarts the Red gate |
| `test_failure` in Phase 4 | Tester: own test -> fix; bug in code -> `implementation_error` -> Developer |
| `check_syntax` failure | Developer fixes before review |
| MCP/VA unavailable for a UI task | Apply fallback rules from `va-visual-check`; if the fallback does not provide a sufficient signal - blocker -> escalation |

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
  - framework/rules/herdr-agent-routing/SKILL.md
  - framework/skills/framework-meta/agent-development-ext/SKILL.md
---
