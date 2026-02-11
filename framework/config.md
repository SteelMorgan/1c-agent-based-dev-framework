---
id: config/main
type: config
depends_on: []
---

# Конфигурация фреймворка

> **Единственный файл**, который редактирует пользователь для настройки фреймворка под свой проект.

---

## Режим работы

| Параметр | Значение | Описание |
|----------|----------|----------|
| `mode` | `deterministic` \| `free` | **deterministic** — полный цикл с обязательным кросс-ревью; **free** — навыки и правила без навязывания воркфлоу |

---

## Модели агентов

Таблица соответствия ролей агентов моделям:

| Роль | Tier | Модель по умолчанию | Альтернативы |
|------|------|---------------------|--------------|
| analyst | Mid | gemini-3-pro | gpt-5.3-codex |
| architect | High | sonnet-4.5 | opus-4.6 |
| developer | High | sonnet-4.5 | gpt-5.3-codex |
| reviewer | Premium | opus-4.6 | gpt-5.2 |
| tester | Mid | gemini-3-pro | sonnet-4.5 |
| explorer | Economy | grok-code-fast | haiku-4.5 |
| formatter | Economy | grok-code-fast | haiku-4.5 |

---

## MCP-провайдеры

Список активных провайдеров (включить/выключить):

| Провайдер | Статус |
|-----------|--------|
| platform-context | enabled |
| copilot-proxy | enabled |
| test-runner | enabled |
| log-checker | enabled |
| metadata-tools | enabled |
| batch-ops | disabled |
| lsp-bridge | enabled |

---

## Параметры кросс-ревью

| Параметр | Значение | Описание |
|----------|----------|----------|
| `max_iterations` | 3 | Максимум итераций ревью на один артефакт |
| `escalation` | user | Кому эскалировать при нерешённых BLOCK-замечаниях |
| `review_gating` | full \| standard \| light \| none | Уровень контроля по умолчанию (можно задать отдельно для типа артефакта) |

---

## Ограничения

| Параметр | Значение | Описание |
|----------|----------|----------|
| `metadata_creation` | user_only | Агент не может создавать объекты метаданных |
| `max_files_per_change` | 20 | Предупреждать, если изменяется больше файлов |
| `language` | ru | Комментарии и переменные в BSL-коде — на русском |

---

## Проект

| Параметр | Значение | Описание |
|----------|----------|----------|
| `bsp_version` | "3.1" \| null | Версия БСП или null, если БСП не используется |
| `platform_version` | "8.3.25" | Версия платформы 1С:Предприятие |
| `ide` | cursor \| claude-code \| other | Используемая IDE |
