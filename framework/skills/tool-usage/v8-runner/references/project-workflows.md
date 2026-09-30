# Сценарии проекта

Используй эти потоки по намерению пользователя. Не разделяй workflow только из-за того, что исходники — Designer или EDT; многие команды разделяют один и тот же жизненный цикл и отличаются только `format`, `builder` или доступностью инструментов.

Точные правила поддержки читай в `config-and-backends.md` вместе с этим файлом.

## Инициализация

Создай дефолтный конфиг, если у проекта нет `v8project.yaml`:

```bash
v8-runner config init
```

Выбирай более узкую команду init только когда форма проекта известна:

```bash
v8-runner config init --connection "File=build/ib"
v8-runner config init --format edt
v8-runner config init --builder IBCMD
```

Инициализируй сгенерированное runtime-состояние только когда нужно создать файловую ИБ или EDT-воркспейс:

```bash
v8-runner init
```

## Build

Применить Git-видимые изменения исходников к настроенному runtime-состоянию:

```bash
v8-runner build
```

Используй полный rebuild после переключения веток, rebase, широких перемещений объектов или подозрительного инкрементального состояния:

```bash
v8-runner build --full-rebuild
```

`build` — общий сценарий. Для EDT-проектов он может экспортировать EDT-исходники в Designer-файлы перед применением через настроенный backend. Для Designer-проектов он применяет Designer-исходники напрямую через настроенный backend.

Если настроен `tools.wt_mcp_adapter.extension`, `build` также готовит это tool-расширение после стадии source-set'ов проекта, в том числе для узких сборок с `--source-set`. Tool-расширения на основе исходников используют собственное состояние change-detection и пропускаются, если ничего не изменилось; используй `build --full-rebuild`, чтобы принудительно обновить. Не добавляй tool-расширение как source-set проекта и не выбирай его через `--source-set`.

### Контроль результата build

`v8-runner build` может занимать минуты. Для длительных прогонов используй инструмент Monitor:

1. Запусти в фоне (`Bash run_in_background: true`), перенаправь stdout в файл.
2. Подпишись через **Monitor** с фильтром `ERROR:|Failed|error:` — уведомление придёт при первом совпадении.
3. Завершай ожидание: процесс завершился ИЛИ в stdout появился `ERROR:` / `Failed` / явный признак успеха.
4. После завершения: код возврата 0 = успех; иначе — читать stdout на ошибку.

## Syntax

Выбирай синтаксические проверки исходя из возможностей конфига, а не из предположений по имени репозитория.

Проверки модулей Designer:

```bash
v8-runner build
v8-runner syntax designer-modules --server --thin-client
```

Проверки конфигурации Designer:

```bash
v8-runner build
v8-runner syntax designer-config
```

Проверки EDT:

```bash
v8-runner build
v8-runner syntax edt
```

Если команда syntax недоступна для текущего `format` или `builder`, сообщи об ограничении конфига вместо того, чтобы выдумывать сырые команды платформы.

## Dump

Используй dump, когда желаемый источник истины — текущее состояние ИБ.

Перед dump'ом изучи текущие изменения в Git:

```bash
git status --short
```

Инкрементальный dump:

```bash
v8-runner dump --mode incremental
```

Объектный partial dump, когда backend это поддерживает:

```bash
v8-runner dump --mode partial --object <TYPE:NAME>
```

После dump'а запусти `git diff` и сообщи затронутые файлы.

## Extensions

Используй `extensions`, когда нужно синхронизировать свойства расширений без более широкого шага восстановления.

Не подменяй специфическую для расширений синхронизацию полным rebuild, если пользователь не просит восстановления или более узкая команда не падает по релевантной причине.

```bash
v8-runner extensions
v8-runner extensions --name <SOURCE_SET>
```

## Launch

Предпочитай команды launch у runner'а, а не сборку сырых `1cv8`-команд:

```bash
v8-runner launch designer
v8-runner launch thin
v8-runner launch thick
v8-runner launch ordinary
```

Запускай wt-mcp-adapter через поддерживаемую поверхность `launch mcp`, а не собирай вручную `/C"runMcp..."`:

```bash
v8-runner launch mcp
v8-runner launch mcp --mode thin --mcp-port <PORT>
v8-runner launch mcp --mcp-config <FILE>
```

Для прямого ordinary-launch'а типизированные launch-флаги включают `--c`, `--execute`, `--use-privileged-mode`, `--output` и повторяемый `--raw-key`.

Для `launch mcp` используй `--mcp-config` и `--mcp-port`; не передавай `/C` через `--c`.

