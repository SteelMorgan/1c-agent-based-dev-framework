---
name: buddy-prompting
description: "Before asking 1C Buddy: API, ITS, versions, BSL"
uses_capabilities:
  - ask_ai_assistant
alwaysApply: false
---

# Prompts for 1C Buddy (Buddy Prompting)

## Decision Table — choosing a template

| Agent task | Template | When |
|---------------|--------|-------|
| Platform API, syntax, methods, types, events | `SEARCH_DOCS` | A question about the built-in language or the behavior of platform objects |
| Standards, methodology, BСП, typical configurations | `SEARCH_ITS` | A question about development rules, EDT, Configurator, 1C products |
| Full text of a specific ITS document | `FETCH_ITS` | You already have the document id after SEARCH_ITS |
| Differences between platform versions | `DIFF_VERSIONS` | Migration, compatibility, "what changed" |
| Code review for standards and searching for analogs in BСП | `VALIDATE_BSL` | Review of a BSL fragment: syntax + compliance with standards + recommendations for using BСП |

## Prompt Templates

### SEARCH_DOCS — platform documentation

```
Задача: ответить на вопрос по документации платформы 1С.

Обязательно используй внутренний инструмент: Search_Documentation

Ограничения:
- Не отвечай по памяти.
- Не используй Search_ITS.
- Если документации нет — так и скажи.

Вход:
Версия платформы: {version}
Объект: {object}
Режим выполнения: {context}          # клиент / сервер / фоновое задание
Вид форм: {form_mode}                # управляемые / обычные
Вопрос: {query}

Формат ответа:
1. Краткий ответ по найденной документации.
2. Найденные методы/свойства с параметрами и возвращаемыми значениями.
3. Если неоднозначность — перечисли варианты.
```

**Query formation:** for general topics add "List all...", "General information about...". Example: "Form parameters" → "List all parameters of the managed form". Empty result → rephrase.

### SEARCH_ITS — standards and methodology

```
Задача: найти материалы в базе ИТС.

Обязательно используй внутренний инструмент: Search_ITS

Ограничения:
- Не отвечай по памяти.
- Не используй Search_Documentation.
- Не пересказывай статьи — найди документы.

Вход:
Поисковый запрос: {query}
Контекст: {context}

Формат ответа:
1. 3–5 наиболее релевантных документов.
2. Для каждого: id, заголовок, почему релевантен.
3. Если ничего не найдено — скажи явно.
4. Приведи ссылки (https://...).
```

### FETCH_ITS — full text of an ITS document

Only after SEARCH_ITS, when you already have the document id.

```
Задача: получить содержание документа ИТС.

Обязательно используй внутренний инструмент: Fetch_ITS

Ограничения:
- Не выполняй новый поиск.
- Не отвечай по памяти.

Вход:
ID документа: {doc_id}
Что извлечь: {focus}

Формат ответа:
1. Структурированное содержание документа.
2. Ответ на вопрос: {question}
3. Важные ограничения и исключения из документа.
```

**SEARCH_ITS → FETCH_ITS orchestration** — two separate `ask_ai_assistant` calls:

1. Call 1: SEARCH_ITS → list of documents with ids
2. The agent selects the best id
3. Call 2: FETCH_ITS with the selected id → full text

### DIFF_VERSIONS — differences between versions

```
Задача: сравнить документацию платформы между версиями.

Обязательно используй внутренний инструмент: Diff_Documentation_Versions

Ограничения:
- Не отвечай по памяти.
- Сравни именно указанные версии.

Вход:
Тема: {topic}
Версия (старая): {version_old}
Версия (новая): {version_new}

Формат ответа:
1. Что добавилось.
2. Что изменилось.
3. Что удалено или несовместимо.
4. Практический вывод для разработчика.
```

### VALIDATE_BSL — code validation for standards and BСП analogs

```
Задача: проверить код 1С на соответствие стандартам разработки
и предложить аналоги из БСП, если они есть.

Обязательно используй внутренний инструмент: syntax-checker__validate
Используй режим extended (обогащение стандартами 1С).

Ограничения:
- Не анализируй код по памяти.
- Проверь именно приведённый код.
- Проверка без глобального контекста — ошибки "необъявленная переменная"
  на глобальные методы/переменные могут быть ложными, укажи это.

Вход:
Код:
```bsl
{code}
```

Формат ответа:
1. Синтаксические ошибки и предупреждения (место, причина, исправление).
2. Нарушения стандартов разработки 1С.
3. Рекомендации: какие фрагменты можно заменить методами БСП/платформы.
4. Если проблем нет — скажи явно.
```

## Stop rules

1. **Prohibition on inventing signatures.** Do not specify a method, property, or parameter
   unless `ask_ai_assistant` (SEARCH_DOCS) has confirmed it. If the documentation was not found —
   report the absence; do not construct a signature by analogy or from memory.

2. **Pre-flight context for SEARCH_DOCS.** Before the call, explicitly fix four parameters:
   object (`{object}`), execution mode (`{context}`: client / server / background job),
   platform version (`{version}`), and form type (`{form_mode}`: managed / ordinary).
   A missing parameter = a request with undefined context, the result is unreliable.

3. **Prohibition on answering from memory when version or mode matters.** If the behavior of an API
   or object may differ depending on the platform version or execution mode —
   always clarify it first through `ask_ai_assistant` (SEARCH_DOCS or DIFF_VERSIONS).
   Answering "from memory" in such cases is prohibited regardless of the agent's confidence.

## Errors and limitations

| Problem | Workaround |
|----------|------------|
| Empty SEARCH_DOCS result | Rephrase query: "List all..."; check version |
| Irrelevant SEARCH_ITS results | Refine the query with ITS terminology; narrow the search |
| Buddy answered from memory without calling the tool | Strengthen: "It is PROHIBITED to answer without the tool" |
| False VALIDATE_BSL errors | "Undefined variable" on global methods is normal; filter them out |
| FETCH_ITS without id | First SEARCH_ITS → select id → then FETCH_ITS |
| No project context (session_id) | DO NOT try to trigger FindRelated, FindSimilar, GetObject |

---
depends_on: []
---
