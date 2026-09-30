# Sessions and tools

## Core principle: storefront = extension function + persistent cache

`tools/list` of the manager is assembled from two sources:

1. **Built-in manager tools** - `session_list` and `tools_cache_reset` (ADR-0035). Available at all times.
2. **Proxied tools** - from `tools_cache.json` (per-`(kind, config_id)`, default TTL 5 days), which is updated on every `session.register` / `session.tools_changed` from the connected 1C client.

Consequences:
- **Tool name in `tools/list` ≠ call availability.** After client disconnect or manager restart, the name remains on the storefront from the cache, but a call without a live session of the required `kind` will return MCP tool error `isError:true, _meta.error_code="no_live_session"` (do not confuse with JSON-RPC `method not found`).
- **Extension first, registration second.** For a tool to enter the cache for the first time, the extension must be in the project and successfully connect to the manager over WS at least once (see `extending-tools.md`).
- **TTL eviction.** An entry with `now - last_seen_at > cache_life_period` is removed lazily on the next `tools/list` or `session.register`. Default is `5d`, configurable via `tools_cache.cache_life_period` (see `bootstrap.md`).
- **Client startup is not the manager's job.** If you need a real tool call, the client with that `kind` must be brought up by the orchestrator (typically `v8-runner`), not the manager.

## `session_list`

One of the two built-in tools. Read-only snapshot of the session registry.

