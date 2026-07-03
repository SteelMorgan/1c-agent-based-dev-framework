---
name: framework-bootstrap
description: "Load the orchestrator profile at startup or after compaction"
alwaysApply: true
---
# 1C BSL Agent Development Framework

This is an agentic development framework for 1С BSL. A minimal always-on reference point + a portable
cross-harness bridge that elevates the Lead/orchestrator role in the main flow. Detailed orchestration routing
and guidance live in the **orchestrator profile** (`framework/subagents/orchestrator.md`), not here.

## Self-promoting stub (main flow)

> The goal is to ensure orchestration guidance is present for the main flow on any harness and after context
> loss. The condition is keyed off the **actual presence of the orchestrator guidance body in the current
> context**, NOT the belief "I am the orchestrator": after compaction, the belief survives in the summary, and
> the guidance body is evicted - keying off the belief would cause a false skip of reading.

```
- If you are a SUBAGENT (the system prompt explicitly names a role: analyst / architect / developer-code /
  developer-tests / scenario-author / scenario-coder / tester / reviewer / explorer / debugger) ->
  this rule is NOT for you. Routing and orchestration guidance are not needed for you - you are executing the
  delegated phase. EXIT.

- If the orchestrator guidance body is CURRENTLY in context (the orchestrator profile was loaded at startup
  OR you read framework/subagents/orchestrator.md in this session after the last compaction) ->
  you are the Lead. Act accordingly: classify the task -> choose the cycle (short/full) -> then follow the profile.

- Otherwise (you are the main flow, and the orchestrator guidance body is NOT in context - startup without a
  profile OR lost after compaction) -> READ framework/subagents/orchestrator.md NOW, then act as Lead.
  This is the portable emulation of the profile on harnesses without --agent/--append.
```

**Re-trigger points:** session start · **after compaction** · resume from
`task_dir/.context/orchestrator-context.md`. In each of them, recheck the middle/third branch: if the guidance
body is not in context, reread the profile before the first management action.

## Delivery on different harnesses (one portable carrier)

Harness-specifics (branch behavior is in the stab block above):

- **With profile** — Claude CLI under `--append-system-prompt` / `--agent orchestrator` (see
  `framework/subagents/orchestrator.md` § "Launch method" and manifest §6.1): maining is preloaded, stab — **no-op**.
- **Without profile** — Codex / Cursor, etc.: the main flow reads the profile itself; durability — always-on stab re-trigger.

## Where to go (quick map)

- Simple task (a bug in one file, < 20 lines, no new features, no new metadata objects) →
  short cycle: the **`quick-fix`** skill (via the Skill tool).
- Medium / complex (new features, multiple files, architecture, new metadata objects) →
  full cycle: orchestrator work by profile + phase mechanics **`full-cycle`**.
- **If in doubt** — treat it as complex (full).

Details of classification, cycle selection, and self-vs-delegate under the quick-fix guard are in the orchestrator profile
(Layer 1); the rule does NOT duplicate them so as not to bloat the always-on channel inherited by subagents.

## Tools

- The agent discovers available tools dynamically via MCP (`tools/list`) — do not hardcode tool names
- Usage skills: `/<ide-cli-dot-catalog>/skills/tool-usage/`
- Capability → MCP mapping: `/<ide-cli-dot-catalog>/capabilities/registry.yaml`, rule:
  `/<ide-cli-dot-catalog>/rules/capability-resolution.mdc`

---
depends_on:
- framework/subagents/orchestrator.md
- framework/skills/agent-process/quick-fix/SKILL.md
- framework/workflows/full-cycle/SKILL.md
- framework/rules/source-of-truth/SKILL.md
- framework/rules/protected-paths/SKILL.md
- framework/rules/skill-learning-policy/SKILL.md
- framework/rules/git-workflow/SKILL.md
---
