---
name: agent-context
description: MUST use WHEN the agent starts or finishes work. Provides the procedure for maintaining {role}-context.md (file location, structure, table of names, resume mechanism, context savings through delegation).
alwaysApply: false
---

# Agent Context Protocol (Procedure)

> Every agent - **both the orchestrator and subagents** - MUST save context before finishing and MUST read it at startup. The orchestrator maintains `orchestrator-context.md`, subagents - `{role}-context.md`. This skill is a detailed procedure; the start-read / exit-write invariant is fixed in the rule `framework/rules/agent-context-protocol/SKILL.md`.

## Document Language

All task artifacts MUST be in **Russian**: specifications, technical design, agent contexts, reports, comments in `.feature` files, `final-report.md`. The exception is code identifiers (variable names, modules, metadata), which remain unchanged.

## Context Location

All agent context files are stored in the `.context/` subdirectory inside `task_dir`:

```
task_dir/.context/{role}-context.md
```

The agent MUST create the `.context/` directory if it does not already exist (mkdir -p).

## First Step at Startup

Each agent (orchestrator and subagents) MUST as the **first step**: check `task_dir/.context/{role}-context.md`, read it, and continue working without repeating completed steps.

| Agent | Context File |
|-------|----------------|
| **orchestrator** | `orchestrator-context.md` |
| analyst | `analyst-context.md` |
| architect | `architect-context.md` |
| scenario-author | `scenario-author-context.md` |
| developer-tests | `developer-tests-context.md` |
| developer-code | `developer-code-context.md` |
| tester | `tester-context.md` |
| reviewer | `reviewer-context-{scope}.md` |

## Last Step Before Completion

Each agent (orchestrator and subagents) MUST write `task_dir/.context/{role}-context.md` **before any completion**: `completed`, `clarification_needed`, `implementation_error`.

## Context File Structure

```markdown
# {Role} Context

## Status
{completed | clarification_needed | implementation_error}

## Completed Steps
- {files, tools, artifacts - enough to avoid repeating the work}

## Findings
- {modules, patterns, data structures, dependencies}

## Assumptions
- {assumptions under uncertainty}

## Pending Questions
- {only when clarification_needed, all questions in one block}

## User Answers
- {filled in by the orchestrator}
```

## What to EXCLUDE

- Full file contents - only conclusions and paths
- Intermediate reasoning - only final findings
- Information from other `task_dir` artifacts

## Resume Mechanism

`{role}-context.md` is the primary mechanism. `resume agentId` is an optimization within a single session. When using `resume`, the context file MUST still be written.

## Saving Context Through Delegation

> The agent context window is a limited resource. Heavy code search and analysis operations SHOULD be delegated to subagents rather than performed manually through sequential Read/Grep.

**Principle:** the agent spends its context on **decision-making**, not on collecting raw data. Anything that can be delegated is delegated.

### When to Delegate

| Situation | Action |
|----------|--------|
| Need to find all occurrences of a pattern in the project | Launch an `Explore` agent with a specific question |
| Need to understand the structure of a module (500+ lines) | Launch an `Explore` agent: "list the exported functions and their purpose" |
| Need to check the call graph | Launch an `explorer` agent with a call_graph task |
| Need an independent opinion on a piece of code | Launch a `reviewer` agent with a narrow scope |
| Simple search (1-2 grep calls for a known pattern) | Do it yourself - delegation is more expensive |

### How to Delegate

```
Agent(subagent_type="Explore", prompt="In module <path>, find all calls to <function> and return a list: file:line → call context")
```

**MUST:** formulate the task precisely - what to search for, where to search, and in what format to return the result.

**MUST NOT:** delegate trivial operations (reading one file, one grep) - the overhead of launching an agent exceeds the savings.

### Who Can Delegate

All agents with access to the `Agent` tool (developer-code, reviewer, tester, architect, analyst, explorer, scenario-author, developer-tests). The `Explore` and `Plan` agents CANNOT launch subagents.

---
depends_on: []
---
