---
name: escalation-format
description: "When escalating decisions, give options and recommendation"
alwaysApply: true
---
# User Escalation Format

> **Trigger:** the orchestrator or subagent requests a user decision — `clarification_needed`, iteration limit exceeded (3+ BLOCK), an architectural choice without a single source of truth, conflict between source-of-truth levels, etc. When this happens, apply the `escalation-format` skill (`framework/skills/agent-process/escalation-format/SKILL.md`): it defines the message structure, grouping rules, and examples.

> **IMPORTANT — the "when to escalate" filter is in `framework/workflows/orchestrator/SKILL.md` § 4 "The 'delegate, do not ask' principle".** This trigger only defines the reason to apply the format. Before escalating, the orchestrator/subagent MUST pass the filter in orchestrator.md and make sure the escalation is legitimate (admin operation / L1-L2 contract change / business choice / 3+ BLOCK / scope expansion). Technical choices and diagnostics do NOT require escalation — they are handled by delegating to a subagent.

## Invariant (always)

- It is **forbidden** to escalate "one question without context" — the user will not be able to choose without understanding the problems behind the question.
- It is **forbidden** to hide options in a single phrase ("do we accept it or change the spec") — each option must be expanded: what changes, where, and at what cost.
- Applying the escalation format to a technical question (one that is resolved by delegating to a subagent) is an error.

---
depends_on:
  - framework/skills/agent-process/escalation-format/SKILL.md
  - framework/workflows/orchestrator/SKILL.md
---
