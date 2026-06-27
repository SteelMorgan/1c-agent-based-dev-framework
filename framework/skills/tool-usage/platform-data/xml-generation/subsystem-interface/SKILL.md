---
name: subsystem-interface
description: "xml-gen subsystems and CommandInterface.xml"
---

# Subsystem + Interface Operations

## Когда применять

| Триггер | Действие |
|---------|----------|
| Создать подсистему из JSON | `subsystem compile subsystem.json <output_dir>` |
| Посмотреть состав подсистемы | `subsystem info <subsystemPath>` |
| Добавить объект в подсистему | `subsystem edit <path> --op add-content --value "Catalog.Товары"` |
| Проверить подсистему | `subsystem validate <subsystemPath>` |
| Дерево подсистем | `subsystem info --mode tree <subsystemPath>` |
| Скрыть/показать команду | `xml-gen interface edit … --op hide/show` |
| Разместить команду в группе | `xml-gen interface edit … --op place` |
| Задать порядок команд/подсистем/групп | `xml-gen interface edit … --op set-order/set-subsystem-order/set-group-order` |
| Проверить CommandInterface.xml | `xml-gen interface validate <ciPath>` |
| Видимость по роли | `xml-gen interface edit … --op hide --role <RoleName>` |

---

## §1 Subsystem operations

### subsystem compile

```bash
xml-gen subsystem compile <subsystem.json> <output_dir> [--parent <parentSubsystem.xml>] [--no-stubs]
```

- `--parent <path>` — bottom-up регистрация: новая подсистема добавляется в ChildObjects родительской (а не в Configuration.xml).
- `--no-stubs` — отключить автосоздание stub-XML для `content`/`children` без файлов. По умолчанию включено — нужно чтобы валидация не падала в промежуточном состоянии.

### subsystem info

```bash
xml-gen subsystem info [--mode brief|overview|full|tree|ci] <subsystemPath>
```

### subsystem edit

```bash
xml-gen subsystem edit <subsystemPath> --op <operation> --value <value>
```

Операции: `add-content` / `remove-content` / `add-child` / `remove-child` / `set-property`

- `add-content` — `"Catalog.Товары"` или `["Catalog.Товары","Document.Заказ"]`
- `add-child` — если файл `<Parent>/Subsystems/<childName>.xml` отсутствует — создаётся stub-XML.
- `set-property` — `"IncludeInCommandInterface=true"`, `"Synonym=Торговля"`, `"Picture=CommonPicture.ТорговляИСклад"`

### subsystem validate

13 проверок: XML-структура, Properties, Content, ChildObjects, файлы, CommandInterface.

```bash
xml-gen subsystem validate <subsystemPath>
```

---

## §2 Interface operations

### interface edit

```bash
xml-gen interface edit <ciPath> --op <operation> --value <value> [--role <RoleName>] [--create-if-missing] [--no-validate] [--definition-file <file.json>]
```

- `ciPath` — путь к `CommandInterface.xml` или к директории подсистемы (`Ext/CommandInterface.xml` добавляется автоматически).
- `--definition-file` — JSON-файл с массивом операций (пакетный режим; несовместим с `--op`).
- `--create-if-missing` — создать новый файл если отсутствует.
- `--no-validate` — пропустить авто-валидацию после изменений.

#### Операции

| Операция | Аргумент `--value` |
|----------|--------------------|
| `hide` | `"Catalog.Товары.StandardCommand.Create"` или `["Cmd1","Cmd2"]` |
| `show` | `"Report.Продажи.Command.Отчёт"` |
| `place` | `"command=Catalog.X.StandardCommand.Create group=CommandGroup.Документы"` |
| `set-order` | `'{"group":"CommandGroup.Отчеты","commands":["Cmd1","Cmd2"]}'` |
| `set-subsystem-order` | `'["Subsystem.X.Subsystem.A","Subsystem.X.Subsystem.B"]'` |
| `set-group-order` | `'["NavigationPanelOrdinary","NavigationPanelImportant"]'` |

#### Пакетный режим (definition-file)

```json
[
  { "op": "hide", "value": "Catalog.Товары.StandardCommand.OpenList" },
  { "op": "show", "value": "Report.Продажи.Command.Отчёт" },
  { "op": "place", "value": "command=CommonCommand.Настройки group=CommandGroup.Сервис" }
]
```

### interface validate

```bash
xml-gen interface validate <ciPath> [--detailed] [--max-errors <N>]
```

13 проверок: корневой элемент `<CommandInterface>`, допустимые секции (`CommandsVisibility`/`CommandsPlacement`/`CommandsOrder`/`SubsystemsOrder`/`GroupsOrder`), канонический порядок секций, формат ссылок, дубликаты, значения `true`/`false`, ссылки на группы, формат `Subsystem.X.Subsystem.Y`, encoding UTF-8 BOM, лишние атрибуты.

Субагент `reviewer` обязан вызвать `interface validate` перед финальным ревью изменений в `CommandInterface.xml`.

---

## Формат ссылок на команды

| Тип | Пример |
|-----|--------|
| Общая команда | `CommonCommand.ОткрытьСправочник` |
| Стандартная команда каталога | `Catalog.Товары.StandardCommand.Create` |
| Стандартная команда (список) | `Catalog.Товары.StandardCommand.OpenList` |
| Команда объекта | `Catalog.Товары.Command.ПечатьЭтикетки` |
| Команда отчёта | `Report.Продажи.Command.Отчёт` |
| UUID-ссылка | `0:<uuid>` |

По умолчанию `interface edit` запускает `interface validate` после каждого изменения.

---

depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
