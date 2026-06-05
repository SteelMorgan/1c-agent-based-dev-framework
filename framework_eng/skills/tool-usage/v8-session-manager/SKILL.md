---
name: v8-session-manager
description: "Use for working with the 1С session manager: launching, configuration, connecting clients, reading session_list, calling proxied MCP tools from 1С extensions. Helps with errors «no active sessions» / «session_id required» and connecting a client via `mcpMode=ws`."
provides_capabilities:
  # Built-in manager tools — always available while it is running.
  - session_list
  - tools_cache_reset
  # Tools that the manager proxies from connected 1С clients.
  # WARNING: their names in tools/list are read from the persistent tools-cache
  # (ADR-0035) — having a name does NOT guarantee that the call is available.
  # Without a live session of the required kind the call will return an MCP
  # tool error `isError:true, _meta.error_code="no_live_session"`.
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

A thin MCP aggregator: accepts WS connections from 1С clients and publishes their MCP tools on a single HTTP endpoint for the AI agent.

## What the manager provides itself

| Capability | Source |
|---|---|
| Built-in tool — `session_list` (read-only registry snapshot) | manager |
| Built-in tool — `tools_cache_reset` (full reset or by `config_id`) | manager (ADR-0035) |
| Showcase of proxied tools from connected clients | 1С extensions |
| Persistent showcase cache (`workPath/tools_cache.json`, TTL 5d) | manager (ADR-0035) |
| Routing a call to the correct session by `session_id` | manager |
| Soft reconnect of a client by `client_uid` | manager |
| FIFO order of calls into one session | manager |

Everything else (domain tools — form descriptions, test runs, navigation, etc.) is added by **1С extensions**, not by the manager. There is a separate skill for each extension.

## Proxied tools cache (ADR-0035) — key point

The manager's `tools/list` is read from a **persistent cache on disk**, not only from live WS sessions. Implications for the agent:

- **A tool name in `tools/list` ≠ a successful call.** The cache survives client disconnect and manager restart — the name stays on the showcase, but a call without a live session returns an MCP tool error `isError:true, _meta.error_code="no_live_session"`. This is not a bug, it is the contract.
- **Why it is done this way:** some MCP harnesses (in particular Claude Code) respond unreliably to `notifications/tools/list_changed`. The persistent cache removes the dependency on stable notification handling.
- **When `tools_cache_reset` is needed:** when a tool has been intentionally removed from the extension and will not come back (or the configuration has been removed completely). Otherwise it will linger until the TTL expires (by default 5 days from the last `session.register`). Full reset — without arguments; targeted — `{"config_id": "<id>"}` (taken from `session_list[*].config_id`).
- **What the cache does NOT do:** it does not start 1С, does not reproduce the tool response, does not replace a live session. It only stores names and `inputSchema`.

Details — `references/sessions-and-tools.md` § «Persistent cache and `tools_cache_reset`».

## Boundaries

The manager does **not**:
- launch 1С clients (that is the job of an external orchestrator, typically `v8-runner`);
- store state across restarts (the registry is in-memory);
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

1. **Do not edit the manager source code** (`src/`, `Cargo.toml`, `systemd/`, `etc/`, `spec/`, ADR in `docs/decisions/`) — this is the upstream repository. All manager-level changes are coordinated in a separate task.
2. **Do not create or modify MCP tools without the user's direct permission.** Tools live in 1С extensions (`exts/<extension>/`); editing/adding them means changing the public contract.
3. **Do not pull business logic into the manager.** If a task requires "the manager should do X", that is a signal that X belongs either in the extension or in the launch orchestrator.
4. **Do not try to start a 1С client through the manager.** The manager only accepts an incoming WS connection. Starting 1С is the responsibility of `v8-runner`.
5. **Ask the user before building/restarting the client.** Any operation that changes project state (build, restart) requires confirmation.
