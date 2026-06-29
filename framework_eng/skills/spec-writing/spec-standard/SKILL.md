---
name: spec-standard
description: "For SDD specifications with RFC 2119 and a checklist"
---

# Specification Writing Skill (SDD)

The skill **does not choose the execution mode** (subagent/linear) - only the structure, RFC 2119, and the quality checklist.

---

## 2. When a Specification Is Needed

| Task type | Spec needed | Rationale |
|------------|-------------|-------------|
| New functionality | MUST | Fixes scope, requirements, alternatives, and the chosen solution. |
| Bug fix with architectural impact | MUST | The change in structure/behavior must be justified. |
| Simple local bug fix | MAY | A short description without a full spec is acceptable if the change is isolated. |
| Large refactoring | SHOULD | Transparency is needed around the boundaries and consequences of the changes. |

---

## 3. Specification Language

The specification MUST be written in **Russian** - section headings, descriptions, requirements, and scenarios. Exception: code and metadata identifiers (module names, attributes, variables) remain as-is.

## 4. Required Specification Structure

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

If tests (unit / BDD / integration) depend on user roles, permissions, or context, the spec MUST contain a "Test Users" section (or equivalent) with the following rules:

- List **only actual users** that exist in the target database (login + role set + source link: preloaded profile, fixture, final-report of the linked task, etc.).
- **Placeholder names are forbidden** ("User1", "TestUser", "Manager_NoRole"), as are invented full names without confirmed correspondence to a real account in the database ("Sidorov", "Ivanov" - if such a user does not exist in the database).
- For each test user, specify at minimum: login, role set, source, and the test scenario/application.
- If a suitable user is **unknown** or **does not exist** - the Analyst asks the user for `clarification_needed` during the clarification round instead of inventing a name. It is acceptable to propose candidates for creation to the user (with roles specified), but the name must be confirmed.
- If a test user must be **created by an administrator** before execution (manual data prep), this is explicitly recorded in a separate item in `manual-test-scenario.md` or an equivalent artifact, with the creation steps described.

**Why:** placeholder names in a spec lead to Vanessa scenarios like "Could not connect TestClient <Sidorov>" and cause the entire Vanessa level to fail. The Tester / Scenario-Coder cannot "guess" a real user and lose hours to diagnostics.

## Acceptance Scenarios (BDD)

## Open Questions

