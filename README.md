# 1C BSL Agent Development Framework

Фреймворк модульной агентной разработки для 1С BSL. Позволяет настроить AI-агентов (Cursor, Claude Code и др.) для работы с конфигурациями 1С через MCP-серверы — без привязки к конкретным провайдерам, с кросс-ревью и tier-ингом моделей.

*English: Modular agent framework for 1C BSL development. Configure AI agents for 1C configurations via MCP servers — MCP-agnostic, cross-review, model tiering.*

---

## Ключевые возможности

- **Модульность** — навыки, правила, агенты и воркфлоу подключаются отдельно
- **MCP-agnostic** — агенты обнаруживают инструменты через MCP (`tools/list`), без жёстких привязок
- **Кросс-ревью** — артефакты проверяются другим агентом по чек-листам
- **SDD / TDD** — стандарт спецификаций, тест-планы, тест-раннер
- **Tier-инг моделей** — haiku/sonnet/opus задаётся per-agent через `model` field

---

## Быстрый старт

### Install-скрипт (рекомендуется)

```bash
# Интерактивный режим — выбор IDE, дерево компонентов с чекбоксами
python tools/install.py

# Командная строка — IDE + компоненты, зависимости подтянутся автоматически
python tools/install.py --ide cursor --include agent/developer workflow/quick-fix

# Посмотреть дерево всех компонентов
python tools/install.py --ide cursor --list

# Установить всё
python tools/install.py --ide cursor --all

# Пересоздать симлинки если переместили фреймворк
python tools/install.py --relink
```

Скрипт создаёт симлинки в директории IDE, настраивает модели агентов через TUI. Работает на Python 3.7+ без внешних зависимостей.

Подробное руководство по всем возможностям — [docs/install-guide.md](docs/install-guide.md).

**Время до первого запуска: ~5 минут.**

---

## Архитектура

Обзор архитектуры — [docs/SPEC-001-framework-architecture.md](docs/SPEC-001-framework-architecture.md).

---

## Структура каталогов

```
1c-agent-based-dev-framework/
├── docs/                     # Спецификации и исследования
│   └── SPEC-001-framework-architecture.md
├── framework/                # Ядро (IDE-agnostic markdown)
│   ├── skills/              # tool-usage, bsl-practices, spec-writing, *_ext
│   ├── rules/               # mandatory-tools, cross-review, TDD, SDD
│   ├── agents/              # Роли: analyst, architect, developer, etc.
│   └── workflows/           # full-cycle, quick-fix, orchestrator
├── tools/
│   ├── install.py           # Установщик компонентов
│   ├── tui.py               # TUI-интерфейс для install.py
│   └── model-defaults.json  # Маппинг моделей по IDE
└── README.md
```

---

## MCP-серверы с готовыми tool-usage навыками

> Фреймворк не ограничен этим списком. Для добавления нового MCP-сервера — создайте tool-usage навык.
> Полный маппинг capability → MCP → навык: [`_capability-index.md`](framework/skills/tool-usage/_capability-index.md).

| MCP-сервер | Репозиторий | Навык |
|------------|-------------|-------|
| platform-context | [alkoleft/mcp-bsl-platform-context](https://github.com/alkoleft/mcp-bsl-platform-context) | search-before-write |
| copilot-proxy | [SteelMorgan/spring-mcp-1c-copilot](https://github.com/SteelMorgan/spring-mcp-1c-copilot) | search-before-write |
| test-runner | [alkoleft/mcp-onec-test-runner](https://github.com/alkoleft/mcp-onec-test-runner) | syntax-checking, test-execution |
| log-checker | [SteelMorgan/1c-log-checker](https://github.com/SteelMorgan/1c-log-checker) | log-analysis |
| metadata-tools | [RooLee10/1c-mcp-tools](https://github.com/RooLee10/1c-mcp-tools) | metadata-discovery |
| batch-ops | [vladimir-kharin/1c-batch](https://github.com/vladimir-kharin/1c-batch) | — |
| lsp-bridge | mcp-bsl-lsp-bridge | code-navigation |

---

## Проектные навыки и правила

Фреймворк разделяет навыки на два уровня:

| Уровень | Где лежит | Что содержит | Кто редактирует |
|---------|-----------|-------------|-----------------|
| **Фреймворк** | `framework/skills/`, `framework/rules/` | Универсальные навыки BSL, стандарты, политики | Сообщество фреймворка |
| **Проект** | Каталог проекта, предусмотренный IDE | Навыки и правила конкретного проекта/конфигурации | Команда проекта |

**Проектные навыки** — это знания, актуальные только для вашего проекта:
- Особенности конфигурации (какие подсистемы, какие модули критичные)
- Локальные coding conventions (если отличаются от стандарта ИТС)
- Бизнес-правила (например: "документ X всегда проводится через регистр Y")
- Маппинг ролей и прав
- Инструкции по работе с нетиповыми обработками

**Куда размещать:**

| IDE | Каталог для проектных навыков |
|-----|------------------------------|
| **Cursor** | `.cursor/rules/` и `.cursor/skills/` в корне проекта |
| **Claude Code** | `.claude/skills/` в корне проекта |
| **Windsurf** | `.windsurfrules` в корне проекта |
| **VS Code + Continue** | `.continue/` в корне проекта |

Проектные навыки коммитятся в репозиторий проекта и доступны всей команде.

---

## Расширение фреймворка

### Добавление нового навыка (skill)

Навык — это markdown-документ, обучающий агента конкретному умению.

| Категория | Каталог | Назначение | Пример |
|-----------|---------|-----------|--------|
| **bsl-practices** | `framework/skills/bsl-practices/` | Стандарты кодирования, паттерны, антипаттерны | `coding-standards.md` |
| **tool-usage** | `framework/skills/tool-usage/` | Когда и как использовать MCP-инструменты | `syntax-checking.md` |
| **spec-writing** | `framework/skills/spec-writing/` | Стандарты спецификаций | `spec-standard.md` |
| **_ext** | `framework/skills/*_ext/` | Расширения внешних навыков (Anthropic и др.) | `agent-development_ext` |

Подробности создания навыков — в навыке `skill-creator_ext`.

### Добавление нового MCP-инструмента

1. **Создать или обновить tool-usage навык** — описать КОГДА и ПОЧЕМУ использовать инструмент, workarounds, сценарии
2. **Обновить `_capability-index.md`** — добавить строку capability → MCP → навык
3. **Обновить агентов** — если навык нужен новым агентам, добавить в `skills` frontmatter
4. **Обновить таблицу MCP-серверов** в этом README

**Чек-лист:**
- [ ] Tool-usage навык описывает КОГДА использовать + workarounds + сценарии
- [ ] `_capability-index.md` содержит маппинг
- [ ] Агенты, использующие навык, обновлены (`skills` в frontmatter)

---

## Лицензия

MIT (или на выбор пользователя).

---

## Благодарности

- Референсные репозитории: comol/cursor_rules_1c, AndreevED/1c-ai-feature-dev-workflow, rmartynenko/workflow-dev-1c-claude-code, Nikolay-Shirokov/cc-1c-skills
- Паттерны и практики: Anthropic Claude, Cursor IDE
