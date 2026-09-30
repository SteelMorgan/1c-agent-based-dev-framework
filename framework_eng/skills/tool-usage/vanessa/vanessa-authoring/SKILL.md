---
name: vanessa-authoring
description: "Vanessa: authoring and refining feature scenarios"
uses_capabilities:
  - run_vanessa
  - build_project
---

# Vanessa Automation scenario authoring

## Writing algorithm

1. Determine the source of the requirement - specification or business case (`vanessa-scenario-policy`).
2. Determine **under which user** the scenario is executed (see "User Context").
3. Find suitable steps: first in the Vanessa library, then in the project scenarios.
4. **Inspect the interface and fill out the form manually**. The preferred path is the Vanessa Automation MCP tools through `v8-session-manager` (see "MCP Investigation through Vanessa Automation"). For UI/UX form checks, use `va-visual-check`: VA MCP screenshot route, Linux/Xvfb recipe, and browser fallback with reason logging. In any case, record the exact names and captions of elements, fields, buttons, and tabs **before** referencing them in steps; do not guess identifiers (Title vs name - see `vanessa-scenario-policy`).
5. Write one smoke scenario: open → one action → one observable outcome.
6. If a step does not exist - mark `# unknown_step_candidate`, do not invent a BSL step.
7. Pass the scenario for execution through `v8-runner` (`v8-runner test va`).

---

## MCP Investigation through Vanessa Automation

This section captures the universal workflow validated on Vanessa Automation `1.2.043.28`. For another version, first verify the behavior against the official VA instructions and live tool schemas.

Official Vanessa Automation source: <https://github.com/Pr-Mex/vanessa-automation>. AI/MCP instructions are in `docs/AI/`. Take VA updates from the official repository/releases, not by modifying vendor code in the project. For WS launch, we use our fork `v8-runner` <https://github.com/SteelMorgan/v8-runner-rust> and `v8-session-manager` <https://github.com/1c-neurofish/v8-session-manager>.

### Version and readiness check

1. If the VA manager session is not already running, start it strictly through the `v8-runner` skill (section "Vanessa Automation MCP via session-manager"); do not assemble the launch string in this skill.
2. Through `session_list`, wait for a live session of `kind=vanessa_test_client`, where VA tools appear: for example `get_VanessaAutomation_state`, `connect_test_client`, `get_form_analysis`, `manage_command_interface`.
3. Call `get_environment_data` or the nearest available VA environment tool and record the Vanessa Automation version in the task context.
4. If service data tools are needed (`get_table_data`, `get_object_attributes`), verify that the VA service extension is loaded into the test database. The presence of fresh extension files in source is not enough: runtime tools look for forms in the connected database.

### Required workflow sequence

1. **Connect the test client.** Before any tools that read or control the interface of the tested application, call `connect_test_client` with the test client profile. Choose the profile from VA settings/the `ДанныеКлиентовТестирования` profile table in VAParams; do not guess the name. How to form `tools.va` / `tests.va` in `v8project.yaml` and the TestClient profile inside VAParams is described in `v8-runner`, `references/config-and-backends.md`.
2. **Inspect the form through VA tools.** Use the live tool schemas and their descriptions from `session_list` / `tools/list`, because the tool set expands between VA versions. Do not treat a closed list as complete. As of VA `1.2.043.28`, the main tool classes are: command interface, window list, active window data, form analysis, form element actions, object attribute reading, table/data reading, screenshots, user action recording, execution of `.feature` steps.
3. **Take a visual control screenshot.** For any UI/UX check, after opening the required form, use `va-visual-check`: first VA MCP PNG, then if needed the Linux/Xvfb recipe or browser fallback with reason logging.
4. **Do not write data without a test purpose.** For form fill-in research, you can open the creation form, read attributes, and try navigation; write/post only if it is required to verify fill-in or the scenario, and follow test data isolation rules.
5. **Close the test client.** After the investigation, execution, or an error, call `close_test_client` for the connected profile. If the VA manager session was launched manually for investigation, stop it after you finish.

