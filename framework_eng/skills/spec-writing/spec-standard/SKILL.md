---
name: spec-standard
description: "Use for writing task specifications (SDD). Defines the document structure, RFC 2119 requirement levels, and a quality checklist for Phase 1 full-cycle."
---

# Specification Writing Skill (SDD)

The skill **does not choose the execution mode** (subagent/linear) - only the structure, RFC 2119, and the quality checklist.

---

## 2. When a specification is needed

| Task type | Specification required | Rationale |
|------------|-------------|-------------|
| New functionality | MUST | Captures scope, requirements, alternatives, and the chosen solution. |
| Bug fix with architectural impact | MUST | The change in structure/behavior must be justified. |
| Simple local bug fix | MAY | A short description without a full spec is acceptable if the change is isolated. |
| Large refactoring | SHOULD | Transparency about boundaries and the consequences of changes is needed. |

---

## 3. Specification language

The specification MUST be written in **Russian** - section headings, descriptions, requirements, and scenarios. The exception is code and metadata identifiers (module names, attributes, variables), which remain as-is.

## 4. Required specification structure

```markdown
# SPEC-NNN: [Краткое название]
Статус: Черновик | Ревью | Утверждена | Реализована
Дата: YYYY-MM-DD

## Контекст и постановка проблемы

## Требования (RFC 2119)
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

## 4a. No duplication of test levels (MUST)

Every test in the "Test Plan" MUST add coverage that does not already exist. It is forbidden to
plan a test (especially BDD/Vanessa or integration) that checks the same logic with the same
inputs and the same observable result as an already covered unit test - that is, 1-to-1.

**1-to-1 duplicate criterion (DO NOT plan):** the second test goes through the same code path, with
the same Arrange and the same asserts as the first, and does not involve any new layer (UI/client,
posting in a real database, integration boundary, rights/roles, multi-session operation,
concurrency). BDD on top of full unit coverage of the same server-side calculation is a typical
duplicate.

**When a second test is justified (plan it):** it EXTENDS coverage - it adds a layer or dimension
that the first test cannot cover:
- client/UI form behavior (visibility, availability, notifications, operator input);
- end-to-end posting through a real database write (while unit mocked the engine);
- integration boundary (external API, exchange, HTTP service);
- user rights/roles/context (if the mode is NOT unconditionally privileged - see
  [[test-writing]] on the privileged-mode test anti-pattern);
- concurrency, restart idempotence, multi-session operation.

**Why:** a 1-to-1 duplicate wastes resources and time (writing + execution + maintenance + false
failure diagnostics) without adding a single line of new coverage. A "green" duplicate creates the
illusion of greater confidence, which does not exist. The cost of BDD-level work (Phase 3a/3c:
executable steps, run profile, iterations to GREEN, zero-residue teardown) is especially high - it
must be justified by a new layer, not by repeating server logic.

**Analyst action:** for each BDD/integration scenario in the spec, explicitly state WHICH layer it
covers beyond the unit plan (one line "extends: <layer>"). If there is no extension and the
scenario is 1-to-1 with unit, do NOT include it in the plan; record the decision in the ADR as
"BDD not needed: covered by unit, duplicate avoided". The Reviewer checks this as part of test
plan acceptance.

---

## 5. RFC 2119 rules

| Keyword | Meaning | Usage rule |
|----------------|----------|------------------------|
| MUST | Required | Without it, the requirement is considered unmet. |
| SHOULD | Strongly recommended | Deviation is allowed only with explicit justification. |
| MAY | Optional | An improvement that does not block acceptance. |
| MUST NOT | Forbidden | An explicit restriction, violation is unacceptable. |

Requirements must be:
- atomic (one requirement - one verifiable idea);
- verifiable (can be confirmed by a test/scenario);
- non-contradictory between sections.

---

## 6. Task decomposition

For tasks with a specification, decomposition is **mandatory** (a separate Task Breakdown JSON
file). In the specification - include a link to the JSON and/or a short summary.

The quality control process is outside this skill: `task-breakdown` (§3 Linear - self-check, §4
Subagent - cross-review).

---

## 7. Specification quality criteria

Review checklist:

- [ ] "Context" describes who has the problem and what is not working.
- [ ] Every MUST is covered by an item in the "Test Plan".
- [ ] "Scope" clearly separates "In scope" and "Out of scope".
- [ ] "Considered options" contains at least 2 alternatives.
- [ ] "Chosen solution" contains rationale and consequences.
- [ ] "Technical design" separates user tasks (metadata) and agent tasks (code).
- [ ] There are no contradictions between sections.
- [ ] Requirements are formulated using RFC 2119 (MUST/SHOULD/MAY/MUST NOT).
- [ ] There is a link/summary for a separate Task Breakdown JSON.
- [ ] "Acceptance scenarios" contain business-level Gherkin scenarios (Given/When/Then) for MUST
  requirements.
- [ ] The document is written in Russian (except code identifiers).

---

## 8. Common mistakes

| Error | Consequence |
|--------|------------|
| Mixing the problem and solution in Context | It is unclear what needs to be fixed |
| Vague requirements without RFC 2119 | The work cannot be accepted unambiguously |
| Empty Out of scope | Scope creep |
| Lack of task decomposition | Weak traceability |
| Contradictions Requirements ↔ Technical Design | Implementation errors |

---
depends_on: []
---
