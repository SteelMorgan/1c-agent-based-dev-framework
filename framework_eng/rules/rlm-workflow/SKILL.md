---
name: rlm-workflow
description: "Before non-trivial domain work, read RLM"
alwaysApply: true
---
# Memory Layout and Working with RLM

> Two memory layers with different loading models. Native is push (always in context), RLM is pull (retrieved on demand). Do not confuse their purpose.

## Layout (GUARD)

| Knowledge | Where | Why |
|---|---|---|
| Small, needed **every turn**, critical: safety, invariants, standing preferences, pointer to the active task | **native** (`MEMORY.md` + `memory/*.md`) | push - stays in context always, survives compaction for free |
| Universal, reusable, **growing**, detailed: patterns/antipatterns, architecture decisions with alternatives, stable domain facts about 1С/БСП, findings by modules | **RLM** (pull) | must NOT stay in context; retrieved by topic via semantic search |
| Transient task state: PENDING, next step, WIP | `task_dir/.context/*.md` (agent-context) | lives in the task, not in memory |

**GUARD:** do NOT keep universal knowledge in native/always-on context all the time (bloats every startup). Transient state is NOT in RLM.

## TRIGGER - WRITE to RLM

- Learned a reusable pattern/antipattern (after >=2 iterations, see `skill-learning`)
- Made an architectural decision with alternatives
- Found a stable domain fact (platform/БСП behavior) that matters beyond the current task

→ apply the `rlm-workflow` skill (how to write: tool, level, causal decision).

## TRIGGER - READ from RLM (at the boundary of work, NOT every turn)

- You are starting a non-trivial task or architectural decision in domain D
- You hit a problem that smells repetitive (bug, integration quirk)

→ one `rlm_enterprise_context(query=<domain/symptom>)` BEFORE design/implementation. Details are in the skill.

> Parallel: `search-before-write` checks existing **code**; this trigger checks existing **knowledge**. The trigger is phase-based: at work entry, otherwise pull degrades into push.

## MUST (invariant, always)

- Any write to RLM is only after `rlm_start_session` (otherwise silent failure).
- Universal knowledge is written to RLM, NOT duplicated in the native always-on core.

---
depends_on:
  - rlm-workflow
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/search-before-write/SKILL.md
---
