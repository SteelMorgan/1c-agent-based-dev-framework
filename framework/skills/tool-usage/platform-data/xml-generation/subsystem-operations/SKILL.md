---
name: subsystem-operations
description: Операции с подсистемами и командным интерфейсом 1С — compile, info, edit, validate. Используй при создании подсистем, управлении составом, настройке CommandInterface.
---

# Subsystem + Interface Operations

Работа с подсистемами 1С и командным интерфейсом.

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать подсистему | `subsystem compile subsystem.json <output_dir>` |
| Нужно посмотреть состав подсистемы | `subsystem info <subsystemPath>` |
| Нужно добавить объект в подсистему | `subsystem edit <path> --op add-content --value "Catalog.Товары"` |
| Нужно проверить подсистему | `subsystem validate <subsystemPath>` |
| Нужно настроить видимость команд | `interface edit <ciPath> --op hide --value "..."` |
| Нужно проверить CommandInterface.xml | `interface validate <ciPath>` |
| Нужно посмотреть дерево подсистем | `subsystem info --mode tree <subsystemPath>` |

## Команды подсистем

### subsystem compile

Генерация подсистемы из JSON.

```bash
xml-gen subsystem compile <subsystem.json> <output_dir>
```

### subsystem info

Анализ подсистемы (5 режимов: brief, overview, full, tree, ci).

```bash
xml-gen subsystem info [--mode brief|overview|full|tree|ci] <subsystemPath>
```

### subsystem edit

```bash
xml-gen subsystem edit <subsystemPath> --op <operation> --value <value>
```

**Операции:**
- `add-content` — добавить объект: `"Catalog.Товары"` или `["Catalog.Товары","Document.Заказ"]`
- `remove-content` — удалить объект
- `add-child` — добавить дочернюю подсистему
- `remove-child` — удалить дочернюю подсистему
- `set-property` — `"IncludeInCommandInterface=true"`, `"Synonym=Торговля"`, `"Picture=CommonPicture.ТорговляИСклад"`

### subsystem validate

13 проверок: XML-структура, Properties, Content, ChildObjects, файлы, CommandInterface.

```bash
xml-gen subsystem validate <subsystemPath>
```

## Команды интерфейса

### interface edit

```bash
xml-gen interface edit <ciPath> --op <operation> --value <value>
```

**Операции:**
- `hide` — скрыть команду: `"Catalog.Товары.StandardCommand.Create"`
- `show` — показать команду
- `place` — разместить команду в группе: `"command=... group=NavigationPanelImportant"`
- `set-order` — порядок команд в группе
- `set-subsystem-order` — порядок подсистем
- `set-group-order` — порядок групп

### interface validate

13 проверок: секции, CommandsVisibility, CommandsPlacement, CommandsOrder, SubsystemsOrder, GroupsOrder.

```bash
xml-gen interface validate <ciPath>
```

## Формат ссылок на команды

- `CommonCommand.ИмяКоманды` — общая команда
- `Catalog.Товары.StandardCommand.Create` — стандартная команда
- `Catalog.Товары.Command.ПечатьЭтикетки` — команда объекта
- `0:<uuid>` — UUID-ссылка

## См. также

- [config-operations](../config-operations/) — конфигурация (ChildObjects <-> подсистемы)
- [meta-operations](../meta-operations/) — объекты метаданных

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
