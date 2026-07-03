# Config and backends

Study `v8project.yaml` before diagnosing the behavior of build, syntax, dump, test, and launch.
If `v8project.local.yaml` exists alongside it, study that too - it overrides machine-local
settings before CLI overrides.

## Fields We Check First

- `basePath`: root of the 1С sources; if omitted, defaults to the directory containing the main config.
- `workPath`: location of generated state, temporary files, and the workspace.
- `format`: `DESIGNER` or `EDT`.
- `builder`: `DESIGNER` or `IBCMD`.
- `infobase.connection`: often `File=build/ib` for local automation.
- `source-set`: ordered configuration and extension sources.
- `tools.platform.path` or `tools.platform.version`: hints for locating the 1С platform.
- `tools.edt_cli.path`, `version`, and `interactive-mode`: hints for locating EDT CLI and the execution mode.
- `tests.yaxunit` and `tests.va`: configuration of test runners.
- `tools.client_mcp`, `tools.va`, and `tools.enterprise`: hints for launch and client MCP integration.
- `tools.client_mcp.extension`: optional tool extension that prepares `build`; this is not the project's source-set.

## Format and Backend Rules

- `format=DESIGNER`, `builder=DESIGNER`: supports init, build, extensions, dump, Designer syntax checks, tests, make/load/artifact scenarios if configured.
- `format=DESIGNER`, `builder=IBCMD`: supports init, build, extensions, dump with a limited backend and file-based infobases only.
- `format=EDT`, `builder=DESIGNER`: supports init, build through EDT export into Designer files, EDT syntax checks, extensions, and tests.
- `format=EDT`, `builder=IBCMD`: supports init and build through EDT export into Designer files followed by IBCMD import/apply; requires a file-based infobase.
- `extensions` supports Designer and EDT projects, but only `source-set` entries with extensions are effective.
- `syntax designer-config` and `syntax designer-modules` require the Designer format and Designer backend.
- `syntax edt` requires the EDT format with the Designer backend.
- `dump --mode partial` with IBCMD degrades to incremental dump, and this must be mentioned in user summaries.
- `convert` is CLI-only, repo-aware, uses configured `source-set`, does not use `builder`, and does not require an infobase.
- `load` supports `.cf` and `.cfe` only for `format=DESIGNER`, `builder=DESIGNER`.
- `tools.client_mcp.extension.source` is prepared during `build`, skipped if nothing changed, and refreshed via `build --full-rebuild`; `.artifact.path` must point to `.cfe` and in the current implementation requires `builder=DESIGNER`.
- `make` / `artifacts` require `builder=DESIGNER` and publish `.cf`, `.cfe`, `.epf`, or `.erf` depending on the target/source-set.

## Source-Set Notes

`source-set.name` is the stable identity for ordering, diagnostics, runtime contexts, generated directories, and command selection.

Supported `source-set.type` values:

- `CONFIGURATION`
- `EXTENSION`
- `EXTERNAL_DATA_PROCESSORS`
- `EXTERNAL_REPORTS`

Prefer `--source-set <NAME>` for narrow build, dump, convert, and artifact scenarios when the user's changes are limited to one configured source-set.

## Config Path

`v8project.yaml` is the default config file name. Use `--config <path>` only when the project's active config is not at the default path or the user explicitly asks for that command form.

`v8project.local.yaml` is only an automatic local overlay. It may override only `workPath`,
`infobase.*`, `tools.*`, `tests.*`, and `mcp.*`; it must not set `source-set`, `format`, or
`builder`, and it cannot be used as `--config`. `--workdir` takes precedence over both config files.

## Vanessa Automation in `v8project.yaml`

VA configuration is split into two layers:

1. `v8project.yaml` / `v8project.local.yaml` specifies which external VA processing to run, which JSON parameter template to use, and which feature profile is active.
2. The JSON from `tests.va.params_path` is a `VAParams` template. It contains Vanessa Automation settings themselves, including the TestClient profiles table. `v8-runner` reads this template, creates a runtime copy in `workPath/temp/.../va-params.json`, applies the selected feature/tags/logs profile, and passes the runtime copy to `/C` as `VAParams=<path>`. Do not edit the runtime copy as the source of truth.

