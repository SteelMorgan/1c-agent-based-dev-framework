---
name: reviewer-controller
installable: true
description: Контроллер Reviewer: primary-проверка через review-swarm и ленивый standalone fallback при доказанной технической недоступности роя.
---

# Контроллер Reviewer

## Порядок чтения

1. Проверь наличие `references/primary-review-swarm.md`; отсутствие — `BLOCK`, fallback не читать.
2. Прочитай только primary reference и выполни его evidence/gate protocol.
3. `references/fallback-standalone.md` разрешено читать только после канонического `FALLBACK_TRIGGER` Оркестратора.

## Технический fallback

Fallback разрешён, только если machine-readable preflight одновременно показывает `quorum.ok=false`, непустой `excluded` и отсутствие eligible reviewer исключительно из-за `failed_check` allowlist `cli_on_path`, `adapter_help`, `kimi_doctor`, `adapter_probe`; неизвестных причин быть не должно. Пустой `excluded`, generic exit code, disagreement, findings, `BLOCK_COMPLETION`, exhausted iterations, provider cost/latency, missing reference и cleanup failure fallback не разрешают.

До перехода все созданные Swarm-сессии должны быть успешно закрыты. Оркестратор — единственный автор `FALLBACK_TRIGGER`; Reviewer возвращает только structured evidence. Partial report/transcript в standalone fallback не передаётся.

`readonly: true` запрещает исправлять проверяемый артефакт, но разрешает создавать review evidence/session artifacts и выполнять lifecycle review-swarm. Focused-paths и минимизация evidence обязательны.

Оркестратор обязан выдать controller-процессу права на lifecycle-запись только
в `.swarm-sessions/`, `.review-sandboxes/`, `.swarm-track-record/` и evidence
текущей задачи, сохранив project sources read-only. Если профиль инструментов
не позволяет выполнить lifecycle, возвращается
`BLOCKED_REVIEW_TOOL_PROFILE` с требуемыми путями/командами; это явный BLOCK
конфигурации запуска, а не основание для standalone fallback.

---
depends_on:
  - framework/skills/tool-usage/review/review-swarm/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/spec-writing/technical-design-standard/SKILL.md
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/bsl-practices/test-writing/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/agent-debug/SKILL.md
  - framework/rules/capability-resolution/SKILL.md
  - framework/rules/dap-bsl-debugger/SKILL.md
  - framework/rules/no-direct-db-access/SKILL.md
  - framework/rules/skill-learning-policy/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
  - framework/rules/test-zero-residue/SKILL.md
  - framework/rules/vanessa-scenario-policy/SKILL.md
  - framework/rules/vanessa-test-isolation-policy/SKILL.md
  - framework/rules/self-recovery-limits/SKILL.md
  - framework/rules/skill-reading-protocol/SKILL.md
---
