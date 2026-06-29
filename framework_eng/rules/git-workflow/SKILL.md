---
name: git-workflow
description: "Apply git workflow guardrails before commit/delete"
alwaysApply: true
---
# Git Workflow Policy

> **Trigger:** work with git (commits, branches, merge tasks, rollback). When triggered, apply the `git-workflow` skill (`framework/skills/agent-process/git-workflow/SKILL.md`): branch strategy, phase-based commit format, squash-merge, rollback.

The guardrail below must always be visible, regardless of whether the skill is loaded or not.

## MUST (invariant, always)

| Prohibition / rule | Description |
|---|---|
| **Subagents DO NOT commit** | The `git commit` right belongs ONLY to the orchestrator (within the task and the final merge) and to the user. A subagent does not see the full phase scope and may commit garbage. |
| **Commit and merge are only for the orchestrator / user** | No subagent initiates a commit or merge on its own. |
| **Prohibition on deleting files from git without explicit user permission** | `git rm`, `git rm --cached`, physical deletion of a file under git control with inclusion in the commit, `git reset --hard` with loss of someone else's changes are PROHIBITED without an explicit "yes, delete it" in the current dialogue. Permissions from memory / CLAUDE.md do NOT count. Instead of deleting, comment out via `//--agent`, overwrite with a new version, escalate. Never delete the `tasks/<TASK-XXX>/` folder. |
| **No force-push or destructive operations in the parent branch** | Within the local task branch, rebase/amend are allowed; in the parent branch, they are not. |
| **Suspicion of deletion -> stop** | `D <path>` or `R <old> -> <new>` in `git status` not explicitly initiated by the current phase -> DO NOT commit, record `SUSPECTED_DELETION: <path>` in `orchestrator-context.md`, escalate to the user. |

Details of the "how" (creating a task branch, what to commit, message format, conditions and command for squash-merge, rollback scenarios, exceptions for deletion) are in the `git-workflow` skill.

---
depends_on:
  - framework/skills/agent-process/git-workflow/SKILL.md
  - agent-code-marking
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/protected-paths/SKILL.md
---
