---
name: vanessa-run
description: "Запуск сценарных тестов Vanessa Automation. Используй, когда нужно выполнить feature-сценарий, проверить baseline запуска, прочитать артефакты прогона или понять, чем запускать Vanessa в проекте."
---

# Запуск Vanessa Automation

## Когда применять

| Триггер | Действие |
|---------|----------|
| Запуск `.feature`-сценариев | Использовать baseline из этого навыка |
| Проверка успеха прогона | `va-status.json` + `vanessa-execution.log` |
| Выбор способа запуска | `vrunner` (основной) или `1cv8c` (запасной) |

---

## Подключение

Строка подключения, пользователь и пароль — в `<project_root>/configs/yaxunit-runner.yml`, секция `app.connection`.

---

## Файлы (НЕ УГАДЫВАТЬ)

Используется **только Vanessa-Automation single** (Pr-Mex/vanessa-automation). Никаких альтернативных runner-ов в проектах не применяем.

**Точка входа:** `<framework_repos>/vanessa-automation/dist/vanessa-automation/vanessa-automation-single.epf`

**Каталог инструментов:** `<framework_repos>/vanessa-automation/dist/vanessa-automation` — там же лежит **родной** `plugins/` (4 плагина с 1-параметровой сигнатурой `ОписаниеПлагина(ВозможныеТипыПлагинов)`).

**Антиприём (был корневой причиной VA-ERR-00001):** направить `КаталогИнструментов` на `/opt/onescript/2.0.0/lib/add`. VA-single ищет плагины по пути `КаталогИнструментов + "plugins/"` и в этом случае подхватит чужие плагины с двухпараметровой сигнатурой `ОписаниеПлагина(КонтекстЯдра, ВозможныеТипыПлагинов)` → `Недостаточно фактических параметров` для каждого плагина → шаги уходят в `Pending: Пустой адрес снипета`. **Не повторять.**

### Где взять Vanessa-Automation

Разворачивается **в каталоге фреймворка**, один комплект на все проекты:

```bash
git clone https://github.com/Pr-Mex/vanessa-automation '<framework_repos>/vanessa-automation'
cd '<framework_repos>/vanessa-automation'
gh release download <tag> --repo Pr-Mex/vanessa-automation \
  --pattern 'vanessa-automation.<tag>.zip' --pattern 'vanessa-automation-single.<tag>.zip'
unzip vanessa-automation.<tag>.zip -d dist/                # каталог с epf+plugins+locales
unzip vanessa-automation-single.<tag>.zip -d dist-single/  # single-EPF отдельно
cp dist-single/vanessa-automation-single.epf dist/vanessa-automation/  # положить single рядом с plugins
```

После распаковки:

```
<framework_repos>/vanessa-automation/dist/vanessa-automation/
├── vanessa-automation.epf            # точка входа полной сборки (резерв)
├── vanessa-automation-single.epf     # точка входа single-сборки (используем эту)
├── plugins/                          # 4 РОДНЫХ плагина с 1-параметровой сигнатурой
│   ├── ЗагрузчикПользовательскихНастроек.epf
│   ├── ЗапросыИзБД.epf
│   ├── СериализаторMXL.epf
│   └── УтвержденияBDD.epf
├── locales/                          # Messages.epf, Steps.epf
└── ...                               # tools/, features/, lib/ и т.п.
```

**Shared runtime templates:** `tools/runtime/vanessa/va-params.template.json`, `va-params-debug.template.json`, `vrunner-va.json`

**Project-local:**

```text
<project_root>/vanessa-tests/features
<project_root>/vanessa-tests/support
<project_root>/vanessa-tests/reports/va-status.json
<project_root>/vanessa-tests/logs/vanessa-execution.log
<project_root>/vanessa-tests/reports/junit/junit.xml
<project_root>/vanessa-tests/reports/cucumber/CucumberJson.json
```

**Библиотечные steps:** `<framework_repos>/vanessa-automation/dist/vanessa-automation/features/libraries`

Сценарии и test data проекта — всегда project-local. Shared templates с проектными путями копируются в project-local runtime.

---

## Runtime-конфиг

Перед запуском необходимо подготовить два файла в `<project_root>/vanessa-tests/runtime/`:

