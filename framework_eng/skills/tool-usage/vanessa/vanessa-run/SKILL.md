---
name: vanessa-run
description: Running Vanessa Automation scenario tests. Use when you need to execute a feature scenario, verify the run baseline, read run artifacts, or determine how to launch Vanessa in the project.
---

# Running Vanessa Automation

## When to use

| Trigger | Action |
|---------|--------|
| Running `.feature` scenarios | Use the baseline from this skill |
| Checking run success | `va-status.json` + `vanessa-execution.log` |
| Selecting the launch method | `vrunner` (primary) or `1cv8c` (fallback) |

---

## Connection

Connection string, user, and password are in `<project_root>/configs/yaxunit-runner.yml`, section `app.connection`.

---

## Files

**Baseline EPF:** `/opt/onescript/2.0.0/lib/add/bddRunner.epf`

**Shared runtime templates:** `tools/runtime/vanessa/va-params.template.json`, `va-params-debug.template.json`, `vrunner-va.json`

**Project-local:**

```text
<project_root>/vanessa-tests/features
<project_root>/vanessa-tests/support
<project_root>/vanessa-tests/reports/va-status.json
<project_root>/vanessa-tests/logs/vanessa-execution.log
<project_root>/vanessa-tests/reports/junit/junit.xml
<project_root>/vanessa-tests/reports/cucumber/CucumberJson.json
```

**Library steps:** `/opt/onescript/2.0.0/lib/add/features/libraries`

Project scenarios and test data are always project-local. Shared templates with project paths are copied into the project-local runtime.

---

## Launch methods

### 1. Via `vrunner` (preferred)

```bash
DISPLAY=:99 vrunner vanessa \
  --settings <runtime_vrunner_va_json> \
  --ibconnection /S"<ib_connection>" \
  --db-user <db_user> \
  --db-pwd <db_pwd> \
  --pathvanessa "/opt/onescript/2.0.0/lib/add/bddRunner.epf"
```

### 2. Via `1cv8c` (fallback)

```bash
DISPLAY=:99 /opt/1cv8/x86_64/8.3.27.1719/1cv8c ENTERPRISE \
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

Display `:99` is used by default. After the run completes close the display to free X11 resources.

---

## Success indicator

1. There is a `va-status.json` with value `0`.
2. There is a `vanessa-execution.log`.

---

## If the run fails

1. Check `DISPLAY`.
2. Check `va-status.json` and `vanessa-execution.log`.
3. Diagnostics: `event-log-analysis` → `gui-control` / `screenshot` → `tech-log-analysis` (last).

---

## Common errors

| Error | What to do |
|--------|------------|
| `va-status.json` was not created | Check the X11/GUI, then `event-log` |
| `DISPLAY` is not up | Start/use a working X11 display |
| Runner finished without artifacts | Treat as invalid and proceed to diagnostics |
| `Предупреждение безопасности` | Rule `vanessa-security-warning` |

---
depends_on:
  - framework/rules/vanessa-run-loop.mdc
  - framework/rules/vanessa-tests-location.mdc
  - framework/rules/vanessa-security-warning.mdc
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
requires:
  - tools
---
