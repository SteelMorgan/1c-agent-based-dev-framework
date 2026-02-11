---
id: rule/mandatory-tools
type: rule
depends_on:
  - registry/tool-registry
  - skill/search-before-write
  - skill/syntax-checking
  - skill/test-execution
  - skill/metadata-discovery
  - skill/code-navigation
---

# Обязательное использование возможностей (Mandatory Tools)

> RULES — обязательные политики, ограничивающие поведение агента. Правила ссылаются на имена capability из tool-registry, никогда — на конкретные MCP-tool-ы.

---

## Назначение

Это правило обеспечивает контроль качества и снижение ошибок за счёт обязательного использования ключевых возможностей (capabilities) фреймворка в определённых ситуациях. Агент обязан вызывать проверяемые инструменты до/после критичных операций, чтобы не допускать типичных ошибок: использование неизвестного API, дублирование функций, синтаксические ошибки, регрессии в тестах.

---

## Условия срабатывания

Правило применяется, когда агент:

- Пишет или изменяет BSL-код
- Обращается к API платформы 1С
- Создаёт новые функции или процедуры
- Ссылается на объекты метаданных
- Реализует утилитарные функции
- Выполняет рефакторинг
- Отлаживает ошибки

---

## Требования

### MUST (обязательно)

| Ситуация | Capability | Действие |
|----------|------------|----------|
| Первое использование метода API платформы 1С | `search_platform_api` или `get_type_info` | Вызвать ДО написания кода, использующего этот метод |
| Написание или изменение BSL-кода | `check_syntax` | Вызвать ПОСЛЕ записи/модификации кода |
| Создание новой функции или процедуры | `navigate_symbol` | Вызвать ДО создания — проверить, что функция не существует |
| Изменения кода, затрагивающие протестированные модули | `run_tests` | Вызвать ПОСЛЕ изменений |
| Отладка ошибок (ошибки LSP, runtime) | `get_diagnostics` | Вызвать при анализе ошибок |
| Код, ссылающийся на объекты метаданных | `search_metadata` | Вызвать ДО написания кода, использующего объекты |

### SHOULD (настоятельно рекомендуется)

| Ситуация | Capability | Действие |
|----------|------------|----------|
| Реализация утилитарной функции | `search_ssl_functions` | Вызвать ДО реализации — проверить наличие в БСП |
| Рефакторинг (переименование, изменение сигнатур) | `get_call_graph` | Вызвать для понимания зоны влияния |

### MUST NOT (запрещено)

- Агент MUST NOT использовать метод API платформы без предварительной проверки через `search_platform_api` или `get_type_info`
- Агент MUST NOT сохранять изменённый BSL-код без вызова `check_syntax` (если capability доступна)
- Агент MUST NOT создавать новую функцию без проверки её отсутствия через `navigate_symbol`

---

## Исключения

### Escape hatch (SPEC-001)

Если capability **UNAVAILABLE** (MCP-сервер не подключён, tool возвращает ошибку), агент MUST:

1. **Зафиксировать факт**: «Capability [имя] недоступна: [причина]»
2. **Продолжить работу** без выполнения проверки
3. **Добавить WARNING в вывод**: «Проверка [имя] не выполнена — рекомендуется проверить вручную»

Если capability **IRRELEVANT** к задаче (например, в проекте нет БСП), агент MAY пропустить проверку с краткой пометкой.

---

## Связанные навыки и правила

| Ресурс | Связь |
|--------|-------|
| [tool-registry/tool-registry.md](../tool-registry/tool-registry.md) | Описание контрактов capability |
| [skills/tool-usage/search-before-write.md](../skills/tool-usage/search-before-write.md) | Поиск перед написанием кода |
| [skills/tool-usage/syntax-checking.md](../skills/tool-usage/syntax-checking.md) | Проверка синтаксиса |
| [skills/tool-usage/test-execution.md](../skills/tool-usage/test-execution.md) | Запуск тестов |
| [skills/tool-usage/metadata-discovery.md](../skills/tool-usage/metadata-discovery.md) | Поиск по метаданным |
| [skills/tool-usage/code-navigation.md](../skills/tool-usage/code-navigation.md) | Навигация по символам |
| [docs/SPEC-001-framework-architecture.md](../../docs/SPEC-001-framework-architecture.md) | Архитектурное решение о mandatory tools и escape hatch |
