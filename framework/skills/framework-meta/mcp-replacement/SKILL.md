---
type: skill
name: mcp-replacement
description: Замена MCP-серверов. Учит агента заменять один MCP-сервер на другой с минимальными изменениями в навыках фреймворка.
---

# Замена MCP-серверов (MCP Replacement)

> **Назначение:** Этот навык учит агента **заменять MCP-серверы** целиком, адаптируя навыки фреймворка под новый набор capabilities.

---

## Когда использовать

Используй этот навык, когда:
- Пользователь хочет заменить MCP-сервер на другой (например, переход с одного BSL LSP на другой)
- Нужно мигрировать на альтернативную реализацию с похожей функциональностью
- Требуется адаптировать фреймворк под кастомный MCP-сервер

---

## Типы замены MCP-серверов

### 1. Полная замена (Drop-in replacement)

**Когда:** Новый MCP-сервер предоставляет **те же capabilities** с теми же параметрами.

**Действия:**
1. Обновить конфигурацию MCP-сервера в проекте
2. Проверить, что все capabilities доступны
3. Протестировать навыки

**Пример:**
```json
// Было
{
  "mcpServers": {
    "bsl-lsp": {
      "command": "bsl-language-server",
      "args": ["--stdio"]
    }
  }
}

// Стало
{
  "mcpServers": {
    "bsl-lsp-v2": {
      "command": "bsl-language-server-v2",
      "args": ["--stdio"]
    }
  }
}
```

**Изменения в навыках:** Не требуются.

### 2. Частичная замена (Partial replacement)

**Когда:** Новый MCP-сервер предоставляет **большинство capabilities**, но некоторые отличаются.

**Действия:**
1. Составить таблицу маппинга capabilities (старый → новый)
2. Обновить навыки `tool-usage/` для изменённых capabilities
3. Обновить упоминания в `bsl-practices/` и `rules/`
4. Обновить конфигурацию MCP-сервера

**Пример маппинга:**

| Старая capability | Новая capability | Изменения |
|-------------------|------------------|-----------|
| `search_syntax_reference` | `lsp_search` | Параметры изменены |
| `check_syntax` | `validate_bsl` | Название изменено |
| `get_type_info` | `lsp_hover` | Структура ответа изменена |
| `navigate_symbol` | `lsp_definition` | Без изменений |

**Изменения в навыках:** Требуются (см. [tool-replacement](../tool-replacement/SKILL.md)).

### 3. Полная миграция (Full migration)

**Когда:** Новый MCP-сервер предоставляет **другой набор capabilities** с другой архитектурой.

**Действия:**
1. Проанализировать capabilities нового сервера
2. Составить таблицу соответствия функциональности (старая → новая)
3. Переписать навыки `tool-usage/` под новые capabilities
4. Обновить все упоминания в `bsl-practices/` и `rules/`
5. Обновить конфигурацию MCP-сервера
6. Протестировать все навыки

**Пример:**
```
Старый сервер: bsl-language-server
- search_syntax_reference
- check_syntax
- get_type_info
- navigate_symbol

Новый сервер: custom-bsl-tools
- query_platform_api
- lint_code
- inspect_type
- find_definition
```

**Изменения в навыках:** Требуется полная переработка.

---

## Протокол замены MCP-сервера

### Шаг 1: Инвентаризация capabilities

**1.1. Список capabilities старого сервера**

Найди все capabilities, используемые в навыках:

```bash
# Поиск всех упоминаний capabilities
grep -rh "Capability:" framework/skills/tool-usage/ | sort -u
```

**Пример вывода:**
```
Capability: search_syntax_reference
Capability: check_syntax
Capability: get_type_info
Capability: navigate_symbol
Capability: get_call_graph
```

**1.2. Список capabilities нового сервера**

Получи список capabilities из документации нового MCP-сервера или через MCP-протокол:

```bash
# Запрос capabilities через MCP
mcp-client list-tools --server new-bsl-server
```

### Шаг 2: Маппинг функциональности

Создай таблицу соответствия:

| Функциональность | Старая capability | Новая capability | Статус |
|------------------|-------------------|------------------|--------|
| Поиск по синтаксису | `search_syntax_reference` | `lsp_search` | ✅ Есть аналог |
| Проверка синтаксиса | `check_syntax` | `validate_bsl` | ✅ Есть аналог |
| Информация о типе | `get_type_info` | `lsp_hover` | ⚠️ Другая структура |
| Навигация по символам | `navigate_symbol` | `lsp_definition` | ✅ Есть аналог |
| Граф вызовов | `get_call_graph` | — | ❌ Нет аналога |

### Шаг 3: Стратегия миграции

**Для каждой capability определи стратегию:**

**✅ Есть прямой аналог:**
- Обновить название capability в навыке
- Обновить параметры (если изменились)
- Обновить примеры

**⚠️ Есть аналог с изменениями:**
- Обновить название capability
- Переписать описание параметров
- Обновить структуру возвращаемых данных
- Обновить все примеры использования

