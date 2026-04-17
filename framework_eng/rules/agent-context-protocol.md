---
name: agent-context-protocol
description: Protocol for preserving and restoring agent context (orchestrator + subagents) between runs.
---

# Agent Context Protocol

> Each agent - **both the orchestrator and the subagents** - MUST save the context before finishing and MUST read it on startup. The orchestrator maintains `orchestrator-context.md`, subagents maintain `{role}-context.md`.

## Document Language

All task artifacts MUST be in **Russian**: specifications, technical design, agent contexts, reports, comments in `.feature` files, `final-report.md`. Exception - code identifiers (variable names, modules, metadata) stay as-is.

## Context File Location

All agent context files are stored in the `.context/` subdirectory inside `task_dir`:

```
task_dir/.context/{role}-context.md
```

An agent MUST create the `.context/` directory if it does not yet exist (`mkdir -p`).

## First Step on Startup

Each agent (orchestrator and subagents) MUST as the **first step**: check `task_dir/.context/{role}-context.md`, read it, and continue work without repeating already completed steps.

| Agent | Context File |
|-------|--------------|
| **orchestrator** | `orchestrator-context.md` |
| analyst | `analyst-context.md` |
| architect | `architect-context.md` |
| scenario-author | `scenario-author-context.md` |
| developer-tests | `developer-tests-context.md` |
| developer-code | `developer-code-context.md` |
| tester | `tester-context.md` |
| reviewer | `reviewer-context-{scope}.md` |

## Last Step Before Completion

Each agent (orchestrator and subagents) MUST write `task_dir/.context/{role}-context.md` **before any termination**: `completed`, `clarification_needed`, `implementation_error`.

## Context File Structure

```markdown
# {Role} Context

## Status
{completed | clarification_needed | implementation_error}

## Completed Steps
- {files, tools, artifacts - enough so the work does not repeat}

## Findings
- {modules, patterns, data structures, dependencies}

## Assumptions
- {assumptions under uncertainty}

## Pending Questions
- {only when clarification_needed, keep questions in a single block}

## User Answers
- {filled out by the orchestrator}
```

## What NOT to Include

- Full contents of files - only conclusions and paths
- Intermediate reasoning - only final findings
- Information from other `task_dir` artifacts

## Resume Mechanism

`{role}-context.md` is the primary mechanism. `resume agentId` is an optimization within one session. Even when resuming, the context file MUST still be written.

## Context Savings Through Delegation

> An agent's context window is a limited resource. Heavy operations for searching and analyzing code SHOULD be delegated to subagents rather than performed manually via consecutive Read/Grep steps.

**Principle:** the agent spends its context on **decision making**, not on gathering raw data. Everything that can be delegated should be delegated.

### When to Delegate

| Situation | Action |
|----------|--------|
| Need to find all occurrences of a pattern in the project | Run an `Explore` agent with a specific question |
| Need to understand the structure of a module (500+ lines) | Run an `Explore` agent: "list the exported functions and their purposes" |
| Need to check the call graph | Run an `explorer` agent with a call_graph task |
| Need an independent opinion on a piece of code | Run a `reviewer` agent with a narrow scope |
| Simple search (1-2 greps for a known pattern) | Do it yourself - delegation is more expensive |

### How to Delegate

```
Agent(subagent_type="Explore", prompt="В модуле <путь> найди все вызовы <функция> и верни список: файл:строка -> контекст вызова")
```

**MUST:** formulate the task concretely - what to find, where to find it, and in what format to return the result.

**MUST NOT:** delegate trivial operations (reading a single file, a single grep) - the overhead of launching an agent outweighs the savings.

### Who Can Delegate

All agents with access to the `Agent` tool (developer-code, reviewer, tester, architect, analyst, explorer, scenario-author, developer-tests). `Explore` and `Plan` agents CANNOT launch subagents.

---
depends_on: []
---