`launch mcp` и `launch mcp va` не устанавливают и не обновляют `tools.wt_mcp_adapter.extension`; запусти сначала `v8-runner build`, если это расширение может отсутствовать или быть устаревшим.

Про `launch mcp va` читай `testing.md`; это часть workflow отладки и написания сценариев Vanessa Automation.

## WS-режим к session-manager

> Форки SteelMorgan, используемые для WS-транспорта (`v8-runner-rust`, `wt-mcp-adapter`) — канон в `SKILL.md`, раздел «Форма команды».

Когда рядом с проектом запущен [`v8-session-manager`](https://github.com/1c-neurofish/v8-session-manager), 1С-клиент может подключаться к нему по WebSocket вместо локального HTTP MCP-сервера (`runMcp`-режим). В контейнерных окружениях менеджер обычно автозапускается; перед запуском клиента достаточно проверить доступность manager endpoint / live-сессию менеджера, а не стартовать новый экземпляр. v8-runner делает выбор транспорта автоматически. Эта секция — канон по механике транспорта, `/C`, `kind`, VA MCP и UI MCP workflow.

### Транспорт и автоопределение

`tools.wt_mcp_adapter.transport`:

- `auto` (по умолчанию) — короткий TCP-probe (200 ms) на хост:порт из `manager_url`. Слышим listener → WS, нет → `mcp`.
- `ws` — строго WS, при недоступности менеджера запуск падает с `session-manager unreachable at <url>`.
- `mcp` — локальный HTTP MCP-режим без probe.

Override через `--mcp-transport={ws|mcp|auto}`. CLI приоритет конфига. Те же параметры настраиваются через `tools.wt_mcp_adapter.*` в `v8project.yaml` / `v8project.local.yaml`:

```yaml
tools:
  wt_mcp_adapter:
    transport: auto         # mcp | ws | auto
    manager_url: ws://127.0.0.1:4000/sessions
    log_level: info
    ws_timeout_ms: 1000
```

### Что v8-runner подставляет в `/C` в WS-ветке

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;kind=<KIND>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

Источники значений:

| Ключ | По умолчанию | Override |
|------|--------------|----------|
| `manager_url` | `tools.wt_mcp_adapter.manager_url` или `ws://127.0.0.1:4000/sessions` | `--manager-url <URL>` |
| `client_uid` | новый UUID v4 на каждый запуск | `--client-uid <UUID>` |
| `kind` | внутренний mapping (см. таблицу ниже) | (нет — kind не переопределяется из CLI) |
| `corr_id` | `vr-<первые 8 символов client_uid>` | `--corr-id <STR>` |
| `mcp_log_level` | `tools.wt_mcp_adapter.log_level` или `info` | `--mcp-log-level={off\|error\|warn\|info\|debug\|trace}` |
| `mcp_ws_timeout_ms` | `tools.wt_mcp_adapter.ws_timeout_ms` или `1000` | `--mcp-ws-timeout-ms <N>` |

Для `launch mcp` / `launch mcp va` этот фрагмент — весь `/C`. Для `launch thin/thick/ordinary` используется тот же WS-фрагмент, но **без** `kind=<KIND>`, и он дописывается через `;` к существующему `/C`, если он уже задан:

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

**Важно:** не добавляй `kind` вручную для `launch thin/thick/ordinary` — такой клиент публикует только базовые инструменты `client_mcp`, не Vanessa Automation MCP.

### Internal `kind` mapping

| Команда v8-runner | `kind` |
|---|---|
| `launch thin/thick/ordinary` | не передаётся; клиентская сторона объявляет дефолтный kind |
| `launch mcp` | `v8_runner_client` |
| `launch mcp va` | `vanessa_test_client` |
| `test yaxunit ...` | `yaxunit_runner` |
| `test va ...` | `vanessa_test_client` |

Прокси-тулы менеджера публикуются на MCP HTTP по «голым» именам — `<toolname>`, **без** префикса `<kind>__`. `kind` определяет маршрутизацию запросов к нужному клиенту внутри менеджера, но в имена tools не попадает. Не подменяй `kind` вручную.

### Тестовые подкоманды (`test yaxunit`, `test va`)

Для тестовых запусков WS-фрагмент **дописывается** через `;` к существующему `/C` (`RunUnitTests=…` или Vanessa-плеер). Никаких отдельных флагов прописывать не надо — те же `--mcp-transport`/`--manager-url`/`--mcp-log-level` доступны и тут.

### JSON-output

В режиме `--json-message` ответ launch- и test-команд включает поля транспорта:

WS-ветка:
```json
{ "transport": "ws", "client_uid": "...", "kind": "...", "manager_url": "...", "corr_id": "..." }
```
MCP-ветка:
```json
{ "transport": "mcp", "mcp_port": 9874 }
```

Внешний оркестратор (CI, AI-агент) использует `client_uid` для поиска сессии в `session_list` менеджера. Структура записи сессии и `session_list` описаны в навыке `v8-session-manager`.

### Менеджер не запускается из v8-runner

v8-runner только подключается к запущенному менеджеру и не должен поднимать его сам. В типовом контейнере менеджер уже запущен автоматически; если проверки показывают, что менеджера нет, поднимай его по навыку `v8-session-manager`, не через `v8-runner`. Если менеджер не нужен — `--mcp-transport=mcp` форсирует локальный HTTP MCP flow.

### UI MCP через платформенный тест-клиент

Если задача — пройти интерфейс 1С через клиентские MCP-tools (`open_form`, `click`, `input`, `get_value`, `get_table_rows`, `test_client_start`), используй этот контур для структурного управления обычным 1С-клиентом. Для визуальной приёмки и скриншотов управляемых форм сначала предпочитай VA MCP по навыку `va-visual-check`.

Рабочая цепочка:

1. Проверь, что session-manager отвечает через MCP endpoint и не требует ручного запуска.
2. Запусти управляющий MCP-клиент detached через `v8-runner launch ...` с WS-транспортом и `/TESTMANAGER` через штатные параметры v8-runner. Сохрани PID/лог запуска.
3. После команды запуска дай клиенту 10-20 секунд до первой проверки. Затем проверяй live-сессию менеджера: нужный `kind`, `state=active`, `disconnected_secs_ago=null`, подходящий `infobase_name`, `inflight=0`.
4. После появления live-сессии жди регистрацию нужных UI tools периодическим чеком до 120 секунд. Сессия может появиться раньше, чем расширение опубликует полный набор tools. Базовая smoke-проверка перед UI-вызовами: `infobase_info` быстро возвращает ответ.
5. Запусти тестируемое приложение отдельным процессом с `/TESTCLIENT -TPort <port>` и теми же параметрами подключения, пользователем и паролем, что в проектном запуске. Предпочтительно поднимать этот процесс detached, сохранять PID/лог и передавать все обязательные ключи проекта (`/N`, `/P`, при необходимости `/UC`, тот же connection string). Если в проекте есть штатный launcher TestClient, используй его; иначе допускается прямая форма платформы:

```bash
setsid nohup /opt/1cv8/x86_64/<version>/1cv8c ENTERPRISE \
  /DisableStartupDialogs \
  /IBConnectionString 'Srvr="<server>";Ref="<infobase>";' \
  /N <user> /P <password> /UC <unlock_code> \
  /TESTCLIENT -TPort 1538 \
  > /tmp/test-client-1538.log 2>&1 &
```

6. Подключи тестируемое приложение через управляющую MCP-сессию:

```json
{"name":"test_client_start","arguments":{"session_id":"<1c-client session_id>","port":1538}}
```

Успешный критерий: `{"ok": true, "data": {"connected": true}}`.

7. После подключения выполняй UI MCP-tools только через `session_id` управляющей сессии: `open_form` → `click/input/select` → `get_value/get_table_rows`. Если live-сессий несколько, `session_id` обязателен.
8. Перед длинной UI-операцией проверь `inflight=0`; если вызов завис или `inflight` не падает, переходи к диагностике `v8-session-manager`.
9. Заверши оба процесса явно: сначала тестируемый TestClient (tool закрытия/команда клиента/сохранённый PID), затем управляющий `/TESTMANAGER` клиент. `kill <PID>` применяй только к сохранённым PID своих процессов.

Не удерживай клиент живым через `sleep`, `tail -f` или бесконечный shell-loop. Такие wrapper'ы становятся владельцем процесса и могут оборвать WS-сессию при завершении окружения агента.

Не делай так:

- Не запускай управляющий клиент без `/TESTMANAGER`: при первом `test_client_start` платформа может упасть с `Тип не определен (ТестируемоеПриложение)`.
- Не полагайся на `test_client_start` как на единственный способ запуска `/TESTCLIENT`, если он стартует клиента без `/N` и `/P`: такой процесс может остаться на входе в базу, а подключение вернёт `Отсутствует подходящий клиент тестирования`.
- Не считай `tools/list` доказательством готовности: proxied tools могут быть только из кеша session-manager. Готовность подтверждают live-сессия и успешный простой вызов.
