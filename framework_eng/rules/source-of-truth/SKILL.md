---
name: source-of-truth
description: Conflict / test failure / artifact dispute -> check the source-of-truth chain from top to bottom (L1→L6). Method is in the `source-of-truth` skill.
alwaysApply: true
---
# Source of Truth Policy

> **Trigger:** any conflict, test failure, behavior mismatch, or dispute between artifacts. When triggered, apply the `source-of-truth` skill (`framework/skills/framework-meta/source-of-truth/SKILL.md`): full L1→L6 hierarchy, end-to-end verification method, classification of the first broken link, implications for roles, typical applications.

---
depends_on:
  - source-of-truth
  - framework/rules/sdd-policy/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
---
