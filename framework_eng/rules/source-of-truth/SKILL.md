---
name: source-of-truth
description: "When there are conflicts or failures, follow source-of-truth"
alwaysApply: true
---
# Source of Truth Policy

> **Trigger:** any conflict, test failure, behavioral divergence, or dispute between artifacts. When triggered, apply the `source-of-truth` skill (`framework/skills/agent-process/source-of-truth/SKILL.md`): full L1→L6 hierarchy, end-to-end verification method, classification of the first broken link, implications for roles, and typical applications.

---
depends_on:
  - framework/skills/agent-process/source-of-truth/SKILL.md
  - framework/rules/sdd-policy/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
---
