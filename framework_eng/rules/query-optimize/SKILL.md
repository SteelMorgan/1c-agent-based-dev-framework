---
name: query-optimize
description: "After detecting a slow query, apply optimize"
alwaysApply: true
---
# Query Optimization (after the issue is identified)

> **Trigger:** after detecting a slow query or receiving a complaint about query/СКД performance. When triggered, apply the `query-optimize` skill (`framework/skills/bsl-practices/query-optimize/SKILL.md`).

---
depends_on:
  - query-optimize
---
