# Surface diagnostics

Top 5 typical failures. Deep scenarios (WS frame analysis, `schema_hash` conflicts at the implementation level, metrics) are in `docs/architecture/` and `docs/decisions/`; if needed, bring them in as a separate task.

Where to always look:
- manager log (stdout / journalctl, `--log-level info` level);
- `tools/call session_list` — registry state;
- Prometheus `:9100/metrics` (if metrics are enabled) — `mcp_session_register`, `mcp_session_call`, `mcp_session_queue_depth`.

## 1. The manager does not start

**Symptom:** `cargo run` / the binary crashes immediately.

**Check:**
- In the log, `Address already in use` → port `:4000` or `:4001` is occupied. `ss -lntp | grep -E '4000|4001'` — find the holder.
- `workPath does not exist` / `permission denied` → the directory from `v8project.yaml` was not created or there are no permissions. Create it manually, set the owner.
- `failed to parse config` → YAML syntax.

**Solution:** free the port (or change `bind_address` in the config), create the directory, fix the YAML.

## 2. The manager is running, but the client does not appear in `session_list`

**Symptom:** the 1С client started, but `session_list` is empty or does not contain its record.

**Check:**
- There is no `accepting WebSocket connection` entry in the manager log → the client did not reach WS. Reasons: incorrect `manager_url`, unavailable network, `mcpMode` is not `ws`.
- There is an `accepting WebSocket connection` entry in the manager log, but no `session.register` → the handshake did not reach registration. Check the `client_mcp` extension log on the 1С side: the problem is in the `session_y8` addin or devkit.
- The log contains `register rejected: client_uid collision (Active)` → another active session is already using this `client_uid`. Change the uid or wait for it to close.

**Solution:** check the client's `/C` launch parameters (`bootstrap.md`), and the transport layer level.

## 3. The client is in `session_list`, but the tool did not appear in `tools/list`

**Symptom:** `session_list` shows the client, but `<kind>__<expected_tool>` is missing from the storefront.

**Check:**
- The `tools` field in the `session_list` record is empty → the client did not send `tools/publish`. This is the 1С extension's responsibility, not the manager's.
- The `tools` field contains the name, but it is not on the storefront → check the manager log for `proxy_tool_hidden{reason:"schema_conflict"}` (see case 4).
- On the 1С side, the extension responsible for this tool is not loaded or its registration fails silently — check the extension log.

**Solution:** verify that the required extension exists in the project and publishes the tool; check whether `kind` is specified correctly when starting the client.

## 4. The tool is hidden from the storefront (`schema_conflict` warning)

**Symptom:** the expected `<kind>__<tool>` is missing. In the manager log: `proxy_tool_hidden{reason:"schema_conflict"}`.

**Cause:** two clients of the same `kind` registered a tool with the same name but **different** `input_schema`. Usually this is a version mismatch between extensions on different clients.

**Check:** `session_list` shows 2+ sessions of one `kind`; their `generation` or the contents of `tools` differ.

**Solution:**
- Bring the extension to one version on all clients and reconnect.
- If the mismatch is intentional, call the tool through the low-level `session.call(session_id, tool_name, args)`.

## 5. `tools/call` returned `no_live_session` (ADR-0035) or `session_id required`

**Symptom A — `isError:true`, `_meta.error_code="no_live_session"`** in the `tools/call` response:

```json
{ "isError": true, "_meta": { "error_code": "no_live_session", "kind": "...", "tool": "..." }, "content": [...] }
```

This is **not** the JSON-RPC `method not found` (`-32601`) error — the tool is on the storefront (read from the persistent tools-cache, ADR-0035), but no live session of this `kind` is connected.

**Check/solution:**
- `session_list` is empty for this `kind` → bring up the client via the orchestrator (`v8-runner`).
- The session exists but `state="disconnected"` → wait for reconnect or start the client again.
- If the tool will **never** come back (extension retired) — `tools_cache_reset` (see case 6).

**Symptom B — `session_id required`:** the storefront contains a collapsed tool from 2+ sessions, and without an explicit `session_id` the manager does not know where to send it.

**Check/solution:** take the required `session_id` from `session_list` and add it as a parameter in `arguments`. See `sessions-and-tools.md` § "collapsing".

## 6. A tool lingers on the storefront after the extension was removed

**Symptom:** `tools/list` still contains `<kind>__<tool>` that has been removed from the extension and should never appear again. Every call now returns `no_live_session`.

**Reason:** the persistent tools-cache (ADR-0035) keeps a record until `tools_cache.cache_life_period` (default 5d) since the last `session.register`. The cache will not "rot" on its own until TTL passes.

**Solution:**
- Targeted reset: `tools/call tools_cache_reset` with `{"config_id": "<id>"}` — wipes only this configuration's record. `config_id` is taken from `session_list[*].config_id` (or equals `kind` if the BSL side does not send the field).
- Full reset: `tools/call tools_cache_reset` with no arguments — all entries removed, `${workPath}/tools_cache.json` rewritten to `{version:1, entries:[]}`, manager sends `notifications/tools/list_changed`.
- Alternative: wait for TTL (often not acceptable in dev).

**When `tools_cache_reset` is NOT needed:** client reconnect (replace semantics handle it), `inputSchema` change (next `register` rewrites), "has not been used for a while" (TTL handles it).

## 7. After a manager restart `tools/list` is "correct", but `tools/call` immediately returns `no_live_session`

**Symptom:** manager has been restarted, `tools/list` is full (names of tools from all configurations are present), but every call returns `no_live_session`.

**Reason:** this is **normal behavior by ADR-0035** — the on-disk cache survived the restart, but the WS sessions of the 1С clients are gone. The manager honestly reports "I know the name, no one to serve".

**Solution:** bring up the required client via `v8-runner`. The cache will automatically synchronize on the first `session.register`.

This is the main motivation for the persistent cache — a stable `tools/list` surface for MCP harnesses (Claude Code in particular) that handle `notifications/tools/list_changed` unreliably.

---

If the symptom is not covered, collect it into a task: manager log for the interval, a `session_list` snapshot, contents of `${workPath}/tools_cache.json`, `:9100/metrics` metrics, extension versions in the project — and escalate.
