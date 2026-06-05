---
name: source-of-truth
description: MUST use BEFORE making a judgment about the cause of a conflict, a test failure, or an artifact dispute. Defines the end-to-end verification method L1→L6 and the classification of the first broken link.
alwaysApply: false
---

# Source of Truth Policy (Source of Truth) — method

> In full-cycle, artifacts form a hierarchy of sources of truth. A lower level refines the upper level, but does NOT override it. In a conflict, priority is always with the higher level. Verification is performed along the entire chain from top to bottom until the first broken link is found. This skill is a detailed procedure; the invariant-trigger "conflict → check the chain from top to bottom" is fixed in the rule `framework/rules/source-of-truth.md`.

## Hierarchy of sources of truth

| Level | Artifact | Purpose |
|---------|----------|------------|
| 1 | Business goal of the task + explicit user answers | Define intent and the acceptable result |
| 2 | Approved specification + acceptance criteria | Fix the task contract |
| 3 | Technical design + task-breakdown.json | Define the implementation approach and decomposition |
| 4 | BDD / acceptance scenarios | Formalize observable behavior |
| 5 | Unit / integration tests | Verify behavior at the executable level |
| 6 | Code | Implements behavior |

If a particular task is missing a level according to the process rules, the source of truth is the nearest approved level above the current one.

## MUST

- Each lower level MUST be traceable to the upper level
- The lower level MUST NOT silently override behavior fixed above
- In a conflict between lower and upper levels, the agent MUST treat the lower level as suspicious until it checks the chain
- A level 1 user answer has the highest priority, but after such a change the downstream artifacts MUST be updated and revalidated
- A visible error at a lower level does NOT prove that the cause is there as well
- The first level that is no longer justified by the upper level is considered the root cause
- If the root cause is outside the agent's responsibility, the agent MUST stop, record the conclusion in context, and return control to the orchestrator

## End-to-End Chain Verification

For any conflict, test failure, behavior mismatch, or dispute between artifacts, the agent MUST check the chain from top to bottom:

1. The business goal and user answers are clear, current, and do not contradict each other
2. The specification correctly reflects the business goal
3. Technical design and task breakdown correctly reflect the specification
4. BDD / acceptance scenarios correctly reflect the specification and design
5. Unit / integration tests correctly reflect the scenarios and the specification
6. Code correctly reflects the design, tests, and specification

It is forbidden to skip levels and make a binary conclusion like "the test is at fault" or "the code is at fault" without checking the upper levels.

## Classification of the First Broken Link

| Where the discrepancy is first found | Classification |
|------------------------------------|---------------|
| Levels 1-2 | Requirements / specification issue |
| Level 3 | Technical design / decomposition issue |
| Level 4 | Acceptance / BDD scenarios issue |
| Level 5 | Test issue |
| Level 6 | Implementation issue |

You must record not only the symptom, but also the first broken link in the chain.

## What This Rule Adds

- Defines which artifact is authoritative in a conflict
- Establishes a unified diagnostic algorithm for ALL agents
- Separates two questions: "what is true?" and "who has the authority to change it?"

## Principle, Boundaries, and Arbitration

- This rule is a general principle for all agents: the order of artifact priority and the way to find the root cause
- Sub-agent prompts define responsibility boundaries: who CAN and CANNOT change what
- The orchestrator performs arbitration and routing: to whom to return the task after the first broken link is found

In other words: this rule defines the source of truth, agent prompts define the right to change, and the orchestrator defines the return route.

## Consequences for Roles

- Analyst works from level 1 to level 2 and does not substitute requirements with design
- Architect works from level 2 to level 3 and does not change the task contract
- Scenario-Author formalizes level 4 from levels 2-3 and does not extend behavior beyond them
- Developer-Tests writes level 5 based on levels 2-4 and does not fit tests to the implementation
- Developer-Code implements level 6 based on levels 2-5 and does not fix tests or scenarios
- Tester checks levels 4-6 against levels 1-3, but fixes only test artifacts
- Reviewer can analyze any level of the chain, but does not modify artifacts
- Orchestrator routes the task by the first broken link, not by a single symptom

## Typical Uses

- A unit test failed: this is NOT automatically a bug in the code. First check that the test follows the specification and scenarios
- Tester sees `implementation_error` only after checking that the test and the expected behavior are consistent with the upper levels
- Developer-Code suspects an error in the test: does NOT fix the test, but records the conflict and stops
- A conflict between code and technical design: the code must be brought into alignment with the design, or the design must be officially updated through the process

---
depends_on:
  - framework/rules/sdd-policy.md
  - framework/rules/tdd-policy.md
---
