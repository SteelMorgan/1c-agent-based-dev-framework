---
name: vanessa-test-isolation-policy
description: You are writing a Vanessa scenario with data writes → full isolation (the test creates its own objects). Apply the `vanessa-authoring` skill.
alwaysApply: true
---

# Vanessa Test Data Isolation Policy

> **Trigger:** writing a scenario that creates, modifies, or writes objects to the DB. When triggered, apply the `vanessa-authoring` skill (`framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md`).

## Isolation Criterion (MUST)

| Does the test write to the DBMS? | Isolation | Data |
|---|---|---|
| Yes | **Full** | The test creates its own objects |
| No | Relaxed | Existing stable objects |

The main question: **can the test be rerun** without intervention? If not, increase the isolation.

## MUST for Full Isolation

- The test creates its own document/object at the beginning of the scenario.
- Identifiers are passed between steps through Vanessa variables (`$VarName$`).
- When switching TestClient, the document is saved and closed before the switch.
- Do not post test documents unless necessary — postings affect other tests.
- Business-critical dependencies (counterparties with contracts, limit settings) must be recorded in the scenario comment with a reference to the specific object.

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
  - framework/rules/vanessa-scenario-policy.md
---
