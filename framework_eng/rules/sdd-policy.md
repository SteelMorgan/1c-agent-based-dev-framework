---
name: sdd-policy
description: New feature / architectural change / complex bug -> spec before code. Apply the spec-standard skill.
alwaysApply: true
---

# SDD Policy (Spec-Driven Development)

> **Trigger:** the task is classified as a new feature, an architectural change, or a complex bug. When triggered, apply the `spec-standard` skill (`framework/skills/spec-writing/spec-standard/SKILL.md`).

## When a spec is needed (MUST / SHOULD / MAY)

| Task type | Level |
|------------|---------|
| New feature, architectural changes, complex bug | MUST |
| Major refactoring | SHOULD |
| Simple bug, formatting, typos | MAY (skip) |

## MUST

- The spec is created and reviewed **before** implementation.
- Task Breakdown JSON is a separate `.json`, reviewed before implementation.
- Any deviation from the approved spec -> stop implementation, update the spec, re-review.
- Any change to the approved spec/JSON requires a re-review.

## Exceptions

- Simple tasks - no spec needed.
- Prototyping at the user's request - without a spec.
- Free mode (without full cycle) - advisory.

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/rules/tdd-policy.md
---
