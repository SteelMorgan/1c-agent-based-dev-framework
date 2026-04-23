---
name: vanessa-diagnostics
description: "Diagnostics for Vanessa Automation test runs. Use when a feature scenario did not pass, artifacts were not created, or you need to classify a failure after execution."
---

# Vanessa Automation Diagnostics

## When to Use

| Trigger | Action |
|---------|----------|
| `va-status.json` not created | Treat the run as failed, go to diagnostics |
| `va-status.json != 0` | Read artifacts and classify the failure |
| `vanessa-execution.log` contains an error | Determine the error class |
| Suspected GUI lockup | Visual diagnostics |

---

## Mandatory Diagnostic Order

1. Check `va-status.json`.
2. Check `vanessa-execution.log`.
3. Check `event-log`: first recent `Error`; if empty, then without a level filter.
4. If there is a signal for a modal window / security warning — `gui-control` / `screenshot`.
5. Only if that is not enough — `tech-log-analysis`.

### Special-case: `Security Warning`

If `event-log` has an entry about `Security Warning` for `bddRunner.epf` or plugins:
1. Treat it as a trigger for visual verification.
2. Open the real screen through noVNC or take a screenshot (do not rely on X11 window titles).
3. Interpret a rerun only after visual confirmation.

---

## Error Classes

| Class | When to Assign |
|-------|---------------|
| `scenario_error` | The scenario is formulated incorrectly or uses an inappropriate flow |
| `step_resolution_error` | The required step was not found or cannot be resolved |
| `assertion_error` | The steps ran, but the result check did not match |
| `test_data_error` | Depends on missing / unsuitable data |
| `environment_error` | Problem in X11, environment, runner, client startup |
| `product_ui_error` | Error in the visible behavior of a form or UI flow |
| `product_logic_error` | Business logic returns an incorrect result for a correct scenario |

### Quick Heuristic

| Signal | Class |
|--------|-------|
| No `va-status.json`, GTK/X11 error | `environment_error` |
| Step not found | `step_resolution_error` |
| Form opened, expectation did not match | `assertion_error` / `product_ui_error` |
| Error from a business module in the event log | `product_logic_error` |
| Document/object not found | `test_data_error` |

---

## Diagnostic Result

The agent should report: the error class, the main signal source, the next action path.

```text
failure_type = test_data_error
main_signal = document not found in event log / form flow
next_action = choose another fixture or prepare stable test data
```

---
depends_on:
  - framework/rules/vanessa-diagnostics-policy.mdc
  - framework/rules/vanessa-security-warning.mdc
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
  - framework/skills/tool-usage/browser-ui/gui-control/SKILL.md
  - framework/skills/tool-usage/browser-ui/screenshot/SKILL.md
---
