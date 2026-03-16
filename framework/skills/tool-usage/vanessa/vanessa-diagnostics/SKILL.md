---
name: vanessa-diagnostics
description: Диагностика проблем прогона Vanessa Automation. Используй, когда feature-сценарий не прошёл, артефакты не создались или нужно классифицировать сбой после запуска.
---

# Диагностика Vanessa Automation

## Назначение

Навык задаёт как разбирать неуспешный прогон Vanessa Automation и как классифицировать результат так, чтобы следующий агентный шаг был очевиден.

---

## Когда применять

| Триггер | Действие |
|---------|----------|
| `va-status.json` не создан | Считать запуск аварийным, идти в диагностику |
| `va-status.json != 0` | Читать артефакты и классифицировать падение |
| `vanessa-execution.log` содержит ошибку | Определить класс ошибки |
| Есть подозрение на блокировку GUI | Идти в визуальную диагностику |

---

## Обязательный порядок диагностики

1. Проверить `va-status.json`.
2. Проверить `vanessa-execution.log`.
3. Проверить `event-log`:
   - сначала последние `Error`;
   - если пусто, последние записи без фильтра уровня.
4. Если есть сигнал на модальное окно или security warning — перейти к `gui-control` / `screenshot`.
5. Только если этого недостаточно — использовать `tech-log-analysis`.

### Special-case: `Предупреждение безопасности`

Если в `event-log` есть запись о `Предупреждение безопасности` для `bddRunner.epf` или его плагинов:

1. считать это триггером на визуальную проверку;
2. не полагаться только на заголовки X11-окон;
3. открыть реальный экран через noVNC или снять скриншот;
4. только после визуального подтверждения трактовать повторный запуск как валидный или невалидный.

---

## Классы ошибок

| Класс | Когда ставить |
|-------|---------------|
| `scenario_error` | Сценарий неверно сформулирован или использует неподходящий поток |
| `step_resolution_error` | Нужный шаг не найден или не резолвится |
| `assertion_error` | Шаги выполнились, но проверка результата не совпала с ожиданием |
| `test_data_error` | Сценарий зависит от отсутствующих/неподходящих данных |
| `environment_error` | Проблема в X11, окружении, доступности runner, запуске клиента |
| `product_ui_error` | Ошибка видимого поведения формы или UI-потока |
| `product_logic_error` | Бизнес-логика даёт неверный результат при корректном сценарии |

---

## Быстрая эвристика

| Сигнал | Класс |
|--------|-------|
| Нет `va-status.json`, GTK/X11 error | `environment_error` |
| Не найден шаг | `step_resolution_error` |
| Форма открылась, ожидание не совпало | `assertion_error` или `product_ui_error` |
| Ошибка из бизнес-модуля в ЖР | `product_logic_error` |
| Документ/объект не найден на базе | `test_data_error` |

---

## Что вернуть после диагностики

В результате агент должен явно сообщить:

1. класс ошибки;
2. главный источник сигнала;
3. следующий контур действий.

Пример структуры:

```text
failure_type = test_data_error
main_signal = document not found in event log / form flow
next_action = choose another fixture or prepare stable test data
```

---

## Связанные ресурсы

- `framework/rules/vanessa-diagnostics-policy.mdc`
- `framework/rules/vanessa-security-warning.mdc`
- `framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md`
- `framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md`
- `framework/skills/tool-usage/browser-ui/gui-control/SKILL.md`
- `framework/skills/tool-usage/browser-ui/screenshot/SKILL.md`

---
depends_on:
  - framework/rules/vanessa-diagnostics-policy.mdc
  - framework/rules/vanessa-security-warning.mdc
---
