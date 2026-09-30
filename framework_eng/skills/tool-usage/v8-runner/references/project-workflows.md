# Project Scenarios

Use these flows according to the user's intent. Do not split the workflow just because the sources are Designer or EDT; many teams share the same lifecycle and differ only by `format`, `builder`, or tool availability.

Read the exact support rules in `config-and-backends.md` together with this file.

## Initialization

Create the default config if the project does not have `v8project.yaml`:

```bash
v8-runner config init
```

Choose a narrower init command only when the project form is known:

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

Use a full rebuild after switching branches, rebase, broad object moves, or suspicious incremental state:

```bash
v8-runner build --full-rebuild
```

`build` is the general scenario. For EDT projects, it can export EDT sources into Designer files before applying them through the configured backend. For Designer projects, it applies Designer sources directly through the configured backend.

If `tools.wt_mcp_adapter.extension` is configured, `build` also prepares this tool extension after the project source-set stage, including for narrow builds with `--source-set`. Source-based tool extensions use their own change-detection state and are skipped if nothing has changed; use `build --full-rebuild` to force an update. Do not add the tool extension as a project source set and do not select it via `--source-set`.

### Build result control

`v8-runner build` can take minutes. For long runs, use the Monitor tool:

1. Run it in the background (`Bash run_in_background: true`), redirect stdout to a file.
2. Subscribe through **Monitor** with the filter `ERROR:|Failed|error:` - the notification will arrive on the first match.
3. End the wait: the process finished OR `ERROR:` / `Failed` / an explicit success indicator appeared in stdout.
4. After completion: exit code 0 = success; otherwise, read stdout for the error.

## Syntax

Choose syntax checks based on the capabilities of the config, not on assumptions about the repository name.

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

If the syntax command is unavailable for the current `format` or `builder`, report the config limitation instead of inventing raw platform commands.

## Dump

Use dump when the desired source of truth is the current state of the IB.

Before the dump, inspect the current changes in Git:

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

Use `extensions` when you need to synchronize extension properties without a broader rebuild step.

Do not replace extension-specific synchronization with a full rebuild unless the user asks for recovery or a narrower command fails for a relevant reason.

```bash
v8-runner extensions
v8-runner extensions --name <SOURCE_SET>
```

## Launch

Prefer runner `launch` commands over assembling raw `1cv8` commands:

```bash
v8-runner launch designer
v8-runner launch thin
v8-runner launch thick
v8-runner launch ordinary
```

Run wt-mcp-adapter through the supported `launch mcp` surface, rather than manually assembling `/C"runMcp..."`:

```bash
v8-runner launch mcp
v8-runner launch mcp --mode thin --mcp-port <PORT>
v8-runner launch mcp --mcp-config <FILE>
```

For a direct ordinary-launch, the typed launch flags include `--c`, `--execute`, `--use-privileged-mode`, `--output`, and the repeatable `--raw-key`.

For `launch mcp`, use `--mcp-config` and `--mcp-port`; do not pass `/C` through `--c`.

`launch mcp` and `launch mcp va` do not install or update `tools.wt_mcp_adapter.extension`; run `v8-runner build` first if this extension may be missing or outdated.

For `launch mcp va`, read `testing.md`; this is part of the Vanessa Automation debugging and scenario-writing workflow.

## WS mode for session-manager

> SteelMorgan forks used for WS transport (`v8-runner-rust`, `wt-mcp-adapter`) are canonical in `SKILL.md`, section “Command Form”.

