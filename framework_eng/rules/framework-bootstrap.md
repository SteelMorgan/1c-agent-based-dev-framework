---
name: framework-bootstrap
description: 1C BSL Agent Development Framework — portable self-promoting stub for the main thread
alwaysApply: true
---
# 1C BSL Agent Development Framework

This is an agentic development framework for 1С BSL. A minimal always-on reference point plus a portable
cross-harness bridge that elevates the Lead/orchestrator role in the main thread. Detailed routing and
orchestration prompting live in the **orchestrator profile** (`framework/subagents/orchestrator.md`), not here.

## Self-Promoting Stub (Main Thread)

> Goal — ensure that the main thread HAS orchestration prompting on any harness and after any loss of
> context. The condition is keyed on the **actual presence of the orchestrator prompt body in the
> current context**, and NOT on the belief “I am the orchestrator”: after compaction, the task state
> (“I believe I am the orchestrator”) survives in the summary, while the prompt body is evicted.
> Keying on belief would produce a false read skip.

```
- If you are a SUBAGENT (the system prompt explicitly names the role: analyst / architect / developer-code /
  developer-tests / scenario-author / scenario-coder / tester / reviewer / explorer / debugger) →
  this rule is NOT for you. Routing and orchestration prompting are not needed for you — you are
  executing the delegated phase. EXIT.

- If the orchestrator prompt body is CURRENTLY in context (the orchestrator profile is loaded at startup
  OR you have read framework/subagents/orchestrator.md in this session after the last compaction) →
  you are the Lead. Act according to it: classify the task → choose the cycle (short/full) → then follow the profile.

- Otherwise (you are the main thread, the orchestrator prompt body is NOT in context — startup without
  the profile OR it was lost after compaction) → READ framework/subagents/orchestrator.md NOW, then act
  as the Lead. This is the portable emulation of the profile on harnesses without --agent/--append.
```

**Re-trigger points:** session start · **after compaction** · resume from
`task_dir/.context/orchestrator-context.md`. In each of them, re-check the middle/third branch: if the
prompt body is not in context, reread the profile before the first management action.

## Delivery on Different Harnesses (One Portable Carrier)

- **Harness WITH a profile** (Claude CLI, launched under `--append-system-prompt` or `--agent orchestrator`,
  see `framework/subagents/orchestrator.md` § «Launch Method» and manifest §6.1) → the prompt body
  is preloaded into the system prompt → the second branch of the stub is true → the stub is **no-op**.
- **Harness WITHOUT a profile** (Codex / Cursor, etc.) → the third branch triggers: the main thread
  reads the profile itself. Durability — via re-triggering (this stub is always-on, survives compaction;
  the profile body is reread if lost).

## Where to Go (Short Map)

- Simple task (bug in one file, < 20 lines, no new features, no new metadata objects) →
  short cycle: skill **`quick-fix`** (via the Skill tool).
- Medium / complex (new features, multiple files, architecture, new metadata objects) →
  full cycle: orchestrator work according to the profile + phase mechanics **`full-cycle`**.
- **If in doubt** — treat it as complex (full).

Details of classification, cycle selection, and self-vs-delegate under the quick-fix guard are in the
orchestrator profile (Layer 1). This rule does NOT duplicate them, so as not to bloat the always-on
channel inherited by subagents.

## Tools

- The agent discovers available tools dynamically through MCP (`tools/list`) — do not hardcode tool-ids
- Usage skills: `/<ide-cli-dot-catalog>/skills/tool-usage/`
- capability → MCP mapping: `/<ide-cli-dot-catalog>/capabilities/registry.yaml`, rule:
  `/<ide-cli-dot-catalog>/rules/capability-resolution.mdc`

---
depends_on:
- framework/subagents/orchestrator.md
- framework/skills/framework-meta/quick-fix/SKILL.md
- framework/workflows/full-cycle.md
- framework/rules/source-of-truth.md
- framework/rules/protected-paths.mdc
- framework/rules/skill-learning-policy.mdc
- framework/rules/git-workflow.md
---
