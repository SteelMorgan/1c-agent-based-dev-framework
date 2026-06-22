---
name: v8-runner
description: "Use for управления v8-runner на локальных 1С-проектах: настройка v8project.yaml, сборка, синтаксические проверки, тесты, выгрузка/загрузка ИБ, конвертация форматов, запуск клиентов 1С."
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
- Сопрячь запущенный 1С-клиент с работающим [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) по WebSocket: см. отдельный раздел «WS-параметры сопряжения» ниже. WS-флаги (`--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`) доступны на `launch ...` и `test ...` командах одинаково. Тонкий момент clap-структуры: на `test` флаги ставятся **до** подкоманды `yaxunit/va` (например `v8-runner test --mcp-transport=ws yaxunit module <NAME>`), а не после.

## WS-параметры сопряжения с session-manager

WS-сопряжение с [v8-client-session-manager](https://github.com/SteelMorgan/v8-client-session-manager) — режим, в котором клиентский MCP-сервер 1С подключается к менеджеру по WebSocket вместо локального HTTP MCP. Управляется одним и тем же набором CLI-флагов или `tools.client_mcp.*` в `v8project.yaml`.

### Применимые точки входа

Один и тот же набор флагов работает для:

- `v8-runner launch designer | thin | thick | ordinary` — флаги ставятся после `launch`.
- `v8-runner launch mcp` / `launch mcp va` — флаги ставятся после `launch mcp [va]`.
- `v8-runner test yaxunit all` / `test yaxunit module <NAME>` — флаги ставятся **на уровне `test`**, ДО подкоманды `yaxunit`.
- `v8-runner test va` — флаги ставятся **на уровне `test`**, ДО подкоманды `va`.

Пример (test): `v8-runner test --mcp-transport=ws --mcp-log-level=debug yaxunit module mcp_МспПровайдер_Тесты`. Если ставишь WS-флаги после `yaxunit` или `module <NAME>` — clap отвечает `error: unexpected argument`, потому что эти подкоманды свой собственный `McpClientWsArgs` не объявляют.

### CLI-флаги

- `--mcp-transport={ws|legacy|auto}` — `auto` (по умолчанию) делает TCP-пробу `manager_url` ~200 ms; `ws` — строго WS, падает при недоступности; `legacy` — старый HTTP-режим без probe.
- `--manager-url <URL>` — переопределить `tools.client_mcp.manager_url` (дефолт `ws://127.0.0.1:4000/sessions`).
- `--client-uid <UUID>` — переопределить автогенерированный UUID v4.
- `--corr-id <STR>` — переопределить `vr-<первые 8 символов client_uid>`.
- `--mcp-log-level={off|error|warn|info|debug|trace}` — уровень логирования внутри клиента.
- `--mcp-ws-timeout-ms <N>` — таймаут WS-handshake (дефолт 1000 ms; релевантен `auto`-fallback).

Альтернатива: всё это можно прописать в `tools.client_mcp.*` в `v8project.yaml` / `v8project.local.yaml` — порядок приоритета: CLI → yaml → внутренние дефолты.

```yaml
tools:
  client_mcp:
    transport: auto         # ws | legacy | auto
    manager_url: ws://127.0.0.1:4000/sessions
    log_level: info
    ws_timeout_ms: 1000
```

`kind` фиксируется точкой входа и из CLI не переопределяется ни в одном режиме.

### Внутренний mapping `kind`

| Команда | `kind` |
|---|---|
| `launch mcp` | `v8_runner_client` |
| `launch mcp va` | `vanessa_test_client` |
| `test yaxunit ...` | `yaxunit_runner` |
| `test va ...` | `vanessa_test_client` |

### Что v8-runner подставляет в `/C` в WS-ветке

```text
/C"mcpMode=ws;manager_url=<URL>;client_uid=<UUID>;kind=<KIND>;corr_id=<CORR>;mcp_log_level=<LVL>;mcp_ws_timeout_ms=<MS>"
```

Для launch — это весь `/C`; для test-команд WS-фрагмент дописывается через `;` к существующему `RunUnitTests=…` / Vanessa-плееру (если transport=ws выбран через yaml-конфиг).

Полный payload, JSON-форму вывода (`--json-message`), правила probe и поведение при недоступности менеджера — в `references/project-workflows.md` (раздел «WS-режим к session-manager»). Подъём самого менеджера в v8-runner **не входит** — см. навык `v8-session-manager`.

### Resolved: WS-сессии в `test yaxunit` (DRIVE 2026-05-11)

Симптом: yaxunit_runner НЕ регистрируется в `session_list` менеджера, хотя v8-runner правильно подставляет WS-payload в `/C` (`RunUnitTests=...;mcpMode=ws;...;kind=yaxunit_runner;...`).

Корень — race condition в BSL `client_mcp` (`ManagedApplicationModule.bsl`): idle-handler `Мсп_ОтложенныйСтарт_Тик` ставился с интервалом **1 секунда**, а YAXUNIT с `closeAfterTests: true` закрывал приложение через ~1с от старта (тесты прогоняются за ~200ms), поэтому idle-handler не успевал тикнуть.

Фикс: уменьшить интервал idle-handler с `1` до `0.1`:
```bsl
// exts/client_mcp/Ext/ManagedApplicationModule.bsl
ПодключитьОбработчикОжидания("Мсп_ОтложенныйСтарт_Тик", 0.1, Истина);
```

После фикса yaxunit-Enterprise регистрируется как `kind=yaxunit_runner` в `session_list` менеджера (подтверждение в stdout v8-runner: `[MCP INFO ...] WS-сессия зарегистрирована: uid=... kind=yaxunit_runner ... tools=24`).

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
