---
name: vanessa-diagnostics
description: "Vanessa diagnostics: падения, артефакты и причины"
---

# Диагностика Vanessa Automation

Запуск Vanessa делается через `v8-runner test va` (см. навык `v8-runner` → `references/testing.md`). Этот навык — про то, как разобрать упавший прогон.

## Артефакты прогона

Слоя два — не путать:

| Слой | Что пишет | Где лежит |
|------|-----------|-----------|
| Артефакты Vanessa | сам VA-плеер (`va-status.json`, `vanessa-execution.log`, отчёты `junit/junit.xml`, `cucumber/CucumberJson.json`) | по путям из активного профиля `tests.va` / `va-params`, обычно project-local (`<project_root>/vanessa-tests/reports/…`, `.../logs/…`) |
| Run-артефакты v8-runner | сам `v8-runner` (внутренние логи запуска, stdout/stderr 1cv8c, метаданные run-id) | `workPath/temp/<runner-id>/runs/<run-id>/` (`workPath` берётся из `v8project.yaml`) |

При падении прогона не очищать **обе** локации до завершения диагностики. Точные пути Vanessa-отчётов читать из активного профиля.

## Мониторинг прогресса во время прогона

Для длительных операций `v8-runner test va` (обычно несколько минут) используй инструмент Monitor вместо слепого опроса файлов:

1. Запусти v8-runner в фоне: `Bash run_in_background: true`, перенаправь stdout в файл лога (например `v8-runner test va 2>&1 | tee /tmp/va-stdout.log`).
2. Подпишись на этот файл через инструмент Monitor с фильтром: `ERROR:|\\[artifact\\]|passed|Failed:` — каждая совпавшая строка придёт как уведомление.
3. Завершай ожидание при выполнении **любого** условия:
   - `va-status.log` появился в каталоге прогона (создаётся при успехе И при ошибке — в отличие от `va-status.json`);
   - процесс `1cv8c.*vanessa-automation` завершился;
   - в stdout появилась строка `ERROR:` (например `ERROR: runtime error: test run reported failures`).
4. **Не используй `va-status.json` как единственное условие выхода.** Он создаётся только при штатном завершении сценария; при раннем падении (ошибка шага, краш клиента) файл отсутствует и ожидание по его наличию зависнет навечно.

После завершения прогона переходи к порядку диагностики ниже.

## Когда применять

| Триггер | Действие |
|---------|----------|
| `va-status.json` не создан | Считать запуск аварийным, идти в диагностику |
| `va-status.json != 0` | Читать артефакты и классифицировать падение |
| `vanessa-execution.log` содержит ошибку | Определить класс ошибки |
| Подозрение на блокировку GUI | Визуальная диагностика по `va-visual-check`: сначала VA MCP-скриншот, при необходимости fallback с фиксацией причины |
| Прогон «зелёный», но 0 шагов выполнено / шаги `undefined`/`skipped` | Ложный успех — классифицировать как `step_resolution_error`/`scenario_error` |

---

## Обязательный порядок диагностики

1. Проверить `va-status.json`.
2. Проверить `vanessa-execution.log`.
3. Проверить `event-log`: сначала последние `Error`; если пусто — без фильтра уровня.
4. Если нужно увидеть состояние формы тест-клиента — применяй `va-visual-check`: VA MCP-скриншот, проверка PNG, затем fallback при необходимости.
5. Если сигнал на модальное окно / security warning / manager window, визуальный артефакт также получай по `va-visual-check`.
6. Только если недостаточно — `tech-log-analysis`.

### Special-case: `Предупреждение безопасности`

Если в `event-log` запись о `Предупреждение безопасности` для `bddRunner.epf` или плагинов:
1. Считать триггером на визуальную проверку.
2. Снять реальный экран по `va-visual-check`, не полагаясь только на заголовки X11-окон.
3. Только после визуального подтверждения трактовать повторный запуск.

---

## Классы ошибок

| Класс | Когда ставить |
|-------|---------------|
| `scenario_error` | Сценарий неверно сформулирован или использует неподходящий поток |
| `step_resolution_error` | Нужный шаг не найден или не резолвится |
| `assertion_error` | Шаги выполнились, проверка результата не совпала |
| `test_data_error` | Зависит от отсутствующих/неподходящих данных |
| `environment_error` | Проблема в X11, окружении, runner, запуске клиента |
| `product_ui_error` | Ошибка видимого поведения формы или UI-потока |
| `product_logic_error` | Бизнес-логика даёт неверный результат при корректном сценарии |

### Быстрая эвристика

| Сигнал | Класс |
|--------|-------|
| Нет `va-status.json`, GTK/X11 error | `environment_error` |
| Не найден шаг | `step_resolution_error` |
| Форма открылась, ожидание не совпало | `assertion_error` / `product_ui_error` |
| Ошибка из бизнес-модуля в ЖР | `product_logic_error` |
| Документ/объект не найден | `test_data_error` |

---

## Результат диагностики

Агент должен сообщить: класс ошибки, главный источник сигнала, следующий контур действий.

```text
failure_type = test_data_error
main_signal = document not found in event log / form flow
next_action = choose another fixture or prepare stable test data
```

---
depends_on:
  - framework/rules/vanessa-diagnostics-policy/SKILL.md
  - framework/rules/vanessa-security-warning/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
  - framework/skills/tool-usage/browser-ui/gui-control/SKILL.md
  - framework/skills/tool-usage/browser-ui/screenshot/SKILL.md
---
