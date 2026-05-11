# Тестирование

Используй тесты, когда важно поведение. Тестовые команды сначала собирают, поэтому не запускай отдельный `build`, если пользователь не попросил именно build-only диагностику.

## YaXUnit

Все тесты:

```bash
v8-runner test yaxunit all
v8-runner test yaxunit --full all
```

Один модуль:

```bash
v8-runner test yaxunit module <MODULE_NAME>
v8-runner test yaxunit --full module <MODULE_NAME>
```

Используй прогон по модулю для узких изменений в коде. Используй прогон всех тестов перед push'ем или при широких изменениях.

## Vanessa Automation

Запусти настроенный профиль Vanessa Automation:

```bash
v8-runner test va
```

Если пользователь указывает на конкретную фичу или профиль, изучи `tests.va` в `v8project.yaml` до изменения команды.

`test va` использует настроенный `tests.va.profile`; не выдумывай ad-hoc пути к фичам без обновления конфига или использования установленного враппера в репозитории.

`tests.va.fail_fast` по умолчанию `false`.

При установке `tests.va.profiles.<name>.filter_tags` или `ignore_tags`, а также при передаче `--filter-tag` / `--ignore-tag`, ведущий `@` принимается для удобства пользователя, но сгенерированные `СписокТеговОтбор` и `СписокТеговИсключение` в runtime `VAParams` должны записываться без этого ведущего `@`.

## Отладка VA и написание сценариев

Используй `launch mcp va`, когда цель — интерактивная отладка Vanessa Automation, написание сценариев или управление feature-плеером VA через onec-client-mcp-devkit:

```bash
v8-runner launch mcp va
v8-runner launch mcp va --mode thin
v8-runner launch mcp va --mcp-port <PORT>
v8-runner launch mcp va --mcp-config <FILE>
```

Это запускает клиентский MCP-сервер в 1С и загружает Vanessa Automation из `tools.va`. Предпочитай его для разведочной работы с VA; для настроенного автоматического прогона тестов используй `test va`.

## Опции launch во время тестов

Тестовые команды принимают launch-связанные опции, такие как `--client-mode`, `--c`, `--execute`, `--use-privileged-mode` и повторяемый `--raw-key`.

Используй их только когда пользователю нужен специфический launch-контекст 1С; в остальных случаях предпочитай настроенные дефолты.

## Syntax как валидация

Синтаксис модулей Designer:

```bash
v8-runner syntax designer-modules --server --thin-client
```

Синтаксис конфигурации Designer:

```bash
v8-runner syntax designer-config
```

Синтаксис EDT:

```bash
v8-runner syntax edt
```

## Артефакты

Сохраняй артефакты упавших тестов в:

```text
workPath/temp/<runner-id>/runs/<run-id>/
```

В итоговых ответах указывай команду, результат pass/fail и путь к артефакту, если он есть.
