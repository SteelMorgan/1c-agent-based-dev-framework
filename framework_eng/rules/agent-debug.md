---
name: agent-debug
description: "Standard diagnostics (registration log/screenshots) did not reveal the actual behavior -> apply the `agent-debug` skill (critical trigger)"
alwaysApply: true
---
# Debug Messages (Agent Debug)

> **Trigger:** standard diagnostics (registration log, screenshots) did not make it possible to determine the system's actual behavior. When triggered, apply the `agent-debug` skill (`framework/skills/tool-usage/diagnostics/agent-debug/SKILL.md`).

**IMPORTANT:** this is a critical trigger. Do not skip it for "invisible" errors - inserting temporary logging points is often the only way to understand the real execution path. After analyzing the registration log, be sure to remove the logging points.

---
depends_on:
  - agent-debug
---
