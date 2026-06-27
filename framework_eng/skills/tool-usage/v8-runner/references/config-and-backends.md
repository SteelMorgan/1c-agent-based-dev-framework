# Config and backends

Study `v8project.yaml` before diagnosing build, syntax, dump, test, and launch behavior.
If `v8project.local.yaml` exists next to it, study it too - it overrides machine-local
settings before CLI overrides.

## Fields we check first

- `basePath`: the root of the 1C sources; if omitted, defaults to the directory containing the main configuration.
- `workPath`: the location of generated state, temporary files, and the workspace.
- `format`: `DESIGNER` or `EDT`.
- `builder`: `DESIGNER` or `IBCMD`.
- `infobase.connection`: often `File=build/ib` for local automation.
- `source-set`: ordered configuration and extension sources.
- `tools.platform.path` or `tools.platform.version`: hints for locating the 1C platform.
- `tools.edt_cli.path`, `version` and `interactive-mode`: hints for locating EDT CLI and execution mode.
- `tests.yaxunit` and `tests.va`: test launcher configuration.
- `tools.client_mcp`, `tools.va` and `tools.enterprise`: hints for launch and client MCP integration.
- `tools.client_mcp.extension`: optional tool extension that prepares `build`; this is not the project's source-set.

## Format and backend rules

- `format=DESIGNER`, `builder=DESIGNER`: supports init, build, extensions, dump, Designer syntax checks, tests, and make/load/artifact scenarios if they are configured.
- `format=DESIGNER`, `builder=IBCMD`: supports init, build, extensions, dump with a limited backend and only file-based infobases.
- `format=EDT`, `builder=DESIGNER`: supports init, build through EDT export into Designer files, EDT syntax checks, extensions, and tests.
- `format=EDT`, `builder=IBCMD`: supports init and build through EDT export into Designer files followed by IBCMD import/apply; requires a file-based infobase.
- `extensions` supports Designer and EDT projects, but only `source-set` entries with extensions are effective.
- `syntax designer-config` and `syntax designer-modules` require Designer format and Designer backend.
- `syntax edt` requires EDT format with Designer backend.
- `dump --mode partial` with IBCMD degrades to incremental dump, and this needs to be mentioned in user summaries.
- `convert` is CLI-only, repo-aware, uses configured `source-set`s, does not use `builder`, and does not require an infobase.
- `load` supports `.cf` and `.cfe` only for `format=DESIGNER`, `builder=DESIGNER`.
- `tools.client_mcp.extension.source` is prepared during `build`, skipped if nothing changed, and updated via `build --full-rebuild`; `.artifact.path` must point to a `.cfe` and in the current implementation requires `builder=DESIGNER`.
- `make` / `artifacts` require `builder=DESIGNER` and publish `.cf`, `.cfe`, `.epf` or `.erf` depending on target/source-set.

## Notes on source-set

`source-set.name` is a stable identity for ordering, diagnostics, runtime contexts, generated directories, and command selection.

Supported values of `source-set.type`:

- `CONFIGURATION`
- `EXTENSION`
- `EXTERNAL_DATA_PROCESSORS`
- `EXTERNAL_REPORTS`

Prefer `--source-set <NAME>` for narrow build, dump, convert, and artifact scenarios when user changes are limited to one configured source-set.

## Config Path

`v8project.yaml` is the default config file name. Use `--config <path>` only when the active project config is not under the default path or the user explicitly asks for that command form.

`v8project.local.yaml` is only an automatic local overlay. It can override only `workPath`,
`infobase.*`, `tools.*`, `tests.*` and `mcp.*`; it must not set `source-set`, `format`, or
`builder`, and it cannot be used as `--config`. `--workdir` takes precedence over both config files.

## Vanessa Automation in `v8project.yaml`

VA configuration is split into two levels:

