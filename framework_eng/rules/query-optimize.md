---
name: query-optimize
description: "After identifying a slow query → apply the query-optimize skill"
alwaysApply: true
---
# Query Optimization (after identifying the problem)

> **Trigger:** after identifying a slow query or receiving a complaint about query/Data Composition System performance. When triggered, apply the `query-optimize` skill (`framework/skills/bsl-practices/query-optimize/SKILL.md`).

Do not optimize blindly: first collect the query plan and identify the bottleneck using the skill.

---
depends_on:
  - query-optimize
---
