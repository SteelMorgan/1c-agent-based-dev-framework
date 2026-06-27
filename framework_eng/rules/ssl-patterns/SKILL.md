---
name: ssl-patterns
description: "Before custom logic, check BSP mechanisms"
alwaysApply: true
---
# БСП Patterns (before implementation)

> **Trigger:** before implementing any business logic in 1C code. When triggered, apply the `ssl-patterns` skill (`framework/skills/bsl-practices/ssl-patterns/SKILL.md`).

**GUARD:** duplicating built-in БСП mechanisms blocks review.

---
depends_on:
  - ssl-patterns
---
