---
name: agent-context-protocol
description: Protocol for saving and restoring a subagent's context between runs.
---



# Agent Context Protocol

> RULES — mandatory policy. Each subagent MUST save the context before shutting down
> and MUST read it on startup. This is the only reliable continuity mechanism between runs.

---

## Purpose

A subagent runs inside an isolated session — its context is destroyed after completion.
When the agent restarts, it begins with a clean slate and is forced to repeat work it already did.

`{role}-context.md` solves this problem: the agent preserves everything important before exiting
and reads it when the next run starts.

---

## Rule: first step on startup

Every subagent MUST perform as the **very first step**:

```
1. Check for {role}-context.md in task_dir
2. If the file exists → read it completely
3. Continue working taking the saved context into account,
   without repeating steps already completed
```

File name by role:

| Agent     | Context file                |
|-----------|-----------------------------|
| analyst   | `analyst-context.md`        |
| architect | `architect-context.md`      |
| developer | `developer-context.md`      |
| tester    | `tester-context.md`         |
| reviewer  | `reviewer-context.md`       |

---

## Rule: final step before completion

Every subagent MUST write `{role}-context.md` to `task_dir` **before any completion**:
- on successful completion (`completed`)
- when blocking questions arise (`clarification_needed`)
- when an implementation error is detected (`implementation_error`)

---

## Context file structure

```markdown
# {Role} Context

## Status
<!-- Одно из: completed | clarification_needed | implementation_error -->
{status}

## Completed Steps
<!-- Что уже сделано: какие файлы изучены, какие инструменты вызваны,
     какие артефакты созданы. Достаточно чтобы не повторять работу. -->
- ...

## Findings
<!-- Конкретные находки: модули, паттерны, структуры данных, зависимости.
     Всё что потребует усилий для повторного обнаружения. -->
- ...

## Assumptions
<!-- Допущения, принятые при неопределённости. -->
- ...

## Pending Questions
<!-- Заполняется только при Status: clarification_needed.
     Все вопросы одним блоком — не добавлять вопросы по одному. -->
- ...

## User Answers
<!-- Заполняет оркестратор перед повторным запуском агента.
     Агент читает этот раздел чтобы продолжить работу. -->
- ...
```

---

## What MUST go into the context

| Section | What to include |
|--------|--------------|
| `Completed Steps` | List of files/modules reviewed, invoked tools, created artifacts |
| `Findings` | Discovered modules and their paths, implementation patterns, data structures, extension points, existing dependencies |
| `Assumptions` | Any assumption adopted instead of asking the user |
| `Pending Questions` | All blocking questions in one list (only when `clarification_needed`) |
| `User Answers` | Not filled by the agent — only by the orchestrator |

---

## What MUST NOT be in the context

- The full contents of reviewed files — only conclusions and paths
- Intermediate reasoning — only final findings
- Information available from other artifacts in `task_dir` (specification, technical design)

---

## Resume mechanism (session-level optimization)

If the orchestrator and subagent operate in the same session, the orchestrator MAY use
`resume agentId` to continue in the same transcript instead of launching a new run.

```
When resume works:           When it DOES NOT work:
  Analyst → question                User closed the IDE
  → answer in the same session      → opened it tomorrow
  → resume agentId ✅              → agentId is outdated ❌
```

**Rule:** `{role}-context.md` is the primary mechanism and is always reliable.
`resume` is an additional optimization to apply when the agentId is still current.
When `resume` is used, the context file still MUST be written.

---

## Related resources

| Resource | Relation |
|--------|-------|
| [orchestrator.md](../workflows/orchestrator.md) | Protocol for transferring task_dir and agentId |
| [tdd-policy.md](./tdd-policy.md) | Completion tags: implementation_error |
| [sdd-policy.md](./sdd-policy.md) | Completion tags: clarification_needed |

---
depends_on: []
---
