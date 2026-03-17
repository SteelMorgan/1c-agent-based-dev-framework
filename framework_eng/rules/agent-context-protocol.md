---
name: agent-context-protocol
description: Protocol for saving and restoring a subagent context between runs.
---



# Agent Context Protocol

> RULES — mandatory policy. Every subagent MUST save the context before exiting and MUST read it on startup. This is the only reliable mechanism for continuity between runs.

---

## Purpose

A subagent runs inside an isolated session — when it finishes, its context is destroyed.
On the next launch, the agent starts with a clean slate and has to repeat work that was already done.

`{role}-context.md` solves this problem: the agent saves everything important before exiting and reads it at the start of the next run.

---

## Rule: first step on startup

Every subagent MUST execute the following as the **first step**:

```
1. Check for the presence of {role}-context.md in task_dir
2. If the file exists → read it completely
3. Continue working taking the saved context into account,
   without repeating steps that were already performed
```

Context file names by role:

| Agent     | Context file                |
|-----------|-----------------------------|
| analyst         | `analyst-context.md`          |
| architect       | `architect-context.md`        |
| scenario-author | `scenario-author-context.md`  |
| developer-tests | `developer-tests-context.md`  |
| developer-code  | `developer-code-context.md`   |
| tester          | `tester-context.md`           |
| reviewer        | `reviewer-context-{scope}.md` |

---

## Rule: final step before finishing

Every subagent MUST write `{role}-context.md` into `task_dir` **before any exit**:
- upon successful completion (`completed`)
- when blocked by questions (`clarification_needed`)
- when an implementation error is detected (`implementation_error`)

---

## Context file structure

```markdown
# {Role} Context

## Status
<!-- One of: completed | clarification_needed | implementation_error -->
{status}

## Completed Steps
<!-- What has already been done: which files were reviewed, which tools were invoked,
     which artifacts were created. Enough detail to avoid repeating work. -->
- ...

## Findings
<!-- Specific discoveries: modules, patterns, data structures, dependencies.
     Anything that would take effort to rediscover. -->
- ...

## Assumptions
<!-- Assumptions made in the face of uncertainty. -->
- ...

## Pending Questions
<!-- Filled only when Status: clarification_needed.
     Group all questions in one block — do not add separate question entries. -->
- ...

## User Answers
<!-- Filled by the orchestrator before relaunching the agent.
     The agent reads this section to resume work. -->
- ...
```

---

## What MUST be included in the context

| Section | Include |
|--------|---------|
| `Completed Steps` | A list of reviewed files/modules, invoked tools, created artifacts |
| `Findings` | Discovered modules and their paths, implementation patterns, data structures, extension points, existing dependencies |
| `Assumptions` | Any assumption made instead of asking the user |
| `Pending Questions` | All blocking questions in a single list (only when `clarification_needed`) |
| `User Answers` | Not filled by the agent — only by the orchestrator |

---

## What NOT to include in the context

- The full contents of reviewed files — only conclusions and paths
- Intermediate reasoning — only final findings
- Information already available from other artifacts in `task_dir` (specifications, design docs)

---

## The resume mechanism (in-session optimization)

If the orchestrator and the subagent execute inside the same session, the orchestrator MAY use
`resume agentId` to continue within that transcript instead of launching anew.

```
When resume works:           When it DOES NOT work:
  Analyst → question               User closed the IDE
  → answer in the same session      → returns tomorrow
  → resume agentId ✅              → agentId is stale ❌
```

**Rule:** `{role}-context.md` is the primary mechanism and always reliable.
`resume` is an additional optimization; apply it when the agentId is still current.
When using `resume`, the context file STILL MUST be written.

---

## Related resources

| Resource | Relation |
|--------|-------|
| [orchestrator.md](../workflows/orchestrator.md) | Protocol for passing task_dir and agentId |
| [tdd-policy.md](./tdd-policy.md) | Completion tags: implementation_error |
| [sdd-policy.md](./sdd-policy.md) | Completion tags: clarification_needed |

---
depends_on: []
---
