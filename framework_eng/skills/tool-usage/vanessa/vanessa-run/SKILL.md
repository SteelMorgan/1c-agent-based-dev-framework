---
name: vanessa-run
description: Running Vanessa Automation scenario tests. Use when you need to execute a feature scenario, check the run baseline, read run artifacts, or understand how to run Vanessa in a project.
---

# Running Vanessa Automation

## Purpose

This skill documents exactly how to launch Vanessa Automation scenario tests, where to look for the run baseline, and which artifacts count as the results of a run.

The main goal is to avoid reinventing the run command in every session and wasting time tracking down the baseline.

---

## When to apply

| Trigger | Action |
|---------|--------|
| Need to run Vanessa `.feature` scenarios | Use this skill as the run baseline |
| Need to understand where settings and artifacts are located | Determine which files are shared and which are project-local |
| Need to verify whether the run succeeded | Check `va-status.json` and `vanessa-execution.log` |
| Need to switch between a direct run and `vrunner` | Use the commands from this skill |

---

## Shared and project-local files

### Shared baseline EPF

```text
/opt/onescript/2.0.0/lib/add/bddRunner.epf
```

### Shared runtime templates

These files live inside the framework and serve as universal runtime templates:

```text
/.../tools/runtime/vanessa/va-params.template.json
/.../tools/runtime/vanessa/va-params-debug.template.json
/.../tools/runtime/vanessa/vrunner-va.json
```

### Project-local runtime files

```text
<project_root>/vanessa-tests/features
<project_root>/vanessa-tests/support
<project_root>/build/vanessa/reports/va-status.json
<project_root>/build/vanessa/logs/vanessa-execution.log
<project_root>/build/vanessa/reports/junit/junit.xml
<project_root>/build/vanessa/reports/cucumber/CucumberJson.json
```

### Important separation

- `tools/runtime/vanessa/*.json` — shared runtime templates of the framework.
- If they embed paths, feature directories, data, or settings for a specific infobase, those files must exist as project-local runtime copies.
- `feature` scenarios and test data belong to the specific project and must be project-local.
- Shared library feature/steps for Vanessa are stored in the tools directory:

```text
/opt/onescript/2.0.0/lib/add/features/libraries
```

---

## Basic run methods

### 1. Direct run via `1cv8c`

Substitute the project-local `project_root` and runtime settings file:

```bash
DISPLAY=:110 /opt/1cv8/x86_64/8.3.27.1719/1cv8c ENTERPRISE \
  /S"<ib_connection>" \
  /N"<db_user>" \
  /P"<db_pwd>" \
  /Lru /VLru_RU \
  /DisableStartupMessages /DisableStartupDialogs \
  /C"StartFeaturePlayer;workspaceRoot=<project_root>;VBParams=<runtime_va_params_json>" \
  /out"/tmp/va-run.out" \
  /TESTMANAGER \
  /Execute"/opt/onescript/2.0.0/lib/add/bddRunner.epf"
```

### 2. Run via `vrunner`

Substitute the project-local runtime settings file:

```bash
DISPLAY=:110 vrunner vanessa \
  --settings <runtime_vrunner_va_json> \
  --ibconnection /S"<ib_connection>" \
  --db-user <db_user> \
  --db-pwd <db_pwd> \
  --pathvanessa "/opt/onescript/2.0.0/lib/add/bddRunner.epf"
```

---

## Signs of success

A run counts as successful only if:

1. the file `<project_root>/build/vanessa/reports/va-status.json` exists;
2. its value is `0`;
3. the file `<project_root>/build/vanessa/logs/vanessa-execution.log` exists.

---

## If the run failed

1. Check whether there is a live `DISPLAY`.
2. Check `va-status.json`.
3. Check `vanessa-execution.log`.
4. Move to diagnostics via:
   - `event-log-analysis`
   - `gui-control`
   - `screenshot`
   - `tech-log-analysis` only as the last step

---

## Common errors

| Error | What to do |
|-------|------------|
| `va-status.json` is not created | Check X11/GUI, then `event-log` |
| `DISPLAY` is not set up | Bring up/use a working X11 display |
| Runner finished but there are no artifacts | Consider the run invalid and go to diagnostics |
| A `Security warning` appeared | Apply the `vanessa-security-warning` rule |

---

## Related resources

- `framework/rules/vanessa-run-loop.mdc`
- `framework/rules/vanessa-tests-location.mdc`
- `framework/rules/vanessa-security-warning.mdc`
- `framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md`
- `framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md`
- `tools/runtime/vanessa/va-params.template.json`
- `tools/runtime/vanessa/va-params-debug.template.json`
- `tools/runtime/vanessa/vrunner-va.json`

---
depends_on:
  - framework/rules/vanessa-run-loop.mdc
  - framework/rules/vanessa-tests-location.mdc
  - framework/rules/vanessa-security-warning.mdc
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
---
