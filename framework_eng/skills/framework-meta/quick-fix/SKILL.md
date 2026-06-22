---
name: quick-fix
description: MUST use WHEN task is classified as simple (< 20 lines, 1 file, no new metadata objects, no architectural decisions). Provides a short cycle of 3 steps with a guard on self-path and mandatory verify.
alwaysApply: false
---

# Skill: Quick Fix (short cycle)

> Three steps without cross-review. One file, < 20 lines, no architectural decisions, no new features,
> no new metadata objects.

## When it applies

Lead (main flow) classified the task as **simple** (see orchestrator profile, Layer 1) and
chose the short cycle. In the short cycle, Lead may execute it himself or delegate one subagent - under
the guard below.

## Guard on self-path (MUST, otherwise slippery slope)

> self-execution is the only mode where the main flow has no cross-review. Therefore the boundaries are strict
> and are checked BEFORE any change is made.

- self is allowed ONLY within the boundaries: `< 20 lines, 1 file, no new metadata objects, no
  architectural decisions`;
- **exceeding any criterion -> forced transition to full cycle (delegation), self is forbidden**;
- **the verify step (step 3) is mandatory EVEN for self** - the only compensation for the absence of cross-review.

## Steps

### 1. Find (Explorer → Economy)

`navigate_symbol` + `get_call_graph` → path to the module, dependencies, make sure the change is localized
(confirmation of guard boundaries: one file, no architectural links).

### 2. Fix (Developer → Mid)

The minimum necessary change according to `coding-standards`. No "improvements" beyond the scope of the task.

### 3. Verify (Developer → Mid) - MANDATORY, including for self

1. `get_diagnostics` - quick check of the changed file
2. `run_tests` - if there are tests for the module
3. `check_syntax` - final check before commit

## Escalation to full cycle

| Situation | Action |
|----------|----------|
| Tests fail after the change | Fix or escalate to full |
| Multiple modules / architecture / review / > 20 lines / new metadata object | full cycle |

**Escalation protocol:** record the state -> **the orchestrator, in its own context, raises phase management**.
The orchestration discipline and phase form are already durable in its profile (`framework/subagents/orchestrator.md`,
Layer 2); it reads the detailed phase mechanics from `framework/workflows/full-cycle/SKILL.md` when entering the phase.
This is NOT "passing to an external document" and NOT starting another session - Lead simply puts on the hat
of the full-cycle orchestrator and proceeds with Phase 1 (or Phase 3, if the spec already exists).

---
depends_on:
  - framework/subagents/orchestrator.md
  - framework/workflows/full-cycle/SKILL.md
  - framework/subagents/explorer.md
  - framework/subagents/developer-code.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
