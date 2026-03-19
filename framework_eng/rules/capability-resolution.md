---
name: capability-resolution
description: Resolve capability → MCP tool. Agent uses registry.yaml for invocation.
alwaysApply: true
---

# Capability Resolution

## Invocation Rule

1. Skill specifies capability — use `capabilities/registry.yaml` to choose server + tool.
2. Take the invocation schema (arguments) from the IDE tool list.
3. If the capability is missing or the tool is unavailable — inform the user.

## Replacing an MCP tool

CLI: `python tools/capability-registry.py set <capability> --server <server> --tool <tool>`.
