---
name: rlm-workflow
description: Universal reusable knowledge (patterns, architecture decisions, domain facts) → RLM, NOT into context. Before a non-trivial task/decision in a domain, pull from RLM. Native memory is only a thin always-on core.
alwaysApply: true
---
# Memory Layout and Working with RLM

> Two layers of memory with different loading models. Native is push (always in context), RLM is pull (retrieved on demand). Do not confuse their purpose.

## Layout (GUARD)

| Knowledge | Where | Why |
|---|---|---|
| Small, needed **every turn**, critical: security, invariants, standing preferences, pointer to active task | **native** (`MEMORY.md` + `memory/*.md`) | push - hangs in context all the time, survives compaction for free |
| Universal, reusable, **growing**, detailed: patterns/anti-patterns, architecture decisions with alternatives, stable 1С/БСП domain facts, module findings | **RLM** (pull) | must NOT hang in context; retrieved by topic via semantic search |
| Transient task state: PENDING, next step, WIP | `task_dir/.context/*.md` (agent-context) | lives in the task, not in memory |

**GUARD:** do NOT keep universal knowledge in native/always-on context permanently (bloats every startup). Transient must NOT be in RLM.

## TRIGGER - WRITE to RLM

- Learned a reusable pattern/anti-pattern (after >=2 iterations, see `skill-learning`)
- Made an architectural decision with alternatives
- Found a stable domain fact (platform/БСП behavior) that matters beyond the current task

→ apply the `rlm-workflow` skill (how to write: tool, level, causal decision).

## TRIGGER - READ from RLM (at the boundary of work, NOT every turn)

- You are starting a non-trivial task or architectural decision in domain D
- You got stuck on a problem that smells recurrent (error, integration quirk)

→ one `rlm_enterprise_context(query=<domain/symptom>)` BEFORE design/implementation. Details are in the skill.

> Parallel: `search-before-write` checks existing **code**; this trigger checks existing **knowledge**. The trigger is phase-based: when entering work, otherwise pull degenerates into push.

## MUST (invariant, always)

- Any write to RLM only after `rlm_start_session` (otherwise silent failure).
- Universal knowledge is written to RLM, NOT duplicated in the native always-on core.

---
depends_on:
  - rlm-workflow
  - framework/rules/agent-context-protocol.md
  - framework/rules/search-before-write.md
---
