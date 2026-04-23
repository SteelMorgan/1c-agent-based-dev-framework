---
name: vanessa-run
description: "Running Vanessa Automation scenario tests. Use when you need to execute a feature scenario, verify the run baseline, read run artifacts, or determine how to launch Vanessa in the project."
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

## Runtime config

Before launching, prepare two files in `<project_root>/vanessa-tests/runtime/`:

**`vrunner-va-run.json`** — vrunner config:
```json
{
  "default": {
    "--v8version": "<platform_version>",
    "--language": "ru", "--locale": "ru_RU",
    "--workspace": "<project_root>",
    "--root": "<project_root>",
    "--nocacheuse": true,
    "--debuglogfile": "<project_root>/vanessa-tests/logs/vrunner-debug.log"
  },
  "vanessa": {
    "--workspace": "<project_root>",
    "--vanessasettings": "<project_root>/vanessa-tests/runtime/va-params-run.json"
  }
}
```

**`va-params-run.json`** — bddRunner settings. Take the template `tools/runtime/vanessa/va-params.template.json` and replace all `$workspaceRoot` with the absolute path to the project. The `FeatureCatalog` field points to the directory containing the `.feature` files to run.

---

## Database update before the run

**Preparation sequence:**
1. `build_project` (MCP `yaxunit-runner`) — build and load the configuration into the database.
2. Database update using the command below — **only if** structural changes were made.
3. Run Vanessa (methods below).

If structural changes were loaded into the database since the last update, Vanessa will fail when accessing new objects. A database update run is required first.

**Required when** (changes to DBMS structure or permissions):
- New role or change to role composition
- New attribute / metadata object (catalog, document, register)
- New predefined element
- Attribute type change, object deletion

**Not required** (code/interface only):
- Module code changes, new common module, layout/report/subsystem changes

**Command** (parameters taken from `configs/yaxunit-runner.yml`):

```bash
# <platform_version> — field platform-version
# <server>, <base>   — from connection-string: Srvr='<server>';Ref='<base>';
# <db_user>, <db_pwd> — fields user / password

DISPLAY=:99 /opt/1cv8/x86_64/<platform_version>/1cv8c ENTERPRISE \
  /S"<server>\\<base>" \
  /N"<db_user>" \
  /P"<db_pwd>" \
  /C"ЗапуститьОбновлениеИнформационнойБазы" \
  /DisableStartupMessages \
  /DisableStartupDialogs \
  /Out"/tmp/1c-update.out"
```

> The key name `/C"ЗапуститьОбновлениеИнформационнойБазы"` does not require translation - it is a platform system constant, not a user-visible string. For bases built on English BSP use `/C"StartInfobaseUpdate"` instead.

Wait for the process to complete (the window closes automatically). Check `/tmp/1c-update.out` for errors.

---

## Launch methods

### 1. Via `vrunner` (preferred)

The `--ibconnection` format for a server base: `/S<server>\<base>` (no quotes — vrunner adds them internally).

Convert the connection string from `yaxunit-runner.yml` (`Srvr='server';Ref='base';`) to `/Sserver\base`.

```bash
DISPLAY=:99 vrunner vanessa \
  --settings '<project_root>/vanessa-tests/runtime/vrunner-va-run.json' \
  --ibconnection '/S<server>\<base>' \
  --db-user <db_user> \
  --db-pwd <db_pwd> \
  --pathvanessa "/opt/onescript/2.0.0/lib/add/bddRunner.epf"
```

### 2. Via `1cv8c` (fallback)

Take the platform version from `configs/yaxunit-runner.yml`, field `platform-version`.

```bash
DISPLAY=:99 /opt/1cv8/x86_64/<platform_version>/1cv8c ENTERPRISE \
  /S"<server>\\<base>" \
  /N"<db_user>" \
  /P"<db_pwd>" \
  /Lru /VLru_RU \
  /DisableStartupMessages /DisableStartupDialogs \
  /C"StartFeaturePlayer;workspaceRoot=<project_root>;VBParams=<project_root>/vanessa-tests/runtime/va-params-run.json" \
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
| `Security warning` | Rule `vanessa-security-warning` |
| `Infobase is undefined` | Wrong `--ibconnection` format; for server bases use `/Sserver\base` |
| Scenario list empty (0 executed) | Check tags — the `@draft` tag excludes a scenario from the run |

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
