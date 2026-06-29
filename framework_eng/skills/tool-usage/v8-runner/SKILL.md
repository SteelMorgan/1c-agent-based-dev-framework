---
name: v8-runner
description: "v8-runner: infobases, build, checks, tests, 1C clients"
provides_capabilities:
  - build_project
  - full_rebuild_project
  - init_infobase
  - config_init
  - syntax_check_designer_modules
  - syntax_check_designer_config
  - syntax_check_edt
  - run_yaxunit
  - run_vanessa
  - dump_config
  - load_artifact
  - make_artifacts
  - convert_sources
  - launch_designer
  - launch_thin_client
  - launch_mcp_client
  - run_session_manager
  - extensions_update
---

# v8-runner

Use this skill to manage `v8-runner` as an automation layer for local 1C development projects.

Treat this file as the entry point for decisions. Load only the reference file that matches the task:

- `references/command-selection.md` — for choosing the correct command sequence.
- `references/bootstrap.md` — for generating `v8project.yaml` from an existing repository: what to determine yourself and what to ask the user (decision tree for `format`, `builder`, `connection`).
- `references/config-and-backends.md` — about `v8project.yaml`, source sets, formats, builders, and backend limitations.
- `references/project-workflows.md` — typical build, syntax, dump, launch, and source synchronization scenarios for Designer and EDT projects.
- `references/file-and-artifact-workflows.md` — about dump, convert, load, make/artifacts, and staged publication.
- `references/testing.md` — about YaXUnit, Vanessa Automation, syntax checks, and artifacts.
- `references/troubleshooting.md` — about setup failures, stale state, and environment diagnostics.
- `references/auth-guard.md` — hard stop on license patterns, the two-candidate rule, classification of auth/path errors, storing credentials in `v8project.local.yaml`.

## Command Form

