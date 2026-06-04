---
name: source-of-truth
description: Method of end-to-end verification of the hierarchy of sources of truth (L1→L6) when there is a conflict/test failure/artifact dispute. Classification of the first broken link, implications for roles, typical uses.
alwaysApply: false
---

# Source of Truth Policy (Source of Truth) — method

> In full-cycle, artifacts form a hierarchy of sources of truth. A lower level refines a higher one, but it DOES NOT override it. In a conflict, priority always belongs to the higher level. Verification is performed across the entire chain from top to bottom until the first broken link is found. This skill is a detailed procedure; the invariant trigger “conflict → check the chain from top to bottom” is captured in rule `framework/rules/source-of-truth.md`.

## Hierarchy of Sources of Truth

| Level | Artifact | Purpose |
|-------|----------|---------|
| 1 | Business goal of the task + explicit user answers | Define intent and the acceptable result |
| 2 | Approved specification + acceptance criteria | Fix the task contract |
| 3 | Technical design + task-breakdown.json | Define the implementation approach and decomposition |
| 4 | BDD / acceptance scenarios | Formalize observable behavior |
| 5 | Unit / integration tests | Verify behavior at the executable level |
| 6 | Code | Implements behavior |

If a given task does not have some level according to process rules, the source of truth is the nearest approved level above the current one.

## MUST

- Every lower level MUST be traceable to the upper level
- A lower level MUST NOT silently override behavior fixed above
- In a conflict between a lower and upper level, the agent MUST treat the lower level as suspicious until it checks the chain
- The user answer at level 1 has the highest priority, but after such a change downstream artifacts MUST be updated and re-verified
- A visible error at a lower level does NOT prove that the cause is there
- The first level that is no longer justified by the upper level is considered the root cause
- If the root cause is outside the agent's responsibility, the agent MUST stop, record the conclusion in context, and return control to the orchestrator

## End-to-End Chain Verification

In any conflict, test failure, behavioral mismatch, or dispute between artifacts, the agent MUST verify the chain from top to bottom:

1. The business goal and user answers are clear, current, and do not contradict each other
2. The specification correctly reflects the business goal
3. Technical design and task breakdown correctly reflect the specification
4. BDD / acceptance scenarios correctly reflect the specification and design
5. Unit / integration tests correctly reflect the scenarios and the specification
6. The code correctly reflects the design, tests, and specification

It is forbidden to skip levels and make a binary conclusion like "the test is at fault" or "the code is at fault" without checking the upper levels.

## Classification of the First Broken Link

| Where the discrepancy was first found | Classification |
|--------------------------------------|----------------|
| Levels 1-2 | Requirements / specification problem |
| Level 3 | Technical design / decomposition problem |
| Level 4 | Acceptance / BDD scenarios problem |
| Level 5 | Tests problem |
| Level 6 | Implementation problem |

What must be recorded is not only the symptom, but also the first broken link in the chain.

## What This Rule Adds

- Defines which artifact is authoritative in a conflict
- Sets a single diagnostic algorithm for ALL agents
- Separates two questions: "what is true?" and "who has the right to change it?"

## Principle, Boundaries, and Arbitration

- This rule is a general principle for all agents: the order of artifact priority and the method for finding the root cause
- Subagent prompts define the boundaries of responsibility: who CAN and CANNOT change what
- The orchestrator performs arbitration and routing: who to return the task to after finding the first broken link

In other words: this rule defines the source of truth, agent prompts define the right to change, orchestrator defines the return route.

## Implications for Roles

- Analyst works from level 1 to level 2 and does not replace requirements with design
- Architect works from level 2 to level 3 and does not change the task contract
- Scenario-Author formalizes level 4 based on levels 2-3 and does not expand behavior beyond them
- Developer-Tests writes level 5 based on levels 2-4 and does not tune tests to the implementation
- Developer-Code implements level 6 based on levels 2-5 and does not fix tests or scenarios
- Tester checks levels 4-6 against levels 1-3, but only fixes test artifacts
- Reviewer can analyze any level of the chain, but does not modify artifacts
- Orchestrator routes the task by the first broken link, not by a single symptom

## Typical Uses

- A unit test failed: this is NOT automatically a bug in the code. First check that the test follows the specification and scenarios
- Tester sees `implementation_error` only after checking that the test and expected behavior are consistent with the upper levels
- Developer-Code suspects an error in the test: does NOT fix the test, but records the conflict and stops
- Conflict between code and technical design: the code must be brought into line with the design, or the design must be officially updated through the process

---
depends_on:
  - framework/rules/sdd-policy.md
  - framework/rules/tdd-policy.md
---