**`vrunner-va-run.json`** — конфиг vrunner-а:
```json
{
  "default": {
    "--v8version": "<platform_version>",
    "--language": "ru", "--locale": "ru_RU",
    "--workspace": "<project_root>",
    "--root": "<project_root>",
    "--nocacheuse": true,
    "--debuglogfile": "<project_root>/vanessa-tests/logs/vrunner-debug.log"
  },
  "vanessa": {
    "--workspace": "<project_root>",
    "--vanessasettings": "<project_root>/vanessa-tests/runtime/va-params-run.json",
    "--pathvanessa": "<framework_repos>/vanessa-automation/dist/vanessa-automation/vanessa-automation-single.epf"
  }
}
```

**`va-params-run.json`** — настройки VA-single. Взять шаблон `tools/runtime/vanessa/va-params.template.json` и:
- заменить все `$workspaceRoot` на абсолютный путь к проекту;
- `КаталогФич` указывает на каталог с `.feature`-файлами;
- `КаталогИнструментов` = `<framework_repos>/vanessa-automation/dist/vanessa-automation` (НЕ `/opt/onescript/2.0.0/lib/add`);
- `ИспользоватьКомпонентуVanessaExt = "Истина"` — нужно для `(Расширение)`-шагов.

---

## Обновление базы перед прогоном

**Порядок подготовки к прогону:**
1. `build_project` (MCP `yaxunit-runner`) — сборка и загрузка конфигурации в базу.
2. Обновление базы командой ниже — **только если** были структурные изменения.
3. Запуск Vanessa (способы ниже).

Если после загрузки конфигурации в базу были сделаны структурные изменения — прогон Vanessa упадёт на обращении к новым объектам. Нужен запуск в режиме обновления.

**Когда обязательно** (меняется структура СУБД или права):
- Новая роль или изменение состава роли
- Новый реквизит / объект метаданных (справочник, документ, регистр)
- Новый предопределённый элемент
- Изменение типа реквизита, удаление объектов

**Когда не нужно** (только код/интерфейс):
- Изменение кода модуля, добавление общего модуля, изменение макетов, СКД, подсистем

**Команда** (параметры берутся из `configs/yaxunit-runner.yml`):

```bash
# <platform_version> — поле platform-version
# <server>, <base>   — из connection-string: Srvr='<server>';Ref='<base>';
# <db_user>, <db_pwd> — поля user / password

DISPLAY=:99 /opt/1cv8/x86_64/<platform_version>/1cv8c ENTERPRISE \
  /S"<server>\\<base>" \
  /N"<db_user>" \
  /P"<db_pwd>" \
  /C"ЗапуститьОбновлениеИнформационнойБазы" \
  /DisableStartupMessages \
  /DisableStartupDialogs \
  /Out"/tmp/1c-update.out"
```

> Имя ключа `/C "ЗапуститьОбновлениеИнформационнойБазы"` перевода не требует — это системная константа платформы, не пользовательская строка. Для баз на английской БСП используй `/C"StartInfobaseUpdate"`.

Дождаться завершения (окно закроется автоматически). Проверить `/tmp/1c-update.out` на ошибки.

---

## Способы запуска

### 1. Через `vrunner` (предпочтительный)

Формат `--ibconnection` для серверной базы: `/S<server>\<base>` (без кавычек — vrunner добавит их сам).

Строку подключения из `yaxunit-runner.yml` (`Srvr='server';Ref='base';`) преобразуй в `/Sserver\base`.

```bash
DISPLAY=:99 vrunner vanessa \
  --settings '<project_root>/vanessa-tests/runtime/vrunner-va-run.json' \
  --ibconnection '/S<server>\<base>' \
  --db-user <db_user> \
  --db-pwd <db_pwd>
```

`--pathvanessa` берётся из `vrunner-va-run.json` (ключ `vanessa.--pathvanessa` указывает на VA-single — см. секцию «Runtime-конфиг»). Передавать его повторно через CLI не нужно.

### 2. Через `1cv8c` (запасной)

Версию платформы брать из `configs/yaxunit-runner.yml`, поле `platform-version`.

```bash
DISPLAY=:99 /opt/1cv8/x86_64/<platform_version>/1cv8c ENTERPRISE \
  /S"<server>\\<base>" \
  /N"<db_user>" \
  /P"<db_pwd>" \
  /Lru /VLru_RU \
  /DisableStartupMessages /DisableStartupDialogs \
  /C"StartFeaturePlayer;workspaceRoot=<project_root>;VBParams=<project_root>/vanessa-tests/runtime/va-params-run.json" \
  /out"/tmp/va-run.out" \
  /TESTMANAGER \
  /Execute"<framework_repos>/vanessa-automation/dist/vanessa-automation/vanessa-automation-single.epf"
```