## Decision Log (ADR)
```

---

## 4a. No Duplication Across Test Levels (MUST)

Each test in the "Test Plan" MUST add coverage that does not already exist. It is forbidden to
plan a test (especially BDD/Vanessa or integration) that checks the same logic with the same
inputs and the same observable result as an already covered unit test - that is, a 1:1 duplicate.

**1:1 duplicate criterion (DO NOT plan):** the second test follows the same code path, with the same
Arrange and the same assertions as the first, and does not use any new layer (UI/client, posting
to a real database, integration boundary, permissions/roles, multi-session behavior, concurrency).
BDD on top of full unit coverage of the same server-side calculation is a typical duplicate.

**When a second test is justified (plan it):** it EXPANDS coverage - it adds a layer or dimension
unavailable to the first:
- form client/UI behavior (visibility, availability, notifications, operator input);
- end-to-end posting through a real database write (while the unit test mocked the engine);
- integration boundary (external API, exchange, HTTP service);
- permissions/roles/user context (if the mode is NOT unconditionally privileged - see
  [[test-writing]] about the privileged-mode test antipattern);
- concurrency, restart idempotence, multi-session behavior.

**Why:** a 1:1 duplicate wastes resources and time (authoring + execution + maintenance + debugging
false failures) without adding a single line of new coverage. A "green" duplicate creates the
illusion of greater verification, which does not exist. The cost of the BDD level (Phase 3a/3c:
executable steps, run profile, iterations to GREEN, zero-residue teardown) is especially high - it
must be justified by a new layer, not by repeating server-side logic.

**Analyst action:** for each BDD/integration scenario in the spec, explicitly state WHICH layer it
covers beyond the unit plan (one line: "expands: <layer>"). If there is no expansion and the
scenario is 1:1 with the unit test, DO NOT include it in the plan; record the ADR decision "BDD is
not needed: covered by unit, duplicate avoided". Reviewer checks this as part of test plan
acceptance.

---

## 4b. Coverage by the Affected Runtime Layer (MUST)

The test plan MUST choose the test level based on the runtime layer that changes. You cannot cover
a client-side change only with syntax/unit tests, or a server-side change only with a UI click.

| What is affected | Required coverage |
|---------------|------------------------|
| Server-side logic, common module, manager/object module, form server method, query, register/document posting | YaxUnit unit/integration. If the test already exists, update and rerun it; if there is no test, add one. |
| UI or client context: form, command, button, command interface, client handler, `ОткрытьФорму`, notification, visibility/availability, permissions to open the UI | Scenario test through Vanessa/TestClient: open the user entrypoint, perform the action, and verify the observable result without error. For UI/UX acceptance of a form, plan a PNG screenshot through VA MCP (`connect_test_client -> get_window_list_os -> get_window_screenshot_os`) with a check that the screenshot is not empty/black. Plan the web client only for a browser-specific layer (DOM/CSS/JS console/network/web-auth/viewport/browser extension), explicitly stating which function is missing from VA MCP in principle. For a targeted command, the minimal scenario clicks the command and confirms successful start/completion. |
| A related user process spanning multiple forms/objects | End-to-end process scenario. First reuse the existing scenario and adapt it to the change; write a new scenario only if no existing coverage exists. |
| Integration boundary, HTTP/API, background or scheduled execution | Integration/YaxUnit or a scenario test with a verifiable external/register effect; for background jobs, verify idempotence and rerun behavior if it is relevant to the change. |

Each MUST in the spec must have an explicit trace line in the "Test Plan":
`requirement → affected layer → test type → existing test is updated or a new one is created`.

If a required UI/VA test is technically impossible in the current environment, the spec MUST NOT silently
reduce coverage: apply the `va-visual-check` fallback rules and record the completed VA steps, the fallback reason, and the residual risk. If the fallback does not provide a sufficient signal for the requirement, record a blocker. Reviewer checks not only that tests exist, but also that the test level matches the affected runtime layer.

---

## 5. RFC 2119 Rules

| Keyword | Meaning | Usage rule |
|----------------|----------|------------------------|
| MUST | Required | Without this, the requirement is considered unmet. |
| SHOULD | Strongly recommended | Deviation is allowed only with explicit justification. |
| MAY | Optional | An improvement that does not block acceptance. |
| MUST NOT | Forbidden | An explicit restriction; violation is not allowed. |

Requirements must be:
- atomic (one requirement - one verifiable thought);
- testable (can be confirmed by a test/scenario);
- consistent across sections.

---

## 6. Task Decomposition

For tasks with a specification, decomposition is **mandatory** (a separate JSON file, Task Breakdown). The spec must include a link to the JSON and/or a short summary.

The quality control process is outside this skill: `task-breakdown` (§3 Linear — self-check, §4 Subagent — cross-review).

---

## 7. Specification Quality Criteria

Review checklist:

- [ ] "Context" describes who has the problem and what is not working.
- [ ] Every MUST is covered by an item in the "Test Plan".
- [ ] For each MUST, the affected runtime layer is specified and the appropriate test type is selected:
  server → YaxUnit, UI/client → scenario UI/BDD, process → end-to-end, integration/background → integration/job.
- [ ] "Boundaries" clearly separate "In scope" and "Out of scope".
- [ ] "Considered options" contains at least 2 alternatives.
- [ ] "Chosen solution" includes justification and consequences.
- [ ] "Technical design" separates user tasks (metadata) and agent tasks (code).
- [ ] There are no contradictions between sections.
- [ ] Requirements are expressed with RFC 2119 (MUST/SHOULD/MAY/MUST NOT).
- [ ] There is a link/summary for a separate Task Breakdown JSON.
- [ ] "Acceptance scenarios" contain business-level Gherkin scenarios (Given/When/Then) for MUST requirements.
- [ ] If the change affects the UI/client context, there is a scenario that opens the user entrypoint and performs the changed action.
- [ ] If the change affects a server method/logic, there is YaxUnit coverage: an existing test is updated or a new one is created.
- [ ] The document is written in Russian (except code identifiers).

---

## 8. Common Mistakes

| Error | Consequence |
|--------|-------------|
| Mixing the problem and solution in Context | It is unclear what needs to be fixed |
| Vague requirements without RFC 2119 | The work cannot be accepted unambiguously |
| Empty Out of scope | Scope creep |
| Lack of task decomposition | Weak traceability |
| Contradictions Requirements ↔ Technical Design | Implementation errors |

---
depends_on: []
---