The canonical binary path is `tools/external/v8-runner/v8-runner` (in the project this works through the `tools/` symlink to the framework). The framework installer pulls the Latest release from [`alkoleft/v8-runner-rust`](https://github.com/alkoleft/v8-runner-rust) (upstream) on every run; manual reinstall is `python tools/install.py --install-external-tools`. If the binary is missing at this path and is not in `PATH` either, ask the user for the path or use the project wrapper script.

> **WS transport: the SteelMorgan fork is used.** For WS integration with the session manager, the fork [`SteelMorgan/v8-runner-rust`](https://github.com/SteelMorgan/v8-runner-rust) is used instead of upstream `alkoleft/v8-runner-rust`, because PRs with WS support are not accepted upstream. The framework installer targets releases from this fork. Similarly, `onec-client-mcp-devkit` (extensions `mcp_client`, `test_client`, etc.) is taken from the fork [`SteelMorgan/onec-client-mcp-devkit`](https://github.com/SteelMorgan/onec-client-mcp-devkit).

`v8project.yaml` is the default project config name. The adjacent `v8project.local.yaml` is loaded automatically for machine-local paths, credentials, tools, tests, and MCP settings. Do not pass `--config v8project.yaml` unless the user explicitly asks for a nonstandard command form or the active config path differs from the default; never pass `v8project.local.yaml` through `--config`.

Generated `v8project.yaml` files contain a `yaml-language-server` modeline that points to a versioned JSON Schema for the current `v8-runner` release. For `v8project.local.yaml`, use the corresponding raw URL `docs/schemas/v8project.local.schema.json` from the GitHub tag in editor settings when schema-aware editing matters.

Use JSON output only when another tool, script, or the final answer needs structured results:

```bash
v8-runner --json-message build
```

For direct human diagnostics, use text output.

Useful global flags:

- `--config <CONFIG>` — when the active config is not `./v8project.yaml`.
- `--json-message` — for machine-readable CLI envelopes.
- `--workdir <WORKDIR>` — overrides `workPath`; takes precedence over `v8project.local.yaml`.
- `--clean-before-execution` — clear logs before execution.
- `--log-level <error|warn|info|debug|trace>` — for diagnostics.
- `--no-color` — plain text output.

## 1C Client Lifecycle

Run interactive 1C clients and MCP/VA sessions that must remain available after the command returns to the agent as standalone processes with explicit lifecycle management. Do not use `sleep`, `tail -f`, an infinite shell loop, or a similar wrapper command to "keep" a 1C client alive: when the wrapper exits, the terminal/PTY or the agent environment may close the child 1C process, and `session-manager` will see this as a WS break without a normal close.

The correct order is:

1. Start the client with the standard `v8-runner launch ...` command.
2. If the runtime cleans up child processes after the shell command exits, run the command using detached environment facilities (`nohup`, `setsid`, a service/job runner, or the project equivalent), and save the PID and launch log.
3. Check readiness through externally observable state: `session_list`, appearance of the required MCP tools, the 1C window, a file protocol, or an entry in the registration log.
4. Stop the client with an explicit action: the standard session-manager/VA tool, a client shutdown command, or a targeted `kill <PID>` only for your saved PID.

`sleep` is allowed only as a short wait between readiness checks inside a script/poll loop. It must not own the lifecycle of the 1C process.

## First Pass

1. Check whether `v8project.yaml` exists at the root of the 1C project.
2. If it does not, run the narrowest possible `v8-runner config init ...` command appropriate for the project shape.
3. Inspect the generated config before running any mutating commands.
4. Run `v8-runner init` only when you need to create a file infobase or an EDT workspace.
5. Run the narrowest validation command that matches the user's goal.

Useful initialization commands:

```bash
v8-runner config init
v8-runner config init --connection "File=build/ib"
v8-runner config init --format edt
v8-runner config init --builder IBCMD
v8-runner init
```

## Typical Scenario Routing

- Sources changed, the infobase may be stale: run `v8-runner build`.
- Only one source set changed: use commands that accept `--source-set <NAME>` instead of a full rebuild or full materialization.
- Branch switch, rebase, large object moves, stale source-based tool-extension state, or suspicious incremental state: run `v8-runner build --full-rebuild`.
- Syntax checking: look at `format` and `builder`, then choose `syntax designer-modules`, `syntax designer-config`, or `syntax edt`.
- Behavior validation: run the appropriate `v8-runner test ...` command; tests build first.
- Debugging Vanessa Automation, exploring forms, and writing scenarios through MCP: use `v8-runner launch mcp va ...` to start a VA test-manager session with MCP tools. After startup, verify readiness via `session_list`: you need `kind=vanessa_test_client` and the appearance of VA tools, not just the initial WS registration.
- Extension property synchronization is needed: use `v8-runner extensions` or `extensions --name <SOURCE_SET>`.
- Infobase changes must become Git-visible files: check `git status`, then run the appropriate `v8-runner dump ...` command.
- Need to convert sources between Designer and EDT: use `v8-runner convert`; this is CLI only and does not use the infobase.
- Existing `.cf` or `.cfe` artifacts need to be applied to the infobase: use `v8-runner load ...`.
- Need to export release artifacts or publish external artifacts: use `v8-runner make ...` or the `artifacts` alias.
- Need a 1C UI session: use `v8-runner launch designer`, `launch thin`, `launch thick`, or `launch ordinary`.
- Need to run onec-client-mcp-devkit inside 1C without authoring VA: use `v8-runner launch mcp ...`.
- Couple a running 1C client with an active [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) over WebSocket: see the separate "WS coupling parameters" section below. WS flags (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`) are available on `launch ...` and `test ...` commands in the same way. The subtle clap-structure point: on `test`, the flags are placed **before** the `yaxunit/va` subcommand (for example `v8-runner test --mcp-transport=ws yaxunit module <NAME>`), not after it.

## WS Coupling Parameters with session-manager

WS coupling with [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) is a mode in which the 1C client MCP server connects to the manager over WebSocket instead of local HTTP MCP. It is controlled by the same set of CLI flags or by `tools.client_mcp.*` in `v8project.yaml`.

### Applicable Entry Points

The same flag set works for:

- `v8-runner launch designer | thin | thick | ordinary` — flags are placed after `launch`.
- `v8-runner launch mcp` / `launch mcp va` — flags are placed after `launch mcp [va]`.
- `v8-runner test yaxunit all` / `test yaxunit module <NAME>` — flags are placed **at the `test` level**, BEFORE the `yaxunit` subcommand.
- `v8-runner test va` — flags are placed **at the `test` level**, BEFORE the `va` subcommand.

Example (test): `v8-runner test --mcp-transport=ws --mcp-log-level=debug yaxunit module mcp_МспПровайдер_Тесты`. If you place WS flags after `yaxunit` or `module <NAME>`, clap responds with `error: unexpected argument`, because those subcommands do not declare their own `McpClientWsArgs`.

### CLI Flags

- `--mcp-transport={mcp|ws|auto}` — `auto` (default) performs a TCP probe of `manager_url` for about 200 ms; `ws` is strict WS, and fails if unavailable; `mcp` is the local HTTP MCP mode without probing.
- `--manager-url <URL>` — overrides `tools.client_mcp.manager_url` (default `ws://127.0.0.1:4000/sessions`).
- `--client-uid <UUID>` — overrides the auto-generated v4 UUID.
- `--corr-id <STR>` — overrides `vr-<first 8 characters of client_uid>`.
- `--mcp-log-level={off|error|warn|info|debug|trace}` — logging level inside the client.
- `--mcp-ws-timeout-ms <N>` — WS handshake timeout (default 1000 ms; relevant for `auto` fallback).

Alternative: all of this can be set in `tools.client_mcp.*` in `v8project.yaml` / `v8project.local.yaml` — priority order: CLI → yaml → internal defaults.

```yaml
tools:
  client_mcp:
    transport: auto         # mcp | ws | auto
    manager_url: ws://127.0.0.1:4000/sessions
    log_level: info
    ws_timeout_ms: 1000
```

For specialized entry points, `kind` is fixed by the entry point and cannot be overridden from the CLI. For ordinary UI clients (`launch thin/thick/ordinary`), `kind` is not passed in `/C`: the `client_mcp` extension declares its own client kind when `session.register` runs.

### Internal `kind` Mapping

| Command | `kind` |
|---|---|
| `launch thin/thick/ordinary` | not passed; the client side declares the default kind |
| `launch mcp` | `v8_runner_client` |
| `launch mcp va` | `vanessa_test_client` |
| `test yaxunit ...` | `yaxunit_runner` |
| `test va ...` | `vanessa_test_client` |

### Client and Test Launch Modes

| Mode | Purpose | MCP/VA behavior |
|---|---|---|
| `launch designer` | Open Designer. | Does not start client MCP tools and does not apply enterprise additional keys. |
| `launch thin`, `launch thick`, `launch ordinary` | Open a regular 1C UI client. | With WS coupling, registers the base client MCP tool set without `kind`; by itself it does not publish VA tools. |
| `launch mcp` | Start onec-client-mcp-devkit inside 1C without Vanessa. | `kind=v8_runner_client` for WS; local HTTP MCP with `--mcp-transport=mcp` or `auto` fallback. |
| `launch mcp va` | Start the Vanessa test manager for research, authoring, and VA client MCP tools. | `kind=vanessa_test_client`; the runner adds `/TESTMANAGER`, `/DisableUnsafeActionProtection`, `/Execute <vanessa-automation.epf>`, runtime `VAParams`, disables automatic scenario start/close, and does not use `StartFeaturePlayer`. |
| `test yaxunit ...` | Run YAxUnit tests. | `kind=yaxunit_runner` in WS mode; this is a test runner, not an interactive UI session. |
| `test va` | Run Vanessa feature scenarios. | `kind=vanessa_test_client`, but the payload is `StartFeaturePlayer;VAParams=...`; this is scenario execution, not the manager research mode. |

### What v8-runner injects into `/C` in the WS branch

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;kind=<KIND>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

For `launch mcp` / `launch mcp va`, this is the entire `/C`. For `launch thin/thick/ordinary`, the same WS fragment is used, but **without** `kind=<KIND>`, and it is appended via `;` to an existing `/C` if one is already set. For test commands, the WS fragment is appended via `;` to an existing `RunUnitTests=…` / Vanessa player (if `transport=ws` is selected through the yaml config).

Ordinary thin client in WS mode:

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

Important: do not add `kind` in ordinary `launch thin/thick/ordinary`. Such a client registers the base `client_mcp` tools, but it does not publish Vanessa Automation MCP tools on its own.

### Vanessa Automation MCP through session-manager

For a Vanessa Research/Scenario workflow through our `v8-client-session-manager`, a plain thin client is not started, but a test-manager session with the Vanessa Automation external processing open:

```bash
v8-runner launch mcp va \
  --mcp-transport ws \
  --manager-url ws://127.0.0.1:4000/sessions \
  --client-uid <uid> \
  --corr-id <uid> \
  --mcp-log-level debug \
  --mcp-ws-timeout-ms 5000
```

Expected 1C launch form that the runner must assemble:

```text
1cv8c ENTERPRISE
  /TESTMANAGER
  /DisableStartupDialogs
  /DisableUnsafeActionProtection
  /IBConnectionString <connection string from v8project.yaml>
  /N <user>
  /P <password>
  /Execute <path>/vanessa-automation.epf
  /C"mcpMode=ws;manager_url=ws://127.0.0.1:4000/sessions;client_uid=<uid>;kind=vanessa_test_client;corr_id=<uid>;mcp_log_level=debug;mcp_ws_timeout_ms=5000;VAParams=<runtime va-params.json>"
```

The required meaning of this launch string is: the MCP session must live on the test-manager process side with the Vanessa Automation external processing open. Do not start the tested application with the `MCPVA` form: `MCPVA` is the internal form/module of the VA external processing, and it is VA running inside `/TESTMANAGER` that must call `MCPVA.ЗарегистрироватьИнструментыMCP()`.

The readiness criterion for the VA MCP session is: a live `kind=vanessa_test_client` session appeared in `session_list`, and its tools contain VA tools (`get_VanessaAutomation_state`, `connect_test_client`, `get_window_list_os`, `get_window_screenshot_os`, `get_form_analysis`, `manage_command_interface`) or the number of tools became larger than the base `client_mcp` set. The initial registration with the base tools does not yet mean that `MCPVA.ЗарегистрироватьИнструментыMCP()` has already run.

Immediately after `v8-runner launch mcp va`, the response `session_list=[]` or the absence of VA tools is **not an error**: starting the test manager and registering tools normally takes 10-90 seconds. Mandatory readiness loop:

1. Poll `session_list` every 5-10 seconds.
2. Wait up to 120 seconds from the start: 10-90 seconds is normal, 90-120 seconds is diagnostic headroom.
3. Continue only when there is a live `kind=vanessa_test_client`, `state=active`, `disconnected_secs_ago=null`, `inflight=0` session, and the required VA tools for the current task are present.
4. Tool names from the MCP/showcase cache without a live session do not prove readiness.
5. If the condition is not satisfied within 120 seconds, stop and report `VA MCP readiness blocker`.

After the WS session is ready, the tested application in the VA context is started by the test manager itself: call the MCP tool `connect_test_client` with the `profileName` argument (the testing client profile name, for example `Codex thin AgentAI`). VA will start a separate `/TESTCLIENT -TPort <auto>` process from the profile and connect `ТестируемоеПриложение` to it; after that, the VA client MCP methods become available (`get_form_analysis`, `manage_command_interface`, `manage_form_elements`, screenshot/data tools, etc.). Do not start this `/TESTCLIENT` manually for the VA path unless you are debugging the profile mechanism itself.

After investigation, manual actions, or an error, always call the MCP tool `close_test_client`. Pass the same `profileName` when you worked with a specific profile; without `profileName`, the tool closes the currently connected profile. This releases the test-client process and avoids keeping extra 1C sessions before the next launch.

Treat VA screenshot MCP tools (`get_window_list_os`, `get_window_screenshot_os`) as ready only after a short smoke check in the current environment: the live session must remain active, `inflight=0`, and the PNG must be non-empty and non-black. The detailed visual-check order and fallback conditions are described in the `va-visual-check` skill.

Configure the `tools.va` / `tests.va` section in `v8project.yaml` and the TestClient profile in VAParams according to `references/config-and-backends.md` (section "Vanessa Automation in `v8project.yaml`"). The exact command chain for manager → `connect_test_client` → close is in `references/testing.md` (section "Exact VA manager → TestClient chain"). The full payload, the JSON output form (`--json-message`), probe rules, and behavior when the manager is unavailable are in `references/project-workflows.md` (section "WS mode to session-manager"). Starting the manager itself is **not** part of v8-runner — see the `v8-session-manager` skill.

### UI MCP through the platform test client

If the task is to drive the 1C UI through client MCP tools (`open_form`, `click`, `input`, `get_value`, `get_table_rows`, `test_client_start`), this contour is allowed only for structural control when the needed function is fundamentally absent in VA MCP or when it is used as part of a VA/TestClient scenario.

The working chain is:

1. Start session-manager and verify the HTTP endpoint: `tools/call session_list` must respond, even if `sessions=[]`.
2. Start the control MCP client detached, explicitly with `/TESTMANAGER`:

```bash
uid=$(cat /proc/sys/kernel/random/uuid)
setsid nohup v8-runner --no-color --log-level debug launch thin \
  --mcp-transport ws \
  --manager-url ws://127.0.0.1:4000/sessions \
  --client-uid "$uid" \
  --corr-id "ui-$uid" \
  --mcp-log-level debug \
  --mcp-ws-timeout-ms 5000 \
  --raw-key /TESTMANAGER \
  > "/tmp/ui-mcp-$uid.log" 2>&1 &
```

3. Wait in `session_list` for a live session `kind=1c-client`, `state=active`, `inflight=0`, `infobase_name=<required infobase>`. The baseline check before UI calls is: `infobase_info` must return quickly.
4. Start the tested application as a separate process with `/TESTCLIENT -TPort <port>` and the same connection parameters, user, and password as in the project launch. This is the preferred path: the agent starts the tested application detached and saves the PID/log, while `test_client_start` on the next step is used as a connection from the control `/TESTMANAGER` to the already listening port. If the project has a ready-made launcher, it must also pass `/N`, `/P`, `/UC` and the same connection string; otherwise use the direct platform form:

```bash
setsid nohup /opt/1cv8/x86_64/<version>/1cv8c ENTERPRISE \
  /DisableStartupDialogs \
  /IBConnectionString 'Srvr="<server>";Ref="<infobase>";' \
  /N <user> /P <password> /UC <unlock_code> \
  /TESTCLIENT -TPort 1538 \
  > /tmp/test-client-1538.log 2>&1 &
```

5. Connect the tested application through the control MCP session:

```json
{"name":"test_client_start","arguments":{"session_id":"<1c-client session_id>","port":1538}}
```

Successful criterion: `{"ok": true, "data": {"connected": true}}`.

6. After that, perform UI MCP tools only through the control session's `session_id`: `open_form` → `click/input/select` → `get_value/get_table_rows`. For form elements, you can build the URI directly as `control://<urlencoded form name>/<urlencoded element name>` if `find` is unstable.

Do not do this:

- Do not start the control client without `/TESTMANAGER`: on the first `test_client_start` the platform may fail with `Type not defined (ТестируемоеПриложение)`.
- Do not rely on `test_client_start` as the only way to start `/TESTCLIENT` if it starts the client without `/N` and `/P`: such a process may stay at the infobase login screen, and the connection will return `No suitable test client found`.
- Do not treat `tools/list` as proof of readiness: proxied tools may come only from the session-manager cache. Readiness is confirmed by a live session in `session_list` and a successful simple call (`infobase_info`).

### Resolved: WS Sessions in `test yaxunit` (DRIVE 2026-05-11)

Symptom: yaxunit_runner is NOT registered in the manager's `session_list`, although v8-runner correctly injects the WS payload into `/C` (`RunUnitTests=...;mcpMode=ws;...;kind=yaxunit_runner;...`).

The root cause is a race condition in BSL `client_mcp` (`ManagedApplicationModule.bsl`): the idle handler `Мсп_ОтложенныйСтарт_Тик` was set with a **1 second** interval, and YAXUNIT with `closeAfterTests: true` closed the application about 1 second after startup (tests finish in about 200 ms), so the idle handler did not get a chance to tick.

Fix: reduce the idle-handler interval from `1` to `0.1`:
```bsl
// exts/client_mcp/Ext/ManagedApplicationModule.bsl
ПодключитьОбработчикОжидания("Мсп_ОтложенныйСтарт_Тик", 0.1, Истина);
```

After the fix, yaxunit-Enterprise registers as `kind=yaxunit_runner` in the manager's `session_list` (confirmation in v8-runner stdout: `[MCP INFO ...] WS session registered: uid=... kind=yaxunit_runner ... tools=24`).

## Headless Launch of External Processing (.epf) with a Server Method Call

Launching an external processing in batch (headless) mode with automatic execution of its logic is done through `v8-runner launch <thin|thick|ordinary> --execute "<path to .epf>"` (this is `1cv8 ENTERPRISE /Execute<epf>`). The key nuance without which the approach does not work:

- **`/Execute<epf>` OPENS the processing form** (it emulates "Open processing"). By itself it does **NOT** call the object module's export method. Therefore, a **processing without a form** (only an object module with an export procedure) will **not execute** its logic through `/Execute` — the entry point is never called.
- The canonical headless approach: the processing **has a managed form**, and in its module there is an `&НаКлиенте Процедура ПриОткрытии(Отказ)` handler that recognizes batch mode by the **startup parameter**, calls an `&НаСервере` method (which does the work / invokes the object module's export procedure), and then cleanly ends the session via `ЗавершитьРаботуСистемы(Ложь)`.

### Passing the Parameter and Suppressing the Security Warning

- The startup parameter is passed with the `--c "<string>"` key (this is `/C"<string>"`) and is read in BSL through `ПараметрЗапуска()`. Use a sentinel string so the form distinguishes headless startup from interactive opening and does not auto-execute when opened manually.
- The **first launch** of the external processing opens a security warning dialog (protection against dangerous actions) - in headless mode it will hang the process. Suppress it with the `--raw-key /DisableUnsafeActionProtection` key. An alternative is to remove the user's "Protection against dangerous actions" flag or configure a security profile (but the CLI key is preferable for one-off runs).

### Minimal Processing Skeleton

```bsl
// Модуль формы обработки
&НаКлиенте
Процедура ПриОткрытии(Отказ)
    Если ПараметрЗапуска() = "ЗАПУСК_ПАКЕТНО" Тогда   // sentinel из --c
        Протокол = ВыполнитьОперациюНаСервере();      // серверная работа
        // записать Протокол в известный файл для верификации снаружи
        ЗавершитьРаботуСистемы(Ложь);                 // корректный выход без диалогов
    КонецЕсли;
КонецПроцедуры

&НаСервере
Функция ВыполнитьОперациюНаСервере()
    // разрешить все параметры СЕРВЕРНО (не из реквизитов формы — в headless их никто не заполнил),
    // выполнить бизнес-логику, вернуть текст протокола
КонецФункции
```

### Command and Verification

```bash
v8-runner launch thin --execute "<абс. путь к .epf>" --c "ЗАПУСК_ПАКЕТНО" --raw-key /DisableUnsafeActionProtection
```

- The connection to the infobase is taken from `v8project.yaml` — there is no need to specify a separate `/S`/`/F`.
- **Completion condition:** wait for the 1cv8 process to exit OR for the protocol file written by the processing itself to appear. A process exit code alone is a weak signal.
- **Verify the result by behavior, not by the fact of launch:** data delta (query before/after), protocol file content, an entry in the registration log. "The process ran without error" does not mean "the logic executed."

> Alternative without `/Execute`: from an **already connected** server session - `ВнешниеОбработки.Создать(<path>, Ложь)` plus a call to its export method (or БСП `ДлительныеОперации.ВыполнитьПроцедуруМодуляОбъектаОбработки`). This requires a "run code on server" channel (session manager / test runner), while `/Execute` is self-contained from the command line.

## Guard Rules

- Before any v8-runner operation that accesses the infobase, apply the auth guard: check credentials and classify possible errors (license / auth / path) — see `references/auth-guard.md`.
- Do not delete or recreate the infobase, workspace, temp directory, or generated state unless the user explicitly asked for it or the command itself is documented as a recovery path.
- Do not invent raw `1cv8`, `ibcmd`, or `1cedtcli` flags; prefer the `v8-runner` command surface.
- Before `dump`, check `git status` if the result may overwrite or mix with already made source changes.
- Keep failed test artifacts in `workPath/temp/<runner-id>/runs/<run-id>/` for diagnostics; do not clean them up immediately.
- Report missing local 1C utilities as environment/installation problems, not as project source errors.
- Keep final answers specific: the command run, the result, the path to the relevant artifact, and any follow-up command.

## Output Discipline

When reporting results, separate:

- project source failures;
- v8-runner command/config failures;
- failures to find the local 1C platform, EDT, IBCMD, or tools;
- test failures and the paths to their artifacts.
