---
name: vanessa-test-isolation-policy
description: "For data-writing Vanessa tests, isolate data"
alwaysApply: true
---

# Vanessa test data isolation policy

> **Trigger:** writing a scenario that creates, modifies, or writes objects to the DB. When triggered, apply the `vanessa-authoring` skill (`framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md`).

## Isolation criterion (MUST)

| Does the test write to the DBMS? | Isolation | Data |
|---|---|---|
| Yes | **Full** | The test creates its own objects |
| No | Relaxed | Existing stable objects |

The main question: **can the test pass again** without intervention? If not, increase isolation.

## MUST for full isolation

- The test creates its own document/object at the beginning of the scenario.
- Identifiers are passed between steps through Vanessa variables (`$VarName$`).
- When switching TestClient, the document is saved and closed **before** the switch.
- Do not post test documents unless necessary - postings affect other tests.
- Record business-critical dependencies (counterparties with contracts, limit settings) in the scenario comment with the specific object indicated.

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
  - framework/rules/vanessa-scenario-policy/SKILL.md
---
