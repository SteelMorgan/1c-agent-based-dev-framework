---
name: agent-context-protocol
description: Protocol for saving and restoring agent contexts (orchestrator + subagents) between runs.
---

# Agent Context Protocol

> Each agent — **both orchestrator and subagents** — MUST save the context before termination and MUST read it at startup. The orchestrator keeps `orchestrator-context.md`, subagents keep `{role}-context.md`.

## Context locations

All agent context files are stored in the `.context/` subdirectory inside `task_dir`:

```
task_dir/.context/{role}-context.md
```

The agent MUST create the `.context/` directory if it does not yet exist (mkdir -p).

## First step on startup

Each agent (orchestrator and subagents) MUST as **the first step** check `task_dir/.context/{role}-context.md`, read it, and continue working without repeating already completed steps.

| Agent | Context file |
|-------|--------------|
| **orchestrator** | `orchestrator-context.md` |
| analyst | `analyst-context.md` |
| architect | `architect-context.md` |
| scenario-author | `scenario-author-context.md` |
| developer-tests | `developer-tests-context.md` |
| developer-code | `developer-code-context.md` |
| tester | `tester-context.md` |
| reviewer | `reviewer-context-{scope}.md` |

## Last step before termination

Each agent (orchestrator and subagents) MUST write `task_dir/.context/{role}-context.md` **before any termination**: `completed`, `clarification_needed`, `implementation_error`.

## Structure of the context file

```markdown
# {Role} Context

## Status
{completed | clarification_needed | implementation_error}

## Completed Steps
- {файлы, инструменты, артефакты — достаточно чтобы не повторять работу}

## Findings
- {модули, паттерны, структуры данных, зависимости}

## Assumptions
- {допущения при неопределённости}

## Pending Questions
- {только при clarification_needed, все вопросы одним блоком}

## User Answers
- {заполняет оркестратор}
```

## What NOT to include

- Full contents of files — only findings and paths
- Intermediate reasoning — only final observations
- Information from other `task_dir` artifacts

## Resume mechanism

`{role}-context.md` is the main mechanism. `resume agentId` is an optimization within a single session. When using `resume`, the context file still MUST be written.

---
depends_on: []
---
