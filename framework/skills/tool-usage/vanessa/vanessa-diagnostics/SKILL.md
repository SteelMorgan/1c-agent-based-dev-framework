---
name: vanessa-diagnostics
description: "Диагностика проблем прогона Vanessa Automation. Используй, когда feature-сценарий не прошёл, артефакты не создались или нужно классифицировать сбой после запуска."
---

# Диагностика Vanessa Automation

## Когда применять

| Триггер | Действие |
|---------|----------|
| `va-status.json` не создан | Считать запуск аварийным, идти в диагностику |
| `va-status.json != 0` | Читать артефакты и классифицировать падение |
| `vanessa-execution.log` содержит ошибку | Определить класс ошибки |
| Подозрение на блокировку GUI | Визуальная диагностика |

---

## Обязательный порядок диагностики

1. Проверить `va-status.json`.
2. Проверить `vanessa-execution.log`.
3. Проверить `event-log`: сначала последние `Error`; если пусто — без фильтра уровня.
4. Если сигнал на модальное окно / security warning — `gui-control` / `screenshot`.
5. Только если недостаточно — `tech-log-analysis`.

### Special-case: `Предупреждение безопасности`

Если в `event-log` запись о `Предупреждение безопасности` для `bddRunner.epf` или плагинов:
1. Считать триггером на визуальную проверку.
2. Открыть реальный экран через noVNC или снять скриншот (не полагаться на заголовки X11-окон).
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
  - framework/rules/vanessa-diagnostics-policy.mdc
  - framework/rules/vanessa-security-warning.mdc
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
  - framework/skills/tool-usage/browser-ui/gui-control/SKILL.md
  - framework/skills/tool-usage/browser-ui/screenshot/SKILL.md
---
