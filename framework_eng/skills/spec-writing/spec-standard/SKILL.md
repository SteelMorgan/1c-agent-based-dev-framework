---
name: spec-standard
description: Universal skill for writing specifications (SDD). Defines the structure of the spec, RFC 2119, and a quality checklist regardless of the task execution mode.
---

# Spec Writing Skill (SDD)

## 1. Purpose

This skill defines the **universal specification standard** for 1С development tasks in the **SDD (Spec-Driven Development)** format.

The skill ensures:
- the structure and completeness of the specification;
- the phrasing of requirements via RFC 2119;
- the quality check of the specification before implementation.

The skill **does not select the execution mode** (subagent/linear) and does not introduce mechanics for switching modes.

---

## 2. When a specification is required

| Task type | Spec required | Justification |
|------------|-------------|-------------|
| New functionality | MUST | Captures the scope, requirements, alternatives, and the chosen solution. |
| Bug fix with architectural impact | MUST | Needed to justify changes in structure or behavior. |
| Simple localized bug fix | MAY | A short description without a full spec is acceptable if the change is isolated. |
| Major refactoring | SHOULD | Transparency is needed regarding the boundaries and consequences of changes. |

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

## Open Questions

## Decision Log (ADR)
```

---

## 4. RFC 2119 rules

| Keyword | Meaning | Usage rule |
|----------------|----------|------------------------|
| MUST | Mandatory | Without completion the requirement is considered unmet. |
| SHOULD | Strongly recommended | Deviation is allowed only with explicit justification. |
| MAY | Optional | An enhancement that does not block acceptance. |
| MUST NOT | Prohibited | A clear restriction; violation is unacceptable. |

Requirements must be:
- atomic (one requirement equals one verifiable idea);
- testable (verifiable by a test or scenario);
- consistent across sections.

---

## 5. Task decomposition as a mandatory spec extension

For tasks that require a specification, task decomposition is **mandatory**.

Rules:
1. Decomposition is documented in a **separate Task Breakdown JSON file**.
2. The specification must include a **link to the JSON** and/or a brief summary of the task structure.
3. The format and terminology of the decomposition are set by the extension skill.
4. The choice of the decomposition quality control process is performed **outside this skill**:
   - via `task-breakdown-subagent` (cross-review mode), or
   - via `task-breakdown-linear` (linear single-agent mode with self-check).

`spec-standard` enforces the mandatory decomposition but does not contain mechanics for choosing the mode.

---

## 6. Specification quality criteria

Review checklist:

- [ ] Context describes who has the problem and what is not working.
- [ ] Every MUST is covered by an item in the Test Plan.
- [ ] Scope clearly separates In scope and Out of scope.
- [ ] Considered Options contains at least two alternatives.
- [ ] Decision Outcome provides justification and consequences.
- [ ] Technical Design separates user tasks (metadata) and agent tasks (code).
- [ ] There are no contradictions between sections.
- [ ] Requirements are phrased through RFC 2119 (MUST/SHOULD/MAY/MUST NOT).
- [ ] There is a link or summary for the separate Task Breakdown JSON.

---

## 7. Common mistakes

| Mistake | Consequence | How to avoid |
|--------|------------|--------------|
| Mixing problem and solution in Context | Unclear what exactly needs to be fixed | Formulate the problem first, then the solution options |
| Vague requirements without RFC 2119 | Impossible to accept the work unambiguously | Phrase requirements using MUST/SHOULD/MAY/MUST NOT |
| Empty Out of scope | Scope creep and uncontrolled expansion of the task | Explicitly list what is not included |
| Absence of task decomposition | Weak traceability of implementation | Add a separate Task Breakdown JSON and link it in the spec |
| Contradictions between Requirements and Technical Design | Implementation and review errors | Perform a final cross-check of sections before switching to Review status |

---

## 8. Related skills

- `framework/skills/spec-writing/task-breakdown-subagent/SKILL.md` — task decomposition for the subagent mode (cross-review, BLOCK cycle).
- `framework/skills/spec-writing/task-breakdown-linear/SKILL.md` — task decomposition for the linear single-agent mode (self-check).

---
depends_on: []
---
