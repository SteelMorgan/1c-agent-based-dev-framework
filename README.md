# 1C BSL Agent Development Framework

Фреймворк модульной агентной разработки для 1С BSL. Позволяет настроить AI-агентов (Cursor, Claude Code и др.) для работы с конфигурациями 1С через MCP-серверы — без привязки к конкретным провайдерам, с кросс-ревью и tier-ингом моделей.

*English: Modular agent framework for 1C BSL development. Configure AI agents for 1C configurations via MCP servers — MCP-agnostic, cross-review, model tiering.*

---

## Ключевые возможности

- **Модульность** — навыки, правила, агенты и воркфлоу подключаются отдельно
- **MCP-agnostic** — замена MCP-сервера = правка одного provider profile
- **Кросс-ревью** — артефакты проверяются другим агентом по чек-листам
- **SDD / TDD** — стандарт спецификаций, тест-планы, тест-раннер
- **Tier-инг моделей** — Economy/Mid/High/Premium для экономии токенов

---

## Быстрый старт

### Вариант 1: Ручная установка (MVP)

1. Скопируйте `framework/` в корень проекта
2. Скопируйте `.cursor/` (адаптер для Cursor IDE)
3. Отредактируйте `framework/config.md` под свой проект
4. Начинайте работу — bootstrap-правило подхватит конфигурацию

### Вариант 2: Install-скрипт

```bash
# Интерактивный режим — выбор IDE, дерево компонентов с чекбоксами
python install.py

# Командная строка — IDE + компоненты, зависимости подтянутся автоматически
python install.py --ide cursor --include agent/developer workflow/quick-fix

# Посмотреть дерево всех компонентов
python install.py --ide cursor --list

# Установить всё
python install.py --ide cursor --all

# Пересоздать симлинки если переместили фреймворк
python install.py --relink
```

