---
name: v8-runner
description: "Use when Codex needs to operate v8-runner on local 1С projects through the CLI: configure v8project.yaml, initialize infobases or EDT workspaces, build Designer or EDT sources, run syntax checks and tests, dump infobase changes, convert source formats, load or export artifacts, launch 1С clients, or choose safe 1С automation command sequences."
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

Use this skill to operate `v8-runner` as the automation layer for local 1С development projects.

Keep this file as the decision entrypoint. Load only the reference file that matches the task:

- `references/command-selection.md` for choosing the right command sequence.
- `references/bootstrap.md` for generating `v8project.yaml` from an existing repository: what to determine on your own and what to ask the user (decision tree for `format`, `builder`, `connection`).
- `references/config-and-backends.md` for `v8project.yaml`, source sets, formats, builders, and backend limits.
- `references/project-workflows.md` for typical build, syntax, dump, launch, and source synchronization workflows for Designer and EDT projects.
- `references/file-and-artifact-workflows.md` for dump, convert, load, make/artifacts, and staged publication.
- `references/testing.md` for YaXUnit, Vanessa Automation, syntax checks, and artifacts.
- `references/troubleshooting.md` for setup failures, stale state, and environment diagnostics.

## Command Form

Use the available `v8-runner` binary directly. If it is not on `PATH`, ask for the path to the binary or use the wrapper script from the project.

`v8project.yaml` is the default project config name. A neighboring `v8project.local.yaml` is loaded automatically for machine-local paths, credentials, tools, tests, and MCP settings. Do not pass `--config v8project.yaml` unless the user explicitly asks for a non-default command form or the active config path differs from the default; never pass `v8project.local.yaml` through `--config`.

Generated `v8project.yaml` files contain a `yaml-language-server` modeline that points to the versioned JSON Schema for the current `v8-runner` release. For `v8project.local.yaml`, use the corresponding raw GitHub tag URL `docs/schemas/v8project.local.schema.json` in editor settings when schema-assisted editing matters.

Use JSON output only when another tool, script, or final answer needs structured results:

```bash
v8-runner --json-message build
```

Use text output for direct human diagnostics.

Useful global flags:

- `--config <CONFIG>` when the active config is not `./v8project.yaml`.
- `--json-message` for machine-readable CLI envelopes.
- `--workdir <WORKDIR>` overrides `workPath`; it has priority over `v8project.local.yaml`.
- `--clean-before-execution` to clear logs before execution.
- `--log-level <error|warn|info|debug|trace>` for diagnostics.
- `--no-color` for plain text output.

## First Pass

1. Check whether `v8project.yaml` exists at the root of the 1С project.
2. If it does not exist, run the narrowest `v8-runner config init ...` command that fits the project form.
3. Inspect the generated config before running mutating commands.
4. Run `v8-runner init` only when you need to create a file infobase or EDT workspace.
5. Run the narrowest validation command that answers the user's goal.

Useful bootstrap commands:

```bash
v8-runner config init
v8-runner config init --connection "File=build/ib"
v8-runner config init --format edt
v8-runner config init --builder IBCMD
v8-runner init
```

## Default Use-Case Routing

