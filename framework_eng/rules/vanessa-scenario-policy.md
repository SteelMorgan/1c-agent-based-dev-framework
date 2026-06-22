---
name: vanessa-scenario-policy
description: You are writing/updating a Vanessa feature file -> apply the vanessa-authoring skill.
alwaysApply: true
---

# Vanessa Scenario Policy

> **Trigger:** creating or modifying a `.feature` file. When triggered, apply the `vanessa-authoring` skill (`framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md`).

## Purpose of Testing Layers (MUST - fundamental)

> A scenario (Vanessa) test exists to verify **client behavior through the UI by simulating user actions**. That is its direct purpose. Server-side business logic is the domain of **unit tests** (YaxUnit). Do not mix the layers: a server-side test wrapped in Vanessa does not verify what scenario tests are meant to verify.

| What we verify | Which test |
|---------------|--------------|
| Server-side/business logic: validation checks (`ОбработкаПроверкиЗаполнения`), posting, calculations, queries, registers, scheduled jobs | **Unit (YaxUnit)** |
| Client/UI behavior: opening a form, `ПриСозданииНаСервере`/`ПриОткрытии`, visibility/accessibility/requiredness of elements, reaction to input (`ПриИзменении`), form commands, navigation, create based on | **Scenario (Vanessa) through the UI** |

- **Verified behavior goes ONLY through the UI.** The scenario opens the form and simulates the user with real UI steps (`window opened`, `in the field named ... I enter`, `I click the ... button`, `I see the element`, `element ... is available`). **It is FORBIDDEN** to replace the user action path with a server call (`ПроверитьЗаполнение()`, `ЗаписатьОбъект()`, direct call to a common module) - such a "scenario" is a unit test in Vanessa clothing and misses form-layer defects.
- **Server-side BSL in Vanessa is allowed ONLY for data preparation/cleanup** (fixtures, setup, teardown - see the two-session split below), NOT as a replacement for the verified user scenario. Boundary: code that "prepares the stage" - server-side is fine; code that "verifies behavior" - only through the UI.
- **Why the rule exists.** If a scenario calls server-side logic instead of opening the form, **all client logic remains completely untested** - it is not covered by the scenario (it bypassed the UI), nor by the unit test (that is not its layer). The following remain unchecked: client handlers (`&НаКлиенте`, `ПриОткрытии`, `ПриИзменении`, command handlers), form client-server calls (`ПриСозданииНаСервере` and others), conditional formatting, visibility/accessibility/requiredness of elements, reaction to input and navigation, and the structure of the form itself. The result is that server-side validation is green, but the form crashes at runtime (broken form structure, unhandled event, wrong visibility - for example "Object field not found"), and this surfaces only for the user. Client logic is verified ONLY by a scenario that actually opens the form and simulates the user (`create -> window opened -> I see the element ... / enter / click`).

## MUST

- The scenario is based on the task specification or an existing business case - no invented cases.
- One scenario = one observable behavior.
- Before a new step - search for an existing one in the Vanessa library and the project scenarios.
- The first scenario for a new case is a short smoke test.
- The scenario is executed under a specific **business user**, not under admin/AgentAI - the only exception is when the function under test is available exclusively to the administrator; the user is determined from the task description, and if absent - **ask a human**.
- **Two-session split:** infrastructure data preparation (creating objects, VAExtension steps, BSL fixtures) - under the technical user (AgentAI); business flow (behavior verification) - under the specific business user. Switch via `And I close TestClient` + new `Given I connect TestClient`. You must not assign technical roles to the business user just to pass infrastructure steps.
- **Task tag is mandatory.** Each `.feature` file MUST contain the `@task-<ID>` tag (for example `@task-103`) at the `Feature:` level.
- **Source comment.** The file header (before the tags) MUST contain a comment: `# Task: <ID> — <title>`.
- Do not guess the logic - read the code (delegate to Explorer / `code-navigation`). A discrepancy between code and test is a found inconsistency, record it as a result.
- **Do not guess the interface** - element names and captions, fields, buttons, tabs, and the availability and state of elements are taken from the **real rendered interface**, inspected through the web client (`gui-control` / `screenshot` / `chrome-devtools` snapshot), not from code guesses or memory. Remember the difference between identifiers: steps "contains strings" / "go to line" expect the **title (Title)**, "I remember the field value" expects the **name (name)** - the exact value is learned by inspecting the form, not guessed. A discrepancy between the scenario and the real UI is a found inconsistency, record it as a result.
- **Manual fill before the scenario.** A new scenario for a document is written AFTER manually filling in the form in the web client with verification of the form composition **at each step** (the field value affects the visibility/requiredness of other fields); the key header fields and required tabular sections (>= several rows) are filled in; the document Hint/reference data has been studied; scroll bars hiding fields have been taken into account. If needed according to the test meaning - the document is **saved and posted**, popup errors at the bottom of the screen are analyzed and the filling is corrected. Filling scenarios are reusable "building blocks" (`@exportscenarios`), and one document can have several. Details - `vanessa-authoring`.

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
  - framework/rules/vanessa-test-isolation-policy.md
---