Скрипт создаёт симлинки или копии (если нет админ прав) в директории IDE. Работает на Python 3.7+ без внешних зависимостей.

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
├── .cursor/                  # Адаптер для Cursor IDE
│   └── rules/
│       └── framework-bootstrap.mdc
├── framework/                # Ядро (IDE-agnostic markdown)
│   ├── config.md            # ← Единственный файл для настройки
│   ├── tool-registry/       # Capability-контракты и provider profiles
│   ├── skills/              # tool-usage, bsl-practices, spec-writing
│   ├── rules/               # mandatory-tools, cross-review, TDD, SDD
│   ├── agents/              # Роли: analyst, architect, developer, etc.
│   └── workflows/           # full-cycle, quick-fix, orchestrator
└── README.md
```

---

## Поддерживаемые MCP-серверы

| Провайдер | Репозиторий |
|-----------|-------------|
| platform-context | [alkoleft/mcp-bsl-platform-context](https://github.com/alkoleft/mcp-bsl-platform-context) |
| copilot-proxy | [SteelMorgan/spring-mcp-1c-copilot](https://github.com/SteelMorgan/spring-mcp-1c-copilot) |
| test-runner | [alkoleft/mcp-onec-test-runner](https://github.com/alkoleft/mcp-onec-test-runner) |
| log-checker | [SteelMorgan/1c-log-checker](https://github.com/SteelMorgan/1c-log-checker) |
| metadata-tools | [RooLee10/1c-mcp-tools](https://github.com/RooLee10/1c-mcp-tools) — метаданные, запросы к БД, навигационные ссылки (6 tool-ов) |
| batch-ops | [vladimir-kharin/1c-batch](https://github.com/vladimir-kharin/1c-batch) |
| lsp-bridge | mcp-bsl-lsp-bridge |

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
| **Claude Code** | `AGENTS.md` или `.claude/` в корне проекта |
| **Windsurf** | `.windsurfrules` в корне проекта |
| **VS Code + Continue** | `.continue/` в корне проекта |

Проектные навыки коммитятся в репозиторий проекта и доступны всей команде.

---

## Расширение фреймворка

### Добавление нового навыка (skill)

Навык — это markdown-документ, обучающий агента конкретному умению. Два типа:

| Тип | Каталог | Назначение | Пример |
|-----|---------|-----------|--------|
| **bsl-practices** | `framework/skills/bsl-practices/` | Стандарты кодирования, паттерны, антипаттерны | `coding-standards.md` |
| **tool-usage** | `framework/skills/tool-usage/` | Как и когда использовать конкретную capability | `syntax-checking.md` |

**Шаги создания:**

1. **Скопировать шаблон** [`_template-skill.md`](framework/skills/_template-skill.md) в нужный подкаталог
2. **Заполнить YAML frontmatter:**
   ```yaml
   ---
   id: skill/<имя-файла-без-расширения>
   type: skill
   depends_on: []  # или [registry/tool-registry] для tool-usage
   ---
   ```
3. **Заполнить секции:** Назначение, Когда применять (таблица триггер → действие), Сценарии использования, Примеры (корректный / некорректный код)
4. **Обновить зависимости** — если агенты должны знать про новый навык, добавить его id в `depends_on` соответствующих агентов

**Чек-лист нового навыка:**
- [ ] Объясняет ПОЧЕМУ, а не только ЧТО
- [ ] Есть примеры кода (правильный + неправильный)
- [ ] Привязан к конкретным capability (для tool-usage) или паттернам (для bsl-practices)
- [ ] YAML frontmatter с `id`, `type`, `depends_on`
- [ ] Агенты, использующие навык, обновлены

### Добавление нового инструмента (tool)

Когда программист 1С дорабатывает MCP-сервер (добавляет обработку-контейнер, расширяет существующую) или подключает новый MCP — нужно обновить фреймворк, чтобы агенты знали про новый tool.

#### Сценарий 1: Новый tool в существующем MCP-сервере

Пример: добавили обработку-контейнер `mcp_ИнструментАнализДанных` в RooLee10/1c-mcp-tools.

**Шаги:**

1. **Обновить provider profile** (`framework/tool-registry/providers/<provider>.md`)
   - Добавить новый tool в секцию "Инструменты"
   - Описать: имя tool, параметры (с типами и обязательностью), формат результата, побочные эффекты
   - Добавить пример вызова

2. **Решить: это новая capability или расширение существующей?**
   - Если tool расширяет возможности существующей capability → обновить маппинг в provider profile
   - Если tool даёт **принципиально новую** возможность → создать capability contract в `tool-registry.md` (шаг 3)

3. **Создать capability contract** (если нужен)
   - Добавить в `framework/tool-registry/tool-registry.md` → секция нужной категории (Core/Important/Optional)
   - Описать: входные параметры, формат результата (`structured` / `raw_text` / `mixed`), типичные ошибки, побочные эффекты
   - Обновить матрицу совместимости

4. **Создать или обновить skill** (`framework/skills/tool-usage/`)
   - Если для tool нужен отдельный навык — создать файл по образцу существующих
   - Если tool логически относится к существующему навыку — дополнить его
   - В skill описать: КОГДА использовать, с ЧЕМ комбинировать, примеры сценариев

5. **Обновить зависимости**
   - Добавить YAML frontmatter `depends_on` в новые файлы
   - Обновить `depends_on` в файлах, которые теперь ссылаются на новый навык/capability
   - Обновить агентов, если новый tool входит в их зону ответственности

#### Сценарий 2: Совершенно новый MCP-сервер

1. **Создать provider profile** из шаблона [`_template-provider.md`](framework/tool-registry/_template-provider.md)
2. Выполнить шаги 2-5 из Сценария 1 для каждого tool
3. **Обновить `config.md`** — добавить MCP-сервер в секцию провайдеров
4. **Обновить таблицу** "Поддерживаемые MCP-серверы" в этом README

#### Чек-лист после добавления tool

- [ ] Provider profile содержит все tool-ы с точными параметрами
- [ ] Новые capability (если есть) описаны в `tool-registry.md`
- [ ] Матрица совместимости в `tool-registry.md` обновлена
- [ ] Skill для нового tool создан/обновлён (КОГДА использовать + примеры)
- [ ] YAML frontmatter `depends_on` актуален во всех затронутых файлах
- [ ] Агенты, работающие с новой capability, обновлены

#### Пример: tool-ы запросов в metadata-tools

RooLee10/1c-mcp-tools добавил обработку `mcp_ИнструментРаботаСЗапросами` с 4 tool-ами:

| Tool | Что делает | Capability |
|------|-----------|------------|
| `validate_query` | Проверка синтаксиса запроса без выполнения | `validate_query` (новая) |
| `execute_query` | Выполнение запроса с авторезолвом ссылок | `execute_query` (новая) |
| `parse_nav_link` | Навигационная ссылка → описание объекта | `resolve_nav_link` (новая) |
| `get_nav_link` | Описание объекта → навигационная ссылка | `resolve_nav_link` (новая) |

Были созданы 3 новые capability в `tool-registry.md`, обновлён provider profile `metadata-tools.md`, обновлена матрица совместимости.

---

## Лицензия

MIT (или на выбор пользователя).

---

## Благодарности

- Референсные репозитории: comol/cursor_rules_1c, AndreevED/1c-ai-feature-dev-workflow, rmartynenko/workflow-dev-1c-claude-code, Nikolay-Shirokov/cc-1c-skills
- Паттерны и практики: Anthropic Claude, Cursor IDE
