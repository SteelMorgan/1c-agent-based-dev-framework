# Tool-Usage Skills — Карта ролей × навыки × инструменты

> **Версия:** 1.2 | **Дата:** 2026-03-01 | **Актуально для:** registry.yaml текущей версии

## 1. Назначение каталога

Каталог `tool-usage/` содержит **20 навыков**, которые учат AI-агента правильно использовать MCP-инструменты при разработке на платформе 1С:Предприятие. Навыки разделены на три группы: **core** (10 навыков для работы с LSP, метаданными, запросами, тестами, логами, визуальными проверками), **xml-gen** (7 навыков для генерации XML-метаданных через DSL и CLI) и **review** (3 навыка для запуска независимого ревью через Opus, Codex, Gemini). Всего фреймворк содержит **35 навыков** в четырёх категориях; данный README описывает взаимосвязь всех 35 с ролями жизненного цикла.

Этот README — **навигационная карта** перехода от ручной разработки к агентной. Для каждой из пяти ролей жизненного цикла задачи (БА → ФА → СА → Программист → QA) документ показывает: какие навыки и MCP-инструменты агент уже может использовать, а где остаются пробелы, требующие ручной работы или новых навыков.

Цель — дать участникам команды ясное понимание: что именно агент берёт на себя на каждом этапе, какие инструменты при этом вызывает, и куда направить усилия по расширению фреймворка.

> **Об именах инструментов.** В таблицах используются **capability-имена** из `registry.yaml` (например, `navigate_symbol`, `ask_ai_assistant`). Это абстракция поверх конкретных MCP-серверов — реальные имена инструментов могут отличаться (например, `ask_ai_assistant` → `ask_1c_ai` в `1c-copilot-proxy`, `browser_navigate` → `navigate_page` в `chrome-devtools`). Полный маппинг см. в `registry.yaml`.

> **Примечание:** Некоторые инструменты (например, `navigate_symbol`) являются сквозными и появляются в нескольких навыках, поскольку выполняют разные функции в разных контекстах (навигация, поиск, отладка).

**Как пользоваться документом:**
- Раздел 2 — обзор пайплайна и оценка покрытия по ролям
- Раздел 3 — детальное описание каждой роли: навыки, примеры, пробелы
- Раздел 4 — сводная матрица «навык × роль» для быстрого поиска
- Разделы 5–6 — анализ пробелов и рекомендации по развитию

**Соседние категории навыков:**
- [`bsl-practices/`](../bsl-practices/) — стандарты кодирования, паттерны форм, запросов, обработки ошибок, БСП, написание тестов (7 навыков)
- [`spec-writing/`](../spec-writing/) — спецификации, декомпозиция задач (3 навыка)
- [`framework-meta/`](../framework-meta/) — инструменты разработки самого фреймворка, создание навыков и агентов (5 навыков)

**Связанные документы:**
- [`../../capabilities/registry.yaml`](../../capabilities/registry.yaml) — маппинг capability → MCP-сервер/инструмент
- [`../../../docs/mcp-inventory.md`](../../../docs/mcp-inventory.md) — полный перечень MCP-серверов и инструментов

---

## 2. Путь задачи: от бизнес-требования до production

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│     БА      │───▶│     ФА      │───▶│     СА      │───▶│ Программист │───▶│     QA      │
│  (человек)  │    │  ~50% авто  │    │  ~70% авто  │    │  ~90% авто  │    │  ~75% авто  │
│             │    │             │    │             │    │             │    │             │
│ Требования  │    │ Спецификация│    │ Техническое │    │  Код, XML,  │    │  Тесты,     │
│ и приоритет │    │ и модель    │    │ проектиров. │    │  формы,     │    │  логи,      │
│  → входной  │    │ данных      │    │ и стандарты │    │  тесты      │    │  визуальная │
│   контекст  │    │             │    │             │    │             │    │  проверка   │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
  (вне агента)      spec-writing       bsl-practices       bsl-practices    test-execution
                    platform-data-core code-navigation     tool-usage/core  va-visual-check
                    search-before-w.   review-*            xml-gen          syntax-checking
                    code-navigation    event-log-analysis  review-*         event-log-analysis
                                       tech-log-analysis   spec-writing     tech-log-analysis
                                       platform-data-core                   review-*