- Source files changed and the infobase may be stale: run `v8-runner build`.
- Only one source set changed: use commands that accept `--source-set <NAME>` instead of rebuilding or materializing everything.
- Branch switch, rebase, large object moves, stale source-backed tool extension state, or suspicious incremental state: run `v8-runner build --full-rebuild`.
- Syntax check: inspect `format` and `builder`, then choose `syntax designer-modules`, `syntax designer-config`, or `syntax edt`.
- Behavior validation: run the relevant `v8-runner test ...` command; tests build first.
- Vanessa Automation debugging or scenario authoring: use `v8-runner launch mcp va ...` to start the client MCP server with VA loaded.
- Extension properties need synchronization: use `v8-runner extensions` or `extensions --name <SOURCE_SET>`.
- Infobase changes must become Git-visible files: check `git status`, then run the relevant `v8-runner dump ...` command.
- Source files need conversion between Designer and EDT: use `v8-runner convert`; this is CLI-only and does not use the infobase.
- Existing `.cf` or `.cfe` artifacts need to be applied to an infobase: use `v8-runner load ...`.
- Release artifacts need to be exported or external artifacts published: use `v8-runner make ...` or the `artifacts` alias.
- Need a 1С UI session: use `v8-runner launch designer`, `launch thin`, `launch thick`, or `launch ordinary`.
- Need to run onec-client-mcp-devkit inside 1С without VA authoring: use `v8-runner launch mcp ...`.
- Pair the running 1С client with a running [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) over WebSocket: see the dedicated section "WS pairing parameters" below. WS flags (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`) are available identically on `launch ...` and `test ...` commands. Subtle clap structure point: on `test`, the flags go **before** the `yaxunit/va` subcommand (e.g. `v8-runner test --mcp-transport=ws yaxunit module <NAME>`), not after.

## WS Pairing Parameters with session-manager

WS pairing with [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) is a mode in which the 1С client-side MCP server connects to the manager over WebSocket instead of local HTTP MCP. The same set of CLI flags or `tools.client_mcp.*` in `v8project.yaml` controls it.

### Applicable entry points

The same set of flags works for:

- `v8-runner launch designer | thin | thick | ordinary` — flags go after `launch`.
- `v8-runner launch mcp` / `launch mcp va` — flags go after `launch mcp [va]`.
- `v8-runner test yaxunit all` / `test yaxunit module <NAME>` — flags go **at the `test` level**, BEFORE the `yaxunit` subcommand.
- `v8-runner test va` — flags go **at the `test` level**, BEFORE the `va` subcommand.

Example (test): `v8-runner test --mcp-transport=ws --mcp-log-level=debug yaxunit module mcp_МспПровайдер_Тесты`. If you put WS flags after `yaxunit` or `module <NAME>`, clap responds with `error: unexpected argument`, because those subcommands do not declare `McpClientWsArgs` of their own.

### CLI flags

- `--mcp-transport={ws|legacy|auto}` — `auto` (default) performs a TCP probe on `manager_url` (~200 ms); `ws` — strictly WS, fails on unreachable; `legacy` — old HTTP mode without probe.
- `--manager-url <URL>` — override `tools.client_mcp.manager_url` (default `ws://127.0.0.1:4000/sessions`).
- `--client-uid <UUID>` — override the auto-generated UUID v4.
- `--corr-id <STR>` — override `vr-<first 8 chars of client_uid>`.
- `--mcp-log-level={off|error|warn|info|debug|trace}` — log level inside the client.
- `--mcp-ws-timeout-ms <N>` — WS handshake timeout (default 1000 ms; relevant for `auto` fallback).

Alternative: all of these can be set in `tools.client_mcp.*` in `v8project.yaml` / `v8project.local.yaml` — priority order: CLI → YAML → internal defaults.

```yaml
tools:
  client_mcp:
    transport: auto         # ws | legacy | auto
    manager_url: ws://127.0.0.1:4000/sessions
    log_level: info
    ws_timeout_ms: 1000
```

`kind` is fixed by the entry point and is not configurable from CLI in any mode.

### Internal `kind` mapping

| Command | `kind` |
|---|---|
| `launch mcp` | `v8_runner_client` |
| `launch mcp va` | `vanessa_test_client` |
| `test yaxunit ...` | `yaxunit_runner` |
| `test va ...` | `vanessa_test_client` |

### What v8-runner substitutes into `/C` in the WS branch

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;kind=<KIND>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

For launch — this is the entire `/C`; for test commands, the WS fragment is appended via `;` to the existing `RunUnitTests=…` / Vanessa player (only if `transport=ws` is chosen via the YAML config).

Full payload, JSON output (`--json-message`), probe rules, and behavior when the manager is unreachable — in `references/project-workflows.md` (section "WS mode with session-manager"). Starting the manager itself is **out of scope** for v8-runner — see the `v8-session-manager` skill.

### Resolved: WS Sessions in `test yaxunit` (DRIVE 2026-05-11)

Symptom: `yaxunit_runner` is NOT registered in the manager's `session_list`, although v8-runner correctly inserts the WS payload into `/C` (`RunUnitTests=...;mcpMode=ws;...;kind=yaxunit_runner;...`).

The root cause is a race condition in the BSL `client_mcp` (`ManagedApplicationModule.bsl`): the idle handler `Мсп_ОтложенныйСтарт_Тик` was scheduled with a **1 second** interval, while YAXUNIT with `closeAfterTests: true` closed the application about 1 second after startup (tests finish in about 200 ms), so the idle handler did not get a chance to tick.

Fix: reduce the idle-handler interval from `1` to `0.1`:
```bsl
// exts/client_mcp/Ext/ManagedApplicationModule.bsl
ПодключитьОбработчикОжидания("Мсп_ОтложенныйСтарт_Тик", 0.1, Истина);
```

After the fix, yaxunit-Enterprise is registered as `kind=yaxunit_runner` in the manager's `session_list` (confirmed in v8-runner stdout: `[MCP INFO ...] WS session registered: uid=... kind=yaxunit_runner ... tools=24`).

## Guardrails

- Do not delete or recreate an infobase, workspace, temporary directory, or generated state unless the user explicitly asked for it or the command itself is documented as the recovery path.
- Do not invent raw `1cv8`, `ibcmd`, or `1cedtcli` flags; prefer the `v8-runner` command surface.
- Check `git status` before `dump` if the result may overwrite or mix with already made source changes.
- Preserve failed test artifacts in `workPath/temp/<runner-id>/runs/<run-id>/` for diagnostics; do not clean them up immediately.
- Report missing local 1С utilities as environment/setup problems, not as project source errors.
- Keep final answers concrete: the command run, the result, the path to the relevant artifact, and any follow-up command.

## Output Discipline

When reporting results, distinguish:

- project source failures;
- v8-runner command/config failures;
- local 1С platform, EDT, IBCMD, or tool discovery failures;
- test failures and their artifact paths.
