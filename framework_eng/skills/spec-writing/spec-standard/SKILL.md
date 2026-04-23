---
name: spec-standard
description: "Universal skill for writing specifications (SDD). Defines the spec structure, RFC 2119, and the quality checklist regardless of the task execution mode."
---

# Specification writing skill (SDD)

The skill **does not choose an execution mode** (subagent/linear) — it only provides the structure, RFC 2119, and the quality checklist.

---

## 2. When a specification is needed

| Task type | Spec needed | Rationale |
|------------|-------------|-------------|
| New functionality | MUST | Captures the scope, requirements, alternatives, and the chosen solution. |
| Bug fix with architectural impact | MUST | Requires justification for the change in structure/behavior. |
| Simple local bug fix | MAY | A brief description without a full spec is acceptable if the change is isolated. |
| Large refactoring | SHOULD | Needs transparency around the boundaries and consequences of the changes. |

---

## 3. Specification language

The specification MUST be written in **Russian** — section headings, descriptions, requirements, scenarios. Exception — code and metadata identifiers (module names, attributes, variables) remain as they are.

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

### Test Users

If tests (unit / BDD / integration) depend on user roles, rights, or context, the spec MUST contain a "Test Users" section (or equivalent) with the following rules:

- List **only users that actually exist** in the target database (login + role composition + source reference: pre-loaded profile, fixture, related task's final-report, etc.).
- **Placeholder names are forbidden** ("User1", "TestUser", "Manager_NoRole"), as well as fictional full names without confirmed correspondence to a real account in the database ("Sidorov", "Ivanov" — if no such user exists in the database).
- For each test user specify at minimum: login, role composition, source, test scenario application.
- If a suitable user is **unknown** or **does not exist** — Analyst raises `clarification_needed` to the user in a clarification round rather than inventing a name. It is acceptable to suggest candidates to the user for creation (with role list), but the name must be confirmed.
- If a test user must be **created by the administrator** before the run (manual data prep) — this is explicitly recorded as a separate item in `manual-test-scenario.md` or an equivalent artifact, with creation steps described.

**Why:** placeholder names in the spec lead to Vanessa scenarios like "Could not connect TestClient <Sidorov>" and fail the entire Vanessa layer. Tester / Scenario-Coder cannot "guess" a real user and lose hours on diagnostics.

## Acceptance scenarios (BDD)

## Open questions

## Decision log (ADR)
```

---

## 5. RFC 2119 rules

| Keyword | Meaning | Usage rule |
|----------------|----------|------------------------|
| MUST | Mandatory | Without fulfillment the requirement is considered not met. |
| SHOULD | Strongly recommended | Deviation is allowed only with explicit justification. |
| MAY | Optional | An enhancement that does not block acceptance. |
| MUST NOT | Prohibited | Explicit restriction, violation is unacceptable. |

Requirements must be:
- atomic (one requirement — one verifiable idea);
- verifiable (can be confirmed with a test/scenario);
- consistent across sections.

---

## 6. Task decomposition

For tasks with a specification, decomposition is **mandatory** (a separate Task Breakdown JSON file). The specification should include a link to the JSON and/or a brief summary.

The quality control process is outside this skill: `task-breakdown-subagent` (cross-review) or `task-breakdown-linear` (self-check).

---

## 7. Specification quality criteria

Checklist for review:

- [ ] "Context" describes who has the problem and what is broken.
- [ ] Every MUST is covered by a point in the "Test Plan".
- [ ] "Boundaries" clearly separate "In scope" and "Out of scope".
- [ ] "Considered options" contains at least 2 alternatives.
- [ ] "Chosen solution" includes rationale and consequences.
- [ ] "Technical design" separates user tasks (metadata) and agent tasks (code).
- [ ] There are no contradictions between sections.
- [ ] Requirements are formulated using RFC 2119 (MUST/SHOULD/MAY/MUST NOT).
- [ ] There is a link/summary to a separate Task Breakdown JSON.
- [ ] "Acceptance scenarios" contain business-level Gherkin scenarios (Given/When/Then) for MUST requirements.
- [ ] The document is written in Russian (except for code identifiers).

---

## 8. Common mistakes

| Mistake | Consequence |
|--------|------------|
| Mixing the problem and the solution in Context | It becomes unclear what needs to be fixed |
| Vague requirements without RFC 2119 | Impossible to unambiguously accept the work |
| Empty Out of scope | Scope creep |
| Lack of task decomposition | Weak traceability |
| Contradictions between Requirements ↔ Technical Design | Implementation errors |

---
depends_on: []
---
