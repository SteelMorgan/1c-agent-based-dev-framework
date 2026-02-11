---
id: provider/metadata-tools
type: provider
depends_on: [registry/tool-registry]
provider: metadata-tools
mcp_server: https://github.com/RooLee10/1c-mcp-tools
version: актуальная
category: Метаданные
status: active
---

# Provider: metadata-tools

**MCP Server:** [RooLee10/1c-mcp-tools](https://github.com/RooLee10/1c-mcp-tools) (поддерживаемый форк vladimir-kharin/1c_mcp)
**Версия:** актуальная
**Категория:** Метаданные конфигурации 1С
**Транспорт:** stdio (Python MCP proxy → HTTP → расширение 1С → DataProcessor-контейнеры)

## Архитектура

MCP-сервер построен как **Python MCP proxy**, который через HTTP общается с расширением 1С. Функциональность реализована в виде **DataProcessor-контейнеров**:

- **mcp_ИнструментДанныеОКонфигурации** — метаданные: список объектов, структура
- **mcp_ИнструментРаботаСЗапросами** — запросы: валидация, выполнение, навигационные ссылки
- **mcp_РесурсОписаниеСинтаксисаВстроенногоЯзыка** — ресурс справочника синтаксиса BSL
- **mcp_УправлениеСервером** — placeholder (пустой модуль)

Архитектура **расширяема**: добавление новых DataProcessor-контейнеров расширяет набор tool-ов без изменения proxy.

## Описание

MCP-сервер для работы с метаданными конфигурации 1С, выполнения запросов на языке запросов и работы с навигационными ссылками. Позволяет агенту исследовать структуру конфигурации и получать данные из информационной базы.

## Предварительные требования

- Подключённое расширение 1С (расширение MCP) в информационной базе
- Настроенное соединение с MCP-сервером

---

## Реализуемые Capability

### search_metadata

**Tools:** `list_metadata_objects` + `get_metadata_structure`

**Маппинг:** capability реализуется двумя последовательными вызовами. Сначала `list_metadata_objects` для поиска объектов по типу и маске имени, затем `get_metadata_structure` для получения детальной структуры выбранного объекта.

#### Шаг 1: list_metadata_objects

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `query` | → | `nameMask` | Подстрока для поиска по имени или синониму (case-insensitive) |
| `object_type` | → | `metaType` | Тип объекта метаданных (обязательный) |
| — | — | `maxItems` | По умолчанию 100 |

**Параметр `metaType`** — enum: Catalogs, Documents, InformationRegisters, AccumulationRegisters, AccountingRegisters, CalculationRegisters, ChartsOfCharacteristicTypes, ChartsOfAccounts, ChartsOfCalculationTypes, BusinessProcesses, Tasks, ExchangePlans, FilterCriteria, Reports, DataProcessors, Enums, CommonModules, SessionParameters, CommonTemplates, CommonPictures, XDTOPackages, WebServices, HTTPServices, WSReferences, Styles, Languages, FunctionalOptions, FunctionalOptionsParameters, DefinedTypes, CommonAttributes, CommonCommands, CommandGroups, Constants, CommonForms, Roles, Subsystems, EventSubscriptions, ScheduledJobs, SettingsStorages, Sequences, DocumentJournals, ExternalDataSources, Interfaces.

**Результат:** текст — строки вида `ПолноеИмя (Синоним)` через перевод строки.

#### Шаг 2: get_metadata_structure

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `objects[].full_name` (из шага 1) | → | `name` | Точное имя объекта (case-insensitive) |
| `object_type` | → | `metaType` | Тип объекта метаданных (обязательный) |

**Поддерживаемые `metaType` для структуры:** Catalogs, Documents, InformationRegisters, AccumulationRegisters, AccountingRegisters, CalculationRegisters, Reports, DataProcessors, ChartsOfCharacteristicTypes, ChartsOfAccounts, ChartsOfCalculationTypes, BusinessProcesses, Tasks, ExchangePlans.

**Результат:** текст — форматированная структура: заголовок, стандартные реквизиты, реквизиты (имя + тип + синоним), табличные части, измерения/ресурсы для регистров, владельцы для справочников.

**Пример вызова:**

```
Tool: list_metadata_objects
Параметры: {
  "metaType": "Catalogs",
  "nameMask": "Номенклатура",
  "maxItems": 10
}

Tool: get_metadata_structure
Параметры: {
  "metaType": "Catalogs",
  "name": "Номенклатура"
}
```

---

### execute_query

**Tool:** `execute_query`

**⚠️ SECURITY:** Данные, возвращаемые запросом, уходят в LLM. Не передавать конфиденциальные данные.

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `queryText` | → | `queryText` | Текст запроса на языке запросов 1С (SELECT/ВЫБРАТЬ) |
| `parameters` | → | `parameters` | object, optional. Ссылочные типы: `{_objectRef: true, УникальныйИдентификатор: "...", ТипОбъекта: "СправочникСсылка.Контрагенты"}` |
| `limit` | → | `limit` | По умолчанию 100, максимум 1000 |

**Результат:** JSON `{success, message, truncated, limit, columns: [{name}], rows: [{...}]}`. Ссылочные поля автоконвертируются в `{_objectRef: true, УникальныйИдентификатор, ТипОбъекта, Представление}`.

**Особенности:** поддерживает **итеративный анализ** — результаты одного запроса можно использовать как параметры следующего.

**Побочные эффекты:** read-only (только SELECT).

**Пример вызова:**

```
Tool: execute_query
Параметры: {
  "queryText": "ВЫБРАТЬ ПЕРВЫЕ 10 Номенклатура.Ссылка КАК Ссылка, Номенклатура.Наименование КАК Наименование ИЗ Справочник.Номенклатура КАК Номенклатура",
  "limit": 5
}
```

---

### validate_query

**Tool:** `validate_query`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `queryText` | → | `queryText` | Текст запроса 1С (обязательный) |

**Результат:** JSON `{valid: boolean, message: string}`. При ошибке включает HINT с рекомендациями (check get_metadata_structure, check parameter format, use parse_nav_link).

**Побочные эффекты:** read-only (только парсинг, без доступа к БД — быстрая операция).

**Пример вызова:**

```
Tool: validate_query
Параметры: {
  "queryText": "ВЫБРАТЬ * ИЗ Справочник.Номенклатура"
}
```

---

### resolve_nav_link

**Tools:** `parse_nav_link` (навигационная ссылка → описание объекта) + `get_nav_link` (описание → ссылка)

#### parse_nav_link — ссылка → описание

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `navLink` | → | `navLink` | Ссылка формата `e1cib/data/Документ.Реализация?ref=...` |

**Результат:** JSON `{success, object?: {_objectRef, УникальныйИдентификатор, ТипОбъекта, Представление}, message?}`.

#### get_nav_link — описание → ссылка

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `objectDescription` | → | `objectDescription` | `{_objectRef: true, УникальныйИдентификатор, ТипОбъекта}` |

**Результат:** JSON `{success, navLink?: string, message?}`.

**Пример вызова:**

```
Tool: parse_nav_link
Параметры: {
  "navLink": "e1cib/data/Документ.Реализация?ref=..."
}

Tool: get_nav_link
Параметры: {
  "objectDescription": {
    "_objectRef": true,
    "УникальныйИдентификатор": "...",
    "ТипОбъекта": "ДокументСсылка.Реализация"
  }
}
```

---

## Ресурс: 1csyntax

**Resource:** `mcp_РесурсОписаниеСинтаксисаВстроенногоЯзыка`  
**URI:** `file://resource/syntax_1c.txt`

Описание синтаксиса встроенного языка 1С в формате Markdown — справочник BSL-синтаксиса для capability `1csyntax`.

---

## Полный список tool-ов (6 tools + 1 resource)

| Tool | DataProcessor | Описание |
|------|---------------|----------|
| `list_metadata_objects` | mcp_ИнструментДанныеОКонфигурации | Список объектов метаданных с фильтрацией по типу и имени |
| `get_metadata_structure` | mcp_ИнструментДанныеОКонфигурации | Структура объекта (реквизиты, табличные части, измерения, ресурсы) |
| `validate_query` | mcp_ИнструментРаботаСЗапросами | Проверка синтаксиса запроса без выполнения |
| `execute_query` | mcp_ИнструментРаботаСЗапросами | ОСНОВНОЙ инструмент — выполнение запроса, автоконвертация ссылок |
| `parse_nav_link` | mcp_ИнструментРаботаСЗапросами | Навигационная ссылка → описание объекта |
| `get_nav_link` | mcp_ИнструментРаботаСЗапросами | Описание объекта → навигационная ссылка |
| 1csyntax (resource) | mcp_РесурсОписаниеСинтаксисаВстроенногоЯзыка | Справочник синтаксиса BSL в Markdown |

---

## Ограничения

- Требует подключённое расширение 1С в информационной базе
- `execute_query`: данные уходят в LLM — не передавать конфиденциальную информацию
- `get_metadata_structure` поддерживает не все типы метаданных (см. список выше)

---

## Ссылки

- [Реестр capability](../tool-registry.md)
- [Репозиторий MCP-сервера](https://github.com/RooLee10/1c-mcp-tools)
