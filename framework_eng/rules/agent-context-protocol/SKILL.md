---
name: agent-context-protocol
description: Agent startup -> read {role}-context.md; exit -> write it. Procedure and structure are in the agent-context skill.
alwaysApply: true
---
# Agent Context Protocol

> **Trigger:** start of any agent (orchestrator or subagent) and any of its terminations (`completed`, `clarification_needed`, `implementation_error`). When triggered, apply the `agent-context` skill (`framework/skills/framework-meta/agent-context/SKILL.md`): location of `.context/`, file structure, role name table, resume mechanism, saving context through delegation.

## Invariant (always)

- Every agent - **both the orchestrator and the subagents** - MUST as the **first step** read `task_dir/.context/{role}-context.md` (if present) and continue without repeating completed steps.
- Every agent MUST write `task_dir/.context/{role}-context.md` **before any termination**. No write = agent error.
- File by role: orchestrator - `orchestrator-context.md`, subagent - `{role}-context.md`; stored in `task_dir/.context/`.
- All task artifacts (contexts, spec, design, reports, comments `.feature`) are in **Russian**; code identifiers stay as-is.

---
depends_on:
  - agent-context
---
