---
name: git-workflow
description: MUST use WHEN the orchestrator creates a task branch, commits a phase, merges a task, or rolls back changes. Provides branch strategy, phase commit format, squash-merge, and a ban on file deletion.
alwaysApply: false
---

# Git Workflow Procedure

> The body has been moved out of the `git-workflow` rule. The goal is a transparent history (one commit = one logical unit of work), task-level rollback capability, and careful handling of generated 1C artifacts. The guardrail (who commits, deletion ban) stays in the rule - here only the "how".

## Principles

1. **One task - one branch.** Isolate the task on a `task/TASK-XXX-<slug>` branch derived from the parent branch (usually `main`).
2. **Commit = checkpoint.** Each approved full-cycle phase is recorded in a commit. This provides recovery points if the next phase fails.
3. **Squash-merge into the parent.** The task enters the parent branch as a single commit. The phase history remains in the task branch for postmortem analysis.
4. **Subagents do not commit.** Only the orchestrator has `git commit` rights (within the task and for the final merge when the conditions in § "Final merge" are met) and the user.
5. **The `//++agent` markers and git complement each other.** Markers provide a local audit trail in the source code (see `agent-code-marking`), while git provides history over time. Markers in code are NOT removed before commit.
6. **No force-pushes or destructive operations to the parent branch.** Within a task branch, rebase/amend are allowed as long as the branch remains local.
7. **Deletion of files from git is forbidden without explicit user permission.** This principle is critical - see the separate "File deletion ban" section below.

## Branch Strategy

### Creating a task branch

When classifying a task as **medium/complex** (full-cycle), the orchestrator:

1. Ensures the working tree is clean (`git status --porcelain` is empty). If not, it is either an unfinished previous task (escalate to user) or manual user edits (require separate discussion).
2. Starts from the current parent branch: `git fetch origin && git switch <parent>` (for example `main` or `master` - determined by the project).
3. Creates a task branch: `git switch -c task/TASK-XXX-<slug>`, where `<slug>` is a short kebab-case name (maximum 4-5 words).
4. Records the branch name and the parent SHA at the moment of branching in `orchestrator-context.md`.

### Quick-fix

For a **simple task** (see `quick-fix.md`: <20 lines, one file, no new artifacts), a separate branch is NOT created. The commit goes directly to the project's current branch as a single commit tagged `fix(TASK-XXX): ...`.

### Subtasks (TASK-XXX.Y)

If a task is split into numbered subtasks (TASK-103.1, TASK-103.2), each subtask has **its own branch** `task/TASK-103.1-<slug>`, and each is merged into `main` independently. If the work is indivisible, there is one branch `task/TASK-103-<slug>`, and commits in it are tagged `TASK-103.1`, `TASK-103.2`.

## What We Commit and What We Do Not

### MUST commit

| Category | Paths |
|-----------|------|
| Source code | `src/xml/**`, `exts/<extension>/**` (except protected ones, see `protected-paths`) |
| Unit tests | `exts/TESTS/**` |
| BDD scenarios | `vanessa-tests/features/**/*.feature`, `vanessa-tests/support/**` |
| Task documentation | `tasks/<TASK-XXX>/**` in full - specification, technical design, task-breakdown, agent contexts, final report, images |
| Configuration metadata | `ConfigDumpInfo.xml`, `Configuration.xml` - even if they are auto-generated, they are a valid part of the Designer format |
| `//++agent` markers in code | Markers inside BSL remain in commits - they are part of the source |

### MUST NOT commit (`.gitignore`)

