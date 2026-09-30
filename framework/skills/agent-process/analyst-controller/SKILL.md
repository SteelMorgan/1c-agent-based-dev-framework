---
name: analyst-controller
installable: true
description: Контроллер роли Analyst: primary-работа через Consilium и ленивый standalone fallback только при доказанной технической недоступности.
---

# Контроллер Analyst

Контроллер выбирает ветку исполнения и не анализирует требования вместо выбранной ветки.

## Порядок чтения

1. Проверь наличие `references/primary-consilium.md`. Отсутствие файла — `BLOCK`; fallback не читать.
2. Прочитай только `references/primary-consilium.md` и начни primary-протокол.
3. Не открывай `references/fallback-standalone.md`, пока Оркестратор не зафиксировал канонический `FALLBACK_TRIGGER`.

## Primary outcomes

- `SUCCESS` — спецификация и durable Consilium verdict подготовлены.
- `CONSILIUM_NOT_APPLICABLE` — material trade-off отсутствует; primary-ветка сама создаёт спецификацию и возвращает Оркестратору rationale со ссылками на входы. После материализации acceptance gate проверяет ссылку на раздел спецификации.
- `BLOCK` / `ESCALATE_TO_HUMAN` — содержательная проблема; fallback запрещён.

## Технический fallback

Fallback допустим только до создания сессии, если machine-readable `doctor --json` одновременно показывает:

- `quorum.ok = false`;
- `excluded` содержит хотя бы одну запись; пустой список не разрешает fallback;
- все причины исключения, повлиявшие на кворум, принадлежат allowlist `cli_on_path`, `adapter_help`, `kimi_doctor`, `adapter_probe` в поле `failed_check`;
- JSON-схема распознана полностью, неизвестных причин нет.

До fallback Оркестратор является единственным автором события `FALLBACK_TRIGGER` и указывает evidence. Generic exit code, неизвестный исход, `stalemate`, `round_limit`, disagreement, quality failure, содержательный BLOCK, missing reference и cleanup failure fallback не разрешают. Если сессия уже создана, сначала обязателен успешный `close`; авария после старта без стабильной machine-readable причины остаётся `BLOCK`.

## Граница прав

`readonly: true` запрещает изменять исходники проекта, но разрешает создавать артефакты задачи, запускать lifecycle-команды Consilium через review-harness и закрывать созданную сессию. Внешним моделям передаются только focused paths; секреты, полный workspace и лишние evidence-файлы запрещены.

После разрешённого `FALLBACK_TRIGGER` прочитай `references/fallback-standalone.md` и выполни старый контракт без смешивания с частичным transcript primary-ветки.

---
depends_on:
  - framework/skills/tool-usage/review/agent-consilium/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
  - framework/skills/tool-usage/platform-data/platform-data-core/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
  - framework/skills/tool-usage/v8-session-manager/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/capability-resolution/SKILL.md
  - framework/rules/no-direct-db-access/SKILL.md
  - framework/rules/skill-learning-policy/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/rules/skill-reading-protocol/SKILL.md
---
