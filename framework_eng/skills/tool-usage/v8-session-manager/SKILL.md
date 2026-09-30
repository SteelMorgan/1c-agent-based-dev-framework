---
name: v8-session-manager
description: "Session manager 1C: запуск, clients, session_list, MCP"
provides_capabilities:
  # Built-in manager tools — always available while the manager is up.
  - session_list
  - tools_cache_reset
  # Tools proxied by the manager from connected 1C clients.
  # WARNING: their names in tools/list are read from the persistent tools-cache
  # (ADR-0035) — the presence of a name does NOT guarantee that a call is available.
  # Without a live session of the required kind, the call will return an MCP tool error
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

## Agent Work Interface

For the agent, `v8-session-manager` is an MCP endpoint, a tools showcase, and a call router into live 1C sessions. The agent workflow is:

1. Find available tools through MCP discovery in the current harness (connected MCP server). Direct HTTP/JSON-RPC requests to the manager's endpoint (`curl`, etc.) are not a way to work; see Guardrail 6.
2. Call the discovered capabilities as MCP tools.
3. For proxied tools, first prove that there is a live 1C session of the required `kind`.

The `v8sm` binary (alias `v8-session-manager`) is not a working way to call tools. It is needed only for diagnostics or manager process control: check the binary/help/version, inspect launch parameters, confirm listeners, start the manager in an explicitly agreed scenario. The manager usually starts automatically together with the container; before launching it manually, first verify that there is no live MCP endpoint, HTTP listener, or `v8-session-manager` process.

## What the manager itself provides

| Capability | Source |
|---|---|
| Built-in tool — `session_list` (read-only snapshot of the registry) | manager |
| Built-in tool — `tools_cache_reset` (full reset or by `config_id`) | manager (ADR-0035) |
| Showcase of proxied tools from connected clients | 1С extensions |
| Persistent showcase cache (`workPath/tools_cache.json`, TTL 5d) | manager (ADR-0035) |
| Routing a call to the appropriate session by `session_id` | manager |
| Soft-reconnect of the client by `client_uid` | manager |
| FIFO order of calls within one session | manager |

Everything else (domain tools — form descriptions, running tests, navigation, etc.) is added by **1С extensions**, not by the manager. One skill per extension.

## Live availability protocol

`tools/list` shows the capabilities showcase, but does not prove that a proxied tool can be called right now. Before calling a tool handled by a 1С client:

1. Get a snapshot of the session registry through the manager's built-in MCP tool.
2. Find a live session for the required infobase and `kind`: `state=active`, `disconnected_secs_ago=null`, matching `infobase_name`.
3. Check `inflight=0` before a long scenario or an interactive UI operation.
4. If there are multiple live sessions, pass `session_id` in the proxied tool call.
5. If the tool is in the showcase but there is no live session, start/connect the required 1С client through the profile launch orchestrator (usually `v8-runner`), not through the manager.

After issuing the command to start the 1С client, do not treat the absence of a tool as an immediate error. The client usually does not come up immediately: waiting 10-20 seconds before the first check is a normal working interval. After a live session appears, tool registration by the extension can take up to 120 seconds; periodically check the registry/showcase and stop waiting early as soon as the needed tools appear.

If `session_list` already shows a live session with published tools, do not use `tools_cache_reset` to "refresh the list". This is a mutating operation on the persistent showcase, not a refresh of the current client. If callable tools do not appear in the harness after client registration, first check `tools/list` through the manager's HTTP MCP endpoint and call the tool with ordinary `tools/call`; if necessary, wait for the discovery update of the current environment.

## Proxied tools cache (ADR-0035) - key point

`tools/list` for the manager is read from a **persistent disk cache**, not only from live WS sessions. Implications for the agent:

