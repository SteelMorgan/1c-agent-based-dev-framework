---
name: meta-operations
description: Operations on 1C metadata objects (23 types) — compile, info, edit, validate, remove. Use when creating catalogs, documents, registers, enumerations, and other configuration objects.
---

# Meta Operations

Working with 1C metadata objects (Catalog, Document, Register, etc. — 23 types).

## When to apply

| Trigger | Action |
|---------|----------|
| Need to create a catalog/document/register | `meta compile meta.json <output_dir>` |
| Need to inspect an object structure | `meta info <objectPath>` |
| Need to add an attribute/TS/dimension | `meta edit <objectPath> --op add-attribute "Name: Type"` |
| Need to validate a metadata object | `meta validate <objectPath>` |
| Need to delete an object from the configuration | `meta remove <configDir> Type.Name` |

## Supported types (23)

| Category | Types |
|-----------|------|
| Reference | Catalog, Document, Enum, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, ExchangePlan |
| Registers | InformationRegister, AccumulationRegister, AccountingRegister, CalculationRegister |
| Processes | BusinessProcess, Task |
| Service | HTTPService, WebService |
| Other | Constant, DefinedType, CommonModule, Report, DataProcessor, ScheduledJob, DocumentJournal, EventSubscription |

## Commands

### meta compile

Generating an object from JSON DSL.

```bash
xml-gen meta compile <meta.json> <output_dir>
```

**JSON DSL:**
```json
{
  "type": "Catalog",
  "name": "Products",
  "codeLength": 9,
  "descriptionLength": 150,
  "hierarchical": true,
  "attributes": [
    "ItemCode: String(50)",
    "Price: Number(15,2)",
    "Manufacturer: CatalogRef.Контрагенты"
  ],
  "tabularSections": [
    {
      "name": "Barcodes",
      "attributes": ["Barcode: String(13)"]
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

Modifying an object (add/remove/modify).

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

Object validation (~40 checks).

```bash
xml-gen meta validate <objectPath>
```

**Checks:** XML structure, UUID, Properties (Name, Synonym), boolean properties, type-specific rules (22 types), StandardAttributes, forbidden properties, ChildObjects, InternalInfo/GeneratedType, file structure.

### meta remove

Removing an object from the configuration.

```bash
xml-gen meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]
```

**Algorithm:**
1. Locate the object files
2. Inspect XML/BSL references
3. Remove from Configuration.xml ChildObjects
4. Remove from subsystems
5. Delete files

## Russian synonyms of types

You can use Russian names in the shorthand by mapping the Russian term for Catalog to Catalog, the Russian term for Document to Document, the Russian term for Enumeration to Enum, the Russian term for Information register to InformationRegister, etc.

## See also

- [config-operations](../config-operations/) — working with the configuration
- [xml-gen-cli](../xml-gen-cli/) — validate and edit commands

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
