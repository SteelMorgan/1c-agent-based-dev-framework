---
name: agent-context-protocol
description: Protocol for saving and restoring subagent context between runs.
---

# Agent Context Protocol

> Each subagent MUST save its context before finishing and MUST read it when starting.

## First step on startup

Each subagent MUST as **the first step**: check `{role}-context.md` in `task_dir`, read it, and continue working without redoing completed steps.

| Agent | Context file |
|-------|--------------|
| analyst | `analyst-context.md` |
| architect | `architect-context.md` |
| scenario-author | `scenario-author-context.md` |
| developer-tests | `developer-tests-context.md` |
| developer-code | `developer-code-context.md` |
| tester | `tester-context.md` |
| reviewer | `reviewer-context-{scope}.md` |

## Last step before completion

Each subagent MUST write `{role}-context.md` into `task_dir` **before any termination**: `completed`, `clarification_needed`, `implementation_error`.

## Context file structure

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

`{role}-context.md` — primary mechanism. `resume agentId` — optimization within a single session. When using `resume`, the context file still MUST be written.

---
depends_on: []
---