```

> **Примечание о пайплайне:** Перед Phase 1 (ФА) запускается **Phase 0 (Explorer)** — техническая роль оркестратора для классификации задачи (простая → `quick-fix`, средняя/сложная → full-cycle). Explorer не является бизнес-ролью жизненного цикла. Подробнее см. [`framework/workflows/full-cycle/SKILL.md`](../../../framework/workflows/full-cycle/SKILL.md).

**Оценка покрытия** — качественная, отражает долю типичных действий роли, которые агент может выполнить самостоятельно или при минимальном контроле человека. БА — внешний входной контекст: агентная цепочка начинается с ФА, который получает уже сформулированное требование.

---

## 3. Роли и навыки

### 3.1 Бизнес-аналитик (БА) — вне агентной цепочки

**Зона ответственности.** Переводит потребности бизнеса в формализованные требования. Отвечает за то, чтобы задача была корректно сформулирована и приоритизирована. Входная точка любой задачи в жизненном цикле разработки.

> БА — **человеческая роль вне агентной цепочки**. Агент получает уже сформулированное требование от ФА. Навыки `spec-standard` и `task-breakdown` технически доступны БА, но реальная агентная автоматизация начинается с ФА.

**Что делает человек:**
- Проводит интервью с заказчиком, фиксирует бизнес-потребность
- Моделирует бизнес-процессы (BPMN, EPC), описывает as-is / to-be
- Формулирует функциональные требования в трекере (Jira, YouTrack)
- Приоритизирует задачи совместно с владельцем продукта
- Согласовывает scope и критерии приёмки

**Что умеет агент:**

| Категория | Навык | Назначение | MCP Tools |
|-----------|-------|------------|-----------|
| spec-writing | `spec-standard` | Структурирование требований по шаблону SDD, RFC2119 | — |
| spec-writing | `task-breakdown` | Декомпозиция задачи в JSON-формате (linear или subagent режим) | — |
| tool-usage | `platform-data-core` | Поиск объектов метаданных, выполнение запросов, работа с навигационными ссылками | `list_metadata_objects`, `get_metadata_structure`, `validate_query`, `execute_query`, `parse_nav_link`, `get_nav_link` |

**Пример рабочего сценария:** БА получает запрос «Добавить скидку по промокоду». Агент помогает оформить требование по шаблону `spec-standard`, декомпозирует на подзадачи через `task-breakdown` (linear mode), а через `platform-data-core` § Metadata Discovery проверяет, есть ли уже справочник «ПромоКоды» или регистр скидок в конфигурации.

**Незакрытые зоны:**
- Моделирование бизнес-процессов (BPMN/EPC-диаграммы) — нет инструмента
- Интеграция с трекерами задач (Jira, YouTrack) — нет MCP-сервера
- Приоритизация и оценка трудоёмкости — требует контекста, недоступного агенту
- Проведение интервью и сбор требований — чисто человеческая задача

---

### 3.2 Функциональный аналитик (ФА) — ~50% покрытие агентом

**Зона ответственности.** Проектирует решение на уровне предметной области — какие объекты метаданных нужны, как устроена модель данных, какие бизнес-правила реализовать. Пишет спецификацию (SDD), декомпозирует на задачи. Мост между бизнесом и технической реализацией.

**Что делает человек:**
- Проектирует модель данных (справочники, документы, регистры)
- Описывает бизнес-логику и правила валидации
- Составляет SDD-спецификацию с детальными требованиями
- Проектирует пользовательские интерфейсы (макеты форм)
- Согласовывает техническое решение с СА и командой

**Что умеет агент:**

| Категория | Навык | Назначение | MCP Tools |
|-----------|-------|------------|-----------|
| spec-writing | `spec-standard` | Генерация SDD по шаблону | — |
| spec-writing | `task-breakdown` | Декомпозиция SDD на задачи разработки (linear или subagent режим) | — |
| tool-usage | `platform-data-core` | Исследование модели данных, проверка гипотез, выполнение запросов, работа с навигационными ссылками | `list_metadata_objects`, `get_metadata_structure`, `navigate_symbol`, `get_call_graph`, `validate_query`, `execute_query`, `parse_nav_link`, `get_nav_link` |
| tool-usage | `search-before-write` | Поиск существующих решений перед проектированием | `navigate_symbol`, `list_metadata_objects`, `get_metadata_structure`, `search_syntax_reference`, `get_type_info`, `search_ssl_functions`, `ask_ai_assistant` |
| tool-usage | `code-navigation` | Анализ существующего кода для понимания паттернов | `navigate_symbol`, `get_call_graph`, `rename_symbol`, `get_diagnostics`, `get_code_actions` |
| bsl-practices | `form-visual-requirements` | Предварительная оценка визуальных требований при проектировании форм | — |

**Пример рабочего сценария:** ФА проектирует систему скидок по промокодам. Агент через `platform-data-core` § Metadata Discovery анализирует существующие объекты (Справочник.Номенклатура, Документ.ЗаказКлиента), через `search-before-write` находит готовые функции в БСП для работы со скидками, через `platform-data-core` § Query Execution проверяет текущую структуру данных, а затем генерирует SDD-спецификацию по шаблону `spec-standard`.

**Незакрытые зоны:**
- Построение диаграмм (ER, sequence, use-case) — нет генератора
- Прототипирование UI (мокапы форм) — нет инструмента
- Оценка влияния на смежные подсистемы — требует глубокого понимания бизнес-контекста

---

### 3.3 Системный аналитик / Архитектор (СА) — ~70% покрытие агентом

**Зона ответственности.** Определяет КАК реализовать решение технически — выбирает паттерны (формы, запросы, обработка ошибок), оценивает производительность и архитектурные риски. Ревьюирует технические решения, контролирует соответствие стандартам платформы и БСП.

**Что делает человек:**
- Выбирает архитектурные паттерны реализации
- Оценивает производительность и масштабируемость решения
- Проводит код-ревью на соответствие стандартам
- Определяет стратегию обработки ошибок и транзакций
- Контролирует использование БСП и платформенных механизмов

**Что умеет агент:**

| Категория | Навык | Назначение | MCP Tools |
|-----------|-------|------------|-----------|
| bsl-practices | `coding-standards` | Проверка соответствия стандартам кодирования | — |
| bsl-practices | `error-handling` | Паттерны обработки ошибок, транзакции, блокировки | — |
| bsl-practices | `form-patterns` | Паттерны клиент-серверного взаимодействия в формах | — |
| bsl-practices | `form-visual-requirements` | Чеклист визуальных и UX-требований к формам | — |
| bsl-practices | `query-patterns` | Правила оптимизации запросов | — |
| bsl-practices | `ssl-patterns` | Паттерны использования БСП | — |
| tool-usage | `code-navigation` | Навигация по коду: определения, вызовы, иерархия; верификация API платформы после ошибки | `navigate_symbol`, `get_call_graph`, `rename_symbol`, `get_diagnostics`, `get_code_actions`, `search_syntax_reference`, `getMembers`, `getMember`, `getConstructors` |
| tool-usage | `platform-data-core` | Анализ метаданных, валидация/выполнение запросов при ревью, навигационные ссылки | `list_metadata_objects`, `get_metadata_structure`, `navigate_symbol`, `get_call_graph`, `validate_query`, `execute_query`, `parse_nav_link`, `get_nav_link` |
| tool-usage | `search-before-write` | Поиск готовых решений в БСП и платформе | `navigate_symbol`, `list_metadata_objects`, `get_metadata_structure`, `search_syntax_reference`, `get_type_info`, `search_ssl_functions`, `ask_ai_assistant` |
| tool-usage | `event-log-analysis` | Журнал регистрации: ошибки, аудит действий | `search_event_log`, `navigate_symbol` |
| tool-usage | `tech-log-analysis` | Технологический журнал: диагностика производительности и блокировок | `search_tech_log`, `logc_get_techlog_config`, `logc_save_techlog`, `logc_configure_techlog`, `logc_get_actual_log_timestamp`, `logc_restore_techlog`, `logc_disable_techlog`, `navigate_symbol` |
| tool-usage | `syntax-checking` | Проверка синтаксиса при ревью | `check_syntax`, `get_diagnostics` |
| tool-usage | `cross-provider-review` | Cross-family второе мнение (Claude↔Codex, изолированный sandbox, read-only) | `.agents/skills/cross-provider-review/scripts/{claude_opus_review,codex_review}.py` |
| tool-usage | `gemini-review` | Независимое ревью через Gemini | — (внешний CLI) |
| spec-writing | `spec-standard` | Контроль соответствия реализации спецификации | — |

**Пример рабочего сценария:** СА ревьюирует реализацию модуля скидок. Агент через `code-navigation` строит граф вызовов модуля, через `syntax-checking` проверяет отсутствие ошибок, применяет правила `coding-standards` и `error-handling` для валидации паттернов, а затем запускает `cross-provider-review` для получения независимого второго мнения от opposite-family модели (Claude↔Codex).

**Незакрытые зоны:**
- Нагрузочное тестирование и профилирование — нет MCP-инструментов
- Оценка архитектурных рисков — требует экспертного суждения
- Визуализация архитектуры (C4, компонентные диаграммы) — нет генератора

---

### 3.4 Программист — ~90% покрытие агентом

**Зона ответственности.** Реализует решение в коде — пишет BSL-модули, создаёт XML-метаданные, настраивает формы, пишет тесты. Выполняет отладку, рефакторинг и синтаксическую проверку. Основной потребитель всех инструментов фреймворка.

**Что делает человек:**
- Пишет BSL-код модулей, обработчиков событий
- Создаёт и редактирует XML-метаданные (формы, роли, макеты, отчёты)
- Настраивает формы (элементы, обработчики, условное оформление)
- Пишет юнит-тесты (YaxUnit)
- Выполняет отладку и рефакторинг

**Что умеет агент — навыки из всех категорий фреймворка:**

| Категория | Навык | Назначение | MCP Tools |
|-----------|-------|------------|-----------|
| **tool-usage / core** | | | |
| tool-usage | `code-navigation` | LSP-навигация по коду; верификация API платформы после ошибки | `navigate_symbol`, `get_call_graph`, `rename_symbol`, `get_diagnostics`, `get_code_actions`, `search_syntax_reference`, `getMembers`, `getMember`, `getConstructors` |
| tool-usage | `event-log-analysis` | Анализ ЖР для отладки | `search_event_log`, `navigate_symbol` |
| tool-usage | `tech-log-analysis` | Анализ ТЖ и управление жизненным циклом | `search_tech_log`, `logc_get_techlog_config`, `logc_save_techlog`, `logc_configure_techlog`, `logc_get_actual_log_timestamp`, `logc_restore_techlog`, `logc_disable_techlog`, `navigate_symbol` |
| tool-usage | `platform-data-core` | Метаданные, запросы, навигационные ссылки | `list_metadata_objects`, `get_metadata_structure`, `navigate_symbol`, `get_call_graph`, `validate_query`, `execute_query`, `parse_nav_link`, `get_nav_link` |
| tool-usage | `search-before-write` | Поиск перед написанием кода | `navigate_symbol`, `list_metadata_objects`, `get_metadata_structure`, `search_syntax_reference`, `get_type_info`, `search_ssl_functions`, `ask_ai_assistant` |
| tool-usage | `syntax-checking` | Проверка синтаксиса BSL | `check_syntax`, `get_diagnostics` |
| tool-usage | `test-execution` | TDD: сборка, запуск тестов, анализ | `run_tests`, `build_project`, `navigate_symbol`, `check_syntax` |
| tool-usage | `va-visual-check` | Визуальная проверка форм 1С через Vanessa/VA MCP; browser fallback по правилам навыка | VA MCP, `web-test-1c`/Playwright как fallback |
| **tool-usage / xml-gen** | | | |
| tool-usage | `xml-generation` | Обзор генерации XML-метаданных + CLI (validate, edit, replace-text) | — (CLI) |
| tool-usage | `epf-full` | Создание EPF/ERF, макеты объектов, BSP-регистрация | — (CLI) |
| tool-usage | `form-dsl` | JSON DSL для генерации форм | — (CLI) |
| tool-usage | `mxl-dsl` | JSON DSL для табличных документов | — (CLI) |
| tool-usage | `role-dsl` | JSON DSL для ролей и прав | — (CLI) |
| tool-usage | `skd-dsl` | JSON DSL для СКД-отчётов | — (CLI) |
| **tool-usage / review** | | | |
| tool-usage | `cross-provider-review` | Cross-family ревью (Claude↔Codex, sandbox, read-only) | CLI-адаптеры |
| tool-usage | `gemini-review` | Ревью через Gemini | — (внешний CLI) |
| **bsl-practices** | | | |
| bsl-practices | `coding-standards` | Стандарты кодирования 1С | — |
| bsl-practices | `error-handling` | Обработка ошибок, транзакции | — |
| bsl-practices | `form-patterns` | Паттерны программирования форм | — |
| bsl-practices | `form-visual-requirements` | Визуальные требования к формам | — |
| bsl-practices | `query-patterns` | Оптимизация запросов | — |
| bsl-practices | `ssl-patterns` | Паттерны использования БСП | — |
| **spec-writing** | | | |
| spec-writing | `spec-standard` | Структура спецификации | — |
| spec-writing | `task-breakdown` | Декомпозиция задач (linear / subagent режим) | — |
| **framework-meta** | | | |
| framework-meta | `1c-ai-agent-cli` | CLI фреймворка | — |
| framework-meta | `skill-creator` | Создание новых навыков | — |
| framework-meta | `skill-creator-ext` | Расширение навыков (1С-специфика) | — |
| framework-meta | `agent-development` | Разработка субагентов | — |
| framework-meta | `agent-development-ext` | Разработка субагентов (1С) | — |

**Пример рабочего сценария:** Программист реализует справочник «ПромоКоды» с формой. Агент: (1) через `search-before-write` ищет аналоги в конфигурации, (2) через `epf-full` / `form-dsl` генерирует XML-метаданные справочника и формы, (3) пишет BSL-код модуля по `coding-standards`, (4) через `syntax-checking` проверяет синтаксис, (5) через `test-execution` запускает YaxUnit-тесты, (6) через `va-visual-check` проверяет отображение формы, (7) через `cross-provider-review` получает второе мнение от opposite-family модели.

**Незакрытые зоны:**
- Работа с жизненным циклом ТЖ — покрыто навыком `tech-log-analysis`

---

### 3.5 Тестировщик / QA — ~75% покрытие агентом

**Зона ответственности.** Проверяет, что реализация соответствует спецификации — запускает тесты, проверяет UI, анализирует логи на ошибки, выполняет регрессионное тестирование. Последний рубеж перед production.

**Что делает человек:**
- Составляет тест-кейсы по спецификации
- Запускает автоматические и ручные тесты
- Проверяет UI/UX на соответствие макетам
- Анализирует логи на наличие ошибок и исключений
- Проводит регрессионное тестирование

**Что умеет агент:**

| Категория | Навык | Назначение | MCP Tools |
|-----------|-------|------------|-----------|
| tool-usage | `test-execution` | Сборка проекта и запуск YaxUnit-тестов | `run_tests`, `build_project`, `navigate_symbol`, `check_syntax` |
| tool-usage | `va-visual-check` | Визуальная проверка форм через Vanessa/VA MCP; browser fallback по правилам навыка | VA MCP, `web-test-1c`/Playwright как fallback |
| tool-usage | `syntax-checking` | Проверка синтаксиса BSL | `check_syntax`, `get_diagnostics` |
| tool-usage | `event-log-analysis` | Анализ ЖР на ошибки | `search_event_log`, `navigate_symbol` |
| tool-usage | `tech-log-analysis` | Анализ ТЖ на блокировки и исключения | `search_tech_log`, `navigate_symbol` |
| tool-usage | `code-navigation` | Трассировка кода при расследовании падений тестов | `navigate_symbol`, `get_call_graph`, `get_diagnostics`, `get_code_actions` |
| bsl-practices | `form-visual-requirements` | Чеклист визуальных требований к формам (используется как верификационный чеклист, а не руководство по разработке) | — |
| tool-usage | `cross-provider-review` | Cross-family ревью кода (Claude↔Codex) | CLI-адаптеры |
| tool-usage | `gemini-review` | Ревью кода через Gemini | — (внешний CLI) |

**Пример рабочего сценария:** QA проверяет реализацию промокодов. Агент: (1) через `test-execution` запускает YaxUnit-тесты модуля, (2) через `va-visual-check` открывает форму и проверяет элементы по чеклисту `form-visual-requirements`, (3) через `event-log-analysis` ищет ошибки в ЖР, через `tech-log-analysis` — блокировки и исключения в ТЖ, (4) через `cross-provider-review` инициирует финальное ревью.

**Незакрытые зоны:**
- Визуальная регрессия (сравнение скриншотов) — нет инструмента
- Интеграционное и E2E-тестирование — нет MCP-инструментов
- Тестирование производительности — нет профилировщика

---

## 4. Матрица навыков × роли

Маркеры: **●** основная роль, **◐** вспомогательная, пусто — не используется.

| # | Навык | Категория | БА¹ | ФА | СА | Прог. | QA |
|---|-------|-----------|:---:|:--:|:--:|:-----:|:--:|
| 1 | `code-navigation` | tool-usage | | ◐ | ● | ● | ◐ |
| 2 | `event-log-analysis` | tool-usage | | | ● | ● | ● |
| 3 | `tech-log-analysis` | tool-usage | | | ● | ● | ● |
| 4 | `platform-data-core` | tool-usage | ◐ | ● | ◐ | ● | |
| 7 | `search-before-write` | tool-usage | | ● | ● | ● | |
| 8 | `syntax-checking` | tool-usage | | | ● | ● | ● |
| 9 | `test-execution` | tool-usage | | | | ● | ● |
| 10 | `va-visual-check` | tool-usage | | | | ● | ● |
| 11 | `xml-generation` | tool-usage | | | | ● | |
| 12 | `epf-full` | tool-usage | | | | ● | |
| 14 | `form-dsl` | tool-usage | | | | ● | |
| 15 | `mxl-dsl` | tool-usage | | | | ● | |
| 16 | `role-dsl` | tool-usage | | | | ● | |
| 17 | `skd-dsl` | tool-usage | | | | ● | |
| 18 | `cross-provider-review` | tool-usage | | | ● | ● | ◐ |
| 19 | `gemini-review` | tool-usage | | | ● | ● | ◐ |
| 20 | `coding-standards` | bsl-practices | | | ● | ● | |
| 21 | `error-handling` | bsl-practices | | | ● | ● | |
| 22 | `form-patterns` | bsl-practices | | | ● | ● | |
| 23 | `form-visual-requirements` | bsl-practices | | ◐ | ◐ | ● | ● |
| 24 | `query-patterns` | bsl-practices | | | ● | ● | |
| 25 | `ssl-patterns` | bsl-practices | | | ● | ● | |
| 26 | `test-writing` | bsl-practices | | | | ● | ◐ |
| 27 | `spec-standard` | spec-writing | ● | ● | ◐ | ◐ | |
| 28 | `task-breakdown` | spec-writing | ● | ● | | ◐ | |
| 29 | `1c-ai-agent-cli` | framework-meta | | | | ● | |
| 31 | `skill-creator` | framework-meta | | | | ● | |
| 32 | `skill-creator-ext` | framework-meta | | | | ● | |
| 33 | `agent-development` | framework-meta | | | | ● | |
| 34 | `agent-development-ext` | framework-meta | | | | ● | |
| | **Итого навыков:** | | **4¹** | **8** | **16** | **34** | **9** |

> ¹ БА — входной контекст вне агентной цепочки; навыки доступны, но агентная автоматизация начинается с ФА.

---

## 5. Незакрытые зоны (Gap Analysis)

**Обозначения статусов:**
- 🔴 — Нет навыка (инструмент доступен, но не покрыт ни одним навыком)
- 🟡 — Частичное покрытие (инструмент есть, навык покрывает не полностью)
- 🟢 — Покрывается существующим навыком
- ⚪ — Низкий приоритет / системный инструмент (навык не требуется)

### 5.1 MCP-инструменты: полная инвентаризация

Все инструменты из MCP-серверов, зарегистрированных в `registry.yaml` и `mcp-inventory.md`, с указанием текущего статуса покрытия навыками.

| MCP-инструмент | Провайдер | Описание | Затронутые роли | Статус |
|----------------|-----------|----------|-----------------|--------|
| **Не требуют навыка** | | | | |
| `launch_app` | mcp-onec-test-runner | Запуск клиентов 1С | — | ⚪ YaxUnit запускает клиент автоматически через `run_tests`; интерактивная работа с толстым/тонким клиентом идёт через Vanessa/TestClient и профильные навыки |
| `explain_1c_syntax` | spring-mcp-1c-copilot | Объяснение конструкций BSL | — | ⚪ Современный агент справляется без инструмента |
| `check_1c_code` | spring-mcp-1c-copilot | AI-проверка кода через copilot (старая модель) | — | ⚪ Слабее `cross-provider-review`; ревью закрыто review-навыками |
| `range` | mcp-bsl-lsp-bridge | Анализ диапазона кода | Программист | 🟡 Инструмент доступен; навык не нужен — низкоуровневой LSP-операции достаточно |
| `hover` | mcp-bsl-lsp-bridge | Информация о символе при наведении | Программист | ⚪ Системный LSP, неявно через `code-navigation` |
| `definition` | mcp-bsl-lsp-bridge | Переход к определению | Программист | ⚪ Системный LSP, неявно через `code-navigation` |
| `selection_range` | mcp-bsl-lsp-bridge | Диапазон выделения | Программист | ⚪ Низкий приоритет |
| `prepare_rename` | mcp-bsl-lsp-bridge | Подготовка к переименованию | Программист | ⚪ Системный, часть `rename_symbol` |
| `call_hierarchy` | mcp-bsl-lsp-bridge | Иерархия вызовов | СА, Программист | ⚪ Покрывается `get_call_graph` |
| `did_change_watched_files` | mcp-bsl-lsp-bridge | Уведомление об изменении файлов | Программист | ⚪ Системный, не требует навыка |
| `lsp_status` | mcp-bsl-lsp-bridge | Статус подключения к LSP | Программист | ⚪ Системный, не требует навыка |
| **Покрыты существующими навыками** | | | | |
| `navigate_symbol` | lsp-bsl-bridge | Навигация по символам | Все роли | 🟢 `code-navigation`, `search-before-write`, `platform-data-core`, `event-log-analysis`, `tech-log-analysis`, `test-execution` |
| `get_call_graph` | lsp-bsl-bridge | Граф вызовов | СА, Программист | 🟢 `code-navigation`, `platform-data-core` |
| `get_diagnostics` | lsp-bsl-bridge | Диагностика (= `document_diagnostics` в LSP) | Программист, QA | 🟢 `syntax-checking`, `code-navigation` |
| `get_code_actions` | lsp-bsl-bridge | Быстрые исправления | Программист | 🟢 `code-navigation` |
| `rename_symbol` | lsp-bsl-bridge | Переименование символа | Программист | 🟢 `code-navigation` |
| `search_ssl_functions` | lsp-bsl-bridge | Поиск функций БСП | СА, Программист | 🟢 `search-before-write` |
| `check_syntax` | test-runner | Проверка синтаксиса (абстрагирует `check_syntax_edt`, `check_syntax_designer_modules`) | Программист, QA | 🟢 `syntax-checking`, `test-execution` |
| `run_tests` | test-runner | Запуск тестов (абстрагирует `run_all_tests`, `run_module_tests`) | Программист, QA | 🟢 `test-execution` |
| `build_project` | test-runner | Сборка проекта | Программист, QA | 🟢 `test-execution` |
| `dump_config` | test-runner | Выгрузка конфигурации | Программист | 🟢 `platform-data-core` |
| `list_metadata_objects` | 1c-mcp | Список объектов метаданных | ФА, СА, Программист | 🟢 `platform-data-core`, `search-before-write` |
| `get_metadata_structure` | 1c-mcp | Структура объекта метаданных | ФА, СА, Программист | 🟢 `platform-data-core`, `search-before-write` |
| `validate_query` | 1c-mcp | Валидация запроса | ФА, Программист | 🟢 `platform-data-core` |
| `execute_query` | 1c-mcp | Выполнение запроса | ФА, Программист | 🟢 `platform-data-core` |
| `parse_nav_link` | 1c-mcp | Парсинг навигационной ссылки | Программист | 🟢 `platform-data-core` |
| `get_nav_link` | 1c-mcp | Генерация навигационной ссылки | Программист | 🟢 `platform-data-core` |
| `search_syntax_reference` | 1c-platform-context | Поиск по справке синтаксиса (= `search` в MCP) | СА, Программист | 🟢 `search-before-write`, `code-navigation` (Сценарий 6) |
| `get_type_info` | 1c-platform-context | Информация о типе (= `info` в MCP) | СА, Программист | 🟢 `search-before-write` |
| `getMember` | mcp-bsl-platform-context | Получение метода/свойства типа | СА, Программист | 🟢 `code-navigation` (Сценарий 6: верификация API после ошибки) |
| `getMembers` | mcp-bsl-platform-context | Список методов и свойств типа | СА, Программист | 🟢 `code-navigation` (Сценарий 6: верификация API после ошибки) |
| `getConstructors` | mcp-bsl-platform-context | Конструкторы типа | СА, Программист | 🟢 `code-navigation` (Сценарий 6: верификация API после ошибки) |
| `ask_ai_assistant` | 1c-copilot-proxy | AI-консультации (= `ask_1c_ai` в MCP) | СА, Программист | 🟢 `search-before-write` |
| `search_event_log` | 1c-log-checker | Поиск в журнале регистрации | Программист, QA | 🟢 `event-log-analysis` |
| `search_tech_log` | 1c-log-checker | Поиск в технологическом журнале | Программист, QA | 🟢 `tech-log-analysis` |
| `logc_configure_techlog` | 1c-log-checker | Настройка ТЖ | Программист, QA | 🟢 `tech-log-analysis` |
| `logc_save_techlog` | 1c-log-checker | Сохранение конфигурации ТЖ | Программист, QA | 🟢 `tech-log-analysis` |
| `logc_restore_techlog` | 1c-log-checker | Восстановление конфигурации ТЖ | Программист, QA | 🟢 `tech-log-analysis` |
| `logc_disable_techlog` | 1c-log-checker | Отключение ТЖ | Программист, QA | 🟢 `tech-log-analysis` |
| `logc_get_techlog_config` | 1c-log-checker | Чтение конфигурации ТЖ | Программист, QA | 🟢 `tech-log-analysis` |
| `logc_get_actual_log_timestamp` | 1c-log-checker | Актуальная метка времени журнала | Программист, QA | 🟢 `tech-log-analysis` |
| `browser_navigate` | chrome-devtools | Навигация в браузере (= `navigate_page`) | Программист, QA | 🟢 `web-test-1c` / `playwright`; для 1C UI только browser-layer или fallback по `va-visual-check` |
| `browser_snapshot` | chrome-devtools | Снимок страницы (= `take_snapshot`) | Программист, QA | 🟢 `web-test-1c` / `playwright`; для 1C UI только browser-layer или fallback по `va-visual-check` |
| `browser_fill` | chrome-devtools | Заполнение полей (= `fill`) | Программист, QA | 🟢 `web-test-1c` / `playwright`; для 1C UI только browser-layer или fallback по `va-visual-check` |
| `browser_click` | chrome-devtools | Клик по элементу (= `click`) | Программист, QA | 🟢 `web-test-1c` / `playwright`; для 1C UI только browser-layer или fallback по `va-visual-check` |
| `browser_take_screenshot` | chrome-devtools | Скриншот (= `take_screenshot`) | Программист, QA | 🟢 `web-test-1c` / `playwright`; для 1C UI только browser-layer или fallback по `va-visual-check` |
| `browser_console_messages` | chrome-devtools | Сообщения консоли (= `list_console_messages`) | Программист, QA | 🟢 `web-test-1c` / `playwright`; для 1C UI только browser-layer или fallback по `va-visual-check` |
| `browser_wait_for` | chrome-devtools | Ожидание элемента (= `wait_for`) | Программист, QA | 🟢 `web-test-1c` / `playwright`; для 1C UI только browser-layer или fallback по `va-visual-check` |

> **Альтернативные MCP-провайдеры.** Помимо основных серверов, зарегистрированных в `registry.yaml`, существуют альтернативные провайдеры: **1c-batch** (сборка/выгрузка конфигурации — альтернатива `test-runner` для `build_project`/`dump_config`), **1c-mcp-tools** (метаданные/запросы — альтернатива `1c-mcp`), **1c_mcp** (прозрачный прокси с динамическими инструментами на стороне 1С — потенциальный путь для закрытия пробелов без создания новых MCP-серверов). Фреймворк выбрал конкретные провайдеры; альтернативы подключаются через `registry.yaml` при необходимости.

### 5.2 Процессные разрывы

Пробелы, не связанные с конкретным MCP-инструментом, но влияющие на полноту агентного процесса.

| Разрыв | Описание | Затронутые роли | Статус |
|--------|----------|-----------------|--------|
| Интеграция с трекерами | Нет MCP-сервера для Jira/YouTrack — ФА забирает задачу вручную | ФА | 🟢 Низкий приоритет, удобство а не необходимость |
| Моделирование процессов | Нет генератора BPMN/EPC-диаграмм | БА | 🔴 Нет инструмента |
| Визуализация архитектуры | Нет генератора C4/компонентных/ER-диаграмм | ФА, СА | 🔴 Нет инструмента |
| Визуальная регрессия | Нет сравнения скриншотов (pixel diff) | QA | 🟡 Можно реализовать поверх `va-visual-check` |
| Нагрузочное тестирование | Нет профилировщика и генератора нагрузки | СА, QA | 🔴 Нет инструмента |
| E2E-тестирование | Нет оркестратора сквозных сценариев | QA | 🔴 Нет инструмента |

---

## 6. Рекомендуемые новые навыки

| # | Имя навыка | Категория | Приоритет | Описание | MCP Tools |
|---|------------|-----------|-----------|----------|-----------|
| 1 | `visual-regression` | tool-usage | 🟢 Низкий | Сравнение скриншотов форм (pixel diff) для регрессионного тестирования UI | VA/browser screenshot + внешний diff |

**Рекомендуемый порядок реализации:** `visual-regression` — зависит от `va-visual-check`, требует внешнего инструмента сравнения скриншотов.

---

## 7. Структура каталога

```
framework/skills/tool-usage/
│
├── README.md                          ← этот файл
│
├── code-navigation/
│   └── SKILL.md                       # LSP-навигация: символы, вызовы, рефакторинг; верификация API платформы после ошибки
│
├── event-log-analysis/
│   └── SKILL.md                       # Журнал регистрации: поиск ошибок, аудит действий
│
├── tech-log-analysis/
│   └── SKILL.md                       # Технологический журнал: полный цикл save→configure→read→restore
│
├── platform-data-core/
│   ├── SKILL.md                       # Метаданные + запросы + навигационные ссылки (объединённый)
│   └── references/
│       └── query-syntax-cheatsheet.md # Справочник синтаксиса запросов и примеры
│
├── search-before-write/
│   └── SKILL.md                       # Обязательный поиск перед написанием кода
│
├── syntax-checking/
│   └── SKILL.md                       # Проверка синтаксиса BSL
│
├── test-execution/
│   └── SKILL.md                       # TDD: сборка + тесты YaxUnit
│
├── vanessa/
│   └── va-visual-check/
│       └── SKILL.md                   # Визуальная проверка форм 1С через VA MCP
│
├── xml-generation/
│   ├── xml-generation/
│   │   └── SKILL.md                   # Обзор JSON→XML генерации + CLI (validate, edit, replace-text)
│   ├── epf-full/
│   │   ├── SKILL.md                   # Полный цикл EPF/ERF: init, шаблоны, BSP-регистрация
│   │   └── references/
│   │       ├── epf-base.md            # init, add-form, add-template, add-attribute
│   │       ├── templates.md           # template add/remove/add-help для любых объектов
│   │       └── epf-bsp.md             # BSP-регистрация: СведенияОВнешнейОбработке + add-command
│   ├── form-dsl/
│   │   └── SKILL.md                   # JSON DSL для форм
│   ├── mxl-dsl/
│   │   └── SKILL.md                   # JSON DSL для табличных документов
│   ├── role-dsl/
│   │   └── SKILL.md                   # JSON DSL для ролей и прав
│   └── skd-dsl/
│       └── SKILL.md                   # JSON DSL для СКД-отчётов
│
└── cross-provider-review/
    ├── SKILL.md                       # Cross-family second opinion (Claude↔Codex)
    ├── references/
    │   └── review-prompt.md           # Шаблон reviewer prompt
    └── scripts/
        ├── claude_opus_review.py      # Адаптер для Claude/Opus reviewer
        └── codex_review.py            # Адаптер для Codex/GPT reviewer
```

**Соседние категории:**
- [`../bsl-practices/`](../bsl-practices/) — coding-standards, error-handling, form-patterns, form-visual-requirements, query-patterns, ssl-patterns, test-writing
- [`../spec-writing/`](../spec-writing/) — spec-standard, task-breakdown
- [`../framework-meta/`](../framework-meta/) — 1c-ai-agent-cli, skill-creator, skill-creator-ext, agent-development, agent-development-ext
