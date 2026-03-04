# MCP-серверы и методология capability

## Доступ к информации о платформе
1. MCP: [BSL Platform Context](https://github.com/alkoleft/mcp-bsl-platform-context) — актуальная справка по синтаксису вашей версии платформы.
2. MCP: [1C.Напарник Прокси](https://github.com/SteelMorgan/spring-mcp-1c-copilot) — экспертные знания об устройстве платформы 1С и типовых решений. Работает на ваших ключах, в репозитории описано как получить доступ. (1С сейчас добавили к нему думающий режим, я доработал MCP, но не всегда удаётся получить финальный ответ. Пока не пойму, это багает Напарник или мой кремниевый друг не смог победить задачу в формате SSE Reasoning.)
3. [ИТС Scrapper](https://github.com/hawkxtreme/scraping_its) — сбор документов для базы знаний. Работает на вашем логине/пароле от ИТС.

## Оптимизация работы с кодовой базой
1. [LSP BSL Bridge](https://github.com/SteelMorgan/mcp-bsl-lsp-bridge) — современные модели сами могут «нагрепать» всё что угодно, но работа через LSP Bridge экономит токены и позволяет надёжно строить графы вызовов (агенты на таких операциях иногда лажают).

## Доступ «внутрь 1С» для агента
1. MCP Server 1C: [RooLee10/1c-mcp-tools](https://github.com/RooLee10/1c-mcp-tools) — выполнение запросов, получение метаданных и др. <<КОД 1С>>

## Loopback для агента
1. MCP YaxUnit Test Runner: [alkoleft/mcp-onec-test-runner](https://github.com/alkoleft/mcp-onec-test-runner) — теперь «дармовые» юнит-тесты + автоматизация сборки/разборки базы.
2. MCP Log Checker: [SteelMorgan/1c-log-checker](https://github.com/SteelMorgan/1c-log-checker) — доступ к ЖР и ТЖ для обеспечения loopback. (ТЖ сильно не тестил, пока не было подходящих задач. С Event Log проблемы пофиксил, вроде работает стабильно.)
3. Здесь не хватает инструмента для сценарного тестирования.

## Методология MCP + SKILL
Есть три слоя:
1. MCP Tool и его описание. Tool-ы реализуют возможности (Capability).
2. Навык, описывающий применение возможности (рабочий сценарий). Носит рекомендательный характер.
3. Правило, фиксирующее обязательные рамки применения навыка, то есть когда обязательно использовать возможность.

Старайтесь избегать дублирования информации между слоями (это путает агента и раздувает контекст).

При добавлении нового MCP внесите его в таблицу [framework/capabilities/registry.yaml](../../framework/capabilities/registry.yaml).
Перед тем как создавать навык, описывающий возможность вашего MCP, проверьте раздел [framework/skills/tool-usage](../../framework/skills/tool-usage) — вдруг такая возможность уже есть и описана (тогда достаточно зафиксировать в реестре ниже, какую возможность из фреймворка может реализовывать ваш MCP).

| MCP-сервер | Репозиторий | Возможность |
|------------|-------------|-------------|
| platform-context | [alkoleft/mcp-bsl-platform-context](https://github.com/alkoleft/mcp-bsl-platform-context) | search-before-write |
| copilot-proxy | [SteelMorgan/spring-mcp-1c-copilot](https://github.com/SteelMorgan/spring-mcp-1c-copilot) | search-before-write |
| test-runner | [alkoleft/mcp-onec-test-runner](https://github.com/alkoleft/mcp-onec-test-runner) | syntax-checking, test-execution |
| log-checker | [SteelMorgan/1c-log-checker](https://github.com/SteelMorgan/1c-log-checker) | log-analysis |
| metadata-tools | [RooLee10/1c-mcp-tools](https://github.com/RooLee10/1c-mcp-tools) | metadata-discovery |
| batch-ops | [vladimir-kharin/1c-batch](https://github.com/vladimir-kharin/1c-batch) | — |
| lsp-bridge | [SteelMorgan/mcp-bsl-lsp-bridge](https://github.com/SteelMorgan/mcp-bsl-lsp-bridge) | code-navigation |

## Навык или MCP?
Если то, что вы делаете, можно выполнить одним скриптом — лучше сделайте сразу навык, который опишет, как этим скриптом пользоваться. Это текущий глобальный стандарт от Anthropic.

Если у вас многокомпонентный механизм, который «что-то делает сам в себе, а потом даёт возможность агенту получить из него результат», то это MCP.

Например, мой log-checker — это несколько микросервисов, которые парсят логи и сохраняют их в ClickHouse, предоставляя возможность быстрых запросов по текстовым данным (каждое поле техлога — в своей колонке). Такое «одним скриптом» не получить, поэтому MCP имеет смысл.

---

Связанная информация:
- [docs/info/skills.md](./skills.md)
- [docs/info/ru-en-mirror.md](./ru-en-mirror.md)
- [framework/capabilities/registry.yaml](../../framework/capabilities/registry.yaml)
- [framework/skills/tool-usage](../../framework/skills/tool-usage)
- [framework/rules/mandatory-tools.md](../../framework/rules/mandatory-tools.md)
- [framework/rules/tdd-policy.md](../../framework/rules/tdd-policy.md)
- [framework/rules/sdd-policy.md](../../framework/rules/sdd-policy.md)
- [framework/workflows/full-cycle.md](../../framework/workflows/full-cycle.md)