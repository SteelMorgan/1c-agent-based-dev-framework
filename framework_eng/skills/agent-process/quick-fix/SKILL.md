---
name: quick-fix
description: MUST use WHEN the task is classified as simple (< 20 lines, 1 file, no new metadata objects, no architectural decisions). Provides a short cycle of 3 steps with a guard on the self path and mandatory verify.
installable: true
alwaysApply: false
---

# Skill: Quick Fix (short cycle)

> Three steps without cross-review. One file, < 20 lines, no architectural decisions, no new features,
> no new metadata objects.

## When it applies

Lead (main flow) classified the task as **simple** (see the orchestrator profile, Layer 1) and
selected the short cycle. In the short cycle, the Lead may execute it themselves or delegate one subagent - subject to
the guard below.

## Guard on the self path (MUST, otherwise slippery slope)

> self-execution is the only mode where the main flow has no cross-review. Therefore the boundaries are strict
> and are checked BEFORE editing begins.

- self is allowed ONLY within the boundaries: `< 20 lines, 1 file, no new metadata objects, no
  architectural decisions`;
- **exceeding any criterion -> mandatory transition to full cycle (delegation), self is forbidden**;
- the **verify step (step 3) is mandatory EVEN for self** - the only compensation for the lack of cross-review.

## Steps

### 1. Find (Explorer -> Economy)

`navigate_symbol` + `get_call_graph` -> path to the module, dependencies, make sure the change is localized
(confirmation of the guard boundaries: one file, no architectural links).

### 2. Fix (Developer -> Mid)

The minimal necessary change according to `coding-standards`. No "improvements" beyond the scope of the task.

### 3. Verify (Developer -> Mid) - REQUIRED, including for self

1. `get_diagnostics` - quick check of the changed file
2. `run_tests` - if there are tests for the module
3. `check_syntax` - final check before commit
4. Coverage for the runtime layer of the change:
   - if server logic/server method/query was changed - update and run a YaxUnit test; if there is no test, add a minimal YaxUnit test or escalate to full-cycle;
   - if the UI or client context was changed (form, command, button, client handler, `ОткрытьФорму`, visibility/availability) - perform a scenario check of the user action in a live infobase/test client: open the entrypoint, perform the action, ensure it starts/finishes without error;
   - if verification is technically impossible - explicitly record this in the response as uncovered risk; silent skipping is forbidden.

## Escalation to full cycle

| Situation | Action |
|----------|----------|
| Tests fail after the change | Fix or escalate to full |
| Multiple modules / architecture / review / > 20 lines / new metadata object | full cycle |

**Escalation protocol:** record the state -> **the orchestrator raises the phase management on its own**.
The orchestration discipline and phase shape are already durable in its profile (`framework/subagents/orchestrator.md`,
Layer 2); it reads the detailed phase mechanics from `framework/workflows/full-cycle/SKILL.md` when entering the phase.
This is NOT "handing off to an external document" and NOT starting another session - the Lead simply puts on
the full-cycle orchestrator hat and proceeds with Phase 1 (or Phase 3, if the spec already exists).

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
