# Тестирование

Используй тесты, когда важно поведение. Тестовые команды сначала собирают, поэтому не запускай отдельный `build`, если пользователь не попросил именно build-only диагностику.

## WS-сопряжение с session-manager на test yaxunit / test va

> **Используемый форк:** WS-транспорт реализован в форке [`SteelMorgan/v8-runner-rust`](https://github.com/SteelMorgan/v8-runner-rust) (upstream: `alkoleft/v8-runner-rust`), т.к. PR-ы в upstream не принимаются. Расширения `mcp_client`/`test_client` входят в [`SteelMorgan/onec-client-mcp-devkit`](https://github.com/SteelMorgan/onec-client-mcp-devkit).

WS-флаги для `test ...` те же, что и для `launch ...`: `--mcp-transport`, `--manager-url`, `--client-uid`, `--corr-id`, `--mcp-log-level`, `--mcp-ws-timeout-ms`. **Тонкость clap-структуры:** на test-командах флаги объявлены на уровне `TestArgs` (через `flatten(McpClientWsArgs)`), то есть **до** подкоманды `yaxunit`/`va`:

```bash
# Правильно — флаги ДО подкоманды
v8-runner test --mcp-transport=ws --mcp-log-level=debug yaxunit module <NAME>
v8-runner test --mcp-transport=ws --mcp-ws-timeout-ms 5000 va

# Неправильно — clap отвечает "error: unexpected argument"
v8-runner test yaxunit module <NAME> --mcp-transport=ws       # ❌
v8-runner test yaxunit --mcp-transport=ws all                 # ❌
```

Подкоманды `test yaxunit ...` / `test yaxunit module ...` / `test va` свой `McpClientWsArgs` не объявляют, поэтому `--help` на их уровне WS-опции не показывает. Чтобы увидеть их — `v8-runner test --help`.

Альтернатива CLI — `tools.client_mcp.*` в `v8project.yaml`:

```yaml
tools:
  client_mcp:
    transport: auto         # ws | legacy | auto
    manager_url: ws://127.0.0.1:4000/sessions
    log_level: info
    ws_timeout_ms: 1000
```

Приоритет: CLI-флаг → yaml → внутренние дефолты.

Маппинг `kind`: `test yaxunit ...` → `yaxunit_runner`, `test va ...` → `vanessa_test_client`. Фиксируется точкой входа, из CLI не переопределяется.

### Диагностика WS-сопряжения test-фазы

Если yaxunit_runner / vanessa_test_client не появляется в `session_list` менеджера:

1. **Лог менеджера** — `/tmp/v8sm/logs/mcp/actions.log` (путь зависит от `workPath` менеджера). Искать `WS connection accepted (handshake completed)` в окне прогона. Запустить менеджер с `--log-level debug`, если он стоит на `info`.
2. **`/C`-payload** — поднять v8-runner с `--log-level=trace` (на уровне глобальных опций) и смотреть, дописался ли `mcpMode=ws;manager_url=...` к `RunUnitTests=...`. Если нет — `decide_mcp_transport` вернул `Legacy`.
3. **Лог Enterprise-1С** — `<workPath>/temp/yaxunit/runs/<run-id>/enterprise.out.log` и `runner.log`. Искать `[MCP INFO ...] Logging params applied` и `Регистрация провайдера ...` — это диагностика MCP-инициализации со стороны BSL devkit.
4. **Stdout v8-runner** — диагностический блок `[MCP INFO ...]` появляется в `diagnostic`-секции `test`-output (только при удачной MCP-инициализации клиента).

Resolved (DRIVE 2026-05-11): yaxunit_runner не регистрировался в `session_list` менеджера, хотя v8-runner правильно подставлял WS-payload в `/C`. ЖР-трассировка показала race condition в BSL: idle-handler `Мсп_ОтложенныйСтарт_Тик` в `client_mcp` ставился с интервалом 1 секунда, YAXUNIT с `closeAfterTests: true` закрывал приложение за ~1с (тесты ~200ms). idle-handler не успевал тикнуть. Фикс: уменьшить интервал `1` → `0.1` в `exts/client_mcp/Ext/ManagedApplicationModule.bsl` (вызов `ПодключитьОбработчикОжидания("Мсп_ОтложенныйСтарт_Тик", 0.1, Истина)`). После фикса yaxunit-Enterprise регистрирует WS-сессию (`kind=yaxunit_runner`, tools=24).

Полное описание (транспорт, дефолты, `/C`-payload, JSON-output, поведение при недоступном менеджере) — в `SKILL.md` (раздел «WS-параметры сопряжения с session-manager») и в `project-workflows.md` (раздел «WS-режим к session-manager»).

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

## Мониторинг прогона Vanessa (MUST)

| Требование | Описание |
|------------|----------|
| Мониторинг stdout v8-runner | Агент MUST читать stdout v8-runner каждые **20 секунд** пока тест выполняется. Стандартный вывод содержит маркеры успеха и падения сразу (`[diagnostic]`, `[artifact]`, `ERROR: runtime error: test run reported failures` и т.п.) — это надёжнее ЖР. |
| Прерывание при ошибке | Если в stdout появилась строка `ERROR:` (например `ERROR: runtime error: test run reported failures`) — агент MUST прервать ожидание, прочитать `runner.log` + `junit/junit.xml` в каталоге прогона и перейти к диагностике. ЖР смотреть дополнительно, если первичных артефактов недостаточно. |
| Детект зависания | Если в stdout v8-runner нет новых строк >60 сек И процесс `1cv8c.*vanessa-automation` ещё жив — агент MUST снять скриншоты (noVNC/X11) и оценить, жив ли тест. |
| Корректное условие завершения | Условие выхода из ожидания: появился `va-status.log` (создаётся И при success, И при failure) ИЛИ исчез процесс `1cv8c.*vanessa-automation` ИЛИ в stdout появился `ERROR:`. **Не использовать только `va-status.json`** — он создаётся только при штатном завершении сценария; при ранних падениях (ошибка шага, краш клиента) его не будет, и блокирующее ожидание зависнет. |
| Обязательный анализ артефактов | После прогона агент MUST проверить `va-status.json` и `vanessa-execution.log` под `workPath/temp/<runner-id>/runs/<run-id>/`. |
| Обязательный анализ ЖР | После прогона агент MUST проверить `event-log`, если сценарий не прошёл или запуск выглядит подозрительно. |
| Пост-валидация успеха | После `va-status == 0` агент MUST проверить логи на полноту: все шаги выполнены, нет пропущенных/ненайденных шагов. |
| Явная классификация | При падении агент MUST вернуть класс ошибки, а не просто текст сбоя. |

**Ложный успех:** Vanessa считает прогон успешным даже если ни один шаг не был найден или часть шагов пропущена. Агент MUST выявлять такие случаи (количество выполненных шагов > 0, нет пропущенных/ненайденных).

## Pre-run config check (для v8-runner)

Перед стартом `v8-runner test va` runner-агент выполняет процедуру:

1. Read `v8project.yaml` → секция `tests.va`, активный профиль (`tests.va.profile` или передаваемый через `--profile`).
2. Сравнить путь к features в профиле с ожидаемым `vanessa-tests/features/tasks/<taskID>/`.
3. При несоответствии — добавить task-dedicated профиль `tests.va.profiles.<taskID>` (либо в `v8project.yaml`, либо через `v8project.local.yaml`), запускать с ним.
4. При работе с тегами помнить: `filter_tags` / `ignore_tags` записываются **без ведущего `@`** в `СписокТеговОтбор` / `СписокТеговИсключение`.
5. Зафиксировать в `{role}-context.md` использованный профиль и каталог фич.

**Почему:** общий профиль в проекте обычно содержит «залипший» путь от последней задачи. Запуск без pre-check подхватывает чужой каталог и крутит сценарии другой задачи десятки минут.

## Контроль результата при длительных прогонах

Команды `v8-runner test yaxunit ...` и `v8-runner test va` — длительные. Вместо слепого опроса файлов используй инструмент Monitor:

1. Запусти v8-runner в фоне (`Bash run_in_background: true`), перенаправь stdout в файл лога.
2. Подпишись на этот файл через инструмент **Monitor** с фильтром на ключевые маркеры: `ERROR:|passed|Failed:|\\[artifact\\]` — каждая совпавшая строка придёт как уведомление.
3. Завершай ожидание при выполнении **любого** условия:
   - Для `test va`: появился `va-status.log` (создаётся при успехе И при ошибке, в отличие от `va-status.json`) ИЛИ процесс `1cv8c.*vanessa` завершился ИЛИ в stdout появилась строка `ERROR:` (например `ERROR: runtime error: test run reported failures`).
   - Для `test yaxunit`: появился `junit/junit.xml` ИЛИ процесс завершился ИЛИ в stdout появилась строка `ERROR:` или `FAIL`.
4. **Не используй `va-status.json` как единственное условие выхода.** Он создаётся только при штатном завершении сценария; при раннем падении файл отсутствует и ожидание по его наличию зависнет навечно.
5. После завершения: прочитать артефакты прогона (`va-status.json` / `junit.xml`), при падении классифицировать ошибку — см. навык `vanessa-diagnostics`.

## Артефакты

Сохраняй артефакты упавших тестов в:

```text
workPath/temp/<runner-id>/runs/<run-id>/
```

В итоговых ответах указывай команду, результат pass/fail и путь к артефакту, если он есть.
