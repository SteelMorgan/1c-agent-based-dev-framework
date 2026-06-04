---
name: vanessa-scenario-policy
description: You are writing/updating a Vanessa feature file → apply the vanessa-authoring skill.
alwaysApply: true
---

# Vanessa Scenario Policy

> **Trigger:** creating or modifying a `.feature` file. When triggered, apply the `vanessa-authoring` skill (`framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md`).

## MUST

- The scenario is based on the task specification or an existing business case — no invented cases.
- One scenario = one observable behavior.
- Before a new step — search for an existing one in the Vanessa library and the project scenarios.
- The first scenario for a new case is a short smoke test.
- The scenario is executed under a specific **business user**, not under admin/AgentAI — the only exception is when the function under test is available exclusively to the administrator; the user is determined from the task description, and if absent — **ask a human**.
- **Two-session split:** infrastructure data preparation (creating objects, VAExtension steps, BSL fixtures) — under the technical user (AgentAI); business flow (behavior verification) — under the specific business user. Switch via `And I close TestClient` + new `Given I connect TestClient`. You must not assign technical roles to the business user just to pass infrastructure steps.
- **Task tag is mandatory.** Each `.feature` file MUST contain the `@task-<ID>` tag (for example `@task-103`) at the `Feature:` level.
- **Source comment.** The file header (before the tags) MUST contain a comment: `# Task: <ID> — <title>`.
- Do not guess the logic — read the code (delegate to Explorer / `code-navigation`). A discrepancy between code and test is a found inconsistency, record it as a result.

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
  - framework/rules/vanessa-test-isolation-policy.md
---
