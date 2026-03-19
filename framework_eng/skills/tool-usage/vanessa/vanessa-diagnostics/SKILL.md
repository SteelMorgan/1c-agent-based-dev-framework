---
name: vanessa-diagnostics
description: Diagnostics of Vanessa Automation runs. Use when a feature scenario failed, artifacts were not created, or you need to classify a failure after execution.
---

# Vanessa Automation Diagnostics

## When to apply

| Trigger | Action |
|---------|----------|
| `va-status.json` not created | Treat the run as failed and proceed to diagnostics |
| `va-status.json != 0` | Read artifacts and classify the crash |
| `vanessa-execution.log` contains an error | Determine the error class |
| Suspicion of a GUI hang | Perform visual diagnostics |

---

## Required diagnostic order

1. Check `va-status.json`.
2. Check `vanessa-execution.log`.
3. Check `event-log`: start with the latest `Error`; if empty — without a level filter.
4. If there is a signal for a modal window / security warning — `gui-control` / `screenshot`.
5. Only if that is insufficient — `tech-log-analysis`.

### Special-case: `Security warning`

If `event-log` contains a `Security warning` entry for `bddRunner.epf` or plugins:
1. Treat it as a trigger for a visual check.
2. Open the real display via noVNC or capture a screenshot (do not rely on X11 window titles).
3. Only after visual confirmation interpret a rerun.

---

## Error classes

| Class | When to set |
|-------|---------------|
| `scenario_error` | The scenario is incorrectly formulated or uses an inappropriate flow |
| `step_resolution_error` | The required step was not found or does not resolve |
| `assertion_error` | Steps completed, but the validation did not match |
| `test_data_error` | Depends on missing or unsuitable data |
| `environment_error` | A problem in X11, the environment, the runner, or client launch |
| `product_ui_error` | Visible form behavior or UI flow error |
| `product_logic_error` | Business logic delivers an incorrect result with a correct scenario |

### Quick heuristic

| Signal | Class |
|--------|-------|
| No `va-status.json`, GTK/X11 error | `environment_error` |
| Missing step | `step_resolution_error` |
| Form opened, expectation mismatched | `assertion_error` / `product_ui_error` |
| Business-module error in the event log | `product_logic_error` |
| Document/object not found | `test_data_error` |

---

## Diagnostic outcome

The agent should report: the error class, the primary signal source, and the next action path.

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
