---
name: query-optimize
description: "After finding a slow query, apply query optimize"
alwaysApply: true
---
# Query Optimization (after the issue is identified)

> **Trigger:** after identifying a slow query or receiving a complaint about query/Data Composition System performance. When triggered, apply the `query-optimize` skill (`framework/skills/bsl-practices/query-optimize/SKILL.md`).

---
depends_on:
  - query-optimize
---
