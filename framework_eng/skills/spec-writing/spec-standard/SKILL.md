---
name: spec-standard
description: "A universal skill for writing specifications (SDD). Defines the spec structure, RFC 2119, and the quality checklist regardless of the task execution mode."
---

# Specification Writing Skill (SDD)

This skill **does not choose an execution mode** (subagent/linear) - only the structure, RFC 2119, and the quality checklist.

---

## 2. When a Specification Is Needed

| Task type | Spec needed | Rationale |
|------------|-------------|-------------|
| New functionality | MUST | Captures scope, requirements, alternatives, and the chosen solution. |
| Bug fix with architectural impact | MUST | The change in structure/behavior must be justified. |
| Simple local bug fix | MAY | A short description without a full spec is acceptable if the change is isolated. |
| Large refactoring | SHOULD | Transparency about boundaries and consequences of the changes is needed. |

---

## 3. Specification Language

The specification MUST be written in **Russian** - section headings, descriptions, requirements, scenarios. The exception is code and metadata identifiers (module names, attributes, variables), which remain unchanged.

## 4. Mandatory Specification Structure

```markdown
# SPEC-NNN: [Краткое название]
Статус: Черновик | Ревью | Утверждена | Реализована
Date: YYYY-MM-DD

## Контекст и постановка проблемы

## Requirements (RFC 2119)
### MUST
### SHOULD
### MAY
### MUST NOT

## Границы
### Входит в scope
### Не входит в scope

## Рассмотренные варианты

## Выбранное решение

## Технический дизайн
### Объекты метаданных (создаёт пользователь)
### Модули (пишет агент)
### Поток данных

## План тестирования (TDD)

### Тестовые пользователи (Test Users)

Если тесты (unit / BDD / integration) зависят от ролей, прав или контекста пользователя, спека ОБЯЗАНА содержать секцию «Test Users» (или эквивалент) со следующими правилами:

- Перечислять **только реально существующих** в целевой базе пользователей (логин + состав ролей + ссылка на источник: предзагруженный профиль, fixture, final-report связанной задачи и т.п.).
- **Запрещены placeholder-имена** («User1», «TestUser», «Manager_NoRole»), а также вымышленные ФИО без подтверждённого соответствия реальному аккаунту в базе («Сидоров», «Иванов» — если такого пользователя в базе нет).
- Для каждого test user указать минимум: логин, состав ролей, источник, тестовый сценарий-применение.
- Если подходящий пользователь **неизвестен** или **не существует** — Analyst задаёт `clarification_needed` пользователю в clarification round, а не выдумывает имя. Допустимо предложить пользователю кандидатов на создание (с указанием ролей), но имя должно быть подтверждено.
- Если test user должен быть **создан администратором** перед запуском (manual data prep) — это явно фиксируется отдельным пунктом в `manual-test-scenario.md` или эквивалентном артефакте, с описанием шагов создания.

**Почему:** placeholder-имена в спеке приводят к Vanessa-сценариям типа «Не смог подключить TestClient <Сидоров>» и проваливают весь Vanessa-уровень. Tester / Scenario-Coder не могут «угадать» реального пользователя и теряют часы на диагностику.

## Приёмочные сценарии (BDD)

## Открытые вопросы

## Журнал решений (ADR)
```

---

## 5. RFC 2119 Rules

| Keyword | Meaning | Usage rule |
|---------|---------|------------|
| MUST | Mandatory | Without this, the requirement is considered unmet. |
| SHOULD | Strongly recommended | Deviation is allowed only with explicit justification. |
| MAY | Optional | An enhancement that does not block acceptance. |
| MUST NOT | Prohibited | An explicit restriction, violation is unacceptable. |

Requirements must be:
- atomic (one requirement - one verifiable thought);
- verifiable (can be confirmed by a test/scenario);
- non-contradictory across sections.

---

## 6. Task Decomposition

For tasks with a specification, decomposition is **mandatory** (a separate JSON file Task Breakdown). The specification should include a link to the JSON and/or a brief summary.

The quality control process is outside this skill: `task-breakdown` (§3 Linear - self-check, §4 Subagent - cross-review).

---

## 7. Specification Quality Criteria

Review checklist:

- [ ] The "Context" describes who has the problem and what is not working.
- [ ] Every MUST is covered by an item in the "Test Plan".
- [ ] "Boundaries" clearly separate "In scope" and "Out of scope".
- [ ] "Considered Alternatives" contains at least 2 alternatives.
- [ ] "Chosen Solution" contains justification and consequences.
- [ ] "Technical Design" separates the user's tasks (metadata) and the agent's tasks (code).
- [ ] There are no contradictions between sections.
- [ ] Requirements are formulated using RFC 2119 (MUST/SHOULD/MAY/MUST NOT).
- [ ] There is a link/summary for a separate Task Breakdown JSON.
- [ ] The "Acceptance Scenarios" contain business-level Gherkin scenarios (Given/When/Then) for MUST requirements.
- [ ] The document is written in Russian (except for code identifiers).

---

## 8. Typical Mistakes

| Mistake | Consequence |
|---------|-------------|
| Mixing the problem and the solution in Context | It is unclear what needs to be fixed |
| Vague requirements without RFC 2119 | The work cannot be accepted unambiguously |
| Empty Out of scope | Scope creep |
| Missing task decomposition | Weak traceability |
| Contradictions Requirements ↔ Technical Design | Implementation errors |

---
depends_on: []
---
