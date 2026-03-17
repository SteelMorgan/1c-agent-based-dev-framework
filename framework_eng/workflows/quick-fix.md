---
name: quick-fix
description: Quick fix in a single file without cross-review.
---

# Workflow: Quick Fix

> Three steps without cross-review. One file, < 20 lines, no architectural decisions, no new features.

## Steps

### 1. Find (Explorer → Economy)

`navigate_symbol` + `get_call_graph` → path to the module, dependencies, ensure the change is localized.

### 2. Fix (Developer → Mid)

The minimally necessary change according to `coding-standards`. No “improvements” beyond the task scope.

### 3. Check (Developer → Mid)

1. `check_syntax` — required
2. `run_tests` — if there are tests for the module
3. `get_diagnostics` — no LSP errors

## Escalation to Full-cycle

| Situation | Action |
|----------|--------|
| Tests fail after the change | Fix or escalate |
| Multiple modules / architecture / review / > 20 lines | Full-cycle |

**Protocol:** log the state → hand off to the orchestrator → full-cycle starting with Phase 1 (or Phase 3 if a spec exists).

---
depends_on:
  - framework/workflows/full-cycle.md
  - framework/subagents/explorer.md
  - framework/subagents/developer-code.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md
  - framework/skills/tool-usage/code-analysis/test-execution/SKILL.md
---
