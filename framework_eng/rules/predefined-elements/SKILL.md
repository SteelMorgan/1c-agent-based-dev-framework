---
name: predefined-elements
description: "Before new settings, search predefined values"
alwaysApply: true
---

# Reuse-first for settings and predefined values

> **Trigger:** the task needs a setting, a named predefined value, a threshold, a flag, a code, a reference to an object, or another parameter that should live centrally.

## Principle

Settings replicated across copies or hardcoded as literals in code diverge between installations and silently break logic. If the project already has a centralized storage for settings or named predefined values, the agent must first look for the value there and use the standard access layer for it.

## MUST

| Requirement | Description |
|-----------|----------|
| Search first | Before creating a new setting or hardcoding a value, find the existing value by business key through the project's standard mechanism. If found, reuse it and do not create a duplicate |
| Read through wrappers | If the project has a service module, API, or БСП access wrapper for settings, use it instead of a direct query to storage |
| No hardcoding | Do not hardcode codes, references, thresholds, and flags that should be manageable settings. The value must be read by a meaningful business key |
| New key only when absent | Create a new setting only after checking that no existing key and no collisions by purpose exist |
| One key in one place | Declare the string business key only once: a constant, an exported function, or a single access point. Do not duplicate the literal across the code |
| Document the purpose | For a new setting, record the purpose, value format, logic owner, and acceptable default value in task artifacts or project documentation |

## SHOULD

- If a setting is needed in multiple places, all of them read it through the same key and the same access layer.
- If the project does not have a centralized settings storage, first check standard or library mechanisms instead of creating a local catalog/register without an architectural decision.
- For migrating old hardcoded values, first find all usages of the literal and define a single key, then replace accesses through a shared API.

## What this rule does NOT cover

- Settings that are a full-fledged domain model with complex identification and lifecycle. They require a separate design, not a key-value entry.
- Standard platform or library mechanisms for storing settings. They should be reused according to `ssl-patterns` if they fit the task.

## Related rules

- `search-before-write` - reuse-first for code and ready-made mechanisms.
- `ssl-patterns` - reuse of standard and library mechanisms.

---
depends_on:
  - search-before-write
  - ssl-patterns
---
