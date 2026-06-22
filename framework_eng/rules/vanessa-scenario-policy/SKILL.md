---
name: vanessa-scenario-policy
description: You are writing / updating a Vanessa feature file -> apply the vanessa-authoring skill.
alwaysApply: true
---

# Vanessa Automation Scenario Policy

> **Trigger:** creating or modifying a `.feature` file. When triggered, apply the `vanessa-authoring` skill (`framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md`).

## Test Layer Purpose (MUST - fundamental)

> A scenario (Vanessa) test exists to verify **client-side behavior through the UI, simulating user actions**. This is its direct purpose. Server-side business logic belongs to **unit tests** (YaxUnit). Do not mix layers: a server test wrapped in Vanessa does not verify what scenario tests are meant for.

| What we verify | Which test |
|---------------|--------------|
| Server/business logic: fill validation (`ОбработкаПроверкиЗаполнения`), posting, calculations, queries, registers, regulations | **Unit (YaxUnit)** |
| Client/UI behavior: opening a form, `ПриСозданииНаСервере`/`ПриОткрытии`, visibility/availability/requiredness of elements, reaction to input (`ПриИзменении`), form commands, navigation, input based on another object | **Scenario (Vanessa) through the UI** |

- **Verified behavior goes ONLY through the UI.** The scenario opens the form and simulates the user with real UI steps (`window opens`, `in the field named ... I enter`, `I click the ... button`, `I see the element`, `element ... is available`). It is **FORBIDDEN** to replace the user action path with a server call (`CheckFill()`, `WriteObject()`, direct call to a common module) - such a "scenario" is a unit test in a Vanessa costume and misses form-layer defects.
- **Server-side BSL in Vanessa is allowed ONLY for data preparation/cleanup** (fixtures, setup, teardown - see the two-session split below), NOT as a substitute for the verified user scenario. Boundary: code "sets the stage" - server-side is allowed; code "verifies behavior" - only through the UI.
- **Why this rule exists.** If a scenario calls server logic instead of opening the form, **all client logic remains completely untested** - it is not covered by the scenario (it bypassed the UI), nor by a unit test (that is not its layer). The following go unverified: client handlers (`&НаКлиенте`, `ПриОткрытии`, `ПриИзменении`, command handlers), form client-server calls (`ПриСозданииНаСервере` and others), conditional formatting, visibility/availability/requiredness of elements, reaction to input and navigation, the structure of the form itself. Result - server validation is green, but the form crashes at runtime (broken form structure, unhandled event, incorrect visibility - for example "Object field not found"), and this only surfaces for the user. Only a scenario that really opens the form and simulates the user checks client logic (`create -> window opens -> I see the element ... / enter / click`).

## MUST

- The scenario is based on the task specification or an existing business case - no invented cases.
- One scenario = one observable behavior.
- Before a new step, look for an existing one in the Vanessa library and project scenarios.
- The first scenario for a new case is a short smoke test.
- The scenario runs under a specific **business user**, not under admin/AgentAI - the only exception is if the function being checked is available exclusively to an administrator; the user is determined from the task description, and if absent - **ask a person**.
- **Two-session split:** infrastructure data preparation (creating objects, VAExtension steps, BSL fixtures) - under the technical user (AgentAI); business flow (behavior verification) - under the specific business user. Switch via `And I close TestClient` + new `Given I connect TestClient`. You cannot assign technical roles to a business user just to pass infrastructure steps.
- **Task tag is mandatory.** Each `.feature` file MUST contain the `@task-<ID>` tag (for example `@task-103`) at the `Feature:` level.
- **Source comment.** At the top of the file (before the tags) MUST be a comment: `# Task: <ID> - <title>`.
- Do not guess logic - read the code (delegate to Explorer / `code-navigation`). A discrepancy between code and test is a discovered mismatch; record it as a result.
- **Do not guess the interface** - names and titles of elements, fields, buttons, tabs, availability and state of elements are taken from the **real rendered interface**, examined through the web client (`gui-control` / `screenshot` / `chrome-devtools` snapshot), not guessed from code or memory. Remember the difference in identifiers: the steps "contains strings" / "go to line" expect the **title**, while "remember the field value" expects the **name** - the exact value is learned by inspecting the form, not guessed. A discrepancy between the scenario and the real UI is a discovered mismatch; record it as a result.
- **Manual fill-in before the scenario.** A new document scenario is written AFTER manually filling the form in the web client and checking the form composition **at every step** (the field value affects visibility/requiredness of other fields); the key header fields and required tabular sections (≥ several rows) are filled; the document's hint/reference data have been studied; scrollbars hiding fields have been taken into account. If needed by the test meaning, the document is **saved and posted**, and pop-up errors at the bottom of the screen are analyzed and the filling corrected. Fill-in scenarios are reusable "building blocks" (`@exportscenarios`), and one document can have several. Details - `vanessa-authoring`.

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
  - framework/rules/vanessa-test-isolation-policy/SKILL.md
---