Minimal universal block in `v8project.yaml`:

```yaml
tools:
  va:
    epf_path: '<path-to-vanessa-automation.epf>'

tests:
  va:
    params_path: '<path-to-va-params-template.json>'
    profile: '<default-feature-profile>'
    fail_fast: false
    profiles:
      <default-feature-profile>:
        feature_path: '<feature-file-or-directory>'
        # опционально:
        # features_to_run: ['feature-name.feature']
        # filter_tags: ['tag-without-or-with-leading-at']
        # ignore_tags: ['wip']
        # scenario_filter: ['scenario name fragment']
```

Field meaning:

- `tools.va.epf_path` - path to the external Vanessa Automation processing. The legacy `tests.va.epf_path` field is not supported.
- `tests.va.params_path` - path to the VAParams JSON template. This is not a generated file, but a stable project or local-environment template.
- `tests.va.profile` - name of the active feature profile; it must exist in `tests.va.profiles`.
- `tests.va.profiles.<name>.feature_path` - `.feature` file or directory that will be written into the runtime VAParams as `КаталогФич`.
- `filter_tags` and `ignore_tags` can be written with or without `@`; the runner removes one leading `@` before writing to `СписокТеговОтбор` / `СписокТеговИсключение`.

Use `v8project.local.yaml` for machine-local paths and secrets: for example, when `epf_path`, `params_path`, the TestClient user/password, or the local infobase path differ on the agent machine. Do not store real secrets in the shared `v8project.yaml`; instead, move them into a local VAParams template and reference it via `tests.va.params_path` in `v8project.local.yaml`.

### VA Test Manager Base

For `launch mcp va`, the `infobase.connection` string from the active v8-runner config is the **test manager** database, not necessarily the database of the application under test. The recommended mode for exploratory VA MCP and UI/UX checks is a separate empty file-based infobase for the test manager.

The test manager does not need the application data of the tested configuration. It only needs a 1C environment in which the Vanessa Automation external processing will open and VA MCP tools will be registered. An empty manager database reduces memory and resource usage, does not keep unnecessary data, and allows a single manager process to launch different TestClient databases through VAParams profiles.

If such a manager database does not exist, the setup workflow must create/initialize it before `v8-runner launch mcp va` (for example, a file-based infobase in the local `workPath`). Do not substitute the tested infobase with this step: test-client databases are described separately in `ДанныеКлиентовТестирования`.

Example separation:

```yaml
# v8project.local.yaml or a separate config for the VA manager workflow
infobase:
  connection: 'File=build/va-manager-empty-ib'

tools:
  va:
    epf_path: '${FRAMEWORK_REPOS}/vanessa-automation/dist/vanessa-automation/vanessa-automation-single.epf'

tests:
  va:
    params_path: 'tools/runtime/vanessa/va-params.local.json'
    profile: 'ui-research'
    profiles:
      ui-research:
        feature_path: 'vanessa-tests/features'
```

In this example, `File=build/va-manager-empty-ib` is the database that hosts `/TESTMANAGER` and the VA EPF. The tested databases are set not here, but in `VAParams.ДанныеКлиентовТестирования[*].ПутьКИнфобазе`.
### TestClient Profile Inside VAParams

For `launch mcp va` and UI/UX checks through VA MCP, the test-client profile is not set by a separate `v8project.yaml` field, but by the `ДанныеКлиентовТестирования` table in the VAParams JSON template. It is this row name that is then passed to the MCP call `connect_test_client {"profileName":"<profile-name>"}`.

Minimal structure:

