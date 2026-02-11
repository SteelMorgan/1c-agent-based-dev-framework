---
id: provider/lsp-bridge
type: provider
depends_on: [registry/tool-registry]
provider: lsp-bridge
mcp_server: mcp-bsl-lsp-bridge
version: актуальная
category: Навигация
status: active
---

# Provider: lsp-bridge

**MCP Server:** mcp-bsl-lsp-bridge (внутренний)
**Версия:** актуальная (Go, mcp-go SDK)
**Категория:** Навигация по коду / Диагностика / Рефакторинг
**Транспорт:** stdio

## Описание

Мост между MCP-протоколом и BSL Language Server (LSP). Предоставляет полный набор возможностей LSP для BSL-кода: навигация по символам, переход к определению, переименование, диагностика, граф вызовов, быстрые исправления. **Основной провайдер фреймворка** — покрывает 2 Core, 3 Important и 2 Optional capability.

## Предварительные требования

- BSL Language Server запущен и доступен
- Проект проиндексирован BSL LS (может занять время при первом запуске)
- Go runtime (для сборки/запуска MCP-сервера)

---

## Реализуемые Capability

### navigate_symbol

Capability реализуется через **3 tool-а** в зависимости от параметра `operation`:

#### operation = `search`

**Tool:** `symbol_explore`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `query` | → | `query` | Имя символа для поиска |
| (нет прямого аналога) | → | `file_context` | Опциональный контекст файла для сужения поиска |
| (нет прямого аналога) | → | `detail_level` | `auto`, `basic`, `full`. По умолчанию: `auto` |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `symbols` | ← | Результаты поиска | Символы с документацией, реализацией и ссылками |
| `symbols[].name` | ← | Имя символа | |
| `symbols[].kind` | ← | Тип символа | |
| `symbols[].uri` | ← | URI файла | |
| `symbols[].documentation` | ← | Документация | При `detail_level = full` |

**Пример вызова:**

```
Tool: symbol_explore
Параметры: {
  "query": "ОбщегоНазначения",
  "detail_level": "full"
}
```

#### operation = `definition`

**Tool:** `definition`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `uri` | → | `uri` | URI файла (формат `file://...`) |
| `line` | → | `line` | Номер строки (0-based) |
| `character` | → | `character` | Позиция символа (0-based) |
| (нет прямого аналога) | → | `language` | Опциональное переопределение языка |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `symbols[].uri` | ← | target URI | Файл с определением |
| `symbols[].range` | ← | target range | Позиция определения |

**Пример вызова:**

```
Tool: definition
Параметры: {
  "uri": "file:///path/to/module.bsl",
  "line": 42,
  "character": 10
}
```

#### operation = `hover`

**Tool:** `hover`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `uri` | → | `uri` | URI файла |
| `line` | → | `line` | 0-based |
| `character` | → | `character` | 0-based |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `symbols[].documentation` | ← | Hover content | Форматированная документация с сигнатурой и описанием |

**Пример вызова:**

```
Tool: hover
Параметры: {
  "uri": "file:///path/to/module.bsl",
  "line": 15,
  "character": 8
}
```

---

### get_diagnostics

**Tool:** `document_diagnostics`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `uri` | → | (параметр URI) | URI файла для диагностики. Использует LSP 3.17+ `textDocument/diagnostic` |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `diagnostics` | ← | Список диагностик | |
| `diagnostics[].range` | ← | range | Позиция в файле |
| `diagnostics[].severity` | ← | severity | `error`, `warning`, `information`, `hint` |
| `diagnostics[].message` | ← | message | Текст диагностики |
| `diagnostics[].code` | ← | code | Код диагностики BSL LS |
| `diagnostics[].source` | ← | source | `bsl-language-server` |

**Особенности и отклонения от контракта:**

- Диагностики возвращаются для одного файла (document-level)
- Для проектного уровня используйте `project_analysis` (дополнительный tool)

**Пример вызова:**

```
Tool: document_diagnostics
Параметры: {
  "uri": "file:///path/to/module.bsl"
}
```

---

### rename_symbol

Capability реализуется через **2 tool-а**: подготовка + переименование.

#### Шаг 1: Проверка возможности переименования

**Tool:** `prepare_rename`

Проверяет, возможно ли переименование в данной позиции (LSP `textDocument/prepareRename`).

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `uri` | → | `uri` | URI файла |
| `line` | → | `line` | 0-based |
| `character` | → | `character` | 0-based |

#### Шаг 2: Переименование

**Tool:** `rename`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `uri` | → | `uri` | URI файла |
| `line` | → | `line` | 0-based |
| `character` | → | `character` | 0-based |
| `new_name` | → | `new_name` | Новое имя |
| `preview` | → | `apply` | ⚠️ **Инверсия!** capability `preview=true` → tool `apply="false"` |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `changes` | ← | Список изменений по файлам | Все затронутые файлы |
| `files_affected` | ← | Количество файлов | |
| `applied` | ← | Применено или нет | `apply="true"` → `applied=true` |

**Особенности и отклонения от контракта:**