Anti-patterns:

- call `get_form_analysis`, `manage_command_interface`, `manage_form_elements`, `get_object_attributes`, screenshot/recording tools before `connect_test_client`;
- treat the internal `get_window_list_testclient` window list as a visual screenshot: it is needed for structure and navigation, while UI/UX acceptance requires PNG according to `va-visual-check` rules;
- treat the presence of a tool name in cached `tools/list` as proof of availability - check the live session of the required `kind`;
- keep the test client open after the operation is complete;
- modify VA/VAExtension vendor code when the problem is the version, extension loading, or launch configuration.

### Integration into scenario authoring

Before writing a `.feature` for a new form, first perform MCP investigation:

1. Open the section/command through `manage_command_interface` or direct navigation.
2. Get `get_active_window_data` and `get_form_analysis`.
3. Create a visual PNG of the form using `va-visual-check` and verify it against `form-visual-requirements`.
4. For the object form, get `get_object_attributes` in header attribute mode and tabular section mode.
5. If needed, get reference data through `get_table_data` to choose existing valid values.
6. Based on the results, record the exact commands, element names, required fields, conditional visibility/availability, visual notes, and fill order in the scenario context.
7. Only after that write the Gherkin steps and subscenarios.

## Manual form fill-in before the scenario (MUST)

> Before writing a NEW scenario for a document, the agent first fills out the form **through Vanessa/TestClient**, checking the real form composition at each step. The platform TestClient MCP is allowed only for an action that VA MCP fundamentally does not provide, with the reason recorded in the context. The `.feature` is written only AFTER successful fill-in through VA/TestClient. Web client is allowed only for browser functions that VA MCP fundamentally does not support.

| Requirement | Description |
|-----------|----------|
| Snapshot after each field | After changing **each** field, re-read the form composition (`get_form_analysis`, `get_active_window_data`, element/table reading): the field value changes visibility, availability, and **requiredness** of other fields (`ПриИзменении` handlers). Take the visual PNG according to `va-visual-check` at key form states and always for final UI/UX acceptance. The full set of required fields is discovered **iteratively**, not guessed in advance |
| Study the Help and reference data | Before filling in, read the document Help/tooltip and reference data - understand the work scenarios and fill order. They may be empty, but typical objects often have them filled |
| All key header fields | Fill them based on the semantic purpose of the document (Organization, Counterparty, Agreement, Warehouse, etc. - what the document meaning requires) |
| Required tabular sections | Fill required TTs (usually goods / as implied by the document) **with at least several rows**; verify that all row fields are filled |
| Scrollbars | During visual analysis remember: the form and TTs may have **scrollbars hiding part of the fields** - scroll to see all elements, not only the visible area |
| Reusable "building blocks" | Format fill-in scenarios as reusable subscenarios (`@exportscenarios`) so that other tests with the same document can be assembled from them like construction blocks. One document can have several fill-in scenarios |
| Writing and posting | During manual execution, the document must be **saved and posted** (if that is required by the test meaning) - make sure the fill-in really passes, not just looks complete |
| Fill-in error analysis | Saving/posting may produce errors - **popup messages at the bottom of the screen** (they may have their own scrollbar - scroll and read everything). Analyze each one, correct the fill-in, and repeat until the document is saved/posted cleanly |
| Order | First successful manual fill-in with composition verification, **saving and posting** of the document and elimination of popup errors -> then write `.feature` |

---

## Feature file anatomy

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

- `Context:` runs **before each** scenario in the file.
- Step keywords: `Given`, `When`, `Then`, `And`, `Then` - are syntactically interchangeable.
- Strings are in apostrophes or double quotes; special characters: `\'`, `\"`, `\\`.
- `Scenario structure:` + `Examples:` - runs the scenario for each row of the parameter table.
- `@tree` in the header - enables Turbo Gherkin: Tab indentation defines the step tree (spaces are not allowed!).
- `@exportscenarios` - makes the scenario available as a subscenario from another feature file.

---

