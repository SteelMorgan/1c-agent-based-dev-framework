---
name: capability-resolution
description: "When selecting a tool, first map the capability"
alwaysApply: true
---

# Capability Resolution

## Model

- Capability is a stable contract by which skills refer to a capability ("what needs to be done").
- The capability implementation is fixed in `framework/capabilities/registry.yaml`:
  - `kind: mcp` — MCP server + tool;
  - `kind: cli` — CLI command.
- The capability ↔ skill relationship is bidirectional:
  - an application skill in frontmatter specifies `uses_capabilities: [<name>, …]`;
  - an implementing skill in frontmatter specifies `provides_capabilities: [<name>, …]`.

## Invocation Rule

1. Open the skill — its frontmatter lists capabilities via `uses_capabilities`.
2. For each capability, find the entry in `framework/capabilities/registry.yaml`.
3. Use the `kind` field to choose the invocation method:
   - **`kind: mcp`** — take `server` + `tool`, and get the argument schema from `tools/list` of the corresponding MCP server (IDE/Claude Code tool list).
   - **`kind: cli`** — open the SKILL.md specified in `skill`, and from there take the exact command and argument syntax; the template in `command` is only a hint for the subcommand.
4. If the capability is missing from the registry, tell the user and do not substitute a similar tool.
5. If the MCP server is unavailable (not in the tool list) or the CLI binary is not found, tell the user; do not try workarounds.

## v8-session-manager: Runtime Showcase

Part of the v8-session-manager tools (`session_list`) are built in and always available while the manager is running. The remaining tools are proxied from connected 1C clients and appear in the showcase only when a client with the required extension is connected to the manager via WS. If a capability with `server: v8-session-manager` is not available in `tools/list`, that means the corresponding client is not connected. Bringing up the client is the responsibility of `v8-runner` (see SKILL.md in `framework/skills/tool-usage/v8-runner/`).

> Replacing a capability implementation (through `tools/capability-registry.py` or by editing `registry.yaml` directly) is a procedural how-to; see the documentation for the `tools/capability-registry.py` tool.

---
depends_on:
  - framework/capabilities/registry.yaml
---