1. `v8project.yaml` / `v8project.local.yaml` specifies which external VA processing to launch, which JSON parameter template to use, and which feature profile is active.
2. The JSON from `tests.va.params_path` is a `VAParams` template. It contains Vanessa Automation settings themselves, including the TestClient profile table. `v8-runner` reads this template, creates a runtime copy in `workPath/temp/.../va-params.json`, applies the selected feature/tag/log profile, and passes the runtime copy to `/C` as `VAParams=<path>`. Do not edit the runtime copy as the source of truth.

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
        # optional:
        # features_to_run: ['feature-name.feature']
        # filter_tags: ['tag-without-or-with-leading-at']
        # ignore_tags: ['wip']
        # scenario_filter: ['scenario name fragment']
```

Meaning of the fields:

- `tools.va.epf_path` — path to the external Vanessa Automation processing. The legacy `tests.va.epf_path` field is not supported.
- `tests.va.params_path` — path to the VAParams JSON template. This is not a generated file, but a stable project or local-environment template.
- `tests.va.profile` — name of the active feature profile; it must exist in `tests.va.profiles`.
- `tests.va.profiles.<name>.feature_path` — file or `.feature` directory that will be written into runtime VAParams as `КаталогФич`.
- `filter_tags` and `ignore_tags` can be written with or without `@`; the runner removes one leading `@` before writing to `СписокТеговОтбор` / `СписокТеговИсключение`.

Use `v8project.local.yaml` for machine-local paths and secrets: for example, if `epf_path`, `params_path`, the TestClient user/password, or the path to a local infobase differ on the agent machine. Do not store real secrets in the shared `v8project.yaml`; it is better to move them into a local VAParams template and point to it through `tests.va.params_path` in `v8project.local.yaml`.

### TestClient profile inside VAParams

For `launch mcp va` and UI/UX verification through VA MCP, the test-client profile is not set by a separate `v8project.yaml` field, but by the `ДанныеКлиентовТестирования` table in the VAParams JSON template. It is the name of this row that is later passed to the MCP call `connect_test_client {"profileName":"<profile-name>"}`.

Minimal structure:

```json
{
  "ИспользоватьКомпонентуVanessaExt": "Истина",
  "ИспользоватьВнешнююКомпонентуДляСкриншотов": "Истина",
  "ДиапазонПортовTestclient": "<fixed-port>-<fixed-port>",
  "ОпределятьРеальныйПортНаКоторомЗапустилсяКлиентТестирования": "Истина",
  "ДанныеКлиентовТестирования": [
    {
      "Имя": "<stable-profile-name>",
      "Синоним": "<stable-profile-name>",
      "ПутьКИнфобазе": "<same-infobase-connection-as-tested-app>",
      "ПортЗапускаТестКлиента": <fixed-port>,
      "ДопПараметры": "/N<user> /P<password> /DisableStartupDialogs /DisableUnsafeActionProtection",
      "ТипКлиента": "Тонкий",
      "ИмяКомпьютера": "localhost"
    }
  ]
}
```

Why these fields are named this way:

- `Имя` — stable profile key that the agent passes to `connect_test_client`; the name must be independent of the specific task.
- `Синоним` — human-readable alias; if a separate alias is not needed, keep it equal to `Имя` so you do not create ambiguity.
- `ПутьКИнфобазе` — connection string of the application under test. The VA manager runs separately and must know which infobase to open as `/TESTCLIENT`.
- `ПортЗапускаТестКлиента` and `ДиапазонПортовTestclient` — fix the port so the agent can reliably connect to the expected client and not depend on old open TestClient processes. Before running, close old test-client processes or choose a free reserved port.
- `ДопПараметры` — everything that should not stop startup on dialogs: user/password or another authentication method, `/DisableStartupDialogs`, `/DisableUnsafeActionProtection`, and `/UC <code>` if needed. If the string contains secrets, the template must be local.
- `ТипКлиента` — the client type that VA should launch. For automation, thin client is usually chosen unless the project requires thick or ordinary client.
- `ИмяКомпьютера` — the machine where VA looks for/starts TestClient. For a local manager + test-client, this is `localhost`.
- Enable `ИспользоватьКомпонентуVanessaExt` and `ИспользоватьВнешнююКомпонентуДляСкриншотов` when the VA profile must work with OS windows and the real test-client PID.
- `ОпределятьРеальныйПортНаКоторомЗапустилсяКлиентТестирования` should stay enabled: VA should verify the actual process/port, not consider the launch successful based on a single profile.
