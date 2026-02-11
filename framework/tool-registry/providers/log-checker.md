---
id: provider/log-checker
type: provider
depends_on: [registry/tool-registry]
provider: log-checker
mcp_server: https://github.com/SteelMorgan/1c-log-checker
version: актуальная
category: Логирование
status: active
---

# Provider: log-checker

**MCP Server:** [SteelMorgan/1c-log-checker](https://github.com/SteelMorgan/1c-log-checker)
**Версия:** актуальная (Go, MCP stdio/HTTP)
**Категория:** Логирование (ЖР + ТЖ)
**Транспорт:** stdio / HTTP

## Описание

MCP-сервер для работы с журналом регистрации (ЖР) и технологическим журналом (ТЖ) 1С:Предприятие. Данные хранятся в ClickHouse, парсинг логов выполняется отдельным сервисом. Сервер предоставляет 8 tool-ов для чтения логов и управления конфигурацией технологического журнала.

## Предварительные требования

- Docker (для ClickHouse + парсер + MCP-сервер)
- Настроенный `cluster_map.yaml` с GUID-ами кластера и информационных баз
- Доступ к ClickHouse с данными логов
- Для ТЖ: доступ к файловой системе сервера 1С (для управления `logcfg.xml`)

---

## Реализуемые Capability

### search_event_log

**Tool:** `logc_get_event_log`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `cluster_guid` | → | `cluster_guid` | ⚠️ ОБЯЗАТЕЛЬНО из `cluster_map.yaml`. UUID формат |
| `infobase_guid` | → | `infobase_guid` | ⚠️ ОБЯЗАТЕЛЬНО из `cluster_map.yaml`. UUID формат |
| `from` | → | `from` | ISO 8601. По умолчанию: последние 10 минут |
| `to` | → | `to` | ISO 8601. По умолчанию: текущее время |
| `level` | → | `level` | `Error`, `Warning`, `Information`, `Note`. По умолчанию: `Error` |
| `mode` | → | `mode` | `minimal` (по умолчанию, экономия ~60-70% токенов) или `full` |
| `limit` | → | `limit` | По умолчанию: 1000, максимум: 10000 |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `records[].event_time` | ← | `event_time` (в minimal mode) / `ts` | Время события |
| `records[].level` | ← | `level` | Уровень |
| `records[].event_presentation` | ← | `event_presentation` | Представление события |
| `records[].user_name` | ← | `user_name` | Имя пользователя |
| `records[].comment` | ← | `comment` | Комментарий |
| `records[].metadata_presentation` | ← | `metadata_presentation` | Представление метаданных |

**Особенности и отклонения от контракта:**

- **Критически важно:** агент ОБЯЗАН предварительно прочитать `cluster_map.yaml` и извлечь GUID-ы. Запрещено использовать подставные значения
- Режим `minimal` возвращает только 6 полей — всегда начинать с него для экономии токенов
- Режим `full` возвращает все поля записи ЖР

**Пример вызова:**

```
Tool: logc_get_event_log
Параметры: {
  "cluster_guid": "af4fcd7c-0a86-11e7-8e5a-00155d000b0b",
  "infobase_guid": "b8d1c34e-5f2e-11e9-80e4-00155d000c0a",
  "level": "Error",
  "mode": "minimal"
}
```

---

### search_tech_log

**Tool:** `logc_get_tech_log`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `cluster_guid` | → | `cluster_guid` | ⚠️ ОБЯЗАТЕЛЬНО из `cluster_map.yaml` |
| `infobase_guid` | → | `infobase_guid` | ⚠️ ОБЯЗАТЕЛЬНО из `cluster_map.yaml` |
| `from` | → | `from` | ISO 8601. Обязателен |
| `to` | → | `to` | ISO 8601. Обязателен |
| `name` | → | `name` | Фильтр по типу события: `EXCP`, `DBMSSQL`, `DBPOSTGRS`, `TLOCK`, `TTIMEOUT`, `TDEADLOCK`, `CONN`, `SESN` |
| `mode` | → | `mode` | `minimal` или `full`. По умолчанию: `minimal` |
| `limit` | → | `limit` | По умолчанию: 1000, максимум: 10000 |

**Особенности и отклонения от контракта:**

- Параметры `from` и `to` обязательны (в отличие от `search_event_log`)
- Рекомендуется использовать узкие временные окна (15-30 мин)
- Тип события (`name`) позволяет значительно сократить объём данных

**Пример вызова:**

```
Tool: logc_get_tech_log
Параметры: {
  "cluster_guid": "af4fcd7c-0a86-11e7-8e5a-00155d000b0b",
  "infobase_guid": "b8d1c34e-5f2e-11e9-80e4-00155d000c0a",
  "from": "2026-02-11T10:00:00",
  "to": "2026-02-11T10:15:00",
  "name": "EXCP",
  "mode": "minimal"
}
```

---

### configure_tech_log

Capability реализуется через **6 tool-ов**, в зависимости от параметра `action`:

#### action = `configure`

**Tool:** `logc_configure_techlog`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `cluster_guid` | → | `cluster_guid` | Для валидации пути |
| `infobase_guid` | → | `infobase_guid` | Для валидации пути |
| `location` | → | `location` | Путь для логов. ОБЯЗАТЕЛЬНО формат: `<base>/<cluster_guid>/<infobase_guid>` |
| `history` | → | `history` | Хранение в часах (1-168). Рекомендуется 12-48 |
| `events` | → | `events` | Типы событий: `['EXCP', 'DBMSSQL', 'TLOCK']` |
| `properties` | → | `properties` | Свойства: `['all']` или конкретные |
| `config_path` | → | `config_path` | Путь к `logcfg.xml` (опционально) |

**Пример вызова:**

```
Tool: logc_configure_techlog
Параметры: {
  "cluster_guid": "af4fcd7c-0a86-11e7-8e5a-00155d000b0b",
  "infobase_guid": "b8d1c34e-5f2e-11e9-80e4-00155d000c0a",
  "location": "D:\\TechLogs\\af4fcd7c-0a86-11e7-8e5a-00155d000b0b\\b8d1c34e-5f2e-11e9-80e4-00155d000c0a",
  "history": 24,
  "events": ["EXCP", "DBMSSQL", "TLOCK"],
  "properties": ["all"]
}
```

#### action = `save`

**Tool:** `logc_save_techlog`

Создаёт резервную копию текущей конфигурации (`logcfg.xml` → `logcfg.xml.OLD`).

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `config_path` | → | `config_path` | Путь к `logcfg.xml` (опционально) |

#### action = `restore`

**Tool:** `logc_restore_techlog`

Восстанавливает конфигурацию из бэкапа (`logcfg.xml.OLD` → `logcfg.xml`).

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `config_path` | → | `config_path` | Путь к `logcfg.xml` (опционально) |

#### action = `disable`

**Tool:** `logc_disable_techlog`

Отключает технологический журнал (очищает `logcfg.xml`).

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `config_path` | → | `config_path` | Путь к `logcfg.xml`. Обязателен |

#### action = `read`

**Tool:** `logc_get_techlog_config`

Чтение текущей конфигурации `logcfg.xml`.

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `config_path` | → | `config_path` | Путь к `logcfg.xml`. Обязателен |

#### action = `get_timestamp`

**Tool:** `logc_get_actual_log_timestamp`

Получение актуальной метки времени (максимальной обработанной записи) — для smart polling.

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `infobase_guid` | → | `base_id` | ⚠️ Имя параметра отличается от контракта! |

**Особенности и отклонения от контракта:**

- Capability `configure_tech_log` реализуется через **6 разных tool-ов**
- Типичный workflow: `save` → `configure` → (использование) → `restore` или `disable`
- `logcfg.xml` — глобальный для сервера, изменение затрагивает все ИБ
- После изменения конфигурации требуется перезапуск служб 1С

---

## Дополнительные tool-ы (без маппинга на capability)

Все 8 tool-ов MCP-сервера маппятся на 3 capability (`search_event_log`, `search_tech_log`, `configure_tech_log`).

---

## Ограничения

- Требует ClickHouse с данными логов (парсер должен быть запущен)
- GUID-ы обязательно из `cluster_map.yaml` — подставные значения вызывают ошибку
- Технологический журнал может генерировать очень большие объёмы данных — всегда отключать после диагностики
- Изменение `logcfg.xml` требует перезапуска служб 1С
- Временные окна для ТЖ рекомендуется делать узкими (15-30 мин)

---

## Ссылки

- [Реестр capability](../tool-registry.md)
- [Документация MCP-сервера](https://github.com/SteelMorgan/1c-log-checker)
- [Руководство по использованию MCP](https://github.com/SteelMorgan/1c-log-checker/blob/main/docs/mcp/usage.md)
