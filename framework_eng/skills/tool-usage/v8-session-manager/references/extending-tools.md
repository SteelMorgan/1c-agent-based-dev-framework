# Developing a new MCP tool

> **Hard guardrail.** Creating, changing, or deleting an MCP tool is a change to the extension's public contract. It is forbidden to do this **without direct user permission**. Without an explicit "yes, add tool X to extension Y" - stop and ask.

## Where tools live

Tools are registered by 1С application extensions (`exts/<extension>/`), not by the manager itself. Each extension publishes its own set:

| Extension | What it usually publishes |
|---|---|
| `client_mcp` | Base skeleton, usually has no domain-specific tools of its own |
| `test_client` | Form descriptions, element reading, simple UI scenarios |
| `yaxunit_runner` | Running unit tests |
| `vanessa_test_client` | Managing Vanessa scenarios |
| Any new project extension | According to the domain task |

If there is any doubt about which extension a tool should live in, **ask the user**; do not place it yourself.

## Tool addition algorithm (after obtaining permission)

1. **Clarify the contract.** Tool name, input schema, output fields, side effects, expected behavior on error. Record it in the task (spec/ADR) if the task is nontrivial.
2. **Choose the extension.** A tool belongs to one `kind`. If the business logic is split across extensions, discuss it with the user.
3. **Implement BSL.** Register the tool through the `client_mcp` mechanism (see the skill for the corresponding extension; for `client_mcp` - a separate skill, if available). It will be published on the storefront as `<kind>__<tool_name>`.
4. **Build the project.** Through the relevant skill: `yaxunit-runner__build_project` for DRIVE or `v8-runner` for other projects. There is **no need** to rebuild the manager.
5. **Start the infobase in update mode** if metadata objects changed (see the project's `CLAUDE.md`).
6. **Restart the 1C client.** The manager will pick up the new tools itself on `session.register` / `tools/publish`.
7. **Check registration:**
   - `tools/call session_list` → the client with the required `kind` is present, and the new name appeared in its `tools` field.
   - `tools/list` → `<kind>__<new_tool>` is present on the storefront.
   - A test call to `tools/call <kind>__<new_tool>` with the minimum set of arguments returns the expected result.

## What NOT to touch

- **Manager source files** (`src/`, `Cargo.toml`, `build.rs`). Extending the manager is a separate task in the upstream repository.
- **The `session_y8` transport.** The add-in does not depend on specific tools.
- **The storefront name is set manually.** The `<kind>__` prefix is set by the manager; define it through the client's `kind`, not by renaming.
- **Existing tools of other extensions without separate permission.** Even cosmetic changes to the name/schema are a change to the public contract.

## Signs the tool was built correctly

- It appears in `tools/list` immediately after client registration, without restarting the manager.
- `inflight` in `session_list` increases correctly for the duration of the call and resets afterward.
- When two clients of the same `kind` with this tool are connected simultaneously, there is a single entry on the storefront, and the call with `session_id` goes to the correct session (see `sessions-and-tools.md`).
- When the client's WS connection is interrupted, the tool disappears; after reconnect, it returns without loss.

If something is not like this, see `troubleshooting.md`.
