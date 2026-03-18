---
name: vanessa-run
description: Запуск сценарных тестов Vanessa Automation. Используй, когда нужно выполнить feature-сценарий, проверить baseline запуска, прочитать артефакты прогона или понять, чем запускать Vanessa в проекте.
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

## Файлы

**Baseline EPF:** `/opt/onescript/2.0.0/lib/add/bddRunner.epf`

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

**Библиотечные steps:** `/opt/onescript/2.0.0/lib/add/features/libraries`

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
    "--vanessasettings": "<project_root>/vanessa-tests/runtime/va-params-run.json"
  }
}
```

**`va-params-run.json`** — настройки bddRunner. Взять шаблон `tools/runtime/vanessa/va-params.template.json` и заменить все `$workspaceRoot` на абсолютный путь к проекту. `КаталогФич` указывает на нужный каталог с `.feature`-файлами.

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
  --db-pwd <db_pwd> \
  --pathvanessa "/opt/onescript/2.0.0/lib/add/bddRunner.epf"
```

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
  /Execute"/opt/onescript/2.0.0/lib/add/bddRunner.epf"
```

По умолчанию дисплей `:99`. После завершения прогона закрыть дисплей для освобождения ресурсов X11.

---

## Признак успеха

1. Существует `va-status.json` со значением `0`.
2. Существует `vanessa-execution.log`.

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
