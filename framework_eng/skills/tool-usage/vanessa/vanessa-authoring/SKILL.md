---
name: vanessa-authoring
description: Creating and refining Vanessa Automation feature scenarios based on real project requirements. Use when you need to write or update a scenario test, not just run it.
---

# Authoring Vanessa Automation Scenarios

## Writing process

1. Identify the requirement source — specification or business case (`vanessa-scenario-policy`).
2. Determine **under which user** the scenario runs (see "User Context").
3. Find suitable steps: first in the Vanessa library, then in the project scenarios.
4. Write a single smoke scenario: open → one action → one observable consequence.
5. If a step is missing — tag it `# unknown_step_candidate`, do not invent a BSL step.
6. Submit the scenario to `vanessa-run` for execution.

---

## Anatomy of a feature file

```gherkin
# language: ru
# encoding: utf-8

@тег-фичи
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

- `Контекст:` runs **before each** scenario in the file.
- Step keywords: `Дано`, `Когда`, `Тогда`, `И`, `Затем` — interchangeable syntactically.
- Strings — in apostrophes or quotes; escape sequences: `\'`, `\"`, `\\`.
- `Структура сценария:` + `Примеры:` — runs the scenario for each row of the parameter table.
- `@tree` in the header — enables Turbo Gherkin: Tab indents define the step tree (spaces are not allowed!).
- `@exportscenarios` — makes the scenario available as a subscenario from another feature file.

---

## User Context

**MUST:** each scenario runs under a specific business user, not under admin/AgentAI. The only exception is when the tested function is available exclusively to an administrator.

**How to determine the user:**
1. Specified in the task description → use it.
2. Not specified → **ask the person** before writing the scenario.

**One user** (in the `Контекст:` section):
```gherkin
Дано Я запускаю тест-клиент для пользователя "SalesManager" с паролем "123" или подключаю уже существующий
```

**Multiple users** (in the scenario body — named TestClient):
```gherkin
И я подключаю TestClient "Менеджер" логин "SalesManager" пароль "123"
И я подключаю TestClient "Руководитель" логин "Director" пароль "456"

И я активизирую TestClient "Менеджер"
# ... шаги от имени менеджера ...

И я активизирую TestClient "Руководитель"
# ... шаги от имени руководителя ...

И я закрываю TestClient "Менеджер"
И я закрываю TestClient "Руководитель"
```

> Password is plain text in the feature file. Test users must have a simple or empty password (`password ""`).

---

## Step discovery

Library: `/opt/onescript/2.0.0/lib/add/features/libraries/`

| Category | Library file |
|-----------|-----------------|
| Interface, fields, buttons, tabs | `UITestRunner/РаботаСИнтерфейсом.feature` |
| Tables (TCh) | `UITestRunner/РаботаСТаблицами.feature` |
| State of form elements | `UITestRunner/СостояниеЭлементаФормы.feature` |
| Flags / switches | `UITestRunner/РаботаСФлагами.feature` |
| Messages to the user | `UITestRunner/РаботаСОкномСообщений.feature` |
| Database data, Справочники | `Данные/ЗапросыКБД.feature` |
| One / multiple TestClient | `UITestRunner/ОткрытьTestClient.feature`, `UITestRunner/ПодключениеНесколькихКлиентовТестирования.feature` |
| Conditions, variables | `Условие/Условие.feature` |
| Pause | `Пауза/СделатьПаузу.feature` |

Cheat sheet for common steps with syntax → `references/steps-cheatsheet.md`.

**Full library:** `references/steps.json` (1116 steps). **Do NOT read it in full** — use `grep` to search for keywords from the task. The structure of each entry:
- `ИмяШага` — sample call with parameters
- `ОписаниеШага` — what the step does
- `ПолныйТипШага` — category (UI, Other, Files, Variables, etc.)

---

## Tags

| Tag | Meaning |
|-----|-------|
| `@draft` / `@Draft@` | Exclude from runs when executing the directory |
| `@manual-data` | Scenario depends on data that is created manually |
| `@regression` | Regression test |
| `@ui` | UI test via TestClient |
| `@tree` | Turbo Gherkin: Tab indents = nesting (spaces prohibited) |
| `@exportscenarios` | Scenario is called as a subscenario from another file |
| `@IgnoreOnXxx` | System: skip in the specified environment |

---

## Anti-patterns

| Anti-pattern | Consequence |
|-------------|-------------|
| Scenario under admin without justification | Does not verify real user rights |
| Step inspects an internal detail (method call, direct DB query) | Fragile: no observable UI behavior |
| Invented step instead of searching the library | Does not resolve during execution |
| Long scenario (7+ actions) | Hard to localize a failure |
| Data setup mixed with verification | Violates the Given/Then separation |

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
