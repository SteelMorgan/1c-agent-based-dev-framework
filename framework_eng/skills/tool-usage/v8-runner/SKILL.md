---
name: v8-runner
description: "Use when Codex needs to manage v8-runner on local 1C projects through the CLI: configure v8project.yaml, initialize infobases or EDT workspaces, build sources from Designer or EDT, run syntax checks and tests, dump infobase changes, convert source formats, load or export artifacts, launch 1C clients, or choose safe 1C automation command sequences."
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

Keep this file as the entry point for decisions. Load only the reference file that matches the task:

- `references/command-selection.md` — for choosing the correct command sequence.
- `references/bootstrap.md` — for generating `v8project.yaml` from an existing repository: what to determine yourself and what to ask the user (decision tree for `format`, `builder`, `connection`).
- `references/config-and-backends.md` — about `v8project.yaml`, source sets, formats, builders, and backend limitations.
- `references/project-workflows.md` — typical build, syntax, dump, launch, and source synchronization scenarios for Designer- and EDT-based projects.
- `references/file-and-artifact-workflows.md` — about dump, convert, load, make/artifacts, and staged publication.
- `references/testing.md` — about YaXUnit, Vanessa Automation, syntax checks, and artifacts.
- `references/troubleshooting.md` — about configuration failures, stale state, and environment diagnostics.
- `references/auth-guard.md` — hard stop on license patterns, the rule of two candidates, auth/path error classification, storing credentials in `v8project.local.yaml`.

## Command Form

