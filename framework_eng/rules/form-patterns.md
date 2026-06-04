---
name: form-patterns
description: "Before writing a managed form module → apply the form-patterns skill"
alwaysApply: true
---
# Managed Form Module Patterns

> **Trigger:** before writing or making significant changes to a managed form module in 1С. When triggered, apply the `form-patterns` skill (`framework/skills/bsl-practices/form-patterns/SKILL.md`).

Minimize server calls. Do not keep data in `&НаКлиенте` that the UI does not need. Every `&НаСервере` is a round-trip.

---
depends_on:
  - form-patterns
---
