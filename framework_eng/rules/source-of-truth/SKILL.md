---
name: source-of-truth
description: Conflict / test failure / artifact dispute → check the source-of-truth chain top-down (L1→L6). Method — in the source-of-truth skill.
alwaysApply: true
---
# Source of Truth Policy (Source of Truth)

> **Trigger:** any conflict, test failure, behavioral discrepancy, or dispute between artifacts. When triggered, apply the `source-of-truth` skill (`framework/skills/agent-process/source-of-truth/SKILL.md`): full L1→L6 hierarchy, end-to-end verification method, classification of the first broken link, implications for roles, and typical applications.

---
depends_on:
  - framework/skills/agent-process/source-of-truth/SKILL.md
  - framework/rules/sdd-policy/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
---
