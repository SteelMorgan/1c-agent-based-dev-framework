---
name: subsystem-operations
description: Operations with 1C subsystems and the command interface — compile, info, edit, validate. Use when creating subsystems, controlling their content, and configuring CommandInterface.
---

# Subsystem + Interface Operations

Working with 1C subsystems and the command interface.

## When to apply

| Trigger | Action |
|---------|--------|
| Need to create a subsystem | `subsystem compile subsystem.json <output_dir>` |
| Need to inspect subsystem contents | `subsystem info <subsystemPath>` |
| Need to add an object to a subsystem | `subsystem edit <path> --op add-content --value "Catalog.Товары"` |
| Need to validate a subsystem | `subsystem validate <subsystemPath>` |
| Need to adjust command visibility | `interface edit <ciPath> --op hide --value "..."` |
| Need to check CommandInterface.xml | `interface validate <ciPath>` |
| Need to view the subsystem tree | `subsystem info --mode tree <subsystemPath>` |

## Subsystem Commands

### subsystem compile

Generate a subsystem from JSON.

```bash
xml-gen subsystem compile <subsystem.json> <output_dir>
```

### subsystem info

Analyze a subsystem (5 modes: brief, overview, full, tree, ci).

```bash
xml-gen subsystem info [--mode brief|overview|full|tree|ci] <subsystemPath>
```

### subsystem edit

```bash
xml-gen subsystem edit <subsystemPath> --op <operation> --value <value>
```

**Operations:**
- `add-content` — add an object: `"Catalog.Товары"` or `["Catalog.Товары","Document.Заказ"]`
- `remove-content` — remove an object
- `add-child` — add a child subsystem
- `remove-child` — remove a child subsystem
- `set-property` — `"IncludeInCommandInterface=true"`, `"Synonym=Торговля"`, `"Picture=CommonPicture.ТорговляИСклад"`

### subsystem validate

13 checks: XML structure, Properties, Content, ChildObjects, files, CommandInterface.

```bash
xml-gen subsystem validate <subsystemPath>
```

## Interface Commands

### interface edit

```bash
xml-gen interface edit <ciPath> --op <operation> --value <value>
```

**Operations:**
- `hide` — hide a command: `"Catalog.Товары.StandardCommand.Create"`
- `show` — show a command
- `place` — place a command in a group: `"command=... group=NavigationPanelImportant"`
- `set-order` — order of commands inside a group
- `set-subsystem-order` — order of subsystems
- `set-group-order` — order of groups

### interface validate

13 checks: sections, CommandsVisibility, CommandsPlacement, CommandsOrder, SubsystemsOrder, GroupsOrder.

```bash
xml-gen interface validate <ciPath>
```

## Command link format

- `CommonCommand.ИмяКоманды` — common command
- `Catalog.Товары.StandardCommand.Create` — standard command
- `Catalog.Товары.Command.ПечатьЭтикетки` — object command
- `0:<uuid>` — UUID reference


---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