**❌ Нет аналога:**
- Удалить навык `tool-usage/` для этой capability
- Удалить упоминания из `bsl-practices/` и `rules/`
- Обновить escape hatch в правилах (capability unavailable)

### Шаг 4: Обновление навыков

**4.1. Навыки `tool-usage/`**

Для каждого навыка в `framework/skills/tool-usage/`:

1. Открыть файл `SKILL.md`
2. Найти раздел `## Capability: <name>`
3. Обновить название capability
4. Обновить параметры
5. Обновить примеры
6. Обновить описание возвращаемых данных

**4.2. Навыки `bsl-practices/`**

Для каждого навыка в `framework/skills/bsl-practices/`:

1. Найти упоминания старых capabilities: `grep -n "старая_capability" SKILL.md`
2. Заменить на новые capabilities с корректными параметрами
3. Проверить, что контекст остался понятным

**4.3. Правила `rules/`**

Для каждого правила в `framework/rules/`:

1. Найти требования к capabilities
2. Обновить названия capabilities
3. Обновить escape hatch (если capability недоступна)

### Шаг 5: Обновление конфигурации

**5.1. Конфигурация MCP-сервера**

Обновить файл конфигурации проекта (например, `.cursor/mcp.json` или `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "new-bsl-server": {
      "command": "new-bsl-server",
      "args": ["--stdio"],
      "env": {
        "BSL_CONFIG": "/path/to/config"
      }
    }
  }
}
```

**5.2. Документация**

Создать файл `docs/MCP-MIGRATION-<date>.md` с описанием:
- Причина замены
- Таблица маппинга capabilities
- Список изменённых навыков
- Инструкции по настройке нового сервера

### Шаг 6: Тестирование

**6.1. Проверка сканирования**

```bash
python3 tools/install.py --ide cursor --list
```

**Ожидаемый результат:** Все навыки найдены, нет ошибок.

**6.2. Проверка установки**

```bash
python3 tools/install.py --ide cursor --include skill/syntax-checking --dry-run
```

**Ожидаемый результат:** Навык и его зависимости корректно резолвятся.

**6.3. Проверка работы capabilities**

Вручную протестировать каждую изменённую capability:
1. Запустить новый MCP-сервер
2. Вызвать capability через MCP-клиент
3. Проверить, что результат соответствует описанию в навыке

---

## Чек-лист замены MCP-сервера

- [ ] Составлен список capabilities старого сервера
- [ ] Составлен список capabilities нового сервера
- [ ] Создана таблица маппинга функциональности
- [ ] Определена стратегия для каждой capability
- [ ] Обновлены навыки `tool-usage/`
- [ ] Обновлены навыки `bsl-practices/`
- [ ] Обновлены правила `rules/`
- [ ] Обновлена конфигурация MCP-сервера
- [ ] Создана документация миграции
- [ ] Протестирован `install.py --list`
- [ ] Протестирована установка навыков
- [ ] Протестирована работа capabilities

---

## Типичные ошибки

### ❌ Забыть обновить escape hatch в правилах

**Проблема:** Правило требует capability, которой больше нет.

**Решение:** Обновить раздел "Escape hatch" в правиле:
```markdown
Если capability **новая_capability** недоступна (MCP-сервер не подключён), агент MUST продолжить работу без проверки.
```

### ❌ Не проверить структуру ответа новой capability

**Проблема:** Новая capability возвращает данные в другом формате, навык описывает старый формат.

**Решение:** Обновить раздел "Возвращает" в навыке `tool-usage/`:
```markdown
**Возвращает:**
```json
{
  "новая_структура": "..."
}
```

### ❌ Не удалить упоминания capability без аналога

**Проблема:** Навык ссылается на capability, которой больше нет.

**Решение:** Найти и удалить все упоминания:
```bash
grep -rn "старая_capability" framework/skills/
```

---

## Примеры миграций

### Пример 1: Замена BSL Language Server

**Старый сервер:** `bsl-language-server` (v0.20)  
**Новый сервер:** `bsl-language-server` (v1.0)

**Изменения:**
- `search_syntax_reference` → `lsp_search` (параметры изменены)
- `check_syntax` → `validate_bsl` (название изменено)
- Остальные capabilities без изменений

**Затронутые навыки:**
- `tool-usage/search-before-write` — обновлено название capability
- `tool-usage/syntax-checking` — обновлено название capability
- `bsl-practices/coding-standards` — обновлены примеры

**Время миграции:** ~2 часа

### Пример 2: Переход на кастомный MCP-сервер

**Старый сервер:** `bsl-language-server`  
**Новый сервер:** `custom-bsl-tools`

**Изменения:**
- Полная переработка capabilities
- Новая архитектура (REST API вместо LSP)
- Другие названия и параметры

**Затронутые навыки:**
- Все навыки `tool-usage/` — полная переработка
- Все навыки `bsl-practices/` — обновлены примеры
- Все правила `rules/` — обновлены требования

**Время миграции:** ~2 дня

---

## Связанные навыки

- [tool-replacement](../tool-replacement/SKILL.md) — замена отдельных MCP-инструментов
- [skill-creator-ext](../skill-creator-ext/SKILL.md) — создание и модификация навыков
