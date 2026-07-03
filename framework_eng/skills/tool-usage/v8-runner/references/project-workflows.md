# Project workflows

Use these flows according to the user's intent. Do not split workflows just because the sources are Designer or EDT; many commands share the same lifecycle and differ only in `format`, `builder`, or tool availability.

Read the exact support rules in `config-and-backends.md` together with this file.

## Initialization

Create the default config if the project has no `v8project.yaml`:

```bash
v8-runner config init
```

Choose a narrower `init` command only when the project shape is known:

```bash
v8-runner config init --connection "File=build/ib"
v8-runner config init --format edt
v8-runner config init --builder IBCMD
```

Initialize the generated runtime state only when you need to create a file-based infobase or an EDT workspace:

```bash
v8-runner init
```

## Build

Apply Git-visible source changes to the configured runtime state:

```bash
v8-runner build
```

Use a full rebuild after branch switches, rebase, broad object moves, or suspicious incremental state:

```bash
v8-runner build --full-rebuild
```

`build` is the general scenario. For EDT projects it can export EDT sources into Designer files before applying them through the configured backend. For Designer projects it applies Designer sources directly through the configured backend.

If `tools.client_mcp.extension` is configured, `build` also prepares this tool extension after the project's source-set stage, including for narrow builds with `--source-set`. Source-based tool extensions use their own change-detection state and are skipped if nothing changed; use `build --full-rebuild` to force an update. Do not add the tool extension as a project source-set and do not select it through `--source-set`.

### Monitoring the Build Result

`v8-runner build` can take minutes. For long runs use the Monitor tool:

1. Launch in the background (`Bash run_in_background: true`) and redirect stdout to a file.
2. Subscribe via **Monitor** with the filter `ERROR:|Failed|error:` — a notification arrives on the first match.
3. Stop waiting when the process exits OR stdout contains `ERROR:` / `Failed` / an explicit success marker.
4. After completion: exit code 0 = success; otherwise read stdout for the error.

## Syntax

Choose syntax checks based on the config capabilities, not on assumptions from the repository name.

Designer module checks:

```bash
v8-runner build
v8-runner syntax designer-modules --server --thin-client
```

Designer configuration checks:

```bash
v8-runner build
v8-runner syntax designer-config
```

EDT checks:

```bash
v8-runner build
v8-runner syntax edt
```

If the `syntax` command is unavailable for the current `format` or `builder`, report the config limitation instead of inventing raw platform commands.

## Dump

Use `dump` when the desired source of truth is the current infobase state.

Before dumping, inspect the current Git changes:

```bash
git status --short
```

Incremental dump:

```bash
v8-runner dump --mode incremental
```

Object partial dump, when the backend supports it:

```bash
v8-runner dump --mode partial --object <TYPE:NAME>
```

After the dump, run `git diff` and report the affected files.

## Extensions

Use `extensions` when you need to synchronize extension properties without a broader restore step.

Do not replace extension-specific synchronization with a full rebuild unless the user asks for a restore or the narrower command fails for a relevant reason.

```bash
v8-runner extensions
v8-runner extensions --name <SOURCE_SET>
```

## Launch

Prefer the runner's launch commands rather than assembling raw `1cv8` commands:

```bash
v8-runner launch designer
v8-runner launch thin
v8-runner launch thick
v8-runner launch ordinary
```

Launch onec-client-mcp-devkit through the supported `launch mcp` surface, rather than assembling `/C"runMcp..."` manually:

```bash
v8-runner launch mcp
v8-runner launch mcp --mode thin --mcp-port <PORT>
v8-runner launch mcp --mcp-config <FILE>
```

For a direct ordinary launch, the typed launch flags include `--c`, `--execute`, `--use-privileged-mode`, `--output`, and the repeatable `--raw-key`.

For `launch mcp`, use `--mcp-config` and `--mcp-port`; do not pass `/C` through `--c`.

`launch mcp` and `launch mcp va` do not install or update `tools.client_mcp.extension`; run `v8-runner build` first if this extension may be missing or outdated.

Read `testing.md` for `launch mcp va`; it is part of the workflow for debugging and authoring Vanessa Automation scenarios.

## WS mode for session-manager

> SteelMorgan forks used for WS transport (`v8-runner-rust`, `onec-client-mcp-devkit`) are canonical in `SKILL.md`, section "Command Form".

