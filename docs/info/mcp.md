## Доступ информации о платформе
1. MCP: BSL Platform Context (https://github.com/alkoleft/mcp-bsl-platform-context) - Актуальная справка по синтаксису вашей версии платформы
2. MCP: 1C.Напарник Прокси (https://github.com/SteelMorgan/spring-mcp-1c-copilot) - Экспертные знания об устройстве платформы 1С и типовых решений. Работает на ваших ключах, в репо описано как получить. (1С сейчас приделали к нему думающий режим, я доработал MCP, но не всегда удается получить финальный отет. Пока не пойму это багает Напарник или мой кремниевый друг что-то победить не смог в формате SSE Reasoning)
3. ИТС Scrapper (https://github.com/hawkxtreme/scraping_its) - Сбор документов для базы знаний. Работает на вашем логин/пароль от ИТС.


## Оптимизация работы с кодовой базой
1. Lsp-bsl-bridge (https://github.com/SteelMorgan/mcp-bsl-lsp-bridge) - современные модельки сами могут "нагрепать" всё, что угодно, НО работа через LSP-Bridge экономит токены и позволяет надежно строить графы вызовов (агенты на таких операциях бывает лажают)


## Доступ "внутрь 1С" для Агента
1. MCP-Server 1C (https://github.com/RooLee10/1c-mcp-tools) - Выполнение запросов, получение метаданных и др. <<КОД 1С>>


## LoopBack для Агента
1. MCP YaxUnit Test runner (https://github.com/alkoleft/mcp-onec-test-runner) - Теперь «дармовые» Юнит-тесты + автоматизация сборки-разборки базы
2. MCP Log Checker (https://github.com/SteelMorgan/1c-log-checker) - Доступ к ЖР и ТЖ для обеспечения Loopback. (ТЖ сильно не тестил, пока не было подходящих задач. с Event log проблемы пофиксил, вроде работает стабильно)
3. Здесь не хватает инструмента для сценарного тестирования


## Методология MCP + SKILL
Есть три слоя: 
1. MCP Tool и его описание. Tool-ы реализуют Возможности (Capability)
2. Навык описывающий применение Возможности (рабочий сценарий). Носит рекомендательный характер.
3. Правило, фиксирующие обязательные рамки применения навыка, т.к. когда Обязательно использовать Возможность.
Старайтесь избегать дублирования информации в слоях (путает агента + избыточный контекст)

При добавлении нового MCP внесите его в таблицу framework/capabilities/registry.yaml
Перед тем, как создавать Навык, описывающий Возможность вашего MCP - проверьте раздел framework/skills/tool-usage, вдруг такая Возможность уже есть и описана (тогда вам достаточно зафиксировать в реестре ниже какую Возможность из фреймворка Может реализовывать ваш MCP)

| MCP-сервер | Репозиторий | Возможность |
|------------|-------------|-------|
| platform-context | [alkoleft/mcp-bsl-platform-context](https://github.com/alkoleft/mcp-bsl-platform-context) | search-before-write |
| copilot-proxy | [SteelMorgan/spring-mcp-1c-copilot](https://github.com/SteelMorgan/spring-mcp-1c-copilot) | search-before-write |
| test-runner | [alkoleft/mcp-onec-test-runner](https://github.com/alkoleft/mcp-onec-test-runner) | syntax-checking, test-execution |
| log-checker | [SteelMorgan/1c-log-checker](https://github.com/SteelMorgan/1c-log-checker) | log-analysis |
| metadata-tools | [RooLee10/1c-mcp-tools](https://github.com/RooLee10/1c-mcp-tools) | metadata-discovery |
| batch-ops | [vladimir-kharin/1c-batch](https://github.com/vladimir-kharin/1c-batch) | — |
| lsp-bridge | mcp-bsl-lsp-bridge | code-navigation |

## Навык или MCP?
Если то, что вы делаете можно выполнить одним скриптом - лучше сделайте сразу Навык, который опишет как вашим скриптом пользоваться. Это текущий глобальный стандарт от Антропиков.
Если у вас многокомпонентный механизм, который "что-то делает сам в себе, а потом даёт возможность Агенту получить из него результат", то это MCP.
Например, мой log-checker - это несколько микросервисов, которые парсят логи и сохраняют их в Кликхаус, предоставляя возможность быстрых запросов по текстовым данным (каждое поле тех.лога в свое колонке). Такое "одним скриптом" не получить, поэтому MCP имеет смысл.
