---
name: vanessa-run-loop
description: Изменил .feature / конфиг tests.va / MCP-расширение → обязателен прогон v8-runner test va. Применить навыки v8-runner и vanessa-diagnostics.
alwaysApply: true
---

# Политика прогона Vanessa Automation

> **Триггер:** создание/изменение `.feature`, параметров запуска, конфигурации `tests.va` в `v8project.yaml` или клиентского MCP-расширения. При срабатывании — запустить через `v8-runner test va` (навык `v8-runner`: `framework/skills/tool-usage/v8-runner/SKILL.md`). Диагностика при падении — навык `vanessa-diagnostics` (`framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md`).

## MUST

- **Pre-run config sanity:** перед запуском прочитать активный профиль из `tests.va` в `v8project.yaml` и убедиться, что путь к feature-каталогу указывает на папку **текущей задачи**. При несоответствии — добавить `tests.va.profiles.<taskID>` и запускать с ним. Зафиксировать профиль в `{role}-context.md`.
- **Не обходить v8-runner:** MUST NOT собирать `1cv8c` или `vrunner` команды вручную при наличии `v8-runner test va`. Исключение — диагностика самого v8-runner после явного согласия пользователя.
- **Условие завершения:** ждать `va-status.log` (создаётся при успехе И при ошибке) ИЛИ завершения процесса `1cv8c.*vanessa-automation` ИЛИ строки `ERROR:` в stdout. **Не использовать только `va-status.json`** — при раннем падении его нет, ожидание зависнет.

## Признак успеха (все пять условий MUST быть выполнены)

1. файл `va-status.json` существует;
2. значение в `va-status.json` равно `0`;
3. файл `vanessa-execution.log` создан;
4. в логах нет пропущенных или ненайденных шагов;
5. количество выполненных шагов > 0.

> Vanessa считает прогон успешным даже если ни один шаг не был найден — это **ложный успех**. Проверять пункты 4–5 обязательно.

---
depends_on:
  - framework/skills/tool-usage/v8-runner/SKILL.md
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
---