When [`v8-client-session-manager`](https://github.com/SteelMorgan/v8-client-session-manager) is running alongside the project, the 1С client can connect to it over WebSocket instead of the local HTTP MCP server (`runMcp` mode). v8-runner makes the choice automatically. Applicable entry points, VA MCP and UI MCP workflow are in `SKILL.md` (section "WS parameters for coupling with session-manager"); this section is canonical for transport mechanics, `/C` and `kind`.

### Transport and autodetection

`tools.client_mcp.transport`:

- `auto` (default) — a short TCP probe (200 ms) to the host:port from `manager_url`. Listener detected → WS, otherwise → `mcp`.
- `ws` — strict WS; if the manager is unavailable, launch fails with `session-manager unreachable at <url>`.
- `mcp` — local HTTP MCP mode without a probe.

Override via `--mcp-transport={ws|mcp|auto}`. CLI takes precedence over config. The same parameters are configured via `tools.client_mcp.*` in `v8project.yaml` / `v8project.local.yaml`:

```yaml
tools:
  client_mcp:
    transport: auto         # mcp | ws | auto
    manager_url: ws://127.0.0.1:4000/sessions
    log_level: info
    ws_timeout_ms: 1000
```

### What v8-runner injects into `/C` in the WS branch

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;kind=<KIND>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

Sources of values:

| Key | Default | Override |
|------|--------------|----------|
| `manager_url` | `tools.client_mcp.manager_url` or `ws://127.0.0.1:4000/sessions` | `--manager-url <URL>` |
| `client_uid` | a new UUID v4 for each launch | `--client-uid <UUID>` |
| `kind` | internal mapping (see table below) | (none - `kind` is not overridden from the CLI) |
| `corr_id` | `vr-<first 8 characters of client_uid>` | `--corr-id <STR>` |
| `mcp_log_level` | `tools.client_mcp.log_level` or `info` | `--mcp-log-level={off\|error\|warn\|info\|debug\|trace}` |
| `mcp_ws_timeout_ms` | `tools.client_mcp.ws_timeout_ms` or `1000` | `--mcp-ws-timeout-ms <N>` |

For `launch mcp` / `launch mcp va`, this fragment is the entire `/C`. For `launch thin/thick/ordinary`, the same WS fragment is used, but **without** `kind=<KIND>`, and it is appended via `;` to the existing `/C` if one is already set:

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

**Important:** do not add `kind` manually for `launch thin/thick/ordinary` - such a client publishes only the base `client_mcp` tools, not Vanessa Automation MCP.

### Internal `kind` mapping

| v8-runner command | `kind` |
|---|---|
| `launch thin/thick/ordinary` | not passed; the client side declares the default kind |
| `launch mcp` | `v8_runner_client` |
| `launch mcp va` | `vanessa_test_client` |
| `test yaxunit ...` | `yaxunit_runner` |
| `test va ...` | `vanessa_test_client` |

The manager's proxy tools are published over MCP HTTP under bare names - `<toolname>`, **without** the `<kind>__` prefix. `kind` determines request routing to the correct client inside the manager, but does not appear in tool names. Do not override `kind` manually.

### Test subcommands (`test yaxunit`, `test va`)

For test runs, the WS fragment is appended via `;` to the existing `/C` (`RunUnitTests=…` or Vanessa player). No separate flags need to be specified - the same `--mcp-transport`/`--manager-url`/`--mcp-log-level` are available here too.

### JSON output

In `--json-message` mode, the response from launch and test commands includes transport fields:

WS branch:
```json
{ "transport": "ws", "client_uid": "...", "kind": "...", "manager_url": "...", "corr_id": "..." }
```
MCP branch:
```json
{ "transport": "mcp", "mcp_port": 9874 }
```

An external orchestrator (CI, AI agent) uses `client_uid` to find the session in the manager's `session_list`. The structure of the session entry and `session_list` is described in the `v8-session-manager` skill.

### The manager is not started from v8-runner

v8-runner only connects to a running manager. Starting the manager is a separate step (`cargo run --release` in the `v8-client-session-manager` repo, or the `systemd/v8-session-manager.service` unit, or Docker Compose). If the manager is not needed, `--mcp-transport=mcp` forces the local HTTP MCP flow.
