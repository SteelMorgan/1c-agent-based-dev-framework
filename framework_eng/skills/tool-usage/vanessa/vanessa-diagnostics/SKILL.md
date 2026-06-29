---
name: vanessa-diagnostics
description: "Vanessa diagnostics: crashes, artifacts, and causes"
---

# Vanessa Automation Diagnostics

Vanessa is launched via `v8-runner test va` (see the `v8-runner` skill → `references/testing.md`). This skill is about how to analyze a failed run.

## Run Artifacts

There are two layers - do not confuse them:

| Layer | What writes it | Where it is stored |
|------|-----------|-----------|
| Vanessa artifacts | the VA player itself (`va-status.json`, `vanessa-execution.log`, reports `junit/junit.xml`, `cucumber/CucumberJson.json`) | under the paths from the active `tests.va` / `va-params` profile, usually project-local (`<project_root>/vanessa-tests/reports/…`, `.../logs/…`) |
| v8-runner run artifacts | `v8-runner` itself (internal run logs, 1cv8c stdout/stderr, run-id metadata) | `workPath/temp/<runner-id>/runs/<run-id>/` (`workPath` is taken from `v8project.yaml`) |

When a run fails, do not clean up **both** locations until diagnostics are complete. Read the exact Vanessa report paths from the active profile.

## Monitoring Progress During a Run

For long `v8-runner test va` operations (usually several minutes), use the Monitor tool instead of blindly polling files:

1. Launch `v8-runner` in the background: `Bash run_in_background: true`, redirect stdout to a log file (for example `v8-runner test va 2>&1 | tee /tmp/va-stdout.log`).
2. Subscribe to this file through the Monitor tool with the filter: `ERROR:|\\[artifact\\]|passed|Failed:` - each matching line will arrive as a notification.
3. Stop waiting when **any** of these conditions is met:
   - `va-status.log` appeared in the run directory (it is created on success AND on error - unlike `va-status.json`);
   - the `1cv8c.*vanessa-automation` process finished;
   - the stdout contains a line starting with `ERROR:` (for example `ERROR: runtime error: test run reported failures`).
4. **Do not use `va-status.json` as the only exit condition.** It is created only when the scenario ends normally; if execution fails early (step error, client crash), the file is absent and waiting for it will hang forever.

After the run finishes, proceed with the diagnostics order below.

## When to Apply

| Trigger | Action |
|---------|----------|
| `va-status.json` was not created | Treat the run as crashed, go to diagnostics |
| `va-status.json != 0` | Read artifacts and classify the failure |
| `vanessa-execution.log` contains an error | Determine the error class |
| Suspicion of GUI blocking | Visual diagnostics via `va-visual-check`: first a VA MCP screenshot, then a fallback with cause capture if needed |
| The run is "green", but 0 steps were completed / steps are `undefined`/`skipped` | False success - classify as `step_resolution_error`/`scenario_error` |

---

## Mandatory Diagnostics Order

1. Check `va-status.json`.
2. Check `vanessa-execution.log`.
3. Check `event-log`: first the latest `Error`; if empty, then without a level filter.
4. If you need to see the test client form state, use `va-visual-check`: VA MCP screenshot, PNG verification, then fallback if needed.
5. If there is a signal for a modal window / security warning / manager window, get the visual artifact via `va-visual-check` as well.
6. Only if that is not enough - `tech-log-analysis`.

### Special-case: `Security Warning`

If `event-log` contains a `Security Warning` entry for `bddRunner.epf` or plugins:
1. Treat it as a trigger for visual verification.
2. Capture the real screen via `va-visual-check`, not relying only on X11 window titles.
3. Only after visual confirmation interpret the rerun.

---

## Error Classes

| Class | When to set it |
|-------|---------------|
| `scenario_error` | The scenario is formulated incorrectly or uses an unsuitable flow |
| `step_resolution_error` | The required step was not found or cannot be resolved |
| `assertion_error` | Steps ran, but the result check did not match |
| `test_data_error` | Depends on missing/inappropriate data |
| `environment_error` | Problem in X11, environment, runner, or client launch |
| `product_ui_error` | Error in visible form behavior or UI flow |
| `product_logic_error` | Business logic produces an incorrect result for a correct scenario |

### Quick Heuristic

| Signal | Class |
|--------|-------|
| No `va-status.json`, GTK/X11 error | `environment_error` |
| Step not found | `step_resolution_error` |
| Form opened, expectation did not match | `assertion_error` / `product_ui_error` |
| Error from a business module in the Event Log | `product_logic_error` |
| Document/object not found | `test_data_error` |

---

## Diagnostic Result

The agent must report: error class, main signal source, next action contour.

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
