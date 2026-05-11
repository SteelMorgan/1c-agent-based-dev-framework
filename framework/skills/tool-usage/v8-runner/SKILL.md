---
name: v8-runner
description: "Используй, когда Codex должен управлять v8-runner на локальных 1С-проектах через CLI: настроить v8project.yaml, инициализировать информационные базы или EDT-воркспейсы, собирать исходники Designer или EDT, запускать синтаксические проверки и тесты, выгружать изменения ИБ, конвертировать форматы исходников, загружать или экспортировать артефакты, запускать клиенты 1С или выбирать безопасные последовательности команд автоматизации 1С."
provides_capabilities:
  - build_project
  - full_rebuild_project
  - init_infobase
  - config_init
  - syntax_check_designer_modules
  - syntax_check_designer_config
  - syntax_check_edt
  - run_yaxunit
  - run_vanessa
  - dump_config
  - load_artifact
  - make_artifacts
  - convert_sources
  - launch_designer
  - launch_thin_client
  - launch_mcp_client
  - run_session_manager
  - extensions_update
---

# v8-runner

Используй этот навык, чтобы управлять `v8-runner` как слоем автоматизации для локальных 1С-проектов разработки.

Держи этот файл как точку входа для решений. Загружай только тот reference-файл, который соответствует задаче:

- `references/command-selection.md` — для выбора правильной последовательности команд.
- `references/bootstrap.md` — для генерации `v8project.yaml` из существующего репозитория: что определять самостоятельно, а что спрашивать у пользователя (дерево решений для `format`, `builder`, `connection`).
- `references/config-and-backends.md` — про `v8project.yaml`, source-set'ы, форматы, builder'ы и ограничения backend'ов.
- `references/project-workflows.md` — типовые сценарии build, syntax, dump, launch и синхронизации исходников для Designer- и EDT-проектов.
- `references/file-and-artifact-workflows.md` — про dump, convert, load, make/artifacts и поэтапную публикацию.
- `references/testing.md` — про YaXUnit, Vanessa Automation, синтаксические проверки и артефакты.
- `references/troubleshooting.md` — про сбои настройки, устаревшее состояние и диагностику окружения.

## Форма команды

Используй доступный бинарник `v8-runner` напрямую. Если его нет в `PATH`, попроси путь к бинарнику или используй wrapper-скрипт из проекта.

`v8project.yaml` — имя конфига проекта по умолчанию. Соседний `v8project.local.yaml` загружается автоматически для машинно-локальных путей, учётных данных, инструментов, тестов и MCP-настроек. Не передавай `--config v8project.yaml`, если пользователь явно не просит нестандартную форму команды или активный путь к конфигу отличается от дефолтного; никогда не передавай `v8project.local.yaml` через `--config`.

Сгенерированные файлы `v8project.yaml` содержат modeline `yaml-language-server`, который указывает на версионированный JSON Schema для текущего релиза `v8-runner`. Для `v8project.local.yaml` используй соответствующий raw-URL `docs/schemas/v8project.local.schema.json` с GitHub-тега в настройках редактора, когда важна редактура с подсказками по схеме.

Используй JSON-вывод только когда другому инструменту, скрипту или итоговому ответу нужны структурированные результаты:

```bash
v8-runner --json-message build
```

Для прямой человеческой диагностики используй текстовый вывод.

Полезные глобальные флаги:

- `--config <CONFIG>` — когда активный конфиг не `./v8project.yaml`.
- `--json-message` — для машиночитаемых CLI-конвертов.
- `--workdir <WORKDIR>` — переопределяет `workPath`; имеет приоритет над `v8project.local.yaml`.
- `--clean-before-execution` — очистить логи перед выполнением.
- `--log-level <error|warn|info|debug|trace>` — для диагностики.
- `--no-color` — простой текстовый вывод.

## Первый проход

1. Проверь, существует ли `v8project.yaml` в корне 1С-проекта.
2. Если его нет, запусти максимально узкую команду `v8-runner config init ...`, подходящую под форму проекта.
3. Изучи сгенерированный конфиг до запуска изменяющих команд.
4. Запускай `v8-runner init` только когда нужно создать файловую ИБ или EDT-воркспейс.
5. Запусти максимально узкую команду валидации, отвечающую цели пользователя.

