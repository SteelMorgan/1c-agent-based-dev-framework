# MCP-серверы и методология capability

## Доступ к информации о платформе
1. MCP: [BSL Platform Context](https://github.com/alkoleft/mcp-bsl-platform-context) — актуальная справка по синтаксису вашей версии платформы.
2. MCP: [1C.Напарник Прокси](https://github.com/SteelMorgan/spring-mcp-1c-copilot) — экспертные знания об устройстве платформы 1С и типовых решений. Работает на ваших ключах, в репозитории описано как получить доступ. (1С сейчас добавили к нему думающий режим, я доработал MCP, но не всегда удаётся получить финальный ответ. Пока не пойму, это багает Напарник или мой кремниевый друг не смог победить задачу в формате SSE Reasoning.)
3. [ИТС Scrapper](https://github.com/hawkxtreme/scraping_its) — сбор документов для базы знаний. Работает на вашем логине/пароле от ИТС.

## Оптимизация работы с кодовой базой
1. [LSP BSL Bridge](https://github.com/SteelMorgan/mcp-bsl-lsp-bridge) — современные модели сами могут «нагрепать» всё что угодно, но работа через LSP Bridge экономит токены и позволяет надёжно строить графы вызовов (агенты на таких операциях иногда лажают).

## Доступ «внутрь 1С» для агента
1. MCP Server 1C: [SteelMorgan/1c-mcp-tools](https://github.com/SteelMorgan/1c-mcp-tools) — выполнение запросов, получение метаданных и др. Самостоятельный проект: основной код заимствован у [Владимира Харина](https://github.com/vladimir-kharin/1c_mcp) и его форка [Вадима Ли (RooLee10)](https://github.com/RooLee10/1c-mcp-tools), затем существенно переработан под работу с `wt-mcp-adapter` (логика и архитектура изменены). <<КОД 1С>>
2. **Расширения 1С с собственным MCP-сервером** — теперь это отдельный слой архитектуры: расширение 1С реализует tools и подключается к менеджеру сессий по WebSocket (см. ниже). Агент видит эти tools на единой MCP-витрине менеджера, а не как отдельный сервер на порт.
3. MCP BSL Debugger: [liga-1c-command/1c-debug-mcp](https://github.com/liga-1c-command/1c-debug-mcp) — интерактивная отладка BSL через debug server 1С: targets, breakpoints, variables, step/continue. Установка и smoke-тест: [docs/info/mcp-bsl-debugger.md](./mcp-bsl-debugger.md).

## MCP-витрина менеджера сессий (v8-session-manager)

Репозиторий: [1c-neurofish/v8-session-manager](https://github.com/1c-neurofish/v8-session-manager).

Раньше для каждого 1С-клиента, желающего опубликовать tools агенту, нужен был отдельный HTTP-MCP-сервер на отдельный порт (legacy `runMcp`-режим). Это плохо масштабируется и требует ручной координации портов между клиентами.

Сейчас:
- 1С-клиент (`1cv8c`) с расширением `mcp_client` подключается к **менеджеру сессий** по WebSocket и публикует свои tools через `tools/publish`.
- Менеджер агрегирует tools всех подключённых клиентов и публикует их на одном HTTP-MCP-эндпоинте — это и есть витрина для агента.
- Запуск 1С-клиента в WS-режиме делает [v8-runner](https://github.com/SteelMorgan/v8-runner) (`launch mcp`, `launch mcp va`, `test yaxunit ...`, `test va ...`) — он сам выбирает транспорт `auto` / `ws` / `legacy`, пробуя достучаться до менеджера на `manager_url`.
- Маршрутизация в нужную сессию идёт по `session_id`, который менеджер инжектит в `input_schema` опубликованного tool. Один встроенный tool менеджера — `session_list` (read-only снимок реестра).

Что это даёт фреймворку:
- Один порт для агента, сколько бы клиентов 1С не было запущено.
- Soft-reconnect клиента по `client_uid`: переподключение → та же сессия → та же очередь.
- FIFO-порядок вызовов в одну сессию, round-robin между равнозначными.
- Возможность параллельно держать в реестре несколько ИБ — записи различаются по `infobase_name` и `ib_session_number`.

Полная схема набора (пять форков: пускач `v8-runner-rust`, транспортная ВК
`web-transport-addin`, адаптер `wt-mcp-adapter`, набор tools `1c-mcp-tools`, менеджер `v8sm`),
как прикладное расширение регистрирует клиентские и серверные tools и как они попадают
на витрину — в отдельном разборе с диаграммой:
[docs/info/mcp-ws-transport-toolset.md](./mcp-ws-transport-toolset.md).

Подробности — навыки [`framework/skills/tool-usage/v8-session-manager/`](../../framework/skills/tool-usage/v8-session-manager/) и [`framework/skills/tool-usage/v8-runner/`](../../framework/skills/tool-usage/v8-runner/).

## Loopback для агента
1. **v8-runner CLI** ([SteelMorgan/v8-runner](https://github.com/SteelMorgan/v8-runner)) — единый инструмент сборки/разборки ИБ, синтаксических проверок, прогона YaXUnit и Vanessa Automation, запуска клиентов 1С (включая WS-подключение к менеджеру сессий). Заменил отдельный MCP `mcp-onec-test-runner` для большинства сценариев.
2. MCP Log Checker: [SteelMorgan/1c-log-checker](https://github.com/SteelMorgan/1c-log-checker) — доступ к ЖР и ТЖ для обеспечения loopback. (ТЖ сильно не тестил, пока не было подходящих задач. С Event Log проблемы пофиксил, вроде работает стабильно.)
3. Сценарное тестирование закрывается через `v8-runner test va` + расширение `vanessa_test_client` на витрине менеджера (для интерактивного авторинга и debugging — `v8-runner launch mcp va`).

## Методология MCP + SKILL
Есть три слоя:
1. MCP Tool и его описание. Tool-ы реализуют возможности (Capability). Источник tool — либо отдельный MCP-сервер, либо 1С-расширение, опубликованное через витрину менеджера сессий (см. выше).
2. Навык, описывающий применение возможности (рабочий сценарий). Носит рекомендательный характер.
3. Правило, фиксирующее обязательные рамки применения навыка, то есть когда обязательно использовать возможность.

Старайтесь избегать дублирования информации между слоями (это путает агента и раздувает контекст).

При добавлении нового MCP внесите его в таблицу [framework/capabilities/registry.yaml](../../framework/capabilities/registry.yaml).
Перед тем как создавать навык, описывающий возможность вашего MCP, проверьте раздел [framework/skills/tool-usage](../../framework/skills/tool-usage) — вдруг такая возможность уже есть и описана (тогда достаточно зафиксировать в реестре ниже, какую возможность из фреймворка может реализовывать ваш MCP).

| MCP-сервер | Репозиторий | Возможность |
|------------|-------------|-------------|
| platform-context | [alkoleft/mcp-bsl-platform-context](https://github.com/alkoleft/mcp-bsl-platform-context) | search-before-write |
| copilot-proxy | [SteelMorgan/spring-mcp-1c-copilot](https://github.com/SteelMorgan/spring-mcp-1c-copilot) | search-before-write |
| log-checker | [SteelMorgan/1c-log-checker](https://github.com/SteelMorgan/1c-log-checker) | log-analysis |
| metadata-tools | [SteelMorgan/1c-mcp-tools](https://github.com/SteelMorgan/1c-mcp-tools) | platform-data-core |
| batch-ops | [vladimir-kharin/1c-batch](https://github.com/vladimir-kharin/1c-batch) | — |
| lsp-bridge | [SteelMorgan/mcp-bsl-lsp-bridge](https://github.com/SteelMorgan/mcp-bsl-lsp-bridge) | code-navigation |
| 1c-debug-mcp | [liga-1c-command/1c-debug-mcp](https://github.com/liga-1c-command/1c-debug-mcp) | debug_bsl_code |
| v8-session-manager | [1c-neurofish/v8-session-manager](https://github.com/1c-neurofish/v8-session-manager) | витрина для tools 1С-расширений (`vanessa_test_client`, `yaxunit_runner`, `v8_runner_client`) |
| ~~test-runner~~ | ~~[alkoleft/mcp-onec-test-runner](https://github.com/alkoleft/mcp-onec-test-runner)~~ | **упразднён** — заменён CLI [v8-runner](https://github.com/SteelMorgan/v8-runner) (build / syntax / tests / dump) |

## Навык или MCP?
Если то, что вы делаете, можно выполнить одним скриптом — лучше сделайте сразу навык, который опишет, как этим скриптом пользоваться. Это текущий глобальный стандарт от Anthropic.

Если у вас многокомпонентный механизм, который «что-то делает сам в себе, а потом даёт возможность агенту получить из него результат», то это MCP.

Например, мой log-checker — это несколько микросервисов, которые парсят логи и сохраняют их в ClickHouse, предоставляя возможность быстрых запросов по текстовым данным (каждое поле техлога — в своей колонке). Такое «одним скриптом» не получить, поэтому MCP имеет смысл.

---

Связанная информация:
- [docs/info/skills.md](./skills.md)
- [docs/info/mcp-ws-transport-toolset.md](./mcp-ws-transport-toolset.md)
- [docs/info/mcp-bsl-debugger.md](./mcp-bsl-debugger.md)
- [docs/info/ru-en-mirror.md](./ru-en-mirror.md)
- [framework/capabilities/registry.yaml](../../framework/capabilities/registry.yaml)
- [framework/skills/tool-usage](../../framework/skills/tool-usage)
- [framework/rules/tdd-policy/SKILL.md](../../framework/rules/tdd-policy/SKILL.md)
- [framework/rules/sdd-policy/SKILL.md](../../framework/rules/sdd-policy/SKILL.md)
- [framework/workflows/full-cycle/SKILL.md](../../framework/workflows/full-cycle/SKILL.md)
