---
name: query-patterns
description: "Before writing a new query → apply the query-patterns skill"
alwaysApply: true
---
# Query Patterns (before writing)

> **Trigger:** before writing a new query in the 1C query language. When triggered, apply the `query-patterns` skill (`framework/skills/bsl-practices/query-patterns/SKILL.md`).

Each query is a network round-trip. Make sure you do not create query-in-loop, dot-dereference, or excessive joins.

---
depends_on:
  - query-patterns
---
