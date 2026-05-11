# Sessions and tools

## Main principle: the storefront is an extension feature, not the manager's

The manager's `tools/list` is a dynamic storefront. The manager itself exposes exactly one built-in tool: `session_list`. Everything else is tools that connected 1С clients register through `tools/publish`.

Consequences:
- **No client, no tool.** A tool appears in `tools/list` only when a client with the corresponding `kind` is connected and has published the tool.
- **Client restart → storefront rebuild.** When the WS connection drops, the tool disappears; on reconnect it appears again.
- **No extension in the project — the tool will never appear.** First development in `exts/<extension>/`, then registration (see `extending-tools.md`).
- **Starting the client is not the manager's job.** If the expected tool is missing, check the orchestrator (typically `v8-runner`), not the manager.

## `session_list`

The only built-in tool. Read-only snapshot of the session registry.

Record fields:
- `session_id` — session identifier (equal to the client's `client_uid` if there are no collisions). Stable on soft-reconnect.
- `kind` — client namespace (from launch parameters).
- `host_id`, `pid` — machine and 1С process.
- `infobase_name` — infobase name. Server-based — `Ref=` from the connection string (for example `dssl_drive_ai`); file-based — the last path component from `File=`; otherwise `"unknown"`. Distinguishes sessions of different infobases when tool names are identical.
- `ib_session_number` — 1С session number in the cluster (`НомерСеансаИнформационнойБазы()`, u32). Changes when the 1С process restarts. Needed to correlate the record with a session in the 1С cluster (RAC, TsUP). Do not confuse it with the manager's `session_id`.
- `last_call_at` — timestamp of the last call to this session.
- `inflight` — how many calls are in the queue right now.
- `generation` — rebuild counter for the tool set (increases on `tools/list_changed`).
- `tools` — names of the tools published by this session (without the prefix).

Usage:
- Understand which clients are alive and under which `session_id`.
- Before calling a proxied tool, choose the target session (by `kind`, `host_id`, `pid`, or task context).
- Diagnostics: a long `inflight` or an old `last_call_at` are reasons to check `troubleshooting.md`.

## Tool name on the storefront

The published name is `<kind>__<tool_name>`, with a double underscore as the separator. Examples:

- client `kind=test_client` published `describe_form` → storefront: `test_client__describe_form`.
- client `kind=yaxunit_runner` published `run_module_tests` → storefront: `yaxunit_runner__run_module_tests`.

This is a technical namespace — it guarantees that tools from different extensions do not clash by name. Addressing a specific session when there are multiple candidates is a separate mechanism (see below).

## Collapsing same-type tools and the `session_id` parameter

The manager collapses registrations of the same type. The group is defined as `(kind, tool_name, schema_hash)`:

| Case | What is on the storefront | How to call |
|---|---|---|
| 1 session publishes a tool | `<kind>__<tool>` (one entry) | `session_id` is optional; the manager will fill in the only candidate |
| 2+ sessions with **identical** schema | `<kind>__<tool>` (one entry) | `session_id` is **required** — take it from `session_list` and put it into the arguments |
| 2+ sessions with **different** schema | tool **hidden** + warning `proxy_tool_hidden{reason:"schema_conflict"}` | Only through low-level `session.call(session_id, tool_name, arguments)` |

The manager injects the `session_id` property into the published tool's `input_schema`. When there are multiple candidates, it is added to `required`. Before sending the call to the client, the manager removes `session_id` from the arguments.

Agent algorithm:
1. `tools/call session_list` — get the session list.
2. Choose the target `session_id` by `kind`/context.
3. `tools/call <kind>__<tool>` with `session_id` in the arguments (or without it, if there is only one candidate).
4. If the `session_id required` error is returned — add the parameter; if `no active sessions` — the client is not connected, go to `troubleshooting.md`.

## Session lifecycle

- **FIFO in one session.** All calls to one `session_id` are executed strictly in order. There is no parallelism within a session.
- **Soft-reconnect.** When the WS connection drops, the record is marked `Disconnected` and kept for `reconnection_grace_secs` (default 30 s). A client that reconnects with the same `client_uid` gets the same `session_id`, the same tool set, and the saved queue. After the grace timeout, the record is deleted.
- **Idle-killing.** A session with no calls for longer than `idle_timeout_secs` (default 1800 s) is deleted by a timer.
- **Round-robin.** When there are several equivalent candidates and the call is made **without** `session_id`, the manager distributes requests round-robin (per-group counter).
