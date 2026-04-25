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

## Files (DO NOT GUESS)

We always use **Vanessa-Automation single** (Pr-Mex/vanessa-automation). No alternative runners are used in projects.

**Entry point:** `<framework_repos>/vanessa-automation/dist/vanessa-automation/vanessa-automation-single.epf`

**Tools directory:** `<framework_repos>/vanessa-automation/dist/vanessa-automation` — contains the **native** `plugins/` (4 plugins with single-parameter signature `ОписаниеПлагина(ВозможныеТипыПлагинов)`).

**Anti-pattern (was the root cause of VA-ERR-00001):** pointing `КаталогИнструментов` at `/opt/onescript/2.0.0/lib/add`. VA-single resolves plugin path as `КаталогИнструментов + "plugins/"`, so it would pick up foreign plugins with two-parameter signature `ОписаниеПлагина(КонтекстЯдра, ВозможныеТипыПлагинов)` → `Insufficient actual parameters` for every plugin → steps end up in `Pending: Empty snippet address`. **Do not repeat.**

### How to obtain Vanessa-Automation

Deployed **inside the framework directory**, one set serves all projects:

```bash
git clone https://github.com/Pr-Mex/vanessa-automation '<framework_repos>/vanessa-automation'
cd '<framework_repos>/vanessa-automation'
gh release download <tag> --repo Pr-Mex/vanessa-automation \
  --pattern 'vanessa-automation.<tag>.zip' --pattern 'vanessa-automation-single.<tag>.zip'
unzip vanessa-automation.<tag>.zip -d dist/                # каталог с epf+plugins+locales
unzip vanessa-automation-single.<tag>.zip -d dist-single/  # single-EPF отдельно
cp dist-single/vanessa-automation-single.epf dist/vanessa-automation/  # положить single рядом с plugins
```

Resulting structure:

```
<framework_repos>/vanessa-automation/dist/vanessa-automation/
├── vanessa-automation.epf            # full-build entry point (reserve)
├── vanessa-automation-single.epf     # single-build entry point (we use this one)
├── plugins/                          # 4 NATIVE plugins, single-param signature
│   ├── ЗагрузчикПользовательскихНастроек.epf
│   ├── ЗапросыИзБД.epf
│   ├── СериализаторMXL.epf
│   └── УтвержденияBDD.epf
├── locales/                          # Messages.epf, Steps.epf
└── ...                               # tools/, features/, lib/, etc.
```

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

**Library steps:** `<framework_repos>/vanessa-automation/dist/vanessa-automation/features/libraries`

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
    "--vanessasettings": "<project_root>/vanessa-tests/runtime/va-params-run.json",
    "--pathvanessa": "<framework_repos>/vanessa-automation/dist/vanessa-automation/vanessa-automation-single.epf"
  }
}
```

**`va-params-run.json`** — VA-single settings. Take the template `tools/runtime/vanessa/va-params.template.json` and:
- replace all `$workspaceRoot` with the absolute path to the project;
- `КаталогФич` points to the directory with `.feature` files;
- `КаталогИнструментов` = `<framework_repos>/vanessa-automation/dist/vanessa-automation` (NOT `/opt/onescript/2.0.0/lib/add`);
- `ИспользоватьКомпонентуVanessaExt = "Истина"` — required for `(Расширение)` steps.

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
  --db-pwd <db_pwd>
```

`--pathvanessa` is taken from `vrunner-va-run.json` (the `vanessa.--pathvanessa` key points to the VA-single — see the «Runtime config» section). Passing it again on the CLI is unnecessary.

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
  /Execute"<framework_repos>/vanessa-automation/dist/vanessa-automation/vanessa-automation-single.epf"
```

Display `:99` is used by default. After the run completes close the display to free X11 resources.

---

## Success indicator

1. There is a `va-status.json` with value `0`.
2. There is a `vanessa-execution.log`.

This is the **minimum** indicator. After that, post-validation is mandatory (see below).

---

## Post-validation of a successful run

`va-status.json == 0` does not guarantee real success. Vanessa does **not** treat these as errors:
- a scenario with no steps (an empty `.feature`);
- steps that were not found in the library - they are silently skipped;
- a scenario excluded by a tag.

### Verification procedure

1. **Read `vanessa-execution.log`** — find the summary lines:
   - Number of executed scenarios and steps
   - If `0 steps completed` → **false success**, classify as `step_resolution_error`

2. **Read the report** (`CucumberJson.json` or `junit.xml`) — verify:
   - Each scenario from the `.feature` is present in the report
   - Each step has status `passed`, not `undefined` / `skipped`

3. **Match against expected steps** — compare steps from the `.feature` with the ones actually executed:
   - All steps executed → **true success**
   - Some steps `undefined`/`skipped` → **false success** → classify and report

### False-success classification

| Situation | Error class | Next action |
|----------|-------------|-------------|
| 0 steps executed | `step_resolution_error` | Check step binding to the library |
| Some steps `undefined` | `step_resolution_error` | Find or create the missing steps |
| Some steps `skipped` | `scenario_error` | Check scenario logic and conditions |
| Scenario missing from the report | `environment_error` | Check launch tags and filters |

---

## If the launch fails

1. Check `DISPLAY`.
2. Check `va-status.json` and `vanessa-execution.log`.
3. Diagnostics: `event-log-analysis` → `gui-control` / `screenshot` → `tech-log-analysis` (last).

---

## Typical errors

| Error | What to do |
|--------|------------|
| `va-status.json` was not created | Check X11/GUI, then `event-log` |
| `DISPLAY` is not up | Start/use a working X11 display |
| Runner finished without artifacts | Treat as invalid and go to diagnostics |
| `Security warning` | Rule `vanessa-security-warning` |
| `Infobase is undefined` | Wrong `--ibconnection` format; for server bases use `/Sserver\base` |
| Scenario list empty (0 executed) | Check tags - the `@draft` tag excludes a scenario from the run |

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
