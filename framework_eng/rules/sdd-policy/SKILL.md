---
name: sdd-policy
description: "For features, architecture, and complex bugs, start with a spec"
alwaysApply: true
---

# SDD Policy (Spec-Driven Development)

> **Trigger:** the task is classified as new functionality, an architectural change, or a complex bug. When triggered, apply the `spec-standard` skill (`framework/skills/spec-writing/spec-standard/SKILL.md`).

## When a spec is needed (MUST / SHOULD / MAY)

| Task type | Level |
|------------|---------|
| New functionality, architectural changes, complex bug | MUST |
| Large refactoring | SHOULD |
| Simple bug, formatting, typos | MAY (skip) |

## MUST

- The spec is created and reviewed **before** implementation.
- Task Breakdown JSON is a separate `.json`, reviewed before implementation.
- Any deviation from the approved spec → stop implementation, update the spec, re-review.
- Any change to the approved spec/JSON requires re-review.

## Exceptions

- Simple tasks do not need a spec.
- Prototyping at the user's request — without a spec.
- Free mode (without full-cycle) — advisory.

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
---
