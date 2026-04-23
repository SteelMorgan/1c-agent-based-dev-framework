---
name: vanessa-authoring
description: "Creating and refining Vanessa Automation feature scenarios from real project requirements. Use when you need to write or update a scenario test, not just run it."
---

# Authoring Vanessa Automation Scenarios

## Writing Algorithm

1. Determine the source of the requirement: a specification or business case (`vanessa-scenario-policy`).
2. Determine **which user** the scenario runs under (see "User Context").
3. Find suitable steps: first in the Vanessa library, then in the project scenarios.
4. Write one smoke scenario: open -> one action -> one observable outcome.
5. If the step does not exist, mark `# unknown_step_candidate`; do not invent a BSL step.
6. Pass the scenario to `vanessa-run` for execution.

---

## Anatomy of a feature file

```gherkin
# language: ru
# encoding: utf-8
# Задача: task-103 — Оформление заказа клиента через портфель

@task-103 @тег-фичи
Функциональность: Краткое название

Как <роль пользователя>
Я хочу <что сделать>
Чтобы <бизнес-польза>

Контекст:
    Дано Я запускаю тест-клиент для пользователя "Логин" с паролем "Пароль" или подключаю уже существующий

Сценарий: Название сценария
    Когда <действие>
    И    <следующее действие>
    Тогда <ожидаемый результат>
```

- `Context:` is executed **before each** scenario in the file.
- Step keywords: `Given`, `When`, `Then`, `And`, `Next` are syntactically interchangeable.
- Strings use apostrophes or quotes; special characters: `\'`, `\"`, `\\`.
- `Scenario structure:` + `Examples:` runs the scenario for each row in the parameter table.
- `@tree` in the header enables Turbo Gherkin: Tab indentation defines the step tree (spaces are not allowed!).
- `@exportscenarios` makes the scenario available as a subscenario from another feature file.

---

## User Context

**MUST:** every scenario runs under a specific business user, not under admin/AgentAI.
The only exception is when the function being checked is available exclusively to an administrator.

**How to determine the user:**
1. Specified in the task description -> use that user.
2. Not specified -> **ask a person** before writing the scenario.

**One user** (in the `Context:` section):
```gherkin
Дано Я запускаю тест-клиент для пользователя "SalesManager" с паролем "123" или подключаю уже существующий
```

**Multiple users** (in the scenario body - named TestClient instances):
```gherkin
И я подключаю TestClient "Менеджер" логин "SalesManager" пароль "123"
И я подключаю TestClient "Руководитель" логин "Director" пароль "456"

И я активизирую TestClient "Менеджер"
# ... steps on behalf of the manager ...

И я активизирую TestClient "Руководитель"
# ... steps on behalf of the director ...

И я закрываю TestClient "Менеджер"
И я закрываю TestClient "Руководитель"
```

> Password is plain text in the feature file. Test users must have a simple or empty password (`password ""`).

---

## Step Search

Library: `/opt/onescript/2.0.0/lib/add/features/libraries/`

| Category | Library file |
|-----------|--------------|
| Interface, fields, buttons, tabs | `UITestRunner/РаботаСИнтерфейсом.feature` |
| Tables (tabular sections) | `UITestRunner/РаботаСТаблицами.feature` |
| Form element state | `UITestRunner/СостояниеЭлементаФормы.feature` |
| Flags / switches | `UITestRunner/РаботаСФлагами.feature` |
| User messages | `UITestRunner/РаботаСОкномСообщений.feature` |
| Data in DB, catalogs | `Данные/ЗапросыКБД.feature` |
| One / multiple TestClient | `UITestRunner/ОткрытьTestClient.feature`, `UITestRunner/ПодключениеНесколькихКлиентовТестирования.feature` |
| Conditions, variables | `Условие/Условие.feature` |
| Pause | `Пауза/СделатьПаузу.feature` |

Cheat sheet for common steps with syntax -> `references/steps-cheatsheet.md`.

**Full library:** `references/steps.json` (1116 steps). **Do not read it in full** - use `grep` to search by keywords from the task. Structure of each record:
- `ИмяШага` - example invocation with parameters
- `ОписаниеШага` - what the step does
- `ПолныйТипШага` - category (UI, Misc, Files, Variables, etc.)

---

## Tags

| Tag | Meaning |
|-----|---------|
| `@task-<ID>` | Link to the tracker task (MUST, `vanessa-scenario-policy`) |
| `@draft` / `@Draft@` | Exclude from execution when running the catalog |
| `@manual-data` | Scenario depends on data created manually |
| `@regression` | Regression test |
| `@ui` | UI test through TestClient |
| `@tree` | Turbo Gherkin: Tab indentation = nesting (spaces are forbidden) |
| `@exportscenarios` | Scenario is called as a subscenario from another file |
| `@IgnoreOnXxx` | System: skip in the specified environment |

---

## Anti-patterns

| Anti-pattern | Consequence |
|--------------|-------------|
| Scenario under admin without justification | Does not verify real user permissions |
| Step checks an internal detail (method call, direct DB query) | Fragile: no observable UI behavior |
| Invented step instead of searching the library | Will not resolve at runtime |
| Long scenario (7+ actions) | Hard to localize the failure |
| Data preparation is mixed with verification | Breaks the Given/Then separation |

---
depends_on:
  - framework/rules/vanessa-scenario-policy.mdc
  - framework/rules/vanessa-test-isolation-policy.mdc
  - framework/rules/vanessa-tests-location.mdc
  - framework/rules/vanessa-run-loop.mdc
  - framework/skills/tool-usage/vanessa/vanessa-run/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
  - framework/skills/tool-usage/forms/form-element-mapping/SKILL.md
requires:
  - tools
---
