---
name: search-before-write
description: "Before creating a new function/query/processing -> apply the search-before-write skill"
alwaysApply: true
---
# Search Before Writing

> **Trigger:** before creating a new function, query, or processing. When triggered, apply the `search-before-write` skill (`framework/skills/tool-usage/code-analysis/search-before-write/SKILL.md`).

First, run the search cascade from the skill. Do not write new code until you have verified that no equivalent exists in the project or in БСП.

---
depends_on:
  - search-before-write
---
