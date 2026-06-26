---
name: vanessa-authoring
description: "Use for authoring and refining Vanessa Automation feature scenarios from real project requirements."
uses_capabilities:
  - run_vanessa
  - build_project
---

# Vanessa Automation Scenario Authoring

## Writing Algorithm

1. Identify the requirement source: a specification or a business case (`vanessa-scenario-policy`).
2. Determine **which user** runs the scenario (see "User Context").
3. Find suitable steps: first in the Vanessa library, then in the project's scenarios.
4. **Inspect the interface and fill out the form manually**. The preferred path is Vanessa Automation MCP tools through `v8-client-session-manager` (see "MCP Research through Vanessa Automation"). If they are unavailable, use the web client (`gui-control` / `screenshot` / `chrome-devtools` snapshot). In both cases, record the exact names and titles of elements, fields, buttons, and tabs **before** referencing them in steps; do not guess identifiers (Title vs name - see `vanessa-scenario-policy`).
5. Write one smoke scenario: open -> one action -> one observable outcome.
6. If a step does not exist, mark `# unknown_step_candidate`; do not invent a BSL step.
7. Submit the scenario for execution through `v8-runner` (`v8-runner test va`).

---

## MCP Research through Vanessa Automation

This section captures the universal workflow verified on Vanessa Automation `1.2.043.28`. For another version, first reconcile behavior with the official VA instruction and live tool schemas.

Official Vanessa Automation source: <https://github.com/Pr-Mex/vanessa-automation>. AI/MCP instructions are in `docs/AI/`. Take VA updates from the official repository/releases, not by editing vendor code in the project. WS startup uses our `v8-runner` fork <https://github.com/SteelMorgan/v8-runner-rust> and `v8-client-session-manager` <https://github.com/SteelMorgan/v8-client-session-manager>.

### Version and readiness checks

1. Start a VA manager session through `v8-runner launch mcp va --mcp-transport ws ...` (the detailed launch shape is in the `v8-runner` skill, section "Vanessa Automation MCP through session-manager").
2. Through `session_list`, wait for a live `kind=vanessa_test_client` session where VA tools appeared: for example `get_VanessaAutomation_state`, `connect_test_client`, `get_form_analysis`, `manage_command_interface`.
3. Call `get_environment_data` or the nearest available VA environment tool and record the Vanessa Automation version in the task context.
4. If service data tools are needed (`get_table_data`, `get_object_attributes`), verify that the VA service extension is loaded into the tested infobase. Having fresh extension files in source is not enough: runtime tools look for forms in the connected database.

### Mandatory operation sequence

1. **Connect the test client.** Before any tools that read or control the tested application's UI, call `connect_test_client` with the test-client profile. Choose the profile from VA settings/profile table; do not guess the name.
2. **Research the form through VA tools.** Use live tool schemas and descriptions from `session_list` / `tools/list`, because the tool set expands between VA versions. Do not freeze a closed list as complete. As of VA `1.2.043.28`, the main tool classes are: command interface, window list, active window data, form analysis, form element actions, object attribute reading, table/data reading, screenshots, user action recording, and `.feature` step execution.
3. **Do not write data without a test purpose.** For filling research you may open a creation form, read attributes, and try navigation; save/post only when needed for validating filling or the scenario, and follow test-data isolation rules.
4. **Close the test client.** After research, execution, or an error, always call `close_test_client` for the connected profile. If the VA manager session was started manually for research, stop it too after the work is done.

Antipatterns:

- calling `get_form_analysis`, `manage_command_interface`, `manage_form_elements`, `get_object_attributes`, screenshot/recording tools before `connect_test_client`;
- treating a tool name in cached `tools/list` as proof of availability - check the live session of the required `kind`;
- leaving the test client open after the operation;
- editing vendor VA/VAExtension code when the issue is version, extension loading, or launch configuration.

### Embedding into scenario authoring

Before writing a `.feature` for a new form, first perform MCP research:

1. Open the section/command through `manage_command_interface` or direct navigation.
2. Get `get_active_window_data` and `get_form_analysis`.
3. For an object form, get `get_object_attributes` in header-attributes and tabular-sections modes.
4. If needed, get reference data through `get_table_data` to choose existing valid values.
5. Based on the result, record exact commands, element names, required fields, conditional visibility/availability, and filling order in the scenario context.
6. Only after that write Gherkin steps and subscenarios.

## Manual Form Filling Before the Scenario (MUST)

> Before writing a NEW scenario for a document, the agent first fills out the form **manually in the web client**, checking the real form structure at each step. The `.feature` file is written only AFTER successful manual filling.

| Requirement | Description |
|-----------|----------|
| Snapshot after each field | After changing **each** field, take the form structure snapshot again (`take_snapshot` / `screenshot`): changing a field affects the visibility, availability, and **requiredness** of other fields (`ПриИзменении` handlers). The full set of required fields is discovered **iteratively**, not guessed in advance |
| Study Help and reference data | Before filling out the form, read the document Help/tooltip and reference data to understand the usage scenarios and filling order. They may be empty, but typical objects often have them populated |
| All key header fields | Fill out fields according to the document's intended purpose (Organization, Counterparty, Agreement, Warehouse, etc. - whatever the document semantics require) |
| Required tabular sections | Fill out required tabular sections (usually goods / according to the document semantics) with **at least several rows**; verify that all row fields are filled in |
| Scrollbars | During visual analysis, remember that the form and tabular sections may have **scrollbars that hide some fields** - scroll to see all elements, not only the visible area |
| Reusable "building blocks" | Package filling scenarios as reusable subscenarios (`@exportscenarios`) so that other tests with the same document are assembled from them like construction blocks. One document may have several filling scenarios |
| Save and post | During a manual run, the document must be **saved and posted** (if the test intent requires it) - make sure the filling really succeeds, not just appears complete |
| Analyze filling errors | Saving/posting may produce errors - **popup messages at the bottom of the screen** (they may have their own scrollbar - scroll and read everything). Analyze each one, adjust the filling, and repeat until the document saves/posts cleanly |
| Order | First successful manual filling with structure verification, **saving and posting** of the document, and resolution of popup errors -> then write the `.feature` file |

