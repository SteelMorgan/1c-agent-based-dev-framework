---
name: architect-controller
installable: true
description: Контроллер роли Architect: primary-работа через Consilium и ленивый standalone fallback только при доказанной технической недоступности.
---

# Контроллер Architect

Контроллер выбирает ветку исполнения и не проектирует решение вместо выбранной ветки.

## Порядок чтения

1. Проверь наличие `references/primary-consilium.md`. Отсутствие файла — `BLOCK`; fallback не читать.
2. Прочитай только primary reference и начни primary-протокол.
3. `references/fallback-standalone.md` разрешено читать только после канонического `FALLBACK_TRIGGER` Оркестратора.

## Исходы и fallback

`SUCCESS`, `CONSILIUM_NOT_APPLICABLE`, содержательный `BLOCK` и `ESCALATE_TO_HUMAN` являются исходами primary. `CONSILIUM_NOT_APPLICABLE` не включает fallback: primary-ветка сама создаёт design.

Технический fallback допускается только до создания сессии при `doctor --json: quorum.ok=false`, когда `excluded` непуст и все повлиявшие причины имеют `failed_check` из allowlist `cli_on_path`, `adapter_help`, `kimi_doctor`, `adapter_probe`. Пустой `excluded`, неизвестное поле/причина, generic exit code, stalemate, round limit, disagreement, quality failure, missing reference и cleanup failure дают `BLOCK`. Созданная сессия должна быть успешно закрыта до любого перехода.

`readonly: true` означает запрет изменения исходников проекта; разрешены task/session artifacts, lifecycle Consilium и обязательный cleanup. Focused paths обязательны.

После разрешённого события прочитай fallback reference и выполни старый контракт без transcript primary-ветки.

---
depends_on:
  - framework/skills/tool-usage/review/agent-consilium/SKILL.md
  - framework/skills/spec-writing/technical-design-standard/SKILL.md
  - framework/skills/spec-writing/task-breakdown/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/platform-data/platform-data-core/SKILL.md
  - framework/skills/bsl-practices/api-design/SKILL.md
  - framework/skills/bsl-practices/integration-patterns/SKILL.md
  - framework/skills/bsl-practices/security/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/capability-resolution/SKILL.md
  - framework/rules/no-direct-db-access/SKILL.md
  - framework/rules/skill-learning-policy/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/rules/skill-reading-protocol/SKILL.md
---
