---
name: interface-operations
description: "Editing and validating 1C subsystem CommandInterface.xml files - hiding/showing commands, placing them in groups, ordering, and structure checks. Use when configuring a subsystem's command interface or checking its correctness."
---

# Interface Operations — edit + validate

Targeted editing and structural validation of a 1C subsystem `CommandInterface.xml` file.

## When to use

| Trigger | Action |
|---------|----------|
| Hide a command in the subsystem interface | `xml-gen interface edit … --op hide` |
| Show a hidden command | `xml-gen interface edit … --op show` |
| Place a command in a group | `xml-gen interface edit … --op place` |
| Set the order of commands in a group | `xml-gen interface edit … --op set-order` |
| Set the order of child subsystems | `xml-gen interface edit … --op set-subsystem-order` |
| Set the order of groups | `xml-gen interface edit … --op set-group-order` |
| Validate CommandInterface.xml | `xml-gen interface validate <ciPath>` |
| Set visibility by role | `xml-gen interface edit … --op hide --role <RoleName>` |

---

## interface edit

Editing the contents of `CommandInterface.xml`.

```bash
xml-gen interface edit <ciPath> --op <operation> --value <value> [--role <RoleName>] [--create-if-missing]
```

### Parameters

| Parameter | Required | Description |
|----------|:-----:|----------|
| `ciPath` | yes | Path to `CommandInterface.xml` or to the subsystem directory (`Ext/CommandInterface.xml` is added automatically) |
| `--op` | yes | Operation (see the table below) |
| `--value` | yes | Operation value - a string, JSON object, or JSON array |
| `--role` | no | Restrict command visibility for a specific role (`CommandsVisibility` by role) |
| `--definition-file` | no | JSON file with an array of operations (batch mode; incompatible with `--op`) |
| `--create-if-missing` | no | Create a new `CommandInterface.xml` if the file is missing |
| `--no-validate` | no | Skip auto-validation after changes |

### Operations

| Operation | `--value` argument | Description |
|----------|--------------------|----------|
| `hide` | `"Catalog.Товары.StandardCommand.Create"` or `["Cmd1","Cmd2"]` | Hide a command - add/set `false` in `CommandsVisibility` |
| `show` | `"Report.Продажи.Command.Отчёт"` | Show a command - set `true` in `CommandsVisibility` |
| `place` | `"command=Catalog.X.StandardCommand.Create group=CommandGroup.Документы"` | Place a command in a group (`CommandsPlacement`) |
| `set-order` | `'{"group":"CommandGroup.Отчеты","commands":["Cmd1","Cmd2"]}'` | Set the order of commands in a group (`CommandsOrder`) |
| `set-subsystem-order` | `'["Subsystem.X.Subsystem.A","Subsystem.X.Subsystem.B"]'` | Set the order of child subsystems (`SubsystemsOrder`) |
| `set-group-order` | `'["NavigationPanelOrdinary","NavigationPanelImportant"]'` | Set the order of groups (`GroupsOrder`) |

### Examples

```bash
# Hide the standard command
xml-gen interface edit Subsystems/Продажи/Ext/CommandInterface.xml \
  --op hide --value "Catalog.Товары.StandardCommand.OpenList"

# Show a command
xml-gen interface edit Subsystems/Продажи \
  --op show --value "Report.Продажи.Command.Отчёт"

# Place a command in a group
xml-gen interface edit Subsystems/Продажи \
  --op place --value "command=CommonCommand.Настройки group=CommandGroup.Сервис"

# Set the order in a group
xml-gen interface edit Subsystems/Продажи \
  --op set-order --value '{"group":"CommandGroup.Отчеты","commands":["Report.A.Command.Y","Report.B.Command.Z"]}'

# Set the order of child subsystems
xml-gen interface edit Subsystems/Продажи \
  --op set-subsystem-order --value '["Subsystem.Продажи.Subsystem.Розница","Subsystem.Продажи.Subsystem.Опт"]'

# Create a new CommandInterface.xml with group order
xml-gen interface edit Subsystems/НоваяПодсистема/Ext/CommandInterface.xml \
  --op set-group-order --value '["NavigationPanelOrdinary"]' --create-if-missing

# Batch mode from a JSON file
xml-gen interface edit Subsystems/Продажи/Ext/CommandInterface.xml \
  --definition-file ci-changes.json
```

### Structure of the batch-mode JSON file

```json
[
  { "op": "hide", "value": "Catalog.Товары.StandardCommand.OpenList" },
  { "op": "show", "value": "Report.Продажи.Command.Отчёт" },
  { "op": "place", "value": "command=CommonCommand.Настройки group=CommandGroup.Сервис" }
]
```

---

## interface validate

Structural check of `CommandInterface.xml` before commit.

```bash
xml-gen interface validate <ciPath> [--detailed] [--max-errors <N>]
```

### Parameters

| Parameter | Required | Default | Description |
|----------|:-----:|-----------|----------|
| `ciPath` | yes | — | Path to `CommandInterface.xml` or to the subsystem directory |
| `--detailed` | no | — | Detailed output: all checks, including successful ones |
| `--max-errors` | no | 30 | Stop after N errors |

### What is checked (13 checks)

| # | Check |
|---|---------|
| 1 | Root `<CommandInterface>` element, valid namespace |
| 2 | Allowed sections: `CommandsVisibility`, `CommandsPlacement`, `CommandsOrder`, `SubsystemsOrder`, `GroupsOrder` |
| 3 | Canonical section order |
| 4 | Command reference format (see the "Reference Format" section) |
| 5 | Duplicate commands inside sections |
| 6 | `CommandsVisibility` - allowed values (`true`/`false`) |
| 7 | `CommandsPlacement` - group references exist |
| 8 | `CommandsOrder` - groups from `CommandsPlacement` |
| 9 | `SubsystemsOrder` - `Subsystem.X.Subsystem.Y` format |
| 10 | `GroupsOrder` - known group constants |
| 11 | `version` attribute matches the configuration format |
| 12 | UTF-8 BOM encoding |
| 13 | No extra attributes or elements |

### Example

```bash
xml-gen interface validate Subsystems/Продажи/Ext/CommandInterface.xml --detailed
```

---

## Reference Format

| Type | Example |
|-----|--------|
| Common command | `CommonCommand.ОткрытьСправочник` |
| Standard catalog command | `Catalog.Товары.StandardCommand.Create` |
| Standard catalog command | `Catalog.Товары.StandardCommand.OpenList` |
| Object command | `Catalog.Товары.Command.ПечатьЭтикетки` |
| Report command | `Report.Продажи.Command.Отчёт` |
| UUID reference | `0:<uuid>` |

---

## Automatic validation

By default, `interface edit` runs `interface validate` after every change. Disable with `--no-validate`.

Subagent `reviewer` must call `interface validate` before the final review of `CommandInterface.xml` changes.

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
