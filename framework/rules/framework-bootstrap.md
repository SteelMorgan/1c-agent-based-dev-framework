---
name: framework-bootstrap
description: 1C BSL Agent Development Framework — bootstrap-контекст для всех задач
alwaysApply: true
---
# 1C BSL Agent Development Framework

Фреймворк агентной разработки для 1С BSL. Это минимальный контекст — детали загружай по требованию.

## Когда загружать что

| Ситуация | Что загрузить |
|----------|---------------|
| Обсуждение / планирование задачи | Ничего дополнительно — этого bootstrap достаточно |
| **Простая задача** (баг в одном файле, < 20 строк, без новых фич, без новых объектов метаданных) | `/<ide-cli-dot-catalog>/rules/quick-fix.md` |
| **Сложная задача** (новые фичи, несколько файлов, архитектурные решения, новые объекты метаданных) | `/<ide-cli-dot-catalog>/rules/orchestrator.md` |
| Написание / редактирование BSL-кода | `/<ide-cli-dot-catalog>/rules/mandatory-tools.md` + `/<ide-cli-dot-catalog>/skills/bsl-practices/*` по необходимости |
| Написание спецификации | `/<ide-cli-dot-catalog>/skills/spec-writing/spec-standard.md` |
| Ревью кода / артефактов | `/<ide-cli-dot-catalog>/rules/cross-review-policy.md` + чек-лист |
| Работа с git (коммиты, ветки, merge задачи) | `/<ide-cli-dot-catalog>/rules/git-workflow.md` |

> **Если сомневаешься** — трактуй как сложную, загружай `orchestrator.md`.

> **КРИТИЧНО** - сообщи пользователю в чат как ты классифицировал задачу и какой путь дальше будет загружен: `orchestrator.md` или `quick-fix.md`.

## Инструменты

- Агент обнаруживает доступные инструменты динамически через MCP (`tools/list`) — не hardcode имена tool-ов
- Навыки использования: `/<ide-cli-dot-catalog>/skills/tool-usage/`
- Маппинг capability → MCP: `/<ide-cli-dot-catalog>/capabilities/registry.yaml`, правило: `/<ide-cli-dot-catalog>/rules/capability-resolution.mdc`

---
depends_on:
- framework/workflows/quick-fix.md
- framework/workflows/orchestrator.md
- framework/workflows/source-of-truth-policy.md
- framework/rules/protected-paths.mdc
- framework/rules/skill-learning-policy.mdc
- framework/rules/git-workflow.md
---
