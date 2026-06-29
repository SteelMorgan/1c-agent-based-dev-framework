---
name: v8-session-manager
description: "Session manager 1C: запуск, клиенты, session_list, MCP"
provides_capabilities:
  # Встроенные tools менеджера — доступны всегда, пока поднят.
  - session_list
  - tools_cache_reset
  # Tools, которые менеджер проксирует от подключённых 1С-клиентов.
  # ВНИМАНИЕ: их имена в tools/list читаются из persistent tools-cache
  # (ADR-0035) — наличие имени НЕ гарантирует доступность вызова.
  # Без live-сессии нужного kind вызов вернёт MCP tool error
  # `isError:true, _meta.error_code="no_live_session"`.
  # client_mcp / system:
  - infobase_info
  - system_spawn_1c_client
  - system_kill_pid
  - timer
  # test_client (UI-управление):
  - test_client_start
  - test_client_stop
  - ui_find
  - ui_open_form
  - ui_activate
  - ui_click
  - ui_close
  - ui_input
  - ui_select
  - ui_select_row
  - ui_get_value
  - ui_get_cell_value
  - ui_get_table_rows
  - ui_wait_for
---

# v8-session-manager

Тонкий MCP-агрегатор: принимает WS-подключения от 1С-клиентов и публикует их MCP-tools на едином HTTP-эндпоинте для AI-агента.

## Что даёт сам менеджер

| Возможность | Источник |
|---|---|
| Встроенный tool — `session_list` (read-only снимок реестра) | менеджер |
| Встроенный tool — `tools_cache_reset` (полный сброс или по `config_id`) | менеджер (ADR-0035) |
| Витрина проксированных tools от подключённых клиентов | расширения 1С |
| Persistent кеш витрины (`workPath/tools_cache.json`, TTL 5d) | менеджер (ADR-0035) |
| Маршрутизация вызова в нужную сессию по `session_id` | менеджер |
| Soft-reconnect клиента по `client_uid` | менеджер |
| FIFO-порядок вызовов в одну сессию | менеджер |

Всё остальное (доменные tools — описание форм, запуск тестов, навигация и т.п.) добавляется **расширениями 1С**, не менеджером. Под каждое расширение — отдельный skill.

## Кеш проксированных tools (ADR-0035) — ключевой момент

`tools/list` менеджера читается из **persistent кеша на диске**, а не только из живых WS-сессий. Следствия для агента:

- **Имя tool в `tools/list` ≠ доступность вызова.** Кеш переживает disconnect клиента и рестарт менеджера — имя остаётся на витрине, но вызов без live-сессии вернёт MCP tool error `isError:true, _meta.error_code="no_live_session"`. Это не баг, это контракт.
- **Зачем так:** часть MCP-харнесов (в частности Claude Code) нестабильно реагирует на `notifications/tools/list_changed`. Persistent-кеш снимает зависимость от стабильной обработки нотификации.
- **Когда нужен `tools_cache_reset`:** когда tool осознанно удалён из расширения и больше не вернётся (или конфигурация снята полностью). Иначе он будет висеть до истечения TTL (по дефолту 5 суток с момента последнего `session.register`). Полный сброс — без аргументов; точечный — `{"config_id": "<id>"}` (берётся из `session_list[*].config_id`).
- **Что НЕ делает кеш:** не запускает 1С, не воспроизводит ответ tool, не подменяет live-сессию. Только хранит имена и `inputSchema`.

Подробности — `references/sessions-and-tools.md` § «Persistent кеш и `tools_cache_reset`».

## Диагностика UI MCP-сессий

Для клиентских UI-tools (`open_form`, `click`, `input`, `get_value`, `get_table_rows`, `test_client_start`) сначала докажи, что есть живой 1С-клиент, а не только запись в кешированной витрине.

Минимальный порядок:

