---
name: v8-runner
description: "v8-runner: базы, сборка, проверки, тесты, клиенты 1С"
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
  - extensions_update
---

# v8-runner

Точка входа для выбора команд `v8-runner`. Загружай только нужный reference:

| Нужно | Читать |
|---|---|
| Выбрать команду | `references/command-selection.md` |
| Создать/проверить `v8project.yaml` | `references/bootstrap.md`, `references/config-and-backends.md` |
| Сборка, синтаксис, dump, запуск, синхронизация исходников | `references/project-workflows.md` |
| Dump, convert, load, make/artifacts | `references/file-and-artifact-workflows.md` |
| YaXUnit, Vanessa, мониторинг тестов | `references/testing.md` |
| Ошибки path/auth/license | `references/auth-guard.md`, `references/troubleshooting.md` |
| Headless запуск `.epf` через `/Execute` | `references/headless-epf.md` |

## Форма команды

Канон — команда из `PATH`:

```bash
v8-runner <command>
```

Не зашивай абсолютный путь. В контейнере ожидается `/usr/local/bin/v8-runner`; проверка path-проблем начинается с `command -v v8-runner`. Если команда не найдена — остановись и сообщи о проблеме окружения. Legacy fallback `tools/external/v8-runner/v8-runner` допустим только для старых окружений и не становится новым каноном.

`v8project.yaml` — config по умолчанию. `v8project.local.yaml` подхватывается автоматически для локальных путей, credentials и MCP-настроек; не передавай его через `--config`.

JSON-вывод включай только для машинной обработки:

```bash
v8-runner --json-message build
```

Частые global flags: `--config`, `--json-message`, `--workdir`, `--clean-before-execution`, `--log-level`, `--no-color`.

## Первый проход

1. Проверь наличие `v8project.yaml`.
2. Если его нет — выбери минимальный `v8-runner config init ...` по `references/bootstrap.md`.
3. Перед изменяющими командами прочитай сгенерированный config.
4. `v8-runner init` запускай только для создания файловой ИБ или EDT workspace.
5. Валидируй минимальной командой, отвечающей цели.

## Маршрутизация

| Ситуация | Команда / reference |
|---|---|
| Исходники изменились, ИБ устарела | `v8-runner build` |
| Подозрительное incremental-состояние, branch switch, крупные перемещения | `v8-runner build --full-rebuild` |
| Один source-set | Команда с `--source-set <NAME>` |
| Синтаксис | `syntax designer-modules`, `syntax designer-config` или `syntax edt` по `format`/`builder` |
| YaXUnit / Vanessa | `v8-runner test ...`, детали в `references/testing.md` |
| Свойства расширений | `v8-runner extensions [--name <SOURCE_SET>]` |
| ИБ → Git-visible файлы | `git status`, затем `v8-runner dump ...` |
| Convert/load/make | `references/file-and-artifact-workflows.md` |
| UI/VA/MCP launch | `references/project-workflows.md` и `references/testing.md` |

## WS и `v8sm`

`v8-runner` только запускает/подключает 1С-клиентов. Менеджер WS-сессий не поднимается из `v8-runner`: в контейнере он обычно автозапущен и доступен командой `v8sm`. Перед WS-запуском проверь manager endpoint / `session_list`; если менеджера нет — используй навык `v8-session-manager`.

WS-флаги (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`) доступны для `launch ...` и `test ...`. На `test` они ставятся до подкоманды:

```bash
v8-runner test --mcp-transport=ws yaxunit module <NAME>
```

Полный контракт transport/probe, `/C` payload, `kind` mapping и JSON-output — в `references/project-workflows.md`; тестовые нюансы и readiness loops — в `references/testing.md`.

## Жизненный цикл 1С-клиентов

Для процессов, которые должны жить после возврата shell-команды:

1. Запускай штатной командой `v8-runner launch ...`.
2. Если среда прибирает дочерние процессы, используй detached-механизм (`setsid`, `nohup`, service/job runner), сохрани PID и лог.
3. Готовность проверяй внешним состоянием: live-сессия менеджера, MCP-tools, окно 1С, файл-протокол, ЖР.
4. Завершай явно: tool закрытия, команда клиента или `kill <PID>` только для сохранённого PID.

После команды запуска дай клиенту штатное время подняться: 10-20 секунд до первой проверки — нормальный интервал, а не признак зависания. Если процесс/окно/сеанс 1С появился, но нужных MCP-tools ещё нет, продолжай периодическую проверку регистрации tools до 120 секунд: расширение может зарегистрировать витрину позже, чем появляется сама сессия. Заверши ожидание раньше, как только нужная live-сессия и tools видны; если лимит истёк, диагностируй запуск клиента, WS-подключение и регистрацию расширения через `v8-session-manager`.

`sleep` допустим только как короткое ожидание в readiness loop, не как владелец жизненного цикла.

## Guardrails

- Перед операцией, обращающейся к ИБ, применяй `auth-guard`.
- Не удаляй и не пересоздавай ИБ, workspace, временный каталог или generated state без явного запроса либо documented recovery path.
- Не выдумывай сырые флаги `1cv8`, `ibcmd`, `1cedtcli`; предпочитай surface `v8-runner`.
- Перед `dump` проверь `git status`.
- Не очищай артефакты упавших тестов до диагностики.
- Отделяй в отчёте: сбой исходников, сбой команды/config, сбой окружения 1С, сбой тестов и путь к артефактам.
