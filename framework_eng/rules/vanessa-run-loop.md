---
name: vanessa-run-loop
description: Policy requiring mandatory execution and artifact analysis after changing Vanessa Automation feature scenarios.
---

# Vanessa Automation Execution Policy

> When execution is mandatory: creating/changing `.feature`, launch parameters or `bddRunner.epf`.

## MUST

| Requirement | Description |
|------------|-------------|
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

The detailed procedure for monitoring, post-validation, and diagnostics is described by the skills rather than by this rule.

---
depends_on: []
---
