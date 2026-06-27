---
name: vanessa-test-isolation-policy
description: "For data-writing Vanessa tests, isolate test data"
alwaysApply: true
---

# Vanessa Test Data Isolation Policy

> **Trigger:** writing a scenario that creates, modifies, or writes objects to the database. When triggered, apply the `vanessa-authoring` skill (`framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md`).

## Isolation Criterion (MUST)

| Does the test write to the DBMS? | Isolation | Data |
|---|---|---|
| Yes | **Full** | The test creates its own objects |
| No | Relaxed | Existing stable objects |

The main question: **can the test pass again** without intervention? If not, increase isolation.

## MUST for Full Isolation

- The test creates its own document/object at the start of the scenario.
- Identifiers are passed between steps through Vanessa variables (`$VarName$`).
- When switching TestClient, the document is saved and closed **before** switching.
- Do not post test documents unless necessary - movements affect other tests.
- Business-critical dependencies (counterparties with contracts, limit settings) should be fixed in the scenario comment, with the specific object indicated.

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
  - framework/rules/vanessa-scenario-policy/SKILL.md
---
