---
name: quick-fix
description: MUST use WHEN the task is classified as simple (< 20 lines, 1 file, no new metadata objects, no architectural decisions). Provides a short cycle of 3 steps with a guard on the self path and mandatory verify.
alwaysApply: false
---

# Skill: Quick Fix (short-cycle)

> Three steps without cross-review. One file, < 20 lines, no architectural decisions, no new features,
> no new metadata objects.

## When it applies

The Lead (main flow) has classified the task as **simple** (see orchestrator profile, Layer 1) and
chosen the short cycle. In the short cycle, the Lead may execute it personally or delegate one subagent
under the guard below.

## Self-path guard (MUST, otherwise slippery slope)

> self execution is the only mode where the main flow has no cross-review. Therefore the boundaries are strict
> and are checked BEFORE any modification starts.

- self is allowed ONLY within the bounds: `< 20 lines, 1 file, no new metadata objects, no
  architectural decisions`;
- **exceeding any criterion → mandatory transition to full cycle (delegation), self is forbidden**;
- the **verify step (step 3) is mandatory EVEN for self** — the only compensation for the absence
  of cross-review.

## Steps

### 1. Find (Explorer → Economy)

`navigate_symbol` + `get_call_graph` → path to the module, dependencies, confirm that the change is localized
(confirmation of the guard boundaries: one file, no architectural links).

### 2. Fix (Developer → Mid)

The minimum necessary change according to `coding-standards`. No "improvements" beyond the task scope.

### 3. Verify (Developer → Mid) — REQUIRED, including for self

1. `get_diagnostics` — quick check of the changed file
2. `run_tests` — if there are tests for the module
3. `check_syntax` — final check before commit

## Escalation to full cycle

| Situation | Action |
|----------|----------|
| Tests fail after the change | Fix or escalate to full |
| Multiple modules / architecture / review / > 20 lines / new metadata object | full cycle |

**Escalation protocol:** record the state → **the orchestrator itself raises the phase prompt**.
The orchestration discipline and the phase form are already durable in its profile (`framework/subagents/orchestrator.md`,
Layer 2); it reads the detailed phase mechanics from `framework/workflows/full-cycle.md` upon entering the phase.
This is NOT "handing off to an external document" and NOT starting another session — the Lead simply puts on the hat
of the full-cycle orchestrator and proceeds with Phase 1 (or Phase 3, if the spec already exists).

---
depends_on:
  - framework/subagents/orchestrator.md
  - framework/workflows/full-cycle.md
  - framework/subagents/explorer.md
  - framework/subagents/developer-code.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