1. Вызови `session_list`.
2. Найди live-сессию нужной ИБ: `state=active`, `disconnected_secs_ago=null`, `infobase_name=<нужная ИБ>`.
3. Для обычного UI MCP через платформенный тест-клиент нужна управляющая сессия `kind=1c-client`; для Vanessa нужны `kind=vanessa_test_client` и VA-tools сверх базового набора.
4. Если live-сессий несколько, всегда передавай `session_id` в каждый proxied tool call.
5. Перед длинным UI-сценарием проверь простой вызов (`infobase_info`) и `inflight=0`.

Для UI/UX-приёмки 1C-форм основной визуальный путь описан в `va-visual-check`. Базовая цепочка через Vanessa/TestClient:

1. Убедись по `session_list`, что VA manager живой: `kind=vanessa_test_client`, `state=active`, `tools` содержит VA-инструменты, `inflight=0`.
2. Запусти или подключи тест-клиент через VA tool `connect_test_client` с нужным профилем.
3. Проверь, что VA вернула реальный PID тест-клиента, а не `0`/пусто.
4. Получи окна через `get_window_list_os`.
5. Сними PNG через `get_window_screenshot_os`; Linux/Xvfb-рецепт для чёрного PNG и fallback-условия см. в `va-visual-check`.
6. Проверь, что PNG не пустой и не одноцветный/чёрный.

`tools/list` не доказывает готовность этой цепочки: список может быть из persistent cache. Доказательство — live-сессия + успешный smoke `connect_test_client -> get_window_list_os -> get_window_screenshot_os`.

Если proxied вызов зависает или `inflight` остаётся больше нуля:

- для формы тест-клиента сначала применяй `va-visual-check`; если нужен fallback, фиксируй выполненные VA-шаги, причину и остаточный риск;
- проверь `/tmp/mcp-client.log` или проектный лог client_mcp: пришёл ли `MCP_TOOL_CALL`, зарегистрировалась ли WS-сессия, нет ли ошибки платформенного типа;
- не сбрасывай `tools_cache_reset` как первое действие: кеш не блокирует live-вызовы и не лечит зависший клиент;
- если клиент запущен неправильным режимом, заверши только свой сохранённый PID и перезапусти его правильной командой через `v8-runner`.

Для цепочки запуска `1c-client` + `/TESTMANAGER` + отдельный `/TESTCLIENT` см. навык `v8-runner`, раздел «UI MCP через платформенный тест-клиент».

## Границы

Менеджер **не**:
- запускает 1С-клиентов (это работа внешнего оркестратора, типично — `v8-runner`);
- хранит состояние между перезапусками (реестр in-memory);
- содержит бизнес-логику (только транспорт + маршрутизация);
- управляет ИБ.

## Routing задач

| Задача | Reference |
|---|---|
| Что делает каждый слой стека (addin → devkit → BSL → менеджер → AI) | `references/architecture.md` |
| Поднять менеджер, подключить 1С-клиента | `references/bootstrap.md` |
| Прочитать `session_list`, вызвать tool, понять почему он отсутствует | `references/sessions-and-tools.md` |
| Добавить новый tool в расширение 1С | `references/extending-tools.md` |
| Менеджер не стартует / клиент не виден / tool скрыт / вызов падает | `references/troubleshooting.md` |

## Guardrails (hard)

1. **Не править исходники менеджера** (`src/`, `Cargo.toml`, `systemd/`, `etc/`, `spec/`, ADR в `docs/decisions/`) — это upstream-репозиторий. Все изменения уровня менеджера согласуются отдельной задачей.
2. **Не создавать и не менять MCP-tools без прямого разрешения пользователя.** Tools живут в расширениях 1С (`exts/<extension>/`); правка/добавление = изменение публичного контракта.
3. **Не тащить бизнес-логику в менеджер.** Если задача требует «менеджер должен делать X» — это сигнал, что X относится либо к расширению, либо к оркестратору запуска.
4. **Не пытаться запустить 1С-клиента через менеджер.** Менеджер только принимает входящее WS-подключение. Запуск 1С — задача `v8-runner`.
5. **Перед сборкой/перезапуском клиента — спросить пользователя.** Любая операция, меняющая состояние проекта (билд, рестарт), требует подтверждения.
