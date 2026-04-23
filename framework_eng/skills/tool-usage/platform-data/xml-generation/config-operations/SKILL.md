---
name: config-operations
description: "Configuration operations for 1C (CF) — init, info, edit, validate. Use when creating a configuration, analyzing structure, changing properties and ChildObjects, and validating Configuration.xml."
---

# Config Operations

Working with 1C configurations (Configuration.xml).

## When to use

| Trigger | Action |
|---------|----------|
| Need to create a new configuration | `config init --name <Name> <output_dir>` |
| Need to inspect the configuration contents | `config info <configPath>` |
| Need to change a configuration property | `config edit <configPath> --op modify-property --value "PropName=Value"` |
| Need to add/remove an object from ChildObjects | `config edit <configPath> --op add-child --value "Type.Name"` |
| Need to validate Configuration.xml | `config validate <configPath>` |

## Commands

### config init

Create a new configuration.

```bash
xml-gen config init --name <Name> [--compat <Version>] <output_dir>
```

**Result:** Configuration.xml + Languages/Русский.xml + ConfigDumpInfo.xml + module stubs.

### config info

Analyze the configuration: properties, contents, object counters.

```bash
xml-gen config info [--mode brief|overview|full] <configPath>
```

**Modes:**
- `brief` — one-line summary
- `overview` — properties + object counts by type
- `full` — all properties, all objects

### config edit

Modify configuration properties and contents.

```bash
xml-gen config edit <configPath> --op <operation> --value <value>
```

**Operations:**
- `modify-property` — change a property: `--value "CompatibilityMode=Version8_3_24"`
- `add-childObject` — add an object to ChildObjects: `--value "Catalog.Товары"`. Guard: the object file (`Catalogs/Товары.xml`) must exist — otherwise throw. Optional `--no-file-check` disables the guard.
- `remove-childObject` — remove an object: `--value "Catalog.Товары"`
- `add-defaultRole` — add the default role: `--value "ОсновнаяРоль"`
- `remove-defaultRole` — remove the default role
- `set-defaultRoles` — replace the role list: `--value "Роль1 ;; Роль2"`

### config validate

Validate Configuration.xml (10 checks).

```bash
xml-gen config validate <configPath>
```

**Checks:** XML structure, namespace, UUID, InternalInfo (7 ClassId), Properties, enum values (11 properties), ChildObjects (order of 44 types, duplicates), DefaultLanguage, language files, object directories.

## ChildObjects — order of 44 types

1C configuration requires a strict type order in ChildObjects:

Language → Subsystem → StyleItem → Style → CommonPicture → SessionParameter → Role → CommonTemplate → FilterCriterion → CommonModule → CommonAttribute → ExchangePlan → XDTOPackage → WebService → HTTPService → WSReference → EventSubscription → ScheduledJob → SettingsStorage → FunctionalOption → FunctionalOptionsParameter → DefinedType → CommonCommand → CommandGroup → Constant → CommonForm → Catalog → Document → DocumentNumerator → Sequence → DocumentJournal → Enum → Report → DataProcessor → InformationRegister → AccumulationRegister → ChartOfCharacteristicTypes → ChartOfAccounts → AccountingRegister → ChartOfCalculationTypes → CalculationRegister → BusinessProcess → Task → IntegrationService

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
