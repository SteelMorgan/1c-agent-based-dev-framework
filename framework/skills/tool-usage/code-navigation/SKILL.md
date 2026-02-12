---
id_deprecated: skill/code-navigation
type: skill
depends_on: []
name: code-navigation
description: Навигация по коду (Code Navigation). Навык учит агента **эффективно перемещаться по BSL-коду** с помощью LSP (Language Server Protocol).
---


# Навигация по коду (Code Navigation)

## Назначение

Навык учит агента **эффективно перемещаться по BSL-коду** с помощью LSP (Language Server Protocol). Навигация — основа понимания кодовой базы, рефакторинга и поиска причин ошибок.

**Принцип:** Не угадывать расположение кода — использовать структурированный поиск. LSP даёт точные результаты по индексу проекта.

---

## Когда применять

| Триггер | Действие |
|---------|----------|
| Поиск определений процедуры/функции | `navigate_symbol` operation `definition` |
| Поиск всех вызовов функции X | `navigate_symbol` operation `search` или `get_call_graph` direction `incoming` |
| Понять, кого вызывает функция | `get_call_graph` direction `outgoing` |
| Переименование символа по проекту | `rename_symbol` (сначала `preview: true`) |
| Быстрые исправления по предложениям LSP | `get_code_actions` |
| Диагностика текущего файла | `get_diagnostics` |
| Исследование неизвестного кода | Цепочка: `navigate_symbol` → `get_call_graph` → hover |

---

## Сценарии использования

### Сценарий 1: Найти все места, вызывающие функцию

**Шаги:**

1. `navigate_symbol` с `query: "ПолучитьОстатки"`, `operation: "search"` — найти определение.
2. Получить `uri`, `line`, `character` определения.
3. `get_call_graph` с `uri`, `line`, `character`, `direction: "incoming"` — кто вызывает.
4. Либо `navigate_symbol` с `operation: "search"` по имени и фильтрация по типу «references».

**Пример:** Найти все вызовы `ПолучитьОстатки()`.

```
1. navigate_symbol(query: "ПолучитьОстатки", operation: "search")
2. Получить первый результат — definition
3. get_call_graph(uri: "...", line: N, character: M, direction: "incoming")
```

### Сценарий 2: Переименование символа по проекту

**Шаги:**

1. `navigate_symbol` — найти символ, получить `uri`, `line`, `character`.
2. `rename_symbol` с `preview: true` — предпросмотр изменений во всех файлах.
3. Проверить `changes` — корректность замен.
4. Если всё верно — `rename_symbol` с `preview: false` для применения.
5. `check_syntax` — проверка после переименования.

**Пример:** Переименовать `ОбработатьДанные` в `ЗагрузитьДанные`.

```
1. navigate_symbol(query: "ОбработатьДанные", operation: "search")
2. rename_symbol(uri: "...", line: N, character: M, new_name: "ЗагрузитьДанные", preview: true)
3. Анализ changes
4. rename_symbol(..., preview: false)
5. check_syntax(...)
```

### Сценарий 3: Исследование неизвестного кода

**Шаги:**

1. `navigate_symbol` (operation `search`) — найти символ по имени.
2. `navigate_symbol` (operation `hover`) — получить документацию, тип.
3. `get_call_graph` — понять входящие и исходящие вызовы.
4. Рекурсивно переходить к связанным символам.

**Стратегия:** `navigate_symbol` → `get_call_graph` → hover для деталей.

### Сценарий 4: Быстрые исправления (Quick Fixes)

**Шаги:**

1. `get_diagnostics` для файла — получить список диагностик.
2. Для каждой диагностики с `range` — `get_code_actions` с `uri`, `range`, `diagnostic`.
3. Применить предложенное исправление (если подходит).

### Сценарий 5: Переход к определению из места использования

**Шаги:**

1. Известны `uri`, `line`, `character` места вызова.
2. `navigate_symbol` с `operation: "definition"`, `uri`, `line`, `character`.
3. Результат — `symbols` с определением (uri, range).

---

## Стратегия поиска для неизвестного кода

| Шаг | Capability | Цель |
|-----|------------|------|
| 1 | `navigate_symbol` (search) | Найти символ по имени |
| 2 | `get_call_graph` | Понять цепочки вызовов |
| 3 | `navigate_symbol` (hover) | Детали: тип, документация, сигнатура |

---

## MCP-инструменты

| Инструмент | MCP-сервер | Назначение |
|------------|------------|------------|
| `navigate_symbol` | lsp-bridge | Поиск символов, переход к определению, hover |
| `get_call_graph` | lsp-bridge | Граф вызовов (входящие/исходящие) |
| `rename_symbol` | lsp-bridge | Безопасное переименование по проекту |
| `get_diagnostics` | lsp-bridge | Диагностика LSP для файла |
| `get_code_actions` | lsp-bridge | Быстрые исправления (Quick Fixes) |

---

## Типичные ошибки и обходные пути

| Ошибка | Обходной путь |
|--------|---------------|
| LSP-сервер не подключён | Capability `unavailable`; проверить `lsp_status` (если доступен); сообщить пользователю о необходимости запуска BSL Language Server. |
| Символ не найден | Проверить имя (регистр, язык); попробовать нечёткий поиск через `ask_ai_assistant`; проверить, что файл в scope проекта. |
| `get_call_graph` таймаут | Уменьшить `depth`; проверять граф по частям. |
| `rename_symbol` не применим | Проверить позицию курсора (символ должен быть в области переименования); символ может быть в защищённой области; использовать ручное редактирование. |
| Файл не индексирован | Дождаться завершения индексации LSP; `get_diagnostics` может вернуть пусто до индексации. |
| `get_code_actions` пусто | Не для всех диагностик есть исправления; исправлять вручную по `message` диагностики. |