---
name: spec-standard
description: "Universal SDD (Software Design Document) skill for 1C BSL projects. Defines specification structure, RFC 2119 requirement levels, and quality checklist. Use when writing a new feature spec, documenting an architectural decision, or preparing a structured task description before implementation."
user-invocable: true
triggers:
  - write a spec for this
  - create a specification
  - document this feature
  - write an SDD
  - create a design document
  - prepare a specification before coding
  - spec out this task
  - write requirements for this
---

# Навык написания спецификаций (SDD)

Навык **не выбирает режим исполнения** (subagent/linear) — только структура, RFC 2119 и quality checklist.

---

## 2. Когда нужна спецификация

| Тип задачи | Нужна спека | Обоснование |
|------------|-------------|-------------|
| Новая функциональность | MUST | Фиксирует scope, требования, альтернативы и выбранное решение. |
| Исправление бага с архитектурным влиянием | MUST | Требуется обосновать изменение структуры/поведения. |
| Простое локальное исправление бага | MAY | Допустимо короткое описание без полной спеки, если изменение изолированно. |
| Крупный рефакторинг | SHOULD | Нужна прозрачность по границам и последствиям изменений. |

---

## 3. Обязательная структура спецификации

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

## 4. Правила RFC 2119

| Ключевое слово | Значение | Правило использования |
|----------------|----------|------------------------|
| MUST | Обязательно | Без выполнения требование считается невыполненным. |
| SHOULD | Настоятельно рекомендуется | Отклонение допустимо только с явным обоснованием. |
| MAY | Опционально | Улучшение, не блокирующее приемку. |
| MUST NOT | Запрещено | Явное ограничение, нарушение недопустимо. |

Требования должны быть:
- атомарными (одно требование — одна проверяемая мысль);
- проверяемыми (можно подтвердить тестом/сценарием);
- непротиворечивыми между разделами.

---

## 5. Декомпозиция задач

Для задач со спецификацией декомпозиция **обязательна** (отдельный JSON-файл Task Breakdown). В спецификации — ссылка на JSON и/или краткая выжимка.

Процесс контроля качества — вне этого навыка: `task-breakdown-subagent` (cross-review) или `task-breakdown-linear` (self-check).

---

## 6. Критерии качества спецификации

Чеклист для ревью:

- [ ] Context описывает кто имеет проблему и что не работает.
- [ ] Каждый MUST покрыт пунктом в Test Plan.
- [ ] Scope явно разделяет In scope и Out of scope.
- [ ] Considered Options содержит минимум 2 альтернативы.
- [ ] Decision Outcome содержит обоснование и последствия.
- [ ] Technical Design разделяет задачи пользователя (метаданные) и агента (код).
- [ ] Между разделами нет противоречий.
- [ ] Требования сформулированы через RFC 2119 (MUST/SHOULD/MAY/MUST NOT).
- [ ] Есть ссылка/выжимка по отдельному Task Breakdown JSON.
- [ ] Acceptance Scenarios содержат Gherkin-сценарии бизнес-уровня (Given/When/Then) для MUST-требований.

---

## 7. Типичные ошибки

| Ошибка | Последствие |
|--------|------------|
| Смешение проблемы и решения в Context | Неясно, что нужно исправить |
| Размытые требования без RFC 2119 | Невозможно однозначно принять работу |
| Пустой Out of scope | Scope creep |
| Отсутствие декомпозиции задач | Слабая трассируемость |
| Противоречия Requirements ↔ Technical Design | Ошибки при реализации |

---
depends_on: []
---
