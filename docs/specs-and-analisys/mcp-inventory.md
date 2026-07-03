# Инвентаризация MCP-инструментов фреймворка 1C Agent-Based Dev

Полный перечень MCP-серверов и инструментов, доступных в рамках фреймворка разработки 1C на базе агентов.

---

## Сводная таблица MCP-серверов

| Сервер | Репозиторий | Кол-во инструментов | Категория |
|--------|-------------|---------------------|-----------|
| mcp-bsl-platform-context | [alkoleft/mcp-bsl-platform-context](https://github.com/alkoleft/mcp-bsl-platform-context) | 5 | API платформы 1С |
| spring-mcp-1c-copilot | [SteelMorgan/spring-mcp-1c-copilot](https://github.com/SteelMorgan/spring-mcp-1c-copilot) | 3 | AI-ассистент, синтаксис, проверка кода |
| mcp-onec-test-runner | [alkoleft/mcp-onec-test-runner](https://github.com/alkoleft/mcp-onec-test-runner) | 8 | Тесты, сборка, клиенты, проверка |
| 1c-log-checker | [SteelMorgan/1c-log-checker](https://github.com/SteelMorgan/1c-log-checker) | 8 | Журнал регистрации, технологический журнал |
| 1c-mcp-tools | [SteelMorgan/1c-mcp-tools](https://github.com/SteelMorgan/1c-mcp-tools) | динамически | Метаданные, запросы, гиперссылки + прокси с tools на стороне 1С (бывш. `1c_mcp`) |
| 1c-batch | [vladimir-kharin/1c-batch](https://github.com/vladimir-kharin/1c-batch) | — | Сборка, выгрузка конфигурации |
| mcp-bsl-lsp-bridge | (внутренний) | ~12 | LSP/BSL: навигация, анализ, диагностика |
| ~~1c_mcp~~ | → объединён в `1c-mcp-tools` | — | см. строку `1c-mcp-tools` |

---

## Раздел 1: mcp-bsl-platform-context

**Автор:** alkoleft  
**URL:** https://github.com/alkoleft/mcp-bsl-platform-context

Работа с API платформы 1С: поиск, информация о типах, методах и свойствах.

### Инструменты

| Инструмент | Описание |
|------------|----------|
| `search` | Поиск по API платформы 1С по строке запроса. Позволяет находить типы, методы, свойства по ключевым словам. |
| `info` | Детальная информация об элементе API: тип, методы, свойства. |
| `getMember` | Получение конкретного метода или свойства типа. |
| `getMembers` | Получение полного списка методов и свойств типа. |
| `getConstructors` | Получение конструкторов типа. |

### Маппинг на возможности фреймворка

- **search_syntax_reference** — поиск по справке синтаксиса платформы 1С

---

## Раздел 2: spring-mcp-1c-copilot

**Автор:** steelmorgan  
**URL:** https://github.com/SteelMorgan/spring-mcp-1c-copilot

AI-ассистент для вопросов по 1С, объяснение синтаксиса и проверка качества кода.

### Инструменты

| Инструмент | Описание |
|------------|----------|
| `ask_1c_ai` | Задать вопрос AI-ассистенту по 1С: общие вопросы, лучшие практики, рекомендации. |
| `explain_1c_syntax` | Объяснение конструкции синтаксиса BSL. |
| `check_1c_code` | Проверка качества кода: синтаксис, логика, производительность. |

### Маппинг на возможности фреймворка

- **ask_ai** — вопросы и best practices
- **explain_syntax** — объяснение синтаксиса BSL
- **check_code_quality** — проверка качества кода

---

## Раздел 3: mcp-onec-test-runner

**Автор:** alkoleft  
**URL:** https://github.com/alkoleft/mcp-onec-test-runner

Запуск тестов YaxUnit, сборка проекта, экспорт конфигурации, запуск клиентов, проверка синтаксиса.

### Инструменты

| Инструмент | Описание |
|------------|----------|
| `run_all_tests` | Запуск всех тестов YaxUnit в проекте. |
| `run_module_tests` | Запуск тестов для конкретного модуля. |
| `build_project` | Сборка проекта (EDT). |
| `dump_config` | Экспорт конфигурации. Режимы: FULL, INCREMENTAL, PARTIAL. |
| `launch_app` | Запуск приложений: Designer, Thin/Thick клиент. |
| `check_syntax_edt` | Проверка синтаксиса через EDT validate. |
| `check_syntax_designer_config` | Проверка конфигурации через Проверка конфигурации (CheckConfig) в Конфигураторе. |
| `check_syntax_designer_modules` | Проверка модулей через Проверка модулей (CheckModules) в Конфигураторе. |

### Маппинг на возможности фреймворка

- **run_tests** — запуск YaxUnit тестов
- **build_project** — сборка проекта
- **dump_config** — выгрузка конфигурации
- **launch_client** — запуск клиентов
- **check_syntax** — проверка синтаксиса (EDT и Designer)

---

## Раздел 4: 1c-log-checker

**Автор:** steelmorgan  
**URL:** https://github.com/SteelMorgan/1c-log-checker

Работа с журналом регистрации (ЖР) и технологическим журналом (ТЖ).

### Инструменты

| Инструмент | Описание |
|------------|----------|
| `logc_get_event_log` | Получение журнала регистрации (ЖР). |
| `logc_get_tech_log` | Получение технологического журнала (ТЖ). |
| `logc_configure_techlog` | Настройка технологического журнала. |
| `logc_save_techlog` | Сохранение конфигурации технологического журнала. |
| `logc_restore_techlog` | Восстановление конфигурации технологического журнала. |
| `logc_disable_techlog` | Отключение технологического журнала. |
| `logc_get_techlog_config` | Чтение конфигурации технологического журнала. |
| `logc_get_actual_log_timestamp` | Получение актуальной метки времени журнала. |

### Маппинг на возможности фреймворка

- **event_log** — работа с ЖР
- **tech_log** — работа с ТЖ (чтение, настройка, включение/отключение)

---

## Раздел 5: 1c-mcp-tools

**Репозиторий:** https://github.com/SteelMorgan/1c-mcp-tools — самостоятельный проект.
**Заимствование:** основной код у [Владимира Харина](https://github.com/vladimir-kharin/1c_mcp) и форка [Вадима Ли (RooLee10)](https://github.com/RooLee10/1c-mcp-tools); существенно переработан под работу с `wt-mcp-adapter`.

Работа с метаданными, запросами и гиперссылками. Включает и функциональность бывшего «собственного прокси» (ранее Раздел 8): инструменты определяются на стороне 1С и динамически расширяются.

### Функциональность

- Метаданные конфигурации
- Запросы
- Гиперссылки

*Конкретный список инструментов определяется исходным кодом репозитория.*

---

## Раздел 6: 1c-batch

**Автор:** vladimir-kharin  
**URL:** https://github.com/vladimir-kharin/1c-batch

Операции сборки и выгрузки конфигурации.

### Функциональность

- Сборка конфигурации
- Выгрузка (разбор) конфигурации

---

## Раздел 7: mcp-bsl-lsp-bridge

**Описание:** Внутренний мост к BSL Language Server. Обеспечивает LSP-функции для BSL-кода.

### Инструменты (~12 активных)

| Инструмент | Описание |
|------------|----------|
| `symbol_explore` | Навигация по символам. |
| `project_analysis` | Анализ проекта. |
| `hover` | Информация о символе при наведении. |
| `definition` | Переход к определению. |
| `selection_range` | Диапазон выделения. |
| `code_actions` | Быстрые исправления (quick fix). |
| `prepare_rename` | Подготовка к переименованию символа. |
| `rename` | Переименование символа. |
| `range` | Анализ диапазона. |
| `call_hierarchy` | Иерархия вызовов. |
| `call_graph` | Граф вызовов. |
| `document_diagnostics` | Диагностика файла. |
| `did_change_watched_files` | Уведомление об изменении отслеживаемых файлов. |
| `lsp_status` | Статус подключения к LSP. |

### Маппинг на возможности фреймворка

- **navigate_symbols** — навигация по коду
- **go_to_definition** — переход к определению
- **rename_symbol** — переименование
- **code_actions** — автоматические исправления
- **call_analysis** — иерархия и граф вызовов
- **diagnostics** — диагностика кода
- **project_analysis** — анализ проекта

---

## Раздел 8: 1c_mcp → объединён в 1c-mcp-tools

Ранее описывался как отдельный «прозрачный прокси» (`1c_mcp`). После переработки это не самостоятельный проект, а часть `SteelMorgan/1c-mcp-tools` (см. Раздел 5): прозрачный прокси с инструментами на стороне 1С и динамическим расширением без изменения MCP-клиента.

---

## Матрица совместимости: возможность × провайдер

| Возможность | mcp-bsl-platform-context | spring-mcp-1c-copilot | mcp-onec-test-runner | 1c-log-checker | 1c-mcp-tools | 1c-batch | mcp-bsl-lsp-bridge | 1c_mcp |
|-------------|:------------------------:|:---------------------:|:--------------------:|:---------------:|:------------:|:--------:|:------------------:|:------:|
| search_syntax_reference | ✓ | | | | | | | |
| ask_ai | | ✓ | | | | | | |
| explain_syntax | | ✓ | | | | | | |
| check_code_quality | | ✓ | | | | | | |
| run_tests | | | ✓ | | | | | |
| build_project | | | ✓ | | | ✓ | | |
| dump_config | | | ✓ | | | ✓ | | |
| launch_client | | | ✓ | | | | | |
| check_syntax | | | ✓ | | | | | |
| event_log | | | | ✓ | | | | |
| tech_log | | | | ✓ | | | | |
| metadata | | | | | ✓ | | | ✓* |
| navigate_symbols | | | | | | | ✓ | |
| go_to_definition | | | | | | | ✓ | |
| rename_symbol | | | | | | | ✓ | |
| code_actions | | | | | | | ✓ | |
| call_analysis | | | | | | | ✓ | |
| diagnostics | | | | | | | ✓ | |
| project_analysis | | | | | | | ✓ | |

\* В зависимости от настроек прокси

---

## Краткие рекомендации по выбору провайдера

| Задача | Рекомендуемый провайдер |
|--------|-------------------------|
| Поиск по API платформы | mcp-bsl-platform-context |
| Вопросы по best practices | spring-mcp-1c-copilot |
| Проверка синтаксиса/кода | spring-mcp-1c-copilot, mcp-onec-test-runner |
| Запуск тестов | mcp-onec-test-runner |
| Сборка и выгрузка | mcp-onec-test-runner, 1c-batch |
| Работа с журналами | 1c-log-checker |
| Навигация по коду | mcp-bsl-lsp-bridge |
| Метаданные, запросы | 1c-mcp-tools (вкл. бывш. 1c_mcp) |
