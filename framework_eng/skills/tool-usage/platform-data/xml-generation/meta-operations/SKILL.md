---
name: meta-operations
description: Operations with 1С metadata objects (23 types) — compile, info, edit, validate, remove. Use when creating catalogs, documents, registers, enumerations, and other configuration objects.
---

# Meta Operations

Working with 1С metadata objects (Catalog, Document, Register, etc. — 23 types).

## When to apply

| Trigger | Action |
|---------|--------|
| Need to create a catalog/document/register | `meta compile meta.json <output_dir>` |
| Need to inspect an object’s structure | `meta info <objectPath>` |
| Need to add an attribute/TS/dimension | `meta edit <objectPath> --op add-attribute "Name: Type"` |
| Need to validate a metadata object | `meta validate <objectPath>` |
| Need to remove an object from configuration | `meta remove <configDir> Type.Name` |

## Supported types (23)

| Category | Types |
|-----------|------|
| Referential | Catalog, Document, Enum, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, ExchangePlan |
| Registers | InformationRegister, AccumulationRegister, AccountingRegister, CalculationRegister |
| Processes | BusinessProcess, Task |
| Services | HTTPService, WebService |
| Other | Constant, DefinedType, CommonModule, Report, DataProcessor, ScheduledJob, DocumentJournal, EventSubscription |

## Commands

### meta compile

Generate an object from JSON DSL.

```bash
xml-gen meta compile <meta.json> <output_dir>
```

**JSON DSL:**
```json
{
  "type": "Catalog",
  "name": "Товары",
  "codeLength": 9,
  "descriptionLength": 150,
  "hierarchical": true,
  "attributes": [
    "Артикул: String(50)",
    "Цена: Number(15,2)",
    "Производитель: CatalogRef.Контрагенты"
  ],
  "tabularSections": [
    {
      "name": "Штрихкоды",
      "attributes": ["Штрихкод: String(13)"]
    }
  ]
}
```

### meta info

Object analysis: properties, attributes, TS, forms.

```bash
xml-gen meta info [--mode brief|overview|full] <objectPath>
```

### meta edit

Modify an object (add/remove/modify).

```bash
xml-gen meta edit <objectPath> --op <operation> "<value>"
```

**Operations:**
- `add-attribute` — `"Weight: Number(15,3) | indexing"`
- `add-dimension` — for registers
- `add-resource` — for registers
- `add-ts` — `"Barcodes"`
- `add-ts-attribute` — `"TS.Barcodes: Value: String(13)"`
- `add-enumValue` — `"Paid"`
- `add-form` / `add-template` / `add-command`
- `remove-attribute` / `remove-ts` / `remove-enumValue` etc.
- `modify-attribute` — `"Name: synonym=New synonym, type=String(100)"`
- `add-property` / `modify-property` — changing object properties

**Shorthand format:**
```
AttributeName: DataType | flags >> after/before Anchor
```

Examples:
```
ItemCode: String(50)
Amount: Number(15,2) | nonneg
Counterparty: CatalogRef.Контрагенты | indexing
```

### meta validate

Validate an object (~40 checks).

```bash
xml-gen meta validate <objectPath>
```

**Checks:** XML structure, UUID, Properties (Name, Synonym), boolean properties, type-specific rules (22 types), StandardAttributes, forbidden properties, ChildObjects, InternalInfo/GeneratedType, file structure.

### meta remove

Remove an object from a configuration.

```bash
xml-gen meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]
```

**Algorithm:**
1. Search for the object files
2. Check references in XML/BSL
3. Remove from Configuration.xml ChildObjects
4. Remove from subsystems
5. Delete files

## Russian synonyms of types

The shorthand may use Russian names: Справочник → Catalog, Документ → Document, Перечисление → Enum, РегистрСведений → InformationRegister, etc.

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
