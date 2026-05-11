---
name: vanessa-run-loop
description: Policy requiring mandatory execution and artifact analysis after changing Vanessa Automation feature scenarios.
---

# Vanessa Automation Execution Policy

> When execution is mandatory: creating/changing `.feature`, launch parameters, `tests.va` configuration in `v8project.yaml` or the client MCP extension.

## MUST

| Requirement | Description |
|------------|-------------|
| Pre-run config sanity | Before launching the agent MUST read the active profile from `tests.va` in `v8project.yaml` (`tests.va.profile` or the explicitly passed `--profile`) and ensure that the profile's feature directory path points to the feature folder of the **current task**. On mismatch — add a task-dedicated `tests.va.profiles.<taskID>` profile and run with it. |
| Mandatory execution | After changing a scenario the agent MUST perform a scenario run through `v8-runner test va …`. |
| Event log monitoring during execution | The agent MUST poll the event log every **20 seconds** while the test is running. |
| Abort on error | If `Error`-level entries appear in the event log, the agent MUST abort the test and move to diagnostics. |
| Deadlock detection | If there are neither errors nor user action entries in the event log for >60 seconds, the agent MUST capture screenshots and assess whether the test is still alive. |
| Mandatory artifact analysis | After the run the agent MUST inspect `va-status.json` and `vanessa-execution.log` under `workPath/temp/<runner-id>/runs/<run-id>/`. |
| Mandatory event log analysis | After the run the agent MUST inspect `event-log` if the scenario did not pass or the launch looks suspicious. |
| Post-success validation | When `va-status == 0` the agent MUST verify the logs for completeness: all steps executed, no skipped/missing steps. |
| Explicit classification | On failure the agent MUST return an error class rather than just failure text. See `vanessa-diagnostics`. |
| Do not bypass v8-runner | The agent MUST NOT construct `1cv8c` or `vrunner` commands manually when `v8-runner test va` is available. Exception — diagnostics of `v8-runner` itself after explicit user consent. |

## Success indicator

The minimal success indicator:

1. the file `va-status.json` exists;
2. the value in `va-status.json` equals `0`;
3. the file `vanessa-execution.log` is created;
4. the logs contain no skipped or missing steps;
5. the number of executed steps is > 0.

Vanessa considers the run successful even if no step was found or some steps were skipped — this is a **false success**. The agent MUST detect such cases (see `vanessa-diagnostics`).

## Pre-run config check (for v8-runner)

Before starting `v8-runner test va` the runner agent performs the following procedure:

1. Read `v8project.yaml` → section `tests.va`, active profile (`tests.va.profile` or passed through `--profile`).
2. Compare the feature path in the profile with the expected `vanessa-tests/features/tasks/<taskID>/`.
3. On mismatch — add a task-dedicated profile `tests.va.profiles.<taskID>` (either in `v8project.yaml` or through `v8project.local.yaml`), launch with it.
4. When working with tags remember: `filter_tags` / `ignore_tags` are written **without a leading `@`** in `СписокТеговОтбор` / `СписокТеговИсключение` (see `v8-runner/references/testing.md`).
5. Record the profile used and the feature directory in `{role}-context.md`.

**Why:** the shared profile in the project usually contains a stale path from the previous task. Running without a pre-check picks up another task's feature directory and silently runs that task's scenarios for tens of minutes.

The detailed procedure for monitoring, post-validation, and diagnostics is described by the `v8-runner` and `vanessa-diagnostics` skills rather than by this rule.

---
depends_on:
  - framework/skills/tool-usage/v8-runner/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
---
