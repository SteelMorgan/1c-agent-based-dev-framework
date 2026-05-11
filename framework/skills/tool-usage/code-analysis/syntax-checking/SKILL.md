---
name: syntax-checking
description: "Проверка синтаксиса (Syntax Checking). Навык учит агента **правильно использовать возможности проверки синтаксиса** BSL-кода."
uses_capabilities:
  - get_diagnostics
  - syntax_check_designer_modules
  - syntax_check_designer_config
  - syntax_check_edt
---

# Проверка синтаксиса (Syntax Checking)

Любое изменение BSL-кода → немедленная проверка. Без проверки агент может «успешно» завершить задачу с нерабочим кодом.

**Два уровня проверки — разная стоимость:**

| Инструмент | Скорость | Когда использовать |
|------------|----------|-------------------|
| `get_diagnostics` (LSP) | Быстро (секунды) | После каждого изменения, промежуточные проверки |
| `v8-runner syntax …` | Медленно (десятки секунд — минуты) | Финальная проверка: перед коммитом, перед PR, после крупного рефакторинга |

Серверная проверка теперь делается **только через CLI v8-runner** — отдельные MCP-tools `check_syntax`/`build_project`/`dump_config` упразднены. Подробности команд и правил выбора — в навыке `v8-runner` (`framework/skills/tool-usage/v8-runner/`).

## Когда применять

| Триггер | Действие |
|---------|----------|
| После изменения BSL-кода | `get_diagnostics` — быстрая проверка |
| Итеративная правка (цикл edit → check) | `get_diagnostics` |
| После рефакторинга / `rename_symbol` | `get_diagnostics` по затронутым файлам |
| Ошибка компиляции | `get_diagnostics` для локализации |
| **Перед коммитом / перед PR** | **`v8-runner syntax …`** — финальная проверка |
| **Завершение задачи** | **`v8-runner syntax …`** — финальный вердикт |

## Алгоритм проверки

### Промежуточная проверка (после каждого изменения)

1. `get_diagnostics(uri)` — LSP-диагностика изменённого файла.
2. Если есть `error`-уровень — исправить и повторить.
3. `warning` — оценить критичность.

### Финальная проверка (перед коммитом)

Выбор команды зависит от `format`/`builder` в `v8project.yaml` (см. `v8-runner/references/config-and-backends.md`):

```bash
# Designer-модули (требует Designer + Designer-формат)
v8-runner build
v8-runner syntax designer-modules --server --thin-client

# Designer-конфигурация
v8-runner build
v8-runner syntax designer-config

# EDT
v8-runner build
v8-runner syntax edt
```

Тесты (`v8-runner test yaxunit …`, `test va`) делают `build` сами — отдельный `build` перед ними не нужен.

При расхождении LSP и `v8-runner syntax` — ориентироваться на v8-runner как финальный вердикт.

## Интерпретация результатов

| Поле | Действие |
|------|----------|
| `success: true` | Продолжать |
| `success: false` | Исправить `errors` (каждая: `file`, `line`, `message`, `severity`) |
| `warnings` | Оценить критичность |
| Таймаут | Сузить scope (`--source-set <NAME>`) или вернуться к LSP по конкретным модулям |

Severity: `error` (блокирует компиляцию) > `warning` > `information` / `hint`.

## Capabilities и инструменты

| Capability / CLI | Назначение | Стоимость |
|------------------|------------|-----------|
| `get_diagnostics` (MCP `lsp-bsl-bridge`) | LSP-диагностика файла | Быстро — основной инструмент |
| `v8-runner syntax designer-modules` | Проверка Designer-модулей через платформу | Медленно — только финальная проверка |
| `v8-runner syntax designer-config` | Проверка Designer-конфигурации | Медленно |
| `v8-runner syntax edt` | Проверка EDT-проекта | Медленно |

## Типичные ошибки

| Ошибка | Обходной путь |
|--------|---------------|
| LSP не запущен | `v8-runner syntax …` как fallback |
| Команда `syntax …` не поддерживается для текущего `format`/`builder` | См. `v8-runner/references/config-and-backends.md`; не изобретать raw `1cv8`/`ibcmd`-флаги |
| Таймаут на полной проверке | Сузить через `--source-set <NAME>`; LSP по конкретным модулям |
| Проект EDT не найден | Проверить `format`/`builder` и `source-set` в `v8project.yaml` |
| Непонятные `errors` | `navigate_symbol` к месту ошибки; `ask_ai_assistant` |

---
depends_on:
  - framework/skills/tool-usage/v8-runner/SKILL.md
---
