---
name: framework-bootstrap
description: 1C BSL Agent Development Framework — portable self-promoting main-thread stub
alwaysApply: true
---
# 1C BSL Agent Development Framework

This is an agent development framework for 1C BSL. A minimal always-on anchor + a portable
cross-harness bridge that raises the Lead/orchestrator role in the main thread. Detailed routing and
orchestration management live in the **orchestrator profile** (`framework/subagents/orchestrator.md`),
not here.

## Self-Promoting Stub (main thread)

> The goal is to guarantee that the main thread HAS orchestration instructions, on any harness and after
> any context loss. The condition is keyed on the **actual presence of the orchestrator instruction body in
> the current context**, NOT on the belief “I am the orchestrator”: after compaction, the task state (“I
> believe I am the orchestrator”) survives in the summary, while the instruction body is evicted. Keying on
> belief would create a false read skip.

```
- Если ты САБАГЕНТ (системный промпт явно называет роль: analyst / architect / developer-code /
  developer-tests / scenario-author / scenario-coder / tester / reviewer / explorer / debugger) →
  это правило НЕ для тебя. Маршрутизация и манинг оркестрации тебе не нужны — ты исполняешь
  делегированную фазу. ВЫХОД.

- Если тело манинга оркестратора СЕЙЧАС в контексте (профиль orchestrator загружен на старте
  ИЛИ ты прочитал framework/subagents/orchestrator.md в этой сессии после последнего компакта) →
  ты — Lead. Действуй по нему: классифицируй задачу → выбери цикл (short/full) → дальше по профилю.

- Иначе (ты главный поток, тела манинга оркестратора в контексте НЕТ — старт без профиля ИЛИ
  потеряли после компакта) → ПРОЧИТАЙ СЕЙЧАС framework/subagents/orchestrator.md, затем действуй
  как Lead. Это и есть портативная эмуляция профиля на харнесах без --agent/--append.
```

**Re-trigger points:** session start · **after compaction** · resuming from
`task_dir/.context/orchestrator-context.md`. In each of them, re-check the middle/third branch: if
the management body is not in context, reread the profile before the first management action.

## Delivery on Different Harnesses (one portable carrier)

- **Harness WITH profile** (Claude CLI, launched with `--append-system-prompt` or `--agent orchestrator`,
  see `framework/subagents/orchestrator.md` § «Способ запуска» and the manifest §6.1) → management
  is preloaded into the system prompt → the second branch of the stub is true → stub **no-op**.
- **Harness WITHOUT profile** (Codex / Cursor, etc.) → the third branch triggers: the main thread reads
  the profile itself. Durability is via re-trigger (this stub is always-on, survives compaction; the
  profile body is reread on loss).

## Where to Go (short map)

- Simple task (bug in one file, < 20 lines, no new features, no new metadata objects) →
  short cycle: skill **`quick-fix`** (via the Skill tool).
- Medium / complex (new features, several files, architecture, new metadata objects) →
  full cycle: orchestrator work according to the profile + phase mechanics **`full-cycle`**.
- **If in doubt** — treat it as complex (full).

Details of classification, cycle selection, and self-vs-delegate under the quick-fix guard are in the orchestrator
profile (Layer 1). This rule does NOT duplicate them so as not to bloat the always-on channel inherited by subagents.

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
