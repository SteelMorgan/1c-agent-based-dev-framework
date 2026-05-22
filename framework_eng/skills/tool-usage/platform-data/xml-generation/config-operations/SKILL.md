---
name: config-operations
description: "Configuration operations for 1C (CF) — init, info, edit, validate. Use when creating a configuration, analyzing structure, changing properties and ChildObjects, and validating Configuration.xml."
---

# Config Operations

## When to use

| Trigger | Action |
|---------|----------|
| Create a new configuration | `config init --name <Name> <output_dir>` |
| View the configuration contents | `config info <configPath>` |
| Change a configuration property | `config edit <configPath> --op modify-property --value "PropName=Value"` |
| Add/remove an object from ChildObjects | `config edit <configPath> --op add-child --value "Type.Name"` |
| Validate Configuration.xml | `config validate <configPath>` |

## Commands

### config init

```bash
xml-gen config init --name <Name> [--compat <Version>] <output_dir>
```

Result: `Configuration.xml` + `Languages/Русский.xml` + `ConfigDumpInfo.xml` + module stubs.

### config info

```bash
xml-gen config info [--mode brief|overview|full] <configPath>
```

`brief` — one-line summary; `overview` — properties + object counters; `full` — all properties and objects.

### config edit

```bash
xml-gen config edit <configPath> --op <operation> --value <value>
```

Operations:
- `modify-property` — `--value "CompatibilityMode=Version8_3_24"`
- `add-childObject` — `--value "Catalog.Товары"`. Guard: the object file (`Catalogs/Товары.xml`) must exist, otherwise throw. `--no-file-check` disables the guard.
- `remove-childObject` — `--value "Catalog.Товары"`
- `add-defaultRole` / `remove-defaultRole` — `--value "ОсновнаяРоль"`
- `set-defaultRoles` — replace the list: `--value "Роль1 ;; Роль2"`

### config validate

10 checks: XML structure, namespace, UUID, InternalInfo (7 ClassId), Properties, enum values (11 properties), ChildObjects (order of 44 types, duplicates), DefaultLanguage, language files, object directories.

```bash
xml-gen config validate <configPath>
```

## ChildObjects — order of 44 types

1C configuration requires a strict type order in ChildObjects:

Language → Subsystem → StyleItem → Style → CommonPicture → SessionParameter → Role → CommonTemplate → FilterCriterion → CommonModule → CommonAttribute → ExchangePlan → XDTOPackage → WebService → HTTPService → WSReference → EventSubscription → ScheduledJob → SettingsStorage → FunctionalOption → FunctionalOptionsParameter → DefinedType → CommonCommand → CommandGroup → Constant → CommonForm → Catalog → Document → DocumentNumerator → Sequence → DocumentJournal → Enum → Report → DataProcessor → InformationRegister → AccumulationRegister → ChartOfCharacteristicTypes → ChartOfAccounts → AccountingRegister → ChartOfCalculationTypes → CalculationRegister → BusinessProcess → Task → IntegrationService

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
