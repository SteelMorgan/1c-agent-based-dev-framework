# Выбор команды

Выбирай команды по намерению пользователя, а не перечисляя всю CLI-поверхность.

## Инициализация

Используй, когда в проекте нет `v8project.yaml` или сгенерированного runtime-состояния:

```bash
v8-runner config init
v8-runner config init --connection "File=build/ib"
v8-runner config init --format edt
v8-runner config init --builder IBCMD
v8-runner init
```

Изучи `v8project.yaml` после `config init` и до команд, которые создают или изменяют ИБ, воркспейсы или исходники.

## Сборка и восстановление

Применить Git-видимые изменения исходников к настроенной ИБ:

```bash
v8-runner build
```

Ограничить сборку одним настроенным source-set'ом:

```bash
v8-runner build --source-set <NAME>
```

Восстановиться после переключения веток, rebase, больших перемещений объектов или подозрительного инкрементального состояния:

```bash
v8-runner build --full-rebuild
```

Используй `test` напрямую, когда важно поведение; тестовые команды сначала выполняют `build`.

## Синтаксис

Designer-модули:

```bash
v8-runner build
v8-runner syntax designer-modules --server --thin-client
```

Конфигурация Designer:

```bash
v8-runner build
v8-runner syntax designer-config
```

EDT:

```bash
v8-runner build
v8-runner syntax edt
```

## Тесты

Все тесты YaXUnit:

```bash
v8-runner test yaxunit all
```

Точечный модуль YaXUnit:

```bash
v8-runner test yaxunit module <MODULE_NAME>
```

Vanessa Automation:

```bash
v8-runner test va
```

Интерактивная отладка VA и написание сценариев:

```bash
v8-runner launch mcp va
```

## Расширения

Обновить свойства всех настроенных расширений:

```bash
v8-runner extensions
```

Обновить выбранные source-set'ы расширений:

```bash
v8-runner extensions --name <SOURCE_SET>
```

## Dump, convert, load и артефакты

Вернуть изменения ИБ в Git-видимые файлы:

```bash
git status --short
v8-runner dump --mode incremental
git diff
```

Выгрузить отдельные объекты, когда backend это поддерживает:

```bash
v8-runner dump --mode partial --object <TYPE:NAME>
```

Конвертировать настроенные source-set'ы между файловыми форматами Designer и EDT:

```bash
v8-runner convert
v8-runner convert --source-set <NAME>
v8-runner convert --output <DIR>
```

Применить собранные `.cf` или `.cfe` артефакты:

```bash
v8-runner load --path <FILE>
v8-runner load --path <FILE> --mode merge --settings <FILE>
v8-runner load --path <FILE> --extension <NAME>
```

Экспортировать релизные артефакты или опубликовать внешние артефакты:

```bash
v8-runner make --output <TARGET>
v8-runner make --output <TARGET> --source-set <NAME>
v8-runner make --output <TARGET> --extension <NAME>
```

`artifacts` — видимый алиас `make`.

## Launch

Запустить клиенты 1С через runner:

```bash
v8-runner launch designer
v8-runner launch thin
v8-runner launch thick
v8-runner launch ordinary
```

Запустить wt-mcp-adapter внутри 1С без VA:

```bash
v8-runner launch mcp
v8-runner launch mcp --mode thin --mcp-port <PORT>
v8-runner launch mcp --mcp-config <FILE>
```

Флаги WS-режима (когда v8-session-manager доступен):

```bash
v8-runner launch mcp --mcp-transport=ws --manager-url ws://127.0.0.1:4000/sessions
v8-runner launch mcp --mcp-transport=mcp                # принудительно локальный HTTP MCP без probe
v8-runner launch mcp --mcp-log-level=debug --client-uid <UUID> --corr-id <STR>
```

`--mcp-transport=auto` (по умолчанию) выполняет TCP-пробу `manager_url` на 200 ms и выбирает `ws` при успехе и `mcp` при отказе. Те же WS-флаги работают на `test yaxunit ...` и `test va ...`. Смотри полный раздел WS-режима в `project-workflows.md`, внутренний mapping `kind` и форму вывода `--json-message`.
