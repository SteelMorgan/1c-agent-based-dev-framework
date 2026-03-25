---
name: agent-context-protocol
description: Protocol for saving and restoring agent contexts (orchestrator + subagents) between runs.
---

# Agent Context Protocol

> Every agent — **both orchestrator and subagents** — MUST save the context before shutdown and MUST read it at startup. The orchestrator maintains `orchestrator-context.md`, subagents maintain `{role}-context.md`.

## Document Language

All task artifacts MUST be in **Russian**: specifications, technical design, agent contexts, reports, comments in `.feature` files, `final-report.md`. Exception — code identifiers (variable, module, metadata names) stay as they are.

## Context Locations

All agent context files are stored in the `.context/` subdirectory inside `task_dir`:

```
task_dir/.context/{role}-context.md
```

An agent MUST create the `.context/` directory if it does not yet exist (mkdir -p).

## First Step at Startup

Each agent (orchestrator and subagents) MUST as the **first step**: check `task_dir/.context/{role}-context.md`, read it, and continue work without repeating completed steps.

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

## Last Step Before Shutdown

Each agent (orchestrator and subagents) MUST write `task_dir/.context/{role}-context.md` **before any termination**: `completed`, `clarification_needed`, `implementation_error`.

## File Structure

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

- Full file contents — only conclusions and paths
- Intermediate reasoning — only final findings
- Information from other `task_dir` artifacts

## Resume Mechanism

`{role}-context.md` is the primary mechanism. `resume agentId` is an optimization within one session. On `resume`, the context file still MUST be written.

---
depends_on: []
---
