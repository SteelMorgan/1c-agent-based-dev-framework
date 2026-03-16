---
name: vanessa-diagnostics
description: Diagnostics for Vanessa Automation runs. Use when a feature scenario failed, artifacts were not created, or you need to classify the post-run failure.
---

# Vanessa Automation Diagnostics

## Purpose

The skill defines how to break down an unsuccessful Vanessa Automation run and how to classify the outcome so the next agent step is obvious.

---

## When to apply

| Trigger | Action |
|---------|--------|
| `va-status.json` not created | Treat the launch as crashed and go into diagnostics |
| `va-status.json != 0` | Read artifacts and classify the failure |
| `vanessa-execution.log` contains an error | Determine the error class |
| There is suspicion of a GUI lock | Move to visual diagnostics |

---

## Required diagnostic order

1. Check `va-status.json`.
2. Check `vanessa-execution.log`.
3. Check `event-log`:
   - first the latest `Error`;
   - if empty, the latest entries without a level filter.
4. If there is a signal for a modal dialog or security warning — switch to `gui-control` / `screenshot`.
5. Only if that is insufficient — use `tech-log-analysis`.

### Special-case: `Security Warning`

If the `event-log` contains a record about `Security Warning` for `bddRunner.epf` or its plugins:

1. treat it as a trigger for visual verification;
2. do not rely solely on X11 window titles;
3. open the actual display via noVNC or capture a screenshot;
4. only after visual confirmation interpret a rerun as valid or invalid.

---

## Error classes

| Class | When to set |
|-------|-------------|
| `scenario_error` | The scenario is formulated incorrectly or uses an inappropriate thread |
| `step_resolution_error` | The required step was not found or does not resolve |
| `assertion_error` | The steps executed, but the result verification did not match the expectation |
| `test_data_error` | The scenario relies on missing or unsuitable data |
| `environment_error` | There is an issue with X11, the environment, runner availability, or client startup |
| `product_ui_error` | There is a problem with the visible form behavior or UI flow |
| `product_logic_error` | The business logic produces an incorrect result despite a correct scenario |

---

## Quick heuristic

| Signal | Class |
|--------|-------|
| No `va-status.json`, GTK/X11 error | `environment_error` |
| Step not found | `step_resolution_error` |
| The form opened and the expectation mismatched | `assertion_error` or `product_ui_error` |
| Error from a business module in the event log (ЖР) | `product_logic_error` |
| Document/object not found on the database | `test_data_error` |

---

## What to return after diagnostics

As a result the agent should clearly report:

1. the error class;
2. the main signal source;
3. the next action loop.

Example structure:

```text
failure_type = test_data_error
main_signal = document not found in event log / form flow
next_action = choose another fixture or prepare stable test data
```

---

## Related resources

- `framework/rules/vanessa-diagnostics-policy.mdc`
- `framework/rules/vanessa-security-warning.mdc`
- `framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md`
- `framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md`
- `framework/skills/tool-usage/browser-ui/gui-control/SKILL.md`
- `framework/skills/tool-usage/browser-ui/screenshot/SKILL.md`

---
depends_on:
  - framework/rules/vanessa-diagnostics-policy.mdc
  - framework/rules/vanessa-security-warning.mdc
---
