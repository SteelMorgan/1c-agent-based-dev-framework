---
name: vanessa-authoring
description: "Use for creating and refining Vanessa Automation feature scenarios based on real project requirements."
uses_capabilities:
  - run_vanessa
  - build_project
---

# Vanessa Automation scenario authoring

## Writing algorithm

1. Determine the requirement source - a specification or a business case (`vanessa-scenario-policy`).
2. Determine **which user** runs the scenario (see "User context").
3. Find suitable steps: first in the Vanessa library, then in the project scenarios.
4. **Inspect the interface and fill out the form manually in the web client** (`gui-control` / `screenshot` / `chrome-devtools` snapshot) - following the section "Manual form filling before the scenario": record the exact element names and captions, fields, buttons, and tabs **before** referencing them in steps; do not guess identifiers (Title caption vs name - see `vanessa-scenario-policy`).
5. Write one smoke scenario: open -> one action -> one observable result.
6. If a step does not exist, mark `# unknown_step_candidate`, do not invent a BSL step.
7. Pass the scenario for execution through `v8-runner` (`v8-runner test va`).

---

## Manual form filling before the scenario (MUST)

> Before writing a NEW scenario from a document, the agent first fills out the form **manually in the web client**, checking the real form composition at each step. The `.feature` file is written only AFTER successful manual filling.

| Requirement | Description |
|-----------|----------|
| Snapshot after each field | After changing **each** field, take the form composition snapshot again (`take_snapshot` / `screenshot`): a field value changes the visibility, availability, and **requiredness** of other fields (`ПриИзменении` handlers). The full set of required fields is discovered **iteratively**, not guessed in advance |
| Study the Help and reference data | Before filling, read the document Help/tooltip and the reference data - understand the usage scenarios and filling order. They may be empty, but typical objects often have them populated |
| All key header fields | Fill them based on a semantic assessment of the document purpose (Organization, Counterparty, Agreement, Warehouse, etc. - whatever the document semantics require) |
| Required tabular sections | Fill required tabular sections (usually items / according to document semantics) **with at least a few rows**; verify that all row fields are filled |
| Scrollbars | During visual analysis, remember that the form and tabular sections may have **scrollbars that hide some fields** - scroll to see all elements, not just the visible area |
| Reusable building blocks | Format filling scenarios as reusable subscenarios (`@exportscenarios`), so other tests with the same document can be assembled from them like building blocks. One document may have several filling scenarios |
| Save and post | During a manual run, **save and post** the document (if the test meaning requires it) - make sure the filling actually succeeds, not just appears complete |
| Analyzing filling errors | Save/post can produce errors - **popup messages in the bottom part of the screen** (they may have their own scrollbar - scroll and read everything). Analyze each one, adjust the filling, and repeat until the document saves/posts cleanly |
| Order | First successful manual filling with composition verification, **saving and posting** the document and eliminating popup errors -> then write the `.feature` file |

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
- Step keywords such as `Given`, `When`, `Then`, and `And` are syntactically interchangeable.
- Strings are in apostrophes or quotes; special characters: `\'`, `\"`, `\\`.
- `Scenario structure:` + `Examples:` - runs the scenario for each row of the parameter table.
- `@tree` in the header - enables Turbo Gherkin: Tab indentation defines a tree of steps (spaces are not allowed!).
- `@exportscenarios` - makes the scenario available as a subscenario from another feature file.

---

## User context

**MUST:** each scenario runs under a specific business user, not under admin/AgentAI.
The only exception is when the function under test is available exclusively to an administrator.

**How to determine the user:**
1. Specified in the task description - use it.
2. Not specified - **ask the person** before writing the scenario.

**One user** (in the `Context:` section):
```gherkin
Дано Я запускаю тест-клиент для пользователя "SalesManager" с паролем "123" или подключаю уже существующий
```

**Multiple users** (in the scenario body - named TestClient):
```gherkin
И я подключаю TestClient "Менеджер" логин "SalesManager" пароль "123"
И я подключаю TestClient "Руководитель" логин "Director" пароль "456"

И я активизирую TestClient "Менеджер"
# ... steps in the manager's name ...

И я активизирую TestClient "Руководитель"
# ... steps in the director's name ...

И я закрываю TestClient "Менеджер"
И я закрываю TestClient "Руководитель"
```

