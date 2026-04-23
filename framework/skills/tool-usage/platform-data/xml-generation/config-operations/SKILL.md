---
name: config-operations
description: "Операции с конфигурацией 1С (CF) — init, info, edit, validate. Используй при создании конфигурации, анализе структуры, изменении свойств и ChildObjects, валидации Configuration.xml."
---

# Config Operations

Работа с конфигурациями 1С (Configuration.xml).

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать новую конфигурацию | `config init --name <Name> <output_dir>` |
| Нужно посмотреть состав конфигурации | `config info <configPath>` |
| Нужно изменить свойство конфигурации | `config edit <configPath> --op modify-property --value "PropName=Value"` |
| Нужно добавить/удалить объект из ChildObjects | `config edit <configPath> --op add-child --value "Type.Name"` |
| Нужно проверить Configuration.xml | `config validate <configPath>` |

## Команды

### config init

Создать новую конфигурацию.

```bash
xml-gen config init --name <Name> [--compat <Version>] <output_dir>
```

**Результат:** Configuration.xml + Languages/Русский.xml + ConfigDumpInfo.xml + заглушки модулей.

### config info

Анализ конфигурации: свойства, состав, счётчики объектов.

```bash
xml-gen config info [--mode brief|overview|full] <configPath>
```

**Режимы:**
- `brief` — однострочная сводка
- `overview` — свойства + количество объектов по типам
- `full` — все свойства, все объекты

### config edit

Изменение свойств и состава конфигурации.

```bash
xml-gen config edit <configPath> --op <operation> --value <value>
```

**Операции:**
- `modify-property` — изменить свойство: `--value "CompatibilityMode=Version8_3_24"`
- `add-childObject` — добавить объект в ChildObjects: `--value "Catalog.Товары"`. Guard: файл объекта (`Catalogs/Товары.xml`) должен существовать — иначе throw. Опционально `--no-file-check` отключает guard.
- `remove-childObject` — удалить объект: `--value "Catalog.Товары"`
- `add-defaultRole` — добавить роль по умолчанию: `--value "ОсновнаяРоль"`
- `remove-defaultRole` — удалить роль по умолчанию
- `set-defaultRoles` — заменить список ролей: `--value "Роль1 ;; Роль2"`

### config validate

Валидация Configuration.xml (10 проверок).

```bash
xml-gen config validate <configPath>
```

**Проверки:** структура XML, namespace, UUID, InternalInfo (7 ClassId), Properties, enum-значения (11 свойств), ChildObjects (порядок 44 типов, дубликаты), DefaultLanguage, файлы языков, каталоги объектов.

## ChildObjects — порядок 44 типов

Конфигурация 1С требует строгий порядок типов в ChildObjects:

Language → Subsystem → StyleItem → Style → CommonPicture → SessionParameter → Role → CommonTemplate → FilterCriterion → CommonModule → CommonAttribute → ExchangePlan → XDTOPackage → WebService → HTTPService → WSReference → EventSubscription → ScheduledJob → SettingsStorage → FunctionalOption → FunctionalOptionsParameter → DefinedType → CommonCommand → CommandGroup → Constant → CommonForm → Catalog → Document → DocumentNumerator → Sequence → DocumentJournal → Enum → Report → DataProcessor → InformationRegister → AccumulationRegister → ChartOfCharacteristicTypes → ChartOfAccounts → AccountingRegister → ChartOfCalculationTypes → CalculationRegister → BusinessProcess → Task → IntegrationService


---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
