---
name: ssl-patterns
description: "Before custom logic, check the БСП mechanisms"
alwaysApply: true
---
# БСП Patterns (before implementation)

> **Trigger:** before implementing any business logic in 1C code. When triggered, apply the `ssl-patterns` skill (`framework/skills/bsl-practices/ssl-patterns/SKILL.md`).

**GUARD:** duplicating existing БСП mechanisms blocks review.

---
depends_on:
  - ssl-patterns
---