The canonical binary path is `tools/external/v8-runner/v8-runner` (in the project this works through the `tools/` symlink to the framework). The framework installer pulls the latest release from [`alkoleft/v8-runner-rust`](https://github.com/alkoleft/v8-runner-rust) (upstream) on every launch; manual reinstall is `python tools/install.py --install-external-tools`. If the binary is missing at this path and also not in `PATH`, ask the user for the path or use the project wrapper script.

> **WS transport: the SteelMorgan fork is used.** For WS pairing with the session manager, the fork [`SteelMorgan/v8-runner-rust`](https://github.com/SteelMorgan/v8-runner-rust) is used instead of upstream `alkoleft/v8-runner-rust`, because PRs with WS support are not accepted upstream. The framework installer targets releases from this fork. Likewise, `onec-client-mcp-devkit` (the `mcp_client`, `test_client`, and other extensions) is taken from the fork [`SteelMorgan/onec-client-mcp-devkit`](https://github.com/SteelMorgan/onec-client-mcp-devkit).

`v8project.yaml` is the default project config name. A neighboring `v8project.local.yaml` is loaded automatically for machine-local paths, credentials, tools, tests, and MCP settings. Do not pass `--config v8project.yaml` unless the user explicitly asks for a non-standard command form or the active config path differs from the default; never pass `v8project.local.yaml` through `--config`.

Generated `v8project.yaml` files contain a `yaml-language-server` modeline that points to the versioned JSON Schema for the current `v8-runner` release. For `v8project.local.yaml`, use the corresponding raw URL `docs/schemas/v8project.local.schema.json` from the GitHub tag in editor settings when schema-assisted editing matters.

Use JSON output only when another tool, script, or the final answer needs structured results:

```bash
v8-runner --json-message build
```

For direct human diagnostics, use text output.

Useful global flags:

- `--config <CONFIG>` — when the active config is not `./v8project.yaml`.
- `--json-message` — for machine-readable CLI wrappers.
- `--workdir <WORKDIR>` — overrides `workPath`; takes precedence over `v8project.local.yaml`.
- `--clean-before-execution` — clear logs before execution.
- `--log-level <error|warn|info|debug|trace>` — for diagnostics.
- `--no-color` — plain text output.

## First Pass

1. Check whether `v8project.yaml` exists at the root of the 1C project.
2. If it does not, run the narrowest possible `v8-runner config init ...` command that fits the project form.
3. Inspect the generated config before running any mutating commands.
4. Run `v8-runner init` only when you need to create a file-based infobase or an EDT workspace.
5. Run the narrowest validation command that answers the user's goal.

Useful initialization commands:

```bash
v8-runner config init
v8-runner config init --connection "File=build/ib"
v8-runner config init --format edt
v8-runner config init --builder IBCMD
v8-runner init
```

## Routing Typical Scenarios

- If sources changed, the infobase may be stale: run `v8-runner build`.
- If only one source set changed: use commands that accept `--source-set <NAME>` instead of a full rebuild or materializing everything.
- Branch switch, rebase, large object moves, stale source-based tool extension state, or suspicious incremental state: run `v8-runner build --full-rebuild`.
- Syntax check: inspect `format` and `builder`, then choose `syntax designer-modules`, `syntax designer-config`, or `syntax edt`.
- Behavior validation: run the appropriate `v8-runner test ...` command; tests build first.
- Debugging Vanessa Automation, researching forms, or writing scenarios through MCP: use `v8-runner launch mcp va ...` to start a VA test manager session with MCP tools. After startup, check readiness through `session_list`: you need `kind=vanessa_test_client` and VA tools, not only the initial WS registration.
- Need extension property synchronization: use `v8-runner extensions` or `extensions --name <SOURCE_SET>`.
- Infobase changes must become Git-visible files: check `git status`, then run the appropriate `v8-runner dump ...` command.
- Need to convert sources between Designer and EDT: use `v8-runner convert`; this is CLI-only and does not use the infobase.
- Existing `.cf` or `.cfe` artifacts need to be applied to the infobase: use `v8-runner load ...`.
- Need to export release artifacts or publish external artifacts: use `v8-runner make ...` or the `artifacts` alias.
- Need a 1C UI session: use `v8-runner launch designer`, `launch thin`, `launch thick`, or `launch ordinary`.
- Need to start onec-client-mcp-devkit inside 1C without authoring VA: use `v8-runner launch mcp ...`.
- Pair a running 1C client with a running [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) over WebSocket: see the separate "WS pairing parameters" section below. WS flags (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`) are available on `launch ...` and `test ...` commands in the same way. Subtle clap detail: on `test`, the flags are placed **before** the `yaxunit/va` subcommand (for example, `v8-runner test --mcp-transport=ws yaxunit module <NAME>`), not after.

## WS Pairing Parameters with session-manager

WS pairing with [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) is a mode in which the 1C client MCP server connects to the manager over WebSocket instead of local HTTP MCP. It is controlled by the same set of CLI flags or by `tools.client_mcp.*` in `v8project.yaml`.

### Applicable Entry Points

The same set of flags works for:

- `v8-runner launch designer | thin | thick | ordinary` — flags are placed after `launch`.
- `v8-runner launch mcp` / `launch mcp va` — flags are placed after `launch mcp [va]`.
- `v8-runner test yaxunit all` / `test yaxunit module <NAME>` — flags are placed **at the `test` level**, BEFORE the `yaxunit` subcommand.
- `v8-runner test va` — flags are placed **at the `test` level**, BEFORE the `va` subcommand.

Example (test): `v8-runner test --mcp-transport=ws --mcp-log-level=debug yaxunit module mcp_МспПровайдер_Тесты`. If you place the WS flags after `yaxunit` or `module <NAME>`, clap returns `error: unexpected argument` because these subcommands do not declare their own `McpClientWsArgs`.

### CLI Flags

- `--mcp-transport={ws|legacy|auto}` — `auto` (default) performs a TCP probe of `manager_url` for about 200 ms; `ws` is strict WS and fails if unavailable; `legacy` is the old HTTP mode without probe.
- `--manager-url <URL>` — override `tools.client_mcp.manager_url` (default `ws://127.0.0.1:4000/sessions`).
- `--client-uid <UUID>` — override the auto-generated v4 UUID.
- `--corr-id <STR>` — override `vr-<first 8 characters of client_uid>`.
- `--mcp-log-level={off|error|warn|info|debug|trace}` — log level inside the client.
- `--mcp-ws-timeout-ms <N>` — WS handshake timeout (default 1000 ms; relevant for `auto` fallback).

Alternative: all of this can be configured in `tools.client_mcp.*` in `v8project.yaml` / `v8project.local.yaml` — priority order: CLI → yaml → internal defaults.

```yaml
tools:
  client_mcp:
    transport: auto         # ws | legacy | auto
    manager_url: ws://127.0.0.1:4000/sessions
    log_level: info
    ws_timeout_ms: 1000
```

For specialized entry points, `kind` is fixed by the entry point and cannot be overridden from the CLI. For regular UI clients (`launch thin/thick/ordinary`), `kind` is not passed in `/C`: the `client_mcp` extension announces its client kind itself during `session.register`.

### Internal `kind` Mapping

| Command | `kind` |
|---|---|
| `launch thin/thick/ordinary` | not passed; the client side announces the default kind |
| `launch mcp` | `v8_runner_client` |
| `launch mcp va` | `vanessa_test_client` |
| `test yaxunit ...` | `yaxunit_runner` |
| `test va ...` | `vanessa_test_client` |

### What v8-runner Injects into `/C` in the WS Branch

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;kind=<KIND>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

For `launch mcp` / `launch mcp va`, this is the entire `/C`. For `launch thin/thick/ordinary`, the same WS fragment is used **without** `kind=<KIND>` and is appended with `;` to an existing `/C` value if one is already set. For test commands, the WS fragment is appended with `;` to the existing `RunUnitTests=…` / Vanessa player (if `transport=ws` is selected through the yaml config).

Regular thin client in WS mode:

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

Important: do not add `kind` for regular `launch thin/thick/ordinary`. Such a client registers the base `client_mcp` tools, but does not publish Vanessa Automation MCP tools by itself.

### Vanessa Automation MCP through session-manager

For the Vanessa Research/Scenario workflow through our `v8-client-session-manager`, start not a plain thin client but a test manager session with the Vanessa Automation processing opened:

```bash
v8-runner launch mcp va \
  --mcp-transport ws \
  --manager-url ws://127.0.0.1:4000/sessions \
  --client-uid <uid> \
  --corr-id <uid> \
  --mcp-log-level debug \
  --mcp-ws-timeout-ms 5000
```

Expected 1C launch shape assembled by the runner:

```text
1cv8c ENTERPRISE
  /TESTMANAGER
  /DisableStartupDialogs
  /DisableUnsafeActionProtection
  /IBConnectionString <connection string from v8project.yaml>
  /N <user>
  /P <password>
  /Execute <path>/vanessa-automation.epf
  /C"mcpMode=ws;manager_url=ws://127.0.0.1:4000/sessions;client_uid=<uid>;kind=vanessa_test_client;corr_id=<uid>;mcp_log_level=debug;mcp_ws_timeout_ms=5000"
```

VA MCP session readiness criterion: `session_list` contains a live `kind=vanessa_test_client` session, and its tools include VA tools (`get_VanessaAutomation_state`, `connect_test_client`, `get_form_analysis`, `manage_command_interface`) or the tool count is greater than the base `client_mcp` set. The initial registration with base tools does not yet mean that `MCPVA.ЗарегистрироватьИнструментыMCP()` has run.

The full payload, the JSON output form (`--json-message`), probe rules, and behavior when the manager is unavailable are in `references/project-workflows.md` (section "WS Mode with session-manager"). Bringing up the manager itself is **not** part of v8-runner — see the `v8-session-manager` skill.

### Resolved: WS Sessions in `test yaxunit` (DRIVE 2026-05-11)

Symptom: `yaxunit_runner` is NOT registered in the manager's `session_list`, although v8-runner correctly injects the WS payload into `/C` (`RunUnitTests=...;mcpMode=ws;...;kind=yaxunit_runner;...`).

The root cause is a race condition in BSL `client_mcp` (`ManagedApplicationModule.bsl`): the idle handler `Мсп_ОтложенныйСтарт_Тик` was set with a **1 second** interval, while YAXUNIT with `closeAfterTests: true` closed the application about 1 second after startup (the tests run in about 200 ms), so the idle handler did not have time to tick.

Fix: reduce the idle-handler interval from `1` to `0.1`:
```bsl
// exts/client_mcp/Ext/ManagedApplicationModule.bsl
ПодключитьОбработчикОжидания("Мсп_ОтложенныйСтарт_Тик", 0.1, Истина);
```

After the fix, yaxunit-Enterprise registers as `kind=yaxunit_runner` in the manager's `session_list` (confirmed in v8-runner stdout: `[MCP INFO ...] WS session registered: uid=... kind=yaxunit_runner ... tools=24`).

## Headless Launch of an External Processing Object (.epf) with a Server Method Call

Launching an external processing object in batch (headless) mode with automatic execution of its logic is done through `v8-runner launch <thin|thick|ordinary> --execute "<path to .epf>"` (this is `1cv8 ENTERPRISE /Execute<epf>`). The key nuance, without which the approach does not work:

- **`/Execute<epf>` OPENS the processing form** (it emulates "Open processing"). By itself it does **NOT** call the object module's export method. Therefore, a **processing object without a form** (only an object module with an export procedure) will **not execute** its logic through `/Execute` - the entry point will never be called.
- The canonical headless approach: the processing object **has a managed form**, and in its module there is a `&OnClient Procedure OnOpen(Cancel)` handler that recognizes batch mode by the **launch parameter**, calls an `&OnServer` method (which performs the work / invokes the object module's export procedure), and then cleanly terminates the session through `EndSystemWork(False)`.

### Passing the Parameter and Suppressing the Security Warning

- The launch parameter is passed with the `--c "<string>"` key (this is `/C"<string>"`) and read in BSL through `LaunchParameter()`. Use a sentinel string so that the form can distinguish headless launch from interactive opening and does not auto-execute when opened manually.
- The **first launch** of an external processing object raises a security warning dialog (protection against dangerous actions) - in headless mode it will hang the process. It is suppressed with the `--raw-key /DisableUnsafeActionProtection` key. An alternative is to clear the user's "Protection against dangerous actions" flag or configure a security profile (but the CLI key is preferable for one-off runs).

### Minimal Processing Skeleton

```bsl
// Processing form module
&OnClient
Procedure OnOpen(Cancel)
    If LaunchParameter() = "BATCH_START" Then   // sentinel from --c
        Log = PerformOperationOnServer();       // server work
        // write Log to a known file for external verification
        EndSystemWork(False);                   // clean exit without dialogs
    EndIf;
EndProcedure

&OnServer
Function PerformOperationOnServer()
    // resolve all parameters on the SERVER side (not from form attributes - nobody filled them in headless),
    // perform business logic, return the log text
EndFunction
```

### Command and Verification

```bash
v8-runner launch thin --execute "<abs. path to .epf>" --c "BATCH_START" --raw-key /DisableUnsafeActionProtection
```

- The connection to the infobase is taken from `v8project.yaml` - no separate `/S`/`/F` is needed.
- **Completion condition:** wait for the 1cv8 process to exit OR for the log file written by the processing object to appear. The process exit code alone is a weak signal.
- **Verify the result by behavior, not by the fact of launch:** data delta (before/after query), log file contents, registration record in the event log. "The process ran without error" does not mean "the logic executed".

> Alternative without `/Execute`: from an **already connected** server session - `ExternalProcessing.Create(<path>, False)` + call its export method (or BSP `LongOperations.ExecuteProcessingObjectModuleProcedure`). This requires a "run code on the server" channel (session manager / test runner), while `/Execute` is self-sufficient from the command line.

## Protective Rules

- Before any v8-runner operation that accesses an infobase, apply auth-guard: check credentials and classify possible errors (license / auth / path) — see `references/auth-guard.md`.
- Do not delete or recreate an infobase, workspace, temp directory, or generated state unless the user explicitly asked for it or the command itself is documented as a recovery path.
- Do not invent raw `1cv8`, `ibcmd`, or `1cedtcli` flags; prefer the `v8-runner` command surface.
- Before `dump`, check `git status` if the result may overwrite or mix with already applied source edits.
- Keep failed test artifacts in `workPath/temp/<runner-id>/runs/<run-id>/` for diagnostics; do not clean them immediately.
- Report missing local 1C utilities as environment/install problems, not project source errors.
- Keep final responses concrete: executed command, result, path to the relevant artifact, and any follow-up command.

## Output Discipline

When reporting results, separate:

- project source failures;
- v8-runner command/config failures;
- failures locating the local 1C platform, EDT, IBCMD, or tools;
- test failures and their artifact paths.