| Category | Paths |
|-----------|------|
| Vanessa run artifacts | `vanessa-tests/reports/**`, `vanessa-tests/runs/**`, `vanessa-tests/allure/**` |
| Runner logs | `**/*.runner.log`, `**/runner.log`, `**/temp/**` |
| v8-runner artifacts | `workPath/**`, `v8project.local.yaml` |
| Local settings | `.install-session.json` (if it contains local state rather than a project config) |
| Caches and temporary files | `*.cache`, `*.tmp`, IDE-local (`.idea/`, `.vscode/` - at the project's discretion) |

The project's specific `.gitignore` is maintained in the project; this rule fixes the categories.

## When the Orchestrator Commits

The commit is made **after passing the approval gate / review** for each full-cycle phase.

| Phase | What goes into the commit | Message type |
|------|----------------------|---------------|
| Phase 1 (Analyst) after user approval | `tasks/TASK-XXX/spec.md`, `.context/analyst-context.md`, `.context/reviewer-context-spec.md` | `spec(TASK-XXX)` |
| Phase 2 (Architect) after user approval | `tasks/TASK-XXX/technical-design.md`, `task-breakdown.json`, `.context/architect-context.md`, `.context/reviewer-context-arch.md` | `design(TASK-XXX)` |
| Phase 3a (Scenario-Author) after Reviewer | `vanessa-tests/features/**/*.feature`, `.context/scenario-author-context.md`, `.context/reviewer-context-bdd.md` | `bdd(TASK-XXX): scenarios` |
| Phase 3b (Developer-Tests) after Reviewer | unit tests in `exts/TESTS/**`, `.context/developer-tests-context.md`, `.context/reviewer-context-tests.md` | `test(TASK-XXX)` |
| Phase 3c (Scenario-Coder) after Reviewer | Vanessa step implementation in `vanessa-tests/features/steps/**`, `vanessa-tests/support/**`, `.context/scenario-coder-context.md` | `bdd(TASK-XXX): steps` |
| Phase 3d (Developer-Code) after Reviewer | implementation in `src/xml/**`, `exts/<ext>/**` (with `//++agent` markers), `.context/developer-code-context.md`, `.context/reviewer-context-code.md` | `feat(TASK-XXX)` / `fix` / `refactor` |
| Phase 4 (Tester) after Reviewer | additional tests, `.context/tester-context.md`, `.context/reviewer-context-tester.md` | `test(TASK-XXX): coverage` |
| Final report | `tasks/TASK-XXX/final-report.md` | `chore(TASK-XXX): final report` |

**If a phase does not pass review (BLOCK):** the phase authors make changes in the working tree, **without a commit**, until the review is closed. Only the final approved version is committed.

**Bug report from Debugger:** a separate commit `fix(TASK-XXX/debug): <symptom>`. After the Debugger fix is accepted.

## Commit Message Format

```
<тип>(TASK-XXX[/phase]): <короткое описание на русском, до 72 символов>

<необязательное расширенное пояснение: чем эта фаза отличается от предыдущей,
какие важные решения зафиксированы. Однострочные коммиты допустимы для простых
случаев.>

Phase: <название фазы из full-cycle>
Agent: <subagent_type, например developer-code>
Co-Authored-By: Claude <noreply@anthropic.com>
```

### Types

| Type | When |
|-----|-------|
| `spec` | Specification (Phase 1) |
| `design` | Technical design (Phase 2) |
| `bdd` | Vanessa scenarios and steps (Phase 3a / 3c) |
| `test` | Unit/integration tests (Phase 3b / 4) |
| `feat` | New functionality (Phase 3d) |
| `fix` | Bug fix (Phase 3d or quick-fix) |
| `refactor` | Refactoring without behavior changes |
| `perf` | Performance optimization |
| `chore` | Tech debt, marker cleanup, final report |
| `docs` | Documentation (CLAUDE.md, rules, skills) |

## Final Merge of the Task into the Parent Branch

### Conditions for orchestrator auto-merge

The orchestrator has the right to perform a squash-merge into the parent branch **only when ALL conditions are met**:

1. `cross-provider-review` in **gate mode** returned `verdict: PASS` (see orchestrator.md § 7.2).
2. `tasks/TASK-XXX/final-report.md` has been created and contains a `cross_provider_review` block with `review_id`.
3. All test runs (unit + Vanessa) in the final iteration completed with exit_code=0, and raw stdout is attached to the final report.
4. `git status --porcelain` is clean - there are no unsaved files outside the task scope.
5. The parent branch has not moved too far ahead: if it has, the orchestrator first runs `git rebase <parent>` in the task branch and reruns the tests. If rebase conflicts occur - escalate to user.

If at least one condition is not met, the orchestrator does NOT merge, but informs the user and waits for their decision. User confirmation for the merge itself is NOT required when all conditions are met - the orchestrator automatically merges as many tasks as have passed the gate.

### Squash-merge command

```bash
git switch <parent>
git merge --squash task/TASK-XXX-<slug>
git commit -m "TASK-XXX: <название задачи>

<краткое содержание изменений, 3-5 строк>

Phases completed: 1, 2, 3a, 3b, 3c, 3d, 4
Cross-provider-review: PASS (id=<review_id>)
Task branch: task/TASK-XXX-<slug>

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### After merge

1. The task branch is **not deleted** for at least 2 weeks - in case a rollback or postmortem is needed.
2. The orchestrator writes to `orchestrator-context.md`: `MERGED: <merge_sha> → <parent>`.
3. If the project has a remote and push is configured, the orchestrator does NOT push automatically. Pushing to the remote is the user's decision.

## File Deletion Ban in git

> The most critical protection against loss of work. Applies to all agents - orchestrator, subagents, debugger. The short invariant is duplicated in the always-on `git-workflow` rule; the procedure details are here.

### PROHIBITED without explicit user permission

- `git rm <path>` - remove a file from tracking
- `git rm --cached <path>` - remove from tracking while keeping it in the working tree
- Physical deletion of a file that was under git control (that is, `git ls-files` shows it), followed by including the deletion in a commit
- `git reset --hard <commit>` outside the task branch or with loss of someone else's changes
- Any operation that causes `git log -- <path>` in the parent after merge to show "file deleted"

### When deletion is allowed

1. **Explicit written user permission in the current dialog.** Without confirmation "yes, delete this file" - do not delete. Permissions stored in memory or in CLAUDE.md do NOT count as explicit permission for the current deletion.
2. **The file falls under `.gitignore`** and was previously committed by mistake. In this case, `git rm --cached` is allowed, but the orchestrator must explicitly tell the user: "this file is leaving git but remains local because it matches .gitignore".
3. **Atomic rename** (`git mv`). If a file is renamed / moved elsewhere, that is not deletion but a move, and it is transparent to history.

### What to do instead of deleting

| Situation | What to do instead of deletion |
|----------|----------------------------|
| Old code is no longer needed | Comment it out with the `//--agent` marker (see `agent-code-marking`). Do not physically cut out the file/lines |
| A phase artifact turned out to be incorrect | Overwrite it with a new version. The old one will remain in commit history |
| The task folder seems outdated | DO NOT delete `tasks/<TASK-XXX>/`. It is an audit trail of the work. If the task is canceled, leave it as is; you may add a note in `final-report.md` |
| It seems the file is not needed in the repo at all | Escalate to the user with justification. Only the user performs deletion |

### Behavior when a violation is suspected

If the orchestrator detects a `D <path>` (deleted) line in `git status` OR an `R <old> -> <new>` (rename) line that was NOT explicitly initiated by the current phase:

1. Do NOT commit the change
2. Record in `orchestrator-context.md`: `SUSPECTED_DELETION: <path>`
3. Escalate to the user with the specific question "did you delete `<path>` or is this a side effect?"

## Rollback

| Scenario | Action |
|----------|----------|
| The phase failed review, ≥3 iterations | Right before BLOCK on the 3rd iteration: the phase commit is NOT made. Escalate to user. |
| A bug in implementation was found after the Phase 3d commit | A separate `fix(TASK-XXX/debug): ...` commit on the same branch. |
| The task in the parent turned out to be bad | `git revert <squash-sha>` in the parent. One revert removes the task entirely. |
| The task was abandoned in the middle | The branch remains as is. Optional - rename to `abandoned/TASK-XXX-<slug>`. |
| Phase N needs to be redone from scratch | `git reset --hard <commit_phase_N-1>` in the task branch, then redo it. |

## Parallel Phases (3a and 3b)

Phase 3a (Scenario-Author) and Phase 3b (Developer-Tests) run in parallel. **The orchestrator commits the results sequentially:** first whoever finishes first, then the second one. If they touched shared files (which should not happen by design - 3a writes `.feature`, 3b writes `.bsl`) - this is a signal to investigate who stepped outside their phase scope.

## What NOT to Do (Anti-Patterns)

| Anti-pattern | Why it is bad |
|---------------|--------------|
| A subagent commits with `git commit` on its own | The subagent does not see the full phase scope and may commit junk. The commit right belongs to the orchestrator. |
| A separate commit called "ConfigDumpInfo update" | `ConfigDumpInfo.xml` changes on every export and must be included in the commit of the phase that generated it. |
| Removing `//++agent` markers before commit | Markers are part of the source and provide context months later. They are removed only during the next major change (see `agent-code-marking`). |
| Force-push to the parent branch | Overwrites other people's changes and breaks blame. |
| Committing `.context/` separately from phase artifacts | Contexts go in the commit of their phase - they describe exactly that work. |
| Merge into `main` without `cross-provider-review` PASS | Violates the gate protocol in `orchestrator.md` § 7.3. |
| Deleting a file from git without explicit user permission | Loss of work and audit trail. See § "File deletion ban". |
| `git rm` or `git rm --cached` initiated by an agent | Only the user has the right to remove files from tracking. |
| Deleting the `tasks/<TASK-XXX>/` folder of a canceled task | Task documentation is part of the audit trail. Mark it as canceled, but do not delete it. |

## Interaction with Other Rules

- `agent-code-marking` - markers inside commits; required for BSL changes.
- `agent-context-protocol` - agent contexts are included in the commit of their phase.
- `orchestrator.md` § 7 - the `cross-provider-review` gate is a mandatory condition for auto-merge.
- `protected-paths` - files in protected paths are not committed (changes there are prohibited altogether).
- `quick-fix.md` - simple path without a task branch.

---
depends_on:
  - agent-code-marking
  - agent-context-protocol
  - protected-paths
  - framework/skills/framework-meta/quick-fix/SKILL.md
  - framework/workflows/full-cycle.md
  - framework/workflows/orchestrator.md
---
