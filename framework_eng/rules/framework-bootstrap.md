---
name: framework-bootstrap
description: 1C BSL Agent Development Framework — bootstrap context for all tasks
alwaysApply: true
---
# 1C BSL Agent Development Framework

Agent development framework for 1C BSL. This is the minimal context — load details on demand.

## When to load what

| Situation | What to load |
|----------|---------------|
| Discussion / planning a task | Nothing else — this bootstrap is enough |
| **Simple task** (bug in a single file, < 20 lines, no new features, no new metadata objects) | `/<ide-cli-dot-catalog>/rules/quick-fix.md` |
| **Complex task** (new features, multiple files, architectural decisions, new metadata objects) | `/<ide-cli-dot-catalog>/rules/orchestrator.md` |
| Writing / editing BSL code | `/<ide-cli-dot-catalog>/rules/mandatory-tools.md` + `/<ide-cli-dot-catalog>/skills/bsl-practices/*` as needed |
| Writing specifications | `/<ide-cli-dot-catalog>/skills/spec-writing/spec-standard.md` |
| Code / artifact review | `/<ide-cli-dot-catalog>/rules/cross-review-policy.md` + checklist |

> **If in doubt** — treat it as complex and load `orchestrator.md`.

> **CRITICAL** - tell the user in chat how you classified the task and which path will be loaded next: `orchestrator.md` or `quick-fix.md`.

## Tools

- Agent discovers available tools dynamically via MCP (`tools/list`) — do not hardcode tool names
- Tool usage skills: `/<ide-cli-dot-catalog>/skills/tool-usage/`
- Mapping capability → MCP: `/<ide-cli-dot-catalog>/capabilities/registry.yaml`, rule: `/<ide-cli-dot-catalog>/rules/capability-resolution.mdc`

---
depends_on:
- framework/workflows/quick-fix.md
- framework/workflows/orchestrator.md
- framework/workflows/source-of-truth-policy.md
- framework/rules/protected-paths.mdc
- framework/rules/skill-learning-policy.mdc
---