По умолчанию дисплей `:99`. После завершения прогона закрыть дисплей для освобождения ресурсов X11.

---

## Мониторинг во время прогона

Тест Vanessa выполняется в отдельном процессе 1С. Пока он работает, агент **не ждёт пассивно**, а мониторит состояние каждые **20 секунд**.

### Цикл мониторинга

```
Пока процесс 1С жив:
  1. Запросить event-log за последние 30 сек (event-log-analysis)
  2. Классифицировать записи:
     a) Есть Error → ПРЕРВАТЬ тест, перейти к диагностике
     b) Есть записи действий пользователя → тест работает, продолжить ожидание
     c) Нет записей вообще (>60 сек) → подозрение на зависание → шаг 3
  3. Снять 2-3 скриншота с интервалом 5 сек (screenshot)
     - Картинка меняется → тест жив, продолжить ожидание
     - Картинка не меняется → тест завис; прервать, перейти к диагностике
  4. sleep 20 сек
```

### На что обращать внимание в ЖР

| Сигнал в ЖР | Интерпретация | Действие |
|--------------|---------------|----------|
| `Error` любого типа | Сбой в тесте или продукте | Прервать, диагностика |
| Действия пользователя (открытие форм, запись, проведение) | Тест выполняет шаги | Продолжить ожидание |
| `Предупреждение безопасности` | Модальное окно блокирует тест | `gui-control` / `screenshot` → обработать |
| Пусто >60 сек | Возможное зависание | Скриншоты для подтверждения |

---

## Признак успеха

1. Существует `va-status.json` со значением `0`.
2. Существует `vanessa-execution.log`.

Это **минимальный** признак. После него обязательна пост-валидация (см. ниже).

---

## Пост-валидация успешного прогона

`va-status.json == 0` не гарантирует реальный успех. Vanessa **не считает ошибкой**:
- сценарий без шагов (пустой `.feature`);
- шаги, которые не были найдены в библиотеке — они тихо пропускаются;
- сценарий, исключённый тегом.

### Процедура проверки

1. **Прочитать `vanessa-execution.log`** — найти строки с итогами:
   - Количество выполненных сценариев и шагов
   - Если `0 шагов выполнено` → **ложный успех**, классифицировать как `step_resolution_error`

2. **Прочитать отчёт** (`CucumberJson.json` или `junit.xml`) — проверить:
   - Каждый сценарий из `.feature` присутствует в отчёте
   - Каждый шаг имеет статус `passed`, а не `undefined` / `skipped`

3. **Сопоставить с ожидаемыми шагами** — сравнить шаги из `.feature` с реально выполненными:
   - Все шаги выполнены → **истинный успех**
   - Часть шагов `undefined`/`skipped` → **ложный успех** → классифицировать и сообщить

### Классификация ложного успеха

| Ситуация | Класс ошибки | Следующее действие |
|----------|-------------|-------------------|
| 0 шагов выполнено | `step_resolution_error` | Проверить привязку шагов к библиотеке |
| Часть шагов `undefined` | `step_resolution_error` | Найти или создать недостающие шаги |
| Часть шагов `skipped` | `scenario_error` | Проверить логику сценария и условия |
| Сценарий отсутствует в отчёте | `environment_error` | Проверить теги и фильтры запуска |

---

## Если запуск не удался

1. Проверить `DISPLAY`.
2. Проверить `va-status.json` и `vanessa-execution.log`.
3. Диагностика: `event-log-analysis` → `gui-control` / `screenshot` → `tech-log-analysis` (последним).

---

## Типичные ошибки

| Ошибка | Что делать |
|--------|------------|
| `va-status.json` не создан | Проверить X11/GUI, затем `event-log` |
| `DISPLAY` не поднят | Поднять/использовать рабочий X11 display |
| Runner завершился без артефактов | Считать невалидным, идти в диагностику |
| `Предупреждение безопасности` | Правило `vanessa-security-warning` |
| `Неопределена информационная база` | Неверный формат `--ibconnection`; для серверных баз — `/Sserver\base` |
| Список сценариев пуст (0 выполнено) | Проверить теги — тег `@draft` исключает сценарий из прогона |

---
depends_on:
  - framework/rules/vanessa-run-loop.mdc
  - framework/rules/vanessa-tests-location.mdc
  - framework/rules/vanessa-security-warning.mdc
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
requires:
  - tools
---
