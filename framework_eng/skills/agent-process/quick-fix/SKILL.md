---
name: quick-fix
description: MUST use WHEN the task is classified as simple (< 20 lines, 1 file, no new metadata objects, no architectural decisions). Provides a 3-step short cycle with a guard on the self path and mandatory verify.
installable: true
alwaysApply: false
---

# Skill: Quick Fix (short cycle)

> Three steps without cross-review. One file, < 20 lines, no architectural decisions, no new features,
> no new metadata objects.

## When it applies

Lead (main flow) classified the task as **simple** (see the orchestrator profile, Layer 1) and
selected the short cycle. In the short cycle, Lead may execute it directly or delegate one subagent
under the guard below.

## Guard on the self path (MUST, otherwise slippery slope)

> self-execution is the only mode where the main flow has no cross-review. Therefore the boundaries are strict
> and are checked BEFORE the edit begins.

- self is allowed ONLY within these boundaries: `< 20 lines, 1 file, no new metadata objects, no
  architectural decisions`;
- **exceeding any criterion -> forced transition to the full cycle (delegation), self is forbidden**;
- **the verify step (step 3) is mandatory EVEN for self** - the only compensation for the lack of cross-review.

## Steps

### 1. Find (Explorer → Economy)

`navigate_symbol` + `get_call_graph` → path to the module, dependencies, make sure the change is localized
(guard boundary confirmation: one file, no architectural links).

### 2. Fix (Developer → Mid)

The minimum necessary change according to `coding-standards`. No "improvements" beyond the task.

### 3. Verify (Developer → Mid) — MANDATORY, including for self

1. `get_diagnostics` — quick check of the changed file
2. `run_tests` — if there are tests for the module
3. `check_syntax` — final check before commit
4. Coverage for the runtime layer of the change:
   - if server logic/server method/query was changed - update and run a YaxUnit test; if there is no test, add a minimal YaxUnit test or escalate to the full cycle;
   - if the UI or client context was changed (form, command, button, client handler, `ОткрытьФорму`, visibility/accessibility) - perform a scenario check of the user action in the live infobase/test client: open the entrypoint, perform the action, make sure it starts/completes without an error;
   - if verification is technically impossible - explicitly record it in the response as an uncovered risk; silent omission is forbidden.

## Escalation to the full cycle

| Situation | Action |
|----------|----------|
| Tests fail after the change | Fix or escalate to full |
| Multiple modules / architecture / review / > 20 lines / new metadata object | full cycle |

**Escalation protocol:** record the state → **the orchestrator raises phase management in place**.
Orchestration discipline and the phase form are already durable in its profile (`framework/subagents/orchestrator.md`,
Layer 2); it reads the detailed phase mechanics from `framework/workflows/full-cycle/SKILL.md` when entering the phase.
This is NOT "handing off to an external document" and NOT launching another session - Lead simply puts on the hat
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
