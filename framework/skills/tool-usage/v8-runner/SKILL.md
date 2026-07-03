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
- `references/auth-guard.md` — hard-stop по license-паттернам, правило двух кандидатов, классификация ошибок auth/path, хранение credentials в `v8project.local.yaml`.

## Форма команды

Канонический путь к бинарнику — `tools/external/v8-runner/v8-runner` (в проекте это работает через `tools/`-симлинк на фреймворк). Установщик фреймворка тянет Latest-релиз из [`alkoleft/v8-runner-rust`](https://github.com/alkoleft/v8-runner-rust) (upstream) при каждом запуске; ручная переустановка — `python tools/install.py --install-external-tools`. Если бинарник по этому пути отсутствует и в `PATH` тоже нет — попроси путь у пользователя или используй wrapper-скрипт из проекта.

> **WS-транспорт: используется форк SteelMorgan.** Для WS-сопряжения с менеджером сессий используется форк [`SteelMorgan/v8-runner-rust`](https://github.com/SteelMorgan/v8-runner-rust) вместо upstream `alkoleft/v8-runner-rust`, т.к. PR-ы с WS-поддержкой в upstream не принимаются. Установщик фреймворка ориентирован на релизы этого форка. Аналогично `onec-client-mcp-devkit` (расширения `mcp_client`, `test_client` и др.) берётся из форка [`SteelMorgan/onec-client-mcp-devkit`](https://github.com/SteelMorgan/onec-client-mcp-devkit).

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

## Жизненный цикл запущенных клиентов 1С

Интерактивные клиенты 1С и MCP/VA-сессии, которые должны оставаться доступными после возврата команды агенту, запускай как самостоятельные процессы с явным управлением жизненным циклом. Не используй `sleep`, `tail -f`, бесконечный shell-loop или похожую wrapper-команду как способ «удержать» клиент 1С живым: при завершении wrapper'а терминал/PTY или окружение агента может закрыть дочерний процесс 1С, а `session-manager` увидит это как обрыв WS без нормального закрытия.

Правильный порядок:

1. Запусти клиент через штатную команду `v8-runner launch ...`.
2. Если среда выполнения прибирает дочерние процессы после завершения shell-команды, запускай команду detached-средствами окружения (`nohup`, `setsid`, service/job runner или эквивалент проекта), сохрани PID и лог запуска.
3. Готовность проверяй внешним наблюдаемым состоянием: `session_list`, появление нужных MCP-tools, окно 1С, файл-протокол или запись в ЖР.
4. Завершай клиент явным действием: штатным инструментом session-manager/VA, командой закрытия клиента или точечным `kill <PID>` только для своего сохранённого PID.

`sleep` допустим только как короткое ожидание между проверками готовности внутри скрипта/poll-loop. Он не должен быть владельцем жизненного цикла 1С-процесса.

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
- Отладка Vanessa Automation, исследование форм и написание сценариев через MCP: используй `v8-runner launch mcp va ...`, чтобы запустить сеанс менеджера тестирования VA с MCP-инструментами. После старта проверяй готовность по `session_list`: нужен `kind=vanessa_test_client` и появление VA-tools, а не только первичная WS-регистрация.
- Нужна синхронизация свойств расширений: используй `v8-runner extensions` или `extensions --name <SOURCE_SET>`.
- Изменения в ИБ должны стать Git-видимыми файлами: проверь `git status`, затем запусти подходящую команду `v8-runner dump ...`.
- Нужна конвертация исходников между Designer и EDT: используй `v8-runner convert`; это только CLI и не использует ИБ.
- Существующие артефакты `.cf` или `.cfe` нужно применить к ИБ: используй `v8-runner load ...`.
- Нужно экспортировать релизные артефакты или опубликовать внешние артефакты: используй `v8-runner make ...` или алиас `artifacts`.
- Нужна UI-сессия 1С: используй `v8-runner launch designer`, `launch thin`, `launch thick` или `launch ordinary`.
- Нужно запустить onec-client-mcp-devkit внутри 1С без авторинга VA: используй `v8-runner launch mcp ...`.
- Сопрячь запущенный 1С-клиент с работающим [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) по WebSocket: см. отдельный раздел «WS-параметры сопряжения» ниже. WS-флаги (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`) доступны на `launch ...` и `test ...` командах одинаково. Тонкий момент clap-структуры: на `test` флаги ставятся **до** подкоманды `yaxunit/va` (например `v8-runner test --mcp-transport=ws yaxunit module <NAME>`), а не после.

## WS-параметры сопряжения с session-manager

WS-сопряжение с [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) — режим, в котором клиентский MCP-сервер 1С подключается к менеджеру по WebSocket вместо локального HTTP MCP. Управляется одним и тем же набором CLI-флагов (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`) или `tools.client_mcp.*` в `v8project.yaml`. Канон по транспорту/автоопределению, дефолтам и override каждого флага, формату `/C` и internal `kind` mapping — `references/project-workflows.md` (раздел «WS-режим к session-manager»); clap-нюансы и диагностика WS на test-командах — `references/testing.md`.

### Применимые точки входа

Один и тот же набор флагов работает для:

- `v8-runner launch designer | thin | thick | ordinary` — флаги ставятся после `launch`.
- `v8-runner launch mcp` / `launch mcp va` — флаги ставятся после `launch mcp [va]`.
- `v8-runner test yaxunit all` / `test yaxunit module <NAME>` — флаги ставятся **на уровне `test`**, ДО подкоманды `yaxunit`.
- `v8-runner test va` — флаги ставятся **на уровне `test`**, ДО подкоманды `va`.

Пример (test): `v8-runner test --mcp-transport=ws --mcp-log-level=debug yaxunit module mcp_МспПровайдер_Тесты`. Если ставишь WS-флаги после `yaxunit` или `module <NAME>` — clap отвечает `error: unexpected argument`, потому что эти подкоманды свой собственный `McpClientWsArgs` не объявляют.

### Режимы запуска клиентов и тестов

| Режим | Назначение | MCP/VA поведение |
|---|---|---|
| `launch designer` | Открыть Конфигуратор. | Не запускает клиентские MCP-tools и не применяет enterprise additional keys. |
| `launch thin`, `launch thick`, `launch ordinary` | Открыть обычный UI-клиент 1С. | При WS-сопряжении регистрирует базовый клиентский MCP-набор без `kind`; сам по себе не даёт VA-tools. |
| `launch mcp` | Запустить onec-client-mcp-devkit внутри 1С без Vanessa. | `kind=v8_runner_client` для WS; локальный HTTP MCP при `--mcp-transport=mcp` или fallback из `auto`. |
| `launch mcp va` | Запустить менеджер тестирования Vanessa для исследования, авторинга и клиентских MCP-tools VA. | `kind=vanessa_test_client`; runner добавляет `/TESTMANAGER`, `/DisableUnsafeActionProtection`, `/Execute <vanessa-automation.epf>`, runtime `VAParams`, отключает автозапуск/автозакрытие сценариев и не использует `StartFeaturePlayer`. |
| `test yaxunit ...` | Выполнить YAxUnit тесты. | `kind=yaxunit_runner` в WS-режиме; это тестовый runner, а не интерактивная UI-сессия. |
| `test va` | Выполнить Vanessa feature-сценарии. | `kind=vanessa_test_client`, но payload — `StartFeaturePlayer;VAParams=...`; это прогон сценариев, не режим исследования менеджера. |

Формат `/C` в WS-ветке (полный payload, различие `launch mcp`/`mcp va` vs `launch thin/thick/ordinary`, appending для test-команд) — канон в `references/project-workflows.md`, раздел «Что v8-runner подставляет в `/C` в WS-ветке».

### Vanessa Automation MCP через session-manager

Для workflow Vanessa Research/Scenario через наш `v8-client-session-manager` запускается не простой тонкий клиент, а сеанс менеджера тестирования с открытой обработкой Vanessa Automation:

```bash
v8-runner launch mcp va \
  --mcp-transport ws \
  --manager-url ws://127.0.0.1:4000/sessions \
  --client-uid <uid> \
  --corr-id <uid> \
  --mcp-log-level debug \
  --mcp-ws-timeout-ms 5000
```

Ожидаемая форма запуска 1С, которую должен собрать runner:

```text
1cv8c ENTERPRISE
  /TESTMANAGER
  /DisableStartupDialogs
  /DisableUnsafeActionProtection
  /IBConnectionString <строка подключения из v8project.yaml>
  /N <пользователь>
  /P <пароль>
  /Execute <путь>/vanessa-automation.epf
  /C"mcpMode=ws;manager_url=ws://127.0.0.1:4000/sessions;client_uid=<uid>;kind=vanessa_test_client;corr_id=<uid>;mcp_log_level=debug;mcp_ws_timeout_ms=5000;VAParams=<runtime va-params.json>"
```

Обязательный смысл этой строки запуска: MCP-сессия должна жить на стороне процесса тест-менеджера с открытой внешней обработкой Vanessa Automation. Не запускай тестируемое приложение с формой `MCPVA`: `MCPVA` — внутренняя форма/модуль внешней обработки VA, и именно VA в процессе `/TESTMANAGER` должна выполнить `MCPVA.ЗарегистрироватьИнструментыMCP()`.

Критерий готовности VA MCP-сессии: в `session_list` появилась live-сессия `kind=vanessa_test_client`, и в её tools есть VA-инструменты (`get_VanessaAutomation_state`, `connect_test_client`, `get_window_list_os`, `get_window_screenshot_os`, `get_form_analysis`, `manage_command_interface`) или число tools стало больше базового набора `client_mcp`. Первичная регистрация с базовыми tools ещё не означает, что `MCPVA.ЗарегистрироватьИнструментыMCP()` уже отработал.

Сразу после `v8-runner launch mcp va` ответ `session_list=[]` или отсутствие VA-tools **не является ошибкой**: запуск тест-менеджера и регистрация инструментов штатно могут занимать 10-90 секунд. Обязательный readiness-loop:

1. Опроси `session_list` каждые 5-10 секунд.
2. Жди суммарно до 120 секунд с момента запуска: 10-90 секунд — нормальный диапазон, 90-120 секунд — диагностический запас.
3. Продолжай только при live-сессии `kind=vanessa_test_client`, `state=active`, `disconnected_secs_ago=null`, `inflight=0`, и наличии нужных VA-tools для текущей задачи.
4. Имена tools из кеша MCP/showcase без live-сессии не доказывают готовность.
5. Если условие не выполнено за 120 секунд — стоп и доклад `VA MCP readiness blocker`.

После готовности WS-сессии тестируемое приложение в VA-контуре запускает сам тест-менеджер: вызови MCP-tool `connect_test_client` с аргументом `profileName` (имя профиля клиента тестирования, например `Codex thin AgentAI`). VA поднимет отдельный процесс `/TESTCLIENT -TPort <auto>` из профиля и подключит к нему `ТестируемоеПриложение`; после этого становятся доступны клиентские MCP-методы VA (`get_form_analysis`, `manage_command_interface`, `manage_form_elements`, screenshot/data tools и т.п.). Не запускай этот `/TESTCLIENT` вручную для VA-пути, если только не отлаживаешь сам механизм профилей.

После исследования, прогона ручных действий или ошибки обязательно вызови MCP-tool `close_test_client`. Передавай тот же `profileName`, если работал с конкретным профилем; без `profileName` tool закрывает текущий подключенный профиль. Это освобождает test-client процесс и не оставляет лишние 1С-сессии перед следующим запуском.

Скриншотные MCP-инструменты VA (`get_window_list_os`, `get_window_screenshot_os`) считай готовыми только после короткой smoke-проверки на текущем окружении: live-сессия должна оставаться активной, `inflight=0`, а PNG должен быть не пустым и не чёрным. Детальный порядок визуальной проверки и fallback-условия описаны в навыке `va-visual-check`.

Секцию `tools.va` / `tests.va` в `v8project.yaml` и профиль TestClient в VAParams настраивай по `references/config-and-backends.md` (раздел «Vanessa Automation в `v8project.yaml`»). Точную командную цепочку запуска manager → `connect_test_client` → close см. в `references/testing.md` (раздел «Точная цепочка VA manager → TestClient»). Полный payload, JSON-форму вывода (`--json-message`), правила probe и поведение при недоступности менеджера — в `references/project-workflows.md` (раздел «WS-режим к session-manager»). Подъём самого менеджера в v8-runner **не входит** — см. навык `v8-session-manager`.

### UI MCP через платформенный тест-клиент

Если задача — пройти интерфейс 1С через клиентские MCP-tools (`open_form`, `click`, `input`, `get_value`, `get_table_rows`, `test_client_start`), этот контур допустим только для структурного управления, когда нужная функция принципиально отсутствует в VA MCP или когда он используется как часть VA/TestClient-сценария.

Рабочая цепочка:

1. Подними session-manager и проверь HTTP endpoint: `tools/call session_list` должен отвечать, даже если `sessions=[]`.
2. Запусти управляющий MCP-клиент detached, обязательно с `/TESTMANAGER`:

```bash
uid=$(cat /proc/sys/kernel/random/uuid)
setsid nohup v8-runner --no-color --log-level debug launch thin \
  --mcp-transport ws \
  --manager-url ws://127.0.0.1:4000/sessions \
  --client-uid "$uid" \
  --corr-id "ui-$uid" \
  --mcp-log-level debug \
  --mcp-ws-timeout-ms 5000 \
  --raw-key /TESTMANAGER \
  > "/tmp/ui-mcp-$uid.log" 2>&1 &
```

3. Дождись в `session_list` live-сессии `kind=1c-client`, `state=active`, `inflight=0`, `infobase_name=<нужная ИБ>`. Базовая проверка перед UI-вызовами: `infobase_info` должен быстро вернуть ответ.
4. Запусти тестируемое приложение отдельным процессом с `/TESTCLIENT -TPort <порт>` и теми же параметрами подключения, пользователем и паролем, что в проектном запуске. Это предпочтительный путь: агент сам поднимает тестируемое приложение detached и сохраняет PID/лог, а `test_client_start` на следующем шаге используется как подключение управляющего `/TESTMANAGER` к уже слушающему порту. Если в проекте есть готовый launcher, он тоже должен передавать `/N`, `/P`, `/UC` и тот же connection string; иначе используй прямую форму платформы:

```bash
setsid nohup /opt/1cv8/x86_64/<version>/1cv8c ENTERPRISE \
  /DisableStartupDialogs \
  /IBConnectionString 'Srvr="<server>";Ref="<infobase>";' \
  /N <user> /P <password> /UC <unlock_code> \
  /TESTCLIENT -TPort 1538 \
  > /tmp/test-client-1538.log 2>&1 &
```

5. Подключи тестируемое приложение через управляющую MCP-сессию:

```json
{"name":"test_client_start","arguments":{"session_id":"<1c-client session_id>","port":1538}}
```

Успешный критерий: `{"ok": true, "data": {"connected": true}}`.

6. После этого выполняй UI MCP-tools только через `session_id` управляющей сессии: `open_form` → `click/input/select` → `get_value/get_table_rows`. Для элементов формы можно строить URI напрямую как `control://<urlencoded form name>/<urlencoded element name>`, если `find` нестабилен.

Не делай так:

- Не запускай управляющий клиент без `/TESTMANAGER`: при первом `test_client_start` платформа может упасть с `Тип не определен (ТестируемоеПриложение)`.
- Не полагайся на `test_client_start` как на единственный способ запуска `/TESTCLIENT`, если он стартует клиента без `/N` и `/P`: такой процесс может остаться на входе в базу, а подключение вернёт `Отсутствует подходящий клиент тестирования`.
- Не считай `tools/list` доказательством готовности: proxied tools могут быть только из кеша session-manager. Готовность подтверждает live-сессия в `session_list` и успешный простой вызов (`infobase_info`).

> Если yaxunit_runner не регистрируется в `session_list`, хотя WS-payload подставлен в `/C` — известный инцидент idle-handler race (DRIVE 2026-05-11) и его фикс (интервал `Мсп_ОтложенныйСтарт_Тик` `1`→`0.1` в `ManagedApplicationModule.bsl`): канон в `references/testing.md` (раздел «Диагностика WS-сопряжения test-фазы»).

## Headless-запуск внешней обработки (.epf) с вызовом серверного метода

Запуск внешней обработки в пакетном (headless) режиме с автоматическим выполнением её логики делается через `v8-runner launch <thin|thick|ordinary> --execute "<путь к .epf>"` (это `1cv8 ENTERPRISE /Execute<epf>`). Ключевой нюанс, без которого приём не работает:

- **`/Execute<epf>` ОТКРЫВАЕТ ФОРМУ обработки** (эмулирует «Открыть обработку»). Сам по себе он **НЕ вызывает** экспортный метод модуля объекта. Поэтому **обработка без формы** (только модуль объекта с экспортной процедурой) через `/Execute` **не исполнит** свою логику — точка входа никогда не будет вызвана.
- Канонический headless-приём: у обработки **есть управляемая форма**, в её модуле — обработчик `&НаКлиенте Процедура ПриОткрытии(Отказ)`, который распознаёт пакетный режим по **параметру запуска**, вызывает `&НаСервере`-метод (он и делает работу / дёргает экспортную процедуру модуля объекта), затем корректно завершает сеанс через `ЗавершитьРаботуСистемы(Ложь)`.

### Передача параметра и подавление предупреждения безопасности

- Параметр запуска передаётся ключом `--c "<строка>"` (это `/C"<строка>"`) и читается в BSL через `ПараметрЗапуска()`. Используй sentinel-строку, чтобы форма отличала headless-запуск от интерактивного открытия и не авто-исполнялась при ручном открытии.
- **Первый запуск** внешней обработки поднимает диалог предупреждения безопасности (защита от опасных действий) — в headless он повесит процесс. Подавляется ключом `--raw-key /DisableUnsafeActionProtection`. Альтернатива — снять у пользователя флаг «Защита от опасных действий» или настроить профиль безопасности (но CLI-ключ предпочтительнее для разовых прогонов).

### Минимальный скелет обработки

```bsl
// Модуль формы обработки
&НаКлиенте
Процедура ПриОткрытии(Отказ)
    Если ПараметрЗапуска() = "ЗАПУСК_ПАКЕТНО" Тогда   // sentinel из --c
        Протокол = ВыполнитьОперациюНаСервере();      // серверная работа
        // записать Протокол в известный файл для верификации снаружи
        ЗавершитьРаботуСистемы(Ложь);                 // корректный выход без диалогов
    КонецЕсли;
КонецПроцедуры

&НаСервере
Функция ВыполнитьОперациюНаСервере()
    // разрешить все параметры СЕРВЕРНО (не из реквизитов формы — в headless их никто не заполнил),
    // выполнить бизнес-логику, вернуть текст протокола
КонецФункции
```

### Команда и верификация

```bash
v8-runner launch thin --execute "<абс. путь к .epf>" --c "ЗАПУСК_ПАКЕТНО" --raw-key /DisableUnsafeActionProtection
```

- Подключение к ИБ берётся из `v8project.yaml` — отдельный `/S`/`/F` указывать не нужно.
- **Условие завершения:** жди выхода процесса 1cv8 ИЛИ появления файла-протокола, который пишет сама обработка. Один лишь exit-код процесса — слабый сигнал.
- **Верифицируй результат по поведению, а не по факту запуска:** дельта данных (запрос до/после), содержимое файла-протокола, запись в журнале регистрации. «Процесс отработал без ошибки» ≠ «логика выполнилась».

> Альтернатива без `/Execute`: из **уже подключённой** серверной сессии — `ВнешниеОбработки.Создать(<путь>, Ложь)` + вызов её экспортного метода (или БСП `ДлительныеОперации.ВыполнитьПроцедуруМодуляОбъектаОбработки`). Это требует канала «выполнить код на сервере» (менеджер сессий / тест-раннер), а `/Execute` — самодостаточен из командной строки.

## Защитные правила

- Перед любой операцией v8-runner, обращающейся к ИБ, применяй auth-guard: проверь credentials и классифицируй возможные ошибки (license / auth / path) — см. `references/auth-guard.md`.
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
