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

## Запуск менеджера

| Сценарий | Команда |
|---|---|
| Dev-режим из репо менеджера | `cargo run --release` (подхватит `./v8project.yaml`) |
| Готовый бинарь | `./v8-session-manager --config /path/to/v8project.yaml` |
| Production | systemd-юнит из `docs/INSTALL.md` (`systemctl start v8-session-manager`) |

ENV `V8SM_CONFIG=<path>` — альтернатива `--config`.

## Подключение 1С-клиента

Менеджер только принимает входящее WS. Запуск 1С-клиента и формирование параметров подключения — задача `v8-runner` (skill `v8-runner`). Со стороны менеджера достаточно знать: клиент должен прийти на `manager_url` менеджера и при `session.register` указать свой `kind` (определяет namespace tools на витрине) и `client_uid` (для soft-reconnect).

## Проверка, что менеджер поднялся

Процесс жив, в логе есть `accepting WebSocket on ...` и `accepting HTTP on ...`. Дальше — задача оркестратора (запуск клиента — skill `v8-runner`) и проверки витрины (`sessions-and-tools.md`).
