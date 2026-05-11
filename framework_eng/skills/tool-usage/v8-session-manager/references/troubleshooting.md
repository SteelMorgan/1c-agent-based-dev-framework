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

**Symptom:** `session_list` shows the client, but `<kind>__<expected_tool>` is missing from the showcase.

**Check:**
- The `tools` field in the `session_list` record is empty → the client did not send `tools/publish`. This is the 1С extension's responsibility, not the manager's.
- The `tools` field contains the name, but it is not on the showcase → check the manager log for `proxy_tool_hidden{reason:"schema_conflict"}` (see case 4).
- On the 1С side, the extension responsible for this tool is not loaded or its registration fails silently — check the extension log.

**Solution:** verify that the required extension exists in the project and publishes the tool; check whether `kind` is specified correctly when starting the client.

## 4. The tool is hidden from the showcase (`schema_conflict` warning)

**Symptom:** the expected `<kind>__<tool>` is missing. In the manager log: `proxy_tool_hidden{reason:"schema_conflict"}`.

**Cause:** two clients of the same `kind` registered a tool with the same name but **different** `input_schema`. Usually this is a version mismatch between extensions on different clients.

**Check:** `session_list` shows 2+ sessions of one `kind`; their `generation` or the contents of `tools` differ.

**Solution:**
- Bring the extension to one version on all clients and reconnect.
- If the mismatch is intentional, call the tool through the low-level `session.call(session_id, tool_name, args)`.

## 5. `tools/call` fails with `no active sessions` or `session_id required`

**Symptom A — `no active sessions for proxy tool '<kind>__<tool>'`:** no session with this tool is currently active.

**Check/solution:** `session_list` is empty for this `kind` → start the client. If the session exists but is `Disconnected`, wait for reconnect or start the client again.

**Symptom B — `session_id required`:** the showcase contains a collapsed tool from 2+ sessions, and without an explicit `session_id` the manager does not know where to send it.

**Check/solution:** take the required `session_id` from `session_list` and add it as a parameter in `arguments`. See `sessions-and-tools.md` § "collapsing".

---

If the symptom is not covered, collect it into a task: manager log for the interval, a `session_list` snapshot, `:9100/metrics` metrics, extension versions in the project — and escalate.
