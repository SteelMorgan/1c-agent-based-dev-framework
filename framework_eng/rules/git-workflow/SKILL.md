---
name: git-workflow
description: Git guardrail for the agentic cycle - subagents do not commit; deletion/git rm is forbidden without explicit user permission; commit and merge are only for the orchestrator/user. Procedure -> `git-workflow` skill.
alwaysApply: true
---
# Git Workflow Policy

> **Trigger:** working with git (commits, branches, task merge, rollback). When triggered, apply the `git-workflow` skill (`framework/skills/agent-process/git-workflow/SKILL.md`): branch strategy, phase-based commit format, squash-merge, rollback.

The guardrail below must always be visible, regardless of whether the skill is loaded or not.

## MUST (invariant, always)

| Prohibition / rule | Description |
|---|---|
| **Subagents do NOT commit** | The `git commit` right belongs ONLY to the orchestrator (within the task and the final merge) and to the user. A subagent does not see the full phase scope and may commit junk. |
| **Commit and merge are only for the orchestrator / user** | No subagent initiates a commit or merge on its own. |
| **Deletion of files from git is forbidden without explicit user permission** | `git rm`, `git rm --cached`, physical deletion of a file under git with inclusion in the commit, `git reset --hard` with loss of someone else's changes - are FORBIDDEN without an explicit "yes, delete it" in the current dialogue. Permissions from memory / CLAUDE.md do NOT count. Instead of deleting - comment out via `//--agent`, overwrite with a new version, escalate. The `tasks/<TASK-XXX>/` folder must never be deleted. |
| **No force-pushes and no destructive operations in the parent branch** | Inside the local task branch, rebase/amend are allowed; in the parent branch - no. |
| **Suspicion of deletion -> stop** | `D <path>` or `R <old> -> <new>` in `git status`, not explicitly initiated by the current phase -> DO NOT commit, record `SUSPECTED_DELETION: <path>` in `orchestrator-context.md`, escalate to the user. |

Details of the "how" (creating the task branch, what to commit, message format, squash-merge conditions and command, rollback scenarios, exceptions for deletion) are in the `git-workflow` skill.

---
depends_on:
  - framework/skills/agent-process/git-workflow/SKILL.md
  - agent-code-marking
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/protected-paths/SKILL.md
---
