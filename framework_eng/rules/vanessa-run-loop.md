---
name: vanessa-run-loop
description: Policy for mandatory execution and artifact analysis after modifying Vanessa Automation feature scenarios.
---

# Vanessa Automation Execution Policy

> When execution is mandatory: creating/changing `.feature`, launch parameters, `tests.va` configuration in `v8project.yaml`, or the client MCP extension.

Execution is performed through `v8-runner test va` (see the `v8-runner` skill → `references/testing.md`). Previously the direct wrapper `vrunner` + `vrunner-va-run-*.json` / `va-params-run-*.json` was used — it has been deprecated.

## MUST

| Requirement | Description |
|------------|-------------|
| Pre-run config sanity | Before launching, the agent MUST read the active profile from `tests.va` in `v8project.yaml` (`tests.va.profile` or the explicitly passed `--profile`) and ensure that the profile's feature directory path points to the feature folder of the **current task**. If it does not match, add a task-dedicated `tests.va.profiles.<taskID>` profile and run with it. |
| Mandatory execution | After modifying a scenario, the agent MUST perform a scenario run through `v8-runner test va …`. |
| v8-runner stdout monitoring | The agent MUST read v8-runner stdout every **20 seconds** while the test is running. Standard output contains success and failure markers immediately (`[diagnostic]`, `[artifact]`, `ERROR: runtime error: test run reported failures`, etc.) — this is more reliable than the event log. |
| Abort on error | If an `ERROR:` line appears in stdout (for example, `ERROR: runtime error: test run reported failures`) — the agent MUST stop waiting, read `runner.log` + `junit/junit.xml` in the run directory, and move to diagnostics. Inspect the event log additionally if the primary artifacts are insufficient. |
| Deadlock detection | If there are no new lines in v8-runner stdout for >60 seconds AND the `1cv8c.*vanessa-automation` process is still alive, the agent MUST capture screenshots (noVNC/X11) and assess whether the test is alive. |
| Correct exit condition | The wait condition ends when `va-status.log` appears (created on BOTH success and failure) OR the `1cv8c.*vanessa-automation` process disappears OR an `ERROR:` line appears in stdout. **Do not use only `va-status.json`** — it is created only on normal scenario completion; on early failures (step error, client crash) it will not be there, and the blocking wait will hang. |
| Mandatory artifact analysis | After the run the agent MUST inspect `va-status.json` and `vanessa-execution.log` under `workPath/temp/<runner-id>/runs/<run-id>/`. |
| Mandatory event log analysis | After the run the agent MUST inspect `event-log` if the scenario did not pass or the launch looks suspicious. |
| Post-success validation | After `va-status == 0` the agent MUST verify the logs for completeness: all steps executed, no skipped/missing steps. |
| Explicit classification | On failure the agent MUST return an error class, not just failure text. See `vanessa-diagnostics`. |
| Do not bypass v8-runner | The agent MUST NOT assemble `1cv8c` or `vrunner` commands manually when `v8-runner test va` is available. Exception: diagnostics of `v8-runner` itself after explicit user consent. |

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
3. On mismatch, add a task-dedicated profile `tests.va.profiles.<taskID>` (either in `v8project.yaml` or through `v8project.local.yaml`), and run with it.
4. When working with tags, remember: `filter_tags` / `ignore_tags` are written **without a leading `@`** in `СписокТеговОтбор` / `СписокТеговИсключение` (see `v8-runner/references/testing.md`).
5. Record the used profile and the feature directory in `{role}-context.md`.

**Why:** the shared profile in the project usually contains a stale path from the previous task. Running without a pre-check picks up another task's feature directory and silently runs that task's scenarios for tens of minutes.

The detailed procedure for monitoring, post-validation, and diagnostics is described by the `v8-runner` and `vanessa-diagnostics` skills, not by this rule.

---
depends_on:
  - framework/skills/tool-usage/v8-runner/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
---
