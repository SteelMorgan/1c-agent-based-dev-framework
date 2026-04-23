---
name: meta-operations
description: "Operations with 1C metadata objects (23 types) - compile, info, edit, validate, remove. Use when creating catalogs, documents, registers, enumerations, and other configuration objects."
---

# Meta Operations

Working with 1C metadata objects (Catalog, Document, Register, etc. - 23 types).

## When to use

| Trigger | Action |
|---------|----------|
| Need to create a catalog/document/register | `meta compile meta.json <output_dir>` |
| Need to inspect an object structure | `meta info <objectPath>` |
| Need to add an attribute/TS/dimension | `meta edit <objectPath> --op add-attribute "Name: Type"` |
| Need to validate a metadata object | `meta validate <objectPath>` |
| Need to remove an object from the configuration | `meta remove <configDir> Type.Name` |

## Supported types (23)

| Category | Types |
|-----------|------|
| Reference | Catalog, Document, Enum, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, ExchangePlan |
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

**Complete Catalog properties:** `hierarchical`, `hierarchyType` (HierarchyFoldersAndItems|HierarchyItemsOnly), `limitLevelCount`, `levelCount`, `foldersOnTop`, `codeLength`, `codeType` (String|Number), `codeAllowedLength` (Variable|Fixed), `codeSeries` (WholeCatalog|WithinOwnerSubordination|WithinSubordination), `descriptionLength`, `autonumbering`, `checkUnique`, `defaultPresentation` (AsDescription|AsCode), `subordinationUse` (ToItems|ToFolders|ToFoldersAndItems), `quickChoice`, `choiceMode` (BothWays|FromChoiceForm|QuickChoice), `editType` (InDialog|InList|BothWays), `owners` (array of strings, for example `["Catalog.Контрагенты"]`).

**The `multiLine` attribute flag** makes a string field multiline (`<MultiLine>true</MultiLine>`). Applicable to Dimension/Resource/Attribute/TS attribute. In shorthand: `"Описание: String(500) | multiline"`.

### meta info

Analyze an object: properties, attributes, TS, forms.

```bash
xml-gen meta info [--mode brief|overview|full] <objectPath>
```

### meta edit

Object modification (add/remove/modify).

```bash
xml-gen meta edit <objectPath> --op <operation> "<value>"
```

**Operations:**
- `add-attribute` — `"Вес: Number(15,3) | indexing"`
- `add-dimension` — for registers
- `add-resource` — for registers
- `add-ts` — `"Штрихкоды"`
- `add-ts-attribute` — `"ТЧ.Штрихкоды: Значение: String(13)"`
- `add-enumValue` — `"Оплачен"`
- `add-form` / `add-template` / `add-command`
- `remove-attribute` / `remove-ts` / `remove-enumValue` and others
- `modify-attribute` — `"Name: synonym=Новый синоним, type=String(100)"`
- `add-property` / `modify-property` — changing object properties

**Shorthand format:**
```
ИмяРеквизита: ТипДанных | флаги >> after/before Якорь
```

Examples:
```
Артикул: String(50)
Сумма: Number(15,2) | nonneg
Контрагент: CatalogRef.Контрагенты | indexing
```

### meta validate

Validate an object (~40 checks).

```bash
xml-gen meta validate <objectPath>
```

**Checks:** XML structure, UUID, Properties (Name, Synonym), boolean properties, type-specific rules (22 types), strict enum validation of values (HierarchyType, SubordinationUse, ChoiceMode, EditType, CodeAllowedLength, CodeSeries, NumberAllowedLength, RegisterRecordsDeletion, RegisterRecordsWritingOnPost, Periodicity, RequireCalculationTypes, etc.), StandardAttributes, forbidden properties, ChildObjects, InternalInfo/GeneratedType, file structure.

**Compilation invariants:**
- `FillFromFillingValue` / `FillValue` / `DataHistory` - are written only for InformationRegister attributes (for other registers they cause an XSD error during loading).
- Attribute names that match standard ones (Ref, Code, Description, Parent, Owner, IsFolder, DeletionMark, PostingMode, DataVersion, Predefined, PredefinedDataName, Posted, Date, Number + Russian synonyms Ссылка, Код, Наименование, Родитель, Владелец, ЭтоГруппа, ПометкаУдаления, РежимПроведения, ВерсияДанных, Предопределенный, ИмяПредопределенныхДанных, Проведен, Дата, Номер) are rejected during compilation.

### meta remove

Remove an object from the configuration.

```bash
xml-gen meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]
```

**Algorithm:**
1. Search for object files
2. Check references in XML/BSL
3. Remove from Configuration.xml ChildObjects
4. Remove from subsystems
5. Delete files

## Russian type synonyms

In shorthand, you can use Russian names: Справочник -> Catalog, Документ -> Document, Перечисление -> Enum, РегистрСведений -> InformationRegister, etc.

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
