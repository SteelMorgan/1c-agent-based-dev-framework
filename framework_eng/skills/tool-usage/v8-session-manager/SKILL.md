---
name: v8-session-manager
description: Use when working with the 1С session manager (v8-session-manager) - launch, configuration, connecting 1С clients, reading session_list, calling proxied MCP-tools from 1С extensions, diagnostics. Triggers: mention of `v8-session-manager`, `session_list`, 1С extension MCP showcase, error “no active sessions” / “session_id required”, connecting a client to the manager via `mcpMode=ws`.
provides_capabilities:
  # Built-in manager tools — always available while the manager is running.
  - session_list
  # Tools that the manager proxies from connected 1С clients
  # (availability depends on which client is connected via WS).
  # client_mcp / system:
  - infobase_info
  - system_spawn_1c_client
  - system_kill_pid
  - timer
  # test_client (UI control):
  - test_client_start
  - test_client_stop
  - ui_find
  - ui_open_form
  - ui_activate
  - ui_click
  - ui_close
  - ui_input
  - ui_select
  - ui_select_row
  - ui_get_value
  - ui_get_cell_value
  - ui_get_table_rows
  - ui_wait_for
---

# v8-session-manager

A thin MCP aggregator: accepts WS connections from 1С clients and publishes their MCP-tools on a single HTTP endpoint for the AI agent.

## What the manager provides itself

| Capability | Source |
|---|---|
| One built-in tool — `session_list` (read-only) | manager |
| Showcase of proxied tools from connected clients | 1С extensions |
| Routing a call to the correct session by `session_id` | manager |
| Soft-reconnect of a client by `client_uid` | manager |
| FIFO order of calls into one session | manager |

Everything else (domain tools - form descriptions, test runs, navigation, etc.) is added by **1С extensions**, not by the manager. There is a separate skill for each extension.

## Boundaries

The manager does **not**:
- launch 1С clients (that is the job of an external orchestrator, typically `v8-runner`);
- store state across restarts (registry is in-memory);
- contain business logic (transport + routing only);
- manage the infobase.

## Task Routing

| Task | Reference |
|---|---|
| What each layer of the stack does (addin → devkit → BSL → manager → AI) | `references/architecture.md` |
| Bring up the manager, connect a 1С client | `references/bootstrap.md` |
| Read `session_list`, call a tool, understand why it is missing | `references/sessions-and-tools.md` |
| Add a new tool to a 1С extension | `references/extending-tools.md` |
| Manager does not start / client is not visible / tool is hidden / call fails | `references/troubleshooting.md` |

## Guardrails (hard)

1. **Do not edit the manager source code** (`src/`, `Cargo.toml`, `systemd/`, `etc/`, `spec/`, ADR in `docs/decisions/`) - this is the upstream repository. All manager-level changes are coordinated as a separate task.
2. **Do not create or modify MCP-tools without the user's explicit permission.** Tools live in 1С extensions (`exts/<extension>/`); editing/adding them means changing the public contract.
3. **Do not pull business logic into the manager.** If a task requires "the manager should do X", that is a signal that X belongs either in the extension or in the launch orchestrator.
4. **Do not try to start a 1С client through the manager.** The manager only accepts an incoming WS connection. Starting 1С is the responsibility of `v8-runner`.
5. **Ask the user before building/restarting the client.** Any operation that changes project state (build, restart) requires confirmation.
