---
name: sdd-policy
description: SDD (Spec-Driven Development) policy — the specification is created before implementation and serves as a contract.
---


# SDD (Spec-Driven Development) Policy

> RULES — mandatory policies that constrain agent behavior. The specification is created before implementation and serves as a contract.

---

## Purpose

This rule ensures scope management and quality of decisions through SDD: the specification is created before code, reviewed before implementation, and the implementation must not deviate from the approved spec without updating it.

---

## Trigger Conditions

The rule applies when the agent:

- Receives a task for implementation
- Classifies the task by complexity (simple / medium / complex)
- Creates or modifies a specification
- Implements functionality according to the specification

---

## Requirements

### MUST (mandatory)

| Requirement | Description |
|------------|-------------|
| Specification for new features | The specification MUST be created for new features, architectural changes, and complex bug fixes |
| Specification format | The specification MUST follow the format from `skills/spec-writing/spec-standard.md` |
| Review before implementation | The specification MUST be reviewed before implementation begins |
| Task Breakdown JSON before implementation | The architectural Task Breakdown JSON decomposition MUST be created as a separate `.json` file after the spec review and before implementation begins |
| Review of JSON before implementation | The Task Breakdown JSON MUST undergo cross-review before implementation begins |
| Implementation compliance | Implementation MUST NOT deviate from the approved specification and Task Breakdown JSON without updating and repeating the review |
| Review when the spec changes | Changes to the approved specification require a repeat review |
| Review when the JSON changes | Changes to the approved Task Breakdown JSON decomposition require a repeat review |

### SHOULD (strongly recommended)

| Requirement | Description |
|------------|-------------|
| Specification for refactoring | The specification SHOULD be created for large refactoring and moderate changes |

### MAY (permissible)

| Situation | Description |
|----------|-------------|
| Simple bug fix | The specification MAY be skipped |
| Formatting, typo fixes | The specification MAY be skipped |

---

## SDD Workflow

```
1. Получить задачу → классифицировать по сложности
2. Если спека нужна → создать спеку по стандарту
3. Отправить спеку на ревью (применяется cross-review-policy)
4. Итерировать до отсутствия BLOCK-замечаний по спеке
5. Построить архитектуру + Task Breakdown JSON (отдельный `.json` файл, шаблон + пример, без JSON Schema)
6. Отправить архитектуру и Task Breakdown JSON на ревью
7. Итерировать до отсутствия BLOCK-замечаний по архитектуре и JSON
8. Получить одобрение пользователя (для средних/сложных задач)
9. Реализовать согласно спецификации и утверждённой JSON-декомпозиции
10. Проверить соответствие реализации спецификации и JSON-декомпозиции
```

---

## Complexity Classification

| Task type | Spec required | Rationale |
|-----------|---------------|-----------|
| New functionality | MUST | Captures scope, requirements, alternatives |
| Architectural changes | MUST | Structural change requires justification |
| Complex bug fix | MUST | Impacts architecture or behavior |
| Refactoring | SHOULD | Recommended for large refactoring |
| Simple bug fix | MAY | Local change, can be done without a spec |
| Formatting, typos | MAY | Not required |

---

## Deviation from the Specification

If a deviation from the specification is discovered during implementation:

1. Stop the implementation
2. Update the specification (changes, justification)
3. Submit the updated specification for review
4. Obtain approval (if required)
5. Continue implementation according to the updated specification

---

## Exceptions

- Simple tasks (formatting, typos, obvious fixes) — a specification is not required
- The user may explicitly request implementation without a specification for prototyping
- In flexible mode (without full-cycle) creating a specification is advisory, not mandatory

---

## Related skills and rules

| Resource | Relationship |
|----------|--------------|
| [cross-review-policy.md](./cross-review-policy.md) | The specification is reviewed before implementation |
| [tdd-policy.md](./tdd-policy.md) | The test plan is part of the specification |
| [skills/spec-writing/spec-standard.md](../skills/spec-writing/spec-standard.md) | Specification format and structure |
| [docs/SPEC-001-framework-architecture.md](../../docs/SPEC-001-framework-architecture.md) | SDD as a framework requirement |

---
depends_on:
  - framework/rules/cross-review-policy.md
  - framework/rules/tdd-policy.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
