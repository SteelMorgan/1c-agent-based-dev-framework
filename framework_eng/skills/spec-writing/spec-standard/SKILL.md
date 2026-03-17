---
name: spec-standard
description: Universal specification-writing skill (SDD). Sets the spec structure, RFC 2119, and a quality checklist independent of execution mode.
---

# Specification Writing Skill (SDD)

## 1. Purpose

This skill establishes a **universal specification standard** for 1С development tasks in the **SDD (Spec-Driven Development)** format.

The skill is responsible for:
- the structure and completeness of the specification;
- framing requirements through RFC 2119;
- verifying the specification quality before implementation.

The skill **does not choose the execution mode** (subagent/linear) and does not provide mechanics for switching modes.

---

## 2. When a specification is required

| Task type | Spec required | Justification |
|------------|-------------|-------------|
| New functionality | MUST | Captures the scope, requirements, alternatives, and chosen solution. |
| Bug fix with architectural impact | MUST | Needed to justify changes in structure/behavior. |
| Simple localized bug fix | MAY | A short description without a full spec is acceptable if the change is isolated. |
| Major refactoring | SHOULD | Requires transparency about the boundaries and consequences of the changes. |

---

## 3. Required specification structure

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
|----------------|----------|------------------------|
| MUST | Mandatory | Without fulfilling it, the requirement is considered unmet. |
| SHOULD | Strongly recommended | Deviations are allowed only with explicit justification. |
| MAY | Optional | An enhancement that does not block acceptance. |
| MUST NOT | Forbidden | An explicit restriction; violation is unacceptable. |

Requirements must be:
- atomic (one requirement equals one verifiable idea);
- verifiable (it can be confirmed by a test/scenario);
- consistent across sections.

---

## 5. Task decomposition as a mandatory spec extension

For tasks that require a specification, task decomposition is **mandatory**.

Rules:
1. Decomposition is recorded in a **separate Task Breakdown JSON file**.
2. The specification must include a **link to the JSON** and/or a brief extract of the task structure.
3. The decomposition format and terminology are defined by the extension skill.
4. The choice of the decomposition quality control process happens **outside this skill**:
   - via `task-breakdown-subagent` (cross-review mode), or
   - via `task-breakdown-linear` (linear single-agent mode with self-check).

`spec-standard` enforces the obligation of decomposition but does not include the mode-selection mechanics.

---

## 6. Specification quality criteria

Checklist for review:

- [ ] Context explains who has the problem and what is not working.
- [ ] Every MUST is covered by an item in the Test Plan.
- [ ] Scope clearly separates In scope and Out of scope.
- [ ] Considered Options contains at least 2 alternatives.
- [ ] Decision Outcome includes justification and consequences.
- [ ] Technical Design differentiates user tasks (metadata) from agent tasks (code).
- [ ] Sections do not contradict each other.
- [ ] Requirements are phrased via RFC 2119 (MUST/SHOULD/MAY/MUST NOT).
- [ ] There is a link/extract for the separate Task Breakdown JSON.
- [ ] Acceptance Scenarios include business-level Gherkin scenarios (Given/When/Then) for MUST requirements.

---

## 7. Common mistakes

| Mistake | Consequence | How to avoid |
|--------|------------|--------------|
| Mixing the problem and the solution in Context | It becomes unclear what exactly needs to be fixed | First articulate the problem, then the solution options |
| Vague requirements without RFC 2119 | It is impossible to unambiguously accept the work | Phrase requirements using MUST/SHOULD/MAY/MUST NOT |
| Empty Out of scope | Scope creep and uncontrolled task expansion | Explicitly list what is not included |
| Missing task decomposition | Poor traceability of the implementation | Add a separate Task Breakdown JSON and link it in the spec |
| Contradictions between Requirements and Technical Design | Implementation and review errors | Do a final cross-check of the sections before moving to Review status |

---

## 8. Related skills

- `task-breakdown-subagent` — task decomposition for the subagent mode (cross-review, BLOCK cycle).
- `task-breakdown-linear` — task decomposition for the linear single-agent mode (self-check).

---
depends_on: []
---