> Password is plain text in the feature file. Test users must have a simple or empty password (`password ""`).

---

## Two-session split (MUST)

The `.feature` file is logically divided into two parts:

1. **Setup / infrastructure** - runs under a technical user (`AgentAI` in this project): preparing test data (creating documents, catalog items, register records), `VAExtension` (`Extension`) steps, BSL fixtures from `vanessa-tests/support/`, and everything that requires technical roles outside normal business-user access.
2. **Business flow (verification)** - runs under a specific business user (for example `Gavrilova Natalia` for OC-23400): only steps that verify user behavior under test. The business user **must not receive** technical roles (for example roles from `VAExtension.cfe`) just to make a step pass.

Session switch:
```gherkin
И я закрываю сеанс TESTCLIENT
```
or
```gherkin
И я закрываю TestClient "<имя>"
```
after which a new session is opened:
```gherkin
Дано я подключаю TestClient "<роль>" логин "<пользователь>" пароль "<пароль>"
```

**Rationale** (Infostart id=249957, id=249958): if the business flow runs with full rights, the test stops checking real role restrictions and gives a false sense of correctness. Granting technical roles to a business user just to satisfy an infrastructure step is the same antipattern in another form.

**Antipattern:** placing `(Extension)` / fixture steps into the business-user session and then "fixing" the failure by granting that user technical roles. Instead, move the step into the setup block under the technical user.

---

## Step search

Library: `/opt/onescript/2.0.0/lib/add/features/libraries/`

| Category | Library file |
|-----------|--------------|
| Interface, fields, buttons, tabs | `UITestRunner/РаботаСИнтерфейсом.feature` |
| Tables (tabular sections) | `UITestRunner/РаботаСТаблицами.feature` |
| Form element state | `UITestRunner/СостояниеЭлементаФормы.feature` |
| Flags / switches | `UITestRunner/РаботаСФлагами.feature` |
| User messages | `UITestRunner/РаботаСОкномСообщений.feature` |
| Data in the DB, catalogs | `Данные/ЗапросыКБД.feature` |
| One / multiple TestClient | `UITestRunner/ОткрытьTestClient.feature`, `UITestRunner/ПодключениеНесколькихКлиентовТестирования.feature` |
| Conditions, variables | `Условие/Условие.feature` |
| Pause | `Пауза/СделатьПаузу.feature` |

Cheat sheet of common steps with syntax -> `references/steps-cheatsheet.md`.

**Full library:** `references/steps.json` (1116 steps). **Do not read it in full** - use `grep` to search by keywords from the task. The structure of each entry:
- `StepName` - sample call with parameters
- `StepDescription` - what the step does
- `FullStepType` - category (UI, Miscellaneous, Files, Variables, etc.)

---

## Tags

| Tag | Meaning |
|-----|---------|
| `@task-<ID>` | Link to the tracker task (MUST, `vanessa-scenario-policy`) |
| `@draft` / `@Draft@` | Exclude from execution when running the catalog |
| `@manual-data` | The scenario depends on data created manually |
| `@regression` | Regression test |
| `@ui` | UI test through TestClient |
| `@tree` | Turbo Gherkin: Tab indentation = nesting (spaces are forbidden) |
| `@exportscenarios` | The scenario is called as a subscenario from another file |
| `@IgnoreOnXxx` | System: skip in the specified environment |

---

## Antipatterns

| Antipattern | Consequence |
|-------------|-------------|
| Scenario under admin without justification | Does not check real user permissions |
| The step checks an internal detail (method call, direct DB query) | Fragile: there is no observable UI behavior |
| An invented step instead of searching the library | Does not resolve at runtime |
| Long scenario (7+ actions) | Hard to localize the failure |
| Data preparation is mixed with verification | Breaks the Given/Then separation |

---
depends_on:
  - framework/rules/vanessa-scenario-policy.mdc
  - framework/rules/vanessa-test-isolation-policy.mdc
  - framework/rules/vanessa-tests-location.mdc
  - framework/rules/vanessa-run-loop.mdc
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
requires:
  - tools
---
