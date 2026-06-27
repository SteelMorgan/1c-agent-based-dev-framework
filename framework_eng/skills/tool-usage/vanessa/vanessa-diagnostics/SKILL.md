---
name: vanessa-diagnostics
description: "Vanessa diagnostics: failures, artifacts, and causes"
---

# Vanessa Automation Diagnostics

Vanessa is launched through `v8-runner test va` (see the `v8-runner` skill → `references/testing.md`). This skill is about how to analyze a failed run.

## Run Artifacts

There are two layers - do not confuse them:

| Layer | What it writes | Where it is located |
|------|-----------|-----------|
| Vanessa artifacts | the VA player itself (`va-status.json`, `vanessa-execution.log`, reports `junit/junit.xml`, `cucumber/CucumberJson.json`) | by the paths from the active `tests.va` / `va-params` profile, usually project-local (`<project_root>/vanessa-tests/reports/…`, `.../logs/…`) |
| Run artifacts from `v8-runner` | `v8-runner` itself (internal run logs, 1cv8c stdout/stderr, run-id metadata) | `workPath/temp/<runner-id>/runs/<run-id>/` (`workPath` is taken from `v8project.yaml`) |

When a run fails, do not clean up **both** locations until diagnostics are complete. Read the exact Vanessa report paths from the active profile.

## Monitoring Progress During a Run

For long `v8-runner test va` operations (usually several minutes), use the Monitor tool instead of blind file polling:

1. Start `v8-runner` in the background: `Bash run_in_background: true`, redirect stdout to a log file (for example `v8-runner test va 2>&1 | tee /tmp/va-stdout.log`).
2. Subscribe to this file through the Monitor tool with the filter: `ERROR:|\\[artifact\\]|passed|Failed:` - each matched line will arrive as a notification.
3. Stop waiting when **any** of the following conditions is met:
   - `va-status.log` appears in the run directory (it is created on success AND on error - unlike `va-status.json`);
   - the `1cv8c.*vanessa-automation` process finishes;
   - a line `ERROR:` appears in stdout (for example `ERROR: runtime error: test run reported failures`).
4. **Do not use `va-status.json` as the sole exit condition.** It is created only upon normal scenario completion; in the case of an early failure (step error, client crash), the file is absent and waiting for it will hang forever.

After the run completes, proceed to the diagnostic order below.

## When to Apply

| Trigger | Action |
|---------|----------|
| `va-status.json` not created | Treat the run as catastrophic, go to diagnostics |
| `va-status.json != 0` | Read artifacts and classify the failure |
| `vanessa-execution.log` contains an error | Determine the error class |
| Suspected GUI lockup | Visual diagnostics through `va-visual-check`: first a VA MCP screenshot, then fallback with the reason recorded if needed |
| The run is "green", but 0 steps executed / steps `undefined`/`skipped` | False success - classify as `step_resolution_error`/`scenario_error` |

---

## Mandatory Diagnostic Order

1. Check `va-status.json`.
2. Check `vanessa-execution.log`.
3. Check `event-log`: first the last `Error`; if empty - without the level filter.
4. If you need to see the test-client form state, apply `va-visual-check`: VA MCP screenshot, PNG validation, then fallback if needed.
5. If there is a signal of a modal window / security warning / manager window, also obtain the visual artifact through `va-visual-check`.
6. Only if that is insufficient - `tech-log-analysis`.

### Special-case: `Security Warning`

If `event-log` contains an entry about `Security Warning` for `bddRunner.epf` or plugins:
1. Treat it as a trigger for visual inspection.
2. Capture the real screen through `va-visual-check`, without relying only on X11 window titles.
3. Only after visual confirmation interpret a rerun.

---

## Error Classes

| Class | When to set |
|-------|---------------|
| `scenario_error` | The scenario is formulated incorrectly or uses the wrong flow |
| `step_resolution_error` | The required step was not found or cannot be resolved |
| `assertion_error` | The steps ran, but the result check did not match |
| `test_data_error` | Depends on missing/unsuitable data |
| `environment_error` | Problem in X11, environment, runner, or client startup |
| `product_ui_error` | Error in visible form behavior or UI flow |
| `product_logic_error` | Business logic gives the wrong result for a correct scenario |

### Quick Heuristic

| Signal | Class |
|--------|-------|
| No `va-status.json`, GTK/X11 error | `environment_error` |
| Step not found | `step_resolution_error` |
| The form opened, expectation did not match | `assertion_error` / `product_ui_error` |
| Error from the business module in the event log | `product_logic_error` |
| Document/object not found | `test_data_error` |

---

## Diagnostic Result

The agent must report: error class, main signal source, next action loop.

```text
failure_type = test_data_error
main_signal = document not found in event log / form flow
next_action = choose another fixture or prepare stable test data
```

---
depends_on:
  - framework/rules/vanessa-diagnostics-policy/SKILL.md
  - framework/rules/vanessa-security-warning/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
  - framework/skills/tool-usage/browser-ui/gui-control/SKILL.md
  - framework/skills/tool-usage/browser-ui/screenshot/SKILL.md
---
