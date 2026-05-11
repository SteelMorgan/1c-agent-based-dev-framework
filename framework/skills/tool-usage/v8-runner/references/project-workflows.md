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

Если настроен `tools.client_mcp.extension`, `build` также готовит это tool-расширение после стадии source-set'ов проекта, в том числе для узких сборок с `--source-set`. Tool-расширения на основе исходников используют собственное состояние change-detection и пропускаются, если ничего не изменилось; используй `build --full-rebuild`, чтобы принудительно обновить. Не добавляй tool-расширение как source-set проекта и не выбирай его через `--source-set`.

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

Запускай onec-client-mcp-devkit через поддерживаемую поверхность `launch mcp`, а не собирай вручную `/C"runMcp..."`:

```bash
v8-runner launch mcp
v8-runner launch mcp --mode thin --mcp-port <PORT>
v8-runner launch mcp --mcp-config <FILE>
```

Для прямого ordinary-launch'а типизированные launch-флаги включают `--c`, `--execute`, `--use-privileged-mode`, `--output` и повторяемый `--raw-key`.

Для `launch mcp` используй `--mcp-config` и `--mcp-port`; не передавай `/C` через `--c`.

`launch mcp` и `launch mcp va` не устанавливают и не обновляют `tools.client_mcp.extension`; запусти сначала `v8-runner build`, если это расширение может отсутствовать или быть устаревшим.

Про `launch mcp va` читай `testing.md`; это часть workflow отладки и написания сценариев Vanessa Automation.

## WS-режим к session-manager

Когда рядом с проектом запущен [`v8-client-session-manager`](https://github.com/SteelMorgan/v8-client-session-manager), 1С-клиент может подключаться к нему по WebSocket вместо локального HTTP MCP-сервера (legacy `runMcp`-режим). v8-runner делает выбор автоматически.

### Транспорт и автоопределение

`tools.client_mcp.transport`:

- `auto` (по умолчанию) — короткий TCP-probe (200 ms) на хост:порт из `manager_url`. Слышим listener → WS, нет → legacy.
- `ws` — строго WS, при недоступности менеджера запуск падает с `session-manager unreachable at <url>`.
- `legacy` — старый HTTP-режим без probe.

Override через `--mcp-transport={ws|legacy|auto}`. CLI приоритет конфига.

### Что v8-runner подставляет в `/C` в WS-ветке

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;kind=<KIND>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

Источники значений:

| Ключ | По умолчанию | Override |
|------|--------------|----------|
| `manager_url` | `tools.client_mcp.manager_url` или `ws://127.0.0.1:4000/sessions` | `--manager-url <URL>` |
| `client_uid` | новый UUID v4 на каждый запуск | `--client-uid <UUID>` |
| `kind` | внутренний mapping (см. таблицу ниже) | (нет — kind не переопределяется из CLI) |
| `corr_id` | `vr-<первые 8 символов client_uid>` | `--corr-id <STR>` |
| `mcp_log_level` | `tools.client_mcp.log_level` или `info` | `--mcp-log-level={off\|error\|warn\|info\|debug\|trace}` |
| `mcp_ws_timeout_ms` | `tools.client_mcp.ws_timeout_ms` или `1000` | `--mcp-ws-timeout-ms <N>` |

### Internal `kind` mapping

| Команда v8-runner | `kind` |
|---|---|
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
Legacy-ветка:
```json
{ "transport": "legacy", "mcp_port": 9874 }
```

Внешний оркестратор (CI, AI-агент) использует `client_uid` для поиска сессии в `session_list` менеджера. Структура записи сессии и `session_list` описаны в навыке `v8-session-manager`.

### Менеджер не запускается из v8-runner

v8-runner только подключается к запущенному менеджеру. Подъём менеджера — отдельный шаг (`cargo run --release` в репо `v8-client-session-manager`, либо systemd-юнит `systemd/v8-session-manager.service`, либо Docker-compose). Если менеджер не нужен — `--mcp-transport=legacy` форсирует старый flow.
