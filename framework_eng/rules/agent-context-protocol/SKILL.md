---
name: agent-context-protocol
description: "At agent startup/shutdown, read and write role context"
alwaysApply: true
---
# Agent Context Protocol

> **Trigger:** the start of any agent (orchestrator or subagent) and any of its completions (`completed`, `clarification_needed`, `implementation_error`). When triggered, apply the `agent-context` skill (`framework/skills/agent-process/agent-context/SKILL.md`): `.context/` location, file structure, role name table, resume mechanism, context savings through delegation.

## Invariant (always)

- Every agent - **both the orchestrator and subagents** - MUST as the **first step** read `task_dir/.context/{role}-context.md` (if it exists) and continue without repeating completed steps.
- Every agent MUST write `task_dir/.context/{role}-context.md` **before any completion**. No write = agent error.
- Role file: orchestrator - `orchestrator-context.md`, subagent - `{role}-context.md`; stored in `task_dir/.context/`.
- All task artifacts (contexts, spec, design, reports, `.feature` comments) are in **Russian**; code identifiers remain unchanged.

---
depends_on:
  - framework/skills/agent-process/agent-context/SKILL.md
---