When [`v8-session-manager`](https://github.com/1c-neurofish/v8-session-manager) is running near the project, the 1C client can connect to it over WebSocket instead of the local HTTP MCP server (`runMcp` mode). In containerized environments, the manager usually starts automatically; before launching the client, it is enough to check the availability of the manager endpoint / the manager live session, rather than starting a new instance. v8-runner chooses the transport automatically. This section is the canonical source for transport mechanics, `/C`, `kind`, VA MCP, and the UI MCP workflow.

### Transport and auto-detection

`tools.wt_mcp_adapter.transport`:

- `auto` (default) - a short TCP probe (200 ms) on the host:port from `manager_url`. Listener present -> WS, otherwise -> `mcp`.
- `ws` - strict WS; if the manager is unavailable, startup fails with `session-manager unreachable at <url>`.
- `mcp` - local HTTP MCP mode without a probe.

Override via `--mcp-transport={ws|mcp|auto}`. CLI takes precedence over config. The same parameters are configured via `tools.wt_mcp_adapter.*` in `v8project.yaml` / `v8project.local.yaml`:

```yaml
tools:
  wt_mcp_adapter:
    transport: auto         # mcp | ws | auto
    manager_url: ws://127.0.0.1:4000/sessions
    log_level: info
    ws_timeout_ms: 1000
```

### What v8-runner passes into `/C` in the WS branch

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;kind=<KIND>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

Sources of values:

| Key | Default | Override |
|------|--------------|----------|
| `manager_url` | `tools.wt_mcp_adapter.manager_url` or `ws://127.0.0.1:4000/sessions` | `--manager-url <URL>` |
| `client_uid` | a new UUID v4 on each run | `--client-uid <UUID>` |
| `kind` | internal mapping (see the table below) | (none - kind is not overridden from CLI) |
| `corr_id` | `vr-<first 8 characters of client_uid>` | `--corr-id <STR>` |
| `mcp_log_level` | `tools.wt_mcp_adapter.log_level` or `info` | `--mcp-log-level={off\|error\|warn\|info\|debug\|trace}` |
| `mcp_ws_timeout_ms` | `tools.wt_mcp_adapter.ws_timeout_ms` or `1000` | `--mcp-ws-timeout-ms <N>` |

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

The manager proxy tools are published on MCP HTTP under plain names - `<toolname>`, **without** the `<kind>__` prefix. `kind` determines routing of requests to the required client inside the manager, but it does not appear in tool names. Do not override `kind` manually.

### Test subcommands (`test yaxunit`, `test va`)

For test runs, the WS fragment is **appended** with `;` to the existing `/C` (`RunUnitTests=…` or Vanessa-player). There is no need to specify any separate flags - the same `--mcp-transport`/`--manager-url`/`--mcp-log-level` are available here as well.

### JSON-output

In `--json-message` mode, the response of the launch and test commands includes transport fields:

WS branch:
```json
{ "transport": "ws", "client_uid": "...", "kind": "...", "manager_url": "...", "corr_id": "..." }
```
MCP branch:
```json
{ "transport": "mcp", "mcp_port": 9874 }
```

The external orchestrator (CI, AI agent) uses `client_uid` to find the session in the manager's `session_list`. The structure of the session record and `session_list` are described in the `v8-session-manager` skill.

### The manager does not start from v8-runner

v8-runner only connects to a running manager and must not start it itself. In the standard container, the manager is already started automatically; if checks show that there is no manager, start it according to the `v8-session-manager` skill, not through `v8-runner`. If the manager is not needed, `--mcp-transport=mcp` forces the local HTTP MCP flow.

### UI MCP through the platform test client

If the task is to go through the 1C interface via client MCP-tools (`open_form`, `click`, `input`, `get_value`, `get_table_rows`, `test_client_start`), use this path for structural control of the regular 1C client. For visual acceptance and screenshots of managed forms, prefer VA MCP first according to the `va-visual-check` skill.

Workflow:

1. Check that session-manager responds through the MCP endpoint and does not require manual startup.
2. Start the controlling MCP client detached via `v8-runner launch ...` with WS transport and `/TESTMANAGER` through the standard v8-runner parameters. Save the PID/start log.
3. After the launch command, give the client 10-20 seconds before the first check. Then check the manager live session: the required `kind`, `state=active`, `disconnected_secs_ago=null`, the appropriate `infobase_name`, `inflight=0`.
4. After the live session appears, wait for the required UI tools to be registered with periodic checks for up to 120 seconds. The session may appear before the extension publishes the full set of tools. A basic smoke check before UI calls: `infobase_info` returns a response quickly.
5. Start the tested application as a separate process with `/TESTCLIENT -TPort <port>` and the same connection parameters, user, and password as in the project launch. It is preferable to start this process detached, save the PID/log, and pass all required project keys (`/N`, `/P`, if needed `/UC`, the same connection string). If the project has a standard TestClient launcher, use it; otherwise, the direct platform form is allowed:

```bash
setsid nohup /opt/1cv8/x86_64/<version>/1cv8c ENTERPRISE \
  /DisableStartupDialogs \
  /IBConnectionString 'Srvr="<server>";Ref="<infobase>";' \
  /N <user> /P <password> /UC <unlock_code> \
  /TESTCLIENT -TPort 1538 \
  > /tmp/test-client-1538.log 2>&1 &
```

6. Connect the tested application through the controlling MCP session:

```json
{"name":"test_client_start","arguments":{"session_id":"<1c-client session_id>","port":1538}}
```

Success criterion: `{"ok": true, "data": {"connected": true}}`.

7. After connecting, perform UI MCP-tools only through the controlling session's `session_id`: `open_form` → `click/input/select` → `get_value/get_table_rows`. If there are multiple live sessions, `session_id` is mandatory.
8. Before a long UI operation, check `inflight=0`; if the call hangs or `inflight` does not drop, move on to diagnosing `v8-session-manager`.
9. Explicitly terminate both processes: first the TestClient under test (closure tool/client command/saved PID), then the controlling `/TESTMANAGER` client. Apply `kill <PID>` only to the saved PIDs of your own processes.

Do not keep the client alive through `sleep`, `tail -f`, or an infinite shell loop. Such wrappers become the process owner and can cut off the WS session when the agent environment ends.

Do not do this:

- Do not start the controlling client without `/TESTMANAGER`: on the first `test_client_start`, the platform may crash with `Тип не определен (ТестируемоеПриложение)`.
- Do not rely on `test_client_start` as the only way to launch `/TESTCLIENT` if it starts the client without `/N` and `/P`: such a process may remain at the database entry, and the connection will return `Отсутствует подходящий клиент тестирования`.
- Do not treat `tools/list` as proof of readiness: proxied tools may come only from the session-manager cache. Readiness is confirmed by a live session and a successful simple call.
