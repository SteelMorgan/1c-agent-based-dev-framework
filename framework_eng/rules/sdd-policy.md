---
name: sdd-policy
description: SDD Policy (Spec-Driven Development) — the specification is created before implementation and is a contract.
---

# SDD Policy (Spec-Driven Development)

> The specification is created before implementation and is a contract. Implementation MUST NOT deviate without updating and re-review.

## Classification: when a spec is needed

| Task type | Level |
|------------|---------|
| New functionality, architectural changes, complex bug | MUST |
| Major refactoring | SHOULD |
| Simple bug, formatting, typos | MAY (skip) |

## SDD Workflow

```
1. Классифицировать задачу по сложности
2. Создать спеку по стандарту (spec-standard) + Acceptance Scenarios (Gherkin) для MUST-требований
3. Ревью спеки → итерации до снятия BLOCK
4. Архитектура + Task Breakdown JSON → ревью → итерации до снятия BLOCK
5. Одобрение пользователя (для средних/сложных)
6. Реализация строго по спеке и JSON-декомпозиции
7. Проверка соответствия реализации спеке
```

## MUST Requirements

- A spec is created for new features, architectural changes, and complex bugs
- Format — `spec-standard`; review before implementation
- Task Breakdown JSON — separate `.json`, review before implementation
- Any change to the approved spec/JSON requires a re-review

## Deviation from the spec

1. Stop implementation
2. Update the spec + review + approval
3. Continue according to the updated spec

## Exceptions

- Simple tasks — spec not required
- Prototyping at the customer's request — without spec
- Free mode (no full cycle) — advisory

---
depends_on:
  - framework/rules/tdd-policy.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---
