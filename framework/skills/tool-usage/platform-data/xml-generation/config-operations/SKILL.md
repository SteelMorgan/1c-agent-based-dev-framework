---
name: config-operations
description: "xml-gen Configuration.xml: свойства и ChildObjects"
---

# Config Operations

## Когда применять

| Триггер | Действие |
|---------|----------|
| Создать новую конфигурацию | `config init --name <Name> <output_dir>` |
| Посмотреть состав конфигурации | `config info <configPath>` |
| Изменить свойство конфигурации | `config edit <configPath> --op modify-property --value "PropName=Value"` |
| Добавить/удалить объект из ChildObjects | `config edit <configPath> --op add-child --value "Type.Name"` |
| Проверить Configuration.xml | `config validate <configPath>` |

## Команды

### config init

```bash
xml-gen config init --name <Name> [--compat <Version>] <output_dir>
```

Результат: `Configuration.xml` + `Languages/Русский.xml` + `ConfigDumpInfo.xml` + заглушки модулей.

### config info

```bash
xml-gen config info [--mode brief|overview|full] <configPath>
```

`brief` — однострочная сводка; `overview` — свойства + счётчики объектов; `full` — все свойства и объекты.

### config edit

```bash
xml-gen config edit <configPath> --op <operation> --value <value>
```

Операции:
- `modify-property` — `--value "CompatibilityMode=Version8_3_24"`
- `add-childObject` — `--value "Catalog.Товары"`. Guard: файл объекта (`Catalogs/Товары.xml`) должен существовать, иначе throw. `--no-file-check` отключает guard.
- `remove-childObject` — `--value "Catalog.Товары"`
- `add-defaultRole` / `remove-defaultRole` — `--value "ОсновнаяРоль"`
- `set-defaultRoles` — заменить список: `--value "Роль1 ;; Роль2"`

### config validate

10 проверок: структура XML, namespace, UUID, InternalInfo (7 ClassId), Properties, enum-значения (11 свойств), ChildObjects (порядок 44 типов, дубликаты), DefaultLanguage, файлы языков, каталоги объектов.

```bash
xml-gen config validate <configPath>
```

## ChildObjects — порядок 44 типов

Конфигурация 1С требует строгий порядок типов в ChildObjects:

Language → Subsystem → StyleItem → Style → CommonPicture → SessionParameter → Role → CommonTemplate → FilterCriterion → CommonModule → CommonAttribute → ExchangePlan → XDTOPackage → WebService → HTTPService → WSReference → EventSubscription → ScheduledJob → SettingsStorage → FunctionalOption → FunctionalOptionsParameter → DefinedType → CommonCommand → CommandGroup → Constant → CommonForm → Catalog → Document → DocumentNumerator → Sequence → DocumentJournal → Enum → Report → DataProcessor → InformationRegister → AccumulationRegister → ChartOfCharacteristicTypes → ChartOfAccounts → AccountingRegister → ChartOfCalculationTypes → CalculationRegister → BusinessProcess → Task → IntegrationService

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
