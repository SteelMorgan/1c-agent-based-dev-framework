---
name: vanessa-run-loop
description: Policy requiring mandatory execution and artifact analysis after changing Vanessa Automation feature scenarios.
---

# Vanessa Automation Execution Policy

> When execution is mandatory: creating/changing `.feature`, launch parameters or `bddRunner.epf`.

## MUST

| Requirement | Description |
|------------|-------------|
| Pre-run config sanity | Before launching `vrunner` the agent MUST read the selected `vrunner-va-run-*.json` and the related `va-params-run-*.json` and confirm that `КаталогФич` (feature directory) points to the feature folder of the **current task**. On mismatch — recreate a task-dedicated config (`vrunner-va-run-<taskID>.json` + `va-params-run-<taskID>.json`) and launch with it; the CLI flag `--vanessasettings` is only a safety net, not the primary mechanism |
| Mandatory execution | After changing a scenario the agent MUST perform a scenario run |
| Event log monitoring during execution | The agent MUST poll the event log every **20 seconds** while the test is running |
| Abort on error | If `Error`-level entries appear in the event log, the agent MUST abort the test and move to diagnostics |
| Deadlock detection | If there are neither errors nor user action entries in the event log for >60 seconds, the agent MUST capture screenshots and assess whether the test is still alive |
| Mandatory artifact analysis | After the run the agent MUST inspect `va-status.json` and `vanessa-execution.log` |
| Mandatory event log analysis | After the run the agent MUST review `event-log` if the scenario failed or the launch looks suspicious |
| Post-success validation | When `va-status == 0` the agent MUST verify the logs for completeness: every step executed, no skipped/missing steps |
| Explicit classification | On failure the agent MUST return an error class rather than just failure text |

## Success indicator

The minimal success indicator:

1. the file `va-status.json` exists;
2. the value in `va-status.json` equals `0`;
3. the file `vanessa-execution.log` is created;
4. the logs contain no skipped or missing steps;
5. the number of executed steps is > 0.

Vanessa considers the run successful even if no step was found or some steps were skipped — this is a **false success**. The agent MUST detect such cases.

## Pre-run config check

Before starting `vrunner` the runner agent performs the following procedure:

1. Read the selected `vrunner-va-run-*.json` → find the `--vanessasettings` key (path to `va-params-run-*.json`).
2. Read the referenced `va-params-run-*.json` → grep the value of `КаталогФич` (feature directory).
3. Compare it with the expected `vanessa-tests/features/tasks/<taskID>/`.
4. On mismatch — create a task-dedicated pair of config files (using the templates in `tools/runtime/vanessa/`), substitute the paths, and launch with them.
5. Record the config file used and the feature directory in `{role}-context.md`.

**Why:** the shared `vrunner-va-run.json` / `va-params-run.json` in the project usually carry a "stale" `КаталогФич` from the previous task. Running without a pre-check picks up a foreign feature directory and silently runs another task's scenarios for tens of minutes. The pre-check inside the runner agent is the primary defense; the CLI flag `--vanessasettings` is only a safety net.

The detailed procedure for monitoring, post-validation, and diagnostics is described by the skills rather than by this rule.

---
depends_on: []
---
