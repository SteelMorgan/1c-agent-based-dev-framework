---
name: config-operations
description: Operations with the 1C configuration (CF) — init, info, edit, validate. Use when creating a configuration, analyzing structure, modifying properties and ChildObjects, validating Configuration.xml.
---

# Config Operations

Working with 1C configurations (Configuration.xml).

## When to apply

| Trigger | Action |
|---------|----------|
| Need to create a new configuration | `config init --name <Name> <output_dir>` |
| Need to inspect the configuration composition | `config info <configPath>` |
| Need to change a configuration property | `config edit <configPath> --op modify-property --value "PropName=Value"` |
| Need to add/remove an object from ChildObjects | `config edit <configPath> --op add-child --value "Type.Name"` |
| Need to validate Configuration.xml | `config validate <configPath>` |

## Commands

### config init

Create a new configuration.

```bash
xml-gen config init --name <Name> [--compat <Version>] <output_dir>
```

**Result:** Configuration.xml + Languages/Русский.xml + ConfigDumpInfo.xml + stub modules.

### config info

Configuration analysis: properties, composition, object counts.

```bash
xml-gen config info [--mode brief|overview|full] <configPath>
```

**Modes:**
- `brief` — one-line summary
- `overview` — properties + object counts by types
- `full` — all properties, all objects

### config edit

Modifying configuration properties and composition.

```bash
xml-gen config edit <configPath> --op <operation> --value <value>
```

**Operations:**
- `modify-property` — change a property: `--value "CompatibilityMode=Version8_3_24"`
- `add-child` — add an object to ChildObjects: `--value "Catalog.Товары"`
- `remove-child` — remove an object: `--value "Catalog.Товары"`
- `add-default-role` — add a default role: `--value "ОсновнаяРоль"`
- `remove-default-role` — remove a default role

### config validate

Validation of Configuration.xml (10 checks).

```bash
xml-gen config validate <configPath>
```

**Checks:** structure of XML, namespace, UUID, InternalInfo (7 ClassId), Properties, enum values (11 properties), ChildObjects (order of 44 types, duplicates), DefaultLanguage, language files, object catalogs.

## ChildObjects — order of 44 types

1C configuration requires a strict order of types in ChildObjects:

Language → Subsystem → StyleItem → Style → CommonPicture → SessionParameter → Role → CommonTemplate → FilterCriterion → CommonModule → CommonAttribute → ExchangePlan → XDTOPackage → WebService → HTTPService → WSReference → EventSubscription → ScheduledJob → SettingsStorage → FunctionalOption → FunctionalOptionsParameter → DefinedType → CommonCommand → CommandGroup → Constant → CommonForm → Catalog → Document → DocumentNumerator → Sequence → DocumentJournal → Enum → Report → DataProcessor → InformationRegister → AccumulationRegister → ChartOfCharacteristicTypes → ChartOfAccounts → AccountingRegister → ChartOfCalculationTypes → CalculationRegister → BusinessProcess → Task → IntegrationService

## See also

- [meta-operations](../meta-operations/) — working with metadata objects
- [subsystem-operations](../subsystem-operations/) — subsystems and interface
- [xml-gen-cli](../xml-gen-cli/) — validate and edit commands

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
