---
name: source-of-truth
description: MUST use BEFORE drawing conclusions about the cause of a conflict, a test failure, or an artifact dispute. Defines the L1→L6 end-to-end verification method and the classification of the first broken link.
installable: true
alwaysApply: false
---

# Source of Truth Policy (Source of Truth) — Method

> In full-cycle, artifacts form a hierarchy of sources of truth. A lower level refines the higher one, but does NOT override it. On conflict, priority is always with the higher level. Verification is performed along the entire chain from top to bottom until the first broken link is found. This skill is a detailed procedure; the invariant trigger "conflict → check the chain from top to bottom" is fixed in rule `framework/rules/source-of-truth/SKILL.md`.

## Hierarchy of Sources of Truth

| Level | Artifact | Purpose |
|---------|----------|------------|
| 1 | Task business goal + explicit user answers | Define intent and the allowed result |
| 2 | Approved specification + acceptance criteria | Fix the task contract |
| 3 | Technical design + task-breakdown.json | Define the implementation approach and decomposition |
| 4 | BDD / acceptance scenarios | Formalize observable behavior |
| 5 | Unit / integration tests | Verify behavior at the executable level |
| 6 | Code | Implements behavior |

If a specific task is missing a level according to the process rules, the source of truth is the nearest approved level above the current one.

## MUST

- Each lower level MUST be traceable to the upper level
- A lower level MUST NOT silently override behavior fixed above
- On a conflict between lower and upper levels, the agent MUST treat the lower level as suspicious until the chain has been checked
- A level 1 user response has the highest priority, but after such a change, downstream artifacts MUST be updated and rechecked
- An error visible at a lower level does NOT prove that the cause is there
- The first level that is no longer justified by the upper level is considered the root cause
- If the root cause is outside the agent's responsibility, the agent MUST stop, record the conclusion in context, and return control to the orchestrator

## End-to-End Chain Verification

On any conflict, test failure, behavior mismatch, or dispute between artifacts, the agent MUST verify the chain from top to bottom:

1. The business goal and user answers are clear, current, and do not contradict each other
2. The specification correctly reflects the business goal
3. Technical design and task breakdown correctly reflect the specification
4. BDD / acceptance scenarios correctly reflect the specification and design
5. Unit / integration tests correctly reflect the scenarios and the specification
6. Code correctly reflects the design, tests, and specification

It is forbidden to skip levels and make a binary conclusion such as "the test is at fault" or "the code is at fault" without checking the upper levels.

## Classification of the First Broken Link

| Where the mismatch was first detected | Classification |
|------------------------------------|---------------|
| Levels 1-2 | Requirements / specification issue |
| Level 3 | Technical design / decomposition issue |
| Level 4 | Acceptance / BDD scenarios issue |
| Level 5 | Test issue |
| Level 6 | Implementation issue |

What must be recorded is not only the symptom, but also the first broken link in the chain.

## What This Rule Adds

- Defines which artifact is authoritative in a conflict
- Establishes a single diagnostic algorithm for ALL agents
- Separates two questions: "what is true?" and "who has the right to change it?"

## Principle, Boundaries, and Arbitration

- This rule is a general principle for all agents: the priority order of artifacts and the method for finding the root cause
- Subagent prompts define responsibility boundaries: who CAN and CANNOT change what
- The orchestrator performs arbitration and routing: who to return the task to after the first broken link is found

In other words: this rule defines the source of truth, agent prompts define the right to change, the orchestrator defines the return route.

## Implications for Roles

- Analyst works from level 1 to level 2 and does not substitute design for requirements
- Architect works from level 2 to level 3 and does not change the task contract
- Scenario-Author formalizes level 4 based on levels 2-3 and does not extend behavior beyond them
- Developer-Tests writes level 5 based on levels 2-4 and does not fit tests to implementation
- Developer-Code implements level 6 based on levels 2-5 and does not fix tests or scenarios
- Tester checks levels 4-6 against levels 1-3, but only fixes test artifacts
- Reviewer can analyze any level of the chain, but does not modify artifacts
- Orchestrator routes the task to the first broken link, not to a single symptom

## Typical Uses

- A unit test failed: this is NOT automatically a bug in the code. First check that the test follows the specification and scenarios
- Tester sees `implementation_error` only after checking that the test and expected behavior are aligned with the upper levels
- Developer-Code suspects an error in the test: does NOT fix the test, but records the conflict and stops
- A conflict between code and technical design: the code must be brought into alignment with the design, or the design must be officially updated through the process

---
depends_on:
  - framework/rules/sdd-policy/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
---
