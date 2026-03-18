---
name: vanessa-authoring
description: Creating and refining Vanessa Automation feature scenarios based on actual project requirements. Use when you need to write or update a scenario test rather than just run it.
---

# Vanessa Automation Scenario Authoring

## Algorithm

1. Identify the requirement source — specification or business case (`vanessa-scenario-policy`).
2. Determine **which user** the scenario runs under (see "User context").
3. Find matching steps: Vanessa library first, then project scenarios.
4. Write a single smoke scenario: open → one action → one observable consequence.
5. If a step is missing — mark `# unknown_step_candidate`, do not invent a BSL step.
6. Hand the scenario to `vanessa-run` for execution.

---

## Feature file anatomy

```gherkin
# language: ru
# encoding: utf-8

@feature-tag
Функциональность: Short name

Как <user role>
Я хочу <what to do>
Чтобы <business value>

Контекст:
    Дано Я запускаю тест-клиент для пользователя "Login" с паролем "Password" или подключаю уже существующий

Сценарий: Scenario name
    Когда <action>
    И    <next action>
    Тогда <expected result>
```

- `Контекст:` runs **before each** scenario in the file.
- Step keywords: `Дано`, `Когда`, `Тогда`, `И`, `Затем` — syntactically interchangeable.
- Strings — in apostrophes or quotes; escape: `\'`, `\"`, `\\`.
- `Структура сценария:` + `Примеры:` — runs the scenario for each row of the parameters table.
- `@tree` in the header — enables Turbo Gherkin: Tab indentation = step nesting (spaces forbidden!).
- `@exportscenarios` — makes a scenario callable as a subscenario from another feature file.

---

## User context

**MUST:** every scenario runs under a specific business user, not admin/AgentAI.
Exception — only if the tested function is exclusively available to an administrator.

**How to determine the user:**
1. Specified in the task description → use it.
2. Not specified → **ask the human** before writing the scenario.

**Single user** (in `Контекст:`):
```gherkin
Дано Я запускаю тест-клиент для пользователя "SalesManager" с паролем "123" или подключаю уже существующий
```

**Multiple users** (in the scenario body — named TestClients):
```gherkin
И я подключаю TestClient "Manager" логин "SalesManager" пароль "123"
И я подключаю TestClient "Director" логин "Director" пароль "456"

И я активизирую TestClient "Manager"
# ... steps as manager ...

И я активизирую TestClient "Director"
# ... steps as director ...

И я закрываю TestClient "Manager"
И я закрываю TestClient "Director"
```

> Password is plain text in the feature file. Test users must have a simple or empty password (`пароль ""`).

---

## Finding steps

Library: `/opt/onescript/2.0.0/lib/add/features/libraries/`

| Category | Library file |
|----------|-------------|
| Interface, fields, buttons, tabs | `UITestRunner/РаботаСИнтерфейсом.feature` |
| Tables (tabular sections) | `UITestRunner/РаботаСТаблицами.feature` |
| Form element state | `UITestRunner/СостояниеЭлементаФормы.feature` |
| Checkboxes / toggles | `UITestRunner/РаботаСФлагами.feature` |
| User messages | `UITestRunner/РаботаСОкномСообщений.feature` |
| DB data, directories | `Данные/ЗапросыКБД.feature` |
| Single / multiple TestClient | `UITestRunner/ОткрытьTestClient.feature`, `UITestRunner/ПодключениеНесколькихКлиентовТестирования.feature` |
| Conditions, variables | `Условие/Условие.feature` |
| Pause | `Пауза/СделатьПаузу.feature` |

Step cheatsheet with syntax → `references/steps-cheatsheet.md`.

**Full step library:** `references/steps.json` (1116 steps). **Do not read the entire file** — use `grep` to search by keywords from the task. Each entry structure:
- `ИмяШага` — example call with parameters
- `ОписаниеШага` — what the step does
- `ПолныйТипШага` — category (UI, Misc, Files, Variables, etc.)

---

## Tags

| Tag | Meaning |
|-----|---------|
| `@draft` / `@Draft@` | Exclude from directory run |
| `@manual-data` | Scenario depends on manually created data |
| `@regression` | Regression test |
| `@ui` | UI test via TestClient |
| `@tree` | Turbo Gherkin: Tab indentation = nesting (spaces forbidden) |
| `@exportscenarios` | Scenario is callable as a subscenario from another file |
| `@IgnoreOnXxx` | System: skip in the specified environment |

---

## Anti-patterns

| Anti-pattern | Consequence |
|-------------|-------------|
| Scenario under admin without justification | Does not verify real user permissions |
| Step checks internal detail (method call, direct DB query) | Fragile: no observable UI behavior |
| Invented step instead of library search | Not resolved at runtime |
| Long scenario (7+ actions) | Hard to localize the failure |
| Data setup mixed with verification | Violates Given/Then separation |

---
depends_on:
  - framework/rules/vanessa-scenario-policy.mdc
  - framework/rules/vanessa-tests-location.mdc
  - framework/rules/vanessa-run-loop.mdc
  - framework/skills/tool-usage/vanessa/vanessa-run/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
requires:
  - tools
---
