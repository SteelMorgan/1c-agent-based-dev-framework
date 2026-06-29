---
name: v8-session-manager
description: "1C session manager: startup, clients, session_list, MCP"
provides_capabilities:
  # Built-in manager tools — always available while the manager is running.
  - session_list
  - tools_cache_reset
  # Tools proxied by the manager from connected 1C clients.
  # WARNING: their names in tools/list are read from the persistent tools-cache
  # (ADR-0035) — the presence of a name does NOT guarantee that the call is available.
  # Without a live session of the required kind, the call returns MCP tool error
  # `isError:true, _meta.error_code="no_live_session"`.
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

A thin MCP aggregator: accepts WS connections from 1C clients and publishes their MCP tools on a single HTTP endpoint for the AI agent.

## What the manager itself provides

| Capability | Source |
|---|---|
| Built-in tool — `session_list` (read-only snapshot of the registry) | manager |
| Built-in tool — `tools_cache_reset` (full reset or by `config_id`) | manager (ADR-0035) |
| Showcase of proxied tools from connected clients | 1C extensions |
| Persistent showcase cache (`workPath/tools_cache.json`, TTL 5d) | manager (ADR-0035) |
| Routing a call to the right session by `session_id` | manager |
| Soft reconnect of a client by `client_uid` | manager |
| FIFO order of calls to one session | manager |

Everything else (domain tools - form descriptions, test launch, navigation, etc.) is added **by 1C extensions**, not by the manager. Separate skill for each extension.

## Proxied tools cache (ADR-0035) - the key point

`tools/list` of the manager is read from a **persistent cache on disk**, not only from live WS sessions. Consequences for the agent:

- **A tool name in `tools/list` does not equal call availability.** The cache survives client disconnect and manager restart - the name remains on the showcase, but a call without a live session will return MCP tool error `isError:true, _meta.error_code="no_live_session"`. This is not a bug, it is the contract.
- **Why this is done:** some MCP harnesses (in particular Claude Code) react unreliably to `notifications/tools/list_changed`. The persistent cache removes dependence on stable notification handling.
- **When `tools_cache_reset` is needed:** when a tool has been intentionally removed from the extension and will not return anymore (or the configuration has been removed completely). Otherwise it will remain until the TTL expires (by default 5 days from the last `session.register`). Full reset is without arguments; targeted reset is `{"config_id": "<id>"}` (taken from `session_list[*].config_id`).
- **What the cache does NOT do:** it does not start 1C, does not reproduce a tool response, and does not replace the live session. It only stores names and `inputSchema`.

Details - `references/sessions-and-tools.md` § "Persistent cache and `tools_cache_reset`".

## UI MCP session diagnostics

For client UI tools (`open_form`, `click`, `input`, `get_value`, `get_table_rows`, `test_client_start`), first prove that there is a live 1C client, not just a record in the cached showcase.

Minimum order:

1. Call `session_list`.
2. Find a live session of the required infobase: `state=active`, `disconnected_secs_ago=null`, `infobase_name=<required infobase>`.
3. For ordinary UI MCP through the platform test client, you need a control session `kind=1c-client`; for Vanessa, you need `kind=vanessa_test_client` and VA tools beyond the base set.
4. If there are multiple live sessions, always pass `session_id` to every proxied tool call.
5. Before a long UI scenario, check a simple call (`infobase_info`) and `inflight=0`.

For UI/UX acceptance of 1C forms, the main visual path is described in `va-visual-check`. The basic chain through Vanessa/TestClient:

1. Verify through `session_list` that the VA manager is alive: `kind=vanessa_test_client`, `state=active`, `tools` contains VA tools, `inflight=0`.
2. Start or connect the test client through the VA tool `connect_test_client` with the required profile.
3. Check that VA returned a real PID of the test client, not `0`/empty.
4. Get windows through `get_window_list_os`.
5. Take a PNG through `get_window_screenshot_os`; see `va-visual-check` for the Linux/Xvfb recipe for black PNGs and the fallback conditions.
6. Check that the PNG is not empty and not monochrome/black.

`tools/list` does not prove that this chain is ready: the list can come from the persistent cache. Proof is a live session + successful smoke `connect_test_client -> get_window_list_os -> get_window_screenshot_os`.

If a proxied call hangs or `inflight` stays above zero:

- for the test-client form, first apply `va-visual-check`; if a fallback is needed, record the completed VA steps, the reason, and the residual risk;
- check `/tmp/mcp-client.log` or the project log of client_mcp: did `MCP_TOOL_CALL` arrive, was the WS session registered, is there no platform-type error;
- do not reset `tools_cache_reset` as the first action: the cache does not block live calls and does not fix a hung client;
- if the client was started in the wrong mode, terminate only your saved PID and restart it with the correct command through `v8-runner`.

For the startup chain `1c-client` + `/TESTMANAGER` + separate `/TESTCLIENT`, see the `v8-runner` skill, section "UI MCP through the platform test client".

## Boundaries

The manager does **not**:
- start 1C clients (that is the job of an external orchestrator, typically `v8-runner`);
- store state across restarts (registry is in-memory);
- contain business logic (only transport + routing);
- manage an infobase.

## Task routing

| Task | Reference |
|---|---|
| What each layer of the stack does (addin → devkit → BSL → manager → AI) | `references/architecture.md` |
| Start the manager, connect a 1C client | `references/bootstrap.md` |
| Read `session_list`, call a tool, understand why it is missing | `references/sessions-and-tools.md` |
| Add a new tool to a 1C extension | `references/extending-tools.md` |
| The manager does not start / the client is not visible / the tool is hidden / the call fails | `references/troubleshooting.md` |

## Guardrails (hard)

1. **Do not edit the manager sources** (`src/`, `Cargo.toml`, `systemd/`, `etc/`, `spec/`, ADR in `docs/decisions/`) — this is the upstream repository. All manager-level changes are agreed as a separate task.
2. **Do not create or change MCP tools without explicit user permission.** Tools live in 1C extensions (`exts/<extension>/`); editing/adding them means changing the public contract.
3. **Do not pull business logic into the manager.** If a task requires "the manager should do X", that is a signal that X belongs either to an extension or to the launch orchestrator.
4. **Do not try to start a 1C client through the manager.** The manager only accepts an incoming WS connection. Starting 1C is the `v8-runner` task.
5. **Ask the user before building/restarting the client.** Any operation that changes project state (build, restart) requires confirmation.