- **Tool name in `tools/list` ≠ call availability.** The cache survives client disconnect and manager restart - the name stays on the storefront, but a call without a live session returns MCP tool error `isError:true, _meta.error_code="no_live_session"`. This is not a bug, it's the contract.
- **Why this way:** some MCP harnesses (in particular Claude Code) react inconsistently to `notifications/tools/list_changed`. The persistent cache removes dependence on stable notification handling.
- **When `tools_cache_reset` is needed:** when a tool has been deliberately removed from the extension and will not return (or the configuration has been removed entirely). Otherwise it will remain until the TTL expires (by default 5 days from the last `session.register`). A full reset takes no arguments; a targeted one is `{"config_id": "<id>"}` (taken from `session_list[*].config_id`).
- **When `tools_cache_reset` is NOT needed:** after a successful 1С client connect/reconnect, when `session_list` already shows a live session and published tools. In this situation, resetting the cache can remove the just-received storefront from the current agent's MCP discovery; to verify readiness, use the live registry and HTTP MCP `tools/list`/`tools/call`.
- **What the cache does NOT do:** it does not start 1С, does not replay the tool response, and does not substitute for a live session. It only stores names and `inputSchema`.

Details - `references/sessions-and-tools.md` § "Persistent cache and `tools_cache_reset`".

## UI MCP session diagnostics

For client UI tools (`open_form`, `click`, `input`, `get_value`, `get_table_rows`, `test_client_start`), first apply the general live-availability protocol above. For standard UI MCP through the platform test client, a control session of the corresponding `kind` is required; for Vanessa, `kind=vanessa_test_client` and VA-tools beyond the base set.

For UI/UX acceptance of 1C forms, the main visual path is described in `va-visual-check`: connecting TestClient, checking PID/profile/windows, and capturing PNG belong there. `tools/list` does not prove the visual chain is ready; the proof is a live session plus the profile-specific smoke from `va-visual-check`.

If a proxied call hangs or `inflight` remains greater than zero:

- for a test-client form, first apply `va-visual-check`; if a fallback is needed, record the VA steps performed, the reason, and the residual risk;
- check `/tmp/mcp-client.log` or the project `client_mcp` log: did `MCP_TOOL_CALL` arrive, did the WS session register, is there any platform-level error;
- do not use `tools_cache_reset` as the first action: the cache does not block live calls and does not fix a hung client;
- if the client was started in the wrong mode, terminate only your saved PID and restart it with the correct command via `v8-runner`.

For the startup chain `1c-client` + `/TESTMANAGER` + a separate `/TESTCLIENT`, see `v8-runner/references/project-workflows.md`, section "UI MCP through the platform test client".

## Boundaries

The manager **does not**:
- start 1C clients (this is the responsibility of the external orchestrator, typically `v8-runner`);
- keep state between restarts (the registry is in-memory);
- contain business logic (transport + routing only);
- manage the information base.

## Task Routing

| Task | Reference |
|---|---|
| What each stack layer does (addin → devkit → BSL → manager → AI) | `references/architecture.md` |
| Start the manager, connect a 1C client | `references/bootstrap.md` |
| Work with the session registry, call a proxied tool, understand why a tool is missing or unavailable | `references/sessions-and-tools.md` |
| Add a new tool to the 1C extension | `references/extending-tools.md` |
| Manager does not start / client is not visible / tool is hidden / call fails | `references/troubleshooting.md` |

## Guardrails (hard)

1. **Do not edit the manager source files** (`src/`, `Cargo.toml`, `systemd/`, `etc/`, `spec/`, ADR in `docs/decisions/`) — this is the upstream repository. All manager-level changes are coordinated in a separate task.
2. **Do not create or modify MCP tools without the user's explicit permission.** Tools live in 1C extensions (`exts/<extension>/`); editing/adding them means changing the public contract.
3. **Do not drag business logic into the manager.** If a task requires "the manager should do X", that is a signal that X belongs either in the extension or in the launch orchestrator.
4. **Do not try to start the 1C client through the manager.** The manager only accepts an incoming WS connection. Starting 1C is `v8-runner`'s job.
5. **Ask the user before building/restarting the client.** Any operation that changes the project state (build, restart) requires confirmation.
6. **Do not bypass the MCP server.** Call the manager's tools ONLY through the harness's MCP connection. Do not call them through any bypass: direct HTTP/JSON-RPC requests to the endpoint (`curl`, scripts), a binary, another client, or a “bare” session. If an MCP tool is available but does not work (server is not connected — `ECONNREFUSED`/`CONNECTION_CLOSED` on startup, call error, gate denial), **do not look for a workaround; inform the user**: what was called, what response was received, and what is needed (for example, reconnect the server via `/mcp`). Most likely, a problem has occurred that needs to be fixed, not bypassed. A bypass call also proves nothing about the standard path: it has a different context (dialog identity, masking, permissions), and its result leads to false conclusions.
