---
id: provider/test-runner
type: provider
depends_on: [registry/tool-registry]
provider: test-runner
mcp_server: https://github.com/alkoleft/mcp-onec-test-runner
version: 1.0.0
category: Тестирование
status: active
---

# Provider: test-runner

**MCP Server:** [alkoleft/mcp-onec-test-runner](https://github.com/alkoleft/mcp-onec-test-runner)
**Версия:** 1.0.0 (YaXUnit Test Runner MCP Server)
**Категория:** Тестирование / Сборка / Проверка синтаксиса
**Транспорт:** stdio

## Описание

Полнофункциональный MCP-сервер для запуска тестов YaxUnit, сборки проекта, выгрузки конфигурации, проверки синтаксиса (через EDT и Designer) и запуска приложений платформы 1С:Предприятие. Один из ключевых провайдеров фреймворка — покрывает 2 Core и 1 Important capability.

## Предварительные требования

- Java 17+ (Spring Boot)
- Платформа 1С:Предприятие установлена (указать `platform-version`)
- Настроенная информационная база (`connection-string`)
- Для EDT-проверки: установленный 1C:EDT
- Настроенный `sourceSet` с путями к проекту

---

## Реализуемые Capability

### check_syntax

Capability реализуется через **3 tool-а**, в зависимости от параметра `mode`:

#### mode = `edt` (по умолчанию)

**Tool:** `check_syntax_edt`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `target` | → | `projectName` | Имя проекта EDT. Если не указано — проверяются все проекты из sourceSet |
| `options` | → | (не поддерживается) | EDT validate не принимает дополнительных опций |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `success` | ← | `success` | Прямое соответствие |
| `errors` | ← | `errors` | Список строк ошибок |
| `warnings` | ← | `warnings` | Список строк предупреждений |
| `check_time` | ← | `checkTime` | Время в миллисекундах |

**Пример вызова:**

```
Tool: check_syntax_edt
Параметры: {
  "projectName": "my-config"
}
```

#### mode = `designer_config`

**Tool:** `check_syntax_designer_config`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `target` | → | (не маппится) | CheckConfig всегда проверяет всю конфигурацию |
| `options.thinClient` | → | `thinClient` | Эмуляция тонкого клиента (по умолчанию: true) |
| `options.server` | → | `server` | Эмуляция сервера (по умолчанию: true) |
| `options.webClient` | → | `webClient` | Эмуляция веб-клиента |
| `options.incorrectReferences` | → | `incorrectReferences` | Поиск некорректных ссылок |
| `options.unreferenceProcedures` | → | `unreferenceProcedures` | Неиспользуемые процедуры (по умолчанию: true) |
| `options.handlersExistence` | → | `handlersExistence` | Проверка обработчиков (по умолчанию: true) |
| `options.emptyHandlers` | → | `emptyHandlers` | Поиск пустых обработчиков (по умолчанию: true) |
| `options.extendedModulesCheck` | → | `extendedModulesCheck` | Расширенная проверка (по умолчанию: true) |
| `options.extension` | → | `extension` | Проверить только указанное расширение |
| `options.allExtensions` | → | `allExtensions` | Проверить все расширения |

**Особенности:** Полный список параметров CheckConfig включает более 20 флагов эмуляции различных режимов клиентов. Полный перечень — в исходном коде `McpServer.kt`.

**Пример вызова:**

```
Tool: check_syntax_designer_config
Параметры: {
  "thinClient": true,
  "server": true,
  "extendedModulesCheck": true,
  "unreferenceProcedures": true
}
```

#### mode = `designer_modules`

**Tool:** `check_syntax_designer_modules`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `target` | → | (не маппится) | CheckModules проверяет все модули |
| `options.thinClient` | → | `thinClient` | По умолчанию: true |
| `options.server` | → | `server` | По умолчанию: true |
| `options.extendedModulesCheck` | → | `extendedModulesCheck` | По умолчанию: true |
| `options.extension` | → | `extension` | Только указанное расширение |
| `options.allExtensions` | → | `allExtensions` | Все расширения |

**Пример вызова:**

```
Tool: check_syntax_designer_modules
Параметры: {
  "thinClient": true,
  "server": true,
  "extendedModulesCheck": true
}
```

---

### run_tests

Capability реализуется через **2 tool-а**, в зависимости от параметра `scope`:

#### scope = `all` (по умолчанию)

**Tool:** `run_all_tests`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| (нет параметров) | | (нет параметров) | Запускает все тесты YaxUnit в проекте |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `success` | ← | `success` | Прямое соответствие |
| `total` | ← | `total` | Общее количество тестов |
| `passed` | ← | `passed` | Успешных |
| `failed` | ← | `failed` | Провалившихся |
| `errors` | ← | `errors` | Список строк ошибок |

**Пример вызова:**

```
Tool: run_all_tests
Параметры: {}
```

#### scope = `<имя_модуля>`

**Tool:** `run_module_tests`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `scope` | → | `moduleName` | Имя модуля для тестирования |

**Пример вызова:**

```
Tool: run_module_tests
Параметры: {
  "moduleName": "ТестыНоменклатура"
}
```

---

### build_project

**Tool:** `build_project`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| (нет параметров) | | (нет параметров) | Автоматическое определение изменений и инкрементальная сборка |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `success` | ← | `success` | Прямое соответствие |
| `message` | ← | `message` | Описание результата |
| `build_time` | ← | `buildTime` | Время в миллисекундах |
| `steps` | ← | `steps` | Шаги выполнения (при ошибках) |

**Пример вызова:**

```
Tool: build_project
Параметры: {}
```

---

### dump_config

**Tool:** `dump_config`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `mode` | → | `mode` | `FULL`, `INCREMENTAL`, `PARTIAL`. По умолчанию: `FULL` |
| `extension` | → | `extension` | Имя расширения |
| `all_extensions` | → | `allExtensions` | Выгрузить все расширения |
| `objects` | → | `objects` | Список объектов для `PARTIAL` (например: `["Справочник.Номенклатура"]`) |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `success` | ← | `success` | Прямое соответствие |
| `message` | ← | `message` | Описание результата |
| `mode` | ← | `mode` | Использованный режим |
| `dump_time` | ← | `dumpTime` | Время в миллисекундах |
| `dumped_objects` | ← | `dumpedObjects` | Список выгруженных объектов |
| `errors` | ← | `errors` | Ошибки (если есть) |

**Пример вызова:**

```
Tool: dump_config
Параметры: {
  "mode": "INCREMENTAL"
}
```

---

### launch_app

**Tool:** `launch_app`

**Маппинг параметров:**

| Параметр capability | → | Параметр tool | Примечание |
|---------------------|---|---------------|------------|
| `app_type` | → | `utilityType` | Допустимы псевдонимы: `DESIGNER`, `designer`, `1cv8`, `конфигуратор`, `THIN_CLIENT`, `thin_client`, `1cv8c`, `тонкий клиент`, `THICK_CLIENT`, `thick_client`, `толстый клиент` |

**Маппинг результата:**

| Поле контракта | ← | Поле ответа tool | Примечание |
|----------------|---|------------------|------------|
| `success` | ← | `success` | Прямое соответствие |
| `message` | ← | `message` | Описание результата |

**Пример вызова:**

```
Tool: launch_app
Параметры: {
  "utilityType": "DESIGNER"
}
```

---

## Дополнительные tool-ы (без маппинга на capability)

| Tool | Описание | Параметры |
|------|----------|-----------|
| `yaxunit_list_modules` | Получение списка тестовых модулей | (нет) |
| `yaxunit_get_configuration` | Получение конфигурации проекта | (нет) |
| `yaxunit_check_platform` | Проверка доступности платформы 1С | (нет) |

---

## Ограничения

- Требует установленную платформу 1С:Предприятие на машине
- Для EDT-проверки нужен установленный 1C:EDT
- `check_syntax_designer_config` и `check_syntax_designer_modules` требуют подключения к ИБ через конфигуратор
- Тесты запускаются в реальной ИБ (mutating операция)
- Инкрементальная выгрузка (`INCREMENTAL`) требует наличия предыдущей полной выгрузки

---

## Ссылки

- [Реестр capability](../tool-registry.md)
- [Документация MCP-сервера](https://github.com/alkoleft/mcp-onec-test-runner)
- [Настройка IDE](https://github.com/alkoleft/mcp-onec-test-runner/blob/main/docs/IDE_SETUP.md)
