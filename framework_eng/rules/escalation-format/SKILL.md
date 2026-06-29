---
name: escalation-format
description: "When escalating a decision, provide options and recommendation"
alwaysApply: true
---
# Escalation Format for the User

> **Trigger:** an orchestrator or subagent requests a user decision — `clarification_needed`, iteration limit exceeded (3+ BLOCK), an architectural choice without an unambiguous source, a conflict between source-of-truth levels, etc. When triggered, apply the `escalation-format` skill (`framework/skills/agent-process/escalation-format/SKILL.md`): it defines the message structure, grouping rules, and examples.

> **IMPORTANT — the filter for "when to escalate" is in `framework/workflows/orchestrator/SKILL.md` § 4 "The principle of 'delegate, don't ask'".** This trigger only defines the reason to apply the format. Before escalating, the orchestrator/subagent MUST go through the filter in orchestrator.md and make sure the escalation is legitimate (admin operation / changing the L1-L2 contract / business decision / 3+ BLOCK / scope expansion). Technical choices and diagnostics do NOT require escalation — they are resolved by delegating to a subagent.

## Invariant (always)

- It is **forbidden** to escalate "one question without context" — the user will not be able to choose without understanding the problems behind the question.
- It is **forbidden** to hide options in a single phrase ("accept or change the spec") — each option must be expanded: what changes, where, and at what cost.
- Applying the escalation format to a technical question (which is resolved by delegating to a subagent) = an error.

---
depends_on:
  - framework/skills/agent-process/escalation-format/SKILL.md
  - framework/workflows/orchestrator/SKILL.md
---