Record fields:
- `session_id` - session identifier (equals the client's `client_uid` if there are no collisions). Stable during soft-reconnect.
- `kind` - client namespace (from launch parameters).
- `host_id`, `pid` - 1C machine and process.
- `infobase_name` - infobase name. Server-side - `Ref=` from the connection string (for example `dssl_drive_ai`); file-based - the last component of the path from `File=`; otherwise `"unknown"`. Distinguishes sessions of different infobases with identical tool names.
- `ib_session_number` - 1C session number in the cluster (`НомерСеансаИнформационнойБазы()`, u32). Changes when the 1C process restarts. Needed to correlate the record with the session in the 1C cluster (RAC, Control Center). Do not confuse with the manager's `session_id`.
- `last_call_at` - timestamp of the last call to this session.
- `inflight` - how many calls are queued right now.
- `generation` - counter for rebuilding the tool set (grows on `tools/list_changed`).
- `tools` - names of the tools published by this session (without the prefix).
- `config_id` - identifier of a specific configuration/extension layout on the 1C side (ADR-0035). One manager serves multiple configurations; the cache is maintained per pair `(kind, config_id)`. If the client did not pass the field in `session.register`, the manager substitutes `config_id = kind`. The value is needed for targeted `tools_cache_reset`.

Usage:
- Find out which clients are alive and under which `session_id`.
- Before calling a proxied tool, choose the target session (by `kind`, `host_id`, `pid`, or task context).
- Diagnostics: a long `inflight` or an old `last_call_at` are a reason to check `troubleshooting.md`.

## Tool name on the storefront

The published name is `<kind>__<tool_name>`, the separator is a double underscore. Examples:

- client `kind=test_client` published `describe_form` -> storefront: `test_client__describe_form`.
- client `kind=yaxunit_runner` published `run_module_tests` -> storefront: `yaxunit_runner__run_module_tests`.

This is a technical namespace - it guarantees that tools from different extensions do not collide by name. Addressing a specific session when there are multiple is a separate mechanism (see below).

## Collapsing same-type tools and the `session_id` parameter

The manager collapses registrations of the same type. A group is defined as `(kind, tool_name, schema_hash)`:

| Case | What is shown on the storefront | How to call |
|---|---|---|
| 1 session publishes a tool | `<kind>__<tool>` (one entry) | `session_id` is optional; the manager will inject the only candidate |
| 2+ sessions with an **identical** schema | `<kind>__<tool>` (one entry) | `session_id` is **required** - take it from `session_list` and place it in the arguments |
| 2+ sessions with a **different** schema | tool is **hidden** + warning `proxy_tool_hidden{reason:"schema_conflict"}` | Only through low-level `session.call(session_id, tool_name, arguments)` |

The manager injects the `session_id` property into the `input_schema` of the published tool. When there are multiple candidates, it is added to `required`. Before sending the call to the client, the manager removes `session_id` from the arguments.

Agent flow:
1. `tools/call session_list` - get the list of sessions.
2. Choose the target `session_id` by `kind`/context.
3. `tools/call <kind>__<tool>` with `session_id` in the arguments (or without it, if there is only one candidate).
4. If you get the `session_id required` error - add the parameter; if `no active sessions` - the client is not connected, go to `troubleshooting.md`.

## Session lifecycle

- **FIFO within a single session.** All calls to one `session_id` are executed strictly in order. There is no parallelism within a session.
- **Soft-reconnect.** When the WS connection drops, the record is marked `Disconnected` and kept for `reconnection_grace_secs` (default 30 s). A client reconnecting with the same `client_uid` will get the same `session_id`, the same tool set, and the preserved queue. After the grace timeout, the record is removed.
- **Idle-killing.** A session without calls for longer than `idle_timeout_secs` (default 1800 s) is removed by timer.
- **Round-robin.** With multiple equivalent candidates and a call **without** `session_id`, the manager distributes requests round-robin (per-group counter).

## Persistent cache and `tools_cache_reset` (ADR-0035)

The manager maintains a persistent cache of proxied tools in `${workPath}/tools_cache.json`. The key is the pair `(kind, config_id)`; the value is the list of `ToolSpec` copied verbatim from `session.register.tools`. The update trigger is receipt of `session.register` or `session.tools_changed`. The `last_seen_at` entry is updated on every register; `tools/call` does not bump the entry (otherwise the cache would be "alive forever").

**When it is read:**

- `tools/list` of the manager = `session_list` + `tools_cache_reset` + merge of all cache entries (lazy eviction by TTL).
- `tools/call <kind>__<tool>` - the manager first looks for a live session through `SessionDispatcher`; if not found, it returns `no_live_session` (see below).

**`no_live_session` - what the agent sees.** Response in MCP tool call format (NOT a JSON-RPC error):

```json
{
  "content": [{
    "type": "text",
    "text": "Tool '<kind>__<tool>' is currently unavailable: no live session of kind=<kind>."
  }],
  "isError": true,
  "_meta": {
    "error_code": "no_live_session",
    "kind": "<kind>",
    "tool": "<tool>"
  }
}
```

This does **not** mean that the tool "does not exist" - JSON-RPC `method not found` has its own path. `no_live_session` means: "I know you from the cache, but nobody is serving it right now."

**When to call `tools_cache_reset`:**

1. **The tool has been intentionally removed from the extension** and will not appear again. Without reset, it will linger until the TTL expires (`tools_cache.cache_life_period`, default 5 days). Targeted reset: `{"config_id": "<id>"}` - it will wipe only the entry for this configuration, while the other `kind`/`config_id`s will remain.
2. **The configuration has been removed entirely / `kind` is no longer served** - `tools_cache_reset` without arguments (full reset). All entries are deleted, the file is atomically rewritten to `{version:1, entries:[]}`, and `notifications/tools/list_changed` is sent.
3. **There is a suspicion that the cache is stale** relative to the current extension set (for example, after a manual edit of the extension outside the normal deployment). Reset + the first register from a new client will restore the correct set.

**When `tools_cache_reset` is NOT needed:**

- During a normal client reconnect - replace semantics kick in automatically.
- When a tool's `inputSchema` changes - the next register will rewrite the entry.
- Simply because it "has not been used for a long time" - TTL handles that on its own.

**Live sessions are NOT touched by reset.** If a client is connected, its `register` after reset will overwrite the record in the cache. This is used in the "hard cleanup" scenario for controlled storefront recreation.

**The `tools_cache:` config section and storage path** - see `bootstrap.md` § "Persistent tools-cache".
