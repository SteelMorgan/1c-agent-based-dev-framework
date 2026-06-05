---
name: capability-resolution
description: Resolve capability → implementation (MCP tool or CLI). The agent uses registry.yaml for invocation.
alwaysApply: true
---

# Capability Resolution

## Model

- Capability is a stable contract through which skills reference a capability ("what needs to be done").
- The capability implementation is fixed in `framework/capabilities/registry.yaml`:
  - `kind: mcp` — MCP server + tool;
  - `kind: cli` — CLI command.
- The capability ↔ skill relationship is bidirectional:
  - the application skill in frontmatter specifies `uses_capabilities: [<name>, …]`;
  - the implementing skill in frontmatter specifies `provides_capabilities: [<name>, …]`.

## Invocation Rule

1. Open the skill — its frontmatter lists capabilities through `uses_capabilities`.
2. For each capability, find the entry in `framework/capabilities/registry.yaml`.
3. Based on the `kind` field, choose the invocation method:
   - **`kind: mcp`** — take `server` + `tool`; get the argument schema from `tools/list` of the corresponding MCP server (IDE/Claude Code tool list).
   - **`kind: cli`** — open the SKILL.md specified in `skill`, and take the exact command and argument syntax from there; the template in `command` is only a hint for the subcommand.
4. If the capability is missing from the registry — inform the user and do not substitute a "similar" tool.
5. If the MCP server is unavailable (not in the tool list) or the CLI binary is not found — inform the user; do not attempt workarounds.

## v8-session-manager: runtime storefront

Some of the tools in v8-session-manager (`session_list`) are built-in and always available as long as the manager is running. The remaining tools are proxied from connected 1С clients and appear on the storefront only when a client with the required extension is connected to the manager via WS. If a capability with `server: v8-session-manager` is not available in `tools/list`, that means the corresponding client is not connected. Bringing up the client is the task of `v8-runner` (see SKILL.md in `framework/skills/tool-usage/v8-runner/`).

> Replacing a capability implementation (via `tools/capability-registry.py` or direct editing of `registry.yaml`) is a procedural how-to; see the documentation for `tools/capability-registry.py`.

---
depends_on:
  - framework/capabilities/registry.yaml
---
