---
name: spec-standard
description: Universal skill for writing specifications (SDD). Defines the spec structure, RFC 2119, and quality checklist regardless of execution mode.
---

# Specification Writing Skill (SDD)

The skill **does not choose an execution mode** (subagent/linear) — it only provides the structure, RFC 2119, and quality checklist.

---

## 2. When a specification is needed

| Task type | Specification required | Justification |
|-----------|------------------------|---------------|
| New functionality | MUST | Captures the scope, requirements, alternatives, and chosen solution. |
| Bug fix with architectural impact | MUST | Needed to justify changes in structure/behavior. |
| Simple local bug fix | MAY | A brief description without a full spec is acceptable if the change is isolated. |
| Large refactoring | SHOULD | Transparency on boundaries and consequences of changes is needed. |

---

## 3. Mandatory specification structure

```markdown
# SPEC-NNN: [Краткое название]
Status: Draft | Review | Approved | Implemented
Date: YYYY-MM-DD

## Context and Problem Statement

## Requirements (RFC 2119)
### MUST
### SHOULD
### MAY
### MUST NOT

## Scope
### In scope
### Out of scope

## Considered Options

## Decision Outcome

## Technical Design
### Metadata Objects (создает пользователь)
### Modules (пишет агент)
### Data Flow

## Test Plan (TDD)

## Acceptance Scenarios (BDD)

## Open Questions

## Decision Log (ADR)
```

---

## 4. RFC 2119 rules

| Keyword | Meaning | Usage rule |
|---------|---------|------------|
| MUST | Mandatory | Without satisfying it the requirement is considered unmet. |
| SHOULD | Strongly recommended | Deviation is allowed only with an explicit justification. |
| MAY | Optional | An improvement that does not block acceptance. |
| MUST NOT | Forbidden | An explicit restriction; violation is unacceptable. |

Requirements must be:
- atomic (one requirement equals one verifiable idea);
- verifiable (confirmable with a test/scenario);
- consistent across sections.

---

## 5. Task breakdown

For tasks with a specification, breakdown is **mandatory** (a separate Task Breakdown JSON file). The specification should include a link to the JSON and/or a brief summary.

The quality control process sits outside this skill: `task-breakdown-subagent` (cross-review) or `task-breakdown-linear` (self-check).

---

## 6. Specification quality criteria

Review checklist:

- [ ] Context describes who has the problem and what is broken.
- [ ] Every MUST is covered by an item in the Test Plan.
- [ ] Scope clearly separates In scope and Out of scope.
- [ ] Considered Options includes at least 2 alternatives.
- [ ] Decision Outcome contains rationale and consequences.
- [ ] Technical Design separates user-facing tasks (metadata) and agent tasks (code).
- [ ] Sections do not contradict each other.
- [ ] Requirements are phrased using RFC 2119 (MUST/SHOULD/MAY/MUST NOT).
- [ ] There is a link/summary for the separate Task Breakdown JSON.
- [ ] Acceptance Scenarios contain business-level Gherkin scenarios (Given/When/Then) for the MUST requirements.

---

## 7. Common mistakes

| Mistake | Consequence |
|---------|-------------|
| Mixing the problem and the solution in Context | It becomes unclear what needs to be fixed |
| Vague requirements without RFC 2119 | The work cannot be accepted unambiguously |
| Empty Out of scope | Scope creep |
| Missing task breakdown | Poor traceability |
| Contradictions between Requirements ↔ Technical Design | Implementation errors |

---
depends_on: []
---
