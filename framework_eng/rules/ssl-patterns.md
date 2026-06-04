---
name: ssl-patterns
description: "Before implementing logic — check for a ready-made mechanism in БСП → ssl-patterns skill"
alwaysApply: true
---
# БСП Patterns (before implementation)

> **Trigger:** before implementing any business logic in 1С code. When triggered — apply the `ssl-patterns` skill (`framework/skills/bsl-practices/ssl-patterns/SKILL.md`).

Check whether БСП already has a ready-made mechanism. Duplicating БСП is an anti-pattern.

---
depends_on:
  - ssl-patterns
---
