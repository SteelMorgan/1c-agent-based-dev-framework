---
name: source-of-truth-policy
description: Hierarchy of artifacts and the end-to-end Source of Truth verification rule in full-cycle.
---

# Source of Truth Policy (Source of Truth)

> In full-cycle the artifacts form a hierarchy of sources of truth. The lower level refines the higher one but does NOT override it. In a conflict the higher level always takes priority. Verification is performed down the chain from the top until the first broken link is found.

## Hierarchy of sources of truth

| Level | Artifact | Purpose |
|-------|----------|---------|
| 1 | Business objective of the task + explicit user responses | Define the intent and the acceptable outcome |
| 2 | Approved specification + acceptance criteria | Fix the task contract |
| 3 | Technical design + task-breakdown.json | Define the implementation approach and decomposition |
| 4 | BDD / acceptance scenarios | Formalize the observable behavior |
| 5 | Unit / integration tests | Verify the behavior at an executable level |
| 6 | Code | Implements the behavior |

If a particular task lacks a level according to process rules, the source of truth is the nearest approved level above the missing one.

## MUST

- Each lower level MUST be traceable to the higher level
- A lower level MUST NOT silently override the behavior defined above
- In a conflict between lower and upper levels, the agent MUST treat the lower level as suspicious until it has checked the chain
- A level 1 user response has the highest priority, but after such a change the downstream artifacts MUST be updated and re-verified
- A visible error at a lower level does NOT prove the cause lies there
- The first level that ceases to be justified by the level above is considered the root cause
- If the root cause lies outside the agent's responsibility, the agent MUST stop, record the conclusion in the context, and return control to the orchestrator

## End-to-end chain verification

In any conflict, test failure, behavior discrepancy, or dispute between artifacts the agent MUST verify the chain from the top down:

1. The business objective and user responses are clear, up to date, and do not contradict one another
2. The specification correctly reflects the business objective
3. The Technical design and task breakdown correctly reflect the specification
4. The BDD / acceptance scenarios correctly reflect the specification and the design
5. The Unit / integration tests correctly reflect the scenarios and the specification
6. The Code correctly reflects the design, tests, and specification

Skipping levels and making a binary conclusion like “the test is to blame” or “the code is to blame” without checking the upper levels is forbidden.

## Classification of the first broken link

| Where the discrepancy is first detected | Classification |
|------------------------------------------|----------------|
| Level 1-2 | Requirements/specification issue |
| Level 3 | Technical design/decomposition issue |
| Level 4 | Acceptance / BDD scenarios issue |
| Level 5 | Tests issue |
| Level 6 | Implementation issue |

Fix not only the symptom but also the first broken link in the chain.

## What this rule adds

- Establishes which artifact is authoritative in a conflict
- Sets a unified diagnostic algorithm for ALL agents
- Separates two questions: “what is true?” and “who has the right to change it?”

## Principle, boundaries, and arbitration

- This rule is a general principle for all agents: the priority order of artifacts and the method for finding the root cause
- Sub-agent prompts define the boundaries of responsibility: who CAN and CANNOT change what
- The orchestrator performs arbitration and routing: who should receive the task after the first broken link is detected

In other words: this rule defines the source of truth, agent prompts define the right to change, and the orchestrator defines the return route.

## Implications for roles

- Analyst works from level 1 to level 2 and does not replace requirements with design
- Architect works from level 2 to level 3 and does not change the task contract
- Scenario-Author formalizes level 4 based on levels 2-3 and does not expand behavior beyond them
- Developer-Tests writes level 5 based on levels 2-4 and does not tailor tests to the implementation
- Developer-Code implements level 6 based on levels 2-5 and does not fix tests or scenarios
- Tester validates levels 4-6 against levels 1-3 but only fixes test artifacts
- Reviewer may analyze any level in the chain but does not modify artifacts
- Orchestrator routes the task based on the first broken link, not on a single symptom

## Typical applications

- A unit test failed: this is NOT automatically a bug in the code. First verify that the test follows the specification and the scenarios
- Tester sees `implementation_error` only after verifying that the test and the expected behavior agree with the upper levels
- Developer-Code suspects a problem in the test: does NOT fix the test, but documents the conflict and stops
- Conflict between the Code and the technical design: the code must be aligned to the design, or the design must be officially updated through the process

---
depends_on:
  - framework/rules/sdd-policy.md
  - framework/rules/tdd-policy.md
---
