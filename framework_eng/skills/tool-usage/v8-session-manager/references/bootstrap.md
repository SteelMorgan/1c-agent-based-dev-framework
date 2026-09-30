# Starting the Manager and Connecting Clients

Only parameters set by the agent are described. Anything for which sensible defaults work is left alone; full reference: `docs/CONFIGURATION.md`.

## Minimal config

`v8project.yaml`:

```yaml
workPath: /var/lib/v8-session-manager
```

One required key. Without `workPath`, the manager will not start. Default addresses: WS `127.0.0.1:4000/sessions`, HTTP MCP `127.0.0.1:4001/mcp`.

## When to change the defaults

| Parameter | When to add |
|---|---|
| `mcp.session_manager.bind_address` | The manager must listen on more than loopback (devcontainer, remote agent). Then `0.0.0.0:4000` |
| `mcp.http.bind_address` | The same for HTTP MCP, for example `0.0.0.0:4001` |
| `mcp.http.auth_token` | Protect HTTP MCP with a token (production / shared network) |
| `mcp.metrics.bind_address` | Enable Prometheus metrics on `127.0.0.1:9100` |

Example:

```yaml
workPath: /var/lib/v8-session-manager
mcp:
  session_manager:
    bind_address: "0.0.0.0:4000"
  http:
    bind_address: "0.0.0.0:4001"
    auth_token: "<token>"
```

The remaining keys (`idle_timeout_secs`, `reconnection_grace_secs`, `ws_ping_*`, `max_sessions`, `stateful_sessions`) are set only when there is an explicit tuning task. The defaults are suitable.

## Persistent tools-cache (ADR-0035)

Top-level section `tools_cache:`. **Defaults are usually fine** - change only when tuning.

```yaml
# The cache survives manager restarts; needed for MCP harnesses that
# respond unreliably to notifications/tools/list_changed (for example Claude Code).
tools_cache:
  enabled: true              # default true; false ⇒ rollback to live-only (as before ADR-0035)
  cache_life_period: 5d      # humantime: 5d, 12h, 30m; minimum 1s
  storage_path: tools_cache.json   # relative — from workPath; absolute — as is
```

| Parameter | When to change |
|---|---|
| `tools_cache.enabled: false` | Targeted smoke / diagnostics without disk; or the manager is behind a reverse proxy that caches itself |
| `tools_cache.cache_life_period` | Configuration changes more or less often than once every 5 days. Minimum 1s (validator will not allow less) |
| `tools_cache.storage_path` | You want to place the cache in a specially mounted path / shared volume |

Behavior when the section is absent is equivalent to `tools_cache: {}` (i.e. the defaults above). Behavior when `enabled: false` is described in detail in ADR-0035 and in `sessions-and-tools.md`.

## Starting the Manager

| Scenario | Command |
|---|---|
| Container environment | `v8sm --config /path/to/v8project.yaml` or auto-start of the container |
| Full alias | `v8-session-manager --config /path/to/v8project.yaml` |
| Legacy fallback after framework installation | `tools/external/v8-session-manager/v8-session-manager --config /path/to/v8project.yaml` |
| Dev mode from the manager repo | `cargo run --release` (will pick up `./v8project.yaml`) |
| Production | systemd unit from `docs/INSTALL.md` (`systemctl start v8-session-manager`) |

In the container, the manager is usually in `PATH` and starts automatically. Before starting it manually, make sure there is no live process/HTTP listener already running and that `session_list` is not responding from an existing instance. The fallback installation binary is pulled as the Latest release from [`1c-neurofish/v8-session-manager`](https://github.com/1c-neurofish/v8-session-manager) when the framework installer runs; manual reinstall is `python tools/install.py --install-external-tools`.

ENV `V8SM_CONFIG=<path>` is an alternative to `--config`.

## Connecting the 1C Client

The manager only accepts incoming WS connections. Starting the 1C client and forming the connection parameters is the job of `v8-runner` (skill `v8-runner`) for any client type: `launch designer/thin/thick/ordinary`, `launch mcp [va]`, `test yaxunit`, `test va`. All client types support the same WS flags (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`); the nuance is that on `test` commands they must be set BEFORE the `yaxunit/va` subcommand - otherwise clap does not accept them. `kind` is fixed by the entry point (`v8_runner_client` / `vanessa_test_client` / `yaxunit_runner`) and cannot be overridden from the CLI.

From the manager side, it is enough to know: the client must connect to the manager's `manager_url` and, during `session.register`, specify its `kind` (determines tool routing on the storefront) and `client_uid` (for soft reconnect).

## Verifying that the manager has started

The process is alive, the log contains `accepting WebSocket on ...` and `accepting HTTP on ...`, or the built-in `session_list` tool responds without an error. An empty `sessions: []` means only that there are no connected 1C clients, not that the manager has crashed. Next is the orchestrator task (client launch - skill `v8-runner`) and checking the dashboard (`sessions-and-tools.md`).