- ⚠️ Параметр `preview` в контракте **инвертирован** относительно `apply` в tool: `preview=true` = `apply="false"`
- Рекомендуется всегда сначала вызывать `prepare_rename`, затем `rename` с `apply="false"`, и только после проверки — `rename` с `apply="true"`

**Пример вызова:**

```
Tool: rename
Параметры: {
  "uri": "file:///path/to/module.bsl",
  "line": 10,
  "character": 5,
  "new_name": "НовоеИмяПеременной",
  "apply": "false"
}
```

---

### get_call_graph

Capability реализуется через **2 tool-а**:

#### Базовая иерархия вызовов

**Tool:** `call_hierarchy`

Показывает прямых вызывающих (callers) и вызываемых (callees) для символа.

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `uri` | → | `uri` | URI файла |
| `line` | → | `line` | 0-based |
| `character` | → | `character` | 0-based |
| `direction` | → | `direction` | `incoming`, `outgoing`, `both` |

#### Полный граф вызовов

**Tool:** `call_graph`

Рекурсивный обход иерархии вызовов с оптимизацией для BSL.

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `uri` | → | `uri` | URI файла |
| `line` | → | `line` | 0-based |
| `character` | → | `character` | 0-based |
| `direction` | → | `direction` | `incoming`, `outgoing`, `both` |
| `depth` | → | `depth` | Глубина обхода |

**Особенности и отклонения от контракта:**

- `call_graph` — **композитный tool**, выполняет множественные LSP-запросы рекурсивно
- Встроенное определение точек входа (обработчики событий BSL)
- Обнаружение циклов в графе
- Лимиты по глубине, количеству узлов и таймауту
- Для свежих файлов рекомендуется предварительно вызвать `did_change_watched_files`

**Пример вызова:**

```
Tool: call_graph
Параметры: {
  "uri": "file:///path/to/module.bsl",
  "line": 25,
  "character": 4,
  "direction": "both",
  "depth": 3
}
```

---

### get_code_actions

**Tool:** `code_actions`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `uri` | → | `uri` | URI файла |
| `line` | → | `line` | 0-based |
| `character` | → | `character` | 0-based |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `actions` | ← | Список действий | |
| `actions[].title` | ← | Описание | |
| `actions[].kind` | ← | Тип | `quickfix`, `refactor`, `source` |
| `actions[].edit` | ← | Предпросмотр | |

**Пример вызова:**

```
Tool: code_actions
Параметры: {
  "uri": "file:///path/to/module.bsl",
  "line": 15,
  "character": 10
}
```

---

### search_ssl_functions

**Tool:** `symbol_explore`

**Роль:** ⚡ primary — основной провайдер для поиска функций БСП в коде проекта.

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `query` | → | `query` | Имя функции БСП или паттерн поиска |
| `file_context` | → | `file_context` | Контекст для сужения поиска (например, `ОбщегоНазначения`) |

**Особенности и отклонения от контракта:**

- Ищет реальные определения в коде проекта (а не в документации, как `copilot-proxy`)
- Возвращает навигационные данные: URI, range, сигнатуру
- Работает только если конфигурация содержит БСП и проект проиндексирован BSL LS
- Для описания/документации функций БСП — fallback на `copilot-proxy`

**Пример вызова:**

```
Tool: symbol_explore
Параметры: {
  "query": "ЗначениеРеквизитаОбъекта",
  "file_context": "ОбщегоНазначения",
  "detail_level": "full"
}
```

---

## Дополнительные tool-ы (без маппинга на capability)

| Tool | Описание | Параметры |
|------|----------|-----------|
| `project_analysis` | Анализ проекта: поиск символов, анализ файлов, обзор workspace | `analysis_type` (`workspace_symbols`, `file_analysis`, `workspace_analysis`), `query`, `limit`, `offset` |
| `selection_range` | Диапазон выделения — расширение выделения от выражения к блоку (LSP selectionRange) | `uri`, `line`, `character` или `positions_json` |
| `get_range_content` | Извлечение текста из диапазона файла | `uri`, `start_line`, `start_character`, `end_line`, `end_character` |
| `did_change_watched_files` | Уведомление LSP об изменении файлов (необходимо после внешних изменений) | Список изменённых файлов |
| `lsp_status` | Статус подключения к LSP, прогресс индексации | (нет параметров) |

**Важно:**
- `did_change_watched_files` — вызывать после изменения файлов перед `call_graph` или `document_diagnostics`
- `lsp_status` — проверять готовность LSP перед использованием capability

---

## Ограничения

- Требует работающий BSL Language Server — без него все capability недоступны
- Индексация проекта может занять время — использовать `lsp_status` для проверки готовности
- `rename` с `apply="true"` модифицирует файлы — необратимая операция
- `call_graph` с большой глубиной может быть медленным на крупных проектах
- Некоторые LSP-возможности (implementation, signature help) не поддерживаются BSL LS

---

## Ссылки

- [Реестр capability](../tool-registry.md)
- [Справочник tool-ов](https://github.com/mcp-bsl-lsp-bridge/docs/tools/tools-reference.md)
- [Маппинг LSP-методов](https://github.com/mcp-bsl-lsp-bridge/docs/tools/lsp-methods-map.md)
