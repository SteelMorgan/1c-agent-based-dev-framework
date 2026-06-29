# Навыки фреймворка

Навык — это markdown-документ, обучающий агента конкретному умению.

В этом фреймворке навыки делятся на категории по назначению:

| Категория | Каталог | Назначение | Пример |
|-----------|---------|-----------|--------|
| **bsl-practices** | `framework/skills/bsl-practices/` | Стандарты кодирования и паттерны | `coding-standards.md` |
| **tool-usage** | `framework/skills/tool-usage/` | Когда и как использовать MCP-инструменты | `syntax-checking.md` |
| **spec-writing** | `framework/skills/spec-writing/` | Стандарты спецификаций | `spec-standard.md` |
| **_ext** | `framework/skills/*_ext/` | Расширения внешних навыков (Anthropic и др.) | `agent-development_ext` |

## Как это связано с правилами

- Навыки описывают **как лучше действовать** (рекомендации и сценарии).
- Правила в `framework/rules/` фиксируют **обязательные ограничения** (что MUST/SHOULD/MAY).

В работе это обычно выглядит так:
1. Правило задаёт рамки.
2. Навык даёт практический способ их реализовать.
3. Workflow оркестратора связывает это в фазовый процесс.

## Где смотреть дальше

- Создание и развитие навыков: `skill-creator_ext`
- Методология MCP + capability: [docs/info/mcp.md](./mcp.md)
- RU→EN зеркало навыков: [docs/info/ru-en-mirror.md](./ru-en-mirror.md)
- Политики SDD/TDD: [docs/info/sdd.md](./sdd.md), [docs/info/tdd.md](./tdd.md)
- Общий workflow: [framework/workflows/full-cycle/SKILL.md](../../framework/workflows/full-cycle/SKILL.md)
- Оркестрация фаз: [framework/workflows/orchestrator/SKILL.md](../../framework/workflows/orchestrator/SKILL.md)
- Правила: [framework/rules](../../framework/rules)

---

Подробности создания навыков — в навыке `skill-creator_ext`.
Подробности установки в проект — через `tools/install.py` и инструкции из `README.md`.
