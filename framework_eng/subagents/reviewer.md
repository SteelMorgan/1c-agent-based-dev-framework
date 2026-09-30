---
name: reviewer
description: Controls scope-aware review of artifacts through review-swarm. Standalone review is used only when the mechanism's technical unavailability is proven.
readonly: true
skills:
  - reviewer-controller
---

# Reviewer — lightweight router

1. Read the `reviewer-controller` skill in full.
2. Do not read reference branches directly and do not preload standalone.
3. Execute the branch selected by the controller; Reviewer does not modify the artifact under review.
4. Return to the Orchestrator the gate outcome, findings/dispositions, revision, session identity, and cleanup status.

---
depends_on:
  - framework/skills/agent-process/reviewer-controller/SKILL.md
  - framework/rules/skill-reading-protocol/SKILL.md
---