---

## Feature File Anatomy

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

- `Context:` runs **before every** scenario in the file.
- Step keywords: `Дано`, `Когда`, `Тогда`, `И`, `Затем` are syntactically interchangeable.
- Strings are enclosed in apostrophes or double quotes; special characters: `\'`, `\"`, `\\`.
- `Scenario structure:` + `Examples:` runs the scenario for each row in the parameter table.
- `@tree` in the header enables Turbo Gherkin: Tab indentation defines the step tree (spaces are not allowed!).
- `@exportscenarios` makes the scenario available as a subscenario from another feature file.

---

## User Context

**MUST:** each scenario runs under a specific business user, not under admin/AgentAI.
Exception - only if the function under test is available exclusively to an administrator.

**How to determine the user:**
1. Specified in the task description -> use that user.
2. Not specified -> **ask the person** before writing the scenario.

**One user** (in the `Context:` section):
```gherkin
Дано Я запускаю тест-клиент для пользователя "SalesManager" с паролем "123" или подключаю уже существующий
```

**Multiple users** (in the scenario body - named TestClient):
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

> The password is plain text in the feature file. Test users should have a simple or empty password (`пароль ""`).

---

## Two-Session Split (MUST)

The `.feature` file is logically split into two parts:

1. **Setup / infrastructure** - runs under the technical user (`AgentAI` in this project): preparing test data (creating documents, catalog items, register records), `VAExtension (Extension)` steps, BSL fixtures from `vanessa-tests/support/`, everything that requires technical roles outside the business user's normal access.
2. **Business flow (verification)** - runs under a specific business user (for example `Gavrilova Natalia` for OC-23400): only steps that verify user behavior under test. The business user **must not receive** technical roles (for example roles from `VAExtension.cfe`) just to make a step pass.

Session switching:
```gherkin
И я закрываю сеанс TESTCLIENT
```
or
```gherkin
И я закрываю TestClient "<имя>"
```
after which a new session opens:
```gherkin
Дано я подключаю TestClient "<роль>" логин "<пользователь>" пароль "<пароль>"
```

**Rationale** (Infostart id=249957, id=249958): if the business flow runs with full rights, the test no longer checks real role restrictions and creates a false sense of correctness. Granting the business user technical roles just to satisfy an infrastructure step is the same antipattern in another form.

**Antipattern:** placing `(Extension)` steps / fixtures into the business user's session and then "fixing" the failure by granting technical roles. Instead, move the step into the setup block under the technical user.

---

## Finding Steps

Library: `/opt/onescript/2.0.0/lib/add/features/libraries/`

| Category | Library file |
|-----------|--------------|
| Interface, fields, buttons, tabs | `UITestRunner/РаботаСИнтерфейсом.feature` |
| Tables (tabular sections) | `UITestRunner/РаботаСТаблицами.feature` |
| Form element state | `UITestRunner/СостояниеЭлементаФормы.feature` |
| Flags / toggles | `UITestRunner/РаботаСФлагами.feature` |
| User messages | `UITestRunner/РаботаСОкномСообщений.feature` |
| Data in DB, catalogs | `Данные/ЗапросыКБД.feature` |
| One / multiple TestClient | `UITestRunner/ОткрытьTestClient.feature`, `UITestRunner/ПодключениеНесколькихКлиентовТестирования.feature` |
| Conditions, variables | `Условие/Условие.feature` |
| Pause | `Пауза/СделатьПаузу.feature` |

Cheat sheet of common steps with syntax -> `references/steps-cheatsheet.md`.

**Full library:** `references/steps.json` (1116 steps). **Do not read it in full** - use `grep` to search by keywords from the task. Structure of each entry:
- `StepName` - example call with parameters
- `StepDescription` - what the step does
- `FullStepType` - category (UI, Misc, Files, Variables, etc.)

---

## Tags

| Tag | Meaning |
|-----|---------|
| `@task-<ID>` | Link to the tracker task (MUST, `vanessa-scenario-policy`) |
| `@draft` / `@Draft@` | Exclude from the run when launching the catalog |
| `@manual-data` | The scenario depends on manually created data |
| `@regression` | Regression test |
| `@ui` | UI test through TestClient |
| `@tree` | Turbo Gherkin: Tab indentation = nesting (spaces are forbidden) |
| `@exportscenarios` | The scenario is invoked as a subscenario from another file |
| `@IgnoreOnXxx` | System tag: skip in the specified environment |

---

## Antipatterns

| Antipattern | Consequence |
|-------------|-------------|
| Scenario under admin without justification | Does not verify real user rights |
| Step checks an internal detail (method call, direct DB query) | Fragile: no observable UI behavior |
| Invented step instead of searching the library | Will not resolve when executed |
| Long scenario (7+ actions) | Hard to localize a failure |
| Data preparation mixed with verification | Breaks Given/Then separation |

---
depends_on:
  - framework/rules/vanessa-scenario-policy/SKILL.md
  - framework/rules/vanessa-test-isolation-policy/SKILL.md
  - framework/rules/vanessa-tests-location/SKILL.md
  - framework/rules/vanessa-run-loop/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
requires:
  - tools
---
