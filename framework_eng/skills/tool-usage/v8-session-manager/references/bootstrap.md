# Starting the manager and connecting clients

Only the parameters set by the agent are described. Everything that has sensible defaults is left alone; full reference: `docs/CONFIGURATION.md`.

## Minimal config

`v8project.yaml`:

```yaml
workPath: /var/lib/v8-session-manager
```

One required key. Without `workPath`, the manager will not start. Default addresses: WS `127.0.0.1:4000/sessions`, HTTP MCP `127.0.0.1:4001/mcp`.

## When to change defaults

| Parameter | When to add |
|---|---|
| `mcp.session_manager.bind_address` | The manager must listen on more than loopback (devcontainer, remote agent). Then `0.0.0.0:4000` |
| `mcp.http.bind_address` | Same for HTTP MCP, for example `0.0.0.0:4001` |
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

The remaining keys (`idle_timeout_secs`, `reconnection_grace_secs`, `ws_ping_*`, `max_sessions`, `stateful_sessions`) are set only when there is an explicit tuning task. The defaults are fine.

## Persistent tools-cache (ADR-0035)

Top-level section `tools_cache:`. **Defaults usually work** - change only when tuning.

```yaml
# Кеш переживает рестарт менеджера; нужен для MCP-харнесов, которые
# нестабильно реагируют на notifications/tools/list_changed (например Claude Code).
tools_cache:
  enabled: true              # default true; false ⇒ rollback to live-only (as before ADR-0035)
  cache_life_period: 5d      # humantime: 5d, 12h, 30m; minimum 1s
  storage_path: tools_cache.json   # relative — from workPath; absolute — as is
```

| Parameter | When to change |
|---|---|
| `tools_cache.enabled: false` | Targeted smoke / diagnostics without disk; or the manager sits behind a reverse proxy that caches on its own |
| `tools_cache.cache_life_period` | The configuration changes more often / less often than once every 5 days. Minimum 1s (validator will reject anything smaller) |
| `tools_cache.storage_path` | You want to place the cache in a specially mounted path / shared volume |

Behavior when the section is absent is equivalent to `tools_cache: {}` (i.e. the defaults above). Behavior when `enabled: false` is described in detail in ADR-0035 and in `sessions-and-tools.md`.

## Starting the manager

| Scenario | Command |
|---|---|
| Dev mode from the manager repo | `cargo run --release` (will pick up `./v8project.yaml`) |
| Ready-made binary | `./v8-session-manager --config /path/to/v8project.yaml` |
| Production | systemd unit from `docs/INSTALL.md` (`systemctl start v8-session-manager`) |

ENV `V8SM_CONFIG=<path>` is an alternative to `--config`.

## Connecting a 1С client

The manager only accepts incoming WS. Starting the 1С client and forming connection parameters is the task of `v8-runner` (skill `v8-runner`) for any client type: `launch designer/thin/thick/ordinary`, `launch mcp [va]`, `test yaxunit`, `test va`. All client types support the same WS flags (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`); the subtlety is that for `test` commands they must be placed BEFORE the `yaxunit/va` subcommand - otherwise clap does not accept them. `kind` is fixed by the entry point (`v8_runner_client` / `vanessa_test_client` / `yaxunit_runner`) and cannot be overridden from the CLI.

From the manager side it is enough to know: the client must come to the manager's `manager_url` and on `session.register` specify its `kind` (determines tool routing on the storefront) and `client_uid` (for soft-reconnect).

## Checking that the manager is up

The process is alive, the log contains `accepting WebSocket on ...` and `accepting HTTP on ...`. Next is the orchestrator's task (starting the client - skill `v8-runner`) and checking the storefront (`sessions-and-tools.md`).
