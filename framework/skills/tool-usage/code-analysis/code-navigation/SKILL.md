---
name: code-navigation
description: Навигация по коду (Code Navigation). Навык учит агента **эффективно перемещаться по BSL-коду** с помощью LSP (Language Server Protocol).
---

# Навигация по коду (Code Navigation)

Не угадывать расположение кода — использовать LSP. Точные результаты по индексу проекта.

## Когда применять

| Триггер | Действие |
|---------|----------|
| Поиск определений процедуры/функции | `navigate_symbol` operation `definition` |
| Все вызовы функции X | `navigate_symbol` `search` или `get_call_graph` `incoming` |
| Кого вызывает функция | `get_call_graph` `outgoing` |
| Переименование по проекту | `rename_symbol` (сначала `preview: true`) |
| Quick Fixes | `get_code_actions` |
| Диагностика файла | `get_diagnostics` |
| Исследование неизвестного кода | `navigate_symbol` → `get_call_graph` → hover |
| Ошибка «метод не найден» на типе платформы | `getMembers` / `getMember` / `getConstructors` |

## Алгоритмы

### Найти все вызовы функции

1. `navigate_symbol(query: "ИмяФункции", operation: "search")` → получить `uri`, `line`, `character`
2. `get_call_graph(uri, line, character, direction: "incoming")`

### Переименование по проекту

1. `navigate_symbol` → `uri`, `line`, `character`
2. `rename_symbol(..., preview: true)` → проверить `changes`
3. `rename_symbol(..., preview: false)`
4. `check_syntax`

### Quick Fixes

1. `get_diagnostics(uri)` → список диагностик
2. `get_code_actions(uri, range, diagnostic)` → применить

### Верификация API платформы после ошибки

**Триггер:** ошибка «Метод объекта не обнаружен» / «Неверное количество параметров» на типе платформы. Не угадывать повторно — верифицировать.

1. `search_syntax_reference(query: "ТипОбъекта")` → подтвердить имя, получить `id`
2. `getMembers(typeId)` → точный список методов/свойств
3. `getMember(typeId, member)` → сигнатура конкретного метода
4. `getConstructors(typeId)` → если ошибка про параметры `Новый`

**Важно:** Только реакция на ошибку, не превентивный поиск.

## Capabilities

| Capability | Назначение |
|------------|------------|
| `navigate_symbol` | Поиск символов, определение, hover |
| `get_call_graph` | Граф вызовов (incoming/outgoing) |
| `rename_symbol` | Переименование по проекту |
| `get_diagnostics` | LSP-диагностика файла |
| `get_code_actions` | Quick Fixes |
| `search_syntax_reference` | Поиск типа платформы |
| `getMembers` / `getMember` | Методы/свойства типа платформы |
| `getConstructors` | Конструкторы типа (`Новый`) |

## Типичные ошибки

| Ошибка | Обходной путь |
|--------|---------------|
| LSP не подключён | Проверить `lsp_status`; запустить BSL Language Server |
| Символ не найден | Проверить имя (регистр, язык); `ask_ai_assistant` (шаблон SEARCH_DOCS из `buddy-prompting`) по имени метода/типа |
| `get_call_graph` таймаут | Уменьшить `depth` |
| `rename_symbol` не применим | Проверить позицию курсора; защищённая область → ручное редактирование |
| Файл не индексирован | Дождаться индексации LSP |

---
depends_on: []
---
