---
name: vanessa-run-loop
description: "After feature/tests.va/MCP changes, run VA tests"
alwaysApply: true
---

# Vanessa Automation Run Policy

> **Trigger:** creating/changing `.feature`, launch parameters, `tests.va` configuration in `v8project.yaml`, or the client MCP extension. When triggered, run through `v8-runner test va` (`v8-runner` skill: `framework/skills/tool-usage/v8-runner/SKILL.md`). Diagnostics on failure are handled by the `vanessa-diagnostics` skill (`framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md`).

## MUST

- **Pre-run config sanity:** before launching, read the active profile from `tests.va` in `v8project.yaml` and make sure the feature directory path points to the folder for the **current task**. If it does not match, add `tests.va.profiles.<taskID>` and run with it. Record the profile in `{role}-context.md`.
- **Do not bypass v8-runner:** MUST NOT assemble `1cv8c` or `vrunner` commands manually when `v8-runner test va` is available. Exception: diagnostics of `v8-runner` itself after explicit user consent.
- **Completion condition:** wait for `va-status.log` (created on success AND on error) OR the completion of the `1cv8c.*vanessa-automation` process OR an `ERROR:` line in stdout. **Do not use only `va-status.json`** — on early failure it will not exist, and the wait will hang.

## Success indicator (all five MUST conditions must be met)

1. the file `va-status.json` exists;
2. the value in `va-status.json` equals `0`;
3. the file `vanessa-execution.log` is created;
4. the logs contain no skipped or missing steps;
5. the number of executed steps is > 0.

> Vanessa considers the run successful even if no step was found - this is a **false success**. Checking items 4-5 is mandatory.

---
depends_on:
  - framework/skills/tool-usage/v8-runner/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
---
