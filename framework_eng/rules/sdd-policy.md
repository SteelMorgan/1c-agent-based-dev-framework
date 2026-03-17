---
name: sdd-policy
description: SDD Policy (Spec-Driven Development) — the specification is created before implementation and serves as a contract.
---



# SDD Policy (Spec-Driven Development)

> RULES — mandatory policies that constrain the agent's behavior. The specification is created before implementation and serves as a contract.

---

## Purpose

This rule ensures scope control and solution quality through SDD: the specification is created before the code, reviewed before implementation, and implementation must not deviate from the approved spec without updating it.

---

## Trigger conditions

The rule applies when the agent:

- Receives an implementation task
- Classifies the task by complexity (simple / medium / complex)
- Creates or modifies the specification
- Implements functionality according to the specification

---

## Requirements

### MUST (mandatory)

| Requirement | Description |
|------------|-------------|
| Spec for new features | The specification MUST be created for new features, architectural changes, and complex bug fixes |
| Specification format | The specification MUST follow the format from `skills/spec-writing/spec-standard.md` |
| Review before implementation | The specification MUST be reviewed before implementation begins |
| Task Breakdown JSON before implementation | An architectural Task Breakdown JSON decomposition MUST be created as a separate `.json` file after the spec review and before implementation starts |
| JSON review before implementation | The Task Breakdown JSON MUST undergo cross-review before implementation begins |
| Alignment with implementation | The implementation MUST NOT diverge from the approved specification and Task Breakdown JSON without updating and re-reviewing them |
| Review when spec changes | Changes to the approved specification require a repeat review |
| Review when JSON changes | Changes to the approved Task Breakdown JSON decomposition require a repeat review |

### SHOULD (strongly recommended)

| Requirement | Description |
|------------|-------------|
| Spec for refactoring | A specification SHOULD be created for major refactoring and moderate changes |

### MAY (optional)

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
8a. Спецификация SHOULD содержать раздел Acceptance Scenarios с Gherkin-сценариями бизнес-уровня для MUST-требований
9. Реализовать согласно спецификации и утверждённой JSON-декомпозиции
10. Проверить соответствие реализации спецификации и JSON-декомпозиции
```

---

## Complexity classification

| Task type | Spec required | Rationale |
|-----------|---------------|-----------|
| New functionality | MUST | Records scope, requirements, alternatives |
| Architectural changes | MUST | Structural changes demand justification |
| Complex bug fix | MUST | Impacts architecture or behavior |
| Refactoring | SHOULD | Recommended for major refactoring |
| Simple bug fix | MAY | Local change, can be done without a spec |
| Formatting, typos | MAY | Not required |

---

## Departing from the spec

If the implementation requires deviation from the specification:

1. Halt implementation
2. Update the specification (changes, justification)
3. Send the updated specification for review
4. Obtain approval (if required)
5. Continue implementation according to the updated specification

---

## Exceptions

- Simple tasks (formatting, typos, obvious fixes) — the specification is not required
- The user can explicitly request implementation without a specification for prototyping
- In free mode (without full-cycle) creating a specification is advisory, not mandatory

---

## Related skills and policies

| Resource | Relationship |
|--------|--------------|
| [cross-review-policy.md](./cross-review-policy.md) | The specification is reviewed before implementation |
| [tdd-policy.md](./tdd-policy.md) | The test plan is part of the specification |
| [skills/spec-writing/spec-standard.md](../skills/spec-writing/spec-standard.md) | Specification format and structure |
| [docs/SPEC-001-framework-architecture.md](../../docs/SPEC-001-framework-architecture.md) | SDD as a framework requirement |

---
depends_on:
  - framework/rules/tdd-policy.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
