# Starting the manager and connecting clients

Only the parameters set by the agent are described here. Anything that works with sensible defaults is left untouched; full reference: `docs/CONFIGURATION.md`.

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

The remaining keys (`idle_timeout_secs`, `reconnection_grace_secs`, `ws_ping_*`, `max_sessions`, `stateful_sessions`) should be set only when there is an explicit tuning task. By default, they are fine.

## Starting the manager

| Scenario | Command |
|---|---|
| Dev mode from the manager repo | `cargo run --release` (will pick up `./v8project.yaml`) |
| Ready-made binary | `./v8-session-manager --config /path/to/v8project.yaml` |
| Production | systemd unit from `docs/INSTALL.md` (`systemctl start v8-session-manager`) |

ENV `V8SM_CONFIG=<path>` is an alternative to `--config`.

## Connecting the 1С client

The manager only accepts incoming WS. Starting the 1С client and forming connection parameters is the task of `v8-runner` (skill `v8-runner`). From the manager side, it is enough to know: the client must come to the manager's `manager_url` and, at `session.register`, specify its `kind` (defines the tools namespace on the storefront) and `client_uid` (for soft-reconnect).

## Checking that the manager is up

The process is alive, the log contains `accepting WebSocket on ...` and `accepting HTTP on ...`. Then it is the orchestrator's task (launching the client - skill `v8-runner`) and checking the storefront (`sessions-and-tools.md`).
