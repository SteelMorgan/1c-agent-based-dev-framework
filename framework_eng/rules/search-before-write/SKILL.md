---
name: search-before-write
description: "Before new code or query, search existing code"
alwaysApply: true
---
# Search Before Writing

> **Trigger:** before creating a new function, query, or processing. When it fires, apply the `search-before-write` skill (`framework/skills/tool-usage/code-analysis/search-before-write/SKILL.md`).

**GUARD:** creating new code without first searching for analogs is prohibited.

---
depends_on:
  - search-before-write
---
