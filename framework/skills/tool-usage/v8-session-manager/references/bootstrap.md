# Запуск менеджера и подключение клиентов

Описаны только параметры, которые задаёт агент. Всё, для чего работают разумные дефолты — не трогаем; полная справка: `docs/CONFIGURATION.md`.

## Минимальный конфиг

`v8project.yaml`:

```yaml
workPath: /var/lib/v8-session-manager
```

Один обязательный ключ. Без `workPath` менеджер не стартует. Дефолтные адреса: WS `127.0.0.1:4000/sessions`, HTTP MCP `127.0.0.1:4001/mcp`.

## Когда менять дефолты

| Параметр | Когда добавлять |
|---|---|
| `mcp.session_manager.bind_address` | Менеджер должен слушать не только loopback (devcontainer, удалённый агент). Тогда `0.0.0.0:4000` |
| `mcp.http.bind_address` | То же для HTTP MCP, например `0.0.0.0:4001` |
| `mcp.http.auth_token` | Закрыть HTTP MCP токеном (production / общая сеть) |
| `mcp.metrics.bind_address` | Включить Prometheus-метрики на `127.0.0.1:9100` |

Пример:

```yaml
workPath: /var/lib/v8-session-manager
mcp:
  session_manager:
    bind_address: "0.0.0.0:4000"
  http:
    bind_address: "0.0.0.0:4001"
    auth_token: "<token>"
```

Остальные ключи (`idle_timeout_secs`, `reconnection_grace_secs`, `ws_ping_*`, `max_sessions`, `stateful_sessions`) задаём только при явной задаче по тюнингу. По умолчанию подходят.

## Persistent tools-cache (ADR-0035)

Top-level секция `tools_cache:`. **Дефолты обычно подходят** — менять только при тюнинге.

```yaml
# Кеш переживает рестарт менеджера; нужен для MCP-харнесов, которые
# нестабильно реагируют на notifications/tools/list_changed (например Claude Code).
tools_cache:
  enabled: true              # default true; false ⇒ откат к live-only (как до ADR-0035)
  cache_life_period: 5d      # humantime: 5d, 12h, 30m; минимум 1s
  storage_path: tools_cache.json   # относительный — от workPath; абсолютный — как есть
```

| Параметр | Когда менять |
|---|---|
| `tools_cache.enabled: false` | Точечный smoke / диагностика без диска; либо менеджер встаёт за reverse-proxy, который сам кеширует |
| `tools_cache.cache_life_period` | Конфигурация меняется чаще / реже, чем раз в 5 суток. Минимум 1s (validator не пустит меньше) |
| `tools_cache.storage_path` | Хочешь положить кеш в специально подмонтированный путь / shared volume |

Поведение при отсутствии секции — равносильно `tools_cache: {}` (т.е. дефолты выше). Поведение при `enabled: false` подробно описано в ADR-0035 и в `sessions-and-tools.md`.

## Запуск менеджера

| Сценарий | Команда |
|---|---|
| Dev-режим из репо менеджера | `cargo run --release` (подхватит `./v8project.yaml`) |
| Готовый бинарь | `./v8-session-manager --config /path/to/v8project.yaml` |
| Production | systemd-юнит из `docs/INSTALL.md` (`systemctl start v8-session-manager`) |

ENV `V8SM_CONFIG=<path>` — альтернатива `--config`.

## Подключение 1С-клиента

Менеджер только принимает входящее WS. Запуск 1С-клиента и формирование параметров подключения — задача `v8-runner` (skill `v8-runner`) для любого типа клиента: `launch designer/thin/thick/ordinary`, `launch mcp [va]`, `test yaxunit`, `test va`. Все типы клиентов поддерживают одни и те же WS-флаги (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`); тонкость в том, что на `test`-командах их надо ставить ДО подкоманды `yaxunit/va` — иначе clap не принимает. `kind` фиксируется точкой входа (`v8_runner_client` / `vanessa_test_client` / `yaxunit_runner`) и из CLI не переопределяется.

Со стороны менеджера достаточно знать: клиент должен прийти на `manager_url` менеджера и при `session.register` указать свой `kind` (определяет маршрутизацию tools на витрине) и `client_uid` (для soft-reconnect).

## Проверка, что менеджер поднялся

Процесс жив, в логе есть `accepting WebSocket on ...` и `accepting HTTP on ...`. Дальше — задача оркестратора (запуск клиента — skill `v8-runner`) и проверки витрины (`sessions-and-tools.md`).
