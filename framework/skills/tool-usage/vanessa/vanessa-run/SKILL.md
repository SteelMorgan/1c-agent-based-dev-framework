---
name: vanessa-run
description: Запуск сценарных тестов Vanessa Automation. Используй, когда нужно выполнить feature-сценарий, проверить baseline запуска, прочитать артефакты прогона или понять, чем запускать Vanessa в проекте.
---

# Запуск Vanessa Automation

## Назначение

Навык фиксирует, как именно запускать сценарные тесты Vanessa Automation, где искать baseline запуска и какие артефакты считать результатом прогона.

Главная цель — не переизобретать команду запуска в каждом сеансе и не терять время на поиск baseline.

---

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно запустить `.feature`-сценарии Vanessa | Использовать этот навык как baseline запуска |
| Нужно понять, где лежат settings и артефакты | Определить, какие файлы shared, а какие project-local |
| Нужно проверить, успешен ли прогон | Проверить `va-status.json` и `vanessa-execution.log` |
| Нужно переключиться между прямым запуском и `vrunner` | Использовать команды из этого навыка |

---

## Универсальные и project-local файлы

### Универсальный baseline EPF

```text
/opt/onescript/2.0.0/lib/add/bddRunner.epf
```

### Shared runtime templates

Эти файлы находятся во framework и используются как универсальные шаблоны запуска:

```text
/.../tools/runtime/vanessa/va-params.template.json
/.../tools/runtime/vanessa/va-params-debug.template.json
/.../tools/runtime/vanessa/vrunner-va.json
```

### Project-local runtime files

```text
<project_root>/vanessa-tests/features
<project_root>/vanessa-tests/support
<project_root>/build/vanessa/reports/va-status.json
<project_root>/build/vanessa/logs/vanessa-execution.log
<project_root>/build/vanessa/reports/junit/junit.xml
<project_root>/build/vanessa/reports/cucumber/CucumberJson.json
```

### Важное разделение

- `tools/runtime/vanessa/*.json` — shared runtime templates фреймворка.
- Если в них зашиты пути, feature-каталоги, данные или настройки конкретной базы, они должны существовать как project-local runtime copies.
- `feature`-сценарии и test data относятся к конкретному проекту и должны быть project-local.
- Универсальные библиотечные feature/steps Vanessa лежат в каталоге инструментов:

```text
/opt/onescript/2.0.0/lib/add/features/libraries
```

---

## Базовые способы запуска

### 1. Прямой запуск через `1cv8c`

Подставь project-local `project_root` и runtime settings file:

```bash
DISPLAY=:110 /opt/1cv8/x86_64/8.3.27.1719/1cv8c ENTERPRISE \
  /S"<ib_connection>" \
  /N"<db_user>" \
  /P"<db_pwd>" \
  /Lru /VLru_RU \
  /DisableStartupMessages /DisableStartupDialogs \
  /C"StartFeaturePlayer;workspaceRoot=<project_root>;VBParams=<runtime_va_params_json>" \
  /out"/tmp/va-run.out" \
  /TESTMANAGER \
  /Execute"/opt/onescript/2.0.0/lib/add/bddRunner.epf"
```

### 2. Запуск через `vrunner`

Подставь project-local runtime settings file:

```bash
DISPLAY=:110 vrunner vanessa \
  --settings <runtime_vrunner_va_json> \
  --ibconnection /S"<ib_connection>" \
  --db-user <db_user> \
  --db-pwd <db_pwd> \
  --pathvanessa "/opt/onescript/2.0.0/lib/add/bddRunner.epf"
```

---

## Признак успеха

Прогон успешен только если:

1. существует файл `<project_root>/build/vanessa/reports/va-status.json`;
2. в нём значение `0`;
3. существует `<project_root>/build/vanessa/logs/vanessa-execution.log`.

---

## Если запуск не удался

1. Проверить, есть ли живой `DISPLAY`.
2. Проверить `va-status.json`.
3. Проверить `vanessa-execution.log`.
4. Перейти к диагностике через:
   - `event-log-analysis`
   - `gui-control`
   - `screenshot`
   - `tech-log-analysis` только последним

---

## Типичные ошибки

| Ошибка | Что делать |
|--------|------------|
| `va-status.json` не создан | Проверить X11/GUI, затем `event-log` |
| `DISPLAY` не поднят | Поднять/использовать рабочий X11 display |
| Runner завершился, но артефактов нет | Считать запуск невалидным и идти в диагностику |
| Появилось `Предупреждение безопасности` | Применить правило `vanessa-security-warning` |

---

## Связанные ресурсы

- `framework/rules/vanessa-run-loop.mdc`
- `framework/rules/vanessa-tests-location.mdc`
- `framework/rules/vanessa-security-warning.mdc`
- `framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md`
- `framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md`
- `tools/runtime/vanessa/va-params.template.json`
- `tools/runtime/vanessa/va-params-debug.template.json`
- `tools/runtime/vanessa/vrunner-va.json`

---
depends_on:
  - framework/rules/vanessa-run-loop.mdc
  - framework/rules/vanessa-tests-location.mdc
  - framework/rules/vanessa-security-warning.mdc
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
---
