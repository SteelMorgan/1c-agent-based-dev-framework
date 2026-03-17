---
name: syntax-checking
description: Проверка синтаксиса (Syntax Checking). Навык учит агента **правильно использовать возможности проверки синтаксиса** BSL-кода.
---

# Проверка синтаксиса (Syntax Checking)

Любое изменение BSL-кода → немедленный `check_syntax`. Без проверки агент может «успешно» завершить задачу с нерабочим кодом.

## Когда применять

| Триггер | Действие |
|---------|----------|
| После изменения BSL-кода | `check_syntax` |
| Перед коммитом | Обязательная проверка изменённых файлов |
| После рефакторинга / `rename_symbol` | Проверка затронутых модулей |
| Ошибка компиляции | `check_syntax` для локализации |

## Алгоритм проверки

1. `check_syntax(target: "путь/Module.bsl", mode: "edt")` — режим по умолчанию (EDT validate).
2. `success = false` → прочитать `errors`, исправить, повторить.
3. EDT недоступен → fallback: `mode: "designer_config"` (CheckConfig) или `mode: "designer_modules"` (CheckModules). Требуют подключения к ИБ.
4. Для быстрой обратной связи по файлу — `get_diagnostics(uri)`. Частичный заменитель, если `check_syntax` недоступен.
5. После рефакторинга нескольких модулей — `target: "all"` или по каждому.

## Интерпретация результатов

| Поле | Действие |
|------|----------|
| `success: true` | Продолжать |
| `success: false` | Исправить `errors` (каждая: `file`, `line`, `message`, `severity`) |
| `warnings` | Оценить критичность |
| Таймаут | Сузить `target` до отдельных модулей |

Severity: `error` (блокирует компиляцию) > `warning` > `information` / `hint`.

При расхождении `get_diagnostics` и `check_syntax` — ориентироваться на `check_syntax` как финальный вердикт.

## Capabilities

| Capability | Назначение |
|------------|------------|
| `check_syntax` | Формальная проверка (EDT / Designer) |
| `get_diagnostics` | LSP-диагностика файла (быстро) |

## Типичные ошибки

| Ошибка | Обходной путь |
|--------|---------------|
| EDT не запущен | `get_diagnostics` или `designer_config` / `designer_modules` |
| Таймаут `target: "all"` | Проверять по модулям |
| Проект EDT не найден | Проверить путь, `sourceSet`; использовать Designer |
| Непонятные `errors` | `navigate_symbol` к месту ошибки; `ask_ai_assistant` |

---
depends_on: []
---