## User Context

**MUST:** each scenario is executed under a specific business user, not under admin/AgentAI.
Exception - only if the function being checked is available exclusively to an administrator.

**How to determine the user:**
1. Specified in the task description → use it.
2. Not specified -> **ask the human** before writing the scenario.

**One user** (in the `Context:` section):
```gherkin
Дано Я запускаю тест-клиент для пользователя "SalesManager" с паролем "123" или подключаю уже существующий
```

**Several users** (in the scenario body - named TestClients):
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

> The password is plain text in the feature file. Test users must have a simple or empty password (`password ""`).

---

## Two-session split (MUST)

The `.feature` file is logically split into two parts:

1. **Setup / infrastructure** - executed under the technical user (`AgentAI` in this project): preparation of test data (creating documents, catalog items, register entries), `VAExtension (Extension)` steps, BSL fixtures from `vanessa-tests/support/`, everything that requires technical roles outside normal business-user access.
2. **Business flow (verification)** - executed under a specific business user (for example `Gavrilova Natalia` for OC-23400): only steps that verify user behavior under test. The business user **MUST NOT receive** technical roles (for example roles from `VAExtension.cfe`) just to make a step pass.

Session switching:
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

**Rationale** (Infostart id=249957, id=249958): if the business flow is executed with full rights, the test stops checking real role restrictions and creates a false sense of correctness. Granting the business user technical roles just to satisfy an infrastructure step is the same anti-pattern in another form.

**Anti-pattern:** place `(Extension)` steps / fixtures in the business-user session and then "fix" the failure by granting technical roles. Instead, move the step into the setup block under the technical user.

---

## Step search

Library: `/opt/onescript/2.0.0/lib/add/features/libraries/`

| Category | Library file |
|-----------|-----------------|
| Interface, fields, buttons, tabs | `UITestRunner/РаботаСИнтерфейсом.feature` |
| Tables (TTs) | `UITestRunner/РаботаСТаблицами.feature` |
| Form element state | `UITestRunner/СостояниеЭлементаФормы.feature` |
| Flags / switches | `UITestRunner/РаботаСФлагами.feature` |
| User messages | `UITestRunner/РаботаСОкномСообщений.feature` |
| Data in DB, catalogs | `Данные/ЗапросыКБД.feature` |
| One / multiple TestClient | `UITestRunner/ОткрытьTestClient.feature`, `UITestRunner/ПодключениеНесколькихКлиентовТестирования.feature` |
| Conditions, variables | `Условие/Условие.feature` |
| Pause | `Пауза/СделатьПаузу.feature` |

Cheat sheet of common steps with syntax → `references/steps-cheatsheet.md`.

**Full library:** `references/steps.json` (1116 steps). **Do not read it in full** - use `grep` to search by keywords from the task. Structure of each record:
- `ИмяШага` - example call with parameters
- `ОписаниеШага` - what the step does
- `ПолныйТипШага` - category (UI, Other, Files, Variables, etc.)

---

## Tags

| Tag | Meaning |
|-----|-------|
| `@task-<ID>` | Link to the tracker task (MUST, `vanessa-scenario-policy`) |
| `@draft` / `@Draft@` | Exclude from execution when running the directory |
| `@manual-data` | The scenario depends on data created manually |
| `@regression` | Regression test |
| `@ui` | UI test through TestClient |
| `@tree` | Turbo Gherkin: Tab indentation = nesting (spaces are forbidden) |
| `@exportscenarios` | The scenario is called as a subscenario from another file |
| `@IgnoreOnXxx` | System: skip in the specified environment |

---

## Anti-patterns

| Anti-pattern | Consequence |
|-------------|-------------|
| Scenario under admin without justification | Does not verify real user rights |
| Step checks an internal detail (method call, direct DB query) | Fragile: no observable UI behavior |
| Invented step instead of searching the library | Does not resolve at runtime |
| Long scenario (7+ actions) | Hard to localize the failure |
| Data preparation mixed with verification | Violates Given/Then separation |

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
