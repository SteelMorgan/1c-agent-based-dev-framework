---
name: vanessa-run
description: Running Vanessa Automation scenario tests. Use this skill when you need to execute a feature scenario, verify the run baseline, inspect run artifacts, or understand how to run Vanessa within the project.
---

# Running Vanessa Automation

## Purpose

This skill records exactly how to run Vanessa Automation scenario tests, where to look for the run baseline, and which artifacts count as the run result.

The main goal is not to reinvent the run command each session and avoid wasting time hunting for the baseline.

---

## When to apply

| Trigger | Action |
|---------|--------|
| Need to run Vanessa `.feature` scenarios | Use this skill as the run baseline |
| Need to understand where settings and artifacts live | Determine which files are shared and which are project-local |
| Need to check whether a run succeeded | Inspect `va-status.json` and `vanessa-execution.log` |
| Need to switch between direct run and `vrunner` | Use the commands from this skill |

---

## Shared vs project-local files

### Universal EPF baseline

```text
/opt/onescript/2.0.0/lib/add/bddRunner.epf
```

### Shared runtime templates

These files live in framework and serve as the universal run templates:

```text
/.../framework/runtime/vanessa/va-params.template.json
/.../framework/runtime/vanessa/va-params-debug.template.json
/.../framework/runtime/vanessa/vrunner-va.json
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

- `framework/runtime/vanessa/*.json` — shared runtime templates of the framework.
- If they embed paths, feature catalogs, data, or settings specific to a base, those must exist as project-local runtime copies.
- `feature` scenarios and test data belong to the specific project and must be project-local.
- Universal library feature/steps for Vanessa live in the tools directory:

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

## Sign of success

The run is successful only if:

1. the file `<project_root>/build/vanessa/reports/va-status.json` exists;
2. its value is `0`;
3. the file `<project_root>/build/vanessa/logs/vanessa-execution.log` exists.

---

## If the run failed

1. Check whether a DISPLAY is alive.
2. Inspect `va-status.json`.
3. Inspect `vanessa-execution.log`.
4. Move to diagnostics via:
   - `event-log-analysis`
   - `gui-control`
   - `screenshot`
   - `tech-log-analysis` only last

---

## Common errors

| Error | Action |
|-------|--------|
| `va-status.json` was not created | Check X11/GUI, then `event-log` |
| `DISPLAY` is not up | Bring up/use a working X11 display |
| Runner finished but there are no artifacts | Treat the run as invalid and proceed to diagnostics |
| `Предупреждение безопасности` appeared | Apply the `vanessa-security-warning` rule |

---

## Related resources

- `framework/rules/vanessa-run-loop.mdc`
- `framework/rules/vanessa-tests-location.mdc`
- `framework/rules/vanessa-security-warning.mdc`
- `framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md`
- `framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md`
- `framework/runtime/vanessa/va-params.template.json`
- `framework/runtime/vanessa/va-params-debug.template.json`
- `framework/runtime/vanessa/vrunner-va.json`

---
depends_on:
  - framework/rules/vanessa-run-loop.mdc
  - framework/rules/vanessa-tests-location.mdc
  - framework/rules/vanessa-security-warning.mdc
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
---
