---
name: source-of-truth
description: Conflict / test failure / artifact dispute -> check the source-of-truth chain from top to bottom (L1→L6). The method is in the `source-of-truth` skill.
alwaysApply: true
---
# Source of Truth Policy

> **Trigger:** any conflict, test failure, behavioral divergence, or dispute between artifacts. When triggered, apply the `source-of-truth` skill (`framework/skills/framework-meta/source-of-truth/SKILL.md`): full L1→L6 hierarchy, end-to-end verification method, classification of the first broken link, implications for roles, and typical applications.

## Invariant (always)

- Artifacts form a hierarchy (1 business objective/user answers → 2 specification → 3 design/task-breakdown → 4 BDD → 5 unit/integration → 6 code). The lower level refines the upper one, but does NOT override it; in case of conflict, the higher level takes priority.
- A visible error at a lower level does NOT prove that the cause is there as well. A binary conclusion of "the test/code is at fault" without checking the upper levels is forbidden.
- Checking the chain MUST be done from top to bottom until the first broken link; that is the root cause, and that is what must be fixed (not just the symptom).
- If the root cause is outside the agent's area of responsibility, stop, record the conclusion in context, and return control to the orchestrator.

---
depends_on:
  - source-of-truth
  - framework/rules/sdd-policy.md
  - framework/rules/tdd-policy.md
---
