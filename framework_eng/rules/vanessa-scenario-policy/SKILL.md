---
name: vanessa-scenario-policy
description: "When writing Vanessa features, apply authoring rules"
alwaysApply: true
---

# Vanessa Automation Scenario Policy

> **Trigger:** creating or modifying a `.feature` file. When triggered, apply the `vanessa-authoring` skill (`framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md`).

## Purpose of Testing Layers (MUST - fundamental)

> A scenario (Vanessa) test exists to verify **client behavior through the UI, imitating user actions**. The division of "what we test with which test" is canonical in `tdd-policy` (section "Purpose of layers"); here are only Vanessa specifics.

- **Verified behavior goes ONLY through the UI.** The scenario opens the form and imitates the user with real UI steps (`window opened`, `in the field named ... I enter`, `I click the ... button`, `I see the element`, `element ... is available`). It is **FORBIDDEN** to replace the user action path with a server call (`ПроверитьЗаполнение()`, `ЗаписатьОбъект()`, direct call of a common module) - such a "scenario" is a unit test in Vanessa clothing and misses form-layer defects.
- **Server-side BSL in Vanessa is allowed ONLY for data preparation/cleanup** (fixtures, setup, teardown - see the two-session split below), NOT as a replacement for the checked user scenario. Boundary: code "prepares the stage" - server-side is allowed; code "checks behavior" - only through the UI.
- **Why this rule exists.** Bypassing the UI with a server call leaves all client logic (handlers, visibility/accessibility, navigation, form structure) without coverage - it is not checked by either such a "scenario" or a unit test, and the defect surfaces only for the user at runtime.

## MUST

- The scenario is based on the task specification or an existing business case - no fictional cases.
- One scenario = one observable behavior.
- Before adding a new step, look for an existing one in the Vanessa library and the project scenarios.
- The first scenario for a new case is a short smoke test.
- The scenario is executed under a specific **business user**, not under admin/AgentAI - the only exception is if the function being checked is available exclusively to an administrator; the user is determined from the task description, and if it is missing, **ask a human**.
- **Two-session split:** infrastructure data preparation (creating objects, VAExtension steps, BSL fixtures) is done under the technical user (AgentAI); the business flow (behavior verification) is done under a specific business user. Switch via `And I close TestClient` + a new `Given I connect TestClient`. You cannot assign technical roles to the business user just to pass infrastructure steps.
- **Task tag is mandatory.** Every `.feature` file MUST contain the tag `@task-<ID>` (for example `@task-103`) at the `Feature:` level.
- **Source comment.** The file header (before the tags) MUST contain the comment: `# Task: <ID> - <title>`.
- Do not guess the logic - read the code (delegate to Explorer / `code-navigation`). A discrepancy between code and test is a discovered mismatch; record it as a result.
- **Do not guess the interface** - names and titles of elements, fields, buttons, tabs, availability and state of elements are taken from the **real rendered interface**, examined through Vanessa/TestClient or through the fallback via `va-visual-check`, not from guesses based on code or memory. Remember the difference between identifiers: the steps "contains strings" / "go to line" expect the **title (Title)**, while "remember the field value" expects the **name (name)** - the exact value is learned by inspecting the form, not guessed. A discrepancy between the scenario and the real UI is a discovered mismatch; record it as a result.
- **Manual fill-in before the scenario.** A new document scenario is written AFTER filling the form manually through Vanessa/TestClient with verification of the form composition **at every step** (the field value affects visibility/requiredness of other fields); the key header fields and required tabular sections (>= several rows) are filled; the document hint/reference data have been studied; scrollbars hiding fields have been taken into account. If needed by the test meaning, the document is **saved and posted**, pop-up errors at the bottom of the screen are analyzed and the filling corrected. Fill-in scenarios are reusable "building blocks" (`@exportscenarios`), and one document can have several. Details - `vanessa-authoring`.

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
  - framework/rules/vanessa-test-isolation-policy/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
---
