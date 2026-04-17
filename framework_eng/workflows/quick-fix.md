---
name: quick-fix
description: Quick fix in one file without cross-review.
---

# Workflow: Quick Fix

> Three steps without cross-review. One file, < 20 lines, no architectural decisions, no new features.

## Steps

### 1. Find (Explorer → Economy)

`navigate_symbol` + `get_call_graph` → path to the module, its dependencies, make sure the change is localized.

### 2. Fix (Developer → Mid)

The minimally required change according to `coding-standards`. No “improvements” beyond the task scope.

### 3. Verify (Developer → Mid)

1. `get_diagnostics` — quick check of the changed file
2. `run_tests` — if there are tests for the module
3. `check_syntax` — final check before commit

## Escalation to Full-cycle

| Situation | Action |
|----------|--------|
| Tests fail after the change | Fix or escalate |
| Multiple modules / architecture / review / > 20 lines | Full-cycle |

**Protocol:** record the state → hand off to the orchestrator → full-cycle with Phase 1 (or Phase 3 if there is a spec).

---
depends_on:
  - framework/workflows/full-cycle.md
  - framework/subagents/explorer.md
  - framework/subagents/developer-code.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md
  - framework/skills/tool-usage/code-analysis/test-execution/SKILL.md
---
