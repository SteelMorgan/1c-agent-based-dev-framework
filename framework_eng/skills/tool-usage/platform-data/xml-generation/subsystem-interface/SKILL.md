---
name: subsystem-interface
description: "xml-gen subsystems and CommandInterface.xml"
---

# Subsystem + Interface Operations

## When to use

| Trigger | Action |
|---------|----------|
| Create a subsystem from JSON | `subsystem compile subsystem.json <output_dir>` |
| View subsystem contents | `subsystem info <subsystemPath>` |
| Add an object to a subsystem | `subsystem edit <path> --op add-content --value "Catalog.Товары"` |
| Validate a subsystem | `subsystem validate <subsystemPath>` |
| View the subsystem tree | `subsystem info --mode tree <subsystemPath>` |
| Hide/show a command | `xml-gen interface edit … --op hide/show` |
| Place a command in a group | `xml-gen interface edit … --op place` |
| Set the order of commands/subsystems/groups | `xml-gen interface edit … --op set-order/set-subsystem-order/set-group-order` |
| Validate CommandInterface.xml | `xml-gen interface validate <ciPath>` |
| Set visibility by role | `xml-gen interface edit … --op hide --role <RoleName>` |

---

## §1 Subsystem operations

### subsystem compile

```bash
xml-gen subsystem compile <subsystem.json> <output_dir> [--parent <parentSubsystem.xml>] [--no-stubs]
```

- `--parent <path>` — bottom-up registration: the new subsystem is added to the parent subsystem's ChildObjects (instead of Configuration.xml).
- `--no-stubs` — disable automatic creation of stub XML for declared `content` items and `children` without files. Enabled by default so validation does not fail in an intermediate state.

### subsystem info

```bash
xml-gen subsystem info [--mode brief|overview|full|tree|ci] <subsystemPath>
```

### subsystem edit

```bash
xml-gen subsystem edit <subsystemPath> --op <operation> --value <value>
```

Operations: `add-content` / `remove-content` / `add-child` / `remove-child` / `set-property`

- `add-content` — `"Catalog.Товары"` or `["Catalog.Товары","Document.Заказ"]`
- `add-child` — if the file `<Parent>/Subsystems/<childName>.xml` does not exist, stub XML is created.
- `set-property` — `"IncludeInCommandInterface=true"`, `"Synonym=Trade"`, `"Picture=CommonPicture.ТорговляИСклад"`

### subsystem validate

13 checks: XML structure, Properties, Content, ChildObjects, files, CommandInterface.

```bash
xml-gen subsystem validate <subsystemPath>
```

---

## §2 Interface operations

### interface edit

```bash
xml-gen interface edit <ciPath> --op <operation> --value <value> [--role <RoleName>] [--create-if-missing] [--no-validate] [--definition-file <file.json>]
```

- `ciPath` — path to `CommandInterface.xml` or to the subsystem directory (`Ext/CommandInterface.xml` is added automatically).
- `--definition-file` — JSON file with an array of operations (batch mode; incompatible with `--op`).
- `--create-if-missing` — create a new file if missing.
- `--no-validate` — skip automatic validation after changes.

#### Operations

| Operation | `--value` argument |
|----------|--------------------|
| `hide` | `"Catalog.Товары.StandardCommand.Create"` or `["Cmd1","Cmd2"]` |
| `show` | `"Report.Продажи.Command.Отчёт"` |
| `place` | `"command=Catalog.X.StandardCommand.Create group=CommandGroup.Документы"` |
| `set-order` | `'{"group":"CommandGroup.Отчеты","commands":["Cmd1","Cmd2"]}'` |
| `set-subsystem-order` | `'["Subsystem.X.Subsystem.A","Subsystem.X.Subsystem.B"]'` |
| `set-group-order` | `'["NavigationPanelOrdinary","NavigationPanelImportant"]'` |

#### Batch mode (definition-file)

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

13 checks: root element `<CommandInterface>`, allowed sections (`CommandsVisibility`/`CommandsPlacement`/`CommandsOrder`/`SubsystemsOrder`/`GroupsOrder`), canonical section order, reference format, duplicates, `true`/`false` values, group references, `Subsystem.X.Subsystem.Y` format, UTF-8 BOM encoding, extra attributes.

The `reviewer` subagent must call `interface validate` before the final review of changes in `CommandInterface.xml`.

---

## Command reference format

| Type | Example |
|-----|--------|
| Common command | `CommonCommand.ОткрытьСправочник` |
| Standard catalog command | `Catalog.Товары.StandardCommand.Create` |
| Standard command (list) | `Catalog.Товары.StandardCommand.OpenList` |
| Object command | `Catalog.Товары.Command.ПечатьЭтикетки` |
| Report command | `Report.Продажи.Command.Отчёт` |
| UUID reference | `0:<uuid>` |

By default, `interface edit` runs `interface validate` after each change.

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