Полезные команды инициализации:

```bash
v8-runner config init
v8-runner config init --connection "File=build/ib"
v8-runner config init --format edt
v8-runner config init --builder IBCMD
v8-runner init
```

## Маршрутизация типовых сценариев

- Изменились исходники, ИБ может быть устаревшей: запусти `v8-runner build`.
- Изменился только один source-set: используй команды, принимающие `--source-set <NAME>`, вместо полной пересборки или материализации всего.
- Переключение ветки, rebase, большие перемещения объектов, устаревшее состояние tool-расширения на основе исходников или подозрительное инкрементальное состояние: запусти `v8-runner build --full-rebuild`.
- Синтаксическая проверка: посмотри `format` и `builder`, затем выбери `syntax designer-modules`, `syntax designer-config` или `syntax edt`.
- Валидация поведения: запусти подходящую команду `v8-runner test ...`; тесты сначала собирают.
- Отладка Vanessa Automation или написание сценариев: используй `v8-runner launch mcp va ...`, чтобы запустить клиентский MCP-сервер с загруженной VA.
- Нужна синхронизация свойств расширений: используй `v8-runner extensions` или `extensions --name <SOURCE_SET>`.
- Изменения в ИБ должны стать Git-видимыми файлами: проверь `git status`, затем запусти подходящую команду `v8-runner dump ...`.
- Нужна конвертация исходников между Designer и EDT: используй `v8-runner convert`; это только CLI и не использует ИБ.
- Существующие артефакты `.cf` или `.cfe` нужно применить к ИБ: используй `v8-runner load ...`.
- Нужно экспортировать релизные артефакты или опубликовать внешние артефакты: используй `v8-runner make ...` или алиас `artifacts`.
- Нужна UI-сессия 1С: используй `v8-runner launch designer`, `launch thin`, `launch thick` или `launch ordinary`.
- Нужно запустить onec-client-mcp-devkit внутри 1С без авторинга VA: используй `v8-runner launch mcp ...`.
- Сопрячь запущенный 1С-клиент с работающим [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) по WebSocket: полагайся на `--mcp-transport=auto` (по умолчанию — TCP-проба `manager_url` на 200 ms). Принудительно WS — через `--mcp-transport=ws` (падает, если менеджер недоступен), или полностью обойти WS — через `--mcp-transport=legacy`. WS-only флаги: `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`. Внутренний mapping `kind` (`v8_runner_client` / `vanessa_test_client` / `yaxunit_runner` / `vanessa_test_client`) фиксируется по точке входа и **не** переопределяется из CLI. Прочитай `references/project-workflows.md` (раздел «WS-режим к session-manager») для полного payload, дефолтов и формы вывода `--json-message`.

## Защитные правила

- Не удаляй и не пересоздавай ИБ, воркспейс, временный каталог или сгенерированное состояние, если пользователь явно об этом не просил или сама команда не задокументирована как путь восстановления.
- Не выдумывай сырые флаги `1cv8`, `ibcmd` или `1cedtcli`; предпочитай командную поверхность `v8-runner`.
- Перед `dump` проверь `git status`, если результат может перезаписать или смешаться с уже внесёнными правками исходников.
- Сохраняй артефакты упавших тестов в `workPath/temp/<runner-id>/runs/<run-id>/` для диагностики, не очищай их сразу.
- Сообщай об отсутствующих локальных утилитах 1С как о проблемах окружения/установки, а не как об ошибках исходников проекта.
- Держи итоговые ответы конкретными: запущенная команда, результат, путь к релевантному артефакту и любая последующая команда.

## Дисциплина вывода

Когда сообщаешь о результатах, разделяй:

- сбои исходников проекта;
- сбои команды/конфига v8-runner;
- сбои поиска локальной платформы 1С, EDT, IBCMD или инструментов;
- сбои тестов и пути их артефактов.