```json
{
  "ИспользоватьКомпонентуVanessaExt": "Истина",
  "ИспользоватьВнешнююКомпонентуДляСкриншотов": "Истина",
  "ДиапазонПортовTestclient": "<fixed-port>-<fixed-port>",
  "ОпределятьРеальныйПортНаКоторомЗапустилсяКлиентТестирования": "Истина",
  "ДанныеКлиентовТестирования": [
    {
      "Имя": "client-sales",
      "Синоним": "client-sales",
      "ПутьКИнфобазе": "Srvr=\"server\";Ref=\"sales\";",
      "ПортЗапускаТестКлиента": 1538,
      "ДопПараметры": "/N<user> /P<password> /DisableStartupDialogs /DisableUnsafeActionProtection",
      "ТипКлиента": "Тонкий",
      "ИмяКомпьютера": "localhost"
    },
    {
      "Имя": "client-accounting",
      "Синоним": "client-accounting",
      "ПутьКИнфобазе": "Srvr=\"server\";Ref=\"accounting\";",
      "ПортЗапускаТестКлиента": 1539,
      "ДопПараметры": "/N<user> /P<password> /DisableStartupDialogs /DisableUnsafeActionProtection",
      "ТипКлиента": "Тонкий",
      "ИмяКомпьютера": "localhost"
    }
  ]
}
```

Why these fields are defined this way:

- `Имя` is the stable profile key that the agent passes to `connect_test_client`; the name must be independent of the specific task.
- `Синоним` is the human-readable alias; if a separate alias is not needed, keep it equal to `Имя` to avoid ambiguity.
- `ПутьКИнфобазе` — the connection string of the application under test. It may and usually should differ from `infobase.connection` of the manager database; the VA manager is started separately and must know which infobase to open as `/TESTCLIENT`.
- `ПортЗапускаТестКлиента` and `ДиапазонПортовTestclient` — pin the port so the agent can reliably connect to the expected client and not depend on old open TestClient processes. Before launch, close old test-clients or choose a free reserved port.
- `ДопПараметры` — anything that should not stop startup on dialogs: user/password or another authentication method, `/DisableStartupDialogs`, `/DisableUnsafeActionProtection`, and `/UC <code>` if needed. If the string contains secrets, the template must be local.
- `ТипКлиента` — the client type that VA should launch. For automation, a thin client is usually chosen if the project does not require a thick or ordinary client.
- `ИмяКомпьютера` — the machine where VA looks for/launches the TestClient. For a local manager + test-client, this is `localhost`.
- `ИспользоватьКомпонентуVanessaExt` and `ИспользоватьВнешнююКомпонентуДляСкриншотов` enable when the VA profile must work with OS windows and the real test-client PID.
- `ОпределятьРеальныйПортНаКоторомЗапустилсяКлиентТестирования` keep enabled: VA should verify the actual process/port, not treat a launch as successful based on a single profile.

### Preflight for TestClient ports

Before `connect_test_client`, the agent must verify that the ports from the selected `ДанныеКлиентовТестирования` profiles are free on the `ИмяКомпьютера` machine. If a fixed `ПортЗапускаТестКлиента` is used, check exactly that one. If the profile uses a range, check at least the target port of the profile and the availability of a free port in `ДиапазонПортовTestclient`.

If the port is occupied, do not start VA/TestClient blindly. First determine the owner of the port:

- it is an old test-client from this same workflow — close it via `close_test_client`, the standard session-manager/VA tool, or a saved PID;
- it is someone else's process — choose another reserved port/profile and update the local VAParams template;
- the owner is unclear — stop with diagnostics, do not kill the process just by port number.

Expected `v8-runner` behavior: fail-fast preflight before launching `launch mcp va` / `test va`. Runner already reads `tests.va.params_path` and builds runtime `VAParams`, so it can parse `ДанныеКлиентовТестирования`, check `ПортЗапускаТестКлиента` / `ДиапазонПортовTestclient` for the active scenario, and exit with a clear error like `VA TestClient port is busy: profile=<name>, port=<port>, pid=<pid>`. This is better than starting a 1C process that is guaranteed not to be able to bring up `/TESTCLIENT`.
