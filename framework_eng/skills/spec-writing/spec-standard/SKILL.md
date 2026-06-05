---
name: spec-standard
description: "Use for writing a task specification (SDD). Defines the document structure, RFC 2119 requirement levels, and the quality checklist for Phase 1 full-cycle."
---

# Skill for writing specifications (SDD)

The skill does **not choose the execution mode** (subagent/linear) - only structure, RFC 2119, and the quality checklist.

---

## 2. When a specification is needed

| Task type | Spec needed | Rationale |
|------------|-------------|-----------|
| New functionality | MUST | Captures scope, requirements, alternatives, and the chosen solution. |
| Bug fix with architectural impact | MUST | The change in structure/behavior must be justified. |
| Simple local bug fix | MAY | A short description without a full spec is acceptable if the change is isolated. |
| Large refactoring | SHOULD | Transparency is needed for boundaries and consequences of the changes. |

---

## 3. Specification language

The specification MUST be written in **Russian** - section headings, descriptions, requirements, and scenarios. Exception: code and metadata identifiers (module names, attributes, variables) remain unchanged.

## 4. Mandatory specification structure

```markdown
# SPEC-NNN: [Short title]
Status: Draft | Review | Approved | Implemented
Date: YYYY-MM-DD

## Context and problem statement

## Requirements (RFC 2119)
### MUST
### SHOULD
### MAY
### MUST NOT

## Boundaries
### In scope
### Out of scope

## Considered options

## Chosen solution

## Technical design
### Metadata objects (created by the user)
### Modules (written by the agent)
### Data flow

## Test plan (TDD)

### Test Users

If tests (unit / BDD / integration) depend on roles, permissions, or user context, the spec MUST include a "Test Users" section (or equivalent) with the following rules:

- List **only real users** that actually exist in the target database (login + role set + source reference: preloaded profile, fixture, final-report of the related task, etc.).
- **Placeholder names are forbidden** ("User1", "TestUser", "Manager_NoRole"), as are invented full names without confirmed correspondence to a real account in the database ("Sidorov", "Ivanov" - if such a user does not exist in the database).
- For each test user, specify at minimum: login, role set, source, and the test scenario usage.
- If a suitable user is **unknown** or **does not exist**, the Analyst asks the user for `clarification_needed` in the clarification round instead of inventing a name. It is acceptable to suggest candidates for creation to the user (with the required roles), but the name must be confirmed.
- If a test user must be **created by an administrator** before execution (manual data prep), this is explicitly recorded as a separate item in `manual-test-scenario.md` or an equivalent artifact, with the creation steps described.

**Why:** placeholder names in the spec lead to Vanessa scenarios like "Could not connect TestClient <Sidorov>" and break the entire Vanessa level. Tester / Scenario-Coder cannot "guess" a real user and lose hours on diagnosis.

## Acceptance scenarios (BDD)

## Open questions

## Decision log (ADR)
```

---

## 5. RFC 2119 rules

| Keyword | Meaning | Usage rule |
|---------|---------|------------|
| MUST | Mandatory | The requirement is not fulfilled unless it is implemented. |
| SHOULD | Strongly recommended | Deviation is allowed only with explicit justification. |
| MAY | Optional | An improvement that does not block acceptance. |
| MUST NOT | Prohibited | An explicit constraint; violation is unacceptable. |

Requirements must be:
- atomic (one requirement - one checkable idea);
- verifiable (can be confirmed by a test/scenario);
- non-contradictory across sections.

---

## 6. Task decomposition

For tasks with a specification, decomposition is **mandatory** (a separate Task Breakdown JSON file). In the specification, provide a link to the JSON and/or a short summary.

The quality control process is outside this skill: `task-breakdown` (§3 Linear - self-check, §4 Subagent - cross-review).

---

## 7. Specification quality criteria

Review checklist:

- [ ] "Context" describes who has the problem and what is not working.
- [ ] Every MUST is covered by an item in the "Test Plan".
- [ ] "Boundaries" clearly separate "In scope" and "Out of scope".
- [ ] "Considered options" contains at least 2 alternatives.
- [ ] "Chosen solution" includes justification and consequences.
- [ ] "Technical design" separates the user's tasks (metadata) and the agent's tasks (code).
- [ ] There are no contradictions between sections.
- [ ] Requirements are phrased using RFC 2119 (MUST/SHOULD/MAY/MUST NOT).
- [ ] There is a link/summary for a separate Task Breakdown JSON.
- [ ] "Acceptance scenarios" contain business-level Gherkin scenarios (Given/When/Then) for MUST requirements.
- [ ] The document is written in Russian (except for code identifiers).

---

## 8. Common mistakes

| Mistake | Consequence |
|---------|-------------|
| Mixing the problem and the solution in Context | It is unclear what needs to be fixed |
| Vague requirements without RFC 2119 | The work cannot be accepted unambiguously |
| Empty Out of scope | Scope creep |
| Missing task decomposition | Weak traceability |
| Contradictions between Requirements and Technical Design | Implementation errors |

---
depends_on: []
---
