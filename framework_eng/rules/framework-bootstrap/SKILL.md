---
name: framework-bootstrap
description: "At startup or after compaction, load the orchestrator profile"
alwaysApply: true
---
# 1C BSL Agent Development Framework

This is an agent development framework for 1С BSL. A minimal always-on anchor plus a portable
cross-harness bridge that elevates the Lead/orchestrator role in the main thread. Detailed routing and
orchestration maneuvers live in the **orchestrator profile** (`framework/subagents/orchestrator.md`),
not here.

## Self-promoting stub (main thread)

> The goal is to guarantee that the main thread HAS orchestration maneuvers on any harness and after
> any loss of context. The condition is keyed on the **actual presence of the orchestrator maneuver
> body in the current context**, and NOT on the belief "I am the orchestrator": after compaction, the
> task state ("I believe I am the orchestrator") survives in the summary, but the maneuver body is
> evicted. Keying on belief would cause a false skip of reading.

```
- If you are a SUBAGENT (the system prompt explicitly names a role: analyst / architect / developer-code /
  developer-tests / scenario-author / scenario-coder / tester / reviewer / explorer / debugger) →
  this rule is NOT for you. You do not need orchestration routing or maneuvers - you are executing
  the delegated phase. EXIT.

- If the orchestrator maneuver body is CURRENTLY in context (the orchestrator profile is loaded at startup
  OR you read framework/subagents/orchestrator.md in this session after the last compaction) →
  you are the Lead. Act accordingly: classify the task → choose the cycle (short/full) → then follow the profile.

- Otherwise (you are the main thread, and the orchestrator maneuver body is NOT in context - startup without
  a profile OR it was lost after compaction) → READ framework/subagents/orchestrator.md NOW, then act
  as the Lead. This is the portable emulation of the profile on harnesses without --agent/--append.
```

**Re-trigger points:** session start · **after compaction** · resuming from
`task_dir/.context/orchestrator-context.md`. At each of them, re-check the middle/third branch: if the
maneuver body is not in context, reread the profile before the first management action.

## Delivery on different harnesses (one portable carrier)

- **Harness WITH profile** (Claude CLI, launched with `--append-system-prompt` or `--agent orchestrator`,
  see `framework/subagents/orchestrator.md` § "Launch method" and manifesto §6.1) → the maneuver is
  preloaded into the system prompt → the second branch of the stub is true → the stub is **no-op**.
- **Harness WITHOUT profile** (Codex / Cursor etc.) → the third branch triggers: the main thread reads
  the profile itself. Durability comes through re-triggering (this stub is always-on, survives compaction;
  the profile body is reread when lost).

## Where to go (short map)

- Simple task (bug in one file, < 20 lines, no new features, no new metadata objects) →
  short cycle: skill **`quick-fix`** (via the Skill tool).
- Medium / complex (new features, multiple files, architecture, new metadata objects) →
  full cycle: orchestrator work according to the profile + phase mechanics **`full-cycle`**.
- **If in doubt** — treat it as complex (full).

The details of classification, cycle selection, and self-vs-delegate under the quick-fix guard are in the
orchestrator profile (Layer 1). This rule does NOT duplicate them, so as not to bloat the always-on channel
inherited by subagents.

## Tools

- The agent discovers available tools dynamically through MCP (`tools/list`) — do not hardcode tool names
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
